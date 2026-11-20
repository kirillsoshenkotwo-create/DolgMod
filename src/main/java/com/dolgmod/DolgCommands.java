package com.dolgmod;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.GameMasterBlock;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
public class DolgCommands {
private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)([smhd])$");
public static void register(CommandDispatcher d, CommandBuildContext registryAccess) {
d.register(Commands.literal("dolg")
.then(Commands.argument("item", ItemArgument.item(registryAccess))
.then(Commands.argument("count", IntegerArgumentType.integer(1, 2304))
.then(Commands.argument("duration", StringArgumentType.word())
.executes(c -> {
ServerPlayer p = c.getSource().getPlayerOrException();
DolgData.Debt existing = DolgData.getDebt(p.getUUID());
if (existing != null) {
long rem = Math.max(0, existing.expireTimestamp - System.currentTimeMillis());
p.sendSystemMessage(Component.literal(DolgConfig.tr(
"У вас уже есть активный долг! Осталось: ",
"You already have an active debt! Time left: "
) + formatTime(rem)).withStyle(ChatFormatting.RED));
return 0;
}
long cdRem = DolgData.getRemainingCooldown(p.getUUID());
if (cdRem > 0) {
p.sendSystemMessage(Component.literal(DolgConfig.tr(
"Подождите перед следующим займом! Осталось: ",
"You must wait before taking another loan! Cooldown left: "
) + formatTime(cdRem)).withStyle(ChatFormatting.RED));
return 0;
}
ItemStack template = ItemArgument.getItem(c, "item").createItemStack(1);
if (isForbiddenItem(template)) {
p.sendSystemMessage(Component.literal(DolgConfig.tr(
"Этот предмет запрещено брать в долг!",
"This item is forbidden from borrowing!"
)).withStyle(ChatFormatting.RED));
return 0;
}
int count = IntegerArgumentType.getInteger(c, "count");
int percent = Math.max(0, DolgConfig.INSTANCE.interestPercent);
int returnCount = (percent == 0) ? count : (int) Math.ceil(count * (1.0 + percent / 100.0));
int maxSlotsLimit = template.getMaxStackSize() * 36;
int hardMax = Math.min(DolgConfig.INSTANCE.maxCount, maxSlotsLimit);
if (returnCount > hardMax) {
int maxBorrow = (percent == 0) ? hardMax : (int) Math.floor(hardMax / (1.0 + percent / 100.0));
p.sendSystemMessage(Component.literal(DolgConfig.tr(
"Слишком много для этого предмета (не влезет в инвентарь при возврате)! Максимум: " + maxBorrow + " шт.",
"Too many items of this type to fit in inventory upon repayment! Max allowed: " + maxBorrow
)).withStyle(ChatFormatting.RED));
return 0;
}
String durRaw = StringArgumentType.getString(c, "duration").trim().toLowerCase(Locale.ROOT);
long dur = parseDuration(durRaw);
if (dur <= 0) {
p.sendSystemMessage(Component.literal(DolgConfig.tr(
"Ошибка времени! Используйте формат: 30s, 10m, 2h, 1d",
"Invalid duration! Use format: 30s, 10m, 2h, 1d"
)).withStyle(ChatFormatting.RED));
return 0;
}
if (dur > DolgConfig.INSTANCE.maxDurationSeconds * 1000L) {
p.sendSystemMessage(Component.literal(DolgConfig.tr(
"Слишком большой срок! Максимум секунд: " + DolgConfig.INSTANCE.maxDurationSeconds,
"Duration is too long! Max seconds: " + DolgConfig.INSTANCE.maxDurationSeconds
)).withStyle(ChatFormatting.RED));
return 0;
}
String itemId = BuiltInRegistries.ITEM.getKey(template.getItem()).toString();
int leftToGive = count;
while (leftToGive > 0) {
int batch = Math.min(leftToGive, template.getMaxStackSize());
ItemStack toAdd = template.copyWithCount(batch);
if (!p.getInventory().add(toAdd)) {
p.level().addFreshEntity(new ItemEntity(p.level(), p.getX(), p.getY(), p.getZ(), toAdd));
}
leftToGive -= batch;
}
DolgData.Debt debt = new DolgData.Debt(
p.getUUID().toString(),
p.getScoreboardName(),
itemId,
returnCount,
System.currentTimeMillis() + dur,
dur
);
DolgData.addDebt(debt);
if (DolgConfig.isRu()) {
String extra = (returnCount > count) ? " (вернуть нужно с процентами: " + returnCount + " шт.)" : "";
p.sendSystemMessage(Component.literal("Выдаю ")
.withStyle(ChatFormatting.GOLD)
.append(template.getHoverName())
.append(Component.literal(" x" + count + " в долг на " + durRaw + extra + "! Верните через /dolg_pay, иначе смерть!").withStyle(ChatFormatting.YELLOW)));
} else {
String extra = (returnCount > count) ? " (repay with interest: " + returnCount + ")" : "";
p.sendSystemMessage(Component.literal("Loaned ")
.withStyle(ChatFormatting.GOLD)
.append(template.getHoverName())
.append(Component.literal(" x" + count + " for " + durRaw + extra + "! Repay via /dolg_pay before time runs out or die!").withStyle(ChatFormatting.YELLOW)));
}
return 1;
})))));
d.register(Commands.literal("dolg_pay")
.executes(c -> {
ServerPlayer p = c.getSource().getPlayerOrException();
DolgData.Debt debt = DolgData.getDebt(p.getUUID());
if (debt == null) {
p.sendSystemMessage(Component.literal(DolgConfig.tr(
"У вас нет активных долгов.",
"You have no active debts."
)).withStyle(ChatFormatting.GREEN));
return 0;
}
if (tryConsumeItems(p, debt.itemId, debt.count)) {
DolgData.removeDebt(p.getUUID());
DolgServerHandler.clearBossBar(p.getUUID());
p.sendSystemMessage(Component.literal(DolgConfig.tr(
"Долг успешно погашен! Предметы изъяты.",
"Debt successfully repaid! Items have been collected."
)).withStyle(ChatFormatting.GREEN));
return 1;
} else {
int has = countItems(p, debt.itemId);
p.sendSystemMessage(Component.literal(DolgConfig.tr(
"Недостаточно целых предметов для погашения! Нужно: " + debt.count + ", у вас: " + has,
"Not enough undamaged items to repay! Required: " + debt.count + ", you have: " + has
)).withStyle(ChatFormatting.RED));
return 0;
}
}));
d.register(Commands.literal("dolg_info")
.executes(c -> {
ServerPlayer p = c.getSource().getPlayerOrException();
DolgData.Debt debt = DolgData.getDebt(p.getUUID());
if (debt == null) {
long cdRem = DolgData.getRemainingCooldown(p.getUUID());
if (cdRem > 0) {
p.sendSystemMessage(Component.literal(DolgConfig.tr(
"Активных долгов нет. Кулдаун до нового займа: ",
"No active debts. Cooldown until next loan: "
) + formatTime(cdRem)).withStyle(ChatFormatting.YELLOW));
} else {
p.sendSystemMessage(Component.literal(DolgConfig.tr(
"У вас нет активных долгов.",
"You have no active debts."
)).withStyle(ChatFormatting.GREEN));
}
return 1;
}
long rem = Math.max(0, debt.expireTimestamp - System.currentTimeMillis());
p.sendSystemMessage(Component.literal(DolgConfig.tr(
"Текущий долг: " + debt.itemId + " x" + debt.count + " | Осталось: " + formatTime(rem),
"Current debt: " + debt.itemId + " x" + debt.count + " | Time left: " + formatTime(rem)
)).withStyle(ChatFormatting.YELLOW));
return 1;
}));
d.register(Commands.literal("dolg_hud")
.executes(c -> {
ServerPlayer p = c.getSource().getPlayerOrException();
boolean visible = DolgServerHandler.toggleBossBar(p);
if (visible) {
p.sendSystemMessage(Component.literal(DolgConfig.tr(
"Полоска таймера долга сверху экрана: ВКЛЮЧЕНА",
"Debt BossBar timer: VISIBLE"
)).withStyle(ChatFormatting.GREEN));
} else {
p.sendSystemMessage(Component.literal(DolgConfig.tr(
"Полоска таймера долга: СКРЫТА (появится за 60 сек. до конца)",
"Debt BossBar timer: HIDDEN (will reappear in the final 60s)"
)).withStyle(ChatFormatting.YELLOW));
}
return 1;
}));
d.register(Commands.literal("dolg_clear")
.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
.then(Commands.argument("target", EntityArgument.player())
.executes(c -> {
ServerPlayer target = EntityArgument.getPlayer(c, "target");
boolean hadDebt = DolgData.getDebt(target.getUUID()) != null;
boolean hadCd = DolgData.getRemainingCooldown(target.getUUID()) > 0;
if (!hadDebt && !hadCd) {
c.getSource().sendFailure(Component.literal(DolgConfig.tr(
"У этого игрока нет активного долга или кулдауна.",
"This player has no active debt or cooldown."
)));
return 0;
}
DolgData.removeDebt(target.getUUID());
DolgData.clearCooldown(target.getUUID());
DolgServerHandler.clearBossBar(target.getUUID());
c.getSource().sendSuccess(() -> Component.literal(DolgConfig.tr(
"Долг и кулдаун игрока " + target.getScoreboardName() + " аннулированы!",
"Cleared active debt and cooldown for " + target.getScoreboardName() + "!"
)).withStyle(ChatFormatting.GREEN), true);
target.sendSystemMessage(Component.literal(DolgConfig.tr(
"Администратор простил ваш долг и сбросил кулдаун!",
"An administrator forgave your debt and reset your cooldown!"
)).withStyle(ChatFormatting.GREEN));
return 1;
})));
d.register(Commands.literal("dolg_reload")
.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
.executes(c -> {
DolgConfig.load();
c.getSource().sendSuccess(() -> Component.literal(DolgConfig.tr(
"Конфиг dolg_settings.json успешно перезагружен!",
"Reloaded dolg_settings.json successfully!"
)).withStyle(ChatFormatting.GREEN), true);
return 1;
}));
}
private static boolean isForbiddenItem(ItemStack stack) {
if (stack.isEmpty()) return true;
if (!stack.getComponentsPatch().isEmpty()) return true;
Item item = stack.getItem();
if (item instanceof SpawnEggItem) return true;
if (item instanceof BlockItem blockItem && blockItem.getBlock() instanceof GameMasterBlock) return true;
String id = BuiltInRegistries.ITEM.getKey(item).toString();
return DolgConfig.INSTANCE.bannedItemIds.contains(id);
}
public static int countItems(ServerPlayer player, String itemId) {
Item target = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
Inventory inv = player.getInventory();
int total = 0;
for (int i = 0; i < inv.getContainerSize(); i++) {
ItemStack s = inv.getItem(i);
if (!s.isEmpty() && s.is(target) && !s.isDamaged() && s.getComponentsPatch().isEmpty()) {
total += s.getCount();
}
}
return total;
}
public static boolean tryConsumeItems(ServerPlayer player, String itemId, int required) {
if (countItems(player, itemId) < required) return false;
Item target = BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
Inventory inv = player.getInventory();
int remaining = required;
for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
ItemStack s = inv.getItem(i);
if (!s.isEmpty() && s.is(target) && !s.isDamaged() && s.getComponentsPatch().isEmpty()) {
int take = Math.min(remaining, s.getCount());
s.shrink(take);
remaining -= take;
}
}
inv.setChanged();
return true;
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
return switch (m.group(2)) {
case "s" -> v * 1000L;
case "m" -> v * 60_000L;
case "h" -> v * 3_600_000L;
case "d" -> v * 86_400_000L;
default -> -1;
};
}
}