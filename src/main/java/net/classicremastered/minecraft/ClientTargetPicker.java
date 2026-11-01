package net.classicremastered.minecraft;

import net.classicremastered.minecraft.gamemode.CreativeGameMode;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.Renderer;
import net.classicremastered.util.MathHelper;

import java.util.List;

/** Chooses the nearest reachable block or entity along the camera ray. */
final class ClientTargetPicker {
    private ClientTargetPicker() {}

    static void pick(Renderer r, float partial) {
        Renderer rPick = r;
        Player player = r.minecraft.player;
        float xRotInterp = player.xRotO + (player.xRot - player.xRotO) * partial;
        float yRotInterp = player.yRotO + (player.yRot - player.yRotO) * partial;
        Vec3D rayStart = r.getPlayerVector(partial);
        float cosYaw = MathHelper.cos(-yRotInterp * 0.017453292F - 3.1415927F);
        float sinYaw = MathHelper.sin(-yRotInterp * 0.017453292F - 3.1415927F);
        float cosPitch = MathHelper.cos(-xRotInterp * 0.017453292F);
        float sinPitch = MathHelper.sin(-xRotInterp * 0.017453292F);
        float lookX = sinYaw * cosPitch;
        float lookZ = cosYaw * cosPitch;
        float reach = r.minecraft.gamemode.getReachDistance();
        Vec3D rayEnd = rayStart.add(lookX * reach, sinPitch * reach, lookZ * reach);
        r.minecraft.selected = r.minecraft.level.clip(rayStart, rayEnd);
        cosPitch = reach;
        if (r.minecraft.selected != null) {
            cosPitch = r.minecraft.selected.vec.distance(r.getPlayerVector(partial));
        }
        float maxEntityReach = (r.minecraft.gamemode instanceof CreativeGameMode) ? 32.0F : r.minecraft.gamemode.getReachDistance();
        rayStart = r.getPlayerVector(partial);
        Vec3D entityRayEnd = rayStart.add(lookX * maxEntityReach, sinPitch * maxEntityReach, lookZ * maxEntityReach);
        MovingObjectPosition solidBlockHit = r.minecraft.level.clip(rayStart, entityRayEnd, true);
        reach = maxEntityReach;
        if (solidBlockHit != null) {
            reach = solidBlockHit.vec.distance(r.getPlayerVector(partial));
        }
        rayStart = r.getPlayerVector(partial);
        rayEnd = rayStart.add(lookX * reach, sinPitch * reach, lookZ * reach);
        r.entity = null;
        List entitiesInPath = r.minecraft.level.blockMap.getEntities(player,
                player.bb.expand(lookX * reach, sinPitch * reach, lookZ * reach));
        float closestHitDist = 0.0F;

        for (int entityIndex = 0; entityIndex < entitiesInPath.size(); ++entityIndex) {
            Entity e;
            if ((e = (Entity) entitiesInPath.get(entityIndex)).isPickable()) {
                cosPitch = 0.1F;
                MovingObjectPosition hit;
                if ((hit = e.bb.grow(cosPitch, cosPitch, cosPitch).clip(rayStart,
                        rayEnd)) != null
                        && ((cosPitch = rayStart.distance(hit.vec)) < closestHitDist
                                || closestHitDist == 0.0F)) {
                    rPick.entity = e;
                    closestHitDist = cosPitch;
                }
            }
        }

        if (rPick.entity != null) {
            rPick.minecraft.selected = new MovingObjectPosition(rPick.entity);
        }
    }
}
