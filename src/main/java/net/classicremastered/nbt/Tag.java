package net.classicremastered.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public abstract class Tag {
    private String name;

    protected Tag(String name) {
        if (name == null) {
            this.name = "";
        } else {
            this.name = name;
        }
    }

    public abstract byte getId();

    public abstract void write(DataOutput out) throws IOException;

    public abstract void read(DataInput in) throws IOException;

    public String getName() {
        return name;
    }

    public Tag setName(String name) {
        if (name == null) {
            this.name = "";
        } else {
            this.name = name;
        }
        return this;
    }

    public static Tag readNamedTag(DataInput in) throws IOException {
        byte id = in.readByte();
        if (id == 0) {
            return new EndTag();
        }
        String name = in.readUTF();
        Tag tag = newTag(id, name);
        tag.read(in);
        return tag;
    }

    public static void writeNamedTag(Tag tag, DataOutput out) throws IOException {
        out.writeByte(tag.getId());
        if (tag.getId() == 0) {
            return;
        }
        out.writeUTF(tag.getName());
        tag.write(out);
    }

    public static Tag newTag(byte id, String name) {
        switch (id) {
            case 0: return new EndTag();
            case 1: return new ByteTag(name);
            case 2: return new ShortTag(name);
            case 3: return new IntTag(name);
            case 4: return new LongTag(name);
            case 5: return new FloatTag(name);
            case 6: return new DoubleTag(name);
            case 7: return new ByteArrayTag(name);
            case 8: return new StringTag(name);
            case 9: return new ListTag(name);
            case 10: return new CompoundTag(name);
            case 11: return new IntArrayTag(name);
            case 12: return new LongArrayTag(name);
            default: throw new IllegalArgumentException("Invalid NBT tag ID: " + id);
        }
    }

    public static String getTagName(byte id) {
        switch (id) {
            case 0: return "TAG_End";
            case 1: return "TAG_Byte";
            case 2: return "TAG_Short";
            case 3: return "TAG_Int";
            case 4: return "TAG_Long";
            case 5: return "TAG_Float";
            case 6: return "TAG_Double";
            case 7: return "TAG_Byte_Array";
            case 8: return "TAG_String";
            case 9: return "TAG_List";
            case 10: return "TAG_Compound";
            case 11: return "TAG_Int_Array";
            case 12: return "TAG_Long_Array";
            default: return "UNKNOWN";
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !getClass().equals(obj.getClass())) {
            return false;
        }
        Tag other = (Tag) obj;
        return name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode() ^ getId();
    }
}
