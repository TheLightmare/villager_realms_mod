package com.lightmare.villagerrealms.core.role;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The shipped datapack JSON and {@link RoleRegistry#defaults()} must agree,
 * so a world with the mod's own data pack behaves exactly like the code
 * fallback. If you change one, change the other.
 */
class RoleDataParityTest {

    private static final String DIR = "/data/villager_realms/villager_realms/roles/";

    @Test
    void everyDefaultRoleHasAnIdenticalJsonFile() throws IOException {
        for (Role expected : RoleRegistry.defaults()) {
            String path = DIR + expected.id() + ".json";
            try (InputStream in = RoleDataParityTest.class.getResourceAsStream(path)) {
                assertNotNull(in, "missing datapack file for role '" + expected.id() + "': " + path);
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                Role parsed = RoleJson.parse(expected.id(), json);
                assertEquals(expected, parsed, "datapack role '" + expected.id() + "' differs from the code default");
            }
        }
    }
}
