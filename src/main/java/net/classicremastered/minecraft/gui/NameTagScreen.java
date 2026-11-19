package net.classicremastered.minecraft.gui;

import net.classicremastered.minecraft.level.itemstack.NameTagItem;
import net.classicremastered.minecraft.mob.Mob;

import org.lwjgl.input.Keyboard;

public final class NameTagScreen extends GuiScreen {
    private final NameTagItem tag;
    private final Mob mob;
    private String name = "";
    private Button apply;

    public NameTagScreen(NameTagItem tag, Mob mob) {
        this.tag = tag;
        this.mob = mob;
    }

    @Override
    public void onOpen() {
        Keyboard.enableRepeatEvents(true);
        buttons.clear();
        apply = new Button(0, width / 2 - 100, height / 2 + 16, "Apply Name Tag");
        apply.active = !name.isBlank();
        buttons.add(apply);
    }

    @Override
    public void onClose() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void onKeyPress(char character, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            super.onKeyPress(character, key);
        } else if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            if (apply.active)
                onButtonClick(apply);
        } else if (key == Keyboard.KEY_BACK && !name.isEmpty()) {
            name = name.substring(0, name.length() - 1);
        } else if (!NameTagItem.isUnsupportedCharacter(character)
                && name.length() < NameTagItem.MAX_NAME_LENGTH
                && fontRenderer.getLiteralWidth(name + character + "_") <= 188) {
            name += character;
        }
        apply.active = !name.isBlank() && !name.strip().equals(mob.customName);
    }

    @Override
    protected void onButtonClick(Button button) {
        if (tag.apply(minecraft, mob, name) || !tag.canApply(minecraft, mob))
            minecraft.setCurrentScreen(null);
    }

    @Override
    public void tick() {
        if (!tag.canApply(minecraft, mob))
            minecraft.setCurrentScreen(null);
    }

    @Override
    public void render(int mouseX, int mouseY) {
        int center = width / 2;
        int middle = height / 2;
        drawBox(0, 0, width, height, 0x80000000);
        drawCenteredString(fontRenderer, "Name Mob", center, middle - 32, 0xFFFFFF);
        drawBox(center - 100, middle - 15, center + 100, middle + 5, 0xFF888888);
        drawBox(center - 99, middle - 14, center + 99, middle + 4, 0xFF000000);
        fontRenderer.renderLiteral(name + "_", center - 95, middle - 9, 0xFFFFFF);
        super.render(mouseX, mouseY);
    }
}
