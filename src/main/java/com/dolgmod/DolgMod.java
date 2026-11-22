package com.dolgmod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
// Удалён лишний импорт LoggerFactory

public class DolgMod implements ModInitializer {
    public static final String MOD_ID = "dolgmod";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        DolgConfig.load();
        DolgData.load();
        DolgServerHandler.init();
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> DolgCommands.register(dispatcher));
    }
}
