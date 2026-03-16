package com.commandpatcher;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommandPatcher implements ModInitializer {
    public static final String MOD_ID = "commandpatcher";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[CommandPatcher] Mod initialized.");
    }
}
