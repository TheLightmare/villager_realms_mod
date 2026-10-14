package com.lightmare.villagerrealms.core.consequence;

/** Event kinds written to {@code MemoryLog} by the consequence layer. */
public final class MemoryKinds {

    /** This NPC took damage. subject = attacker ref; detail = amount. */
    public static final String ATTACKED = "ATTACKED";
    /** A village-mate died. subject = killer ref (nullable); detail = who and how. */
    public static final String DEATH_WITNESSED = "DEATH_WITNESSED";
    /** Someone handed this NPC an item. subject = giver ref; detail = item. */
    public static final String GIFT_RECEIVED = "GIFT_RECEIVED";
    /** This NPC's role changed. subject = null; detail = "from -> to". */
    public static final String ROLE_CHANGED = "ROLE_CHANGED";

    private MemoryKinds() {}
}
