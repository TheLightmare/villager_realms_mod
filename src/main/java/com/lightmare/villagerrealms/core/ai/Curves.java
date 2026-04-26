package com.lightmare.villagerrealms.core.ai;

public final class Curves {

    private Curves() {}

    public static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    public static float linear(float x) { return clamp01(x); }

    public static float inverseLinear(float x) { return clamp01(1f - x); }

    public static float quadratic(float x) {
        float c = clamp01(x);
        return c * c;
    }

    public static float inverseQuadratic(float x) {
        float c = clamp01(1f - x);
        return c * c;
    }

    public static float threshold(float x, float t) {
        return x >= t ? 1f : 0f;
    }

    /** Smooth ramp from 0 at lo to 1 at hi (clamped). */
    public static float ramp(float x, float lo, float hi) {
        if (hi <= lo) return x >= hi ? 1f : 0f;
        return clamp01((x - lo) / (hi - lo));
    }

    /** Smooth ramp from 1 at lo to 0 at hi (clamped). Useful for "I am hungry" = high when hunger value is low. */
    public static float inverseRamp(float x, float lo, float hi) {
        return clamp01(1f - ramp(x, lo, hi));
    }
}
