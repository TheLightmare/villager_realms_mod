package com.lightmare.villagerrealms.core.economy;

import com.lightmare.villagerrealms.core.record.ItemEntry;
import com.lightmare.villagerrealms.core.record.NPCInventory;
import com.lightmare.villagerrealms.core.record.Provenance;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Pure-function helpers for {@link NPCInventory} mutation. Records are
 * immutable so each call returns a new {@link NPCInventory}.
 *
 * Provenance is stamped on every add — required by CLAUDE.md so v2
 * stolen-goods detection and trade-reputation systems have history.
 *
 * v1 keeps stacks separated by (itemId, provenance) to preserve that
 * history. Same-id same-provenance stacks merge; cross-provenance does
 * not. removal is FIFO across entries with the same itemId, oldest first
 * by acquiredAtTick.
 */
public final class InventoryOps {

    private InventoryOps() {}

    public static NPCInventory add(NPCInventory inv, String itemId, int count,
                                   Provenance prov, long tick) {
        if (count <= 0) return inv;
        List<ItemEntry> next = new ArrayList<>(inv.items().size() + 1);
        boolean merged = false;
        for (ItemEntry e : inv.items()) {
            if (!merged && e.itemId().equals(itemId) && e.provenance() == prov) {
                next.add(new ItemEntry(itemId, e.count() + count, prov, e.acquiredAtTick()));
                merged = true;
            } else {
                next.add(e);
            }
        }
        if (!merged) {
            next.add(new ItemEntry(itemId, count, prov, tick));
        }
        return new NPCInventory(next);
    }

    /**
     * Remove up to {@code count} units of {@code itemId}. Returns the new
     * inventory and the number actually removed via the second return path
     * (we expose two methods so callers do not allocate a tuple).
     */
    public static NPCInventory remove(NPCInventory inv, String itemId, int count) {
        if (count <= 0) return inv;
        List<ItemEntry> next = new ArrayList<>(inv.items().size());
        int remaining = count;
        // Sorted by acquiredAtTick ascending — oldest first.
        List<ItemEntry> sorted = new ArrayList<>(inv.items());
        sorted.sort((a, b) -> Long.compare(a.acquiredAtTick(), b.acquiredAtTick()));
        for (ItemEntry e : sorted) {
            if (remaining > 0 && e.itemId().equals(itemId)) {
                int take = Math.min(remaining, e.count());
                int left = e.count() - take;
                remaining -= take;
                if (left > 0) {
                    next.add(new ItemEntry(itemId, left, e.provenance(), e.acquiredAtTick()));
                }
            } else {
                next.add(e);
            }
        }
        return new NPCInventory(next);
    }

    public static int countOf(NPCInventory inv, String itemId) {
        int n = 0;
        for (ItemEntry e : inv.items()) if (e.itemId().equals(itemId)) n += e.count();
        return n;
    }

    public static int countWhere(NPCInventory inv, Predicate<String> idPredicate) {
        int n = 0;
        for (ItemEntry e : inv.items()) if (idPredicate.test(e.itemId())) n += e.count();
        return n;
    }

    public static String firstMatching(NPCInventory inv, Predicate<String> idPredicate) {
        for (ItemEntry e : inv.items()) if (idPredicate.test(e.itemId())) return e.itemId();
        return null;
    }
}
