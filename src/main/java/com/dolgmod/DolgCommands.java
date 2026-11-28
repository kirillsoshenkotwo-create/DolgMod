package com.dolgmod;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DolgCommands implements CommandExecutor, TabCompleter {
    private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)([smhd])$");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);

        if (cmd.equals("dolg_reload")) {
            if (!sender.hasPermission("dolgmod.admin")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            DolgConfig.load(DolgPlugin.getInstance().getDataFolder());
            sender.sendMessage(ChatColor.GREEN + DolgConfig.tr(
                    "Конфиг dolg_settings.json успешно перезагружен!",
                    "Reloaded dolg_settings.json successfully!"
            ));
            return true;
        }

        if (cmd.equals("dolg_clear")) {
            if (!sender.hasPermission("dolgmod.admin")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage(ChatColor.RED + "Usage: /dolg_clear <player>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + DolgConfig.tr("Игрок не в сети!", "Player is not online!"));
                return true;
            }
            boolean hadDebt = DolgData.getDebt(target.getUniqueId()) != null;
            boolean hadCd = DolgData.getRemainingCooldown(target.getUniqueId()) > 0;
            if (!hadDebt && !hadCd) {
                sender.sendMessage(ChatColor.RED + DolgConfig.tr(
                        "У этого игрока нет активного долга или кулдауна.",
                        "This player has no active debt or cooldown."
                ));
                return true;
            }
            DolgData.removeDebt(target.getUniqueId());
            DolgData.clearCooldown(target.getUniqueId());
            DolgServerHandler.clearBossBar(target.getUniqueId());
            sender.sendMessage(ChatColor.GREEN + DolgConfig.tr(
                    "Долг и кулдаун игрока " + target.getName() + " аннулированы!",
                    "Cleared active debt and cooldown for " + target.getName() + "!"
            ));
            target.sendMessage(ChatColor.GREEN + DolgConfig.tr(
                    "Администратор простил ваш долг и сбросил кулдаун!",
                    "An administrator forgave your debt and reset your cooldown!"
            ));
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        Player p = (Player) sender;

        if (cmd.equals("dolg_pay")) {
            DolgData.Debt debt = DolgData.getDebt(p.getUniqueId());
            if (debt == null) {
                p.sendMessage(ChatColor.GREEN + DolgConfig.tr("У вас нет активных долгов.", "You have no active debts."));
                return true;
            }
            if (tryConsumeItems(p, debt.itemId, debt.count)) {
                DolgData.removeDebt(p.getUniqueId());
                DolgServerHandler.clearBossBar(p.getUniqueId());
                p.sendMessage(ChatColor.GREEN + DolgConfig.tr(
                        "Долг успешно погашен! Предметы изъяты.",
                        "Debt successfully repaid! Items have been collected."
                ));
            } else {
                int has = countItems(p, debt.itemId);
                p.sendMessage(ChatColor.RED + DolgConfig.tr(
                        "Недостаточно целых предметов для погашения! Нужно: " + debt.count + ", у вас: " + has,
                        "Not enough undamaged items to repay! Required: " + debt.count + ", you have: " + has
                ));
            }
            return true;
        }

        if (cmd.equals("dolg_info")) {
            DolgData.Debt debt = DolgData.getDebt(p.getUniqueId());
            if (debt == null) {
                long cdRem = DolgData.getRemainingCooldown(p.getUniqueId());
                if (cdRem > 0) {
                    p.sendMessage(ChatColor.YELLOW + DolgConfig.tr(
                            "Активных долгов нет. Кулдаун до нового займа: ",
                            "No active debts. Cooldown until next loan: "
                    ) + formatTime(cdRem));
                } else {
                    p.sendMessage(ChatColor.GREEN + DolgConfig.tr("У вас нет активных долгов.", "You have no active debts."));
                }
                return true;
            }
            long rem = Math.max(0, debt.expireTimestamp - System.currentTimeMillis());
            p.sendMessage(ChatColor.YELLOW + DolgConfig.tr(
                    "Текущий долг: " + formatItemName(debt.itemId) + " x" + debt.count + " | Осталось: " + formatTime(rem),
                    "Current debt: " + formatItemName(debt.itemId) + " x" + debt.count + " | Time left: " + formatTime(rem)
            ));
            return true;
        }

        if (cmd.equals("dolg_hud")) {
            boolean visible = DolgServerHandler.toggleBossBar(p);
            if (visible) {
                p.sendMessage(ChatColor.GREEN + DolgConfig.tr(
                        "Полоска таймера долга сверху экрана: ВКЛЮЧЕНА",
                        "Debt BossBar timer: VISIBLE"
                ));
            } else {
                p.sendMessage(ChatColor.YELLOW + DolgConfig.tr(
                        "Полоска таймера долга: СКРЫТА (появится за 60 сек. до конца)",
                        "Debt BossBar timer: HIDDEN (will reappear in the final 60s)"
                ));
            }
            return true;
        }

        if (cmd.equals("dolg")) {
            if (args.length < 3) {
                p.sendMessage(ChatColor.RED + DolgConfig.tr(
                        "Использование: /dolg <предмет> <кол-во> <время: 30s, 10m, 2h, 1d>",
                        "Usage: /dolg <item> <count> <duration: 30s, 10m, 2h, 1d>"
                ));
                return true;
            }

            DolgData.Debt existing = DolgData.getDebt(p.getUniqueId());
            if (existing != null) {
                long rem = Math.max(0, existing.expireTimestamp - System.currentTimeMillis());
                p.sendMessage(ChatColor.RED + DolgConfig.tr(
                        "У вас уже есть активный долг! Осталось: ",
                        "You already have an active debt! Time left: "
                ) + formatTime(rem));
                return true;
            }

            long cdRem = DolgData.getRemainingCooldown(p.getUniqueId());
            if (cdRem > 0) {
                p.sendMessage(ChatColor.RED + DolgConfig.tr(
                        "Подождите перед следующим займом! Осталось: ",
                        "You must wait before taking another loan! Cooldown left: "
                ) + formatTime(cdRem));
                return true;
            }

            ItemStack template = parseItemInput(args[0]);
            if (template == null || isForbiddenItem(template)) {
                p.sendMessage(ChatColor.RED + DolgConfig.tr(
                        "Неизвестный или запрещенный предмет для взятия в долг!",
                        "Unknown or forbidden item!"
                ));
                return true;
            }

            int count;
            try {
                count = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                p.sendMessage(ChatColor.RED + DolgConfig.tr("Количество должно быть числом!", "Count must be a number!"));
                return true;
            }
            if (count < 1 || count > 2304) {
                p.sendMessage(ChatColor.RED + "1..2304");
                return true;
            }

            int percent = Math.max(0, DolgConfig.INSTANCE.interestPercent);
            int returnCount = (percent == 0) ? count : (int) Math.ceil(count * (1.0 + percent / 100.0));
            int maxSlotsLimit = template.getMaxStackSize() * 36;
            int hardMax = Math.min(DolgConfig.INSTANCE.maxCount, maxSlotsLimit);

            if (returnCount > hardMax) {
                int maxBorrow = (percent == 0) ? hardMax : (int) Math.floor(hardMax / (1.0 + percent / 100.0));
                p.sendMessage(ChatColor.RED + DolgConfig.tr(
                        "Слишком много для этого предмета! Максимум: " + maxBorrow + " шт.",
                        "Too many items of this type! Max allowed: " + maxBorrow
                ));
                return true;
            }

            String durRaw = args[2].trim().toLowerCase(Locale.ROOT);
            long dur = parseDuration(durRaw);
            if (dur <= 0) {
                p.sendMessage(ChatColor.RED + DolgConfig.tr(
                        "Ошибка времени! Используйте формат: 30s, 10m, 2h, 1d",
                        "Invalid duration! Use format: 30s, 10m, 2h, 1d"
                ));
                return true;
            }
            if (dur > DolgConfig.INSTANCE.maxDurationSeconds * 1000L) {
                p.sendMessage(ChatColor.RED + DolgConfig.tr(
                        "Слишком большой срок! Максимум секунд: " + DolgConfig.INSTANCE.maxDurationSeconds,
                        "Duration is too long! Max seconds: " + DolgConfig.INSTANCE.maxDurationSeconds
                ));
                return true;
            }

String itemKey = template.getType().name() + ":" + template.getDurability();
int leftToGive = count;
while (leftToGive > 0) {
int batch = Math.min(leftToGive, template.getMaxStackSize());
ItemStack toAdd = template.clone();
toAdd.setAmount(batch);
HashMap<Integer, ItemStack> leftovers = p.getInventory().addItem(toAdd);
for (ItemStack drop : leftovers.values()) {
p.getWorld().dropItemNaturally(p.getLocation(), drop);
}
leftToGive -= batch;
}
DolgData.Debt debt = new DolgData.Debt(
p.getUniqueId().toString(),
p.getName(),
itemKey,
returnCount,
System.currentTimeMillis() + dur,
dur
);
DolgData.addDebt(debt);
if (DolgConfig.isRu()) {
String extra = (returnCount > count) ? " (вернуть с процентами: " + returnCount + " шт.)" : "";
p.sendMessage(ChatColor.GOLD + "Выдаю " + formatItemName(itemKey) + ChatColor.YELLOW
+ " x" + count + " в долг на " + durRaw + extra + "! Верните через /dolg_pay, иначе смерть!");
} else {
String extra = (returnCount > count) ? " (repay with interest: " + returnCount + ")" : "";
p.sendMessage(ChatColor.GOLD + "Loaned " + formatItemName(itemKey) + ChatColor.YELLOW
+ " x" + count + " for " + durRaw + extra + "! Repay via /dolg_pay or die!");
}
return true;
}
return false;
}
public static ItemStack parseItemInput(String raw) {
String clean = raw.toLowerCase(Locale.ROOT);
if (clean.startsWith("minecraft:")) {
clean = clean.substring("minecraft:".length());
}
short data = 0;
if (clean.contains(":")) {
String[] parts = clean.split(":", 2);
clean = parts[0];
try {
data = Short.parseShort(parts[1]);
if (data < 0) return null;
} catch (NumberFormatException e) {
return null;
}
}
Material mat = Material.matchMaterial(clean.toUpperCase(Locale.ROOT));
if (mat == null || mat == Material.AIR) return null;
if (mat.getMaxDurability() > 0 && data != 0) return null; // Нельзя брать ломаную броню/оружие
return new ItemStack(mat, 1, data);
}
private static boolean isForbiddenItem(ItemStack stack) {
if (stack == null || stack.getType() == Material.AIR) return true;
String name = stack.getType().name();
for (String banned : DolgConfig.INSTANCE.bannedItemIds) {
String cleanBanned = banned.replace("minecraft:", "").toUpperCase(Locale.ROOT);
if (cleanBanned.equals(name)) return true;
}
return false;
}
private static boolean isCleanMatchingStack(ItemStack s, Material targetMat, short targetData) {
if (s == null || s.getType() != targetMat) return false;
if (s.hasItemMeta()) return false; // Пропускаем переименованные/зачарованные предметы
if (targetMat.getMaxDurability() > 0) {
return s.getDurability() == 0; // Инструменты/броня должны быть полностью целыми
}
return s.getDurability() == targetData;
}
public static int countItems(Player player, String itemKey) {
String[] parts = itemKey.split(":");
Material mat = Material.getMaterial(parts[0]);
short data = parts.length > 1 ? Short.parseShort(parts[1]) : 0;
if (mat == null) return 0;
int total = 0;
for (ItemStack s : player.getInventory().getStorageContents()) {
if (isCleanMatchingStack(s, mat, data)) {
total += s.getAmount();
}
}
ItemStack offhand = player.getInventory().getItemInOffHand();
if (isCleanMatchingStack(offhand, mat, data)) {
total += offhand.getAmount();
}
return total;
}
public static boolean tryConsumeItems(Player player, String itemKey, int required) {
if (countItems(player, itemKey) < required) return false;
String[] parts = itemKey.split(":");
Material mat = Material.getMaterial(parts[0]);
short data = parts.length > 1 ? Short.parseShort(parts[1]) : 0;
if (mat == null) return false;
PlayerInventory inv = player.getInventory();
ItemStack[] contents = inv.getStorageContents();
int remaining = required;
for (int i = 0; i < contents.length && remaining > 0; i++) {
ItemStack s = contents[i];
if (isCleanMatchingStack(s, mat, data)) {
int take = Math.min(remaining, s.getAmount());
if (s.getAmount() - take <= 0) {
contents[i] = null;
} else {
s.setAmount(s.getAmount() - take);
}
remaining -= take;
}
}
inv.setStorageContents(contents);
if (remaining > 0) {
ItemStack off = inv.getItemInOffHand();
if (isCleanMatchingStack(off, mat, data)) {
int take = Math.min(remaining, off.getAmount());
if (off.getAmount() - take <= 0) {
inv.setItemInOffHand(null);
} else {
off.setAmount(off.getAmount() - take);
}
}
}
player.updateInventory();
return true;
}
public static String formatItemName(String itemKey) {
String[] parts = itemKey.split(":");
return (parts.length > 1 && !"0".equals(parts[1])) ? itemKey : parts[0];
}
public static String formatTime(long millis) {
long totalSec = Math.max(0, millis / 1000);
long h = totalSec / 3600;
long m = (totalSec % 3600) / 60;
long s = totalSec % 60;
return String.format("%02d:%02d:%02d", h, m, s);
}
private static long parseDuration(String s) {
Matcher m = DURATION_PATTERN.matcher(s);
if (!m.matches()) return -1;
long v = Long.parseLong(m.group(1));
if (v <= 0) return -1;
switch (m.group(2)) {
case "s": return v * 1000L;
case "m": return v * 60_000L;
case "h": return v * 3_600_000L;
case "d": return v * 86_400_000L;
default: return -1;
}
}
@Override
public List onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
if (command.getName().equalsIgnoreCase("dolg")) {
if (args.length == 1) {
String prefix = args[0].toUpperCase(Locale.ROOT).replace("MINECRAFT:", "");
return Arrays.stream(Material.values())
.map(m -> m.name().toLowerCase(Locale.ROOT))
.filter(name -> name.startsWith(prefix.toLowerCase(Locale.ROOT)))
.limit(30)
.collect(Collectors.toList());
} else if (args.length == 2) {
return Arrays.asList("1", "16", "32", "64");
} else if (args.length == 3) {
return Arrays.asList("5m", "30m", "1h", "1d");
}
}
return Collections.emptyList();
}
}