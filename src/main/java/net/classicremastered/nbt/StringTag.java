package net.classicremastered.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class StringTag extends Tag {
    public String value;

    public StringTag(String name) {
        super(name);
    }

    public StringTag(String name, String value) {
        super(name);
        this.value = value == null ? "" : value;
    }

    @Override
    public byte getId() {
        return 8;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeUTF(value == null ? "" : value);
    }

    @Override
    public void read(DataInput in) throws IOException {
        value = in.readUTF();
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object obj) {
        if (super.equals(obj)) {
            StringTag o = (StringTag) obj;
            return (value == null && o.value == null) || (value != null && value.equals(o.value));
        }
        return false;
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ (value == null ? 0 : value.hashCode());
    }
}
