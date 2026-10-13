package com.lightmare.villagerrealms.core.role;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleJsonTest {

    @Test
    void parsesFullRole() {
        Role r = RoleJson.parse("baker", """
                {
                  "worker": true,
                  "consumes": [{"item": "minecraft:wheat", "count": 1}],
                  "produces": [{"item": "minecraft:bread", "count": 2}],
                  "workstation": "baker"
                }
                """);
        assertEquals("baker", r.id());
        assertTrue(r.worker());
        assertEquals(List.of(new RoleItemSpec("minecraft:wheat", 1)), r.consumes());
        assertEquals(List.of(new RoleItemSpec("minecraft:bread", 2)), r.produces());
        assertEquals("baker", r.workstationKind());
    }

    @Test
    void missingFieldsTakeDefaults() {
        Role r = RoleJson.parse("elder", "{}");
        assertFalse(r.worker());
        assertTrue(r.consumes().isEmpty());
        assertTrue(r.produces().isEmpty());
        assertEquals("", r.workstationKind());
    }

    @Test
    void countDefaultsToOne() {
        Role r = RoleJson.parse("x", "{\"produces\": [{\"item\": \"minecraft:apple\"}]}");
        assertEquals(1, r.produces().get(0).count());
    }

    @Test
    void rejectsNonObject() {
        assertThrows(IllegalArgumentException.class, () -> RoleJson.parse("x", "[]"));
        assertThrows(IllegalArgumentException.class, () -> RoleJson.parse("x", "7"));
    }

    @Test
    void rejectsMissingItem() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> RoleJson.parse("x", "{\"produces\": [{\"count\": 1}]}"));
        assertTrue(ex.getMessage().contains("produces[0]"), ex.getMessage());
    }

    @Test
    void rejectsNonPositiveCount() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> RoleJson.parse("x", "{\"consumes\": [{\"item\": \"minecraft:wheat\", \"count\": 0}]}"));
        assertTrue(ex.getMessage().contains("consumes[0]"), ex.getMessage());
    }

    @Test
    void rejectsNonArrayItemList() {
        assertThrows(IllegalArgumentException.class,
                () -> RoleJson.parse("x", "{\"produces\": {\"item\": \"minecraft:apple\"}}"));
    }
}
