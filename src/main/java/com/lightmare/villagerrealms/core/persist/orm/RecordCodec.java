package com.lightmare.villagerrealms.core.persist.orm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class RecordCodec<T> implements Codec<T> {

    @FunctionalInterface
    public interface Constructor<T> {
        T apply(Object[] args);
    }

    private record Field<T, F>(Codec<F> codec, Function<T, F> getter) {
        @SuppressWarnings("unchecked")
        void write(BinaryWriter out, T value) throws IOException {
            ((Codec<Object>) codec).write(out, getter.apply(value));
        }
    }

    private final List<Field<T, ?>> fields;
    private final Constructor<T> constructor;

    private RecordCodec(List<Field<T, ?>> fields, Constructor<T> constructor) {
        this.fields = fields;
        this.constructor = constructor;
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    @Override
    public void write(BinaryWriter out, T value) throws IOException {
        for (var f : fields) f.write(out, value);
    }

    @Override
    public T read(BinaryReader in) throws IOException {
        var args = new Object[fields.size()];
        for (int i = 0; i < fields.size(); i++) {
            args[i] = fields.get(i).codec.read(in);
        }
        return constructor.apply(args);
    }

    public static final class Builder<T> {
        private final List<Field<T, ?>> fields = new ArrayList<>();

        public <F> Builder<T> field(Codec<F> codec, Function<T, F> getter) {
            fields.add(new Field<>(codec, getter));
            return this;
        }

        public Codec<T> build(Constructor<T> ctor) {
            return new RecordCodec<>(List.copyOf(fields), ctor);
        }
    }
}
