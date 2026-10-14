package com.lightmare.villagerrealms.core.persist.store;

import com.lightmare.villagerrealms.core.persist.migration.MigrationChain;
import com.lightmare.villagerrealms.core.persist.orm.BlobFormat;
import com.lightmare.villagerrealms.core.persist.orm.VersionedFormat;
import com.lightmare.villagerrealms.core.record.ScheduleCodec;
import com.lightmare.villagerrealms.core.record.ScheduledEvent;

import java.util.List;

public final class ScheduleStoreFormat {

    public static final int CURRENT_VERSION = 1;

    public static final MigrationChain CHAIN =
            new MigrationChain("ScheduleStore", CURRENT_VERSION);

    public static final VersionedFormat<List<ScheduledEvent>> FORMAT =
            new VersionedFormat<>(BlobFormat.MAGIC_SCHEDULE_STORE, ScheduleCodec.EVENTS, CHAIN);

    private ScheduleStoreFormat() {}
}
