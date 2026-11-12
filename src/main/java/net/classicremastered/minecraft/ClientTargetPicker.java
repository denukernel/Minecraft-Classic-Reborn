package net.classicremastered.minecraft;

import net.classicremastered.minecraft.gamemode.CreativeGameMode;
import net.classicremastered.minecraft.level.liquid.LiquidType;
import net.classicremastered.minecraft.model.Vec3D;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.minecraft.render.Renderer;

import java.util.List;

/** Chooses the nearest reachable block or entity along the camera ray. */
final class ClientTargetPicker {
    private ClientTargetPicker() {}

    static MovingObjectPosition pickWater(Renderer renderer) {
        Vec3D start = renderer.getPlayerVector(1);
        Vec3D direction = renderer.minecraft.player.aimDirection(1);
        float reach = renderer.minecraft.gamemode.getReachDistance();
        Vec3D end = start.add(direction.x * reach, direction.y * reach, direction.z * reach);
        var level = renderer.minecraft.level;
        MovingObjectPosition hit = level.clip(start, end, false, true);
        return hit != null && level.getLiquid(hit.x, hit.y, hit.z) == LiquidType.WATER ? hit : null;
    }

    static void pick(Renderer r, float partial) {
        boolean spectator = r.minecraft.gamemode.isSpectator();
        if (spectator) {
            r.minecraft.selected = null;
            r.entity = null;
            if (!r.minecraft.debugOverlay)
                return;
        }
        Renderer rPick = r;
        Player player = r.minecraft.player;
        Vec3D direction = player.aimDirection(partial);
        Vec3D rayStart = r.getPlayerVector(partial);
        float lookX = direction.x;
        float sinPitch = direction.y;
        float lookZ = direction.z;
        float reach = r.minecraft.gamemode.getReachDistance();
        Vec3D rayEnd = rayStart.add(lookX * reach, sinPitch * reach, lookZ * reach);
        MovingObjectPosition blockHit = r.minecraft.level.clip(rayStart, rayEnd);
        r.minecraft.debugBlockTarget = r.minecraft.debugOverlay ? blockHit : null;
        if (spectator)
            return;
        r.minecraft.selected = blockHit;
        float cosPitch = reach;
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
