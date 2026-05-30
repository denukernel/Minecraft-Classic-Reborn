package net.classicremastered.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class LongTag extends Tag {
    public long value;

    public LongTag(String name) {
        super(name);
    }

    public LongTag(String name, long value) {
        super(name);
        this.value = value;
    }

    @Override
    public byte getId() {
        return 4;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeLong(value);
    }

    @Override
    public void read(DataInput in) throws IOException {
        value = in.readLong();
    }

    @Override
    public String toString() {
        return "" + value;
    }

    @Override
    public boolean equals(Object obj) {
        if (super.equals(obj)) {
            LongTag o = (LongTag) obj;
            return value == o.value;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ (int) (value ^ (value >>> 32));
    }
}
