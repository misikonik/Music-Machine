package com.misikon.musicmachine;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.ConfigScreenHandler;
import org.slf4j.Logger;

@Mod(MusicMachine.MODID)
public class MusicMachine {
    public static final String MODID = "musicmachine";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MusicMachine() {
        LOGGER.info("Music Machine mod loaded!");
        
        // Register the config screen so it appears in the Forge Mod List
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class, () -> new ConfigScreenHandler.ConfigScreenFactory((client, parentScreen) -> new MusicConfigScreen(parentScreen)));
    }
}
