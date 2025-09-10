package net.classicremastered.minecraft.gui;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import net.classicremastered.minecraft.lang.LanguageManager;

public final class LanguageScreen extends GuiScreen {

    private final GuiScreen parent;
    private final LanguageManager langMgr;

    private int page = 0;
    private static final int LANG_PER_PAGE = 6; // max languages visible per page
    private List<String> languages;

    public LanguageScreen(GuiScreen parent, LanguageManager langMgr) {
        this.parent = parent;
        this.langMgr = langMgr;
    }

    @Override
    public void onOpen() {
        this.buttons.clear();

        File dir = langMgr.getLangDir();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".lang"));

        if (files != null) {
            Arrays.sort(files);
            languages = Arrays.stream(files)
                              .map(f -> f.getName().replace(".lang", ""))
                              .collect(Collectors.toList());
        }

        refreshButtons();
    }

    private void refreshButtons() {
        this.buttons.clear();

        if (languages == null || languages.isEmpty()) {
            this.buttons.add(new Button(200, this.width / 2 - 100, this.height - 36, 200, 20, "Done"));
            return;
        }

        int start = page * LANG_PER_PAGE;
        int end = Math.min(start + LANG_PER_PAGE, languages.size());
        int y = this.height / 6;

        // Language buttons
        for (int i = start; i < end; i++) {
            this.buttons.add(new Button(i, this.width / 2 - 100, y, 200, 20, languages.get(i)));
            y += 24;
        }

        // Navigation row ABOVE "Done"
        int navY = this.height - 60;
        if (page > 0) {
            this.buttons.add(new Button(998, this.width / 2 - 100, navY, 90, 20, "< Prev"));
        }
        if (end < languages.size()) {
            this.buttons.add(new Button(999, this.width / 2 + 10, navY, 90, 20, "Next >"));
        }

        // Done button at very bottom
        this.buttons.add(new Button(200, this.width / 2 - 100, this.height - 36, 200, 20, "Done"));
    }


    @Override
    protected void onButtonClick(Button b) {
        if (b.id == 200) {
            this.minecraft.setCurrentScreen(parent);
            return;
        }
        if (b.id == 998) { // prev
            page--;
            refreshButtons();
            return;
        }
        if (b.id == 999) { // next
            page++;
            refreshButtons();
            return;
        }

        // Regular language buttons
        if (b.id >= 0 && b.id < languages.size()) {
            String chosen = b.text;
            langMgr.load(chosen);
            this.minecraft.hud.addChat("&aLanguage set to: " + chosen);
            this.minecraft.setCurrentScreen(parent);
        }
    }

    @Override
    public void render(int mx, int my) {
        drawFadingBox(0, 0, width, height, 0x60000000, 0xA0000000);
        drawCenteredString(this.fontRenderer, "Select Language (page " + (page + 1) + ")", this.width / 2, 20, 0xFFFFFF);
        super.render(mx, my);
    }
}
