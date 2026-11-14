package net.classicremastered.minecraft;

import net.classicremastered.minecraft.chat.ChatInputScreen;
import net.classicremastered.minecraft.entity.BossFireball;
import net.classicremastered.minecraft.entity.SignEntity;
import net.classicremastered.minecraft.gamemode.CreativeGameMode;
import net.classicremastered.minecraft.level.itemstack.BowItem;
import net.classicremastered.minecraft.level.itemstack.BucketItem;
import net.classicremastered.minecraft.level.itemstack.GrapplingHookItem;
import net.classicremastered.minecraft.level.itemstack.GravityGunItem;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.level.itemstack.MobWandItem;
import net.classicremastered.minecraft.level.itemstack.PhysicsGunItem;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.level.tile.FireBlock;
import net.classicremastered.minecraft.level.tile.FlowerBlock;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.Villager;
import net.classicremastered.minecraft.player.DeveloperPowers;
import net.classicremastered.util.MathHelper;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;

public class InputManager {
    private static final int USE_INTERVAL = 4;
    private final Minecraft mc;
    private final PlayerCombat combat;
    private Item activeUse;
    private boolean attackHeld;
    private boolean useHeld;
    private int lastAttackTick;
    private int lastUseTick;

    public InputManager(Minecraft mc) {
        this.mc = mc;
        this.combat = new PlayerCombat(mc);
    }

    public float attackCharge(float partial) {
        return combat.enabled() ? combat.charge(partial) : 1.0f;
    }

    void pressAttack() {
        if (!canUse()) return;
        attackHeld = true;
        lastAttackTick = mc.ticks;
        onMouseClick(0);
    }

    void tickAttack(boolean down) {
        combat.updateHeldItem();
        if (!down || !canUse()) {
            attackHeld = false;
            return;
        }
        float interval =
                mc.selected != null
                                && mc.selected.entityPos == 1
                                && mc.player.developerPowers().fastPunch(mc.player)
                        ? 3
                        : mc.timer.tps / 4f;
        if (combat.enabled()
                && !mc.player.developerPowers().fastPunch(mc.player)
                && (mc.selected == null || mc.selected.entityPos == 1)) {
            return;
        }
        if (attackHeld && mc.ticks - lastAttackTick >= interval) pressAttack();
    }

    void pressUse() {
        if (!canUse()) return;
        useHeld = true;
        useSelected();
    }

    void releaseUse() {
        useHeld = false;
        if (activeUse != null) {
            activeUse.releaseUse(mc.player, mc.level);
            activeUse = null;
        }
    }

    void tickUse(boolean down) {
        if (!canUse()) {
            BowItem.cancelDraw(mc.player);
            releaseUse();
            return;
        }
        BowItem.cancelIfNotSelected(mc.player);
        Item held = selectedItem();
        if (activeUse != held && activeUse != null) {
            activeUse.releaseUse(mc.player, mc.level);
            activeUse = null;
        }
        if (!down) releaseUse();
        if (useHeld && mc.ticks - lastUseTick >= USE_INTERVAL && repeatsUse(held)) {
            useSelected();
            if (!canUse()) return;
            held = selectedItem();
        }
        if (held != null
                && (useHeld || held instanceof GravityGunItem || held instanceof PhysicsGunItem)) {
            held.tick(mc.player, mc.level);
        }
    }

    private boolean canUse() {
        return mc.currentScreen == null
                && mc.hasMouse
                && !mc.gamemode.isSpectator()
                && mc.level != null
                && mc.player != null
                && mc.player.health > 0;
    }

    private Item selectedItem() {
        int index = mc.player.inventory.getSelected() - 256;
        return index >= 0 && index < Item.items.length ? Item.items[index] : null;
    }

    private void useSelected() {
        lastUseTick = mc.ticks;
        Item held = selectedItem();
        if (mc.selected != null
                && mc.selected.entity
                        instanceof net.classicremastered.minecraft.entity.BoatEntity boat
                && !(held instanceof GrapplingHookItem
                        || held instanceof GravityGunItem
                        || held instanceof PhysicsGunItem)) {
            if (!mc.isOnline() && !mc.level.isNetworkMode() && mc.entityWithinReach(boat))
                boat.board(mc.player);
            return;
        }
        onMouseClick(1);
        if (held != null && canUse() && selectedItem() == held) {
            activeUse = held;
            held.use(mc.player, mc.level);
        }
    }

