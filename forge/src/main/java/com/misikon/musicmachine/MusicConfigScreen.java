package com.misikon.musicmachine;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Custom GUI screen for the Music Machine mod.
 *
 * Layout:
 * ┌──────────────────────────────────────────┐
 * │            Music Machine                  │  ← title
 * │ [Menu][Creative][Overworld][Swamp]...     │  ← tab bar
 * ├──────────────────────────────────────────┤
 * │  ♪ Sweden — C418         [ON ] [===5===] │  ← scrollable track list
 * │  ♪ Clark — C418          [OFF] [---3---] │    each row has toggle + slider
 * │  ♪ Danny — C418          [ON ] [===8===] │
 * │  ...                                      │
 * ├──────────────────────────────────────────┤
 * │              [Save & Close]               │  ← bottom bar
 * └──────────────────────────────────────────┘
 */
public class MusicConfigScreen extends Screen {
    private final Screen parent;

    // Layout constants
    private static final int TAB_HEIGHT = 20;
    private static final int TAB_Y = 36;
    private static final int LIST_TOP = TAB_Y + TAB_HEIGHT + 6;
    private static final int BOTTOM_BAR_HEIGHT = 30;
    private static final int ROW_HEIGHT = 26;
    private static final int LEFT_MARGIN = 10;
    private static final int RIGHT_MARGIN = 10;

    // Currently selected category tab
    private String selectedCategory;

    // Scroll offset in pixels
    private int scrollOffset = 0;

    // Working copies of settings (committed on Save)
    private final Map<String, Boolean> trackStates = new LinkedHashMap<>();
    private final Map<String, Integer> trackWeightValues = new LinkedHashMap<>();
    private Config.MusicFrequency frequencyState;
    private Config.MusicProfile profileState;

    // Filtered track list for the current category
    private List<String> filteredTracks = new ArrayList<>();

    // Tab buttons (kept for re-highlighting)
    private final List<Button> tabButtons = new ArrayList<>();

    public MusicConfigScreen(Screen parent) {
        super(Component.translatable("musicmachine.screen.title"));
        this.parent = parent;
        this.selectedCategory = Config.getOrderedCategories().get(0);
    }

    @Override
    protected void init() {
        // Load working copies from Config
        trackStates.clear();
        trackWeightValues.clear();
        for (String track : Config.getIndividualTracks()) {
            trackStates.put(track, Config.isTrackEnabled(track));
            trackWeightValues.put(track, Config.getTrackWeight(track));
        }
        frequencyState = Config.getMusicFrequency();
        profileState = Config.getMusicProfile();

        // Build tab bar
        tabButtons.clear();
        List<String> categories = Config.getOrderedCategories();
        int tabWidth = Math.min(80, (this.width - 20) / categories.size());
        int totalTabWidth = tabWidth * categories.size();
        int tabStartX = (this.width - totalTabWidth) / 2;

        for (int i = 0; i < categories.size(); i++) {
            String cat = categories.get(i);
            int x = tabStartX + i * tabWidth;
            Button tab = Button.builder(Component.literal(cat), btn -> {
                selectedCategory = cat;
                scrollOffset = 0;
                rebuildFilteredList();
                updateTabHighlights();
            }).pos(x, TAB_Y).size(tabWidth, TAB_HEIGHT).build();
            tabButtons.add(tab);
            this.addRenderableWidget(tab);
        }
        updateTabHighlights();

        // Frequency toggle button
        Button freqButton = Button.builder(
                Component.literal("Frequency: " + frequencyState.getLabel()), btn -> {
                    frequencyState = frequencyState.next();
                    btn.setMessage(Component.literal("Frequency: " + frequencyState.getLabel()));
                })
                .pos(this.width / 2 - 156, this.height - BOTTOM_BAR_HEIGHT + 4)
                .size(100, 20)
                .build();
        this.addRenderableWidget(freqButton);

        // Profile toggle button
        Button profileButton = Button.builder(
                Component.literal("Profile: " + profileState.getLabel()), btn -> {
                    profileState = profileState.next();
                    btn.setMessage(Component.literal("Profile: " + profileState.getLabel()));
                })
                .pos(this.width / 2 - 50, this.height - BOTTOM_BAR_HEIGHT + 4)
                .size(100, 20)
                .build();
        this.addRenderableWidget(profileButton);

        // Save & Close button
        this.addRenderableWidget(Button.builder(
                Component.translatable("musicmachine.button.save"), btn -> {
                    // Commit changes to Config
                    for (var entry : trackStates.entrySet()) {
                        Config.setTrackEnabled(entry.getKey(), entry.getValue());
                    }
                    for (var entry : trackWeightValues.entrySet()) {
                        Config.setTrackWeight(entry.getKey(), entry.getValue());
                    }
                    Config.setMusicFrequency(frequencyState);
                    Config.setMusicProfile(profileState);
                    Config.settingsChanged = true;
                    ConfigSaver.save();
                    // Stop current music so the new settings take effect immediately
                    this.minecraft.getMusicManager().stopPlaying();
                    this.minecraft.setScreen(parent);
                })
                .pos(this.width / 2 + 56, this.height - BOTTOM_BAR_HEIGHT + 4)
                .size(100, 20)
                .build()
        );

        // Toggle All button
        this.addRenderableWidget(Button.builder(
                Component.literal("Toggle All"), btn -> {
                    boolean anyOff = false;
                    for (String t : filteredTracks) {
                        if (!t.startsWith("header:") && !Boolean.TRUE.equals(trackStates.get(t))) {
                            anyOff = true;
                            break;
                        }
                    }
                    boolean newState = anyOff;
                    for (String t : filteredTracks) {
                        if (!t.startsWith("header:")) {
                            trackStates.put(t, newState);
                        }
                    }
                })
                .pos(LEFT_MARGIN, this.height - BOTTOM_BAR_HEIGHT + 4)
                .size(70, 20)
                .build()
        );

        rebuildFilteredList();
    }

