package com.lightmare.villagerrealms.core.role;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleRegistryTest {

    @AfterEach
    void resetDefaults() {
        RoleRegistry.resetToDefaults();
    }

    @Test
    void defaultsHaveExpectedRoles() {
        assertTrue(RoleRegistry.get("farmer").isPresent());
        assertTrue(RoleRegistry.get("baker").isPresent());
        assertTrue(RoleRegistry.get("butcher").isPresent());
        assertTrue(RoleRegistry.get("laborer").isPresent());
    }

    @Test
    void farmerProducesWheat() {
        Role farmer = RoleRegistry.get("farmer").orElseThrow();
        assertTrue(farmer.worker());
        assertEquals(1, farmer.produces().size());
        assertEquals("minecraft:wheat", farmer.produces().get(0).itemId());
        assertTrue(farmer.consumes().isEmpty());
    }

    @Test
    void bakerConsumesWheatProducesBread() {
        Role baker = RoleRegistry.get("baker").orElseThrow();
        assertEquals(1, baker.consumes().size());
        assertEquals("minecraft:wheat", baker.consumes().get(0).itemId());
        assertEquals(1, baker.produces().size());
        assertEquals("minecraft:bread", baker.produces().get(0).itemId());
    }

    @Test
    void laborerIsNotAWorker() {
        Role lab = RoleRegistry.getOrLaborer("laborer");
        assertFalse(lab.worker());
        assertTrue(lab.produces().isEmpty());
    }

    @Test
    void unknownRoleFallsBackToLaborer() {
        Role role = RoleRegistry.getOrLaborer("not_a_real_role");
        assertNotNull(role);
        assertEquals("laborer", role.id());
    }

    @Test
    void registerCanOverrideOrAdd() {
        RoleRegistry.register(new Role(
                "alchemist", true,
                List.of(new RoleItemSpec("minecraft:nether_wart", 1)),
                List.of(new RoleItemSpec("minecraft:potion", 1)),
                "alchemist_table"));
        Role got = RoleRegistry.get("alchemist").orElseThrow();
        assertEquals("alchemist", got.id());
        assertEquals("minecraft:potion", got.produces().get(0).itemId());
    }
}
