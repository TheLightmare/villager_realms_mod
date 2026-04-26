package com.lightmare.villagerrealms.core.record;

import com.lightmare.villagerrealms.core.persist.orm.Codec;
import com.lightmare.villagerrealms.core.persist.orm.Codecs;
import com.lightmare.villagerrealms.core.persist.orm.RecordCodec;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class FactionRecordCodec {

    private FactionRecordCodec() {}

    private static final Codec<Set<String>> STRING_SET = new Codec<>() {
        @Override
        public void write(com.lightmare.villagerrealms.core.persist.orm.BinaryWriter out, Set<String> v)
                throws java.io.IOException {
            out.writeVarInt(v.size());
            for (String s : v) out.writeString(s);
        }

        @Override
        public Set<String> read(com.lightmare.villagerrealms.core.persist.orm.BinaryReader in)
                throws java.io.IOException {
            int n = in.readVarInt();
            if (n < 0) throw new java.io.IOException("negative set size: " + n);
            Set<String> set = new HashSet<>(Math.max(16, n * 2));
            for (int i = 0; i < n; i++) set.add(in.readString());
            return set;
        }
    };

    public static final Codec<Faction> FACTION = RecordCodec.<Faction>builder()
            .field(Codecs.VARINT, Faction::dataVersion)
            .field(Codecs.STRING, Faction::id)
            .field(Codecs.STRING, Faction::name)
            .field(Codecs.nullable(Codecs.UUID_), Faction::leaderUuid)
            .field(STRING_SET, Faction::claimedVillageIds)
            .build(a -> new Faction(
                    (Integer) a[0],
                    (String) a[1],
                    (String) a[2],
                    (UUID) a[3],
                    (Set<String>) a[4]));

    public static final Codec<FactionRelations> RELATIONS = RecordCodec.<FactionRelations>builder()
            .field(Codecs.map(Codecs.STRING, Codecs.map(Codecs.STRING, Codecs.VARINT)),
                    FactionRelations::by)
            .build(a -> new FactionRelations((Map<String, Map<String, Integer>>) a[0]));

    public static final Codec<FactionStoreSnapshot> SNAPSHOT = RecordCodec.<FactionStoreSnapshot>builder()
            .field(Codecs.list(FACTION), FactionStoreSnapshot::factions)
            .field(RELATIONS, FactionStoreSnapshot::relations)
            .build(a -> new FactionStoreSnapshot(
                    (List<Faction>) a[0],
                    (FactionRelations) a[1]));
}
