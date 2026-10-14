package com.lightmare.villagerrealms.core.record;

import com.lightmare.villagerrealms.core.persist.orm.Codec;
import com.lightmare.villagerrealms.core.persist.orm.Codecs;
import com.lightmare.villagerrealms.core.persist.orm.RecordCodec;

import java.util.List;

/**
 * Frozen v4 reader for VillageRecord. v4 beds had no reservation field —
 * kept so the v4 -&gt; v5 migration in {@link
 * com.lightmare.villagerrealms.core.persist.store.VillageStoreFormat} can
 * decode old payloads with {@link VillageRecordCodec#BED_LEGACY}.
 *
 * Do not extend this codec when adding new fields. Snapshot a fresh frozen
 * codec at each schema bump.
 */
public final class VillageRecordV4Codec {

    private VillageRecordV4Codec() {}

    public static final Codec<VillageRecord> VILLAGE_V4 = RecordCodec.<VillageRecord>builder()
            .field(Codecs.VARINT, VillageRecord::dataVersion)
            .field(Codecs.STRING, VillageRecord::villageId)
            .field(Codecs.STRING, VillageRecord::dimension)
            .field(Codecs.INT, VillageRecord::minX)
            .field(Codecs.INT, VillageRecord::minY)
            .field(Codecs.INT, VillageRecord::minZ)
            .field(Codecs.INT, VillageRecord::maxX)
            .field(Codecs.INT, VillageRecord::maxY)
            .field(Codecs.INT, VillageRecord::maxZ)
            .field(Codecs.BOOL, VillageRecord::audited)
            .field(Codecs.list(VillageRecordCodec.BED_LEGACY), VillageRecord::beds)
            .field(Codecs.list(VillageRecordCodec.WORKSTATION), VillageRecord::workstations)
            .field(VillageRecordCodec.MARKET, VillageRecord::market)
            .field(Codecs.nullable(Codecs.STRING), VillageRecord::factionOwnerId)
            .field(Codecs.list(VillageRecordCodec.DEATH), VillageRecord::deaths)
            .build(a -> new VillageRecord(
                    (Integer) a[0], (String) a[1], (String) a[2],
                    (Integer) a[3], (Integer) a[4], (Integer) a[5],
                    (Integer) a[6], (Integer) a[7], (Integer) a[8],
                    (Boolean) a[9],
                    (List<VillageBed>) a[10],
                    (List<VillageWorkstation>) a[11],
                    (VillageMarket) a[12],
                    (String) a[13],
                    (List<DeathRecord>) a[14]));
}
