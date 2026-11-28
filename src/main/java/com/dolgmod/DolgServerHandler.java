package com.dolgmod;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DolgServerHandler implements Listener {
    private static final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();
    private static final Set<UUID> hiddenBossBars = ConcurrentHashMap.newKeySet();

    public static void init(DolgPlugin plugin) {
        Bukkit.getPluginManager().registerEvents(new DolgServerHandler(), plugin);
        // Запуск проверки каждую 1 секунду (20 тиков)
        Bukkit.getScheduler().runTaskTimer(plugin, DolgServerHandler::processDebts, 20L, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clearBossBar(event.getPlayer().getUniqueId());
    }

    public static boolean toggleBossBar(Player player) {
        UUID uuid = player.getUniqueId();
        if (hiddenBossBars.contains(uuid)) {
            hiddenBossBars.remove(uuid);
            return true;
        } else {
            hiddenBossBars.add(uuid);
            clearBossBar(uuid);
            return false;
        }
    }

    public static void clearBossBar(UUID uuid) {
        BossBar bar = bossBars.remove(uuid);
        if (bar != null) {
            bar.removeAll();
        }
    }

    public static void clearAllBossBars() {
        for (BossBar bar : bossBars.values()) {
            bar.removeAll();
        }
        bossBars.clear();
    }

    private static void processDebts() {
        long now = System.currentTimeMillis();
        for (DolgData.Debt debt : DolgData.getAllDebts()) {
            UUID uuid = UUID.fromString(debt.uuid);
            Player player = Bukkit.getPlayer(uuid);
            long remaining = debt.expireTimestamp - now;

            if (remaining > 0) {
                if (player != null && player.isOnline()) {
                    if (hiddenBossBars.contains(uuid) && remaining > 60_000L) {
                        clearBossBar(uuid);
                        continue;
                    }
                    String title = ChatColor.GOLD + DolgConfig.tr("ДОЛГ: ", "DEBT: ")
                            + ChatColor.WHITE + DolgCommands.formatItemName(debt.itemId)
                            + ChatColor.YELLOW + DolgConfig.tr(" x" + debt.count + " — Осталось: ", " x" + debt.count + " — Time left: ")
                            + DolgCommands.formatTime(remaining);

                    BossBar bar = bossBars.computeIfAbsent(uuid, id ->
                            Bukkit.createBossBar(title, BarColor.YELLOW, BarStyle.SOLID)
                    );
                    bar.setTitle(title);
                    double progress = Math.max(0.0, Math.min(1.0, (double) remaining / (double) debt.totalDurationMillis));
                    bar.setProgress(progress);
                    bar.setColor(remaining < 30_000L ? BarColor.RED : BarColor.YELLOW);

                    if (!bar.getPlayers().contains(player)) {
                        bar.addPlayer(player);
                    }
                }
            } else {
                hiddenBossBars.remove(uuid);
                DolgData.removeDebt(uuid);
                clearBossBar(uuid);

                if (player != null && player.isOnline()) {
                    if (DolgCommands.tryConsumeItems(player, debt.itemId, debt.count)) {
                        player.sendMessage(ChatColor.GREEN + DolgConfig.tr(
                                "Срок долга истёк! Предметы успешно изъяты из инвентаря.",
                                "Debt expired! Borrowed items have been deducted from your inventory."
                        ));
                    } else {
                        Bukkit.broadcastMessage(ChatColor.RED + DolgConfig.tr(
                                debt.playerName + " не вернул долг (" + DolgCommands.formatItemName(debt.itemId) + " x" + debt.count + ") вовремя и был казнён!",
                                debt.playerName + " failed to repay their debt (" + DolgCommands.formatItemName(debt.itemId) + " x" + debt.count + ") on time and was executed!"
                        ));
                        player.setHealth(0.0);
                    }
                } else {
                    // Игрок оффлайн в момент истечения срока -> бан на 1 день, как в оригинальном моде
                    Date expireBan = new Date(now + 86_400_000L);
                    String reason = DolgConfig.tr("Не вернул долг", "Failed to repay debt");
                    Bukkit.getBanList(BanList.Type.NAME).addBan(debt.playerName, reason, expireBan, "DolgMod");
                    Bukkit.broadcastMessage(ChatColor.RED + DolgConfig.tr(
                            debt.playerName + " не вернул долг в оффлайне и забанен на 1 день по причине: " + reason + "!",
                            debt.playerName + " failed to repay their debt while offline and was banned for 1 day (" + reason + ")!"
                    ));
                }
            }
        }
    }
}