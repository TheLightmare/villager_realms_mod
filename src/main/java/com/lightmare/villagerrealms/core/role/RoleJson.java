package com.lightmare.villagerrealms.core.role;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Role JSON format, one file per role at
 * {@code data/<namespace>/villager_realms/roles/<id>.json}:
 *
 * <pre>
 * {
 *   "worker": true,                                       // default false
 *   "consumes": [{"item": "minecraft:wheat", "count": 1}], // default []
 *   "produces": [{"item": "minecraft:bread", "count": 1}], // default []
 *   "workstation": "baker"                                // default "" (no station → village centre)
 * }
 * </pre>
 *
 * The role id is the file name (no namespace). Pure parsing — no Minecraft
 * types — so it is unit-testable in isolation; the server-side loader only
 * does file discovery and hands each element here.
 */
public final class RoleJson {

    private RoleJson() {}

    public static Role parse(String id, String json) {
        return parse(id, JsonParser.parseString(json));
    }

    public static Role parse(String id, JsonElement json) {
        if (id == null || id.isEmpty()) throw new IllegalArgumentException("role id required");
        if (json == null || !json.isJsonObject()) {
            throw new IllegalArgumentException("role '" + id + "': expected a JSON object");
        }
        JsonObject o = json.getAsJsonObject();
        boolean worker = o.has("worker") && o.get("worker").getAsBoolean();
        List<RoleItemSpec> consumes = items(id, o, "consumes");
        List<RoleItemSpec> produces = items(id, o, "produces");
        String workstation = o.has("workstation") && !o.get("workstation").isJsonNull()
                ? o.get("workstation").getAsString()
                : "";
        return new Role(id, worker, consumes, produces, workstation);
    }

    private static List<RoleItemSpec> items(String roleId, JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) return List.of();
        JsonElement el = o.get(key);
        if (!el.isJsonArray()) {
            throw new IllegalArgumentException("role '" + roleId + "': '" + key + "' must be an array");
        }
        JsonArray arr = el.getAsJsonArray();
        List<RoleItemSpec> out = new ArrayList<>(arr.size());
        for (int i = 0; i < arr.size(); i++) {
            JsonElement e = arr.get(i);
            if (!e.isJsonObject()) {
                throw new IllegalArgumentException(
                        "role '" + roleId + "': '" + key + "[" + i + "]' must be an object");
            }
            JsonObject spec = e.getAsJsonObject();
            if (!spec.has("item")) {
                throw new IllegalArgumentException(
                        "role '" + roleId + "': '" + key + "[" + i + "]' is missing 'item'");
            }
            String item = spec.get("item").getAsString();
            int count = spec.has("count") ? spec.get("count").getAsInt() : 1;
            try {
                out.add(new RoleItemSpec(item, count));
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException(
                        "role '" + roleId + "': '" + key + "[" + i + "]': " + ex.getMessage(), ex);
            }
        }
        return out;
    }
}
