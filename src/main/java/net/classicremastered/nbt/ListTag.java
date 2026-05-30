package net.classicremastered.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ListTag extends Tag {
    public List<Tag> list = new ArrayList<>();
    public byte type;

    public ListTag(String name) {
        super(name);
    }

    public ListTag(String name, byte type) {
        super(name);
        this.type = type;
    }

    @Override
    public byte getId() {
        return 9;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        if (list == null || list.isEmpty()) {
            out.writeByte(type != 0 ? type : 0);
            out.writeInt(0);
        } else {
            type = list.get(0).getId();
            out.writeByte(type);
            out.writeInt(list.size());
            for (Tag tag : list) {
                tag.write(out);
            }
        }
    }

    @Override
    public void read(DataInput in) throws IOException {
        type = in.readByte();
        int size = in.readInt();
        list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Tag tag = Tag.newTag(type, null);
            tag.read(in);
            list.add(tag);
        }
    }

    public void add(Tag tag) {
        if (list.isEmpty()) {
            type = tag.getId();
        } else if (tag.getId() != type) {
            throw new IllegalArgumentException("Tag type mismatch in ListTag. Expected: " + type + ", got: " + tag.getId());
        }
        list.add(tag);
    }

    public int size() {
        return list.size();
    }

    public Tag get(int index) {
        return list.get(index);
    }

    @Override
    public String toString() {
        return "" + list.size() + " entries of type " + Tag.getTagName(type);
    }

    @Override
    public boolean equals(Object obj) {
        if (super.equals(obj)) {
            ListTag o = (ListTag) obj;
            return type == o.type && list.equals(o.list);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ list.hashCode() ^ type;
    }
}
