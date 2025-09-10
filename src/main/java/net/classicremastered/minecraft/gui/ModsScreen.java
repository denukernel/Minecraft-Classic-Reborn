package net.classicremastered.minecraft.gui;

import com.mcraft.api.Mod;
import com.mcraft.api.ModInfo;
import com.mcraft.api.loader.ModLoader;

import java.util.List;

public class ModsScreen extends GuiScreen {

    private final GuiScreen parent;

    public ModsScreen(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void onOpen() {
        this.buttons.clear();
        int btnW = 200, btnH = 20;
        int x = (this.width - btnW) / 2;
        int y = this.height - 40;

        this.buttons.add(new Button(0, x, y, btnW, btnH, "Back"));
    }

    @Override
    public void render(int mouseX, int mouseY) {
        drawCenteredString(this.fontRenderer, "Installed Mods", this.width / 2, 20, 0xFFFFFF);

        // list loaded mods
        int y = 50;
        List<Mod> loaded = ModLoader.getInstance().getLoadedMods();
        if (!loaded.isEmpty()) {
            for (Mod m : loaded) {
                ModInfo info = m.getClass().getAnnotation(ModInfo.class);
                if (info != null) {
                    String text = info.name() + " v" + info.version() + " by " + info.author();
                    drawCenteredString(this.fontRenderer, text, this.width / 2, y, 0xAAAAAA);
                    y += 12;
                } else {
                    String text = m.getClass().getSimpleName() + " (no @ModInfo)";
                    drawCenteredString(this.fontRenderer, text, this.width / 2, y, 0xAAAAAA);
                    y += 12;
                }
            }
        } else {
            drawCenteredString(this.fontRenderer, "No mods loaded.", this.width / 2, y, 0x777777);
            y += 12;
        }

        // list rejected mods
        List<ModLoader.RejectedMod> rejected = ModLoader.getRejectedMods();
        if (!rejected.isEmpty()) {
            y += 20;
            drawCenteredString(this.fontRenderer, "Rejected Mods:", this.width / 2, y, 0xFF5555);
            y += 14;
            for (ModLoader.RejectedMod rm : rejected) {
                String text = rm.name + " (" + rm.reason + ")";
                drawCenteredString(this.fontRenderer, text, this.width / 2, y, 0xFF5555);
                y += 12;
            }
        }

        super.render(mouseX, mouseY);
    }

    @Override
    protected void onButtonClick(Button btn) {
        if (btn.id == 0) {
            this.minecraft.setCurrentScreen(parent);
        }
    }
}
