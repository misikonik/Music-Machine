package com.misikon.musicmachine;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

@Mod(value = MusicMachine.MODID, dist = Dist.CLIENT)
public class MusicMachineClient {
    public static final KeyMapping OPEN_CONFIG = new KeyMapping(
            "key.musicmachine.open_config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            KeyMapping.Category.MISC
    );

    private boolean tracksLoaded = false;

    public MusicMachineClient(IEventBus modEventBus) {
        modEventBus.addListener(this::registerKeys);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
    }

    private void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CONFIG);
    }

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        if (!tracksLoaded && mc.getResourceManager() != null) {
            TrackLoader.load(mc.getResourceManager());  // 1st: register all tracks
            ConfigSaver.load();                          // 2nd: load saved settings
            tracksLoaded = true;
        }

        if (OPEN_CONFIG.consumeClick() && mc.screen == null) {
            mc.setScreen(new MusicConfigScreen(null));
        }
    }
}