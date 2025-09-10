package com.mcraft.api.event.events;

import com.mcraft.api.event.Event;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.level.Level;

public final class LifecycleEvents {
    public static final class Bootstrap extends Event {
        public final Minecraft mc;

        public Bootstrap(Minecraft mc) {
            this.mc = mc;
        }
    }

    public static final class GameStarted extends Event {
        public final Minecraft mc;

        public GameStarted(Minecraft mc) {
            this.mc = mc;
        }
    }

    public static final class GameStopping extends Event {
        public final Minecraft mc;

        public GameStopping(Minecraft mc) {
            this.mc = mc;
        }
    }

    public static final class LevelLoaded extends Event {
        public final Level level;

        public LevelLoaded(Level l) {
            this.level = l;
        }
    }
}