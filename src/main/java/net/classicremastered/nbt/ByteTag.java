package net.classicremastered.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class ByteTag extends Tag {
    public byte value;

    public ByteTag(String name) {
        super(name);
    }

    public ByteTag(String name, byte value) {
        super(name);
        this.value = value;
    }

    @Override
    public byte getId() {
        return 1;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeByte(value);
    }

    @Override
    public void read(DataInput in) throws IOException {
        value = in.readByte();
    }

    @Override
    public String toString() {
        return "" + value;
    }

    @Override
    public boolean equals(Object obj) {
        if (super.equals(obj)) {
            ByteTag o = (ByteTag) obj;
            return value == o.value;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ value;
    }
}
