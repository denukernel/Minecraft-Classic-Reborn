package net.classicremastered.minecraft.level.itemstack;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.player.Player;

public final class NameTagItem extends Item {
    public static final int ID = 243;
    public static final int MAX_NAME_LENGTH = 24;

    NameTagItem() {
        super(ID, "Name Tag", "/items/name_tag.png");
    }

    public boolean canApply(Minecraft game, Mob mob) {
        Player player = game.player;
        return player != null && game.level != null && player.level == game.level
                && mob.level == game.level && !game.isOnline() && !game.level.isNetworkMode()
                && !(mob instanceof Player) && !mob.removed && mob.health > 0
                && game.entityWithinReach(mob) && player.inventory.getSelected() == ID + 256
                && player.inventory.getCount(player.inventory.selected) > 0;
    }

    public boolean apply(Minecraft game, Mob mob, String name) {
        String trimmed = name.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH
                || trimmed.chars().anyMatch(NameTagItem::isUnsupportedCharacter)
                || trimmed.equals(mob.customName) || !canApply(game, mob))
            return false;

        mob.customName = trimmed;
        mob.despawnIdleTicks = 0;
        consumeSelected(game.player, game.level);
        game.player.swingHand();
        return true;
    }

    public static boolean isUnsupportedCharacter(int character) {
        return Character.isISOControl(character) || Character.isSurrogate((char) character)
                || Character.getType(character) == Character.LINE_SEPARATOR
                || Character.getType(character) == Character.PARAGRAPH_SEPARATOR;
    }
}
