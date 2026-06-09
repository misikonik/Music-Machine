package com.misikon.musicmachine;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStreamReader;
import java.util.*;

public class TrackLoader {
    public static final Map<String, List<String>> eventTracks = new LinkedHashMap<>();

    public static void load(ResourceManager resourceManager) {
        eventTracks.clear();
        MusicMachine.LOGGER.info("TrackLoader starting...");
        try {
            ResourceLocation soundsLoc = new ResourceLocation("minecraft", "sounds.json");
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
                            if (name.contains(":")) {
                                name = name.split(":")[1];
                            }
                            tracks.add(name);
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
