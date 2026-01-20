package net.classicremastered.minecraft.player;

import org.lwjgl.input.Keyboard;

public class InputHandler {

    public float xxa = 0.0F;   // strafe (-1 left, +1 right)
    public float yya = 0.0F;   // forward/back (-1 back, +1 forward)
    public boolean jumping = false;
    public boolean running = false;

    private boolean[] keyStates = new boolean[10];

    public void updateMovement() {
        // --- Direction keys ---
        float strafe = 0.0F;
        float forward = 0.0F;

        if (Keyboard.isKeyDown(Keyboard.KEY_W) || Keyboard.isKeyDown(Keyboard.KEY_UP)) forward += 1.0F;
        if (Keyboard.isKeyDown(Keyboard.KEY_S) || Keyboard.isKeyDown(Keyboard.KEY_DOWN)) forward -= 1.0F;
        if (Keyboard.isKeyDown(Keyboard.KEY_A) || Keyboard.isKeyDown(Keyboard.KEY_LEFT)) strafe -= 1.0F;
        if (Keyboard.isKeyDown(Keyboard.KEY_D) || Keyboard.isKeyDown(Keyboard.KEY_RIGHT)) strafe += 1.0F;

        // --- Normalize diagonal speed ---
        if (strafe != 0.0F && forward != 0.0F) {
            float inv = 1.0F / (float) Math.sqrt(2.0);
            strafe *= inv;
            forward *= inv;
        }

        this.xxa = strafe;
        this.yya = forward;

        // --- Jump key ---
        this.jumping = Keyboard.isKeyDown(Keyboard.KEY_SPACE);

        // --- Run key (for walk speed multiplier or fly boost) ---
        this.running = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    /** Resets all movement input (used when GUI screens open). */
    public void resetKeys() {
        this.xxa = 0.0F;
        this.yya = 0.0F;
        this.jumping = false;
        this.running = false;
    }

    /** External key hook (for network or replay control). */
    public void setKeyState(int key, boolean state) {
        if (key >= 0 && key < keyStates.length) keyStates[key] = state;
    }
}
