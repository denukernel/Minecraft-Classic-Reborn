package com.mcraft.api.loader;

import com.mcraft.api.Mod;
import com.mcraft.api.ModContext;
import com.mcraft.api.ModInfo;
import com.mcraft.api.commands.CommandRegistry;
import com.mcraft.api.event.EventBus;
import com.mcraft.api.event.events.LifecycleEvents;
import com.mcraft.api.hooks.Hooks;
import com.mcraft.api.registry.BlockRegistryContext;
import com.mcraft.api.registry.EntityRegistryContext;
import com.mcraft.api.registry.ItemRegistryContext;
import com.mcraft.api.sound.SoundRegistryContext;

import net.classicremastered.minecraft.Minecraft;

import java.io.*;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Loads mods from /mods as jars or loose class folders, plus ServiceLoader + classlist.txt. */
public final class ModLoader {
    private static ModLoader INSTANCE;

    private static final int CURRENT_API_VERSION = 2; // added: bump when API changes

    public static synchronized void bootstrap(Minecraft mc) {
        if (INSTANCE != null) return;
        INSTANCE = new ModLoader(mc);
        Hooks.install(INSTANCE);     // lets Hooks.onClientTick(...) call back into this instance
        INSTANCE.loadAll();
    }

    // ----- state -----
    private final Minecraft mc;
    private final EventBus events = new EventBus();
    private final CommandRegistry commands = new CommandRegistry();
    private final BlockRegistryContext blockCtx = new BlockRegistryContext();
    private final ItemRegistryContext itemCtx  = new ItemRegistryContext();
    private final EntityRegistryContext entCtx = new EntityRegistryContext();
    private final SoundRegistryContext soundCtx = new SoundRegistryContext();

    private final List<Mod> mods = new ArrayList<>();
    private final Map<Mod, Method> tickMethods = new LinkedHashMap<>(); // cached onClientTick()
    private URLClassLoader modClassLoader; // kept alive until shutdown
    private volatile String queuedHudBanner = null;

    private ModLoader(Minecraft mc) { this.mc = mc; }

    public SoundRegistryContext sounds()  { return soundCtx; }
    public EventBus events()              { return events; }
    public CommandRegistry commands()     { return commands; }
    public BlockRegistryContext blocks()  { return blockCtx; }
    public ItemRegistryContext items()    { return itemCtx; }
    public EntityRegistryContext entities(){ return entCtx; }



    // ----- per-frame (from Hooks.onClientTick) -----
    public void onClientTick(Minecraft mc) {
        // Show any queued banner once HUD exists
        String banner = queuedHudBanner;
        if (banner != null && mc != null && mc.hud != null) {
            try { mc.hud.addChat(banner); } catch (Throwable ignored) {}
            queuedHudBanner = null;
        }

        // Only tick mods once LWJGL is ready
        try {
            if (!org.lwjgl.opengl.Display.isCreated() || !org.lwjgl.input.Keyboard.isCreated()) return;
        } catch (Throwable ignored) { return; }

        // Dispatch cached tick methods (no reflection lookup per frame)
        for (Map.Entry<Mod, Method> e : tickMethods.entrySet()) {
            Method m = e.getValue();
            if (m == null) continue;
            try { m.invoke(e.getKey()); } catch (Throwable t) { t.printStackTrace(); }
        }
    }

    private void queueHud(String msg) {
        System.out.println(msg);
        if (mc != null && mc.hud != null) {
            try { mc.hud.addChat(msg); return; } catch (Throwable ignored) {}
        }
        queuedHudBanner = msg;
    }

