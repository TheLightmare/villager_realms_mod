package com.lightmare.villagerrealms.core.record;

import java.util.List;

public record NPCInventory(List<ItemEntry> items) {
    public static final NPCInventory EMPTY = new NPCInventory(List.of());

    public NPCInventory {
        if (items == null) throw new IllegalArgumentException("items required");
        items = List.copyOf(items);
    }
}
