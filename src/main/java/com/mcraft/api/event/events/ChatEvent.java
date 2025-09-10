package com.mcraft.api.event.events;

import com.mcraft.api.event.*;

import net.classicremastered.minecraft.Minecraft;

public final class ChatEvent {
    public static final class Send extends Event implements Cancellable {
        private final Minecraft mc;
        private String text;
        private boolean cancelled;

        public Send(Minecraft mc, String text) {
            this.mc = mc;
            this.text = text;
        }

        public Minecraft mc() {
            return mc;
        }

        public String getText() {
            return text;
        }

        public void setText(String t) {
            this.text = t;
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public void setCancelled(boolean c) {
            this.cancelled = c;
        }
    }
}