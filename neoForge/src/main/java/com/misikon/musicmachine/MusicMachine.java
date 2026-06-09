package com.misikon.musicmachine;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import org.slf4j.Logger;

@Mod(MusicMachine.MODID)
public class MusicMachine {
    public static final String MODID = "musicmachine";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MusicMachine(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Music Machine mod loaded!");
        
        // Register the config screen so it appears in the NeoForge Mod List
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, (container, parentScreen) -> new MusicConfigScreen(parentScreen));
    }
}