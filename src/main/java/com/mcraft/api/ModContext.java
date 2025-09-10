// com.mcraft.api.ModContext
package com.mcraft.api;

import com.mcraft.api.event.EventBus;
import com.mcraft.api.registry.BlockRegistryContext;
import com.mcraft.api.registry.ItemRegistryContext;
import com.mcraft.api.registry.EntityRegistryContext;
import com.mcraft.api.commands.CommandRegistry;
import com.mcraft.api.sound.SoundRegistryContext;

import net.classicremastered.minecraft.Minecraft;

public final class ModContext {
    public final Minecraft mc;
    public final EventBus events;
    public final CommandRegistry commands;
    public final BlockRegistryContext blocks;
    public final ItemRegistryContext items;
    public final EntityRegistryContext entities;
    private final SoundRegistryContext sounds;

    public ModContext(Minecraft mc, EventBus events, CommandRegistry commands, BlockRegistryContext blocks,
            ItemRegistryContext items, EntityRegistryContext entities, SoundRegistryContext sounds) {
        this.mc = mc;
        this.events = events;
        this.commands = commands;
        this.blocks = blocks;
        this.items = items;
        this.entities = entities;
        this.sounds = sounds;
    }

    public SoundRegistryContext sounds() {
        return sounds;
    }
}
