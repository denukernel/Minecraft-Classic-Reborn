package net.classicremastered.minecraft.model;

import java.io.*;
import java.nio.*;
import java.util.*;

public class MD3Model {
    public int numFrames;
    public int numSurfaces;
    public List<MD3Surface> surfaces = new ArrayList<>();
    
    public static MD3Model load(InputStream in) throws IOException {
        if (in == null) throw new IOException("MD3Model.load: InputStream is null");
        byte[] bytes = in.readAllBytes();
        // DO NOT close here; caller owns the stream
        // in.close();

        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        byte[] id = new byte[4];
        buf.get(id);
        if (!new String(id, java.nio.charset.StandardCharsets.US_ASCII).equals("IDP3")) {
            throw new IOException("Not an MD3 model (bad magic)");
        }

        int version = buf.getInt();
        if (version != 15) throw new IOException("Unsupported MD3 version: " + version);

        byte[] nameBytes = new byte[64];
        buf.get(nameBytes);
        // stop at first NUL to avoid embedded garbage
        int n = 0; while (n < nameBytes.length && nameBytes[n] != 0) n++;
        String name = new String(nameBytes, 0, n, java.nio.charset.StandardCharsets.US_ASCII);

        buf.getInt(); // flags

        int numFrames = buf.getInt();
        int numTags   = buf.getInt();
        int numSurfs  = buf.getInt();
        int numSkins  = buf.getInt();

        int ofsFrames = buf.getInt();
        int ofsTags   = buf.getInt();
        int ofsSurfs  = buf.getInt();
        int ofsEnd    = buf.getInt();

        MD3Model model = new MD3Model();
        model.numFrames = numFrames;
        model.numSurfaces = numSurfs;

        buf.position(ofsSurfs);
        for (int i=0; i<numSurfs; i++) {
            MD3Surface surf = MD3Surface.read(buf);
            model.surfaces.add(surf);
            buf.position(buf.position() + (surf.ofsEnd - surf.size));
        }

        return model;
    }


}
