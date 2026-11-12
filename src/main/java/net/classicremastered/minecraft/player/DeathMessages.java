package net.classicremastered.minecraft.player;

import net.classicremastered.minecraft.Entity;
import net.classicremastered.minecraft.entity.Arrow;
import net.classicremastered.minecraft.entity.HeldBlockEntity;
import net.classicremastered.minecraft.mob.StickFigure;
import net.classicremastered.minecraft.net.NetworkPlayer;

/** Player death wording adapted from the modern Minecraft English language assets. */
public final class DeathMessages {
    private DeathMessages() {
    }

    public static String message(Player player, Entity source, String cause) {
        return message(player, source, cause, null);
    }

    public static Entity resolveAttacker(Entity source) {
        if (source instanceof Arrow arrow) return arrow.getOwner();
        if (source instanceof HeldBlockEntity block) return block.getOwner();
        return source;
    }

    static String message(Player player, Entity source, String cause, Entity recentAttacker) {
        String victim = name(player);
        if (source == null
                && recentAttacker != null
                && cause != null
                && switch (cause) {
                    case "in_wall", "drown", "lava", "fire", "burn", "fall", "void" -> true;
                    default -> false;
                }) {
            return name(recentAttacker) + " finished off " + victim;
        }
        Entity attacker = resolveAttacker(source);
        if (attacker instanceof StickFigure) {
            return victim + " got punished by " + name(attacker);
        }
        if (cause != null) {
            switch (cause) {
                case "in_wall":
                    return victim + " suffocated in a wall";
                case "drown" :
                    return victim + " drowned";
                case "lava" :
                    return victim + " tried to swim in lava";
                case "fire":
                    return victim + " went up in flames";
                case "burn":
                    return victim + " burned to death";
                case "fall":
                    return victim
                            + (attacker == null
                                    ? " hit the ground too hard"
                                    : " was doomed to fall by " + name(attacker));
                case "void":
                    return victim + " fell out of the world";
                case "magic":
                    return victim + " was killed by magic";
                case "explosion":
                    return victim
                            + (attacker == null
                                    ? " blew up"
                                    : " was blown up by " + name(attacker));
                default :
                    break;
            }
        }
        if (source instanceof Arrow) return victim + " was shot by " + name(attacker);
        if (source instanceof HeldBlockEntity && attacker != null)
            return victim + " was hit by a block thrown by " + name(attacker);
        return victim + (attacker == null ? " died" : " was slain by " + name(attacker));
    }

    private static String name(Entity entity) {
        if (entity == null)
            return "Arrow";
        if (entity instanceof NetworkPlayer)
            return ((NetworkPlayer) entity).name;
        if (entity instanceof Player) {
            Player player = (Player) entity;
            if (player.minecraft != null && player.minecraft.session != null
                    && player.minecraft.session.username != null && !player.minecraft.session.username.isBlank()) {
                return player.minecraft.session.username;
            }
            return "Player";
        }
        return entity.getClass()
                .getSimpleName()
                .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replaceFirst(" Boss$", "");
    }
}
