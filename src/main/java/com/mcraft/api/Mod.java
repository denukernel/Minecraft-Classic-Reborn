package com.mcraft.api;

public interface Mod {
    void preInit(ModContext ctx) throws Exception;  // config, subscribe events
    void init(ModContext ctx) throws Exception;     // register content/commands
    void postInit(ModContext ctx) throws Exception; // cross-mod wiring

    default void onShutdown() {} // optional
}