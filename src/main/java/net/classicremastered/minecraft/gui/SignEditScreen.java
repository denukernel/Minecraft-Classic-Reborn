package net.classicremastered.minecraft.gui;

import net.classicremastered.minecraft.entity.SignEntity;
import org.lwjgl.input.Keyboard;

public final class SignEditScreen extends GuiScreen {
    private final SignEntity sign;
    private final String[] draft = new String[SignEntity.LINE_COUNT];
    private int row;

    public SignEditScreen(SignEntity sign) {
        this.sign = sign;
        for (int i = 0; i < draft.length; i++) draft[i] = sign.line(i);
    }

    @Override
    public void onOpen() {
        buttons.clear();
        buttons.add(new Button(0, width / 2 - 100, height / 2 + 47, "Done"));
    }

    @Override
    protected void onKeyPress(char character, int key) {
        if (key == Keyboard.KEY_ESCAPE) {
            minecraft.setCurrentScreen(null);
        } else if (key == Keyboard.KEY_UP) {
            row = Math.floorMod(row - 1, draft.length);
        } else if (key == Keyboard.KEY_RETURN
                || key == Keyboard.KEY_DOWN
                || key == Keyboard.KEY_TAB) {
            row = (row + 1) % draft.length;
        } else if (key == Keyboard.KEY_BACK && !draft[row].isEmpty()) {
            draft[row] = draft[row].substring(0, draft[row].length() - 1);
        } else if (character >= 32
                && character < 127
                && draft[row].length() < SignEntity.LINE_LENGTH) {
            draft[row] += character;
        }
    }

    @Override
    protected void onMouseClick(int x, int y, int button) {
        int top = height / 2 - 31;
        if (button == 0 && x >= width / 2 - 66 && x < width / 2 + 66 && y >= top && y < top + 64) {
            row = (y - top) / 16;
        }
        super.onMouseClick(x, y, button);
    }

    @Override
    protected void onButtonClick(Button button) {
        if (sign.level == minecraft.level && !sign.removed) {
            for (int i = 0; i < draft.length; i++) sign.setLine(i, draft[i]);
        }
        minecraft.setCurrentScreen(null);
    }

    @Override
    public void tick() {
        if (sign.removed || sign.level != minecraft.level) minecraft.setCurrentScreen(null);
    }

    @Override
    public void render(int mx, int my) {
        int center = width / 2;
        int top = height / 2 - 31;
        drawBox(0, 0, width, height, 0x80000000);
        drawCenteredString(fontRenderer, "Edit Sign", center, top - 23, 0xFFFFFF);
        drawBox(center - 66, top - 2, center + 66, top + 64, 0xFFC69863);
        for (int i = 0; i < draft.length; i++) {
            if (i == row)
                drawBox(center - 64, top + i * 16, center + 64, top + i * 16 + 14, 0xFF9D744D);
            String text = draft[i] + (i == row ? "_" : "");
            fontRenderer.renderLiteral(
                    text,
                    center - fontRenderer.getLiteralWidth(text) / 2,
                    top + 3 + i * 16,
                    SignEntity.INK_COLOR);
        }
        super.render(mx, my);
    }
}
