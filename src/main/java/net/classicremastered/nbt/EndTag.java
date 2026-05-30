package net.classicremastered.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class EndTag extends Tag {

    public EndTag() {
        super("");
    }

    @Override
    public byte getId() {
        return 0;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        // EndTag has no payload
    }

    @Override
    public void read(DataInput in) throws IOException {
        // EndTag has no payload
    }

    @Override
    public String toString() {
        return "END";
    }
}
