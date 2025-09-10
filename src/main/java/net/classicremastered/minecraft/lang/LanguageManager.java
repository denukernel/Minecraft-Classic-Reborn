// File: src/com/mojang/minecraft/lang/LanguageManager.java
package net.classicremastered.minecraft.lang;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class LanguageManager {
    private final Map<String, String> translations = new HashMap<>();
    private final File langDir;
    private String current = "english"; // default language

    public LanguageManager(File mcDir) {
        this.langDir = new File(mcDir, "languages");
        if (!langDir.exists()) langDir.mkdirs();
        LanguageGenerator.ensureMainMenuLangs(this.langDir);
        // Generate missing files
    }


    /** Expose the directory where language files live. */
    public File getLangDir() {
        return langDir;
    }

    /** Current language name (e.g. "english"). */
    public String getCurrent() {
        return current;
    }

    /** Load a .lang file (format: key=value). */
    public void load(String langName) {
        File file = new File(langDir, langName + ".lang");
        if (!file.exists()) {
            System.out.println("[Lang] Missing: " + file.getAbsolutePath());
            return;
        }

        translations.clear();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                translations.put(key, value);
            }
            current = langName;
            System.out.println("[Lang] Loaded language: " + langName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Lookup translation for a key, fallback to key itself. */
    public String tr(String key) {
        return translations.getOrDefault(key, key);
    }
}
