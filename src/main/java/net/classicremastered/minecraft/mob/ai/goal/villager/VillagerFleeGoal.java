package net.classicremastered.minecraft.mob.ai.goal.villager;

import java.util.List;
import net.classicremastered.minecraft.mob.Villager;
import net.classicremastered.minecraft.mob.ai.goal.movement.FleeThreatGoal;
import net.classicremastered.minecraft.mob.ai.villager.VillagerAI;

public final class VillagerFleeGoal extends FleeThreatGoal {

    public VillagerFleeGoal(VillagerAI ai) {
        super(ai);
    }

    @Override
    protected boolean evacuating() {
        return ai.level.villagerDoors().homeBurning((Villager) ai.mob);
    }

    @Override
    protected List<Escape> shelters() {
        if (evacuating()) {
            return ai.level.villagerDoors().evacuationShelters((Villager) ai.mob).stream()
                    .map(p -> new Escape(p.x(), p.y(), p.z()))
                    .toList();
        }
        var approach = ai.level.villagerDoors().fleeShelter((Villager) ai.mob);
        return approach == null
                ? List.of()
                : List.of(new Escape(approach.x(), approach.y(), approach.z()));
    }
}
