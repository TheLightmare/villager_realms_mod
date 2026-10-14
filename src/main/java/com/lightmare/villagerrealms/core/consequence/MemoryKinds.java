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
    /** This NPC is expecting a child. subject = partner ref; detail = child uuid. Starts the conception cooldown. */
    public static final String CHILD_CONCEIVED = "CHILD_CONCEIVED";
    /** This NPC's child was born. subject = child ref. */
    public static final String CHILD_BORN = "CHILD_BORN";
    /** This NPC was born. subject = parent ref (one event per parent). */
    public static final String BORN_TO = "BORN_TO";
    /** This NPC brokered a trade with the market. subject = trader ref; detail = what and for how much. */
    public static final String TRADED = "TRADED";

    private MemoryKinds() {}
}
