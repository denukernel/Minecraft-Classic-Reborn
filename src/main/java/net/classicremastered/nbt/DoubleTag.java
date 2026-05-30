package net.classicremastered.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class DoubleTag extends Tag {
    public double value;

    public DoubleTag(String name) {
        super(name);
    }

    public DoubleTag(String name, double value) {
        super(name);
        this.value = value;
    }

    @Override
    public byte getId() {
        return 6;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeDouble(value);
    }

    @Override
    public void read(DataInput in) throws IOException {
        value = in.readDouble();
    }

    @Override
    public String toString() {
        return "" + value;
    }

    @Override
    public boolean equals(Object obj) {
        if (super.equals(obj)) {
            DoubleTag o = (DoubleTag) obj;
            return value == o.value;
        }
        return false;
    }

    @Override
    public int hashCode() {
        long temp = Double.doubleToLongBits(value);
        return super.hashCode() ^ (int) (temp ^ (temp >>> 32));
    }
}
