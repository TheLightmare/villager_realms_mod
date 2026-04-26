package com.lightmare.villagerrealms.core.persist.store;

import com.lightmare.villagerrealms.core.persist.orm.BinaryWriter;
import com.lightmare.villagerrealms.core.persist.orm.BlobFormat;
import com.lightmare.villagerrealms.core.persist.orm.Codec;
import com.lightmare.villagerrealms.core.persist.orm.Codecs;
import com.lightmare.villagerrealms.core.record.ActionState;
import com.lightmare.villagerrealms.core.record.EconomicState;
import com.lightmare.villagerrealms.core.record.Gender;
import com.lightmare.villagerrealms.core.record.Identity;
import com.lightmare.villagerrealms.core.record.Location;
import com.lightmare.villagerrealms.core.record.MemoryLog;
import com.lightmare.villagerrealms.core.record.NPCInventory;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import com.lightmare.villagerrealms.core.record.NPCRecordV1Codec;
import com.lightmare.villagerrealms.core.record.Relationships;
import com.lightmare.villagerrealms.core.record.RoleState;
import com.lightmare.villagerrealms.core.record.Tier;
import com.lightmare.villagerrealms.core.record.Traits;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NPCShardMigrationTest {

    @Test
    void v1PayloadUpgradedToV2WithNoFactionAndEmptyFactionOpinions() throws Exception {
        UUID id = UUID.randomUUID();
        UUID friend = UUID.randomUUID();

        // Build a v1-shape record. Note: factionId/factionOpinions don't exist
        // in the v1 wire format — the v1 codec hardcodes them on decode.
        NPCRecord v1Rec = new NPCRecord(
                1,
                new Identity(id, "Alice", 30, Gender.OTHER, Traits.NEUTRAL),
                new Location("v1", 0, 64, 0, "minecraft:overworld", Tier.ACTIVE),
                com.lightmare.villagerrealms.core.record.Vitals.FRESH,
                NPCInventory.EMPTY,
                EconomicState.ZERO,
                new RoleState("laborer", null, 0L),
                NPCRecord.NO_FACTION,
                new Relationships(Map.of(), Map.of(friend, 25)),
                MemoryLog.empty(),
                ActionState.IDLE);

        Codec<List<NPCRecord>> v1List = Codecs.list(NPCRecordV1Codec.NPC_V1);
        byte[] payload = v1List.toBytes(List.of(v1Rec));

        // [magic int32][version=1 varint][payload bytes...]
        ByteArrayOutputStream headerOut = new ByteArrayOutputStream();
        try (var w = new BinaryWriter(headerOut)) {
            w.writeInt(BlobFormat.MAGIC_NPC_SHARD);
            w.writeVarInt(1);
        }
        byte[] header = headerOut.toByteArray();
        byte[] blob = new byte[header.length + payload.length];
        System.arraycopy(header, 0, blob, 0, header.length);
        System.arraycopy(payload, 0, blob, header.length, payload.length);

        List<NPCRecord> upgraded = NPCShardFormat.FORMAT.fromBytes(blob);
        assertEquals(1, upgraded.size());
        NPCRecord r = upgraded.get(0);
        assertEquals(NPCRecord.CURRENT_VERSION, r.dataVersion());
        assertEquals(NPCRecord.NO_FACTION, r.factionId());
        assertTrue(r.relationships().factionOpinions().isEmpty());
        // Sparse UUID opinion preserved across migration.
        assertEquals(Integer.valueOf(25),
                r.relationships().opinions().get(friend));
    }
}
