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
    void laborerForagesWithNoWorkstation() {
        Role lab = RoleRegistry.getOrLaborer("laborer");
        assertTrue(lab.worker());
        assertEquals(List.of(new RoleItemSpec(RoleRegistry.FORAGE_ITEM, 1)), lab.produces());
        assertTrue(lab.consumes().isEmpty());
        assertEquals("", lab.workstationKind());
    }

    @Test
    void nonFoodVanillaProfessionsForage() {
        for (String id : List.of("librarian", "toolsmith", "mason", "cartographer", "cleric",
                "armorer", "fletcher", "leatherworker", "weaponsmith")) {
            Role r = RoleRegistry.get(id).orElseThrow(() -> new AssertionError("missing role " + id));
            assertTrue(r.worker(), id);
            assertEquals(List.of(new RoleItemSpec(RoleRegistry.FORAGE_ITEM, 1)), r.produces(), id);
            assertEquals("", r.workstationKind(), id + " must have no workstation kind (forages at centre)");
        }
    }

    @Test
    void childIsANonWorkerWithNoStation() {
        Role child = RoleRegistry.get(RoleRegistry.CHILD).orElseThrow();
        assertFalse(child.worker());
        assertTrue(child.produces().isEmpty());
        assertEquals("", child.workstationKind());
    }

    @Test
    void replaceAllSwapsTheSetButKeepsALaborerFallback() {
        RoleRegistry.replaceAll(List.of(
                new Role("miner", true, List.of(), List.of(new RoleItemSpec("minecraft:coal", 1)), "miner")));
        assertEquals(2, RoleRegistry.size());
        assertTrue(RoleRegistry.get("miner").isPresent());
        assertFalse(RoleRegistry.get("farmer").isPresent());
        assertEquals("laborer", RoleRegistry.getOrLaborer("farmer").id());
    }

    @Test
    void replaceAllHonoursAnExplicitLaborer() {
        Role customLaborer = new Role("laborer", false, List.of(), List.of(), "");
        RoleRegistry.replaceAll(List.of(customLaborer));
        assertEquals(customLaborer, RoleRegistry.getOrLaborer("anything"));
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
