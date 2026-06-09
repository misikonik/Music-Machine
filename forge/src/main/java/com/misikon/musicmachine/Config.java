package com.misikon.musicmachine;

import java.util.*;

/**
 * Central configuration store for all music track states.
 * Holds per-track enabled/disabled toggles, priority weights (1-10),
 * and a global music frequency setting.
 * Tracks are registered by TrackLoader from sounds.json parsing.
 */
public class Config {

    /** Set to true when the user saves from the GUI — signals the mixin to re-evaluate. */
    public static boolean settingsChanged = false;

    // --- Per-track enabled state (true = will play, false = blocked) ---
    private static final Map<String, Boolean> trackEnabled = new LinkedHashMap<>();

    // --- Per-track priority weight (1 = rare, 10 = very frequent). Default: 5 ---
    private static final Map<String, Integer> trackWeights = new LinkedHashMap<>();

    /** Default weight for newly discovered tracks. */
    public static final int DEFAULT_WEIGHT = 5;
    public static final int MIN_WEIGHT = 1;
    public static final int MAX_WEIGHT = 10;

    // --- Tracker for the currently playing track ---
    private static String nowPlaying = null;

    public static String getNowPlaying() {
        return nowPlaying;
    }

    public static void setNowPlaying(String trackPath) {
        nowPlaying = trackPath;
    }

    // ----------------------------------------------------------------
    // Music frequency — controls the delay between songs
    // Values are in ticks (20 ticks = 1 second)
    // ----------------------------------------------------------------

    /**
     * How frequently music plays between tracks.
     *   NEVER     — music is completely suppressed
     *   SOMETIMES — vanilla-like delays (10–20 minutes between songs)
     *   OFTEN     — shorter gaps (1–3 minutes between songs)
     *   NONSTOP   — near-zero gap (1–5 seconds, essentially continuous)
     */
    public enum MusicFrequency {
        NEVER    (Integer.MAX_VALUE, Integer.MAX_VALUE),
        SOMETIMES(12000,            24000),   // 10–20 min (vanilla default)
        OFTEN    (1200,             3600),    // 1–3 min
        NONSTOP  (20,               100);     // 1–5 sec

        public final int minDelay;
        public final int maxDelay;

        MusicFrequency(int minDelay, int maxDelay) {
            this.minDelay = minDelay;
            this.maxDelay = maxDelay;
        }

        /** Cycle to the next frequency in order. */
        public MusicFrequency next() {
            MusicFrequency[] vals = values();
            return vals[(this.ordinal() + 1) % vals.length];
        }

        /** User-facing label. */
        public String getLabel() {
            return switch (this) {
                case NEVER     -> "Never";
                case SOMETIMES -> "Sometimes";
                case OFTEN     -> "Often";
                case NONSTOP   -> "Nonstop";
            };
        }
    }

    private static MusicFrequency musicFrequency = MusicFrequency.SOMETIMES;

    public static MusicFrequency getMusicFrequency() {
        return musicFrequency;
    }

    public static void setMusicFrequency(MusicFrequency freq) {
        musicFrequency = freq;
    }

    // ----------------------------------------------------------------
    // Music Profile — dictates global track toggles
    // ----------------------------------------------------------------

    public enum MusicProfile {
        DEFAULT, // Plays everything as vanilla
        LEGACY,  // Plays only C418 tracks
        CUSTOM;  // Uses manual toggles

        public MusicProfile next() {
            MusicProfile[] vals = values();
            return vals[(this.ordinal() + 1) % vals.length];
        }

        public String getLabel() {
            return switch (this) {
                case DEFAULT -> "Default";
                case LEGACY  -> "Legacy";
                case CUSTOM  -> "Custom";
            };
        }
    }

    private static MusicProfile musicProfile = MusicProfile.CUSTOM;

    public static MusicProfile getMusicProfile() {
        return musicProfile;
    }

    public static void setMusicProfile(MusicProfile profile) {
        musicProfile = profile;
    }

    // ----------------------------------------------------------------
    // Canonical category order used by the GUI tab bar
    // ----------------------------------------------------------------
    private static final List<String> CATEGORY_ORDER = List.of(
            "Menu", "Creative", "Overworld", "Nether", "The End"
    );

