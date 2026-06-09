package com.misikon.musicmachine.mixin;

import com.misikon.musicmachine.Config;
import com.misikon.musicmachine.MusicMachine;
import com.misikon.musicmachine.TrackLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Mixin into the vanilla MusicManager to:
 *   1) Block disabled tracks from playing.
 *   2) Replace vanilla random selection with our weighted-random algorithm.
 *
 * WEIGHTED SELECTION ALGORITHM (Option B — all logic in this mixin):
 * When Minecraft wants to play a Music event (e.g. "music.game"):
 *   - Look up all individual tracks for that event via TrackLoader.
 *   - Filter out tracks the user has disabled.
 *   - If no tracks remain, cancel playback entirely.
 *   - Otherwise, perform a weighted random pick using each track's
 *     Config weight (1-10). Higher weight = proportionally more likely.
 *   - Create a SimpleSoundInstance for the chosen track and play it
 *     directly through the SoundManager, cancelling the vanilla call.
 *
 * Example: tracks A(w=3), B(w=7), C(w=5) → total=15
 *   Roll r in [0,15): r<3→A, r<10→B, r<15→C
 *   Track B is 7/15 ≈ 47% likely, A is 20%, C is 33%.
 */
@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {

    @Shadow private SoundInstance currentMusic;
    @Shadow private int nextSongDelay;
    @Shadow public abstract void stopPlaying();

    /** True when no enabled tracks are available — prevents the tick loop from retrying. */
    @Unique
    private boolean musicmachine_suppressMusic = false;

    /** Our own Random instance for weighted selection. */
    @Unique
    private static final Random musicmachine_random = new Random();

    // ----------------------------------------------------------------
    // HEAD of tick(): reset suppress flag when settings change
    // ----------------------------------------------------------------
    @Inject(method = "stopPlaying", at = @At("HEAD"))
    private void musicmachine_onStopPlaying(CallbackInfo ci) {
        // Vanilla handles stopping the SoundEngine, we just reset our custom flags
        // We do NOT suppress music here, because this might just be a normal track end
        Config.setNowPlaying(null);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void musicmachine_onTickHead(CallbackInfo ci) {
        if (Config.settingsChanged) {
            musicmachine_suppressMusic = false;
            Config.settingsChanged = false;
            // Force an immediate evaluation so new settings take effect
            this.nextSongDelay = 20;
        }
        // While suppressed, keep pushing the delay so vanilla doesn't try to start a new track
        if (musicmachine_suppressMusic) {
            nextSongDelay = 1200; // ~60 seconds
        }
    }

    // ----------------------------------------------------------------
    // TAIL of tick(): catch any track that vanilla started but we've disabled
    // This is a safety net — normally startPlaying is intercepted first.
    // ----------------------------------------------------------------
    @Inject(method = "tick", at = @At("TAIL"))
    private void musicmachine_onTickTail(CallbackInfo ci) {
        if (currentMusic == null) return;

        net.minecraft.client.resources.sounds.Sound sound = currentMusic.getSound();
        if (sound == null) return;

        String trackPath = sound.getLocation().getPath();
        if (!Config.isTrackEnabled(trackPath)) {
            MusicMachine.LOGGER.debug("Stopping disabled track: {}", trackPath);
            stopPlaying();
            musicmachine_suppressMusic = true;
        } else if (Config.isMusicDisabled()) {
            MusicMachine.LOGGER.debug("Stopping track because music is completely disabled");
            stopPlaying();
            musicmachine_suppressMusic = true;
        } else {
            musicmachine_suppressMusic = false;
        }
    }

    // ----------------------------------------------------------------
    // startPlaying: intercept to perform weighted random track selection
    // ----------------------------------------------------------------
    @Inject(method = "startPlaying", at = @At("HEAD"), cancellable = true)
    private void musicmachine_onStartPlaying(Music music, CallbackInfo ci) {
        // Get the sound event path (e.g. "music.game", "music.menu")
        String eventPath = music.getEvent().value().getLocation().getPath();
        List<String> allTracks = TrackLoader.eventTracks.get(eventPath);

        // If we don't know this event's individual tracks, let vanilla handle it
        if (allTracks == null || allTracks.isEmpty()) return;

        // Filter tracks based on profile and config state
        Config.MusicProfile profile = Config.getMusicProfile();
        List<String> enabledTracks;
        if (profile == Config.MusicProfile.DEFAULT) {
            enabledTracks = new java.util.ArrayList<>(allTracks);
        } else if (profile == Config.MusicProfile.LEGACY) {
            enabledTracks = allTracks.stream()
                    .filter(t -> Config.getAuthor(t).contains("C418"))
                    .collect(Collectors.toList());
        } else {
            enabledTracks = allTracks.stream()
                    .filter(Config::isTrackEnabled)
                    .collect(Collectors.toList());
        }

        if (Config.isMusicDisabled() || enabledTracks.isEmpty()) {
            // All tracks in this event are disabled or global music is disabled — suppress playback
            MusicMachine.LOGGER.debug("Music playback suppressed. (Event: {}, Global Disabled: {})", eventPath, Config.isMusicDisabled());
            musicmachine_suppressMusic = true;
            ci.cancel();
            return;
        }

        // --- Weighted random selection ---
        // Build cumulative weight array from each track's Config weight (1-10)
        int totalWeight = 0;
        int[] cumulativeWeights = new int[enabledTracks.size()];
        for (int i = 0; i < enabledTracks.size(); i++) {
            totalWeight += Config.getTrackWeight(enabledTracks.get(i));
            cumulativeWeights[i] = totalWeight;
        }

        // Roll a random number in [0, totalWeight)
        int roll = musicmachine_random.nextInt(totalWeight);

        // Find which track the roll lands on
        String chosenTrack = enabledTracks.get(enabledTracks.size() - 1); // fallback
        for (int i = 0; i < cumulativeWeights.length; i++) {
            if (roll < cumulativeWeights[i]) {
                chosenTrack = enabledTracks.get(i);
                break;
            }
        }

        MusicMachine.LOGGER.debug("Weighted selection for {}: chose '{}' (roll={}/{})",
                eventPath, chosenTrack, roll, totalWeight);

        // Create a SimpleSoundInstance for the chosen track and play it
        // The track path is relative to assets/minecraft/sounds/ (e.g. "music/game/sweden")
        ResourceLocation trackLocation = new ResourceLocation("minecraft", chosenTrack);
        SoundManager soundManager = Minecraft.getInstance().getSoundManager();

        // Look up the WeighedSoundEvents for this specific sound file
        // We create a SimpleSoundInstance that plays the chosen track as music
        // We MUST override resolve() because individual tracks aren't registered as standalone events.
        // We MUST use the original event ResourceLocation (e.g. minecraft:music.game) for the instance itself
        // so that vanilla's canReplace logic doesn't think the music changed and stop it immediately!
        ResourceLocation eventId = music.getEvent().value().getLocation();
        SimpleSoundInstance instance = new SimpleSoundInstance(
                eventId,                // sound location MUST match the music event
                SoundSource.MUSIC,      // category
                1.0F,                   // volume
                1.0F,                   // pitch
                SoundInstance.createUnseededRandom(), // random source
                false,                  // looping
                0,                      // delay
                SoundInstance.Attenuation.NONE, // no distance attenuation for music
                0.0, 0.0, 0.0,         // position (irrelevant for music)
                true                    // relative to listener
        ) {
            @Override
            public net.minecraft.client.sounds.WeighedSoundEvents resolve(SoundManager manager) {
                // Return a dummy WeighedSoundEvents so the engine accepts it
                net.minecraft.client.sounds.WeighedSoundEvents dummyEvent = 
                        new net.minecraft.client.sounds.WeighedSoundEvents(this.getLocation(), null);
                
                // Directly construct the Sound object for the actual .ogg file path
                this.sound = new net.minecraft.client.resources.sounds.Sound(
                        trackLocation.toString(), // the actual file path, not the event ID!
                        net.minecraft.util.valueproviders.ConstantFloat.of(1.0F),
                        net.minecraft.util.valueproviders.ConstantFloat.of(1.0F),
                        1,
                        net.minecraft.client.resources.sounds.Sound.Type.FILE,
                        true,  // stream for music
                        false, // preload
                        16     // attenuationDistance
                );
                
                return dummyEvent;
            }
        };

        // Stop any currently playing music first
        stopPlaying();

        // Play our chosen track
        soundManager.play(instance);

        // Set the currentMusic field so the MusicManager knows something is playing
        this.currentMusic = instance;

        // Apply delay until next song
        Config.MusicFrequency freq = Config.getMusicFrequency();
        int minDelay = freq == Config.MusicFrequency.SOMETIMES ? music.getMinDelay() : freq.minDelay;
        int maxDelay = freq == Config.MusicFrequency.SOMETIMES ? music.getMaxDelay() : freq.maxDelay;
        
        if (minDelay <= 0) minDelay = 1;
        if (maxDelay < minDelay) maxDelay = minDelay;
        
        this.nextSongDelay = minDelay >= maxDelay ? minDelay : musicmachine_random.nextInt(maxDelay - minDelay + 1) + minDelay;

        musicmachine_suppressMusic = false;
        Config.setNowPlaying(chosenTrack);

        // Cancel the vanilla startPlaying — we've handled it ourselves
        ci.cancel();
    }
}
