package com.lightmare.villagerrealms.core.record;

import com.lightmare.villagerrealms.core.persist.orm.Codec;
import com.lightmare.villagerrealms.core.persist.orm.Codecs;
import com.lightmare.villagerrealms.core.persist.orm.RecordCodec;

public final class NPCRecordCodec {

    private NPCRecordCodec() {}

    public static final Codec<Traits> TRAITS = RecordCodec.<Traits>builder()
            .field(Codecs.FLOAT, Traits::aggression)
            .field(Codecs.FLOAT, Traits::ambition)
            .field(Codecs.FLOAT, Traits::diligence)
            .field(Codecs.FLOAT, Traits::empathy)
            .field(Codecs.FLOAT, Traits::gregariousness)
            .field(Codecs.FLOAT, Traits::thrift)
            .build(a -> new Traits(
                    (Float) a[0], (Float) a[1], (Float) a[2],
                    (Float) a[3], (Float) a[4], (Float) a[5]));

    public static final Codec<Identity> IDENTITY = RecordCodec.<Identity>builder()
            .field(Codecs.UUID_, Identity::uuid)
            .field(Codecs.STRING, Identity::name)
            .field(Codecs.VARINT, Identity::age)
            .field(Codecs.enumByOrdinal(Gender::byOrdinal), Identity::gender)
            .field(TRAITS, Identity::traits)
            .build(a -> new Identity(
                    (java.util.UUID) a[0], (String) a[1], (Integer) a[2],
                    (Gender) a[3], (Traits) a[4]));

    public static final Codec<Location> LOCATION = RecordCodec.<Location>builder()
            .field(Codecs.STRING, Location::homeVillageId)
            .field(Codecs.DOUBLE, Location::x)
            .field(Codecs.DOUBLE, Location::y)
            .field(Codecs.DOUBLE, Location::z)
            .field(Codecs.STRING, Location::dimension)
            .field(Codecs.enumByOrdinal(Tier::byOrdinal), Location::tier)
            .build(a -> new Location(
                    (String) a[0], (Double) a[1], (Double) a[2], (Double) a[3],
                    (String) a[4], (Tier) a[5]));

    public static final Codec<Vitals> VITALS = RecordCodec.<Vitals>builder()
            .field(Codecs.FLOAT, Vitals::health)
            .field(Codecs.FLOAT, Vitals::hunger)
            .field(Codecs.FLOAT, Vitals::energy)
            .field(Codecs.FLOAT, Vitals::mood)
            .build(a -> new Vitals((Float) a[0], (Float) a[1], (Float) a[2], (Float) a[3]));

    public static final Codec<ItemEntry> ITEM_ENTRY = RecordCodec.<ItemEntry>builder()
            .field(Codecs.STRING, ItemEntry::itemId)
            .field(Codecs.VARINT, ItemEntry::count)
            .field(Codecs.enumByOrdinal(Provenance::byOrdinal), ItemEntry::provenance)
            .field(Codecs.VARLONG, ItemEntry::acquiredAtTick)
            .build(a -> new ItemEntry(
                    (String) a[0], (Integer) a[1], (Provenance) a[2], (Long) a[3]));

    public static final Codec<NPCInventory> INVENTORY = RecordCodec.<NPCInventory>builder()
            .field(Codecs.list(ITEM_ENTRY), NPCInventory::items)
            .build(a -> new NPCInventory((java.util.List<ItemEntry>) a[0]));

    public static final Codec<Debt> DEBT = RecordCodec.<Debt>builder()
            .field(Codecs.UUID_, Debt::creditor)
            .field(Codecs.VARLONG, Debt::amount)
            .field(Codecs.VARLONG, Debt::incurredAtTick)
            .build(a -> new Debt((java.util.UUID) a[0], (Long) a[1], (Long) a[2]));

    public static final Codec<PropertyRef> PROPERTY_REF = RecordCodec.<PropertyRef>builder()
            .field(Codecs.STRING, PropertyRef::villageId)
            .field(Codecs.STRING, PropertyRef::key)
            .build(a -> new PropertyRef((String) a[0], (String) a[1]));

