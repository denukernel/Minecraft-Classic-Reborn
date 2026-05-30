package net.classicremastered.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;

public class IntArrayTag extends Tag {
    public int[] value;

    public IntArrayTag(String name) {
        super(name);
    }

    public IntArrayTag(String name, int[] value) {
        super(name);
        this.value = value;
    }

    @Override
    public byte getId() {
        return 11;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        if (value == null) {
            out.writeInt(0);
        } else {
            out.writeInt(value.length);
            for (int val : value) {
                out.writeInt(val);
            }
        }
    }

    @Override
    public void read(DataInput in) throws IOException {
        int length = in.readInt();
        value = new int[length];
        for (int i = 0; i < length; i++) {
            value[i] = in.readInt();
        }
    }

    @Override
    public String toString() {
        return "[" + (value == null ? 0 : value.length) + " ints]";
    }

    @Override
    public boolean equals(Object obj) {
        if (super.equals(obj)) {
            IntArrayTag o = (IntArrayTag) obj;
            return Arrays.equals(value, o.value);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ Arrays.hashCode(value);
    }
}
