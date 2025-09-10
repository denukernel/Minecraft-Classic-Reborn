package com.mcraft.api.event.events;

import com.mcraft.api.event.Event;

import net.classicremastered.minecraft.Minecraft;

public final class TickEvent {
    public static final class Client extends Event {
        public final Minecraft mc;

        public Client(Minecraft mc) {
            this.mc = mc;
        }
    }
}