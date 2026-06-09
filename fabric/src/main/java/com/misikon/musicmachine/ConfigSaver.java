package com.misikon.musicmachine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;

import java.io.*;
import java.nio.file.Path;

/**
 * Persists track settings (enabled + weight) to a JSON file in the config directory.
 *
 * File format:
 * {
 *   "tracks": {
 *     "music/game/sweden": { "enabled": true, "weight": 7 },
 *     "music/game/clark":  { "enabled": false, "weight": 3 }
 *   }
 * }
 */
public class ConfigSaver {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static Path getConfigFile() {
        return MinecraftClient.getInstance().runDirectory.toPath()
                .resolve("config")
                .resolve("musicmachine.json");
    }

    /**
     * Save all track settings to disk.
     */
    public static void save() {
        try {
            Path configFile = getConfigFile();
            configFile.getParent().toFile().mkdirs();
            MusicMachine.LOGGER.info("Saving config to: {}", configFile.toAbsolutePath());

            JsonObject root = new JsonObject();
            root.addProperty("musicFrequency", Config.getMusicFrequency().name());
            root.addProperty("musicProfile", Config.getMusicProfile().name());

            JsonObject tracks = new JsonObject();

            for (String track : Config.getIndividualTracks()) {
                JsonObject entry = new JsonObject();
                entry.addProperty("enabled", Config.isTrackEnabled(track));
                entry.addProperty("weight", Config.getTrackWeight(track));
                tracks.add(track, entry);
            }
            root.add("tracks", tracks);

            MusicMachine.LOGGER.info("Saving {} tracks", tracks.size());

            try (Writer writer = new FileWriter(configFile.toFile())) {
                GSON.toJson(root, writer);
            }
            MusicMachine.LOGGER.info("Config saved successfully!");
        } catch (Exception e) {
            MusicMachine.LOGGER.error("Failed to save config: {}", e.getMessage());
        }
    }

    /**
     * Load track settings from disk. Falls back to defaults for missing entries.
     */
    public static void load() {
        try {
            Path configFile = getConfigFile();
            MusicMachine.LOGGER.info("Loading config from: {}", configFile.toAbsolutePath());

            if (!configFile.toFile().exists()) {
                MusicMachine.LOGGER.info("No config file found, using defaults");
                return;
            }

            try (Reader reader = new FileReader(configFile.toFile())) {
                JsonObject root = GSON.fromJson(reader, JsonObject.class);

                // Load global frequency if present
                if (root.has("musicFrequency")) {
                    try {
                        Config.setMusicFrequency(Config.MusicFrequency.valueOf(root.get("musicFrequency").getAsString()));
                    } catch (Exception ignored) {}
                }

                // Load music profile if present
                if (root.has("musicProfile")) {
                    try {
                        Config.setMusicProfile(Config.MusicProfile.valueOf(root.get("musicProfile").getAsString()));
                    } catch (Exception ignored) {}
                }

                // Support both the new nested format and the old flat format for migration
                if (root.has("tracks") && root.get("tracks").isJsonObject()) {
                    // New format: { "tracks": { "path": { "enabled": bool, "weight": int } } }
                    JsonObject tracks = root.getAsJsonObject("tracks");
                    for (var entry : tracks.entrySet()) {
                        String trackPath = entry.getKey();
                        JsonElement value = entry.getValue();
                        if (value.isJsonObject()) {
                            JsonObject trackObj = value.getAsJsonObject();
                            if (trackObj.has("enabled")) {
                                Config.setTrackEnabled(trackPath, trackObj.get("enabled").getAsBoolean());
                            }
                            if (trackObj.has("weight")) {
                                Config.setTrackWeight(trackPath, trackObj.get("weight").getAsInt());
                            }
                        }
                    }
                } else {
                    // Legacy flat format: { "path": true/false }
                    // Migrate from the old format gracefully
                    for (var entry : root.entrySet()) {
                        String trackPath = entry.getKey();
                        if (trackPath.equals("tracks")) continue; // skip if malformed
                        try {
                            Config.setTrackEnabled(trackPath, entry.getValue().getAsBoolean());
                        } catch (Exception ignored) {
                            // Skip malformed entries
                        }
                    }
                }

                MusicMachine.LOGGER.info("Config loaded successfully!");
            }
        } catch (Exception e) {
            MusicMachine.LOGGER.error("Failed to load config: {}", e.getMessage());
        }
    }
}