    private static boolean repeatsUse(Item item) {
        return !(item instanceof BowItem
                || item instanceof BucketItem
                || item instanceof GravityGunItem
                || item instanceof PhysicsGunItem
                || item instanceof GrapplingHookItem
                || item instanceof MobWandItem
                || item instanceof net.classicremastered.minecraft.level.itemstack.BoatItem);
    }

    public void onMouseClick(int button) {
        if (mc.gamemode.isSpectator()) return;
        // button: 0 = left, 1 = right

        final int selId = mc.player.inventory.getSelected();
        if (button == 0
                && selId >= 256
                && selId - 256 < Item.items.length
                && Item.items[selId - 256]
                        instanceof MobWandItem)
            return;

        DeveloperPowers powers = mc.player.developerPowers();
        if (powers.aimingBlock(mc.player)) return;
        if (button == 0) powers.swingPunch(mc.player);
        if (button == 0
                && mc.selected != null
                && mc.selected.entity
                        instanceof net.classicremastered.minecraft.mob.SkeletonTrap trap
                && trap.isScattered()) {
            if (mc.entityWithinReach(trap)) trap.breakBone(mc.player, mc.selected.bone);
            return;
        }
        if (button == 1 && powers.active(mc.player) && DeveloperPowers.isSword(selId)) {
            powers.swordAttack(
                    mc.player,
                    selId,
                    Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                            || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT));
            return;
        }

        if (selId == 266) {
            if (button == 0) PhysicsGunItem.launch(mc.player);
            return;
        }

        if (selId == GravityGunItem.RAW_ID
                || button == 1
                        && (selectedItem() instanceof GrapplingHookItem
                                || selectedItem()
                                        instanceof
                                        net.classicremastered.minecraft.level.itemstack.BoatItem)) {
            if (button == 0) {
                GravityGunItem.launch(mc.player, mc.level);
            }
            if (mc.renderer != null && mc.renderer.heldBlock != null) {
                mc.renderer.heldBlock.offset = -1;
                mc.renderer.heldBlock.moving = true;
            }
            return;
        }

        // --- LEFT CLICK: swing animation ---
        if (button == 0 && mc.renderer != null && mc.renderer.heldBlock != null) {
            mc.renderer.heldBlock.offset = -1; // fixed
            mc.renderer.heldBlock.moving = true; // fixed
        }

        // Item behavior is dispatched by useSelected after world interactions.
        if (button == 1 && selId >= 256) { // fixed
            if (mc.renderer != null && mc.renderer.heldBlock != null) {
                mc.renderer.heldBlock.offset = -1; // fixed
                mc.renderer.heldBlock.moving = true; // fixed
            }
        }

        if (button == 1 && mc.selected != null) {
            if (mc.selected.entity
                    instanceof net.classicremastered.minecraft.mob.Defender defender) {
                if (mc.entityWithinReach(defender) && defender.canControl(mc.player))
                    mc.setCurrentScreen(
                            new net.classicremastered.minecraft.gui.DefenderEquipmentScreen(
                                    defender));
                return;
            }
            if (mc.selected.entity instanceof Villager villager) {
                if (mc.entityWithinReach(villager) && !mc.level.isNetworkMode())
                    villager.onRightClick(mc.player);
                return;
            }
            if (mc.selected.entity instanceof SignEntity sign) {
                if (!mc.level.isNetworkMode() && !mc.isOnline() && mc.entityWithinReach(sign)) {
                    mc.setCurrentScreen(
                            new net.classicremastered.minecraft.gui.SignEditScreen(sign));
                }
                return;
            }
            if (mc.selected.entity
                    instanceof net.classicremastered.minecraft.mob.CrossbowTurret turret) {
                if (mc.entityWithinReach(turret)) {
                    net.classicremastered.minecraft.level.itemstack.TurretItems.interact(
                            mc.player, turret, selId);
                }
                return;
            }
            if (mc.selected.entityPos == 0
                    && net.classicremastered.minecraft.level.itemstack.TurretItems.isPlacementItem(
                            selId)) {
                net.classicremastered.minecraft.level.itemstack.TurretItems.place(
                        mc.player,
                        mc.selected.x,
                        mc.selected.y,
                        mc.selected.z,
                        mc.selected.face,
                        selId);
                return;
            }
        }

