package com.lightmare.villagerrealms.core.persist.orm;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public final class BinaryWriter implements AutoCloseable {

    private final DataOutputStream out;

    public BinaryWriter(OutputStream out) {
        this.out = new DataOutputStream(out);
    }

    public void writeByte(int v) throws IOException { out.writeByte(v); }
    public void writeInt(int v) throws IOException { out.writeInt(v); }
    public void writeLong(long v) throws IOException { out.writeLong(v); }
    public void writeFloat(float v) throws IOException { out.writeFloat(v); }
    public void writeDouble(double v) throws IOException { out.writeDouble(v); }
    public void writeBool(boolean v) throws IOException { out.writeBoolean(v); }

    public void writeBytes(byte[] bytes) throws IOException {
        writeVarInt(bytes.length);
        out.write(bytes);
    }

    public void writeString(String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(bytes.length);
        out.write(bytes);
    }

    public void writeVarInt(int v) throws IOException {
        while ((v & ~0x7F) != 0) {
            out.writeByte((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.writeByte(v);
    }

    public void writeVarLong(long v) throws IOException {
        while ((v & ~0x7FL) != 0) {
            out.writeByte((int) ((v & 0x7FL) | 0x80L));
            v >>>= 7;
        }
        out.writeByte((int) v);
    }

    public void flush() throws IOException { out.flush(); }

    @Override public void close() throws IOException { out.close(); }
}
