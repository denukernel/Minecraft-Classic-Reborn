package net.classicremastered.minecraft.chat.commands;

import java.util.*;

import net.classicremastered.minecraft.Minecraft;
import net.classicremastered.minecraft.gui.FontRenderer;
import net.classicremastered.minecraft.player.Player;

/** Helpers for paginating and wrapping long chat output. */
public final class ChatLists {
    private ChatLists() {}

    // Remember the last paged list per "user key" so /more works
    private static final Map<String, Pager> LAST = new HashMap<>();

    public static final int DEFAULT_PAGE_SIZE = 12;    // lines per page (vertical)
    public static final int DEFAULT_MAX_PIXELS = 300;  // wrap width (horizontal)

    public static final class Pager {
        public final String title;
        public final List<String> lines; // already wrapped
        public final int pageSize;
        public int page;
        public Pager(String title, List<String> lines, int pageSize) {
            this.title = title;
            this.lines = lines;
            this.pageSize = Math.max(1, pageSize);
            this.page = 1;
        }
    }

    /** Wrap a list of logical lines into pixel-constrained lines using the font renderer. */
    public static List<String> wrapLines(FontRenderer fr, List<String> logical, int maxPixels) {
        List<String> out = new ArrayList<>();
        for (String s : logical) wrapOne(fr, s, maxPixels, out);
        return out;
    }

    /** Simple word wrap that respects color codes (&x). */
    private static void wrapOne(FontRenderer fr, String s, int maxPx, List<String> out) {
        if (s == null) { out.add(""); return; }
        String activeColor = ""; // keep last &x across wraps
        int i = 0;
        while (i < s.length()) {
            int lineStart = i;
            int lastSpace = -1;

            int px = 0;
            String colorHere = activeColor;

            while (i < s.length()) {
                char ch = s.charAt(i);
                if (ch == '&' && i + 1 < s.length()) { colorHere = s.substring(i, i + 2); i += 2; continue; }
                int nextPx = px + fr.getWidth(String.valueOf(ch));
                if (nextPx > maxPx) break;
                if (ch == ' ') lastSpace = i;
                px = nextPx; i++;
            }

            int end;
            if (i == s.length()) end = i;
            else if (lastSpace >= 0 && lastSpace > lineStart) { end = lastSpace; i = lastSpace + 1; }
            else end = Math.max(lineStart, i);

            String piece = s.substring(lineStart, end);
            out.add(colorHere + piece);
            activeColor = colorHere; // carry color
        }
        if (out.isEmpty()) out.add("");
    }

    /** Show a paged list; also stores it so `/more` can advance. */
    public static void showPaged(Minecraft mc, Player p, String title, List<String> wrappedLines,
                                 int page, int pageSize, String nextHintCmd) {
        if (mc == null || mc.hud == null) return;

        int total = wrappedLines.size();
        int pages = Math.max(1, (int)Math.ceil(total / (double)pageSize));
        int cur = Math.min(Math.max(1, page), pages);
        int start = (cur - 1) * pageSize;
        int end = Math.min(start + pageSize, total);

        mc.hud.addChat(title + " &7(page " + cur + "/" + pages + ")");
        if (total == 0) {
            mc.hud.addChat("&8(none)");
        } else {
            for (int i = start; i < end; i++) mc.hud.addChat(wrappedLines.get(i));
            if (cur < pages) mc.hud.addChat("&7Type &f" + nextHintCmd + "&7 for more.");
        }

        // save for /more under a stable user key
        String key = userKey(mc, p);
        Pager pager = new Pager(title, wrappedLines, pageSize);
        pager.page = cur;
        LAST.put(key, pager);
    }

    /** Advance last pager for this "user". */
    public static boolean more(Minecraft mc, Player p, String nextHintCmd) {
        String key = userKey(mc, p);
        Pager pager = LAST.get(key);
        if (pager == null) return false;

        int pages = Math.max(1, (int)Math.ceil(pager.lines.size() / (double)pager.pageSize));
        if (pager.page >= pages) return false;

        pager.page++;
        showPaged(mc, p, pager.title, pager.lines, pager.page, pager.pageSize, nextHintCmd);
        return true;
    }

    // -------- NEW: resolve a stable key; prefer session username --------
    private static String userKey(Minecraft mc, Player p) {
        try {
            if (mc != null && mc.session != null && mc.session.username != null) {
                return "session:" + mc.session.username;
            }
        } catch (Throwable ignored) {}
        // fallback: identity of the Player instance or a constant local key
        return "local:" + (p != null ? System.identityHashCode(p) : 0);
    }
}
