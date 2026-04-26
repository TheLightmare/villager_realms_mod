package com.lightmare.villagerrealms.core.persist.orm;

import com.lightmare.villagerrealms.core.persist.migration.MigrationChain;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Wraps a current-schema {@link Codec} with a magic-tagged, version-tagged blob format
 * and applies the registered {@link MigrationChain} to bring older payloads up to current
 * before decoding.
 *
 * Wire layout: [magic int32][dataVersion varint][payload bytes...]
 * The payload is whatever the inner codec produced.
 */
public final class VersionedFormat<T> {

    private final int magic;
    private final Codec<T> currentCodec;
    private final MigrationChain chain;

    public VersionedFormat(int magic, Codec<T> currentCodec, MigrationChain chain) {
        this.magic = magic;
        this.currentCodec = currentCodec;
        this.chain = chain;
    }

    public byte[] toBytes(T value) throws IOException {
        try (var bos = new ByteArrayOutputStream(); var w = new BinaryWriter(bos)) {
            w.writeInt(magic);
            w.writeVarInt(chain.currentVersion());
            currentCodec.write(w, value);
            w.flush();
            return bos.toByteArray();
        }
    }

    public T fromBytes(byte[] blob) throws IOException {
        int headerLen;
        int version;
        try (var bis = new ByteArrayInputStream(blob); var r = new BinaryReader(bis)) {
            int m = r.readInt();
            if (m != magic) {
                throw new IOException("Bad magic for " + chain.typeName()
                        + ": expected 0x" + Integer.toHexString(magic)
                        + " got 0x" + Integer.toHexString(m));
            }
            version = r.readVarInt();
            headerLen = blob.length - bis.available();
        }

        byte[] payload = new byte[blob.length - headerLen];
        System.arraycopy(blob, headerLen, payload, 0, payload.length);

        byte[] migrated = chain.upgrade(version, payload);

        try (var bis = new ByteArrayInputStream(migrated); var r = new BinaryReader(bis)) {
            return currentCodec.read(r);
        }
    }
}
