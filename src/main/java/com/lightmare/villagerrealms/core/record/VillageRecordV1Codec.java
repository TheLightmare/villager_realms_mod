package com.lightmare.villagerrealms.core.record;

import com.lightmare.villagerrealms.core.persist.orm.Codec;
import com.lightmare.villagerrealms.core.persist.orm.Codecs;
import com.lightmare.villagerrealms.core.persist.orm.RecordCodec;

import java.util.List;

/**
 * Frozen v1 reader for VillageRecord. v1 records had no market field — we
 * keep this codec around so the v1 -&gt; v2 migration in {@link
 * com.lightmare.villagerrealms.core.persist.store.VillageStoreFormat} can
 * decode old payloads and re-encode them with an empty market attached.
 *
 * Do not extend this codec when adding new fields. Future schema changes
 * follow the same pattern: snapshot the codec before the change, register
 * a new migration step.
 */
public final class VillageRecordV1Codec {

    private VillageRecordV1Codec() {}

    public static final Codec<VillageRecord> VILLAGE_V1 = RecordCodec.<VillageRecord>builder()
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
            .build(a -> new VillageRecord(
                    (Integer) a[0], (String) a[1], (String) a[2],
                    (Integer) a[3], (Integer) a[4], (Integer) a[5],
                    (Integer) a[6], (Integer) a[7], (Integer) a[8],
                    (Boolean) a[9],
                    (List<VillageBed>) a[10],
                    (List<VillageWorkstation>) a[11],
                    VillageMarket.EMPTY,
                    null));
}
