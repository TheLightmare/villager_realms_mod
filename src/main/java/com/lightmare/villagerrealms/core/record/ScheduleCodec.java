package com.lightmare.villagerrealms.core.record;

import com.lightmare.villagerrealms.core.persist.orm.Codec;
import com.lightmare.villagerrealms.core.persist.orm.Codecs;
import com.lightmare.villagerrealms.core.persist.orm.RecordCodec;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ScheduleCodec {

    private ScheduleCodec() {}

    public static final Codec<ScheduledEvent> EVENT = RecordCodec.<ScheduledEvent>builder()
            .field(Codecs.UUID_, ScheduledEvent::id)
            .field(Codecs.VARLONG, ScheduledEvent::dueTick)
            .field(Codecs.STRING, ScheduledEvent::kind)
            .field(Codecs.nullable(Codecs.STRING), ScheduledEvent::villageId)
            .field(Codecs.nullable(Codecs.UUID_), ScheduledEvent::subject)
            .field(Codecs.map(Codecs.STRING, Codecs.STRING), ScheduledEvent::data)
            .build(a -> new ScheduledEvent(
                    (UUID) a[0], (Long) a[1], (String) a[2],
                    (String) a[3], (UUID) a[4], (Map<String, String>) a[5]));

    public static final Codec<List<ScheduledEvent>> EVENTS = Codecs.list(EVENT);
}
