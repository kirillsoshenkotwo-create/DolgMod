package com.dolgmod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
public class DolgConfig {
private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("dolg_settings.json");
public static Settings INSTANCE = new Settings();
public static class Settings {
public String _comment_lang = "Язык сообщений / Language: 'en' or 'ru'";
public String language = "en";
public String _comment_interest = "Процент наценки при возврате / Interest rate % (0 = 0%, 20 = +20%)";
public int interestPercent = 0;
public String _comment_cooldown = "Кулдаун между займами в секундах / Cooldown between loans in seconds (3600 = 1 hour, 0 = off)";
public long cooldownSeconds = 3600L;
public String _comment_maxCount = "Максимум предметов за один займ / Max items per loan (up to 2304)";
public int maxCount = 2304;
public String _comment_maxDuration = "Максимальный срок долга в секундах / Max duration in seconds (2592000 = 30 days)";
public long maxDurationSeconds = 2592000L;
public String _comment_banned = "Список запрещенных ID предметов / Banned item IDs";
public List bannedItemIds = defaultBannedList();
}
public static boolean isRu() {
return INSTANCE != null && "ru".equalsIgnoreCase(INSTANCE.language);
}
public static String tr(String ru, String en) {
return isRu() ? ru : en;
}
private static List defaultBannedList() {
return new ArrayList<>(List.of(
"minecraft:air",
"minecraft:bedrock",
"minecraft:barrier",
"minecraft:structure_void",
"minecraft:structure_block",
"minecraft:jigsaw",
"minecraft:command_block",
"minecraft:chain_command_block",
"minecraft:repeating_command_block",
"minecraft:command_block_minecart",
"minecraft:light",
"minecraft:debug_stick",
"minecraft:spawner",
"minecraft:trial_spawner",
"minecraft:vault",
"minecraft:reinforced_deepslate",
"minecraft:end_portal_frame",
"minecraft:knowledge_book",
"minecraft:bundle",
"minecraft:totem_of_undying"
));
}
public static void load() {
File file = CONFIG_PATH.toFile();
if (!file.exists() || file.length() == 0) {
INSTANCE = new Settings();
save();
return;
}
try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
Settings loaded = GSON.fromJson(reader, Settings.class);
if (loaded == null) {
INSTANCE = new Settings();
save();
return;
}
if (loaded.language == null || loaded.language.isBlank()) loaded.language = "en";
if (loaded.bannedItemIds == null) loaded.bannedItemIds = defaultBannedList();
if (loaded.maxCount <= 0) loaded.maxCount = 2304;
if (loaded.maxDurationSeconds <= 0) loaded.maxDurationSeconds = 2592000L;
if (loaded.cooldownSeconds < 0) loaded.cooldownSeconds = 0L;
INSTANCE = loaded;
save();
} catch (Exception e) {
DolgMod.LOGGER.error("Failed to load dolg_settings.json, recreating default", e);
INSTANCE = new Settings();
save();
}
}
public static void save() {
try {
CONFIG_PATH.getParent().toFile().mkdirs();
try (Writer writer = new OutputStreamWriter(new FileOutputStream(CONFIG_PATH.toFile()), StandardCharsets.UTF_8)) {
GSON.toJson(INSTANCE, writer);
}
} catch (IOException e) {
DolgMod.LOGGER.error("Failed to save dolg_settings.json", e);
}
}
}