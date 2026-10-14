package com.lightmare.villagerrealms.core.birth;

/**
 * Tunables for the birth system. Ticks: 24000 = one Minecraft day.
 *
 * The cooldown is deliberately longer than a pregnancy so a parent can
 * never be expecting twice at once — that, plus the memory event written
 * at conception, is what makes "already expecting" derivable without a
 * flag on the NPC record.
 */
public final class Births {

    public static final String CHILD_ROLE = "child";

    /** Conception → birth. 3 days. */
    public static final long PREGNANCY_TICKS = 72_000L;
    /** Birth → age-up. 10 days. */
    public static final long CHILDHOOD_TICKS = 240_000L;
    /** Per-parent gap between conceptions. 5 days, > PREGNANCY_TICKS. */
    public static final long CONCEIVE_COOLDOWN_TICKS = 120_000L;

    public static final int ADULT_AGE = 18;
    public static final int MAX_PARENT_AGE = 50;

    /** Readiness gates: well fed and rested. */
    public static final float MIN_HUNGER = 14f;
    public static final float MIN_ENERGY = 0.5f;

    /**
     * Tier 2 conception roll per ready NPC per 100-tick cycle. With ten
     * ready adults this is roughly one conception per village per day
     * before bed and food gating.
     */
    public static final float ABSTRACT_CONCEIVE_CHANCE = 1f / 2400f;

    /** What a newborn starts with: food so it survives until it can buy. */
    public static final String CHILD_STARTING_FOOD = "minecraft:bread";
    public static final int CHILD_STARTING_FOOD_COUNT = 3;
    /** Each parent hands the newborn up to this much gold. */
    public static final long CHILD_GOLD_FROM_EACH_PARENT = 10L;

    /** Sparse personal opinion between family members. */
    public static final int FAMILY_BOND = 20;

    private Births() {}
}
