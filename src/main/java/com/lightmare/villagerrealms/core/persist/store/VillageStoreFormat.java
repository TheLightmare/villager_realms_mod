package com.lightmare.villagerrealms.core.persist.store;

import com.lightmare.villagerrealms.core.persist.migration.Migration;
import com.lightmare.villagerrealms.core.persist.migration.MigrationChain;
import com.lightmare.villagerrealms.core.persist.orm.BlobFormat;
import com.lightmare.villagerrealms.core.persist.orm.Codec;
import com.lightmare.villagerrealms.core.persist.orm.Codecs;
import com.lightmare.villagerrealms.core.persist.orm.VersionedFormat;
import com.lightmare.villagerrealms.core.record.VillageMarket;
import com.lightmare.villagerrealms.core.record.VillageRecord;
import com.lightmare.villagerrealms.core.record.VillageRecordCodec;
import com.lightmare.villagerrealms.core.record.VillageRecordV1Codec;
import com.lightmare.villagerrealms.core.record.VillageRecordV2Codec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class VillageStoreFormat {

    public static final int CURRENT_VERSION = 3;

    public static final MigrationChain CHAIN =
            new MigrationChain("VillageStore", CURRENT_VERSION)
                    .register(new V1ToV2())
                    .register(new V2ToV3());

    public static final VersionedFormat<List<VillageRecord>> FORMAT =
            new VersionedFormat<>(BlobFormat.MAGIC_VILLAGE_STORE,
                    Codecs.list(VillageRecordCodec.VILLAGE), CHAIN);

    private VillageStoreFormat() {}

    /**
     * v1 -&gt; v2: VillageRecord gained a market field. Decode with the frozen
     * v1 codec (which substitutes {@link VillageMarket#EMPTY}), bump each
     * record's dataVersion, then re-encode with the v2-shape codec.
     */
    private static final class V1ToV2 implements Migration {

        private static final Codec<List<VillageRecord>> V1_LIST =
                Codecs.list(VillageRecordV1Codec.VILLAGE_V1);
        private static final Codec<List<VillageRecord>> V2_LIST =
                Codecs.list(VillageRecordV2Codec.VILLAGE_V2);

        @Override public int fromVersion() { return 1; }

        @Override
        public byte[] migrate(byte[] payload) throws IOException {
            List<VillageRecord> oldRecords = V1_LIST.fromBytes(payload);
            List<VillageRecord> upgraded = new ArrayList<>(oldRecords.size());
            for (VillageRecord r : oldRecords) {
                upgraded.add(new VillageRecord(
                        2,
                        r.villageId(), r.dimension(),
                        r.minX(), r.minY(), r.minZ(),
                        r.maxX(), r.maxY(), r.maxZ(),
                        r.audited(),
                        r.beds(), r.workstations(),
                        VillageMarket.EMPTY,
                        null));
            }
            return V2_LIST.toBytes(upgraded);
        }
    }

    /**
     * v2 -&gt; v3: VillageRecord gained {@code factionOwnerId}. v2 codec
     * already returns records with {@code factionOwnerId = null}; we just
     * bump dataVersion and re-encode with the current codec.
     */
    private static final class V2ToV3 implements Migration {

        private static final Codec<List<VillageRecord>> V2_LIST =
                Codecs.list(VillageRecordV2Codec.VILLAGE_V2);
        private static final Codec<List<VillageRecord>> V3_LIST =
                Codecs.list(VillageRecordCodec.VILLAGE);

        @Override public int fromVersion() { return 2; }

        @Override
        public byte[] migrate(byte[] payload) throws IOException {
            List<VillageRecord> oldRecords = V2_LIST.fromBytes(payload);
            List<VillageRecord> upgraded = new ArrayList<>(oldRecords.size());
            for (VillageRecord r : oldRecords) {
                upgraded.add(new VillageRecord(
                        VillageRecord.CURRENT_VERSION,
                        r.villageId(), r.dimension(),
                        r.minX(), r.minY(), r.minZ(),
                        r.maxX(), r.maxY(), r.maxZ(),
                        r.audited(),
                        r.beds(), r.workstations(),
                        r.market(),
                        null));
            }
            return V3_LIST.toBytes(upgraded);
        }
    }
}
