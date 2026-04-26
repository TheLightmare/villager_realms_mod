package com.lightmare.villagerrealms.core.persist.orm;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public interface Codec<T> {

    void write(BinaryWriter out, T value) throws IOException;

    T read(BinaryReader in) throws IOException;

    default byte[] toBytes(T value) throws IOException {
        try (var bos = new ByteArrayOutputStream(); var w = new BinaryWriter(bos)) {
            write(w, value);
            w.flush();
            return bos.toByteArray();
        }
    }

    default T fromBytes(byte[] bytes) throws IOException {
        try (var bis = new ByteArrayInputStream(bytes); var r = new BinaryReader(bis)) {
            return read(r);
        }
    }
}
