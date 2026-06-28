package net.classicremastered.minecraft.chat.commands;

import java.util.ArrayList;
import java.util.List;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.level.tile.Block;
import net.classicremastered.minecraft.player.Player;

public class BlocksCommand implements Command {

    @Override
    public String getName() {
        return "blocks";
    }

    @Override
    public String getUsage() {
        return "/blocks [page]";
    }

    @Override
    public void execute(Minecraft mc, Player sender, String[] args) {
        if (mc == null || mc.hud == null || sender == null) return;

        int page = 1;
        if (args != null && args.length >= 1) {
            try {
                page = Math.max(1, Integer.parseInt(args[0]));
            } catch (NumberFormatException ignored) {}
        }

        List<String> lines = new ArrayList<>();
        for (int i = 0; i < Block.blocks.length; i++) {
            Block b = Block.blocks[i];
            if (b != null) {
                String name = b.name;
                if (name == null || name.isEmpty()) {
                    name = "Block#" + i;
                }
                lines.add("&7" + i + ": &f" + name);
            }
        }

        List<String> wrapped = ChatLists.wrapLines(mc.fontRenderer, lines, ChatLists.DEFAULT_MAX_PIXELS);
        ChatLists.showPaged(mc, sender, "&eBlocks", wrapped, page, ChatLists.DEFAULT_PAGE_SIZE, "/more");
    }
}
