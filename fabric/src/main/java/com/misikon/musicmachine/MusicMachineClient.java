package com.misikon.musicmachine;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class MusicMachineClient implements ClientModInitializer {
    public static final KeyBinding OPEN_CONFIG = new KeyBinding(
            "key.musicmachine.open_config",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_M,
            "key.categories.misc"
    );

    private static boolean tracksLoaded = false;

    @Override
    public void onInitializeClient() {
        KeyBindingHelper.registerKeyBinding(OPEN_CONFIG);

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (!tracksLoaded && mc.getResourceManager() != null) {
                TrackLoader.load(mc.getResourceManager());  // 1st: register all tracks
                ConfigSaver.load();                          // 2nd: load saved settings
                tracksLoaded = true;
            }

            if (OPEN_CONFIG.wasPressed() && mc.currentScreen == null) {
                mc.setScreen(new MusicConfigScreen(null));
            }
        });
    }
}
