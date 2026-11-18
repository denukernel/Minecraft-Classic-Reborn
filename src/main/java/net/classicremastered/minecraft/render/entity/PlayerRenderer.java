package net.classicremastered.minecraft.render.entity;

import net.classicremastered.minecraft.model.HumanoidModel;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.TextureManager;

final class PlayerRenderer extends ModelMobRenderer<Player> {
    PlayerRenderer(HumanoidModel model) {
        super(model);
    }

    @Override
    public void renderWorld(Player player, TextureManager textures, float partial) {
        if (player.isSpectator()) return;
        var game = player.minecraft;
        if (game != null
                && game.player == player
                && game.renderer != null
                && !game.renderer.shouldRenderLocalPlayer()) return;
        if (player.getClass() != Player.class || game != null && game.cameraMode > 0) {
            super.renderWorld(player, textures, partial);
        }
    }
}
