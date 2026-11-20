package com.dolgmod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.world.BossEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
public class DolgServerHandler {
private static int tickCounter = 0;
private static final Map<UUID, ServerBossEvent> bossBars = new ConcurrentHashMap<>();
private static final Set hiddenBossBars = ConcurrentHashMap.newKeySet();
public static void init() {
ServerTickEvents.END_SERVER_TICK.register(server -> {
if (++tickCounter >= 20) {
tickCounter = 0;
processDebts(server);
}
});
ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
clearBossBar(handler.player.getUUID());
});
}
public static boolean toggleBossBar(ServerPlayer player) {
UUID uuid = player.getUUID();
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
ServerBossEvent bar = bossBars.remove(uuid);
if (bar != null) {
bar.removeAllPlayers();
}
}
private static void processDebts(MinecraftServer server) {
long now = System.currentTimeMillis();
for (DolgData.Debt debt : DolgData.getAllDebts()) {
UUID uuid = UUID.fromString(debt.uuid);
ServerPlayer player = server.getPlayerList().getPlayer(uuid);
long remaining = debt.expireTimestamp - now;
if (remaining > 0) {
if (player != null) {
if (hiddenBossBars.contains(uuid) && remaining > 60_000L) {
clearBossBar(uuid);
continue;
}
Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(debt.itemId));
Component title = Component.literal(DolgConfig.tr("ДОЛГ: ", "DEBT: "))
.append(item.getDefaultInstance().getHoverName())
.append(Component.literal(DolgConfig.tr(" x" + debt.count + " — Осталось: ", " x" + debt.count + " — Time left: ") + DolgCommands.formatTime(remaining)));
ServerBossEvent bar = bossBars.computeIfAbsent(uuid, id ->
new ServerBossEvent(UUID.randomUUID(), title, BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS)
);
bar.setName(title);
float progress = Math.max(0.0f, Math.min(1.0f, (float) remaining / (float) debt.totalDurationMillis));
bar.setProgress(progress);
if (remaining < 30_000L) {
bar.setColor(BossEvent.BossBarColor.RED);
}
if (!bar.getPlayers().contains(player)) {
bar.addPlayer(player);
}
}
} else {
hiddenBossBars.remove(uuid);
if (player != null) {
DolgData.removeDebt(uuid);
clearBossBar(uuid);
if (DolgCommands.tryConsumeItems(player, debt.itemId, debt.count)) {
player.sendSystemMessage(Component.literal(DolgConfig.tr(
"Срок долга истёк! Предметы успешно изъяты из инвентаря.",
"Debt expired! Borrowed items have been deducted from your inventory."
)).withStyle(ChatFormatting.GREEN));
} else {
server.getPlayerList().broadcastSystemMessage(
Component.literal(DolgConfig.tr(
debt.playerName + " не вернул долг (" + debt.itemId + " x" + debt.count + ") вовремя и был казнён!",
debt.playerName + " failed to repay their debt (" + debt.itemId + " x" + debt.count + ") on time and was executed!"
)).withStyle(ChatFormatting.RED),
false
);
if (player.level() instanceof ServerLevel serverLevel) {
player.kill(serverLevel);
}
}
} else if (server.isDedicatedServer()) {
DolgData.removeDebt(uuid);
clearBossBar(uuid);
boolean paidOffline = tryConsumeOffline(server, uuid, debt.itemId, debt.count);
if (paidOffline) {
DolgMod.LOGGER.info("[DolgMod] Offline debt collected from {} ({} x{}).", debt.playerName, debt.itemId, debt.count);
} else {
Date expireBan = new Date(now + 86_400_000L);
String reason = DolgConfig.tr("Не вернул долг", "Failed to repay debt");
UserBanListEntry entry = new UserBanListEntry(
new NameAndId(uuid, debt.playerName),
new Date(now),
"DolgMod",
expireBan,
reason
);
server.getPlayerList().getBans().add(entry);
server.getPlayerList().broadcastSystemMessage(
Component.literal(DolgConfig.tr(
debt.playerName + " не вернул долг в оффлайне и забанен на 1 день по причине: " + reason + "!",
debt.playerName + " failed to repay their debt while offline and was banned for 1 day (" + reason + ")!"
)).withStyle(ChatFormatting.RED),
false
);
}
}
}
}
}
private static boolean tryConsumeOffline(MinecraftServer server, UUID uuid, String itemId, int required) {
try {
Path playerFile = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(uuid.toString() + ".dat");
if (!Files.exists(playerFile)) return false;
CompoundTag root = NbtIo.readCompressed(playerFile, NbtAccounter.unlimitedHeap());
ListTag inventory = root.getListOrEmpty("Inventory");
int total = 0;
for (int i = 0; i < inventory.size(); i++) {
CompoundTag slotTag = inventory.getCompoundOrEmpty(i);
String id = slotTag.getStringOr("id", "");
// Пропускаем повреждённые/изменённые предметы (у которых есть тег components с damage)
if (id.equals(itemId) && !slotTag.contains("components")) {
total += slotTag.getIntOr("count", 0);
}
}
if (total < required) return false;
int remaining = required;
for (int i = inventory.size() - 1; i >= 0 && remaining > 0; i--) {
CompoundTag slotTag = inventory.getCompoundOrEmpty(i);
String id = slotTag.getStringOr("id", "");
if (id.equals(itemId) && !slotTag.contains("components")) {
int count = slotTag.getIntOr("count", 0);
int take = Math.min(remaining, count);
int left = count - take;
remaining -= take;
if (left <= 0) {
inventory.remove(i);
} else {
slotTag.putInt("count", left);
}
}
}
NbtIo.writeCompressed(root, playerFile);
return true;
} catch (Exception e) {
DolgMod.LOGGER.error("Failed to process offline player inventory for " + uuid, e);
return false;
}
}
}