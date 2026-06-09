package com.misikon.musicmachine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStreamReader;
import java.util.*;

/**
 * Parses vanilla sounds.json to discover which individual .ogg track files
 * belong to each music.* sound event.
 *
 * Result: eventTracks maps e.g. "music.game" → ["music/game/sweden", "music/game/clark", ...]
 *
 * This runs client-side once during the first client tick when the
 * resource manager is available.
 */
public class TrackLoader {
    /** Map of sound event name → list of individual track file paths */
    public static final Map<String, List<String>> eventTracks = new LinkedHashMap<>();

    public static void load(ResourceManager resourceManager) {
        eventTracks.clear();
        MusicMachine.LOGGER.info("TrackLoader starting...");
        try {
            // In 1.21.11, ResourceLocation may have been renamed to Identifier in some mappings.
            // With parchment/mojmap on NeoForge, ResourceLocation should still work.
            Identifier soundsLoc = Identifier.withDefaultNamespace("sounds.json");
            List<Resource> resources = resourceManager.getResourceStack(soundsLoc);
            MusicMachine.LOGGER.info("Found {} sounds.json files", resources.size());

            for (Resource resource : resources) {
                try (InputStreamReader reader = new InputStreamReader(resource.open())) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

                    for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                        String eventName = entry.getKey();
                        if (!eventName.startsWith("music.")) continue;

                        JsonObject eventObj = entry.getValue().getAsJsonObject();
                        JsonArray sounds = eventObj.getAsJsonArray("sounds");
                        if (sounds == null) continue;

                        List<String> tracks = new ArrayList<>();
                        for (JsonElement sound : sounds) {
                            String name;
                            if (sound.isJsonObject()) {
                                JsonObject obj = sound.getAsJsonObject();
                                if (obj.has("type") && obj.get("type").getAsString().equals("event")) {
                                    continue;
                                }
                                name = obj.get("name").getAsString();
                            } else {
                                name = sound.getAsString();
                            }
                            // Strip namespace prefix if present (e.g. "minecraft:music/game/sweden")
                            if (name.contains(":")) {
                                name = name.split(":")[1];
                            }
                            tracks.add(name);
                            // Register with Config (sets default enabled=true, weight=5)
                            Config.registerIndividualTrack(name);
                        }
                        if (!tracks.isEmpty()) {
                            eventTracks.put(eventName, tracks);
                        }
                    }
                }
            }
            MusicMachine.LOGGER.info("TrackLoader done! Found {} music events with individual tracks", eventTracks.size());
        } catch (Exception e) {
            MusicMachine.LOGGER.error("TrackLoader failed: {}", e.getMessage());
            e.printStackTrace();
        }
    }
}