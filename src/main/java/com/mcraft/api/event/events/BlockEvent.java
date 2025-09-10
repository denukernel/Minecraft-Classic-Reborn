package com.mcraft.api.event.events;

import com.mcraft.api.event.*;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.player.Player;

public final class BlockEvent {
    public static final class Place extends Event implements Cancellable {
        public final Level level;
        public final int x, y, z;
        public final int blockId;
        public final Player player;
        private boolean cancelled;

        public Place(Level l, int x, int y, int z, int id, Player p) {
            this.level = l;
            this.x = x;
            this.y = y;
            this.z = z;
            this.blockId = id;
            this.player = p;
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public void setCancelled(boolean c) {
            this.cancelled = c;
        }
    }

    public static final class Break extends Event implements Cancellable {
        public final Level level;
        public final int x, y, z;
        public final Player player;
        private boolean cancelled;

        public Break(Level l, int x, int y, int z, Player p) {
            this.level = l;
            this.x = x;
            this.y = y;
            this.z = z;
            this.player = p;
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public void setCancelled(boolean c) {
            this.cancelled = c;
        }
    }
}