package com.mcraft.api.loader;

import com.mcraft.api.Mod;
import com.mcraft.api.ModContext;
import com.mcraft.api.ModInfo;

import net.classicremastered.minecraft.chat.commands.CommandManager;

import java.util.List;

/** Bundled examples using the same lifecycle and API as external mods. */
public final class BuiltinMods {
    private BuiltinMods() {}

    public static List<Mod> create() {
        return List.of(new TestMod(), new CoordinatesMod());
    }

    public static String description(Mod mod) {
        return mod instanceof TestMod
                ? "Checks mod loading. Chat command: /modtest"
                : "Shows your position. Chat command: /whereami";
    }

    @ModInfo(
            id = "classic.test",
            name = "Test Mod",
            version = "1.0",
            author = "Classic Reborn",
            apiVersion = 2)
    public static final class TestMod implements Mod {
        private int stage;
        private Object command;

        public void preInit(ModContext ctx) {
            stage = 1;
        }

        public void init(ModContext ctx) {
            if (stage != 1) throw new IllegalStateException("preInit did not run");
            ctx.commands.register(
                    ctx.mc,
                    "modtest",
                    "/modtest",
                    (mc, player, args) -> {
                        if (mc != null && mc.hud != null) mc.hud.addChat(testResult());
                    });
            command = CommandManager.get("modtest");
            stage = 2;
        }

        public void postInit(ModContext ctx) {
            if (stage != 2) throw new IllegalStateException("init did not run");
            stage = 3;
        }

        public String testResult() {
            return stage == 3 && command != null && CommandManager.get("modtest") == command
                    ? "test passed: lifecycle and /modtest ready"
                    : "test failed: lifecycle or command unavailable";
        }

        public void onShutdown() {
            stage = 0;
        }
    }

    @ModInfo(
            id = "classic.coordinates",
            name = "Coordinates",
            version = "1.0",
            author = "Classic Reborn",
            apiVersion = 2)
    public static final class CoordinatesMod implements Mod {
        public void preInit(ModContext ctx) {}

        public void init(ModContext ctx) {
            ctx.commands.register(
                    ctx.mc,
                    "whereami",
                    "/whereami",
                    (mc, player, args) -> {
                        if (mc == null || mc.hud == null) return;
                        if (player == null) {
                            mc.hud.addChat("join a world to see your coordinates");
                            return;
                        }
                        mc.hud.addChat(
                                "position: "
                                        + (int) Math.floor(player.x)
                                        + ", "
                                        + (int) Math.floor(player.bb.y0)
                                        + ", "
                                        + (int) Math.floor(player.z));
                    });
        }

        public void postInit(ModContext ctx) {}
    }
}
