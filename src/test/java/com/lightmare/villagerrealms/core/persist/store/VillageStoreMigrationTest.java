package com.lightmare.villagerrealms.core.persist.store;

import com.lightmare.villagerrealms.core.persist.orm.BinaryWriter;
import com.lightmare.villagerrealms.core.persist.orm.BlobFormat;
import com.lightmare.villagerrealms.core.persist.orm.Codec;
import com.lightmare.villagerrealms.core.persist.orm.Codecs;
import com.lightmare.villagerrealms.core.record.VillageBed;
import com.lightmare.villagerrealms.core.record.VillageMarket;
import com.lightmare.villagerrealms.core.record.VillageRecord;
import com.lightmare.villagerrealms.core.record.VillageRecordV1Codec;
import com.lightmare.villagerrealms.core.record.VillageRecordV2Codec;
import com.lightmare.villagerrealms.core.record.VillageRecordV3Codec;
import com.lightmare.villagerrealms.core.record.VillageWorkstation;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillageStoreMigrationTest {

    private static VillageRecord record(int version, String id, String role, String factionOwner) {
        return new VillageRecord(
                version, id, "minecraft:overworld",
                0, 60, 0, 16, 70, 16, true,
                List.of(new VillageBed(1, 64, 1, role, UUID.randomUUID())),
                List.of(new VillageWorkstation(2, 64, 2, role)),
                VillageMarket.EMPTY,
                factionOwner,
                List.of());
    }

    /** Build a store blob: [magic int32][version varint][payload bytes...]. */
    private static byte[] blob(int version, byte[] payload) throws Exception {
        ByteArrayOutputStream headerOut = new ByteArrayOutputStream();
        try (var w = new BinaryWriter(headerOut)) {
            w.writeInt(BlobFormat.MAGIC_VILLAGE_STORE);
            w.writeVarInt(version);
        }
        byte[] header = headerOut.toByteArray();
        byte[] out = new byte[header.length + payload.length];
        System.arraycopy(header, 0, out, 0, header.length);
        System.arraycopy(payload, 0, out, header.length, payload.length);
        return out;
    }

    @Test
    void v1PayloadUpgradedToCurrentWithEmptyMarket() throws Exception {
        Codec<List<VillageRecord>> v1List = Codecs.list(VillageRecordV1Codec.VILLAGE_V1);
        byte[] payload = v1List.toBytes(List.of(record(1, "village_a", "farmer", null)));

        List<VillageRecord> upgraded = VillageStoreFormat.FORMAT.fromBytes(blob(1, payload));
        assertNotNull(upgraded);
        assertEquals(1, upgraded.size());
        VillageRecord r = upgraded.get(0);
        assertEquals(VillageRecord.CURRENT_VERSION, r.dataVersion());
        assertEquals("village_a", r.villageId());
        assertEquals(VillageMarket.EMPTY, r.market());
        assertNull(r.factionOwnerId(), "v1 records pre-date factions; null on upgrade");
        assertTrue(r.deaths().isEmpty(), "v1 records pre-date the death ledger");
    }

    @Test
    void v2PayloadUpgradedToCurrentWithNullFactionOwner() throws Exception {
        Codec<List<VillageRecord>> v2List = Codecs.list(VillageRecordV2Codec.VILLAGE_V2);
        byte[] payload = v2List.toBytes(List.of(record(2, "village_b", "baker", null)));

        List<VillageRecord> upgraded = VillageStoreFormat.FORMAT.fromBytes(blob(2, payload));
        assertEquals(1, upgraded.size());
        VillageRecord r = upgraded.get(0);
        assertEquals(VillageRecord.CURRENT_VERSION, r.dataVersion());
        assertEquals("village_b", r.villageId());
        assertNull(r.factionOwnerId(), "v2 records had no faction ownership");
        assertTrue(r.deaths().isEmpty());
    }

    @Test
    void v3PayloadUpgradedToV4KeepingFactionOwnerWithEmptyLedger() throws Exception {
        Codec<List<VillageRecord>> v3List = Codecs.list(VillageRecordV3Codec.VILLAGE_V3);
        byte[] payload = v3List.toBytes(List.of(record(3, "village_c", "butcher", "faction:village_c")));

        List<VillageRecord> upgraded = VillageStoreFormat.FORMAT.fromBytes(blob(3, payload));
        assertEquals(1, upgraded.size());
        VillageRecord r = upgraded.get(0);
        assertEquals(VillageRecord.CURRENT_VERSION, r.dataVersion());
        assertEquals("village_c", r.villageId());
        assertEquals("faction:village_c", r.factionOwnerId(), "v3 faction ownership must survive");
        assertEquals("butcher", r.beds().get(0).role());
        assertTrue(r.deaths().isEmpty(), "v3 records pre-date the death ledger");
    }
}
