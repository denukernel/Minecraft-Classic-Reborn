package com.mcraft.api.commands;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.player.Player;

import java.lang.reflect.Proxy;

public final class CommandRegistry {

    @FunctionalInterface
    public interface Executor {
        void run(Minecraft mc, Player sender, String[] args);
    }

    public void register(Minecraft mc, String name, String usage, Executor exec) {
        try {
            // Load the command interface
            Class<?> cmdIface = Class.forName("net.classicremastered.minecraft.chat.commands.Command");
            Object cmd = Proxy.newProxyInstance(
                    cmdIface.getClassLoader(),
                    new Class[]{cmdIface},
                    (proxy, method, a) -> {
                        switch (method.getName()) {
                            case "getName":  return name;
                            case "getUsage": return usage;
                            case "execute":
                                exec.run((Minecraft) a[0], (Player) a[1], (String[]) a[2]);
                                return null;
                        }
                        return null;
                    });

            // Find CommandManager
            java.lang.reflect.Field f = Class.forName("net.classicremastered.minecraft.Minecraft")
                                             .getDeclaredField("commandManager");
            f.setAccessible(true);
            Object mgr = f.get(mc);

            // Try register methods
            try {
                mgr.getClass().getMethod("register", cmdIface).invoke(mgr, cmd);
            } catch (NoSuchMethodException e) {
                mgr.getClass().getMethod("add", cmdIface).invoke(mgr, cmd);
            }

            System.out.println("[API] Registered command: /" + name);

        } catch (Throwable t) {
            System.err.println("[API] CommandRegistry failed to register /" + name);
            t.printStackTrace();
        }
    }
}
