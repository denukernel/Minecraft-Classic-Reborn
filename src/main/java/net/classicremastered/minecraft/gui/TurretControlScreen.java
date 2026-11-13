package net.classicremastered.minecraft.gui;

import net.classicremastered.minecraft.level.itemstack.TurretItems;
import net.classicremastered.minecraft.level.itemstack.TurretUpgrade;
import net.classicremastered.minecraft.mob.CrossbowTurret;
import net.classicremastered.minecraft.mob.MobRegistry;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TurretControlScreen extends GuiScreen {
    private static final int VISIBLE_TARGETS = 5;
    private static final int VISIBLE_UPGRADES = 5;
    private final CrossbowTurret turret;
    private final List<Map.Entry<Short, String>> targets =
            new ArrayList<>(MobRegistry.turretTargets().entrySet());
    private int tab;
    private int targetOffset;
    private int upgradeOffset;
    private int targetGroup;

    public TurretControlScreen(CrossbowTurret turret) {
        this.turret = turret;
        targets.sort(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER));
    }

    @Override
    public void onOpen() {
        refreshButtons();
    }

    private int left() {
        return width / 2 - 88;
    }

    private int top() {
        return height / 2 - 118;
    }

    private void refreshButtons() {
        buttons.clear();
        buttons.add(new Button(0, left() + 7, top() + 19, 50, 20, "Info"));
        buttons.add(new Button(1, left() + 59, top() + 19, 51, 20, "Targets"));
        buttons.add(new Button(2, left() + 112, top() + 19, 57, 20, "Upgrades"));
        if (tab == 0) {
            row(3, 4, "Toggle firing");
            row(4, 5, "Unload ammunition");
            row(5, 6, "Pack into crate");
        } else if (tab == 1) {
            String group =
                    targetGroup == 0
                            ? "All mobs"
                            : MobRegistry.TargetGroup.values()[targetGroup - 1].label;
            row(100, 0, "Group: " + group + " >");
            buttons.add(new Button(101, left() + 8, top() + 64, 78, 20, "Enable all"));
            buttons.add(new Button(102, left() + 90, top() + 64, 78, 20, "Disable all"));
            var filtered = filteredTargets();
            for (int i = 0; i < VISIBLE_TARGETS && targetOffset + i < filtered.size(); i++) {
                var target = filtered.get(targetOffset + i);
                buttons.add(
                        new Button(
                                10 + i,
                                left() + 8,
                                top() + 86 + i * 21,
                                160,
                                20,
                                (turret.targets(target.getKey()) ? "[x] " : "[ ] ")
                                        + target.getValue()));
            }
            buttons.add(new Button(6, left() + 10, top() + 198, 72, 20, "Previous"));
            buttons.add(new Button(7, left() + 94, top() + 198, 72, 20, "Next"));
        } else {
            for (int i = upgradeOffset;
                    i < Math.min(upgradeOffset + VISIBLE_UPGRADES, TurretUpgrade.COUNT);
                    i++) {
                row(
                        20 + i,
                        i - upgradeOffset,
                        (turret.upgraded(i) ? "Remove " : "Install ") + TurretUpgrade.at(i).label);
            }
            buttons.add(new Button(8, left() + 10, top() + 158, 72, 20, "Previous"));
            buttons.add(new Button(9, left() + 94, top() + 158, 72, 20, "Next"));
        }
    }

    private void row(int id, int index, String text) {
        buttons.add(new Button(id, left() + 8, top() + 42 + index * 21, 160, 20, text));
    }

    @Override
    public void render(int mouseX, int mouseY) {
        drawBox(0, 0, width, height, 0xB0000000);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, minecraft.textureManager.load("/turret/panel.png"));
        GL11.glColor4f(1, 1, 1, 1);
        drawImage(left(), top(), 0, 0, 176, 236);
        drawCenteredString(
                fontRenderer,
                turret.variant.label + " Turret (T1)",
                width / 2,
                top() + 6,
                0xFFFFFF);
        if (tab == 0) {
            drawCenteredString(
                    fontRenderer,
                    "Health " + turret.health + " / " + turret.maxHealth(),
                    width / 2,
                    top() + 51,
                    0x303030);
            drawCenteredString(
                    fontRenderer,
                    (turret.variant == net.classicremastered.minecraft.mob.TurretVariant.ROVER
                                    ? (turret.ammo == 0
                                            ? "Bolts or bullets "
                                            : turret.usesBullets() ? "Bullets " : "Bolts ")
                                    : "Ammo ")
                            + turret.ammo
                            + " / "
                            + turret.ammoCapacity(),
                    width / 2,
                    top() + 68,
                    0x303030);
            drawCenteredString(
                    fontRenderer, turret.firingStatus(), width / 2, top() + 85, 0x303030);
            drawCenteredString(
                    fontRenderer,
                    "Range " + turret.firingRange() + " blocks",
                    width / 2,
                    top() + 102,
                    0x303030);
        }
        super.render(mouseX, mouseY);
    }

    @Override
    public void mouseEvent() {
        if (tab == 1 && Mouse.getEventDWheel() != 0) {
            scroll(-Integer.signum(Mouse.getEventDWheel()));
        }
        super.mouseEvent();
    }

    private void scroll(int direction) {
        targetOffset =
                Math.max(
                        0,
                        Math.min(
                                targetOffset + direction,
                                Math.max(0, filteredTargets().size() - VISIBLE_TARGETS)));
        refreshButtons();
    }

    private List<Map.Entry<Short, String>> filteredTargets() {
        if (targetGroup == 0) return targets;
        var group = MobRegistry.TargetGroup.values()[targetGroup - 1];
        return targets.stream()
                .filter(target -> MobRegistry.turretTargetGroup(target.getKey()) == group)
                .toList();
    }

    @Override
    protected void onButtonClick(Button button) {
        if (!turret.canControl(minecraft.player)) {
            minecraft.setCurrentScreen(null);
            return;
        }
        int id = button.id;
        if (id < 3) {
            tab = id;
        } else if (id == 3) {
            turret.enabled = !turret.enabled;
        } else if (id == 4) {
            turret.unload(minecraft.player);
        } else if (id == 5) {
            if (TurretItems.pack(minecraft.player, turret)) {
                minecraft.setCurrentScreen(null);
                return;
            }
            message("An empty crate and room for a packed turret are required.");
        } else if (id == 6 || id == 7) {
            scroll(id == 6 ? -VISIBLE_TARGETS : VISIBLE_TARGETS);
        } else if (id == 8 || id == 9) {
            upgradeOffset =
                    Math.max(
                            0,
                            Math.min(
                                    upgradeOffset
                                            + (id == 8 ? -VISIBLE_UPGRADES : VISIBLE_UPGRADES),
                                    (TurretUpgrade.COUNT - 1)
                                            / VISIBLE_UPGRADES
                                            * VISIBLE_UPGRADES));
        } else if (id == 100) {
            targetGroup = (targetGroup + 1) % (MobRegistry.TargetGroup.values().length + 1);
            targetOffset = 0;
        } else if (id == 101 || id == 102) {
            turret.setTargets(
                    filteredTargets().stream().map(Map.Entry::getKey).toList(), id == 101);
        } else if (id < 20) {
            turret.toggleTarget(filteredTargets().get(targetOffset + id - 10).getKey());
        } else {
            int upgrade = id - 20;
            if (!turret.changeUpgrade(minecraft.player, upgrade, !turret.upgraded(upgrade))) {
                message("Check required upgrades, inventory space, and loaded ammunition.");
            }
        }
        refreshButtons();
    }

    private void message(String text) {
        if (minecraft.hud != null) minecraft.hud.addChat(text);
    }
}
