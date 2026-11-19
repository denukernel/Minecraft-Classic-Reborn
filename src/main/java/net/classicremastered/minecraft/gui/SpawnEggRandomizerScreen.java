package net.classicremastered.minecraft.gui;

import java.util.concurrent.ThreadLocalRandom;

import org.lwjgl.opengl.GL11;

import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.level.itemstack.SpawnEggLoadout;
import net.classicremastered.minecraft.level.itemstack.SpawnEggLoadout.Species;
import net.classicremastered.minecraft.level.tile.SpawnEggRandomizerBlock;
import net.classicremastered.minecraft.mob.Defender.Skill;
import net.classicremastered.minecraft.render.ShapeRenderer;

public final class SpawnEggRandomizerScreen extends GuiScreen {
    private static final int PANEL_WIDTH = 246;
    private static final int PANEL_HEIGHT = 132;
    private static final int DEFENDER_PROGRESS_HEIGHT = 57;
    private static final int REROLL = 3;
    private static final int SPAWN = 4;
    private static final int TOGGLE_SKILLS = 5;
    private static final int TOGGLE_COMBAT_XP = 6;
    private static final String[] SLOT_NAMES = {"Head", "Chest", "Legs", "Feet", "Weapon", "Item"};
    private static final Species[] SPECIES = Species.values();
    private static boolean randomizeSkills = true;
    private static boolean randomizeCombatExperience = true;
    private final Level level;
    private final int x, y, z;
    private SpawnEggLoadout loadout;
    private String message = "";
    private Button spawn;

    public SpawnEggRandomizerScreen(Level level, int x, int y, int z) {
        this.level = level;
        this.x = x;
        this.y = y;
        this.z = z;
        roll(Species.DEFENDER);
    }

    private int left() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int top() {
        return (height - panelHeight()) / 2;
    }

    private int panelHeight() {
        return PANEL_HEIGHT + (loadout.species() == Species.DEFENDER ? DEFENDER_PROGRESS_HEIGHT : 0);
    }

    @Override
    public void onOpen() {
        buttons.clear();
        for (Species species : SPECIES) {
            int tab = species.ordinal();
            buttons.add(new Button(tab, left() + 8 + tab * 78, top() + 23, 74, 20, species.label));
        }
        int actionY = top() + panelHeight() - 29;
        buttons.add(new Button(REROLL, left() + 8, actionY, 112, 20, "Randomize"));
        spawn = new Button(SPAWN, left() + 126, actionY, 112, 20, "Spawn");
        buttons.add(spawn);
        if (loadout.species() == Species.DEFENDER) {
            buttons.add(new Button(TOGGLE_SKILLS, left() + 8, top() + 88, 112, 20,
                    randomizeSkills ? "Skill ranks: On" : "Skill ranks: Off"));
            buttons.add(new Button(TOGGLE_COMBAT_XP, left() + 126, top() + 88, 112, 20,
                    randomizeCombatExperience ? "Combat XP: On" : "Combat XP: Off"));
        }
        updateSpawnButton();
    }

    private void roll(Species species) {
        loadout = new SpawnEggLoadout(species, ThreadLocalRandom.current());
        loadout.setProgressOptions(randomizeSkills, randomizeCombatExperience);
        message = "";
    }

    private void updateSpawnButton() {
        spawn.active = level.creativeMode || (minecraft.player != null && SpawnEggRandomizerBlock
                .findEgg(minecraft.player.inventory, loadout.species().eggId()) >= 0);
    }

    @Override
    protected void onButtonClick(Button button) {
        if (button.id < SPECIES.length) {
            roll(SPECIES[button.id]);
            onOpen();
            return;
        } else if (button.id == TOGGLE_SKILLS || button.id == TOGGLE_COMBAT_XP) {
            if (button.id == TOGGLE_SKILLS) {
                randomizeSkills = !randomizeSkills;
            } else {
                randomizeCombatExperience = !randomizeCombatExperience;
            }
            loadout.setProgressOptions(randomizeSkills, randomizeCombatExperience);
            onOpen();
            return;
        } else if (button.id == REROLL) {
            roll(loadout.species());
        } else if (SpawnEggRandomizerBlock.spawn(level, x, y, z, minecraft.player, loadout)) {
            minecraft.setCurrentScreen(null);
            return;
        } else {
            message = "Clear the space above the block.";
        }
        updateSpawnButton();
    }

    @Override
    public void tick() {
        if (!SpawnEggRandomizerBlock.canUse(level, x, y, z, minecraft.player)) {
            minecraft.setCurrentScreen(null);
            return;
        }
        updateSpawnButton();
    }