    public static final Codec<EconomicState> ECONOMY = RecordCodec.<EconomicState>builder()
            .field(Codecs.VARLONG, EconomicState::gold)
            .field(Codecs.list(DEBT), EconomicState::debts)
            .field(Codecs.list(PROPERTY_REF), EconomicState::ownedProperty)
            .build(a -> new EconomicState(
                    (Long) a[0],
                    (java.util.List<Debt>) a[1],
                    (java.util.List<PropertyRef>) a[2]));

    public static final Codec<RoleState> ROLE = RecordCodec.<RoleState>builder()
            .field(Codecs.STRING, RoleState::roleId)
            .field(Codecs.nullable(Codecs.STRING), RoleState::workplaceRef)
            .field(Codecs.VARLONG, RoleState::scheduleOffsetTicks)
            .build(a -> new RoleState((String) a[0], (String) a[1], (Long) a[2]));

    public static final Codec<Relationships> RELATIONSHIPS = RecordCodec.<Relationships>builder()
            .field(Codecs.map(Codecs.STRING, Codecs.VARINT), Relationships::factionOpinions)
            .field(Codecs.map(Codecs.UUID_, Codecs.VARINT), Relationships::opinions)
            .build(a -> new Relationships(
                    (java.util.Map<String, Integer>) a[0],
                    (java.util.Map<java.util.UUID, Integer>) a[1]));

    public static final Codec<MemoryEvent> MEMORY_EVENT = RecordCodec.<MemoryEvent>builder()
            .field(Codecs.VARLONG, MemoryEvent::tick)
            .field(Codecs.STRING, MemoryEvent::kind)
            .field(Codecs.nullable(Codecs.STRING), MemoryEvent::subjectRef)
            .field(Codecs.nullable(Codecs.STRING), MemoryEvent::detail)
            .build(a -> new MemoryEvent((Long) a[0], (String) a[1], (String) a[2], (String) a[3]));

    public static final Codec<MemoryLog> MEMORY = RecordCodec.<MemoryLog>builder()
            .field(Codecs.VARINT, MemoryLog::capacity)
            .field(Codecs.list(MEMORY_EVENT), MemoryLog::events)
            .build(a -> new MemoryLog((Integer) a[0], (java.util.List<MemoryEvent>) a[1]));

    public static final Codec<ActionState> ACTION = RecordCodec.<ActionState>builder()
            .field(Codecs.STRING, ActionState::actionId)
            .field(Codecs.VARINT, ActionState::subStep)
            .field(Codecs.VARLONG, ActionState::startedAtTick)
            .field(Codecs.STRING, ActionState::checkpointToken)
            .build(a -> new ActionState((String) a[0], (Integer) a[1], (Long) a[2], (String) a[3]));

    public static final Codec<NPCRecord> NPC = RecordCodec.<NPCRecord>builder()
            .field(Codecs.VARINT, NPCRecord::dataVersion)
            .field(IDENTITY, NPCRecord::identity)
            .field(LOCATION, NPCRecord::location)
            .field(VITALS, NPCRecord::vitals)
            .field(INVENTORY, NPCRecord::inventory)
            .field(ECONOMY, NPCRecord::economy)
            .field(ROLE, NPCRecord::role)
            .field(Codecs.STRING, NPCRecord::factionId)
            .field(RELATIONSHIPS, NPCRecord::relationships)
            .field(MEMORY, NPCRecord::memory)
            .field(ACTION, NPCRecord::action)
            .build(a -> new NPCRecord(
                    (Integer) a[0],
                    (Identity) a[1],
                    (Location) a[2],
                    (Vitals) a[3],
                    (NPCInventory) a[4],
                    (EconomicState) a[5],
                    (RoleState) a[6],
                    (String) a[7],
                    (Relationships) a[8],
                    (MemoryLog) a[9],
                    (ActionState) a[10]));
}
