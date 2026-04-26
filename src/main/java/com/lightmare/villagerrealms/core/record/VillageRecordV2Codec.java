package com.lightmare.villagerrealms.core.record;

import com.lightmare.villagerrealms.core.persist.orm.Codec;
import com.lightmare.villagerrealms.core.persist.orm.Codecs;
import com.lightmare.villagerrealms.core.persist.orm.RecordCodec;

import java.util.List;

/**
 * Frozen v2 reader for VillageRecord. v2 records had no faction ownership —
 * we keep this codec around so the v2 -&gt; v3 migration in {@link
 * com.lightmare.villagerrealms.core.persist.store.VillageStoreFormat} can
 * decode old payloads and re-encode them with {@code factionOwnerId = null}.
 *
 * Do not extend this codec when adding new fields. Snapshot a fresh frozen
 * codec at each schema bump.
 */
public final class VillageRecordV2Codec {

    private VillageRecordV2Codec() {}

    public static final Codec<VillageRecord> VILLAGE_V2 = RecordCodec.<VillageRecord>builder()
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
            .field(Codecs.list(VillageRecordCodec.BED), VillageRecord::beds)
            .field(Codecs.list(VillageRecordCodec.WORKSTATION), VillageRecord::workstations)
            .field(VillageRecordCodec.MARKET, VillageRecord::market)
            .build(a -> new VillageRecord(
                    (Integer) a[0], (String) a[1], (String) a[2],
                    (Integer) a[3], (Integer) a[4], (Integer) a[5],
                    (Integer) a[6], (Integer) a[7], (Integer) a[8],
                    (Boolean) a[9],
                    (List<VillageBed>) a[10],
                    (List<VillageWorkstation>) a[11],
                    (VillageMarket) a[12],
                    null));
}
