package com.lightmare.villagerrealms.core.economy;

import com.lightmare.villagerrealms.core.record.ItemEntry;
import com.lightmare.villagerrealms.core.record.NPCInventory;
import com.lightmare.villagerrealms.core.record.Provenance;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class InventoryOpsTest {

    @Test
    void addStampsProvenanceAndMergesSameProvenance() {
        NPCInventory inv = NPCInventory.EMPTY;
        inv = InventoryOps.add(inv, "minecraft:bread", 1, Provenance.CRAFTED, 100L);
        inv = InventoryOps.add(inv, "minecraft:bread", 2, Provenance.CRAFTED, 110L);
        assertEquals(1, inv.items().size());
        assertEquals(3, inv.items().get(0).count());
        assertEquals(Provenance.CRAFTED, inv.items().get(0).provenance());
        assertEquals(100L, inv.items().get(0).acquiredAtTick());
    }

    @Test
    void addKeepsProvenanceStacksDistinct() {
        NPCInventory inv = NPCInventory.EMPTY;
        inv = InventoryOps.add(inv, "minecraft:bread", 1, Provenance.CRAFTED, 100L);
        inv = InventoryOps.add(inv, "minecraft:bread", 1, Provenance.BOUGHT, 110L);
        assertEquals(2, inv.items().size());
    }

    @Test
    void addNoOpForNonPositiveCount() {
        NPCInventory inv = new NPCInventory(List.of(
                new ItemEntry("minecraft:bread", 1, Provenance.CRAFTED, 1L)));
        assertSame(inv, InventoryOps.add(inv, "minecraft:bread", 0, Provenance.CRAFTED, 5L));
        assertSame(inv, InventoryOps.add(inv, "minecraft:bread", -1, Provenance.CRAFTED, 5L));
    }

    @Test
    void removeFifoByAcquiredTick() {
        NPCInventory inv = new NPCInventory(List.of(
                new ItemEntry("minecraft:bread", 2, Provenance.CRAFTED, 100L),
                new ItemEntry("minecraft:bread", 2, Provenance.BOUGHT, 50L)));
        // Oldest first => bought (50L) consumed first.
        NPCInventory after = InventoryOps.remove(inv, "minecraft:bread", 1);
        assertEquals(2, after.items().size());
        // The bought stack is now 1; crafted untouched.
        var bought = after.items().stream()
                .filter(e -> e.provenance() == Provenance.BOUGHT).findFirst().orElseThrow();
        assertEquals(1, bought.count());
    }

    @Test
    void removeAcrossStacks() {
        NPCInventory inv = new NPCInventory(List.of(
                new ItemEntry("minecraft:bread", 2, Provenance.CRAFTED, 100L),
                new ItemEntry("minecraft:bread", 2, Provenance.BOUGHT, 50L)));
        NPCInventory after = InventoryOps.remove(inv, "minecraft:bread", 3);
        assertEquals(1, after.items().size());
        assertEquals(1, after.items().get(0).count());
    }

    @Test
    void countOfAndCountWhereAggregate() {
        NPCInventory inv = new NPCInventory(List.of(
                new ItemEntry("minecraft:bread", 2, Provenance.CRAFTED, 100L),
                new ItemEntry("minecraft:apple", 5, Provenance.GIFTED, 50L)));
        assertEquals(2, InventoryOps.countOf(inv, "minecraft:bread"));
        assertEquals(7, InventoryOps.countWhere(inv, Foods::isFood));
    }

    @Test
    void firstMatchingFindsAnyFood() {
        NPCInventory inv = new NPCInventory(List.of(
                new ItemEntry("minecraft:emerald", 5, Provenance.BOUGHT, 1L),
                new ItemEntry("minecraft:carrot", 1, Provenance.GIFTED, 2L)));
        assertEquals("minecraft:carrot", InventoryOps.firstMatching(inv, Foods::isFood));
        assertNull(InventoryOps.firstMatching(NPCInventory.EMPTY, Foods::isFood));
        assertNotNull(InventoryOps.firstMatching(inv, id -> id.equals("minecraft:emerald")));
    }
}