        // ============== ENTITY INTERACTION ==============
        if (mc.selected != null && mc.selected.entityPos == 1) {
            if (button == 1
                    && selId == net.classicremastered.minecraft.level.itemstack.SaddleItem.ID + 256
                    && mc.selected.entity instanceof net.classicremastered.minecraft.mob.Cow cow
                    && mc.entityWithinReach(cow)) {
                net.classicremastered.minecraft.level.itemstack.SaddleItem.equip(mc.player, cow);
                return;
            }
            if (button == 1
                    && selId == Item.FLINT_AND_STEEL.id + 256
                    && mc.selected.entity instanceof Mob target) {
                if (!mc.level.isNetworkMode()
                        && mc.entityWithinReach(target)
                        && (target instanceof net.classicremastered.minecraft.mob.Creeper creeper
                                ? creeper.igniteFuse()
                                : target.ignite(100))) {
                    mc.level.playSound("random/click", target, .8f, 1f);
                }
                return;
            }

            if (button == 1
                    && selId <= 0
                    && !mc.level.isNetworkMode()
                    && mc.entityWithinReach(mc.selected.entity)
                    && net.classicremastered.minecraft.mob.ai.MobRiding.canRide(
                            mc.player, mc.selected.entity)) {
                mc.player.isFlying = false;
                mc.player.mount(mc.selected.entity);
                return;
            }

            // Right click on villager → trade
            if (mc.selected.entity instanceof Villager && button == 1) {
                ((Villager) mc.selected.entity).onRightClick(mc.player);
                return;
            }

            // Left click → damage any entity (including villager)
            if (button == 0) {
                if (!mc.entityWithinReach(mc.selected.entity)) return;
                if (mc.selected.entity instanceof BossFireball fireball) {
                    fireball.deflect(mc.player);
                    return;
                }
                int dmg = net.classicremastered.minecraft.level.itemstack.Equipment.damage(selId);
                if (powers.fastPunch(mc.player)) powers.punch(mc.player, mc.selected.entity, dmg);
                else if (combat.enabled()) combat.attack(mc.selected.entity);
                else mc.selected.entity.hurt(mc.player, dmg);
            }
            return;
        }

        if (button == 0 && mc.selected == null && combat.enabled()) combat.miss();

        if (button == 1
                && FlowerBlock.isFlower(selId)
                && mc.level != null
                && !mc.level.isNetworkMode()
                && !mc.isOnline()) {
            MovingObjectPosition water = ClientTargetPicker.pickWater(mc.renderer);
            if (water != null) {
                placeSelectedBlock(selId, selId, water.x, water.y, water.z, water.face);
                return;
            }
        }

