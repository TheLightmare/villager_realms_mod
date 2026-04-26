package com.lightmare.villagerrealms.core.persist.store;

import com.lightmare.villagerrealms.core.persist.migration.Migration;
import com.lightmare.villagerrealms.core.persist.migration.MigrationChain;
import com.lightmare.villagerrealms.core.persist.orm.BlobFormat;
import com.lightmare.villagerrealms.core.persist.orm.Codec;
import com.lightmare.villagerrealms.core.persist.orm.Codecs;
import com.lightmare.villagerrealms.core.persist.orm.VersionedFormat;
import com.lightmare.villagerrealms.core.record.NPCRecord;
import com.lightmare.villagerrealms.core.record.NPCRecordCodec;
import com.lightmare.villagerrealms.core.record.NPCRecordV1Codec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class NPCShardFormat {

    public static final int CURRENT_VERSION = 2;

    public static final MigrationChain CHAIN =
            new MigrationChain("NPCShard", CURRENT_VERSION)
                    .register(new V1ToV2());

    public static final VersionedFormat<List<NPCRecord>> FORMAT =
            new VersionedFormat<>(BlobFormat.MAGIC_NPC_SHARD,
                    Codecs.list(NPCRecordCodec.NPC), CHAIN);

    private NPCShardFormat() {}

    /**
     * v1 -&gt; v2: NPCRecord gained {@code factionId} and {@link
     * com.lightmare.villagerrealms.core.record.Relationships} gained a
     * {@code factionOpinions} map. The frozen v1 codec already returns a
     * record with {@code NO_FACTION} and an empty factionOpinions map; we
     * just bump dataVersion and re-encode.
     */
    private static final class V1ToV2 implements Migration {

        private static final Codec<List<NPCRecord>> V1_LIST =
                Codecs.list(NPCRecordV1Codec.NPC_V1);
        private static final Codec<List<NPCRecord>> V2_LIST =
                Codecs.list(NPCRecordCodec.NPC);

        @Override public int fromVersion() { return 1; }

        @Override
        public byte[] migrate(byte[] payload) throws IOException {
            List<NPCRecord> oldRecords = V1_LIST.fromBytes(payload);
            List<NPCRecord> upgraded = new ArrayList<>(oldRecords.size());
            for (NPCRecord r : oldRecords) {
                upgraded.add(new NPCRecord(
                        NPCRecord.CURRENT_VERSION,
                        r.identity(), r.location(), r.vitals(),
                        r.inventory(), r.economy(), r.role(),
                        NPCRecord.NO_FACTION,
                        r.relationships(), r.memory(), r.action()));
            }
            return V2_LIST.toBytes(upgraded);
        }
    }
}
