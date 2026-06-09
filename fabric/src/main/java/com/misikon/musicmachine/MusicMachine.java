package com.misikon.musicmachine;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;

public class MusicMachine implements ModInitializer {
    public static final String MODID = "musicmachine";
    public static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        LOGGER.info("Music Machine mod loaded!");
    }
}
