package com.lightmare.villagerrealms.core.record;

import com.lightmare.villagerrealms.core.persist.orm.Codec;
import com.lightmare.villagerrealms.core.persist.orm.Codecs;
import com.lightmare.villagerrealms.core.persist.orm.RecordCodec;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class VillageRecordCodec {

    private VillageRecordCodec() {}

    /** Bed shape through VillageRecord v4 (no reservation). Used by the frozen v1–v4 codecs only. */
    public static final Codec<VillageBed> BED_LEGACY = RecordCodec.<VillageBed>builder()
            .field(Codecs.INT, VillageBed::x)
            .field(Codecs.INT, VillageBed::y)
            .field(Codecs.INT, VillageBed::z)
            .field(Codecs.STRING, VillageBed::role)
            .field(Codecs.nullable(Codecs.UUID_), VillageBed::occupant)
            .build(a -> new VillageBed(
                    (Integer) a[0], (Integer) a[1], (Integer) a[2],
                    (String) a[3], (UUID) a[4], null));

    public static final Codec<VillageBed> BED = RecordCodec.<VillageBed>builder()
            .field(Codecs.INT, VillageBed::x)
            .field(Codecs.INT, VillageBed::y)
            .field(Codecs.INT, VillageBed::z)
            .field(Codecs.STRING, VillageBed::role)
            .field(Codecs.nullable(Codecs.UUID_), VillageBed::occupant)
            .field(Codecs.nullable(Codecs.UUID_), VillageBed::reservedFor)
            .build(a -> new VillageBed(
                    (Integer) a[0], (Integer) a[1], (Integer) a[2],
                    (String) a[3], (UUID) a[4], (UUID) a[5]));

    public static final Codec<VillageWorkstation> WORKSTATION = RecordCodec.<VillageWorkstation>builder()
            .field(Codecs.INT, VillageWorkstation::x)
            .field(Codecs.INT, VillageWorkstation::y)
            .field(Codecs.INT, VillageWorkstation::z)
            .field(Codecs.STRING, VillageWorkstation::role)
            .build(a -> new VillageWorkstation(
                    (Integer) a[0], (Integer) a[1], (Integer) a[2], (String) a[3]));

    public static final Codec<VillageMarket> MARKET = RecordCodec.<VillageMarket>builder()
            .field(Codecs.map(Codecs.STRING, Codecs.VARINT), VillageMarket::stockpile)
            .field(Codecs.map(Codecs.STRING, Codecs.VARINT), VillageMarket::supply)
            .field(Codecs.map(Codecs.STRING, Codecs.VARINT), VillageMarket::demand)
            .field(Codecs.VARLONG, VillageMarket::goldLedger)
            .build(a -> new VillageMarket(
                    (Map<String, Integer>) a[0],
                    (Map<String, Integer>) a[1],
                    (Map<String, Integer>) a[2],
                    (Long) a[3]));

    public static final Codec<DeathRecord> DEATH = RecordCodec.<DeathRecord>builder()
            .field(Codecs.UUID_, DeathRecord::uuid)
            .field(Codecs.STRING, DeathRecord::name)
            .field(Codecs.STRING, DeathRecord::roleId)
            .field(Codecs.VARLONG, DeathRecord::tick)
            .field(Codecs.STRING, DeathRecord::cause)
            .field(Codecs.nullable(Codecs.STRING), DeathRecord::killerRef)
            .build(a -> new DeathRecord(
                    (UUID) a[0], (String) a[1], (String) a[2],
                    (Long) a[3], (String) a[4], (String) a[5]));

    public static final Codec<VillageRecord> VILLAGE = RecordCodec.<VillageRecord>builder()
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
            .field(Codecs.list(BED), VillageRecord::beds)
            .field(Codecs.list(WORKSTATION), VillageRecord::workstations)
            .field(MARKET, VillageRecord::market)
            .field(Codecs.nullable(Codecs.STRING), VillageRecord::factionOwnerId)
            .field(Codecs.list(DEATH), VillageRecord::deaths)
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
