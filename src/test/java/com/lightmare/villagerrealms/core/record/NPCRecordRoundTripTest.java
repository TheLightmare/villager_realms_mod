package com.lightmare.villagerrealms.core.record;

import com.lightmare.villagerrealms.core.Fixtures;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NPCRecordRoundTripTest {

    @Test
    void singleRecordRoundTrip() throws Exception {
        UUID id = UUID.randomUUID();
        NPCRecord rec = Fixtures.npc(id, "village-1", 100.0, -200.0);

        byte[] bytes = NPCRecordFormat.FORMAT.toBytes(rec);
        NPCRecord decoded = NPCRecordFormat.FORMAT.fromBytes(bytes);

        assertEquals(rec, decoded);
    }

    @Test
    void rawCodecRoundTripIgnoringHeader() throws Exception {
        UUID id = UUID.randomUUID();
        NPCRecord rec = Fixtures.npc(id, "village-2", 0, 0);

        byte[] bytes = NPCRecordCodec.NPC.toBytes(rec);
        NPCRecord decoded = NPCRecordCodec.NPC.fromBytes(bytes);

        assertEquals(rec, decoded);
    }
}
