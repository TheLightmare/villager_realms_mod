package com.lightmare.villagerrealms.core.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurvesTest {

    private static final float EPS = 1e-5f;

    @Test
    void clampHandlesOutOfRange() {
        assertEquals(0f, Curves.clamp01(-3f));
        assertEquals(1f, Curves.clamp01(7f));
        assertEquals(0.5f, Curves.clamp01(0.5f));
    }

    @Test
    void inverseLinearIsOneMinusX() {
        assertEquals(1f, Curves.inverseLinear(0f), EPS);
        assertEquals(0f, Curves.inverseLinear(1f), EPS);
        assertEquals(0.7f, Curves.inverseLinear(0.3f), EPS);
    }

    @Test
    void inverseQuadraticHurtsHardOnLowInput() {
        assertEquals(1f, Curves.inverseQuadratic(0f), EPS);
        assertEquals(0f, Curves.inverseQuadratic(1f), EPS);
        assertTrue(Curves.inverseQuadratic(0.5f) < Curves.inverseLinear(0.5f),
                "quadratic should drop faster than linear at midpoint");
    }

    @Test
    void rampMatchesEndpoints() {
        assertEquals(0f, Curves.ramp(5f, 10f, 20f), EPS);
        assertEquals(1f, Curves.ramp(25f, 10f, 20f), EPS);
        assertEquals(0.5f, Curves.ramp(15f, 10f, 20f), EPS);
    }
}
