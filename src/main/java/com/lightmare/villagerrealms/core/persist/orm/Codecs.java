package com.lightmare.villagerrealms.core.persist.orm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntFunction;

public final class Codecs {

    private Codecs() {}

    public static final Codec<Integer> VARINT = new Codec<>() {
        @Override public void write(BinaryWriter out, Integer v) throws IOException { out.writeVarInt(v); }
        @Override public Integer read(BinaryReader in) throws IOException { return in.readVarInt(); }
    };

    public static final Codec<Long> VARLONG = new Codec<>() {
        @Override public void write(BinaryWriter out, Long v) throws IOException { out.writeVarLong(v); }
        @Override public Long read(BinaryReader in) throws IOException { return in.readVarLong(); }
    };

    public static final Codec<Integer> INT = new Codec<>() {
        @Override public void write(BinaryWriter out, Integer v) throws IOException { out.writeInt(v); }
        @Override public Integer read(BinaryReader in) throws IOException { return in.readInt(); }
    };

    public static final Codec<Long> LONG = new Codec<>() {
        @Override public void write(BinaryWriter out, Long v) throws IOException { out.writeLong(v); }
        @Override public Long read(BinaryReader in) throws IOException { return in.readLong(); }
    };

    public static final Codec<Float> FLOAT = new Codec<>() {
        @Override public void write(BinaryWriter out, Float v) throws IOException { out.writeFloat(v); }
        @Override public Float read(BinaryReader in) throws IOException { return in.readFloat(); }
    };

    public static final Codec<Double> DOUBLE = new Codec<>() {
        @Override public void write(BinaryWriter out, Double v) throws IOException { out.writeDouble(v); }
        @Override public Double read(BinaryReader in) throws IOException { return in.readDouble(); }
    };

    public static final Codec<Boolean> BOOL = new Codec<>() {
        @Override public void write(BinaryWriter out, Boolean v) throws IOException { out.writeBool(v); }
        @Override public Boolean read(BinaryReader in) throws IOException { return in.readBool(); }
    };

    public static final Codec<String> STRING = new Codec<>() {
        @Override public void write(BinaryWriter out, String v) throws IOException { out.writeString(v); }
        @Override public String read(BinaryReader in) throws IOException { return in.readString(); }
    };

    public static final Codec<UUID> UUID_ = new Codec<>() {
        @Override public void write(BinaryWriter out, UUID v) throws IOException {
            out.writeLong(v.getMostSignificantBits());
            out.writeLong(v.getLeastSignificantBits());
        }
        @Override public UUID read(BinaryReader in) throws IOException {
            long hi = in.readLong();
            long lo = in.readLong();
            return new UUID(hi, lo);
        }
    };

    public static <T> Codec<List<T>> list(Codec<T> elem) {
        return new Codec<>() {
            @Override public void write(BinaryWriter out, List<T> v) throws IOException {
                out.writeVarInt(v.size());
                for (T t : v) elem.write(out, t);
            }
            @Override public List<T> read(BinaryReader in) throws IOException {
                int n = in.readVarInt();
                if (n < 0) throw new IOException("negative list length: " + n);
                var list = new ArrayList<T>(Math.min(n, 1024));
                for (int i = 0; i < n; i++) list.add(elem.read(in));
                return list;
            }
        };
    }

    public static <K, V> Codec<Map<K, V>> map(Codec<K> kc, Codec<V> vc) {
        return new Codec<>() {
            @Override public void write(BinaryWriter out, Map<K, V> v) throws IOException {
                out.writeVarInt(v.size());
                for (var e : v.entrySet()) {
                    kc.write(out, e.getKey());
                    vc.write(out, e.getValue());
                }
            }
            @Override public Map<K, V> read(BinaryReader in) throws IOException {
                int n = in.readVarInt();
                if (n < 0) throw new IOException("negative map size: " + n);
                var m = new LinkedHashMap<K, V>(Math.max(16, n * 2));
                for (int i = 0; i < n; i++) {
                    K k = kc.read(in);
                    V vv = vc.read(in);
                    m.put(k, vv);
                }
                return m;
            }
        };
    }

    public static <T> Codec<T> nullable(Codec<T> codec) {
        return new Codec<>() {
            @Override public void write(BinaryWriter out, T v) throws IOException {
                if (v == null) {
                    out.writeBool(false);
                } else {
                    out.writeBool(true);
                    codec.write(out, v);
                }
            }
            @Override public T read(BinaryReader in) throws IOException {
                return in.readBool() ? codec.read(in) : null;
            }
        };
    }

    public static <E extends Enum<E>> Codec<E> enumByOrdinal(IntFunction<E> resolver) {
        return new Codec<>() {
            @Override public void write(BinaryWriter out, E v) throws IOException {
                out.writeVarInt(v.ordinal());
            }
            @Override public E read(BinaryReader in) throws IOException {
                return resolver.apply(in.readVarInt());
            }
        };
    }
}
