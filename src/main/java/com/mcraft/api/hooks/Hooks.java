package com.mcraft.api.hooks;

import com.mcraft.api.loader.ModLoader;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.level.Level;

import com.mcraft.api.event.events.*;
import com.mcraft.api.commands.CommandRegistry;

public final class Hooks {
    private static ModLoader LOADER;

    public static void install(ModLoader l) {
        LOADER = l;
    }

// Called every client tick
    public static void onClientTick(Minecraft mc) {
        if (LOADER != null) {
            // flush queued HUD banner
            LOADER.onClientTick(mc);
            // (optional) post your tick event:
            // LOADER.events().post(new TickEvent.Client(mc));
        }
    }

// After level created/loaded
    public static void onLevelLoaded(Level level) {
        if (LOADER == null)
            return;
        LOADER.events().post(new LifecycleEvents.LevelLoaded(level));
    }

// Return true to cancel sending
    public static boolean onChatSend(Minecraft mc, String text) {
        if (LOADER == null)
            return false;
        ChatEvent.Send e = new ChatEvent.Send(mc, text);
        LOADER.events().post(e);
        if (e.isCancelled())
            return true;
        String replaced = e.getText();
        if (!replaced.equals(text)) {
// Optional: set the new text back into your chat screen if you support it
        }
        return false;
    }

// Return true to cancel place
    public static boolean onBlockPlace(Level level, int x, int y, int z, int blockId,
            net.classicremastered.minecraft.player.Player player) {
        if (LOADER == null)
            return false;
        BlockEvent.Place e = new BlockEvent.Place(level, x, y, z, blockId, player);
        LOADER.events().post(e);
        return e.isCancelled();
    }

// Return true to cancel break
    public static boolean onBlockBreak(Level level, int x, int y, int z, net.classicremastered.minecraft.player.Player player) {
        if (LOADER == null)
            return false;
        BlockEvent.Break e = new BlockEvent.Break(level, x, y, z, player);
        LOADER.events().post(e);
        return e.isCancelled();
    }

    public static void onRenderHUD(Minecraft mc, float partialTicks) {
        if (LOADER == null)
            return;
        LOADER.events().post(new RenderEvent.HUD(mc, partialTicks));
    }

    public static void onRenderWorld(Object levelRenderer, float partialTicks) {
        if (LOADER == null)
            return;
        LOADER.events().post(new RenderEvent.World(levelRenderer, partialTicks));
    }

    public static void onShutdown() {
        if (LOADER == null)
            return;
        LOADER.shutdown();
    }

    public static CommandRegistry commands() {
        return LOADER == null ? null : LOADER.commands();
    }
}