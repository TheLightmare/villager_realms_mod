package com.lightmare.villagerrealms.core.persist.orm;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class BinaryReader implements AutoCloseable {

    private final DataInputStream in;

    public BinaryReader(InputStream in) {
        this.in = new DataInputStream(in);
    }

    public int readByte() throws IOException { return in.readByte(); }
    public int readUnsignedByte() throws IOException { return in.readUnsignedByte(); }
    public int readInt() throws IOException { return in.readInt(); }
    public long readLong() throws IOException { return in.readLong(); }
    public float readFloat() throws IOException { return in.readFloat(); }
    public double readDouble() throws IOException { return in.readDouble(); }
    public boolean readBool() throws IOException { return in.readBoolean(); }

    public byte[] readBytes() throws IOException {
        int len = readVarInt();
        if (len < 0) throw new IOException("negative byte-array length: " + len);
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return bytes;
    }

    public String readString() throws IOException {
        int len = readVarInt();
        if (len < 0) throw new IOException("negative string length: " + len);
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public int readVarInt() throws IOException {
        int result = 0;
        int shift = 0;
        while (true) {
            int b = in.readUnsignedByte();
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return result;
            shift += 7;
            if (shift >= 35) throw new IOException("VarInt too long");
        }
    }

    public long readVarLong() throws IOException {
        long result = 0L;
        int shift = 0;
        while (true) {
            int b = in.readUnsignedByte();
            result |= ((long) (b & 0x7F)) << shift;
            if ((b & 0x80) == 0) return result;
            shift += 7;
            if (shift >= 70) throw new IOException("VarLong too long");
        }
    }

    @Override public void close() throws IOException { in.close(); }
}