    /** Filter tracks by the selected category, group by subcategory, and sort. */
    private void rebuildFilteredList() {
        List<String> rawTracks = trackStates.keySet().stream()
                .filter(t -> Config.doesTrackBelongToCategory(t, selectedCategory))
                .sorted((t1, t2) -> {
                    String sub1 = Config.getSubcategory(t1);
                    String sub2 = Config.getSubcategory(t2);
                    int cmp = sub1.compareTo(sub2);
                    if (cmp != 0) return cmp;
                    return Config.getDisplayName(t1).compareTo(Config.getDisplayName(t2));
                })
                .collect(Collectors.toList());

        filteredTracks.clear();
        String currentSub = null;
        for (String t : rawTracks) {
            String sub = Config.getSubcategory(t);
            if (!sub.equals(currentSub)) {
                filteredTracks.add("header:" + sub);
                currentSub = sub;
            }
            filteredTracks.add(t);
        }

        // Clamp scroll
        int maxScroll = getMaxScroll();
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
    }

    /** Visual indicator for the active tab. */
    private void updateTabHighlights() {
        List<String> cats = Config.getOrderedCategories();
        for (int i = 0; i < tabButtons.size() && i < cats.size(); i++) {
            boolean active = cats.get(i).equals(selectedCategory);
            Button btn = tabButtons.get(i);
            btn.active = !active; // Inactive = highlighted (can't re-click the current tab)
        }
    }

    private int getListBottom() {
        return this.height - BOTTOM_BAR_HEIGHT;
    }

    private int getVisibleHeight() {
        return getListBottom() - LIST_TOP;
    }

    private int getMaxScroll() {
        return Math.max(0, filteredTracks.size() * ROW_HEIGHT - getVisibleHeight());
    }

