package net.classicremastered.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class ShortTag extends Tag {
    public short value;

    public ShortTag(String name) {
        super(name);
    }

    public ShortTag(String name, short value) {
        super(name);
        this.value = value;
    }

    @Override
    public byte getId() {
        return 2;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeShort(value);
    }

    @Override
    public void read(DataInput in) throws IOException {
        value = in.readShort();
    }

    @Override
    public String toString() {
        return "" + value;
    }

    @Override
    public boolean equals(Object obj) {
        if (super.equals(obj)) {
            ShortTag o = (ShortTag) obj;
            return value == o.value;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ value;
    }
}
