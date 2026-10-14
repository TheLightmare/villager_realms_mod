package com.lightmare.villagerrealms.core.ai;

/**
 * Read-only view of the nearest hostile actor to an NPC, built server-side
 * from the loaded level (player positions, standing). Null or
 * {@link #NONE} when nothing hostile is in range — considerations must
 * treat both the same. Like {@link VillageView}, a plain POJO so the AI
 * layer stays testable without Minecraft.
 */
public interface ThreatView {

    boolean present();

    double x();
    double y();
    double z();

    /** Squared distance from the NPC to the threat. */
    double distSq();

    /** {@code Refs} ref of the threat, e.g. {@code player:<uuid>}. */
    String ref();

    ThreatView NONE = new ThreatView() {
        @Override public boolean present() { return false; }
        @Override public double x() { return 0; }
        @Override public double y() { return 0; }
        @Override public double z() { return 0; }
        @Override public double distSq() { return Double.MAX_VALUE; }
        @Override public String ref() { return null; }
    };

    static ThreatView of(double x, double y, double z, double distSq, String ref) {
        return new ThreatView() {
            @Override public boolean present() { return true; }
            @Override public double x() { return x; }
            @Override public double y() { return y; }
            @Override public double z() { return z; }
            @Override public double distSq() { return distSq; }
            @Override public String ref() { return ref; }
        };
    }
}
