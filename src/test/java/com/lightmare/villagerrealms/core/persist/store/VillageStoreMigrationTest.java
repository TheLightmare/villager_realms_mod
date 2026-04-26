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
import com.lightmare.villagerrealms.core.record.VillageWorkstation;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class VillageStoreMigrationTest {

    @Test
    void v1PayloadUpgradedToV2WithEmptyMarket() throws Exception {
        VillageRecord v1Rec = new VillageRecord(
                1, "village_a", "minecraft:overworld",
                0, 60, 0, 16, 70, 16, true,
                List.of(new VillageBed(1, 64, 1, "farmer", UUID.randomUUID())),
                List.of(new VillageWorkstation(2, 64, 2, "farmer")),
                VillageMarket.EMPTY,
                null);

        Codec<List<VillageRecord>> v1List = Codecs.list(VillageRecordV1Codec.VILLAGE_V1);
        byte[] payload = v1List.toBytes(List.of(v1Rec));

        // Build a v1 blob: [magic int32][version=1 varint][payload bytes...]
        ByteArrayOutputStream headerOut = new ByteArrayOutputStream();
        try (var w = new BinaryWriter(headerOut)) {
            w.writeInt(BlobFormat.MAGIC_VILLAGE_STORE);
            w.writeVarInt(1);
        }
        byte[] header = headerOut.toByteArray();
        byte[] blob = new byte[header.length + payload.length];
        System.arraycopy(header, 0, blob, 0, header.length);
        System.arraycopy(payload, 0, blob, header.length, payload.length);

        List<VillageRecord> upgraded = VillageStoreFormat.FORMAT.fromBytes(blob);
        assertNotNull(upgraded);
        assertEquals(1, upgraded.size());
        VillageRecord r = upgraded.get(0);
        assertEquals(VillageRecord.CURRENT_VERSION, r.dataVersion());
        assertEquals("village_a", r.villageId());
        assertEquals(VillageMarket.EMPTY, r.market());
        assertNull(r.factionOwnerId(), "v1 records pre-date factions; null on upgrade");
    }

    @Test
    void v2PayloadUpgradedToV3WithNullFactionOwner() throws Exception {
        VillageRecord v2Rec = new VillageRecord(
                2, "village_b", "minecraft:overworld",
                0, 60, 0, 16, 70, 16, true,
                List.of(new VillageBed(1, 64, 1, "baker", UUID.randomUUID())),
                List.of(new VillageWorkstation(2, 64, 2, "baker")),
                VillageMarket.EMPTY,
                null);

        Codec<List<VillageRecord>> v2List = Codecs.list(VillageRecordV2Codec.VILLAGE_V2);
        byte[] payload = v2List.toBytes(List.of(v2Rec));

        ByteArrayOutputStream headerOut = new ByteArrayOutputStream();
        try (var w = new BinaryWriter(headerOut)) {
            w.writeInt(BlobFormat.MAGIC_VILLAGE_STORE);
            w.writeVarInt(2);
        }
        byte[] header = headerOut.toByteArray();
        byte[] blob = new byte[header.length + payload.length];
        System.arraycopy(header, 0, blob, 0, header.length);
        System.arraycopy(payload, 0, blob, header.length, payload.length);

        List<VillageRecord> upgraded = VillageStoreFormat.FORMAT.fromBytes(blob);
        assertEquals(1, upgraded.size());
        VillageRecord r = upgraded.get(0);
        assertEquals(VillageRecord.CURRENT_VERSION, r.dataVersion());
        assertEquals("village_b", r.villageId());
        assertNull(r.factionOwnerId(), "v2 records had no faction ownership");
    }
}
