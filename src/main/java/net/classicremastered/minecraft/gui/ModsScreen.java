package net.classicremastered.minecraft.gui;

import com.mcraft.api.Mod;
import com.mcraft.api.ModInfo;
import com.mcraft.api.loader.BuiltinMods;
import com.mcraft.api.loader.ModLoader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ModsScreen extends GuiScreen {
    private final GuiScreen parent;
    private int page;
    private int pageSize;
    private String message = "selections apply after restarting the game";
    private final List<String[]> rows = new ArrayList<>();

    public ModsScreen(GuiScreen parent) {
        super(parent);
        this.parent = parent;
    }

    @Override
    public void onOpen() {
        buttons.clear();
        rows.clear();
        ModLoader loader = ModLoader.getInstance();
        if (loader != null) {
            for (Mod mod : loader.getBuiltinMods()) {
                ModInfo info = mod.getClass().getAnnotation(ModInfo.class);
                boolean active = loader.getLoadedMods().contains(mod);
                rows.add(
                        new String[] {
                            info.name() + " v" + info.version() + " (bundled)",
                            BuiltinMods.description(mod),
                            active ? "active this session" : "inactive this session"
                        });
            }
            for (Mod mod : loader.getLoadedMods()) {
                if (loader.getBuiltinMods().contains(mod)) continue;
                ModInfo info = mod.getClass().getAnnotation(ModInfo.class);
                rows.add(
                        new String[] {
                            info.name() + " v" + info.version(),
                            "external mod by " + info.author(),
                            "active"
                        });
            }
        }
        for (ModLoader.RejectedMod mod : ModLoader.getRejectedMods()) {
            rows.add(new String[] {mod.name, mod.reason, "rejected"});
        }
        pageSize = Math.max(1, (height - 128) / 46);
        page = Math.max(0, Math.min(page, (Math.max(1, rows.size()) - 1) / pageSize));
        if (loader != null) {
            for (int row = 0; row < pageSize; row++) {
                int index = page * pageSize + row;
                if (index >= loader.getBuiltinMods().size()) break;
                Mod mod = loader.getBuiltinMods().get(index);
                buttons.add(
                        new Button(
                                10 + index,
                                width - 116,
                                50 + row * 46,
                                106,
                                20,
                                loader.isSelected(mod) ? "enabled" : "disabled"));
            }
        }
        buttons.add(new Button(1, width / 2 - 150, height - 58, 60, 20, "prev"));
        buttons.add(new Button(2, width / 2 + 90, height - 58, 60, 20, "next"));
        ((Button) buttons.get(buttons.size() - 2)).active = page > 0;
        ((Button) buttons.get(buttons.size() - 1)).active = (page + 1) * pageSize < rows.size();
        buttons.add(new Button(3, width / 2 - 82, height - 58, 164, 20, "test mod"));
        buttons.add(new Button(0, width / 2 - 100, height - 30, 200, 20, "back"));
    }

    private String fit(String text, int maxWidth) {
        if (fontRenderer.getWidth(text) <= maxWidth) return text;
        while (!text.isEmpty() && fontRenderer.getWidth(text + "...") > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + "...";
    }

    @Override
    public void render(int mouseX, int mouseY) {
        if (minecraft.level == null) drawMenuBackground();
        else drawFadingBox(0, 0, width, height, 0xC0101010, 0xD0101010);
        drawCenteredString(fontRenderer, "minecraft mods selection", width / 2, 14, 0xFFFFFF);
        drawCenteredString(fontRenderer, fit(message, width - 16), width / 2, 30, 0xFFFF88);
        for (int row = 0; row < pageSize; row++) {
            int index = page * pageSize + row;
            if (index >= rows.size()) break;
            String[] entry = rows.get(index);
            int y = 50 + row * 46;
            drawString(fontRenderer, fit(entry[0], width - 138), 10, y, 0xFFFFFF);
            drawString(fontRenderer, fit(entry[1], width - 20), 10, y + 22, 0xBBBBBB);
            drawString(
                    fontRenderer,
                    entry[2],
                    10,
                    y + 33,
                    "rejected".equals(entry[2]) ? 0xFF6666 : 0x88DD88);
        }
        if (rows.isEmpty())
            drawCenteredString(fontRenderer, "mod loader unavailable", width / 2, 70, 0xAAAAAA);
        int pages = Math.max(1, (rows.size() + pageSize - 1) / pageSize);
        drawCenteredString(
                fontRenderer, "page " + (page + 1) + "/" + pages, width / 2, height - 74, 0xAAAAAA);
        super.render(mouseX, mouseY);
    }

    @Override
    protected void onButtonClick(Button button) {
        ModLoader loader = ModLoader.getInstance();
        if (button.id == 0) minecraft.setCurrentScreen(parent);
        else if (button.id == 1 || button.id == 2) {
            page += button.id == 1 ? -1 : 1;
            onOpen();
        } else if (button.id == 3) {
            message = loader == null ? "mod loader unavailable" : loader.testBuiltinMod();
        } else if (button.id >= 10 && loader != null) {
            Mod mod = loader.getBuiltinMods().get(button.id - 10);
            try {
                loader.setSelected(mod, !loader.isSelected(mod));
                message = "saved: selections apply after restarting the game";
            } catch (IOException ex) {
                message = "could not save mod selection";
            }
            onOpen();
        }
    }
}
