package com.lightmare.villagerrealms.core.role;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RoleAssignmentTest {

    @Test
    void promotesOneLaborerPerTwoFarmersEarliestFirst() {
        List<String> in = List.of("laborer", "farmer", "butcher", "laborer", "farmer", "farmer", "farmer", "laborer");
        List<String> out = RoleAssignment.deriveBakers(in);
        assertEquals(List.of("baker", "farmer", "butcher", "baker", "farmer", "farmer", "farmer", "laborer"), out);
    }

    @Test
    void roundsDownAndNeverTouchesNonLaborers() {
        assertEquals(List.of("farmer", "butcher"), RoleAssignment.deriveBakers(List.of("farmer", "butcher")));
        assertEquals(List.of("farmer", "laborer"), RoleAssignment.deriveBakers(List.of("farmer", "laborer")));
        assertEquals(List.of("farmer", "farmer", "baker"), RoleAssignment.deriveBakers(List.of("farmer", "farmer", "laborer")));
    }

    @Test
    void capsAtAvailableLaborers() {
        List<String> in = List.of("farmer", "farmer", "farmer", "farmer", "farmer", "farmer", "laborer");
        assertEquals(List.of("farmer", "farmer", "farmer", "farmer", "farmer", "farmer", "baker"),
                RoleAssignment.deriveBakers(in));
    }

    @Test
    void noFarmersMeansNoBakers() {
        assertEquals(List.of("laborer", "laborer"), RoleAssignment.deriveBakers(List.of("laborer", "laborer")));
        assertEquals(List.of(), RoleAssignment.deriveBakers(List.of()));
    }

    @Test
    void customRatio() {
        assertEquals(List.of("farmer", "baker"), RoleAssignment.deriveBakers(List.of("farmer", "laborer"), 1));
        assertThrows(IllegalArgumentException.class, () -> RoleAssignment.deriveBakers(List.of(), 0));
    }
}