        // ============== BLOCK INTERACTION ==============
        if (mc.selected != null && mc.selected.entityPos == 0) {
            final int ox = mc.selected.x;
            final int oy = mc.selected.y;
            final int oz = mc.selected.z;
            final int face = mc.selected.face;

            // 1) RMB: let the targeted block handle use (e.g. TNT)
            if (button == 1) {
                int tid = mc.level.getTile(ox, oy, oz);
                Block tgt = tid > 0 ? Block.blocks[tid] : null;
                if (tgt != null && tgt.onRightClick(mc.level, ox, oy, oz, mc.player, face)) return;
                if (Item.OAK_SIGN
                                instanceof
                                net.classicremastered.minecraft.level.itemstack.SignItem sign
                        && selId == sign.id + 256) {
                    sign.place(mc.player, ox, oy, oz, face);
                    return;
                }
            }

            // 2) RMB + Flint&Steel (allow alongside item swing; main loop still calls
            // use())
            if (button == 1 && selId >= 256) {
                int itemIdx = selId - 256;
                if (itemIdx
                        == net.classicremastered.minecraft.level.itemstack.Item.FLINT_AND_STEEL
                                .id) {
                    int targetedId = mc.level.getTile(ox, oy, oz);
                    if (targetedId == Block.TNT.id) {
                        net.classicremastered.minecraft.level.tile.TNTBlock.ignite(
                                mc.level, ox, oy, oz, mc.player);
                        return;
                    }
                    if (targetedId == Block.SNOW_LAYER.id) {
                        if (mc.level.setTile(ox, oy, oz, 0)) {
                            FireBlock.playExtinguishSound(mc.level, ox + .5f, oy + .5f, oz + .5f);
                        }
                        return;
                    }

                    if (mc.level != null) {
                        float base = 0.595F;
                        float pitch =
                                MathHelper.clamp(
                                        base + (MathHelper.random.nextFloat() - 0.5F) * 0.10F,
                                        0.50F,
                                        0.70F);
                        mc.level.playSound(
                                "random/click", ox + 0.5F, oy + 0.5F, oz + 0.5F, 0.8F, pitch);
                    }

                    int nx = ox, ny = oy, nz = oz;
                    switch (face) {
                        case 0:
                            ny--;
                            break;
                        case 1:
                            ny++;
                            break;
                        case 2:
                            nz--;
                            break;
                        case 3:
                            nz++;
                            break;
                        case 4:
                            nx--;
                            break;
                        case 5:
                            nx++;
                            break;
                        default:
                            return;
                    }

                    if (!mc.level.isInBounds(nx, ny, nz)) return;
                    if (mc.level.getTile(nx, ny, nz) != 0) return;

                    // Ensure fire logic agrees with block attachment
                    if (!FireBlock.canStay(mc.level, nx, ny, nz)) return;

                    if (mc.gamemode.tryPlaceBlock(mc.player, nx, ny, nz, Block.FIRE.id)) {
                        mc.level.playSound(
                                "random/fuse", nx + 0.5f, ny + 0.5f, nz + 0.5f, 1.0f, 1.0f);
                    }
                    return;
                }
            }

            // 3) LMB break / RMB place block
            int x = ox, y = oy, z = oz;
            if (button == 1 && mc.level.getTile(ox, oy, oz) != Block.SNOW_LAYER.id) {
                switch (face) {
                    case 0:
                        --y;
                        break;
                    case 1:
                        ++y;
                        break;
                    case 2:
                        --z;
                        break;
                    case 3:
                        ++z;
                        break;
                    case 4:
                        --x;
                        break;
                    case 5:
                        ++x;
                        break;
                }
            }

            if (button == 0) { // breaking
                Block target = Block.blocks[mc.level.getTile(x, y, z)];
                if (target != null) {
                    if (target != Block.BEDROCK || (mc.gamemode instanceof CreativeGameMode)) {
                        mc.gamemode.hitBlock(x, y, z);
                    }
                }
                return;
            }

            int placeId = selId == Item.REDSTONE_DUST_ID + 256 ? Block.REDSTONE_WIRE.id : selId;
            if (button == 1 && placeId > 0 && placeId < 256 && Block.blocks[placeId] != null) {
                placeSelectedBlock(selId, placeId, x, y, z, face);
            }
        }
    }

    private void placeSelectedBlock(int selectedId, int blockId, int x, int y, int z, int face) {
        if (!mc.gamemode.canPlace(selectedId) || !mc.level.isInBounds(x, y, z)) return;
        if (mc.wouldCollideWithPlayer(x, y, z) || mc.wouldCollideWithMob(x, y, z)) return;
        if (mc.gamemode.tryPlaceBlock(mc.player, x, y, z, blockId, face)) {
            mc.renderer.heldBlock.pos = 0.0F;
            if (!(mc.gamemode instanceof CreativeGameMode)) mc.player.inventory.removeSelected(1);
        }
    }

    private boolean debugKeyHeld;
    private boolean debugShortcutUsed;

    private boolean handleDebugKey() {
        int key = Keyboard.getEventKey();
        boolean down = Keyboard.getEventKeyState();
        if (key == Keyboard.KEY_F3 && !down && debugKeyHeld) {
            debugKeyHeld = false;
            if (!debugShortcutUsed && mc.currentScreen == null) mc.debugOverlay = !mc.debugOverlay;
            return true;
        }
        if (mc.currentScreen != null) return false;
        if (key == Keyboard.KEY_F3 && down) {
            if (!Keyboard.isRepeatEvent()) {
                debugKeyHeld = true;
                debugShortcutUsed = false;
            }
            return true;
        }
        if (down
                && debugKeyHeld
                && Keyboard.isKeyDown(Keyboard.KEY_F3)
                && (key == Keyboard.KEY_N || key == Keyboard.KEY_B || key == Keyboard.KEY_P)) {
            if (!Keyboard.isRepeatEvent()) {
                debugShortcutUsed = true;
                if (key == Keyboard.KEY_N) {
                    mc.toggleSpectatorMode();
                } else if (key == Keyboard.KEY_P) {
                    mc.debugPaths = !mc.debugPaths;
                    mc.hud.addChat(mc.debugPaths ? "&eMob paths shown." : "&eMob paths hidden.");
                } else {
                    mc.debugHitboxes = !mc.debugHitboxes;
                    mc.hud.addChat(mc.debugHitboxes ? "&eHitboxes shown." : "&eHitboxes hidden.");
                }
            }
            return true;
        }
        return false;
    }

    public void handleKeyboardEvent() {
        if (handleDebugKey()) return;
        if (!mc.gamemode.isSpectator() && handlePowerKey()) return;

        // Pass key state to the player
        mc.player.setKey(Keyboard.getEventKey(), Keyboard.getEventKeyState());

        if (Keyboard.getEventKeyState()) {
            // If a GUI screen is open, pass the event there
            if (mc.currentScreen != null) {
                mc.currentScreen.keyboardEvent();
            }

            // --- Game-wide keys (only when no screen is open) ---
            if (mc.currentScreen == null) {
                if (Keyboard.getEventKey() == Keyboard.KEY_F1 && !Keyboard.isRepeatEvent()) {
                    mc.hudHidden = !mc.hudHidden;
                }

                // ESC → pause
                if (Keyboard.getEventKey() == Keyboard.KEY_ESCAPE) {
                    mc.pause();
                }

                // Double-tap SPACE → toggle flight
                if (Keyboard.getEventKey() == mc.settings.jumpKey.key) {
                    boolean isRepeat = false;
                    try {
                        isRepeat = Keyboard.isRepeatEvent();
                    } catch (Throwable ignored) {
                    }
                    if (!isRepeat
                            && mc.gamemode
                                    instanceof
                                    net.classicremastered.minecraft.gamemode.CreativeGameMode
                            && mc.player != null
                            && mc.player.canFly) {

                        int now = mc.ticks;
                        if (mc.jumpTapArmed && (now - mc.lastJumpTapTick) <= mc.jumpTapWindow) {
                            mc.player.isFlying = !mc.player.isFlying;
                            if (!mc.player.isFlying) mc.player.yd = 0.0F;
                            mc.jumpTapArmed = false;
                            mc.lastJumpTapTick = -10000;
                        } else {
                            mc.jumpTapArmed = true;
                            mc.lastJumpTapTick = now;
                        }
                    }
                }
                if (Keyboard.getEventKey() == mc.settings.flyToggleKey.key) {
                    if (mc.gamemode
                                    instanceof
                                    net.classicremastered.minecraft.gamemode.CreativeGameMode
                            && mc.player != null) {
                        mc.player.canFly = true;
                        mc.player.isFlying = !mc.player.isFlying;
                        if (!mc.player.isFlying) {
                            mc.player.yd = 0.0F;
                            mc.player.fallDistance = 0.0F;
                            mc.player.onGround = true;
                        }
                    }
                }

                if (Keyboard.getEventKey() == Keyboard.KEY_Q) {
                    if (mc.player != null) {
                        boolean wholeStack =
                                Keyboard.isKeyDown(Keyboard.KEY_LCONTROL)
                                        || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
                        mc.player.dropSelectedItem(wholeStack);
                    }
                }
                if (Keyboard.getEventKey() == Keyboard.KEY_F5) {
                    mc.cameraMode = (mc.cameraMode + 1) % 3;
                }

                // F6 → fast debug toggle
                if (Keyboard.getEventKey() == Keyboard.KEY_F6) {
                    mc.fastDebugMode = !mc.fastDebugMode;
                    mc.settings.limitFramerate = !mc.fastDebugMode;
                    try {
                        Display.setVSyncEnabled(!mc.fastDebugMode);
                    } catch (Throwable ignored) {
                    }
                    if (mc.hud != null)
                        mc.hud.addChat(mc.fastDebugMode ? "&aFastDebug ON" : "&cFastDebug OFF");
                }

                // Save/load pos (Creative only)
                if (mc.gamemode
                        instanceof net.classicremastered.minecraft.gamemode.CreativeGameMode) {
                    if (Keyboard.getEventKey() == mc.settings.loadLocationKey.key) {
                        mc.player.resetPos();
                    }
                    if (Keyboard.getEventKey() == mc.settings.saveLocationKey.key) {
                        mc.level.setSpawnPos(
                                (int) mc.player.x,
                                (int) mc.player.y,
                                (int) mc.player.z,
                                mc.player.yRot);
                        mc.player.resetPos();
                    }
                }

                // Slash → open chat
                if (Keyboard.getEventKey() == Keyboard.KEY_SLASH) {
                    mc.player.releaseAllKeys();
                    ChatInputScreen screen = new ChatInputScreen();
                    mc.setCurrentScreen(screen);
                    screen.insertText("/");
                }

                if (Keyboard.getEventKey() == Keyboard.KEY_F2) {
                    mc.pendingScreenshot = true;
                }

                // Inventory
                if (Keyboard.getEventKey() == mc.settings.inventoryKey.key) {
                    mc.gamemode.openInventory();
                }

                // Chat
                if (Keyboard.getEventKey() == mc.settings.chatKey.key) {
                    mc.player.releaseAllKeys();
                    mc.setCurrentScreen(new ChatInputScreen());
                }
            }

            if (mc.gamemode.isSpectator()) return;

            // Number keys (hotbar)
            for (int i = 0; i < 9; ++i) {
                if (Keyboard.getEventKey() == i + 2) {
                    mc.player.inventory.selected = i;
                }
            }
        }
    }

    private boolean handlePowerKey() {
        if (Keyboard.getEventKey() == Keyboard.KEY_B && !Keyboard.getEventKeyState()) {
            if (mc.currentScreen == null && mc.hasMouse)
                mc.player.developerPowers().throwBlock(mc.player);
            else mc.player.developerPowers().cancelBlockAim();
        }
        if (mc.currentScreen == null
                && mc.hasMouse
                && Keyboard.getEventKeyState()
                && Keyboard.getEventKey() == Keyboard.KEY_G
                && !Keyboard.isRepeatEvent()
                && mc.player.developerPowers().active(mc.player)) {
            mc.player.developerPowers().kick(mc.player);
            return true;
        }
        if (mc.currentScreen == null
                && mc.hasMouse
                && Keyboard.getEventKeyState()
                && Keyboard.getEventKey() == Keyboard.KEY_B
                && !Keyboard.isRepeatEvent()
                && mc.player.developerPowers().active(mc.player)) {
            mc.player.developerPowers().beginBlockAim(mc.player);
            return true;
        }
        if (mc.currentScreen == null
                && mc.hasMouse
                && Keyboard.getEventKeyState()
                && Keyboard.getEventKey() == Keyboard.KEY_R
                && !Keyboard.isRepeatEvent()
                && mc.player.developerPowers().active(mc.player)
                && mc.player.input != null) {
            mc.player.input.updateMovement();
            mc.player.developerPowers().dodge(mc.player, mc.player.input.xxa, mc.player.input.yya);
            return true;
        }
        return false;
    }
}
