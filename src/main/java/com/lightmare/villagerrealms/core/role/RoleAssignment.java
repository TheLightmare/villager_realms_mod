package com.lightmare.villagerrealms.core.role;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure post-processing of the auditor's bed → role list. Vanilla villages
 * have no baker workstation, so bakers are derived: for every
 * {@link #FARMERS_PER_BAKER} farmers, one leftover (laborer) bed becomes a
 * baker. This is what gives wheat a consumer and the market its bread.
 */
public final class RoleAssignment {

    public static final String FARMER = "farmer";
    public static final String BAKER = "baker";
    public static final int FARMERS_PER_BAKER = 2;

    private RoleAssignment() {}

    /**
     * Returns a copy of {@code roles} (same order) with up to
     * {@code farmers / farmersPerBaker} laborer entries promoted to baker,
     * earliest first. Entries that aren't laborers are never touched.
     */
    public static List<String> deriveBakers(List<String> roles, int farmersPerBaker) {
        if (farmersPerBaker <= 0) throw new IllegalArgumentException("farmersPerBaker must be > 0");
        int farmers = 0;
        for (String r : roles) if (FARMER.equals(r)) farmers++;
        int wanted = farmers / farmersPerBaker;

        List<String> out = new ArrayList<>(roles.size());
        for (String r : roles) {
            if (wanted > 0 && RoleRegistry.LABORER.equals(r)) {
                out.add(BAKER);
                wanted--;
            } else {
                out.add(r);
            }
        }
        return out;
    }

    public static List<String> deriveBakers(List<String> roles) {
        return deriveBakers(roles, FARMERS_PER_BAKER);
    }
}
