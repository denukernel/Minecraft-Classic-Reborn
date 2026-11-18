package net.classicremastered.minecraft.render.entity;

import net.classicremastered.minecraft.mob.Pufferfish;
import net.classicremastered.minecraft.model.Model;

final class PufferfishRenderer extends ModelMobRenderer<Pufferfish> {
    private final Model mid;
    private final Model full;

    PufferfishRenderer(Model normal, Model mid, Model full) {
        super(normal);
        this.mid = mid;
        this.full = full;
    }

    @Override
    public Model model(Pufferfish fish) {
        return switch (fish.puffState()) {
            case 1 -> mid;
            case 2 -> full;
            default -> model;
        };
    }
}
