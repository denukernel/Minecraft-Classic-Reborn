package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.MobRegistry;
import net.classicremastered.minecraft.player.Player;

import java.util.*;

public final class MobWandItem extends Item {
    public enum Power {
        BATTLE,
        GROUP,
        HEAL,
        STOP
    }

    private final Power power;

    public MobWandItem(int id, Power power) {
        super(
                id,
                "Mob "
                        + power.name().charAt(0)
                        + power.name().substring(1).toLowerCase(Locale.ROOT)
                        + " Wand",
                "/items/wand_" + power.name().toLowerCase(Locale.ROOT) + ".png",
                1);
        this.power = power;
    }

    public String instructions() {
        return switch (power) {
            case BATTLE -> "Select two mobs to fight";
            case GROUP -> "Select allies; sneak-click an enemy";
            case HEAL -> "Right-click a mob to heal it";
            case STOP -> "Right-click a mob to stop its battle";
        };
    }

    public void apply(Player player, Mob target) {
        if (player == null
                || target == null
                || target instanceof Player
                || target.level != player.level
                || target.health <= 0
                || target.removed
                || player.level.isNetworkMode()
                || !player.level.creativeMode) return;
        if (player.mobWandSelections == null) player.mobWandSelections = new HashMap<>();
        List<Mob> group = player.mobWandSelections.computeIfAbsent(id, key -> new ArrayList<>());
        group.removeIf(mob -> mob.removed || mob.health <= 0 || mob.level != player.level);
        switch (power) {
            case HEAL -> {
                Mob fresh =
                        MobRegistry.create(
                                MobRegistry.idOf(target),
                                target.level,
                                target.x,
                                target.y,
                                target.z);
                target.health = fresh == null ? Math.max(20, target.lastHealth) : fresh.health;
                target.remainingFireTicks = 0;
            }
            case STOP -> {
                Mob opponent = MobBattle.target(target);
                MobBattle.stop(target);
                if (opponent != null && MobBattle.target(opponent) == target)
                    MobBattle.stop(opponent);
            }
            case BATTLE -> {
                if (group.isEmpty()) group.add(target);
                else if (group.get(0) != target) {
                    Mob first = group.remove(0);
                    MobBattle.fight(first, target);
                    MobBattle.fight(target, first);
                }
            }
            case GROUP -> {
                if (player.isSneaking() && !group.isEmpty()) {
                    Mob first = group.get(0);
                    for (Mob ally : group) if (ally != target) MobBattle.fight(ally, target);
                    if (first != target) MobBattle.fight(target, first);
                    group.clear();
                } else if (!group.remove(target)) group.add(target);
            }
        }
        if (player.minecraft != null && player.minecraft.hud != null)
            player.minecraft.hud.addChat(
                    "&b"
                            + name
                            + "&7: "
                            + instructions()
                            + (group.isEmpty() ? "" : " (" + group.size() + " selected)"));
    }

    @Override
    public void use(Player player, Level level) {
        if (player == null || player.minecraft == null || player.minecraft.selected == null) return;
        if (player.minecraft.selected.entity instanceof Mob mob) apply(player, mob);
    }
}
