package com.lightmare.villagerrealms.core.ai;

/**
 * Minimal village snapshot the AI needs. Kept tiny so the AI package never
 * has to know about VillageRecord shape — economy and faction work in later
 * steps will extend this through additional methods or sibling views.
 */
public interface VillageView {

    String villageId();

    /** Aggregate count of edible items currently held by the village (for Tier 2 plausibility). */
    int communalFoodUnits();

    /** Number of unoccupied beds. */
    int freeBeds();

    /** Total food units across all food item ids in the market stockpile. */
    int marketFoodUnits();

    /** Best-priced food item the market currently sells, or null if none in stock. */
    String cheapestFoodInMarket();

    /** Current price the buyer would pay for {@code itemId}; never less than 1. */
    long marketPriceOf(String itemId);

    /** Village-side gold treasury (informational; not always relevant to the AI). */
    long villageGold();
}
