package com.dolgmod;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class DolgPlugin extends JavaPlugin {
    private static DolgPlugin instance;

    public static DolgPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        DolgConfig.load(getDataFolder());
        DolgData.load(getDataFolder());
        DolgServerHandler.init(this);

        DolgCommands executor = new DolgCommands();
        String[] cmds = {"dolg", "dolg_pay", "dolg_info", "dolg_hud", "dolg_clear", "dolg_reload"};
        for (String cmd : cmds) {
            PluginCommand pc = getCommand(cmd);
            if (pc != null) {
                pc.setExecutor(executor);
                pc.setTabCompleter(executor);
            }
        }
        getLogger().info("DolgMod for Spigot 1.12.2 enabled!");
    }

    @Override
    public void onDisable() {
        DolgServerHandler.clearAllBossBars();
        DolgData.save();
        DolgData.saveCooldowns();
    }
}
