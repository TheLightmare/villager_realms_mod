package com.lightmare.villagerrealms.core.consequence;

/** Opinion scale shared by personal overrides and the faction matrix. */
public final class Opinions {

    public static final int MIN = -100;
    public static final int MAX = 100;

    private Opinions() {}

    public static int clamp(int value) {
        return Math.max(MIN, Math.min(MAX, value));
    }
}
