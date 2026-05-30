package net.classicremastered.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class IntTag extends Tag {
    public int value;

    public IntTag(String name) {
        super(name);
    }

    public IntTag(String name, int value) {
        super(name);
        this.value = value;
    }

    @Override
    public byte getId() {
        return 3;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeInt(value);
    }

    @Override
    public void read(DataInput in) throws IOException {
        value = in.readInt();
    }

    @Override
    public String toString() {
        return "" + value;
    }

    @Override
    public boolean equals(Object obj) {
        if (super.equals(obj)) {
            IntTag o = (IntTag) obj;
            return value == o.value;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ value;
    }
}
