package com.lightmare.villagerrealms.core.record;

import com.lightmare.villagerrealms.core.persist.orm.Codec;
import com.lightmare.villagerrealms.core.persist.orm.Codecs;
import com.lightmare.villagerrealms.core.persist.orm.RecordCodec;

import java.util.Map;
import java.util.UUID;

/**
 * Frozen v1 reader for {@link NPCRecord}. v1 had:
 *
 * <ul>
 *   <li>no {@code factionId} field on NPCRecord</li>
 *   <li>{@code Relationships} held a single {@code Map<UUID,Integer>}; no
 *       per-NPC faction-opinion overrides existed yet</li>
 * </ul>
 *
 * The migration in {@link
 * com.lightmare.villagerrealms.core.persist.store.NPCShardFormat} uses this
 * codec to decode old payloads, then sets {@code factionId =
 * NPCRecord.NO_FACTION} and an empty {@code factionOpinions} map before
 * re-encoding with the current codec.
 *
 * Do not extend this codec when adding new fields. Snapshot a fresh frozen
 * codec at each schema bump.
 */
public final class NPCRecordV1Codec {

    private NPCRecordV1Codec() {}

    public static final Codec<Relationships> RELATIONSHIPS_V1 = RecordCodec.<Relationships>builder()
            .field(Codecs.map(Codecs.UUID_, Codecs.VARINT), Relationships::opinions)
            .build(a -> new Relationships(Map.of(), (Map<UUID, Integer>) a[0]));

    public static final Codec<NPCRecord> NPC_V1 = RecordCodec.<NPCRecord>builder()
            .field(Codecs.VARINT, NPCRecord::dataVersion)
            .field(NPCRecordCodec.IDENTITY, NPCRecord::identity)
            .field(NPCRecordCodec.LOCATION, NPCRecord::location)
            .field(NPCRecordCodec.VITALS, NPCRecord::vitals)
            .field(NPCRecordCodec.INVENTORY, NPCRecord::inventory)
            .field(NPCRecordCodec.ECONOMY, NPCRecord::economy)
            .field(NPCRecordCodec.ROLE, NPCRecord::role)
            .field(RELATIONSHIPS_V1, NPCRecord::relationships)
            .field(NPCRecordCodec.MEMORY, NPCRecord::memory)
            .field(NPCRecordCodec.ACTION, NPCRecord::action)
            .build(a -> new NPCRecord(
                    (Integer) a[0],
                    (Identity) a[1],
                    (Location) a[2],
                    (Vitals) a[3],
                    (NPCInventory) a[4],
                    (EconomicState) a[5],
                    (RoleState) a[6],
                    NPCRecord.NO_FACTION,
                    (Relationships) a[7],
                    (MemoryLog) a[8],
                    (ActionState) a[9]));
}
