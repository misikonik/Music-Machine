package com.misikon.musicmachine;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = MusicMachine.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class MusicMachineClient {
    public static final KeyMapping OPEN_CONFIG = new KeyMapping(
            "key.musicmachine.open_config",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            "key.categories.misc"
    );

    private static boolean tracksLoaded = false;

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_CONFIG);
        MinecraftForge.EVENT_BUS.addListener(MusicMachineClient::onClientTick);
    }

    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
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
