package net.classicremastered.minecraft.net;

import java.awt.image.BufferedImage;
import net.classicremastered.util.SkinDownloader;

public class SkinDownloadThread extends Thread {
    private final NetworkPlayer player;

    public SkinDownloadThread(NetworkPlayer player) {
        super("Network player skin");
        setDaemon(true);
        this.player = player;
    }

    @Override public void run() {
        BufferedImage skin = SkinDownloader.download(player.name);
        if (skin != null) player.newTexture = skin;
    }
}
