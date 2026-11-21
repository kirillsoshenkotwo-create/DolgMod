# DolgMod

A lightweight Minecraft Fabric mod (MC 26.1 - 26.3) that adds a real-time item loan and debt system for **Singleplayer** and **Dedicated Servers**.

## Features
- **Real-Time Tracking:** Loans tick in real time and persist across server restarts.
- **BossBar Countdown:** Live progress bar at the top of the screen (turns red in the final 30 seconds). Can be toggled with `/dolg_hud`.
- **Configurable Economy:** Set interest rates (`%`), cooldowns, item/duration limits, and banned items in `config/dolg_settings.json`.
- **Offline Enforcement (Dedicated Servers):** Deducts undamaged items from offline player data (`.dat`) or applies a 24-hour ban upon default.
- **Exploit Protection:** Blocks unobtainable items, custom NBT components, damaged tools/armor, and unstackable inventory traps.

## Commands
- `/dolg <item> <count> <duration>` — Borrow an item (`30s`, `10m`, `2h`, `1d`).
- `/dolg_pay` — Repay your active debt early.
- `/dolg_info` — Check remaining debt time or cooldown.
- `/dolg_hud` — Hide or show the BossBar timer.
- `/dolg_clear <player>` — *(OP / Cheats)* Forgive a player's debt and reset cooldown.
- `/dolg_reload` — *(OP / Cheats)* Reload `config/dolg_settings.json`.

## Download
Pre-compiled `.jar` files are available in the [Releases](../../releases) tab.
