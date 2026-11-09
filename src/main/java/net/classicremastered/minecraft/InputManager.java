package net.classicremastered.minecraft;

import net.classicremastered.minecraft.chat.ChatInputScreen;
import net.classicremastered.minecraft.entity.SignEntity;
import net.classicremastered.minecraft.gamemode.CreativeGameMode;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.level.tile.FireBlock;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.Villager;
import net.classicremastered.minecraft.player.DeveloperPowers;
import net.classicremastered.util.MathHelper;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;

public class InputManager {
    private final Minecraft mc;

    public InputManager(Minecraft mc) {
        this.mc = mc;
    }

    public void onMouseClick(int button) {
        // button: 0 = left, 1 = right

        final int selId = mc.player.inventory.getSelected();
        if (button == 0
                && selId >= 256
                && selId - 256 < Item.items.length
                && Item.items[selId - 256]
                        instanceof net.classicremastered.minecraft.level.itemstack.MobWandItem)
            return;

        DeveloperPowers powers = mc.player.developerPowers();
        if (powers.aimingBlock(mc.player)) return;
        if (button == 0) powers.swingPunch(mc.player);
        if (button == 1 && powers.active(mc.player) && DeveloperPowers.isSword(selId)) {
            powers.swordAttack(
                    mc.player,
                    selId,
                    Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                            || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT));
            return;
        }

        if (selId == 266) {
            if (button == 0)
                net.classicremastered.minecraft.level.itemstack.PhysicsGunItem.launch(mc.player);
            return;
        }

        // If holding Gravity Gun and clicking Left Click while carrying something, drop
        // gently
        if (selId >= 256 && (selId - 256) == 11) {
            if (button == 0) {
                net.classicremastered.minecraft.level.itemstack.GravityGunItem.dropGently(
                        mc.player);
                if (mc.renderer != null && mc.renderer.heldBlock != null) {
                    mc.renderer.heldBlock.offset = -1;
                    mc.renderer.heldBlock.moving = true;
                }
                return;
            }
        }

        // --- LEFT CLICK: swing animation ---
        if (button == 0 && mc.renderer != null && mc.renderer.heldBlock != null) {
            mc.renderer.heldBlock.offset = -1; // fixed
            mc.renderer.heldBlock.moving = true; // fixed
        }

        // --- RIGHT CLICK: items swing only (let main loop call use()/tick/releaseUse)
        // ---
        if (button == 1 && selId >= 256) { // fixed
            if (mc.renderer != null && mc.renderer.heldBlock != null) {
                mc.renderer.heldBlock.offset = -1; // fixed
                mc.renderer.heldBlock.moving = true; // fixed
            }
            // IMPORTANT: do NOT call held.use() here; main loop handles it to avoid
            // double-fire // fixed
            // (Exception: Flint & Steel block logic handled below; we don't return here)
        }

        // ============== ENTITY INTERACTION ==============
        if (mc.selected != null && mc.selected.entityPos == 1) {
            if (button == 1
                    && selId == Item.FLINT_AND_STEEL.id + 256
                    && mc.selected.entity instanceof Mob target) {
                if (!mc.level.isNetworkMode()
                        && mc.entityWithinReach(target)
                        && target.ignite(100)) {
                    mc.level.playSound("random/click", target, .8f, 1f);
                }
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
                int dmg = net.classicremastered.minecraft.level.itemstack.Equipment.damage(selId);
                if (powers.fastPunch(mc.player)) powers.punch(mc.player, mc.selected.entity, dmg);
                else mc.selected.entity.hurt(mc.player, dmg);
            }
            return;
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
            if (button == 1) {
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
                if (!mc.gamemode.canPlace(selId)) return;
                if (!mc.level.isInBounds(x, y, z)) return;

                if (mc.wouldCollideWithPlayer(x, y, z)) return;
                if (mc.wouldCollideWithMob(x, y, z)) return;

                if (mc.gamemode.tryPlaceBlock(mc.player, x, y, z, placeId, face)) {
                    mc.renderer.heldBlock.pos = 0.0F;

                    if (!(mc.gamemode instanceof CreativeGameMode)) {
                        mc.player.inventory.removeSelected(1);
                    }
                }
                return;
            }
        }
    }

    public void handleKeyboardEvent() {
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
            return;
        }
        if (mc.currentScreen == null
                && mc.hasMouse
                && Keyboard.getEventKeyState()
                && Keyboard.getEventKey() == Keyboard.KEY_B
                && !Keyboard.isRepeatEvent()
                && mc.player.developerPowers().active(mc.player)) {
            mc.player.developerPowers().beginBlockAim(mc.player);
            return;
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
            return;
        }

        // Pass key state to the player
        mc.player.setKey(Keyboard.getEventKey(), Keyboard.getEventKeyState());

        if (Keyboard.getEventKeyState()) {
            // If a GUI screen is open, pass the event there
            if (mc.currentScreen != null) {
                mc.currentScreen.keyboardEvent();
            }

            // --- Game-wide keys (only when no screen is open) ---
            if (mc.currentScreen == null) {
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
                if (Keyboard.getEventKey() == Keyboard.KEY_F7) { // F7 = spawn test sign
                    SignEntity s =
                            new SignEntity(
                                    mc, mc.player.x, mc.player.y, mc.player.z, mc.player.yRot);
                    mc.level.addEntity(s);
                    mc.hud.addChat("&aSpawned Sign at player");
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
                    java.io.File file =
                            net.classicremastered.minecraft.util.Screenshot.take(Minecraft.mcDir);
                    if (file != null) {
                        if (mc.hud != null) {
                            mc.hud.addChat(
                                    "&aScreenshot saved! &7(Type &e/screenshots&7 to open folder)");
                        }
                    } else {
                        if (mc.hud != null) {
                            mc.hud.addChat("&cFailed to save screenshot.");
                        }
                    }
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

            // Number keys (hotbar)
            for (int i = 0; i < 9; ++i) {
                if (Keyboard.getEventKey() == i + 2) {
                    mc.player.inventory.selected = i;
                }
            }

            // Fog toggle
            if (Keyboard.getEventKey() == mc.settings.toggleFogKey.key) {
                mc.settings.toggleSetting(
                        4,
                        !Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                                        && !Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)
                                ? 1
                                : -1);
            }
        }
    }
}