    // ----- loading -----
    private void loadAll() {
        events.post(new LifecycleEvents.Bootstrap(mc));

        // Resolve mods dir: prefer Minecraft.mcDir/mods, fallback to CWD/mods if mcDir is null
        File base = (Minecraft.mcDir != null) ? Minecraft.mcDir : new File(".");
        File modsDir = new File(base, "mods");
        if (!modsDir.exists()) modsDir.mkdirs();

        System.out.println("[ModLoader] mcDir=" + Minecraft.mcDir);
        System.out.println("[ModLoader] CWD=" + new File(".").getAbsolutePath());
        System.out.println("[ModLoader] Mods dir=" + modsDir.getAbsolutePath() + " exists=" + modsDir.exists());

        // Gather candidate containers (jars + dirs, recursive)
        List<File> containers = new ArrayList<>();
        collectContainers(modsDir, containers);
        System.out.println("[ModLoader] containers=" + containers.size());
        for (File c : containers) System.out.println("  - " + c.getAbsolutePath());

        // Build URLClassLoader with those containers
        List<URL> urls = new ArrayList<>();
        for (File f : containers) try { urls.add(f.toURI().toURL()); } catch (Exception ignored) {}
        ClassLoader parent = Minecraft.class.getClassLoader(); // game classes visible
        modClassLoader = new URLClassLoader(urls.toArray(new URL[0]), parent);

        // 1) ServiceLoader discovery (if META-INF/services/com.mcraft.api.Mod present)
        try {
            ServiceLoader<Mod> service = ServiceLoader.load(Mod.class, modClassLoader);
            for (Mod m : service) addModIfNew(m);
        } catch (Throwable t) { t.printStackTrace(); }

        // 2) Explicit class list from mods/classlist.txt (optional, user-friendly)
        Set<String> classNames = new LinkedHashSet<>();
        File classList = new File(modsDir, "classlist.txt");
        if (classList.isFile()) {
            try (BufferedReader br = new BufferedReader(new FileReader(classList))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String cn = line.trim();
                    if (!cn.isEmpty() && !cn.startsWith("#")) classNames.add(cn);
                }
            } catch (Exception ex) { ex.printStackTrace(); }
        }

        // 3) Fall back to scanning all jars/dirs for .class files
        for (File container : containers) {
            if (container.isFile() && container.getName().endsWith(".jar")) {
                classNames.addAll(listClassesInJar(container));
            } else if (container.isDirectory()) {
                classNames.addAll(listClassesInDir(container, container));
            }
        }

        // Instantiate any Mods from collected class names
        for (String cn : classNames) {
            try {
                Class<?> c = Class.forName(cn, true, modClassLoader);
                if (!Mod.class.isAssignableFrom(c)) continue;
                if (Modifier.isAbstract(c.getModifiers())) continue;
                // avoid duplicates if ServiceLoader already created one
                boolean dup = false;
                for (Mod existing : mods) if (existing.getClass() == c) { dup = true; break; }
                if (dup) continue;

                Mod m = (Mod) c.getDeclaredConstructor().newInstance();
                addModIfNew(m);
            } catch (Throwable ignored) {
                // noisy classpaths are common; keep quiet unless debugging
            }
        }

        // HUD banner
        if (mods.isEmpty()) {
            queueHud("[ModLoader] No mods loaded (" + containers.size() + " candidates).");
        } else {
            queueHud("[ModLoader] Loaded " + mods.size() + " mod(s) from " + containers.size() + " candidate(s).");
        }

        // Shared context + lifecycle
        ModContext ctx = new ModContext(mc, events, commands, blockCtx, itemCtx, entCtx, soundCtx);

        for (Mod m : mods) safe(() -> { try { m.preInit(ctx); }  catch (Exception e) { e.printStackTrace(); }});
        for (Mod m : mods) safe(() -> { try { m.init(ctx); }     catch (Exception e) { e.printStackTrace(); }});
        for (Mod m : mods) safe(() -> { try { m.postInit(ctx); } catch (Exception e) { e.printStackTrace(); }});

        // Flush any registered sounds into Paulscode
        try { if (mc != null && mc.soundPC != null) soundCtx.flushTo(mc.soundPC); } catch (Throwable t) { t.printStackTrace(); }