    public static List<String> getOrderedCategories() {
        return CATEGORY_ORDER;
    }

    // ----------------------------------------------------------------
    // Individual track display info
    // Key is the file path like "music/game/sweden"
    // Value is { displayName, author }
    // ----------------------------------------------------------------
    private static final Map<String, String[]> TRACK_INFO = new HashMap<>();
    static {
        // Menu
        TRACK_INFO.put("music/menu/menu3",           new String[]{"Beginning 2",        "C418"});
        TRACK_INFO.put("music/menu/menu4",           new String[]{"Floating Trees",     "C418"});
        TRACK_INFO.put("music/menu/menu2",           new String[]{"Moog City 2",        "C418"});
        TRACK_INFO.put("music/menu/menu1",           new String[]{"Mutation",           "C418"});

        // Creative
        TRACK_INFO.put("music/game/creative/creative1",         new String[]{"Aria Math",          "C418"});
        TRACK_INFO.put("music/game/creative/creative2",        new String[]{"Biome Fest",         "C418"});
        TRACK_INFO.put("music/game/creative/creative3",       new String[]{"Blind Spots",        "C418"});
        TRACK_INFO.put("music/game/creative/creative4",           new String[]{"Dreiton",            "C418"});
        TRACK_INFO.put("music/game/creative/creative5",      new String[]{"Haunt Muskie",       "C418"});
        TRACK_INFO.put("music/game/creative/creative6",           new String[]{"Taswell",            "C418"});

        // The End
        TRACK_INFO.put("music/game/end/credits",     new String[]{"Alpha",              "C418"});
        TRACK_INFO.put("music/game/end/boss",        new String[]{"Boss",               "C418"});
        TRACK_INFO.put("music/game/end/end",         new String[]{"The End",            "C418"});

        // Overworld general
        TRACK_INFO.put("music/game/a_familiar_room", new String[]{"A Familiar Room",   "Aaron Cherof"});
        TRACK_INFO.put("music/game/an_ordinary_day", new String[]{"An Ordinary Day",   "Kumi Tanioka"});
        TRACK_INFO.put("music/game/ancestry",        new String[]{"Ancestry",          "Lena Raine"});
        TRACK_INFO.put("music/game/below_and_above", new String[]{"Below and Above",   "Lena Raine"});
        TRACK_INFO.put("music/game/broken_clocks",   new String[]{"Broken Clocks",     "Lena Raine"});
        TRACK_INFO.put("music/game/bromeliad",       new String[]{"Bromeliad",         "Aaron Cherof"});
        TRACK_INFO.put("music/game/calm2",           new String[]{"Clark",             "C418"});
        TRACK_INFO.put("music/game/comforting_memories", new String[]{"Comforting Memories", "Kumi Tanioka"});
        TRACK_INFO.put("music/game/crescent_dunes",  new String[]{"Crescent Dunes",    "Aaron Cherof"});
        TRACK_INFO.put("music/game/hal4",            new String[]{"Danny",             "C418"});
        TRACK_INFO.put("music/game/piano1",          new String[]{"Dry Hands",         "C418"});
        TRACK_INFO.put("music/game/echo_in_the_wind",new String[]{"Echo in the Wind",  "Aaron Cherof"});
        TRACK_INFO.put("music/game/fireflies",       new String[]{"Fireflies",         "Lena Raine"});
        TRACK_INFO.put("music/game/floating_dream",  new String[]{"Floating Dream",    "Kumi Tanioka"});
        TRACK_INFO.put("music/game/hal3",            new String[]{"Haggstrom",         "C418"});
        TRACK_INFO.put("music/game/infinite_amethyst",new String[]{"Infinite Amethyst","Lena Raine"});
        TRACK_INFO.put("music/game/nuance1",         new String[]{"Key",               "C418"});
        TRACK_INFO.put("music/game/left_to_bloom",   new String[]{"Left to Bloom",     "Lena Raine"});
        TRACK_INFO.put("music/game/hal2",            new String[]{"Living Mice",       "C418"});
        TRACK_INFO.put("music/game/piano3",          new String[]{"Mice on Venus",     "C418"});
        TRACK_INFO.put("music/game/calm1",           new String[]{"Minecraft",         "C418"});
        TRACK_INFO.put("music/game/one_more_day",    new String[]{"One More Day",      "Lena Raine"});
        TRACK_INFO.put("music/game/nuance2",         new String[]{"Oxygène",           "C418"});
        TRACK_INFO.put("music/game/hal1",            new String[]{"Subwoofer Lullaby","C418"});
        TRACK_INFO.put("music/game/calm3",           new String[]{"Sweden",            "C418"});
        TRACK_INFO.put("music/game/wending",         new String[]{"Wending",           "Kumi Tanioka"});
        TRACK_INFO.put("music/game/piano2",          new String[]{"Wet Hands",         "C418"});

        // Swamp
        TRACK_INFO.put("music/game/swamp/aerie",         new String[]{"Aerie",         "Lena Raine"});
        TRACK_INFO.put("music/game/swamp/firebugs",      new String[]{"Firebugs",      "Lena Raine"});
        TRACK_INFO.put("music/game/swamp/labyrinthine",  new String[]{"Labyrinthine",  "Lena Raine"});

        // Underwater
        TRACK_INFO.put("music/game/water/axolotl",       new String[]{"Axolotl",       "C418"});
        TRACK_INFO.put("music/game/water/dragon_fish",   new String[]{"Dragon Fish",   "C418"});
        TRACK_INFO.put("music/game/water/shuniji",       new String[]{"Shuniji",       "C418"});

        // Nether
        TRACK_INFO.put("music/game/nether/nether4",      new String[]{"Ballad of the Cats", "C418"});
        TRACK_INFO.put("music/game/nether/nether1",           new String[]{"Concrete Halls",    "C418"});
        TRACK_INFO.put("music/game/nether/nether2",               new String[]{"Dead Voxel",        "C418"});
        TRACK_INFO.put("music/game/nether/nether3",                   new String[]{"Warmth",            "C418"});
        TRACK_INFO.put("music/game/nether/soulsand_valley/so_below", new String[]{"So Below",          "Lena Raine"});
        TRACK_INFO.put("music/game/nether/crimson_forest/chrysopoeia",new String[]{"Chrysopoeia",      "Lena Raine"});
        TRACK_INFO.put("music/game/nether/nether_wastes/rubedo",     new String[]{"Rubedo",            "Lena Raine"});
    }

