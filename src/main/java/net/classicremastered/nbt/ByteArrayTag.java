package net.classicremastered.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;

public class ByteArrayTag extends Tag {
    public byte[] value;

    public ByteArrayTag(String name) {
        super(name);
    }

    public ByteArrayTag(String name, byte[] value) {
        super(name);
        this.value = value;
    }

    @Override
    public byte getId() {
        return 7;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        if (value == null) {
            out.writeInt(0);
        } else {
            out.writeInt(value.length);
            out.write(value);
        }
    }

    @Override
    public void read(DataInput in) throws IOException {
        int length = in.readInt();
        value = new byte[length];
        in.readFully(value);
    }

    @Override
    public String toString() {
        return "[" + (value == null ? 0 : value.length) + " bytes]";
    }

    @Override
    public boolean equals(Object obj) {
        if (super.equals(obj)) {
            ByteArrayTag o = (ByteArrayTag) obj;
            return Arrays.equals(value, o.value);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ Arrays.hashCode(value);
    }
}