    @Override
    public void render(int mx, int my) {
        GL11.glPushAttrib(
                GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_TEXTURE_BIT);
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            int left = left(), top = top();
            int bottom = top + panelHeight();
            drawBox(0, 0, width, height, 0xA0000000);
            drawBox(left, top, left + PANEL_WIDTH, bottom, 0xFF171717);
            drawBox(left + 1, top + 1, left + PANEL_WIDTH - 1, bottom - 1, 0xFF686868);
            drawBox(left + 2, top + 2, left + PANEL_WIDTH - 3, bottom - 3, 0xFFFFFFFF);
            drawBox(left + 3, top + 3, left + PANEL_WIDTH - 3, bottom - 3, 0xFFC6C6C6);
            drawLabel("Spawn Egg Randomizer", left + PANEL_WIDTH / 2, top + 9);

            int egg = loadout.species().eggId();
            int slotY = top + 62;
            drawLabel("Egg", left + 21, top + 51);
            drawSlot(egg, left + 12, slotY, mx, my);
            drawBox(left + 36, slotY + 8, left + 49, slotY + 10, 0xFF5A5A5A);
            drawBox(left + 45, slotY + 5, left + 47, slotY + 13, 0xFF5A5A5A);
            drawBox(left + 47, slotY + 7, left + 49, slotY + 11, 0xFF5A5A5A);
            drawBox(left + 49, slotY + 8, left + 51, slotY + 10, 0xFF5A5A5A);

            String tooltip = inside(mx, my, left + 12, slotY, 18, 18) ? itemName(egg) : null;
            int slots = loadout.species() == Species.DEFENDER ? SLOT_NAMES.length : SLOT_NAMES.length - 1;
            for (int i = 0; i < slots; i++) {
                int slotX = left + 61 + i * 31;
                int raw = loadout.item(i);
                drawLabel(SLOT_NAMES[i], slotX + 9, top + 51);
                drawSlot(raw, slotX, slotY, mx, my);
                if (inside(mx, my, slotX, slotY, 18, 18))
                    tooltip = itemName(raw);
            }

            String status = message;
            if (status.isEmpty()) {
                status = level.creativeMode
                        ? "Spawns above the block."
                        : spawn.active ? "Uses 1 matching spawn egg." : "A matching spawn egg is required.";
            }
            if (loadout.species() == Species.DEFENDER)
                drawProgress(left + PANEL_WIDTH / 2, top + 115);
            drawLabel(status, left + PANEL_WIDTH / 2, bottom - 44);
            for (Object entry : buttons)
                drawButton((Button) entry, mx, my);
            if (tooltip != null)
                drawTooltip(tooltip, mx, my);
        } finally {
            GL11.glPopAttrib();
        }
    }

    private void drawProgress(int center, int y) {
        drawLabel("Vitality " + loadout.skillRank(Skill.VITALITY) + "   Guard "
                + loadout.skillRank(Skill.GUARD) + "   Combat " + loadout.skillRank(Skill.COMBAT) + "   XP "
                + loadout.skillRank(Skill.COMBAT_EXPERIENCE), center, y);
        String utility = "None";
        if (loadout.skillRank(Skill.WATER_CLUTCH) > 0) {
            utility = "MLG Water Clutch";
        } else if (loadout.skillRank(Skill.ENDER_PEARL_USE) > 0) {
            utility = Skill.ENDER_PEARL_USE.label;
        }
        String arrows = loadout.arrowCount() > 0 ? "   Arrows " + loadout.arrowCount() : "";
        drawLabel("Utility skill: " + utility + arrows, center, y + 13);
    }

    private void drawLabel(String label, int x, int y) {
        fontRenderer.renderNoShadow(label, x - fontRenderer.getWidth(label) / 2, y, 0xFF404040);
    }

    private void drawSlot(int raw, int x, int y, int mx, int my) {
        drawBox(x, y, x + 18, y + 18, 0xFFFFFFFF);
        drawBox(x, y, x + 17, y + 17, 0xFF373737);
        drawBox(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
        if (raw > 0) {
            GL11.glPushMatrix();
            GL11.glTranslatef(x + 1, y + 1, 0);
            GL11.glColor4f(1, 1, 1, 1);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            Item.items[raw - 256].renderIcon(minecraft.textureManager, ShapeRenderer.instance);
            GL11.glPopMatrix();
        }
        if (inside(mx, my, x, y, 18, 18))
            drawBox(x + 1, y + 1, x + 17, y + 17, 0x42FFFFFF);
    }

    private void drawButton(Button button, int mx, int my) {
        int x = button.x, y = button.y, w = button.width, h = button.height;
        boolean highlighted = button.id == loadout.species().ordinal() || button.id == SPAWN
                || button.id == TOGGLE_SKILLS && randomizeSkills
                || button.id == TOGGLE_COMBAT_XP && randomizeCombatExperience;
        boolean hovered = button.active && inside(mx, my, x, y, w, h);
        int face = !button.active ? 0xFF686868 : highlighted ? 0xFF447A35 : 0xFF747474;
        if (hovered)
            face = highlighted ? 0xFF569743 : 0xFF929292;
        drawBox(x, y, x + w, y + h, 0xFF181818);
        drawBox(x + 1, y + 1, x + w - 1, y + h - 1, highlighted ? 0xFF2F4A28 : 0xFF454545);
        drawBox(x + 1, y + 1, x + w - 1, y + 2, highlighted && button.active ? 0xFF87AE72 : 0xFFADADAD);
        drawBox(x + 2, y + 2, x + w - 2, y + h - 3, face);
        drawCenteredString(fontRenderer, button.text, x + w / 2, y + 6,
                button.active ? 0xFFFFFFFF : 0xFFB6B6B6);
    }

    private void drawTooltip(String text, int mx, int my) {
        int w = fontRenderer.getWidth(text);
        int x = Math.max(4, Math.min(mx + 9, width - w - 5));
        int y = Math.max(4, my - 13);
        drawBox(x - 3, y - 3, x + w + 3, y + 10, 0xF0140D20);
        drawString(fontRenderer, text, x, y, 0xFFFFFFFF);
    }

    private static String itemName(int raw) {
        return raw == 0 ? "Empty" : Item.items[raw - 256].name;
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
