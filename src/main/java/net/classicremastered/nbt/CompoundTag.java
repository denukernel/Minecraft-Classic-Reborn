package net.classicremastered.nbt;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class CompoundTag extends Tag {
    private final Map<String, Tag> value = new HashMap<>();

    public CompoundTag(String name) {
        super(name);
    }

    @Override
    public byte getId() {
        return 10;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        for (Tag tag : value.values()) {
            Tag.writeNamedTag(tag, out);
        }
        out.writeByte(0); // TAG_End ID
    }

    @Override
    public void read(DataInput in) throws IOException {
        value.clear();
        Tag tag;
        while ((tag = Tag.readNamedTag(in)).getId() != 0) {
            value.put(tag.getName(), tag);
        }
    }

    public Collection<Tag> getAllTags() {
        return value.values();
    }

    public void put(Tag tag) {
        value.put(tag.getName(), tag);
    }

    public void putByte(String name, byte b) {
        value.put(name, new ByteTag(name, b));
    }

    public void putShort(String name, short s) {
        value.put(name, new ShortTag(name, s));
    }

    public void putInt(String name, int i) {
        value.put(name, new IntTag(name, i));
    }

    public void putLong(String name, long l) {
        value.put(name, new LongTag(name, l));
    }

    public void putFloat(String name, float f) {
        value.put(name, new FloatTag(name, f));
    }

    public void putDouble(String name, double d) {
        value.put(name, new DoubleTag(name, d));
    }

    public void putByteArray(String name, byte[] bytes) {
        value.put(name, new ByteArrayTag(name, bytes));
    }

    public void putString(String name, String s) {
        value.put(name, new StringTag(name, s));
    }

    public void putBoolean(String name, boolean b) {
        putByte(name, (byte) (b ? 1 : 0));
    }

    public boolean hasKey(String name) {
        return value.containsKey(name);
    }

    public Tag get(String name) {
        return value.get(name);
    }

    public byte getByte(String name) {
        if (!value.containsKey(name)) return 0;
        return ((ByteTag) value.get(name)).value;
    }

    public short getShort(String name) {
        if (!value.containsKey(name)) return 0;
        return ((ShortTag) value.get(name)).value;
    }

    public int getInt(String name) {
        if (!value.containsKey(name)) return 0;
        return ((IntTag) value.get(name)).value;
    }

    public long getLong(String name) {
        if (!value.containsKey(name)) return 0;
        return ((LongTag) value.get(name)).value;
    }

    public float getFloat(String name) {
        if (!value.containsKey(name)) return 0.0f;
        return ((FloatTag) value.get(name)).value;
    }

    public double getDouble(String name) {
        if (!value.containsKey(name)) return 0.0;
        return ((DoubleTag) value.get(name)).value;
    }

    public byte[] getByteArray(String name) {
        if (!value.containsKey(name)) return new byte[0];
        return ((ByteArrayTag) value.get(name)).value;
    }

    public String getString(String name) {
        if (!value.containsKey(name)) return "";
        return ((StringTag) value.get(name)).value;
    }

    public boolean getBoolean(String name) {
        return getByte(name) != 0;
    }

    public CompoundTag getCompound(String name) {
        if (!value.containsKey(name)) return new CompoundTag(name);
        return (CompoundTag) value.get(name);
    }

    public ListTag getList(String name) {
        if (!value.containsKey(name)) return new ListTag(name);
        return (ListTag) value.get(name);
    }

    @Override
    public String toString() {
        return "" + value.size() + " entries";
    }

    @Override
    public boolean equals(Object obj) {
        if (super.equals(obj)) {
            CompoundTag o = (CompoundTag) obj;
            return value.equals(o.value);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ value.hashCode();
    }
}
