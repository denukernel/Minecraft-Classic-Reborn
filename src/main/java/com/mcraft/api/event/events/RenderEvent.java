package com.mcraft.api.event.events;

import com.mcraft.api.event.Event;

import net.classicremastered.minecraft.Minecraft;

public final class RenderEvent {
    public static final class HUD extends Event {
        public final Minecraft mc;
        public final float partial;

        public HUD(Minecraft mc, float p) {
            this.mc = mc;
            this.partial = p;
        }
    }

    public static final class World extends Event {
        public final Object levelRenderer;
        public final float partial;

        public World(Object r, float p) {
            this.levelRenderer = r;
            this.partial = p;
        }
    }
}