package net.classicremastered.minecraft;

import net.classicremastered.minecraft.gui.ErrorScreen;
import net.classicremastered.minecraft.level.Level;
import net.classicremastered.minecraft.level.LevelIO;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.net.NetworkManager;
import net.classicremastered.minecraft.net.NetworkPlayer;
import net.classicremastered.minecraft.net.PacketType;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.net.NetworkHandler;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/** Processes incoming Classic packets and sends the local player position. */
final class ClientNetworkHandler {
    private ClientNetworkHandler() {}

    static void tick(Minecraft mc) {
        int var4, var8, var40, var46, var45;
        if (mc.networkManager != null && mc.networkManager.connectionError != null) {
            mc.networkManager.disconnect("Failed to connect", mc.networkManager.connectionError);
            return;
        }
        if (mc.networkManager != null && !(mc.currentScreen instanceof ErrorScreen)) {
            if (!mc.networkManager.isConnected()) {
                mc.progressBar.setTitle("Connecting..");
                mc.progressBar.setProgress(0);
            } else {
                NetworkManager var20 = mc.networkManager;
                if (mc.networkManager.successful) {
                    NetworkHandler var18 = var20.netHandler;
                    if (var20.netHandler.connected) {
                        try {
                            NetworkHandler var22 = var20.netHandler;
                            if (var20.netHandler.channel.read(var22.in) < 0)
                                throw new java.io.EOFException("The server closed the connection.");
                            var4 = 0;

                            while (var22.in.position() > 0 && var4++ != 100) {
                                var22.in.flip();
                                byte var5 = var22.in.get(0);
                                PacketType var6;
                                if ((var6 = PacketType.packets[var5 & 255]) == null) {
                                    throw new IOException("Bad command: " + var5);
                                }

                                if (var22.in.remaining() < var6.length + 1) {
                                    var22.in.compact();
                                    break;
                                }

                                var22.in.get();
                                Object[] var7 = new Object[var6.params.length];

                                for (var8 = 0; var8 < var7.length; ++var8) {
                                    var7[var8] = var22.readObject(var6.params[var8]);
                                }

                                NetworkManager var42 = var22.netManager;
                                if (var22.netManager.successful) {
                                    if (var6 == PacketType.IDENTIFICATION) {
                                        var42.minecraft.progressBar.setTitle(var7[1].toString());
                                        var42.minecraft.progressBar.setText(var7[2].toString());
                                        var42.minecraft.player.userType = ((Byte) var7[3]).byteValue();
                                    } else if (var6 == PacketType.LEVEL_INIT) {
                                        var42.minecraft.setLevel((Level) null);
                                        var42.levelData = new ByteArrayOutputStream();
                                    } else if (var6 == PacketType.LEVEL_DATA) {
                                        short var11 = ((Short) var7[0]).shortValue();
                                        byte[] var12 = (byte[]) ((byte[]) var7[1]);
                                        byte var13 = ((Byte) var7[2]).byteValue();
                                        var42.minecraft.progressBar.setProgress(var13);
                                        if (var42.levelData == null || var11 < 0 || var11 > 1024
                                                || var42.levelData.size() + var11 > 8 * 1024 * 1024)
                                            throw new IOException("Invalid map data packet");
                                        var42.levelData.write(var12, 0, var11);
                                    } else if (var6 == PacketType.LEVEL_FINALIZE) {
                                        try {
                                            var42.levelData.close();
                                        } catch (IOException var14) {
                                            var14.printStackTrace();
                                        }

                                        byte[] var51 = LevelIO
                                                .decompress(new ByteArrayInputStream(var42.levelData.toByteArray()));
                                        var42.levelData = null;
                                        short var55 = ((Short) var7[0]).shortValue();
                                        short var63 = ((Short) var7[1]).shortValue();
                                        short var21 = ((Short) var7[2]).shortValue();
                                        if (var55 <= 0 || var63 <= 0 || var21 <= 0
                                                || (long) var55 * var63 * var21 != var51.length)
                                            throw new IOException("Invalid map dimensions");
                                        Level var30;
                                        (var30 = new Level()).setNetworkMode(true);
                                        var30.setData(var55, var63, var21, var51);
                                        var42.minecraft.setLevel(var30);
                                        var42.minecraft.online = false;
                                        var42.levelLoaded = true;
                                    } else if (var6 == PacketType.BLOCK_CHANGE) {
                                        if (var42.minecraft.level != null) {
                                            short bx = ((Short) var7[0]).shortValue();
                                            short by = ((Short) var7[1]).shortValue();
                                            short bz = ((Short) var7[2]).shortValue();
                                            byte blockId = ((Byte) var7[3]).byteValue();

                                            int oldBlockId = var42.minecraft.level.getTile(bx, by, bz);

                                            if (var42.minecraft.level.netSetTile(bx, by, bz, blockId)) {
                                                if (oldBlockId > 0 && blockId == 0) { // Broken
                                                    Block block = Block.blocks[oldBlockId];
                                                    if (block != null) {
                                                        if (block.stepsound != net.classicremastered.minecraft.level.tile.Tile$SoundType.none) {
                                                            float vol = (block.stepsound.getVolume() + 1.0F) / 2.0F;
                                                            float pitch = block.stepsound.getPitch() * 0.8F;
                                                            var42.minecraft.level.playSound(block.stepsound.pool, (float)bx + 0.5F, (float)by + 0.5F, (float)bz + 0.5F, vol, pitch);
                                                        }
                                                        if (var42.minecraft.particleManager != null) {
                                                            block.spawnBreakParticles(var42.minecraft.level, bx, by, bz, var42.minecraft.particleManager);
                                                        }
                                                    }
                                                } else if (blockId > 0) { // Placed
                                                    Block block = Block.blocks[blockId];
                                                    if (block != null && block.stepsound != net.classicremastered.minecraft.level.tile.Tile$SoundType.none) {
                                                        float vol = (block.stepsound.getVolume() + 1.0F) / 2.0F;
                                                        float pitch = block.stepsound.getPitch() * 0.8F;
                                                        var42.minecraft.level.playSound(block.stepsound.pool, (float)bx + 0.5F, (float)by + 0.5F, (float)bz + 0.5F, vol, pitch);
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        byte var9;
                                        String var34;
                                        NetworkPlayer var33;
                                        short var36;
                                        short var10004;
                                        byte var10001;
                                        short var47;
                                        short var10003;
                                        if (var6 == PacketType.SPAWN_PLAYER) {
                                            var10001 = ((Byte) var7[0]).byteValue();
                                            String var10002 = (String) var7[1];
                                            var10003 = ((Short) var7[2]).shortValue();
                                            var10004 = ((Short) var7[3]).shortValue();
                                            short var10005 = ((Short) var7[4]).shortValue();
                                            byte var10006 = ((Byte) var7[5]).byteValue();
                                            byte var58 = ((Byte) var7[6]).byteValue();
                                            var9 = var10006;
                                            short var10 = var10005;
                                            var47 = var10004;
                                            var36 = var10003;
                                            var34 = var10002;
                                            var5 = var10001;
                                            if (var5 >= 0) {
                                                var9 = (byte) (var9 + 128);
                                                var47 = (short) (var47 - 22);
                                                var33 = new NetworkPlayer(var42.minecraft, var5, var34, var36, var47,
                                                        var10, (float) (var9 * 360) / 256.0F,
                                                        (float) (var58 * 360) / 256.0F);
                                                NetworkPlayer previous = var42.players.put(Byte.valueOf(var5), var33);
                                                if (previous != null) {
                                                    previous.clear();
                                                    var42.minecraft.level.removeEntity(previous);
                                                }
                                                var42.minecraft.level.addEntity(var33);
                                            } else {
                                                var42.minecraft.level.setSpawnPos(var36 / 32, var47 / 32, var10 / 32,
                                                        (float) (var9 * 320 / 256));
                                                var42.minecraft.player.moveTo((float) var36 / 32.0F,
                                                        (float) var47 / 32.0F, (float) var10 / 32.0F,
                                                        (float) (var9 * 360) / 256.0F, (float) (var58 * 360) / 256.0F);
                                            }
                                        } else {
                                            byte var53;
                                            NetworkPlayer var61;
                                            byte var69;
                                            if (var6 == PacketType.POSITION_ROTATION) {
                                                var10001 = ((Byte) var7[0]).byteValue();
                                                short var66 = ((Short) var7[1]).shortValue();
                                                var10003 = ((Short) var7[2]).shortValue();
                                                var10004 = ((Short) var7[3]).shortValue();
                                                var69 = ((Byte) var7[4]).byteValue();
                                                var9 = ((Byte) var7[5]).byteValue();
                                                var53 = var69;
                                                var47 = var10004;
                                                var36 = var10003;
                                                short var38 = var66;
                                                var5 = var10001;
                                                if (var5 < 0) {
                                                    var42.minecraft.player.moveTo((float) var38 / 32.0F,
                                                            (float) var36 / 32.0F, (float) var47 / 32.0F,
                                                            (float) (var53 * 360) / 256.0F,
                                                            (float) (var9 * 360) / 256.0F);
                                                } else {
                                                    var53 = (byte) (var53 + 128);
                                                    var36 = (short) (var36 - 22);
                                                    if ((var61 = (NetworkPlayer) var42.players
                                                            .get(Byte.valueOf(var5))) != null) {
                                                        var61.teleport(var38, var36, var47,
                                                                (float) (var53 * 360) / 256.0F,
                                                                (float) (var9 * 360) / 256.0F);
                                                    }
                                                }
                                            } else {
                                                byte var37;
                                                byte var44;
                                                byte var49;
                                                byte var65;
                                                byte var67;
                                                if (var6 == PacketType.POSITION_ROTATION_UPDATE) {
                                                    var10001 = ((Byte) var7[0]).byteValue();
                                                    var67 = ((Byte) var7[1]).byteValue();
                                                    var65 = ((Byte) var7[2]).byteValue();
                                                    byte var64 = ((Byte) var7[3]).byteValue();
                                                    var69 = ((Byte) var7[4]).byteValue();
                                                    var9 = ((Byte) var7[5]).byteValue();
                                                    var53 = var69;
                                                    var49 = var64;
                                                    var44 = var65;
                                                    var37 = var67;
                                                    var5 = var10001;
                                                    if (var5 >= 0) {
                                                        var53 = (byte) (var53 + 128);
                                                        if ((var61 = (NetworkPlayer) var42.players
                                                                .get(Byte.valueOf(var5))) != null) {
                                                            var61.queue(var37, var44, var49,
                                                                    (float) (var53 * 360) / 256.0F,
                                                                    (float) (var9 * 360) / 256.0F);
                                                        }
                                                    }
                                                } else if (var6 == PacketType.ROTATION_UPDATE) {
                                                    var10001 = ((Byte) var7[0]).byteValue();
                                                    var67 = ((Byte) var7[1]).byteValue();
                                                    var44 = ((Byte) var7[2]).byteValue();
                                                    var37 = var67;
                                                    var5 = var10001;
                                                    if (var5 >= 0) {
                                                        var37 = (byte) (var37 + 128);
                                                        NetworkPlayer var54;
                                                        if ((var54 = (NetworkPlayer) var42.players
                                                                .get(Byte.valueOf(var5))) != null) {
                                                            var54.queue((float) (var37 * 360) / 256.0F,
                                                                    (float) (var44 * 360) / 256.0F);
                                                        }
                                                    }
                                                } else if (var6 == PacketType.POSITION_UPDATE) {
                                                    var10001 = ((Byte) var7[0]).byteValue();
                                                    var67 = ((Byte) var7[1]).byteValue();
                                                    var65 = ((Byte) var7[2]).byteValue();
                                                    var49 = ((Byte) var7[3]).byteValue();
                                                    var44 = var65;
                                                    var37 = var67;
                                                    var5 = var10001;
                                                    NetworkPlayer var59;
                                                    if (var5 >= 0 && (var59 = (NetworkPlayer) var42.players
                                                            .get(Byte.valueOf(var5))) != null) {
                                                        var59.queue(var37, var44, var49);
                                                    }
                                                } else if (var6 == PacketType.DESPAWN_PLAYER) {
                                                    var5 = ((Byte) var7[0]).byteValue();
                                                    if (var5 >= 0 && (var33 = (NetworkPlayer) var42.players
                                                            .remove(Byte.valueOf(var5))) != null) {
                                                        var33.clear();
                                                        var42.minecraft.level.removeEntity(var33);
                                                    }
                                                } else if (var6 == PacketType.CHAT_MESSAGE) {
                                                    var10001 = ((Byte) var7[0]).byteValue();
                                                    var34 = (String) var7[1];
                                                    var5 = var10001;
                                                    if (var5 < 0) {
                                                        var42.minecraft.hud.addChat("&e" + var34);
                                                    } else {
                                                        var42.players.get(Byte.valueOf(var5));
                                                        var42.minecraft.hud.addChat(var34);
                                                    }
                                                } else if (var6 == PacketType.DISCONNECT) {
                                                    var42.disconnect("Connection lost", (String) var7[0]);
                                                    return;
                                                } else if (var6 == PacketType.UPDATE_PLAYER_TYPE) {
                                                    var42.minecraft.player.userType = ((Byte) var7[0]).byteValue();
                                                }
                                            }
                                        }
                                    }
                                }

                                if (!var22.connected) {
                                    break;
                                }

                                var22.in.compact();
                            }

                            if (var22.out.position() > 0) {
                                var22.out.flip();
                                var22.channel.write(var22.out);
                                var22.out.compact();
                            }
                        } catch (Exception error) {
                            var20.disconnect("Disconnected", error.getMessage());
                            return;
                        }
                    }
                }

                Player var28 = mc.player;
                var20 = mc.networkManager;
                if (var20 != null && var20.isConnected() && var20.levelLoaded && var28 != null) {
                    int var24 = (int) (var28.x * 32.0F);
                    var4 = (int) (var28.y * 32.0F);
                    var40 = (int) (var28.z * 32.0F);
                    var46 = (int) (var28.yRot * 256.0F / 360.0F) & 255;
                    var45 = (int) (var28.xRot * 256.0F / 360.0F) & 255;
                    var20.netHandler.send(PacketType.POSITION_ROTATION,
                            new Object[] { Integer.valueOf(-1), Integer.valueOf(var24), Integer.valueOf(var4),
                                    Integer.valueOf(var40), Integer.valueOf(var46), Integer.valueOf(var45) });
                }
            }
        }
    }
}
