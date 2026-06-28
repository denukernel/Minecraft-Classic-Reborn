package net.classicremastered.minecraft.chat.commands;

import java.util.ArrayList;
import java.util.List;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.level.itemstack.Item;
import net.classicremastered.minecraft.player.Player;

public class ItemCommand implements Command, AliasedCommand {

    private static final int ITEM_BASE_ID = 256;
    private static final int EMPTY_GIVE_ID = ITEM_BASE_ID;

    @Override
    public String getName() {
        return "item";
    }

    @Override
    public String[] getAliases() {
        return new String[] { "items" };
    }

    @Override
    public String getUsage() {
        return "/item [page]";
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
        for (int i = 0; i < Item.items.length; i++) {
            Item it = Item.items[i];
            int giveId = ITEM_BASE_ID + i;
            if (giveId == EMPTY_GIVE_ID || it == null || it == Item.EMPTY || i == 0) continue;
            
            String name = it.name;
            if (name == null || name.isEmpty()) {
                name = "Item#" + i;
            }
            lines.add("&7" + giveId + ": &f" + name);
        }

        List<String> wrapped = ChatLists.wrapLines(mc.fontRenderer, lines, ChatLists.DEFAULT_MAX_PIXELS);
        ChatLists.showPaged(mc, sender, "&eItems", wrapped, page, ChatLists.DEFAULT_PAGE_SIZE, "/more");
    }
}
