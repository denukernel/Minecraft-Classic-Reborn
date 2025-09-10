package net.classicremastered.minecraft.gui;

import org.lwjgl.opengl.GL11;

public final class GameOverScreen extends GuiScreen {

    @Override
    public final void onOpen() {
        this.buttons.clear();

        // Respawn button
        this.buttons.add(new Button(0, this.width / 2 - 100, this.height / 4 + 48, "Respawn"));

        // Generate new level
        this.buttons.add(new Button(1, this.width / 2 - 100, this.height / 4 + 72, "Generate new level..."));

        // Load level
        Button loadBtn = new Button(2, this.width / 2 - 100, this.height / 4 + 96, "Load level...");
        if (this.minecraft.session == null) {
            loadBtn.active = false;
        }
        this.buttons.add(loadBtn);
    }

    @Override
    protected final void onButtonClick(Button button) {
        if (button.id == 0) {
            // Respawn: reset player position and close screen
            this.minecraft.player.resetPos();
            this.minecraft.player.health = 20;
            this.minecraft.setCurrentScreen(null);
        }

        if (button.id == 1) {
            this.minecraft.setCurrentScreen(new GenerateLevelScreen(this));
        }

        if (button.id == 2 && this.minecraft.session != null) {
            this.minecraft.setCurrentScreen(new LoadLevelScreen(this));
        }
    }

    @Override
    public final void render(int mouseX, int mouseY) {
        drawFadingBox(0, 0, this.width, this.height, 1615855616, -1602211792);

        GL11.glPushMatrix();
        GL11.glScalef(2.0F, 2.0F, 2.0F);
        drawCenteredString(this.fontRenderer, "Game over!", this.width / 2 / 2, 30, 0xFFFFFF);
        GL11.glPopMatrix();

        drawCenteredString(this.fontRenderer, "Score: &e" + this.minecraft.player.getScore(),
                           this.width / 2, 100, 0xFFFFFF);

        super.render(mouseX, mouseY);
    }
}