        events.post(new LifecycleEvents.GameStarted(mc));
    }

    private void addModIfNew(Mod m) {
        Class<?> cls = m.getClass();
        ModInfo info = cls.getAnnotation(ModInfo.class);

        if (info != null) {
            int modApi = info.apiVersion();
            if (modApi != CURRENT_API_VERSION) {
                String reason = "API " + modApi + " != required " + CURRENT_API_VERSION;
                System.out.println("[ModLoader] Rejected mod " + info.name() + " (" + reason + ")");
                rejectedMods.add(new RejectedMod(info.name() + " v" + info.version(), reason));
                return; // skip loading
            }
        } else {
            String reason = "no @ModInfo annotation";
            System.out.println("[ModLoader] Rejected mod " + cls.getName() + " (" + reason + ")");
            rejectedMods.add(new RejectedMod(cls.getName(), reason));
            return; // skip loading
        }

        // accepted mod → load normally
        mods.add(m);
        try {
            Method tick = m.getClass().getMethod("onClientTick");
            if (tick.getReturnType() == void.class && tick.getParameterCount() == 0) {
                tick.setAccessible(true);
                tickMethods.put(m, tick);
            } else {
                tickMethods.put(m, null);
            }
        } catch (NoSuchMethodException nsme) {
            tickMethods.put(m, null);
        }
    }


    public void shutdown() {
        for (Mod m : mods) safe(m::onShutdown);
        events.post(new LifecycleEvents.GameStopping(mc));
        if (modClassLoader != null) {
            try { modClassLoader.close(); } catch (Exception ignored) {}
            modClassLoader = null;
        }
    }

    public static void shutdownIfAlive() {
        if (INSTANCE != null) {
            try { INSTANCE.shutdown(); } catch (Throwable t) { t.printStackTrace(); } finally { INSTANCE = null; }
        }
    }

    private static void safe(Runnable r) { try { r.run(); } catch (Throwable t) { t.printStackTrace(); } }

    // ----------- filesystem helpers -----------

    private static void collectContainers(File root, List<File> out) {
        File[] list = root.listFiles();
        if (list == null) return;
        for (File f : list) {
            if (f.isDirectory()) {
                out.add(f);                 // treat as a class root
                collectContainers(f, out);  // also descend into subfolders
            } else if (f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                out.add(f);
            }
        }
    }

    private static List<String> listClassesInJar(File jar) {
        List<String> names = new ArrayList<>();
        try (JarFile jf = new JarFile(jar)) {
            Enumeration<JarEntry> e = jf.entries();
            while (e.hasMoreElements()) {
                JarEntry je = e.nextElement();
                if (je.isDirectory()) continue;
                String n = je.getName();
                if (n.endsWith(".class") && !n.contains("$")) {
                    names.add(n.substring(0, n.length() - 6).replace('/', '.'));
                }
            }
        } catch (IOException ignored) {}
        return names;
    }
 // added: allow other classes to access the active ModLoader
    public static ModLoader getInstance() {
        return INSTANCE;
    }
    private static final List<RejectedMod> rejectedMods = new ArrayList<>();

    public static List<RejectedMod> getRejectedMods() {
        return Collections.unmodifiableList(rejectedMods);
    }

    // added: list of successfully loaded mods
    public List<Mod> getLoadedMods() {
        return Collections.unmodifiableList(mods);
    }
 // added: holds info about a rejected mod
    public static class RejectedMod {
        public final String name;
        public final String reason;

        public RejectedMod(String name, String reason) {
            this.name = name;
            this.reason = reason;
        }
    }

    private static List<String> listClassesInDir(File root, File dir) {
        List<String> names = new ArrayList<>();
        File[] list = dir.listFiles();
        if (list == null) return names;
        for (File f : list) {
            if (f.isDirectory()) {
                names.addAll(listClassesInDir(root, f));
            } else if (f.isFile() && f.getName().endsWith(".class") && !f.getName().contains("$")) {
                String abs = f.getAbsolutePath();
                String base = root.getAbsolutePath();
                if (!base.endsWith(File.separator)) base += File.separator;
                if (!abs.startsWith(base)) continue;
                String rel = abs.substring(base.length());
                names.add(rel.substring(0, rel.length() - 6).replace(File.separatorChar, '.'));
            }
        }
        return names;
    }
}
