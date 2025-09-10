package net.classicremastered.minecraft.chat.commands;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.ai.BasicAI;
import net.classicremastered.minecraft.player.Player;

public final class AISpeedCommand implements Command {
    @Override public String getName()  { return "aispeed"; }
    @Override public String getUsage() {
        return "/aispeed <scale>|show|reset  or  /aispeed key=value [...]\n" +
               "keys: scale, walk, air, swim, lava, runmul";
    }

    @Override
    public void execute(Minecraft mc, Player sender, String[] args) {
        if (mc == null || mc.level == null) return;

        // dev-only guard
        if (!mc.developer) {
            hud(mc, "&cDev only: enable developer mode to use /aispeed");
            return;
        }

        if (args.length == 0) {
            hud(mc, "&7" + getUsage());
            return;
        }

        // show current values from any BasicAI
        if ("show".equalsIgnoreCase(args[0])) {
            BasicAI b = findAnyBasicAI(mc);
            if (b != null) {
                hud(mc, String.format(java.util.Locale.ROOT,
                    "&7walk=%.3f air=%.3f swim=%.3f lava=%.3f runmul=%.2f scale=%.2f",
                    b.walkSpeed, b.airSpeed, b.swimSpeed, b.lavaSpeed, b.runMultiplier, b.speedScale));
            } else {
                hud(mc, "&7No AI mobs found.");
            }
            return;
        }

        // parse inputs
        boolean reset = "reset".equalsIgnoreCase(args[0]);
        Float scale=null, walk=null, air=null, swim=null, lava=null, runmul=null;

        if (reset) {
            scale = 1.00f; walk = 0.10f; air = 0.02f; swim = 0.02f; lava = 0.02f; runmul = 1.40f;
        } else if (args.length == 1 && isFloat(args[0])) {
            scale = Float.parseFloat(args[0]); // /aispeed 1.25
        } else {
            for (String a : args) {
                int eq = a.indexOf('=');
                if (eq <= 0 || eq == a.length()-1) continue;
                String k = a.substring(0, eq).toLowerCase(java.util.Locale.ROOT);
                String v = a.substring(eq+1);
                if (!isFloat(v)) continue;
                float f = Float.parseFloat(v);
                switch (k) {
                    case "scale":  scale=f; break;
                    case "walk":   walk=f;  break;
                    case "air":    air=f;   break;
                    case "swim":   swim=f;  break;
                    case "lava":   lava=f;  break;
                    case "runmul": runmul=f;break;
                }
            }
        }

        int touched = 0;
        if (mc.level.blockMap != null && mc.level.blockMap.all != null) {
            for (Object o : mc.level.blockMap.all) {
                if (!(o instanceof Mob)) continue;
                BasicAI b = ((Mob)o).ai instanceof BasicAI ? (BasicAI)((Mob)o).ai : null;
                if (b == null) continue;

                if (scale  != null) b.speedScale    = scale;
                if (walk   != null) b.walkSpeed     = walk;
                if (air    != null) b.airSpeed      = air;
                if (swim   != null) b.swimSpeed     = swim;
                if (lava   != null) b.lavaSpeed     = lava;
                if (runmul != null) b.runMultiplier = runmul;
                touched++;
            }
        }

        // ASCII HUD summary (no Unicode arrows)
        if (touched == 0) {
            hud(mc, "&7No AI mobs in level.");
        } else {
            StringBuilder sb = new StringBuilder("&aAISpeed: ");
            boolean any=false;
            if (scale  != null) { sb.append("scale=").append(fmt(scale)).append(' '); any=true; }
            if (walk   != null) { sb.append("walk=").append(fmt(walk)).append(' ');   any=true; }
            if (air    != null) { sb.append("air=").append(fmt(air)).append(' ');     any=true; }
            if (swim   != null) { sb.append("swim=").append(fmt(swim)).append(' ');   any=true; }
            if (lava   != null) { sb.append("lava=").append(fmt(lava)).append(' ');   any=true; }
            if (runmul != null) { sb.append("runmul=").append(fmt(runmul)).append(' '); any=true; }
            if (!any) sb.append("(no changes) ");
            sb.append("| mobs=").append(touched);
            hud(mc, sb.toString().trim());
        }
    }

    private static void hud(Minecraft mc, String s) {
        if (mc.hud != null) mc.hud.addChat(ascii(s));
    }

    private static String ascii(String s) {
        // filter non-ASCII to '?'
        StringBuilder b = new StringBuilder(s.length());
        for (int i=0;i<s.length();i++) {
            char ch = s.charAt(i);
            if (ch >= 32 && ch <= 126) b.append(ch); else b.append('?');
        }
        return b.toString();
    }

    private static String fmt(float f) {
        return String.format(java.util.Locale.ROOT, "%.3f", f);
    }

    private static boolean isFloat(String s) {
        try { Float.parseFloat(s); return true; } catch (Exception e) { return false; }
    }

    private static BasicAI findAnyBasicAI(Minecraft mc) {
        if (mc.level == null || mc.level.blockMap == null || mc.level.blockMap.all == null) return null;
        for (Object e : mc.level.blockMap.all) {
            if (e instanceof Mob && ((Mob)e).ai instanceof BasicAI) return (BasicAI)((Mob)e).ai;
        }
        return null;
    }
}
