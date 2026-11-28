package com.dolgmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DolgData {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File debtsFile;
    private static File cooldownsFile;
    private static final Map<UUID, Debt> debts = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public static class Debt {
        public String uuid;
        public String playerName;
        public String itemId; // Формат MATERIAL:DATA, например DIAMOND:0 или WOOL:14
        public int count;
        public long expireTimestamp;
        public long totalDurationMillis;

        public Debt(String u, String p, String i, int c, long e, long totalDur) {
            this.uuid = u;
            this.playerName = p;
            this.itemId = i;
            this.count = c;
            this.expireTimestamp = e;
            this.totalDurationMillis = totalDur;
        }
    }

    @SuppressWarnings("unchecked")
    public static void load(File dataFolder) {
        debtsFile = new File(dataFolder, "dolg_mod.json");
        cooldownsFile = new File(dataFolder, "dolg_cooldowns.json");

        if (debtsFile.exists()) {
            try (Reader reader = new InputStreamReader(new FileInputStream(debtsFile), StandardCharsets.UTF_8)) {
                Debt[] loaded = GSON.fromJson(reader, Debt[].class);
                debts.clear();
                if (loaded != null) {
                    for (Debt d : loaded) {
                        if (d.totalDurationMillis <= 0) d.totalDurationMillis = 60_000L;
                        debts.put(UUID.fromString(d.uuid), d);
                    }
                }
            } catch (Exception e) {
                DolgPlugin.getInstance().getLogger().warning("Failed to load debts: " + e.getMessage());
            }
        }

        if (cooldownsFile.exists()) {
            try (Reader reader = new InputStreamReader(new FileInputStream(cooldownsFile), StandardCharsets.UTF_8)) {
                Map<String, Double> loadedCd = GSON.fromJson(reader, Map.class);
                cooldowns.clear();
                if (loadedCd != null) {
                    for (Map.Entry<String, Double> entry : loadedCd.entrySet()) {
                        cooldowns.put(UUID.fromString(entry.getKey()), entry.getValue().longValue());
                    }
                }
            } catch (Exception e) {
                DolgPlugin.getInstance().getLogger().warning("Failed to load cooldowns: " + e.getMessage());
            }
        }
    }

    public static void save() {
        if (debtsFile == null) return;
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(debtsFile), StandardCharsets.UTF_8)) {
            GSON.toJson(debts.values(), writer);
        } catch (IOException e) {
            DolgPlugin.getInstance().getLogger().warning("Failed to save debts: " + e.getMessage());
        }
    }

    public static void saveCooldowns() {
        if (cooldownsFile == null) return;
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(cooldownsFile), StandardCharsets.UTF_8)) {
            Map<String, Long> out = new HashMap<>();
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, Long> entry : cooldowns.entrySet()) {
                if (entry.getValue() > now) {
                    out.put(entry.getKey().toString(), entry.getValue());
                }
            }
            GSON.toJson(out, writer);
        } catch (IOException e) {
            DolgPlugin.getInstance().getLogger().warning("Failed to save cooldowns: " + e.getMessage());
        }
    }

    public static Debt getDebt(UUID u) {
        return debts.get(u);
    }

    public static void addDebt(Debt d) {
        debts.put(UUID.fromString(d.uuid), d);
        save();
    }

    public static void removeDebt(UUID u) {
        debts.remove(u);
        save();
        if (DolgConfig.INSTANCE.cooldownSeconds > 0) {
            cooldowns.put(u, System.currentTimeMillis() + DolgConfig.INSTANCE.cooldownSeconds * 1000L);
            saveCooldowns();
        }
    }

    public static void clearCooldown(UUID u) {
        cooldowns.remove(u);
        saveCooldowns();
    }

    public static long getRemainingCooldown(UUID u) {
        Long until = cooldowns.get(u);
        if (until == null) return 0L;
        long rem = until - System.currentTimeMillis();
        if (rem <= 0) {
            cooldowns.remove(u);
            return 0L;
        }
        return rem;
    }

    public static Collection<Debt> getAllDebts() {
        return new ArrayList<>(debts.values());
    }
}
