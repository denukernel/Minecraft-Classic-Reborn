package net.classicremastered.minecraft.net;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.gui.ErrorScreen;
import net.classicremastered.net.NetworkHandler;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import net.classicremastered.minecraft.gui.GuiScreen;

public class NetworkManager
{
	public GuiScreen parentScreen;
	public NetworkManager(Minecraft minecraft, String server, int port, String username, String key)
	{
		minecraft.online = true;

		this.minecraft = minecraft;

		players = new HashMap<Byte, NetworkPlayer>();

		new ServerConnectThread(this, server, port, username, key, minecraft).start();
	}

	public ByteArrayOutputStream levelData;

	public volatile NetworkHandler netHandler;

	public Minecraft minecraft;

	public volatile boolean successful = false;
	public volatile String connectionError;
	public boolean levelLoaded = false;

	public HashMap<Byte, NetworkPlayer> players;

	public void sendBlockChange(int x, int y, int z, int mode, int block)
	{
		if (isConnected() && levelLoaded) netHandler.send(PacketType.PLAYER_SET_BLOCK, new Object[] {x, y, z, mode, block});
	}

    public void error(Exception e) {
        if (netHandler != null) netHandler.close();
        connectionError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    /** Must run on the game thread because screens and level changes use OpenGL. */
    public void disconnect(String title, String reason) {
        if (netHandler != null) netHandler.close();
        successful = false;
        levelLoaded = false;
        if (minecraft.networkManager != this) return;
        minecraft.networkManager = null;
        minecraft.server = null;
        minecraft.online = false;
        minecraft.setLevel(null);
        minecraft.setCurrentScreen(new ErrorScreen(title, reason,
                parentScreen != null ? parentScreen : new net.classicremastered.minecraft.gui.MainMenuScreen()));
    }

	public boolean isConnected()
	{
		return netHandler != null && netHandler.connected;
	}

	public List getPlayers()
	{
		ArrayList list = new ArrayList();

		list.add(minecraft.session.username);

		Iterator playerIterator = this.players.values().iterator();

		while(playerIterator.hasNext())
		{
			NetworkPlayer networkPlayer = (NetworkPlayer)playerIterator.next();

			list.add(networkPlayer.name);
		}

		return list;
	}
}
