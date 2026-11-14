package net.classicremastered.minecraft.mob.ai.goal.movement;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.classicremastered.minecraft.mob.Mob;
import net.classicremastered.minecraft.mob.Villager;
import net.classicremastered.minecraft.mob.Zombie;
import net.classicremastered.minecraft.mob.ai.BasicAI;
import net.classicremastered.minecraft.mob.ai.goal.MobGoal;
import net.classicremastered.minecraft.path.Pathfinder;

public class FleeThreatGoal extends MobGoal<BasicAI> {

    private static final float FLEE_RANGE = 12;
    private final List<Mob> threats = new ArrayList<>();
    private Escape destination;
    private int replanTicks;

    public record Escape(float x, float y, float z) {}

    public FleeThreatGoal(BasicAI ai) {
        super(ai);
    }

    @Override
    public boolean canStart() {
        if (!super.canStart() || !(ai.mob instanceof Villager villager) || villager.isHostile())
            return false;
        threats.clear();
        for (Object candidate :
                ai.level.findEntities(ai.mob, ai.mob.bb.grow(FLEE_RANGE, 4, FLEE_RANGE))) {
            if (candidate instanceof Zombie threat
                    && !threat.removed
                    && threat.health > 0
                    && !threat.corruptedToDefendPlayer
                    && ai.mob.distanceToSqr(threat) <= FLEE_RANGE * FLEE_RANGE) threats.add(threat);
        }
        return !threats.isEmpty() || evacuating();
    }

    @Override
    public boolean canContinue() {
        return canStart();
    }

    @Override
    public void stop() {
        destination = null;
        replanTicks = 0;
        ai.clearNavigation();
    }

    protected boolean evacuating() {
        return false;
    }

    protected List<Escape> shelters() {
        return List.of();
    }

    @Override
    public void tick() {
        ai.noActionTime = 0;
        List<Escape> shelters = shelters();
        Escape shelter =
                destination != null && shelters.contains(destination)
                        ? destination
                        : shelters.isEmpty() ? null : shelters.get(0);
        if (shelter != null
                && Math.hypot(ai.mob.x - shelter.x, ai.mob.z - shelter.z) < .3
                && Math.abs(ai.mob.bb.y0 - shelter.y) < .3) {
            ai.xxa = ai.yya = ai.yRotA = 0;
            ai.jumping = false;
            ai.mob.xd *= .5f;
            ai.mob.zd *= .5f;
            return;
        }
        if (--replanTicks <= 0
                || destination != null
                        && !destination.equals(shelter)
                        && Math.hypot(ai.mob.x - destination.x, ai.mob.z - destination.z) < .6) {
            destination = shelters.stream().filter(this::safeRoute).findFirst().orElse(null);
            if (destination == null) destination = findEscape();
            replanTicks = 10;
        }
        if (destination == null) {
            ai.jumping = ai.mob.isInWater() || ai.mob.isInLava();
            return;
        }
        ai.running = true;
        ai.runMultiplier = 1.4f;
        ai.navigateTo(destination.x, destination.y, destination.z, ai.runSpeed * 1.5f);
        ai.jumping |= ai.mob.isInWater() || ai.mob.isInLava();
    }

    private Escape findEscape() {
        List<Escape> candidates = new ArrayList<>();
        float feet = ai.mob.bb.y0;
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            for (int radius : new int[] {4, 8}) {
                float x = (float) Math.floor(ai.mob.x + Math.cos(angle) * radius) + .5f;
                float z = (float) Math.floor(ai.mob.z + Math.sin(angle) * radius) + .5f;
                candidates.add(new Escape(x, feet, z));
            }
        }
        float start = clearance(ai.mob.x, feet, ai.mob.z);
        candidates.sort(
                Comparator.comparingDouble((Escape pos) -> clearance(pos.x, pos.y, pos.z))
                        .reversed());
        for (Escape candidate : candidates) {
            if ((threats.isEmpty() || clearance(candidate.x, candidate.y, candidate.z) > start + 1f)
                    && safeRoute(candidate)) return candidate;
        }
        return null;
    }

    private boolean safeRoute(Escape destination) {
        var path =
                Pathfinder.findChasePath(
                        ai.level, ai.mob, destination.x, destination.y, destination.z, 384);
        if (path == null || path.isEmpty()) return false;
        float offset = Pathfinder.offset(ai.mob.bbWidth);
        var end = path.get(path.size() - 1);
        if (Math.hypot(end.x + offset - destination.x, end.z + offset - destination.z) > 1
                || Math.abs(end.feetY - destination.y) > 2) return false;
        float minClearance = Math.min(2.5f, clearance(ai.mob.x, ai.mob.bb.y0, ai.mob.z)) - .3f;
        for (var step : path) {
            var bounds =
                    ai.mob.bb.cloneMove(
                            step.x + offset - ai.mob.x,
                            step.feetY - ai.mob.bb.y0,
                            step.z + offset - ai.mob.z);
            if (step.parent != null && ai.mob.overlapsFire(bounds)) return false;
            if (clearance(step.x + offset, step.feetY, step.z + offset) < minClearance)
                return false;
        }
        return true;
    }

    private float clearance(float x, float feetY, float z) {
        float nearest = Float.MAX_VALUE;
        for (Mob threat : threats) {
            float dx = threat.x - x, dy = threat.bb.y0 - feetY, dz = threat.z - z;
            nearest = Math.min(nearest, dx * dx + dy * dy + dz * dz);
        }
        return (float) Math.sqrt(nearest);
    }
}
