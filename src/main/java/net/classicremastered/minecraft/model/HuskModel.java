// File: src/com/mojang/minecraft/model/HuskModel.java
package net.classicremastered.minecraft.model;

/**
 * Husk model — identical to ZombieModel, but with headwear/hat permanently disabled.
 */
public final class HuskModel extends ZombieModel {
    public HuskModel() {
        super();
        this.hasHeadOverlay = false;
        if (this.headwear != null) this.headwear.render = false; // hard kill-switch
    }
}
