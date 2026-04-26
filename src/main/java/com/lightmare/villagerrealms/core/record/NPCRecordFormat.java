package com.lightmare.villagerrealms.core.record;

import com.lightmare.villagerrealms.core.persist.migration.MigrationChain;
import com.lightmare.villagerrealms.core.persist.orm.BlobFormat;
import com.lightmare.villagerrealms.core.persist.orm.VersionedFormat;

public final class NPCRecordFormat {

    public static final MigrationChain CHAIN =
            new MigrationChain("NPCRecord", NPCRecord.CURRENT_VERSION);

    public static final VersionedFormat<NPCRecord> FORMAT =
            new VersionedFormat<>(BlobFormat.MAGIC_NPC_RECORD, NPCRecordCodec.NPC, CHAIN);

    private NPCRecordFormat() {}
}
