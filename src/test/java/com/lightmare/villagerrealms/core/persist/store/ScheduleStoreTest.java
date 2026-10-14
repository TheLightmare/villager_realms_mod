package com.lightmare.villagerrealms.core.persist.store;

import com.lightmare.villagerrealms.core.persist.shard.PersistenceWorker;
import com.lightmare.villagerrealms.core.record.ScheduledEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleStoreTest {

    private PersistenceWorker worker;

    @BeforeEach
    void setUp() {
        worker = new PersistenceWorker("test-schedule");
        worker.start();
    }

    @AfterEach
    void tearDown() {
        worker.shutdown(2000);
    }

    private static ScheduledEvent ev(long due, String kind, UUID subject, Map<String, String> data) {
        return new ScheduledEvent(UUID.randomUUID(), due, kind, "village_a", subject, data);
    }

    @Test
    void roundTripsEvents(@TempDir Path tmp) throws Exception {
        var store = new ScheduleStore(tmp, worker);
        store.load();
        UUID child = UUID.randomUUID();
        UUID mother = UUID.randomUUID();
        ScheduledEvent birth = ev(5000L, "BIRTH", child, Map.of("mother", mother.toString()));
        ScheduledEvent noVillage = new ScheduledEvent(UUID.randomUUID(), 10L, "PING", null, null, Map.of());
        store.put(birth);
        store.put(noVillage);

        store.flushDirty();
        worker.awaitIdle(5000);
        assertTrue(Files.exists(tmp.resolve("schedule.bin")));

        var reload = new ScheduleStore(tmp, worker);
        reload.load();
        assertEquals(2, reload.size());
        ScheduledEvent got = reload.get(birth.id()).orElseThrow();
        assertEquals(birth, got);
        assertEquals(mother, got.uuidDatum("mother"));
        assertNull(got.uuidDatum("father"));
        assertEquals(noVillage, reload.get(noVillage.id()).orElseThrow());
    }

    @Test
    void dueReturnsEarliestFirstAndOnlyPastEvents() throws Exception {
        var store = new ScheduleStore(Files.createTempDirectory("sched"), worker);
        store.load();
        ScheduledEvent a = ev(300L, "A", null, Map.of());
        ScheduledEvent b = ev(100L, "B", null, Map.of());
        ScheduledEvent c = ev(200L, "C", null, Map.of());
        ScheduledEvent later = ev(999L, "L", null, Map.of());
        for (ScheduledEvent e : List.of(a, b, c, later)) store.put(e);

        List<ScheduledEvent> due = store.due(300L);
        assertEquals(List.of(b, c, a), due);
        assertTrue(store.due(50L).isEmpty());

        store.remove(b.id());
        assertEquals(List.of(c, a), store.due(300L));
    }

    @Test
    void forSubjectFiltersByKind() throws Exception {
        var store = new ScheduleStore(Files.createTempDirectory("sched"), worker);
        store.load();
        UUID s = UUID.randomUUID();
        store.put(ev(1L, "BIRTH", s, Map.of()));
        store.put(ev(2L, "AGE_UP", s, Map.of()));
        store.put(ev(3L, "BIRTH", UUID.randomUUID(), Map.of()));
        assertEquals(1, store.forSubject(s, "BIRTH").size());
        assertEquals(2, store.forSubject(s, null).size());
    }

    @Test
    void cleanStoreWritesNothing(@TempDir Path tmp) throws Exception {
        var store = new ScheduleStore(tmp, worker);
        store.load();
        store.flushDirty();
        worker.awaitIdle(1000);
        assertFalse(Files.exists(tmp.resolve("schedule.bin")));
    }

    @Test
    void corruptFileQuarantinedAndStoreEmpty(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("schedule.bin");
        Files.write(file, new byte[]{0x00, 0x00, 0x00, 0x00, 0x01});

        var store = new ScheduleStore(tmp, worker);
        store.load();

        assertEquals(0, store.size());
        assertFalse(Files.exists(file));
        try (var s = Files.newDirectoryStream(tmp, "schedule.bin.corrupt-*")) {
            assertTrue(s.iterator().hasNext(), "expected quarantined sibling");
        }
    }
}
