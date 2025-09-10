package net.classicremastered.minecraft.model;

import java.io.InputStream;
import java.io.IOException;
import java.util.*;

public class MD3Cache {
    private static final Map<String, MD3Model> cache = new HashMap<>();

    public static MD3Model getModel(String path) {
        if (path == null || path.isEmpty()) {
            System.err.println("[MD3] getModel: empty path");
            return null;
        }
        MD3Model cached = cache.get(path);
        if (cached != null) return cached;

        // Try both with and without leading slash; both "models/foo.md3" and "/models/foo.md3"
        final String p1 = path.startsWith("/") ? path.substring(1) : path;
        final String[] candidates = new String[] {
            p1.startsWith("models/") ? p1 : "models/" + p1,
            (p1.startsWith("models/") ? "/" : "/") + (p1.startsWith("models/") ? p1 : "models/" + p1)
        };

        InputStream in = null;
        try {
            // 1) Context ClassLoader (works in shaded/fat JARs and many launchers)
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            for (String c : candidates) {
                if (in == null) in = cl.getResourceAsStream(stripLeadingSlash(c));
            }
            // 2) Fallback: this class' loader
            if (in == null) {
                for (String c : candidates) {
                    in = MD3Cache.class.getResourceAsStream(ensureLeadingSlash(c));
                    if (in != null) break;
                }
            }
            if (in == null) {
                System.err.println("[MD3] Could not find any of these resources on classpath:");
                for (String c : candidates) System.err.println("       - " + c);
                System.err.println("       Place your .md3 files under src/main/resources/models/ (Maven/Gradle).");
                return null;
            }

            try (InputStream closeMe = in) {
                MD3Model m = MD3Model.load(closeMe);
                cache.put(path, m);
                return m;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String stripLeadingSlash(String s) {
        return (s != null && s.startsWith("/")) ? s.substring(1) : s;
    }
    private static String ensureLeadingSlash(String s) {
        return (s != null && !s.startsWith("/")) ? "/" + s : s;
    }
}
