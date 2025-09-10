// File: src/com/mojang/minecraft/lang/LanguageGenerator.java
package net.classicremastered.minecraft.lang;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Creates or fills missing main-menu keys for every language we ship. */
final class LanguageGenerator {

    // keys used by MainMenuScreen
    private static final String[] KEYS = { "menu.title", "menu.selectLevel", "menu.options",
            // add more later if you localize extra buttons
            // "menu.quit"
    };

    /**
     * Call once at startup: creates .lang if missing, or appends any missing keys.
     */
    static void ensureMainMenuLangs(File langDir) {
        if (!langDir.exists())
            langDir.mkdirs();

        Map<String, Map<String, String>> L = new LinkedHashMap<>();

        // ===== ENGLISH (baseline) =====
        L.put("english", map("Minecraft Classic Improved", "Select Level (6 Slots)", "Options"));

        // ===== FULL SET =====
        L.put("afrikaans", map("Verbeterde Minecraft Classic", "Kies vlak (6 gleuwe)", "Opsies"));
        L.put("arabic", map("ماينكرافت كلاسيك المُحسَّن", "اختر المستوى (6 فتحات)", "الخيارات"));
        L.put("chinese", map("加强版 Minecraft 经典版", "选择关卡（6 个槽位）", "选项"));
        L.put("danish", map("Forbedret Minecraft Classic", "Vælg niveau (6 pladser)", "Indstillinger"));
        L.put("dutch", map("Verbeterde Minecraft Classic", "Niveau selecteren (6 sleuven)", "Opties"));
        L.put("french", map("Minecraft Classique Amélioré", "Sélectionner un niveau (6 emplacements)", "Options"));
        L.put("german", map("Verbessertes Minecraft Classic", "Level wählen (6 Plätze)", "Optionen"));
        L.put("greek", map("Βελτιωμένο Minecraft Classic", "Επιλογή επιπέδου (6 θέσεις)", "Επιλογές"));
        L.put("hindi", map("उन्नत Minecraft Classic", "स्तर चुनें (6 स्लॉट)", "विकल्प"));
        L.put("icelandic", map("Bætt Minecraft Classic", "Velja borð (6 raufar)", "Valkostir"));
        L.put("indonesian", map("Minecraft Classic yang Ditingkatkan", "Pilih level (6 slot)", "Opsi"));
        L.put("irish", map("Minecraft Classic Feabhsaithe", "Roghnaigh leibhéal (6 sliotáin)", "Roghanna"));
        L.put("italian", map("Minecraft Classic Migliorato", "Seleziona livello (6 slot)", "Opzioni"));
        L.put("japanese", map("改良版 Minecraft Classic", "レベル選択（6スロット）", "オプション"));
        L.put("korean", map("향상된 Minecraft Classic", "레벨 선택 (슬롯 6개)", "설정"));
        L.put("norwegian", map("Forbedret Minecraft Classic", "Velg nivå (6 plasser)", "Innstillinger"));
        L.put("polish", map("Ulepszony Minecraft Classic", "Wybierz poziom (6 slotów)", "Opcje"));
        L.put("portuguese", map("Minecraft Clássico Melhorado", "Selecionar nível (6 espaços)", "Opções"));
        L.put("quenchan", map("Minecraft Classic allinchasqa", "Akllay nivel (6 slot)", "Opciones")); // Quechua
                                                                                                      // fallback mix
        L.put("russian", map("Улучшенный Minecraft Classic", "Выбрать уровень (6 слотов)", "Настройки"));
        L.put("serbian", map("Poboljšani Minecraft Classic", "Izaberi nivo (6 slotova)", "Opcije"));
        L.put("spanish", map("Minecraft Classic Mejorado", "Seleccionar nivel (6 espacios)", "Opciones"));
        L.put("swedish", map("Förbättrad Minecraft Classic", "Välj nivå (6 platser)", "Alternativ"));
        L.put("turkish", map("Geliştirilmiş Minecraft Classic", "Seviye seç (6 yuva)", "Seçenekler"));
        L.put("vietnamese", map("Minecraft Classic Nâng Cấp", "Chọn màn chơi (6 khe)", "Tùy chọn"));

        // ===== PROTO / JOKE LANGS (explicitly included) =====
        // File: src/com/mojang/minecraft/lang/LanguageGenerator.java
        // ... inside ensureMainMenuLangs()

        // ===== PROTO / JOKE LANGS (now with proto-flavored strings; ASCII only) =====
        L.put("proto-austronesian", map("*Minecraft klasik ma-bagus", "*pili level (6 slot)", "*opsyon"));

        L.put("proto-germanic", map("*Betarod Minecraft Klassikaz", "*Kiusja Levil (6 slotos)", "*Optjonoz"));

        L.put("proto-indo-european", map("*Minecraft klassikos lep-yo", "*keus lewel (6 slotos)", "*optiom-es"));

        // write or patch each file
        for (Map.Entry<String, Map<String, String>> e : L.entrySet()) {
            upsert(langDir, e.getKey(), e.getValue());
        }
    }

    // ---- helpers ----

    private static Map<String, String> map(String title, String selectLevel, String options) {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("menu.title", title);
        m.put("menu.selectLevel", selectLevel);
        m.put("menu.options", options);
        return m;
    }

    /**
     * Create if missing; if exists, append any missing KEYS without overwriting
     * existing ones.
     */
    private static void upsert(File dir, String langName, Map<String, String> desired) {
        File f = new File(dir, langName + ".lang");
        Properties p = new Properties();

        if (f.exists()) {
            try (InputStream in = new FileInputStream(f)) {
                p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException ignored) {
            }
        }

        boolean changed = false;
        for (String k : KEYS) {
            if (!p.containsKey(k)) {
                String v = desired.getOrDefault(k, k);
                p.setProperty(k, v);
                changed = true;
            }
        }

        if (!f.exists() || changed) {
            try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
                w.write("# Auto-generated/updated main-menu translations for " + langName + "\n");
                for (String k : KEYS) {
                    String v = p.getProperty(k, desired.getOrDefault(k, k));
                    w.write(k + "=" + v + "\n");
                }
                System.out.println("[LangGen] " + (f.exists() ? "updated: " : "created: ") + f.getName());
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
}
