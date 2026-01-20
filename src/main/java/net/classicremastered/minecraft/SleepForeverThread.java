package net.classicremastered.minecraft;

public class SleepForeverThread extends Thread {

    private final Minecraft minecraft;

    public SleepForeverThread(Minecraft minecraft) {
        this.minecraft = minecraft;
        // do NOT start the thread here
        // do NOT call setDaemon() here either — caller handles it
    }

    @Override
    public void run() {
        try {
            while (true) {
                Thread.sleep(2147483647L); // sleep "forever"
            }
        } catch (InterruptedException ignored) {
            // just exit quietly
        }
    }
}
