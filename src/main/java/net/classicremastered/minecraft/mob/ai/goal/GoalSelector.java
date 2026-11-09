package net.classicremastered.minecraft.mob.ai.goal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Runs compatible goals together. Lower priorities win control conflicts. */
public final class GoalSelector {

    private final List<Entry> goals = new ArrayList<>();

    public GoalSelector() {
    }

    public GoalSelector(Goal... initialGoals) {
        for (int i = 0; i < initialGoals.length; i++)
            addGoal(i, initialGoals[i]);
    }

    public void addGoal(Goal goal) {
        addGoal(Integer.MAX_VALUE, goal);
    }

    public void addGoal(int priority, Goal goal) {
        Objects.requireNonNull(goal, "goal");
        for (Entry entry : goals) {
            if (entry.goal == goal)
                return;
        }
        goals.add(new Entry(priority, goal));
        goals.sort(Comparator.comparingInt(entry -> entry.priority));
    }

    public void removeGoal(Goal goal) {
        for (Entry entry : goals) {
            if (entry.goal == goal)
                entry.stop();
        }
        goals.removeIf(entry -> entry.goal == goal);
    }

    public void stopAll() {
        for (Entry entry : goals)
            entry.stop();
    }

    public void tick() {
        for (Entry entry : goals) {
            if (entry.running && !entry.goal.canContinue())
                entry.stop();
        }
        for (Entry candidate : goals) {
            if (candidate.running || !canReplaceConflicts(candidate) || !candidate.goal.canStart())
                continue;
            for (Entry active : goals) {
                if (active.running && conflicts(candidate, active))
                    active.stop();
            }
            candidate.running = true;
            candidate.goal.start();
        }
        for (Entry entry : goals) {
            if (entry.running)
                entry.goal.tick();
        }
    }

    private boolean canReplaceConflicts(Entry candidate) {
        for (Entry active : goals) {
            if (active.running && conflicts(candidate, active)
                    && (active.priority <= candidate.priority || !active.goal.isInterruptible()))
                return false;
        }
        return true;
    }

    private boolean conflicts(Entry first, Entry second) {
        return first.goal.getFlags().stream().anyMatch(second.goal.getFlags()::contains);
    }
    private static final class Entry {

        final int priority;
        final Goal goal;
        boolean running;
        Entry(int priority, Goal goal) {
            this.priority = priority;
            this.goal = goal;
        }

        void stop() {
            if (running) {
                running = false;
                goal.stop();
            }
        }
    }
}
