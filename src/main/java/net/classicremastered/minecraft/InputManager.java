package net.classicremastered.minecraft;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.chat.ChatInputScreen;
import net.classicremastered.minecraft.gamemode.CreativeGameMode;
import net.classicremastered.minecraft.item.SignEntity;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.Villager;
import net.classicremastered.util.MathHelper;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import org.lwjgl.input.Mouse;

public class InputManager {
    private final Minecraft mc;

    public InputManager(Minecraft mc) {
        this.mc = mc;
    }

    public void onMouseClick(int button) {
        // button: 0 = left, 1 = right

        // --- LEFT CLICK: swing animation ---
        if (button == 0 && mc.renderer != null && mc.renderer.heldBlock != null) {
            mc.renderer.heldBlock.offset = -1; // fixed
            mc.renderer.heldBlock.moving = true; // fixed
        }

        final int selId = mc.player.inventory.getSelected();

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
            // Right click on villager → trade
            if (mc.selected.entity instanceof Villager && button == 1) {
                ((Villager) mc.selected.entity).onRightClick(mc.player);
                return;
            }

            // Left click → damage any entity (including villager)
            if (button == 0) {
                if (!mc.entityWithinReach(mc.selected.entity))
                    return;
                int dmg = 2;
                if (selId >= 256) {
                    int itemId = selId - 256;
                    if (itemId >= 0 && itemId < net.classicremastered.minecraft.level.itemstack.Item.items.length) {
                        var it = net.classicremastered.minecraft.level.itemstack.Item.items[itemId];
                        if (it != null && it.name != null
                                && it.name.toLowerCase(java.util.Locale.ROOT).contains("sword")) {
                            dmg = 9;
                        }
                    }
                }
                mc.selected.entity.hurt(mc.player, dmg);
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
                if (tgt != null && tgt.onRightClick(mc.level, ox, oy, oz, mc.player, face))
                    return;
            }

            // 2) RMB + Flint&Steel (allow alongside item swing; main loop still calls
            // use())
            if (button == 1 && selId >= 256) {
                int itemIdx = selId - 256;
                if (itemIdx == net.classicremastered.minecraft.level.itemstack.Item.FLINT_AND_STEEL.id) {
                    if (mc.level != null) {
                        float base = 0.595F;
                        float pitch = MathHelper.clamp(base + (MathHelper.random.nextFloat() - 0.5F) * 0.10F, 0.50F,
                                0.70F);
                        mc.level.playSound("random/click", ox + 0.5F, oy + 0.5F, oz + 0.5F, 0.8F, pitch);
                    }

                    int targetedId = mc.level.getTile(ox, oy, oz);
                    if (targetedId == Block.TNT.id) {
                        net.classicremastered.minecraft.level.tile.TNTBlock.ignite(mc.level, ox, oy, oz);
                        return;
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

                    if (!mc.level.isInBounds(nx, ny, nz))
                        return;
                    if (mc.level.getTile(nx, ny, nz) != 0)
                        return;

                    boolean canPlace = mc.level.isSolidTile(ox, oy, oz);
                    if (!canPlace)
                        return;
                    if (!mc.fireCanStayAt(nx, ny, nz))
                        return;

                    if (mc.gamemode.tryPlaceBlock(mc.player, nx, ny, nz, Block.FIRE.id)) {
                        mc.level.playSound("random/fuse", nx + 0.5f, ny + 0.5f, nz + 0.5f, 1.0f, 1.0f);
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

            // Place only if selection is a BLOCK id (0..255)
            if (button == 1 && selId > 0 && selId < 256 && Block.blocks[selId] != null) {
                if (!mc.gamemode.canPlace(selId))
                    return;
                if (!mc.level.isInBounds(x, y, z))
                    return;

                if (mc.wouldCollideWithPlayer(x, y, z))
                    return;
                if (mc.wouldCollideWithMob(x, y, z))
                    return;

                if (mc.gamemode.tryPlaceBlock(mc.player, x, y, z, selId)) {
                    mc.renderer.heldBlock.pos = 0.0F;
                    Block.blocks[selId].onPlace(mc.level, x, y, z);

                    if (!(mc.gamemode instanceof CreativeGameMode)) {
                        mc.player.inventory.removeSelected(1);
                    }
                }
                return;
            }
        }
    }

    public void handleKeyboardEvent() {
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
                    if (!isRepeat && mc.gamemode instanceof net.classicremastered.minecraft.gamemode.CreativeGameMode
                            && mc.player != null && mc.player.canFly) {

                        int now = mc.ticks;
                        if (mc.jumpTapArmed && (now - mc.lastJumpTapTick) <= mc.jumpTapWindow) {
                            mc.player.isFlying = !mc.player.isFlying;
                            if (!mc.player.isFlying)
                                mc.player.yd = 0.0F;
                            if (mc.hud != null)
                                mc.hud.addChat(mc.player.isFlying ? "&aFlight enabled" : "&cFlight disabled");
                            mc.jumpTapArmed = false;
                            mc.lastJumpTapTick = -10000;
                        } else {
                            mc.jumpTapArmed = true;
                            mc.lastJumpTapTick = now;
                        }
                    }
                }
                if (Keyboard.getEventKey() == Keyboard.KEY_F7) { // F7 = spawn test sign
                    SignEntity s = new SignEntity(mc, mc.player.x, mc.player.y, mc.player.z, mc.player.yRot);
                    mc.level.addEntity(s);
                    mc.hud.addChat("&aSpawned Sign at player");
                }

                // Fly toggle key
                if (Keyboard.getEventKey() == mc.settings.flyToggleKey.key) {
                    if (mc.gamemode instanceof net.classicremastered.minecraft.gamemode.CreativeGameMode
                            && mc.player != null && mc.player.canFly) {
                        mc.player.isFlying = !mc.player.isFlying;
                        if (!mc.player.isFlying)
                            mc.player.yd = 0.0F;
                        if (mc.hud != null)
                            mc.hud.addChat(mc.player.isFlying ? "&aFlight enabled" : "&cFlight disabled");
                    }
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
                if (mc.gamemode instanceof net.classicremastered.minecraft.gamemode.CreativeGameMode) {
                    if (Keyboard.getEventKey() == mc.settings.loadLocationKey.key) {
                        mc.player.resetPos();
                    }
                    if (Keyboard.getEventKey() == mc.settings.saveLocationKey.key) {
                        mc.level.setSpawnPos((int) mc.player.x, (int) mc.player.y, (int) mc.player.z, mc.player.yRot);
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

                // F2 → request screenshot
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

            // Number keys (hotbar)
            for (int i = 0; i < 9; ++i) {
                if (Keyboard.getEventKey() == i + 2) {
                    mc.player.inventory.selected = i;
                }
            }

            // Fog toggle
            if (Keyboard.getEventKey() == mc.settings.toggleFogKey.key) {
                mc.settings.toggleSetting(4,
                        !Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) && !Keyboard.isKeyDown(Keyboard.KEY_RSHIFT) ? 1 : -1);
            }
        }
    }
}
