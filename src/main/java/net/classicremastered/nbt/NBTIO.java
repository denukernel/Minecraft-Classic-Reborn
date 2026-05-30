package net.classicremastered.nbt;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class NBTIO {

    public static CompoundTag readCompressed(InputStream in) throws IOException {
        try (DataInputStream dis = new DataInputStream(new GZIPInputStream(in))) {
            return read(dis);
        }
    }

    public static void writeCompressed(CompoundTag tag, OutputStream out) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(new GZIPOutputStream(out))) {
            write(tag, dos);
        }
    }

    public static CompoundTag read(DataInput in) throws IOException {
        Tag tag = Tag.readNamedTag(in);
        if (tag instanceof CompoundTag) {
            return (CompoundTag) tag;
        }
        throw new IOException("Root tag is not a CompoundTag");
    }

    public static void write(CompoundTag tag, DataOutput out) throws IOException {
        Tag.writeNamedTag(tag, out);
    }
}
