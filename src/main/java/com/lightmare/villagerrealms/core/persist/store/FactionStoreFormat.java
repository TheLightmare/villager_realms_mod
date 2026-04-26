package com.lightmare.villagerrealms.core.persist.store;

import com.lightmare.villagerrealms.core.persist.migration.MigrationChain;
import com.lightmare.villagerrealms.core.persist.orm.BlobFormat;
import com.lightmare.villagerrealms.core.persist.orm.VersionedFormat;
import com.lightmare.villagerrealms.core.record.FactionRecordCodec;
import com.lightmare.villagerrealms.core.record.FactionStoreSnapshot;

public final class FactionStoreFormat {

    public static final int CURRENT_VERSION = 1;

    public static final MigrationChain CHAIN =
            new MigrationChain("FactionStore", CURRENT_VERSION);

    public static final VersionedFormat<FactionStoreSnapshot> FORMAT =
            new VersionedFormat<>(BlobFormat.MAGIC_FACTION_STORE,
                    FactionRecordCodec.SNAPSHOT, CHAIN);

    private FactionStoreFormat() {}
}
