package net.classicremastered.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class FloatTag extends Tag {
    public float value;

    public FloatTag(String name) {
        super(name);
    }

    public FloatTag(String name, float value) {
        super(name);
        this.value = value;
    }

    @Override
    public byte getId() {
        return 5;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeFloat(value);
    }

    @Override
    public void read(DataInput in) throws IOException {
        value = in.readFloat();
    }

    @Override
    public String toString() {
        return "" + value;
    }

    @Override
    public boolean equals(Object obj) {
        if (super.equals(obj)) {
            FloatTag o = (FloatTag) obj;
            return value == o.value;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ Float.floatToIntBits(value);
    }
}