    // ----------------------------------------------------------------
    // Display name / author lookups
    // ----------------------------------------------------------------

    public static String getDisplayName(String trackPath) {
        String[] info = TRACK_INFO.get(trackPath);
        if (info != null) return info[0];
        // Auto-generate from filename for tracks not in the static map
        String[] parts = trackPath.split("/");
        String filename = parts[parts.length - 1];
        return toTitleCase(filename.replace("_", " "));
    }

    public static String getAuthor(String trackPath) {
        String[] info = TRACK_INFO.get(trackPath);
        if (info != null) return info[1];
        return "Unknown";
    }

    private static String toTitleCase(String input) {
        String[] words = input.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                sb.append(word.substring(1).toLowerCase());
                sb.append(" ");
            }
        }
        return sb.toString().trim();
    }

    // ----------------------------------------------------------------
    // Category detection from file path
    // Order of checks matters — more specific paths first!
    // ----------------------------------------------------------------

    public static String getCategory(String trackPath) {
        if (trackPath.contains("/menu/"))           return "Menu";
        if (trackPath.contains("/creative/"))       return "Creative";
        if (trackPath.contains("/nether/"))         return "Nether";
        if (trackPath.contains("/end/"))            return "The End";
        return "Overworld"; // Includes Swamp and Underwater fallback
    }

    public static String getSubcategory(String trackPath) {
        if (trackPath.contains("/water/")) return "Underwater";
        if (trackPath.contains("/swamp/")) return "Swamp";
        if (trackPath.equals("music/game/crescent_dunes")) return "Desert & Badlands";
        if (trackPath.equals("music/game/bromeliad")) return "Forests & Jungles";
        if (trackPath.equals("music/game/ancestry")) return "Deep Dark";
        if (trackPath.equals("music/game/an_ordinary_day")) return "Lush Caves & Snowy Slopes";
        if (trackPath.equals("music/game/floating_dream")) return "Jagged Peaks";
        if (trackPath.equals("music/game/comforting_memories")) return "Grove & Taiga";
        if (trackPath.equals("music/game/infinite_amethyst")) return "Grove & Dripstone Caves";
        if (trackPath.equals("music/game/left_to_bloom")) return "Meadow";
        if (trackPath.equals("music/game/one_more_day")) return "Snowy Slopes";
        if (trackPath.equals("music/game/stand_tall")) return "Stony & Jagged Peaks";
        if (trackPath.equals("music/game/wending")) return "Grove & Peaks";
        if (trackPath.equals("music/game/a_familiar_room")) return "Generic Overworld";
        if (trackPath.equals("music/game/echo_in_the_wind")) return "Flower Forest";

        if (getAuthor(trackPath).contains("C418")) return "General Overworld (C418)";
        
        return "General Overworld";
    }

    public static boolean doesTrackBelongToCategory(String trackPath, String category) {
        boolean matchedAnyEvent = false;
        boolean foundInRequestedCategory = false;

        for (Map.Entry<String, List<String>> entry : TrackLoader.eventTracks.entrySet()) {
            String event = entry.getKey();
            if (!entry.getValue().contains(trackPath)) continue;

            matchedAnyEvent = true;

            if (category.equals("Menu") && event.equals("music.menu")) foundInRequestedCategory = true;
            else if (category.equals("Creative") && event.equals("music.creative")) foundInRequestedCategory = true;
            else if (category.equals("Nether") && event.contains("nether")) foundInRequestedCategory = true;
            else if (category.equals("The End") && (event.startsWith("music.end") || event.startsWith("music.dragon") || event.equals("music.boss"))) foundInRequestedCategory = true;
            else if (category.equals("Overworld") && (event.equals("music.game") || event.startsWith("music.overworld.") || event.contains("swamp") || event.contains("under_water") || event.contains("water"))) {
                foundInRequestedCategory = true;
            }
        }

        if (matchedAnyEvent) {
            return foundInRequestedCategory;
        }

        // Fallback for tracks that don't belong to any known event
        return getCategory(trackPath).equals(category);
    }

    // ----------------------------------------------------------------
    // Track registration (called by TrackLoader)
    // ----------------------------------------------------------------

    /** Register an individual track file path. Sets defaults if not already known. */
    public static void registerIndividualTrack(String trackPath) {
        trackEnabled.putIfAbsent(trackPath, true);
        trackWeights.putIfAbsent(trackPath, DEFAULT_WEIGHT);
    }

    // ----------------------------------------------------------------
    // Enabled state accessors
    // ----------------------------------------------------------------

    public static boolean isTrackEnabled(String trackPath) {
        if (musicProfile == MusicProfile.DEFAULT) {
            return true;
        } else if (musicProfile == MusicProfile.LEGACY) {
            return getAuthor(trackPath).contains("C418");
        }
        return trackEnabled.getOrDefault(trackPath, true);
    }

    public static void setTrackEnabled(String trackPath, boolean enabled) {
        trackEnabled.put(trackPath, enabled);
    }

    // ----------------------------------------------------------------
    // Weight accessors (1-10 scale)
    // ----------------------------------------------------------------

    public static int getTrackWeight(String trackPath) {
        return trackWeights.getOrDefault(trackPath, DEFAULT_WEIGHT);
    }

    public static void setTrackWeight(String trackPath, int weight) {
        trackWeights.put(trackPath, Math.max(MIN_WEIGHT, Math.min(MAX_WEIGHT, weight)));
    }

    // ----------------------------------------------------------------
    // Track set accessor
    // ----------------------------------------------------------------

    /** Returns all registered individual track paths. */
    public static Set<String> getIndividualTracks() {
        return trackEnabled.keySet();
    }

    /**
     * Check if music playback is completely disabled (frequency = NEVER).
     */
    public static boolean isMusicDisabled() {
        return musicFrequency == MusicFrequency.NEVER;
    }
}