    // ----------------------------------------------------------------
    // Input handling
    // ----------------------------------------------------------------

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        int maxScroll = getMaxScroll();
        scrollOffset = (int) Mth.clamp(scrollOffset - scrollY * ROW_HEIGHT, 0, maxScroll);
        return true;
    }

    /**
     * Handle mouse clicks using the 1.21.11 MouseButtonEvent API.
     * We intercept clicks on the track list area to handle toggle/slider interactions.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {

        // Only allow interaction if the profile is CUSTOM
        if (profileState == Config.MusicProfile.CUSTOM) {
            // Only handle left-click inside the track list area
            if (button == 0 && mouseY >= LIST_TOP && mouseY < getListBottom()) {
                int listWidth = this.width - LEFT_MARGIN - RIGHT_MARGIN;
                int listX = LEFT_MARGIN;

            for (int i = 0; i < filteredTracks.size(); i++) {
                int rowY = LIST_TOP + i * ROW_HEIGHT - scrollOffset;
                if (rowY + ROW_HEIGHT < LIST_TOP || rowY > getListBottom()) continue;
                if (mouseY < rowY || mouseY >= rowY + ROW_HEIGHT) continue;

                String track = filteredTracks.get(i);
                if (track.startsWith("header:")) continue;

                // Toggle button area: right side, 36px wide
                int toggleX = listX + listWidth - 36;
                int toggleY = rowY + 3;
                int toggleW = 32;
                int toggleH = 14;

                // Weight slider area: to the left of toggle, 100px wide
                int sliderW = 100;
                int sliderX = toggleX - sliderW - 8;
                int sliderY = rowY + 3;
                int sliderH = 14;

                if (mouseX >= toggleX && mouseX <= toggleX + toggleW
                        && mouseY >= toggleY && mouseY <= toggleY + toggleH) {
                    // Toggle clicked
                    trackStates.put(track, !Boolean.TRUE.equals(trackStates.get(track)));
                    return true;
                }

                if (mouseX >= sliderX && mouseX <= sliderX + sliderW
                        && mouseY >= sliderY && mouseY <= sliderY + sliderH) {
                    // Slider clicked — set weight based on click position
                    if (Boolean.TRUE.equals(trackStates.get(track))) {
                        double ratio = (mouseX - sliderX) / (double) sliderW;
                        int weight = Config.MIN_WEIGHT + (int) Math.round(ratio * (Config.MAX_WEIGHT - Config.MIN_WEIGHT));
                        weight = Mth.clamp(weight, Config.MIN_WEIGHT, Config.MAX_WEIGHT);
                        trackWeightValues.put(track, weight);
                    }
                    return true;
                }
            }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Handle mouse drag using the 1.21.11 MouseButtonEvent API.
     * Allows dragging on weight sliders.
     */
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {

        // Only allow interaction if the profile is CUSTOM
        if (profileState == Config.MusicProfile.CUSTOM) {
            // Allow dragging on sliders
            if (button == 0 && mouseY >= LIST_TOP && mouseY < getListBottom()) {
                int listWidth = this.width - LEFT_MARGIN - RIGHT_MARGIN;
            int listX = LEFT_MARGIN;

            for (int i = 0; i < filteredTracks.size(); i++) {
                int rowY = LIST_TOP + i * ROW_HEIGHT - scrollOffset;
                if (rowY + ROW_HEIGHT < LIST_TOP || rowY > getListBottom()) continue;
                if (mouseY < rowY || mouseY >= rowY + ROW_HEIGHT) continue;

                String track = filteredTracks.get(i);
                if (track.startsWith("header:")) continue;
                if (!Boolean.TRUE.equals(trackStates.get(track))) continue;

                int toggleX = listX + listWidth - 36;
                int sliderW = 100;
                int sliderX = toggleX - sliderW - 8;

                if (mouseX >= sliderX && mouseX <= sliderX + sliderW) {
                    double ratio = (mouseX - sliderX) / (double) sliderW;
                    int weight = Config.MIN_WEIGHT + (int) Math.round(ratio * (Config.MAX_WEIGHT - Config.MIN_WEIGHT));
                    weight = Mth.clamp(weight, Config.MIN_WEIGHT, Config.MAX_WEIGHT);
                    trackWeightValues.put(track, weight);
                    return true;
                }
            }
            }
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    // ----------------------------------------------------------------
    // Rendering
    // ----------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Draw the dark dirt or gradient background
        this.renderBackground(graphics);

        // Title
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);

        // Now Playing
        String currentTrack = Config.getNowPlaying();
        String nowPlayingText = currentTrack == null ? "None" : Config.getDisplayName(currentTrack);
        String displayText = "♪ Now Playing: " + nowPlayingText + " ♪";
        
        long time = System.currentTimeMillis();
        float hue = (time % 5000L) / 5000.0f;
        int animatedColor = java.awt.Color.HSBtoRGB(hue, 0.5f, 0.9f) | 0xFF000000;
        
        graphics.drawCenteredString(this.font, displayText, this.width / 2, 22, animatedColor);

        // Enable scissoring so the track list doesn't overflow into the tab/bottom bar
        graphics.enableScissor(0, 36 + TAB_HEIGHT + 6, this.width, getListBottom());

        int listWidth = this.width - LEFT_MARGIN - RIGHT_MARGIN;
        int listX = LEFT_MARGIN;

        for (int i = 0; i < filteredTracks.size(); i++) {
            int rowY = 36 + TAB_HEIGHT + 6 + i * ROW_HEIGHT - scrollOffset;

            // Skip rows that are fully outside the visible area
            if (rowY + ROW_HEIGHT < 36 + TAB_HEIGHT + 6 || rowY > getListBottom()) continue;

            String track = filteredTracks.get(i);

            if (track.startsWith("header:")) {
                String headerName = track.substring(7);
                graphics.fill(listX, rowY + 1, listX + listWidth, rowY + ROW_HEIGHT - 1, 0xAA000000);
                graphics.drawCenteredString(this.font, headerName, listX + listWidth / 2, rowY + 8, 0xFFAAAAAA);
                continue;
            }
            
            // Determine active state based on profile
            boolean enabled;
            if (profileState == Config.MusicProfile.DEFAULT) {
                enabled = true;
            } else if (profileState == Config.MusicProfile.LEGACY) {
                enabled = Config.getAuthor(track).contains("C418");
            } else {
                enabled = Boolean.TRUE.equals(trackStates.get(track));
            }

            int weight = trackWeightValues.getOrDefault(track, Config.DEFAULT_WEIGHT);

            // Row background
            boolean hovered = mouseY >= rowY && mouseY < rowY + ROW_HEIGHT
                    && mouseX >= listX && mouseX <= listX + listWidth
                    && mouseY >= 36 + TAB_HEIGHT + 6 && mouseY < getListBottom();
            int bgColor = hovered ? 0xCC333344 : 0xAA222233;
            graphics.fill(listX, rowY + 1, listX + listWidth, rowY + ROW_HEIGHT - 1, bgColor);

            // Track name
            String displayName = Config.getDisplayName(track);
            String author = Config.getAuthor(track);
            int color = enabled ? 0xFFFFFFFF : 0xFF555555;
            if (profileState != Config.MusicProfile.CUSTOM) {
                color = enabled ? 0xFF888888 : 0xFF444444; // Gray out if not custom profile
            }
            graphics.drawString(this.font, displayName, listX + 10, rowY + 8, color, false);
            graphics.drawString(this.font, "by " + author, listX + 10 + font.width(displayName) + 8, rowY + 8, 0xFF666666, false);

            // --- Toggle button ---
            int toggleX = listX + listWidth - 36;
            int toggleY = rowY + 4;
            int toggleW = 32;
            int toggleH = 14;

            int toggleColor = enabled ? 0xFF55FF55 : 0xFFFF5555;
            if (profileState != Config.MusicProfile.CUSTOM) {
                toggleColor = enabled ? 0xFF448844 : 0xFF884444;
            }
            graphics.fill(toggleX, toggleY, toggleX + toggleW, toggleY + toggleH, 0xFF000000);
            graphics.fill(toggleX + 1, toggleY + 1, toggleX + toggleW - 1, toggleY + toggleH - 1, toggleColor);
            String toggleText = enabled ? "ON" : "OFF";
            int toggleTextWidth = this.font.width(toggleText);
            graphics.drawString(this.font, toggleText,
                    toggleX + (toggleW - toggleTextWidth) / 2,
                    toggleY + 3, 0xFFFFFFFF, true);

            // --- Weight slider ---
            int sliderW = 100;
            int sliderX = toggleX - sliderW - 8;
            int sliderY = rowY + 4;
            int sliderH = 14;

            if (enabled) {
                // Slider background (track)
                graphics.fill(sliderX, sliderY, sliderX + sliderW, sliderY + sliderH, 0xFF555566);
                graphics.fill(sliderX + 1, sliderY + 1, sliderX + sliderW - 1, sliderY + sliderH - 1, 0xFF333344);

                // Slider fill
                double ratio = (weight - Config.MIN_WEIGHT) / (double) (Config.MAX_WEIGHT - Config.MIN_WEIGHT);
                int fillWidth = (int) (ratio * (sliderW - 2));
                int fillColor = getWeightColor(weight);
                if (profileState != Config.MusicProfile.CUSTOM) {
                    fillColor = (fillColor & 0x00FFFFFF) | 0x66000000; // More transparent
                }
                graphics.fill(sliderX + 1, sliderY + 1, sliderX + 1 + fillWidth, sliderY + sliderH - 1, fillColor);

                // Slider handle (small notch)
                int handleX = sliderX + 1 + fillWidth - 1;
                if (handleX >= sliderX + 1) {
                    graphics.fill(handleX, sliderY, handleX + 3, sliderY + sliderH, 0xFFFFFFFF);
                }

                // Weight number
                String weightStr = String.valueOf(weight);
                int textColor = (profileState != Config.MusicProfile.CUSTOM) ? 0xFF888888 : 0xFFFFFFFF;
                graphics.drawString(this.font, weightStr,
                        sliderX + (sliderW - this.font.width(weightStr)) / 2,
                        sliderY + 3, textColor, true);
            } else {
                // Disabled slider appearance
                graphics.fill(sliderX, sliderY, sliderX + sliderW, sliderY + sliderH, 0xFF444444);
                graphics.fill(sliderX + 1, sliderY + 1, sliderX + sliderW - 1, sliderY + sliderH - 1, 0xFF2A2A2A);
                String disabledStr = "—";
                graphics.drawString(this.font, disabledStr,
                        sliderX + (sliderW - this.font.width(disabledStr)) / 2,
                        sliderY + 3, 0xFF666666, true);
            }
        }

        graphics.disableScissor();

        // Scrollbar indicator (right edge)
        if (getMaxScroll() > 0) {
            int scrollbarX = this.width - 4;
            int visibleH = getVisibleHeight();
            int totalH = filteredTracks.size() * ROW_HEIGHT;
            int thumbH = Math.max(10, (int) ((float) visibleH / totalH * visibleH));
            int thumbY = LIST_TOP + (int) ((float) scrollOffset / getMaxScroll() * (visibleH - thumbH));

            graphics.fill(scrollbarX, LIST_TOP, scrollbarX + 3, getListBottom(), 0x44FFFFFF);
            graphics.fill(scrollbarX, thumbY, scrollbarX + 3, thumbY + thumbH, 0xAAFFFFFF);
        }

        // Track count indicator
        long enabledCount = filteredTracks.stream()
                .filter(t -> !t.startsWith("header:") && Boolean.TRUE.equals(trackStates.get(t)))
                .count();
        long totalCount = filteredTracks.stream()
                .filter(t -> !t.startsWith("header:"))
                .count();
        String countText = enabledCount + "/" + totalCount + " enabled";
        graphics.drawString(this.font, countText,
                LEFT_MARGIN + 76, this.height - BOTTOM_BAR_HEIGHT + 10, 0xFF999999, false);

        // Render widgets (tabs + save button) on top
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /**
     * Returns a color that transitions from cool (low weight) to warm (high weight).
     * 1-3 = blue/teal, 4-6 = green/yellow, 7-10 = orange/red
     */
    private int getWeightColor(int weight) {
        if (weight <= 3)  return 0xCC3388AA; // teal
        if (weight <= 5)  return 0xCC44AA55; // green
        if (weight <= 7)  return 0xCCBBAA33; // yellow-orange
        return 0xCCCC5533;                    // orange-red
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
