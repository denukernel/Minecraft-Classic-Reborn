package net.classicremastered.minecraft.chat.commands;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.player.Player;

import java.util.ArrayList;
import java.util.List;

public final class KillCommand implements Command {
    @Override
    public String getName() {
        return "kill";
    }

    @Override
    public String getUsage() {
        return "/kill [@p|@e]";
    }

    @Override
    public List<String> suggest(Minecraft mc, Player sender, String[] args, int argument) {
        return argument == 0 ? List.of("@p", "@e") : List.of();
    }

    @Override
    public void execute(Minecraft mc, Player sender, String[] args) {
        if (args.length == 0 || (args.length == 1 && args[0].equals("@p"))) {
            kill(sender);
        } else if (args.length == 1 && args[0].equals("@e")) {
            int killed = killNonPlayers(sender);
            if (mc.hud != null) {
                mc.hud.addChat(
                        killed == 0
                                ? "&cNo non-player entities found."
                                : "&aEntities killed: " + killed);
            }
        } else if (mc.hud != null) {
            mc.hud.addChat("&cUsage: " + getUsage());
        }
    }

    private static int killNonPlayers(Player sender) {
        if (sender.level == null || sender.level.blockMap == null) {
            return 0;
        }

        int killed = 0;
        // Mob deaths can add loot to the live entity list.
        for (Entity entity : new ArrayList<>(sender.level.blockMap.all)) {
            if (!(entity instanceof Player) && kill(entity)) {
                killed++;
            }
        }
        return killed;
    }

    private static boolean kill(Entity entity) {
        if (entity.removed) {
            return false;
        }
        if (entity instanceof Mob mob) {
            if (mob.health <= 0) {
                return false;
            }
            mob.health = 0;
            mob.die(null);
        } else {
            entity.remove();
        }
        return true;
    }
}
