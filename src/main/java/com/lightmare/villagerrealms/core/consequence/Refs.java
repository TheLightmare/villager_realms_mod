package com.lightmare.villagerrealms.core.consequence;

import java.util.UUID;

/**
 * Subject references used in memory events, death records and the faction
 * matrix. A ref names something an NPC can hold an opinion about without
 * that thing having to be an NPC: players in particular are not factions,
 * so a faction's standing with a player is stored as a matrix row against
 * the player's ref ({@code player:<uuid>}).
 *
 * <ul>
 *   <li>{@code player:<uuid>} — a player.</li>
 *   <li>{@code npc:<uuid>} — another NPC.</li>
 *   <li>{@code entity:<type id>} — a non-NPC mob (no uuid: mobs are not persistent actors).</li>
 * </ul>
 */
public final class Refs {

    public static final String PLAYER_PREFIX = "player:";
    public static final String NPC_PREFIX = "npc:";
    public static final String ENTITY_PREFIX = "entity:";

    private Refs() {}

    public static String player(UUID uuid) { return PLAYER_PREFIX + uuid; }
    public static String npc(UUID uuid) { return NPC_PREFIX + uuid; }
    public static String entity(String typeId) { return ENTITY_PREFIX + typeId; }

    public static boolean isPlayer(String ref) { return ref != null && ref.startsWith(PLAYER_PREFIX); }
    public static boolean isNpc(String ref) { return ref != null && ref.startsWith(NPC_PREFIX); }

    /** The uuid behind a player or npc ref; null for anything else or malformed. */
    public static UUID uuidOf(String ref) {
        if (ref == null) return null;
        String s;
        if (ref.startsWith(PLAYER_PREFIX)) s = ref.substring(PLAYER_PREFIX.length());
        else if (ref.startsWith(NPC_PREFIX)) s = ref.substring(NPC_PREFIX.length());
        else return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
