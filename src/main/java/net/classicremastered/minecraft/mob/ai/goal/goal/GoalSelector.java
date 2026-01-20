package net.classicremastered.minecraft.mob.ai.goal.goal;

import java.util.ArrayList;
import java.util.List;

public class GoalSelector {
    private final List<Goal> goals = new ArrayList<>();
    private Goal active;

    public void addGoal(Goal goal) { goals.add(goal); }

    public void tick() {
        if (active != null && !active.canContinue()) {
            active.stop();
            active = null;
        }
        if (active == null) {
            for (Goal g : goals) {
                if (g.canStart()) {
                    active = g;
                    g.start();
                    break;
                }
            }
        }
        if (active != null) active.tick();
    }
}
