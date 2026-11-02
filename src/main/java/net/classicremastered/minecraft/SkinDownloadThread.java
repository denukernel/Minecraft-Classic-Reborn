package net.classicremastered.minecraft;

import java.awt.image.BufferedImage;
import net.classicremastered.minecraft.player.Player;
import net.classicremastered.util.SkinDownloader;

public class SkinDownloadThread extends Thread {
    private final Minecraft minecraft;

    public SkinDownloadThread(Minecraft minecraft) {
        super("Local player skin");
        setDaemon(true);
        this.minecraft = minecraft;
    }

    @Override public void run() {
        if (minecraft.session == null) return;
        BufferedImage skin = SkinDownloader.download(minecraft.session.username);
        if (skin != null) Player.newTexture = skin;
    }
}
