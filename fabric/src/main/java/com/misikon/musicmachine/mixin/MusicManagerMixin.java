package com.misikon.musicmachine.mixin;

import com.misikon.musicmachine.Config;
import com.misikon.musicmachine.MusicMachine;
import com.misikon.musicmachine.TrackLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.MusicTracker;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.client.sound.WeightedSoundSet;
import net.minecraft.util.Identifier;
import net.minecraft.sound.MusicSound;
import net.minecraft.sound.SoundCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Mixin(MusicTracker.class)
public abstract class MusicManagerMixin {

    @Shadow private SoundInstance current;
    @Shadow private int timeUntilNextSong;
    @Shadow public abstract void stop();

    @Unique
    private boolean musicmachine_suppressMusic = false;

    @Unique
    private static final Random musicmachine_random = new Random();

    @Inject(method = "stop", at = @At("HEAD"))
    private void musicmachine_onStopPlaying(CallbackInfo ci) {
        Config.setNowPlaying(null);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void musicmachine_onTickHead(CallbackInfo ci) {
        if (Config.settingsChanged) {
            musicmachine_suppressMusic = false;
            Config.settingsChanged = false;
            this.timeUntilNextSong = 20;
        }
        if (musicmachine_suppressMusic) {
            timeUntilNextSong = 1200;
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void musicmachine_onTickTail(CallbackInfo ci) {
        if (current == null) return;

        net.minecraft.client.sound.Sound sound = current.getSound();
        if (sound == null) return;

        String trackPath = sound.getIdentifier().getPath();
        if (!Config.isTrackEnabled(trackPath)) {
            MusicMachine.LOGGER.debug("Stopping disabled track: {}", trackPath);
            stop();
            musicmachine_suppressMusic = true;
        } else if (Config.isMusicDisabled()) {
            MusicMachine.LOGGER.debug("Stopping track because music is completely disabled");
            stop();
            musicmachine_suppressMusic = true;
        } else {
            musicmachine_suppressMusic = false;
        }
    }

    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void musicmachine_onStartPlaying(MusicSound music, CallbackInfo ci) {
        String eventPath = music.getSound().value().getId().getPath();
        List<String> allTracks = TrackLoader.eventTracks.get(eventPath);

        if (allTracks == null || allTracks.isEmpty()) return;

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
            MusicMachine.LOGGER.debug("Music playback suppressed. (Event: {}, Global Disabled: {})", eventPath, Config.isMusicDisabled());
            musicmachine_suppressMusic = true;
            ci.cancel();
            return;
        }

        int totalWeight = 0;
        int[] cumulativeWeights = new int[enabledTracks.size()];
        for (int i = 0; i < enabledTracks.size(); i++) {
            totalWeight += Config.getTrackWeight(enabledTracks.get(i));
            cumulativeWeights[i] = totalWeight;
        }

        int roll = musicmachine_random.nextInt(totalWeight);

        String chosenTrack = enabledTracks.get(enabledTracks.size() - 1);
        for (int i = 0; i < cumulativeWeights.length; i++) {
            if (roll < cumulativeWeights[i]) {
                chosenTrack = enabledTracks.get(i);
                break;
            }
        }

        MusicMachine.LOGGER.debug("Weighted selection for {}: chose '{}' (roll={}/{})",
                eventPath, chosenTrack, roll, totalWeight);

        Identifier trackLocation = new Identifier("minecraft", chosenTrack);
        SoundManager soundManager = MinecraftClient.getInstance().getSoundManager();

        Identifier eventId = music.getSound().value().getId();
        PositionedSoundInstance instance = new PositionedSoundInstance(
                eventId,
                SoundCategory.MUSIC,
                1.0F,
                1.0F,
                SoundInstance.createRandom(),
                false,
                0,
                SoundInstance.AttenuationType.NONE,
                0.0, 0.0, 0.0,
                true
        ) {
            @Override
            public net.minecraft.client.sound.WeightedSoundSet getSoundSet(SoundManager manager) {
                net.minecraft.client.sound.WeightedSoundSet dummyEvent = 
                        new net.minecraft.client.sound.WeightedSoundSet(this.getId(), null);
                
                this.sound = new net.minecraft.client.sound.Sound(
                        trackLocation.toString(),
                        net.minecraft.util.math.floatprovider.ConstantFloatProvider.create(1.0F),
                        net.minecraft.util.math.floatprovider.ConstantFloatProvider.create(1.0F),
                        1,
                        net.minecraft.client.sound.Sound.RegistrationType.FILE,
                        true,
                        false,
                        16
                );
                
                return dummyEvent;
            }
        };

        stop();
        soundManager.play(instance);
        this.current = instance;

        Config.MusicFrequency freq = Config.getMusicFrequency();
        int minDelay = freq == Config.MusicFrequency.SOMETIMES ? music.getMinDelay() : freq.minDelay;
        int maxDelay = freq == Config.MusicFrequency.SOMETIMES ? music.getMaxDelay() : freq.maxDelay;
        
        if (minDelay <= 0) minDelay = 1;
        if (maxDelay < minDelay) maxDelay = minDelay;
        
        this.timeUntilNextSong = minDelay >= maxDelay ? minDelay : musicmachine_random.nextInt(maxDelay - minDelay + 1) + minDelay;

        musicmachine_suppressMusic = false;
        Config.setNowPlaying(chosenTrack);

        ci.cancel();
    }
}
