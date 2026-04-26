package com.lightmare.villagerrealms.core.persist.orm;

public final class BlobFormat {

    public static final int MAGIC_NPC_RECORD    = 0x56524E52; // "VRNR"
    public static final int MAGIC_NPC_SHARD     = 0x56524E53; // "VRNS"
    public static final int MAGIC_VILLAGE_STORE = 0x56525653; // "VRVS"
    public static final int MAGIC_FACTION_STORE = 0x56524653; // "VRFS"

    private BlobFormat() {}
}
