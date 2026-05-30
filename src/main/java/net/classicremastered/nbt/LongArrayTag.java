package net.classicremastered.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;

public class LongArrayTag extends Tag {
    public long[] value;

    public LongArrayTag(String name) {
        super(name);
    }

    public LongArrayTag(String name, long[] value) {
        super(name);
        this.value = value;
    }

    @Override
    public byte getId() {
        return 12;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        if (value == null) {
            out.writeInt(0);
        } else {
            out.writeInt(value.length);
            for (long val : value) {
                out.writeLong(val);
            }
        }
    }

    @Override
    public void read(DataInput in) throws IOException {
        int length = in.readInt();
        value = new long[length];
        for (int i = 0; i < length; i++) {
            value[i] = in.readLong();
        }
    }

    @Override
    public String toString() {
        return "[" + (value == null ? 0 : value.length) + " longs]";
    }

    @Override
    public boolean equals(Object obj) {
        if (super.equals(obj)) {
            LongArrayTag o = (LongArrayTag) obj;
            return Arrays.equals(value, o.value);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ Arrays.hashCode(value);
    }
}
