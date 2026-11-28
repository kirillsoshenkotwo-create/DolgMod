package com.dolgmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class DolgConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static File configFile;
    public static Settings INSTANCE = new Settings();

    public static class Settings {
        public String _comment_lang = "Язык сообщений / Language: 'en' or 'ru'";
        public String language = "ru";
        public String _comment_interest = "Процент наценки при возврате / Interest rate % (0 = 0%, 20 = +20%)";
        public int interestPercent = 0;
        public String _comment_cooldown = "Кулдаун между займами в секундах (3600 = 1 hour, 0 = off)";
        public long cooldownSeconds = 3600L;
        public String _comment_maxCount = "Максимум предметов за один займ (up to 2304)";
        public int maxCount = 2304;
        public String _comment_maxDuration = "Максимальный срок долга в секундах (2592000 = 30 days)";
        public long maxDurationSeconds = 2592000L;
        public String _comment_banned = "Список запрещенных Material в 1.12.2";
        public List<String> bannedItemIds = defaultBannedList();
    }

    public static boolean isRu() {
        return INSTANCE != null && "ru".equalsIgnoreCase(INSTANCE.language);
    }

    public static String tr(String ru, String en) {
        return isRu() ? ru : en;
    }

    private static List<String> defaultBannedList() {
        return Arrays.asList(
                "AIR",
                "BEDROCK",
                "BARRIER",
                "STRUCTURE_VOID",
                "STRUCTURE_BLOCK",
                "COMMAND",
                "COMMAND_CHAIN",
                "COMMAND_REPEATING",
                "COMMAND_MINECART",
                "MOB_SPAWNER",
                "ENDER_PORTAL_FRAME",
                "KNOWLEDGE_BOOK",
                "MONSTER_EGG",
                "TOTEM"
        );
    }

    public static void load(File dataFolder) {
        if (dataFolder != null) {
            configFile = new File(dataFolder, "dolg_settings.json");
        }
        if (configFile == null) return;

        if (!configFile.exists() || configFile.length() == 0) {
            INSTANCE = new Settings();
            save();
            return;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8)) {
            Settings loaded = GSON.fromJson(reader, Settings.class);
            if (loaded == null) {
                INSTANCE = new Settings();
                save();
                return;
            }
            if (loaded.language == null || loaded.language.trim().isEmpty()) loaded.language = "ru";
            if (loaded.bannedItemIds == null) loaded.bannedItemIds = defaultBannedList();
            if (loaded.maxCount <= 0) loaded.maxCount = 2304;
            if (loaded.maxDurationSeconds <= 0) loaded.maxDurationSeconds = 2592000L;
            if (loaded.cooldownSeconds < 0) loaded.cooldownSeconds = 0L;
            INSTANCE = loaded;
            save();
        } catch (Exception e) {
            DolgPlugin.getInstance().getLogger().warning("Failed to load dolg_settings.json, recreating default: " + e.getMessage());
            INSTANCE = new Settings();
            save();
        }
    }

    public static void save() {
        if (configFile == null) return;
        try {
            configFile.getParentFile().mkdirs();
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (IOException e) {
            DolgPlugin.getInstance().getLogger().warning("Failed to save dolg_settings.json: " + e.getMessage());
        }
    }
}
