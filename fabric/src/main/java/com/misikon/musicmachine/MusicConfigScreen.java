package com.misikon.musicmachine;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.screen.Screen;

import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.*;
import java.util.stream.Collectors;

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

    private String selectedCategory;
    private int scrollOffset = 0;

    private final Map<String, Boolean> trackStates = new LinkedHashMap<>();
    private final Map<String, Integer> trackWeightValues = new LinkedHashMap<>();
    private Config.MusicFrequency frequencyState;
    private Config.MusicProfile profileState;

    private List<String> filteredTracks = new ArrayList<>();
    private final List<ButtonWidget> tabButtons = new ArrayList<>();

    public MusicConfigScreen(Screen parent) {
        super(Text.translatable("musicmachine.screen.title"));
        this.parent = parent;
        this.selectedCategory = Config.getOrderedCategories().get(0);
    }

    @Override
    protected void init() {
        trackStates.clear();
        trackWeightValues.clear();
        for (String track : Config.getIndividualTracks()) {
            trackStates.put(track, Config.isTrackEnabled(track));
            trackWeightValues.put(track, Config.getTrackWeight(track));
        }
        frequencyState = Config.getMusicFrequency();
        profileState = Config.getMusicProfile();

        tabButtons.clear();
        List<String> categories = Config.getOrderedCategories();
        int tabWidth = Math.min(80, (this.width - 20) / categories.size());
        int totalTabWidth = tabWidth * categories.size();
        int tabStartX = (this.width - totalTabWidth) / 2;

        for (int i = 0; i < categories.size(); i++) {
            String cat = categories.get(i);
            int x = tabStartX + i * tabWidth;
            ButtonWidget tab = ButtonWidget.builder(Text.literal(cat), btn -> {
                selectedCategory = cat;
                scrollOffset = 0;
                rebuildFilteredList();
                updateTabHighlights();
            }).dimensions(x, TAB_Y, tabWidth, TAB_HEIGHT).build();
            tabButtons.add(tab);
            this.addDrawableChild(tab);
        }
        updateTabHighlights();

        ButtonWidget freqButton = ButtonWidget.builder(
                Text.literal("Frequency: " + frequencyState.getLabel()), btn -> {
                    frequencyState = frequencyState.next();
                    btn.setMessage(Text.literal("Frequency: " + frequencyState.getLabel()));
                })
                .dimensions(this.width / 2 - 156, this.height - BOTTOM_BAR_HEIGHT + 4, 100, 20)
                .build();
        this.addDrawableChild(freqButton);

        ButtonWidget profileButton = ButtonWidget.builder(
                Text.literal("Profile: " + profileState.getLabel()), btn -> {
                    profileState = profileState.next();
                    btn.setMessage(Text.literal("Profile: " + profileState.getLabel()));
                })
                .dimensions(this.width / 2 - 50, this.height - BOTTOM_BAR_HEIGHT + 4, 100, 20)
                .build();
        this.addDrawableChild(profileButton);

        this.addDrawableChild(ButtonWidget.builder(
                Text.translatable("musicmachine.button.save"), btn -> {
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
                    this.client.getMusicTracker().stop();
                    this.client.setScreen(parent);
                })
                .dimensions(this.width / 2 + 56, this.height - BOTTOM_BAR_HEIGHT + 4, 100, 20)
                .build()
        );

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Toggle All"), btn -> {
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
                .dimensions(LEFT_MARGIN, this.height - BOTTOM_BAR_HEIGHT + 4, 70, 20)
                .build()
        );

        rebuildFilteredList();
    }

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

        int maxScroll = getMaxScroll();
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
    }

    private void updateTabHighlights() {
        List<String> cats = Config.getOrderedCategories();
        for (int i = 0; i < tabButtons.size() && i < cats.size(); i++) {
            boolean active = cats.get(i).equals(selectedCategory);
            ButtonWidget btn = tabButtons.get(i);
            btn.active = !active;
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        int maxScroll = getMaxScroll();
        scrollOffset = (int) MathHelper.clamp(scrollOffset - scrollY * ROW_HEIGHT, 0, maxScroll);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (profileState == Config.MusicProfile.CUSTOM) {
            if (button == 0 && mouseY >= LIST_TOP && mouseY < getListBottom()) {
                int listWidth = this.width - LEFT_MARGIN - RIGHT_MARGIN;
                int listX = LEFT_MARGIN;

                for (int i = 0; i < filteredTracks.size(); i++) {
                    int rowY = LIST_TOP + i * ROW_HEIGHT - scrollOffset;
                    if (rowY + ROW_HEIGHT < LIST_TOP || rowY > getListBottom()) continue;
                    if (mouseY < rowY || mouseY >= rowY + ROW_HEIGHT) continue;

                    String track = filteredTracks.get(i);
                    if (track.startsWith("header:")) continue;

                    int toggleX = listX + listWidth - 36;
                    int toggleY = rowY + 3;
                    int toggleW = 32;
                    int toggleH = 14;

                    int sliderW = 100;
                    int sliderX = toggleX - sliderW - 8;
                    int sliderY = rowY + 3;
                    int sliderH = 14;

                    if (mouseX >= toggleX && mouseX <= toggleX + toggleW
                            && mouseY >= toggleY && mouseY <= toggleY + toggleH) {
                        trackStates.put(track, !Boolean.TRUE.equals(trackStates.get(track)));
                        return true;
                    }

                    if (mouseX >= sliderX && mouseX <= sliderX + sliderW
                            && mouseY >= sliderY && mouseY <= sliderY + sliderH) {
                        if (Boolean.TRUE.equals(trackStates.get(track))) {
                            double ratio = (mouseX - sliderX) / (double) sliderW;
                            int weight = Config.MIN_WEIGHT + (int) Math.round(ratio * (Config.MAX_WEIGHT - Config.MIN_WEIGHT));
                            weight = MathHelper.clamp(weight, Config.MIN_WEIGHT, Config.MAX_WEIGHT);
                            trackWeightValues.put(track, weight);
                        }
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (profileState == Config.MusicProfile.CUSTOM) {
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
                        weight = MathHelper.clamp(weight, Config.MIN_WEIGHT, Config.MAX_WEIGHT);
                        trackWeightValues.put(track, weight);
                        return true;
                    }
                }
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(context);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFFFF);

        String currentTrack = Config.getNowPlaying();
        String nowPlayingText = currentTrack == null ? "None" : Config.getDisplayName(currentTrack);
        String displayText = "♪ Now Playing: " + nowPlayingText + " ♪";
        
        long time = System.currentTimeMillis();
        float hue = (time % 5000L) / 5000.0f;
        int animatedColor = java.awt.Color.HSBtoRGB(hue, 0.5f, 0.9f) | 0xFF000000;
        
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(displayText), this.width / 2, 22, animatedColor);

        context.enableScissor(0, 36 + TAB_HEIGHT + 6, this.width, getListBottom());

        int listWidth = this.width - LEFT_MARGIN - RIGHT_MARGIN;
        int listX = LEFT_MARGIN;

        for (int i = 0; i < filteredTracks.size(); i++) {
            int rowY = 36 + TAB_HEIGHT + 6 + i * ROW_HEIGHT - scrollOffset;

            if (rowY + ROW_HEIGHT < 36 + TAB_HEIGHT + 6 || rowY > getListBottom()) continue;

            String track = filteredTracks.get(i);

            if (track.startsWith("header:")) {
                String headerName = track.substring(7);
                context.fill(listX, rowY + 1, listX + listWidth, rowY + ROW_HEIGHT - 1, 0xAA000000);
                context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(headerName), listX + listWidth / 2, rowY + 8, 0xFFAAAAAA);
                continue;
            }
            
            boolean enabled;
            if (profileState == Config.MusicProfile.DEFAULT) {
                enabled = true;
            } else if (profileState == Config.MusicProfile.LEGACY) {
                enabled = Config.getAuthor(track).contains("C418");
            } else {
                enabled = Boolean.TRUE.equals(trackStates.get(track));
            }

            int weight = trackWeightValues.getOrDefault(track, Config.DEFAULT_WEIGHT);

            boolean hovered = mouseY >= rowY && mouseY < rowY + ROW_HEIGHT
                    && mouseX >= listX && mouseX <= listX + listWidth
                    && mouseY >= 36 + TAB_HEIGHT + 6 && mouseY < getListBottom();
            int bgColor = hovered ? 0xCC333344 : 0xAA222233;
            context.fill(listX, rowY + 1, listX + listWidth, rowY + ROW_HEIGHT - 1, bgColor);

            String displayName = Config.getDisplayName(track);
            String author = Config.getAuthor(track);
            int color = enabled ? 0xFFFFFFFF : 0xFF555555;
            if (profileState != Config.MusicProfile.CUSTOM) {
                color = enabled ? 0xFF888888 : 0xFF444444;
            }
            context.drawTextWithShadow(this.textRenderer, Text.literal(displayName), listX + 10, rowY + 8, color);
            context.drawTextWithShadow(this.textRenderer, Text.literal("by " + author), listX + 10 + this.textRenderer.getWidth(displayName) + 8, rowY + 8, 0xFF666666);

            int toggleX = listX + listWidth - 36;
            int toggleY = rowY + 4;
            int toggleW = 32;
            int toggleH = 14;

            int toggleColor = enabled ? 0xFF55FF55 : 0xFFFF5555;
            if (profileState != Config.MusicProfile.CUSTOM) {
                toggleColor = enabled ? 0xFF448844 : 0xFF884444;
            }
            context.fill(toggleX, toggleY, toggleX + toggleW, toggleY + toggleH, 0xFF000000);
            context.fill(toggleX + 1, toggleY + 1, toggleX + toggleW - 1, toggleY + toggleH - 1, toggleColor);
            String toggleText = enabled ? "ON" : "OFF";
            int toggleTextWidth = this.textRenderer.getWidth(toggleText);
            context.drawTextWithShadow(this.textRenderer, Text.literal(toggleText),
                    toggleX + (toggleW - toggleTextWidth) / 2,
                    toggleY + 3, 0xFFFFFFFF);

            int sliderW = 100;
            int sliderX = toggleX - sliderW - 8;
            int sliderY = rowY + 4;
            int sliderH = 14;

            if (enabled) {
                context.fill(sliderX, sliderY, sliderX + sliderW, sliderY + sliderH, 0xFF555566);
                context.fill(sliderX + 1, sliderY + 1, sliderX + sliderW - 1, sliderY + sliderH - 1, 0xFF333344);

                double ratio = (weight - Config.MIN_WEIGHT) / (double) (Config.MAX_WEIGHT - Config.MIN_WEIGHT);
                int fillWidth = (int) (ratio * (sliderW - 2));
                int fillColor = getWeightColor(weight);
                if (profileState != Config.MusicProfile.CUSTOM) {
                    fillColor = (fillColor & 0x00FFFFFF) | 0x66000000;
                }
                context.fill(sliderX + 1, sliderY + 1, sliderX + 1 + fillWidth, sliderY + sliderH - 1, fillColor);

                int handleX = sliderX + 1 + fillWidth - 1;
                if (handleX >= sliderX + 1) {
                    context.fill(handleX, sliderY, handleX + 3, sliderY + sliderH, 0xFFFFFFFF);
                }

                String weightStr = String.valueOf(weight);
                int textColor = (profileState != Config.MusicProfile.CUSTOM) ? 0xFF888888 : 0xFFFFFFFF;
                context.drawTextWithShadow(this.textRenderer, Text.literal(weightStr),
                        sliderX + (sliderW - this.textRenderer.getWidth(weightStr)) / 2,
                        sliderY + 3, textColor);
            } else {
                context.fill(sliderX, sliderY, sliderX + sliderW, sliderY + sliderH, 0xFF444444);
                context.fill(sliderX + 1, sliderY + 1, sliderX + sliderW - 1, sliderY + sliderH - 1, 0xFF2A2A2A);
                String disabledStr = "—";
                context.drawTextWithShadow(this.textRenderer, Text.literal(disabledStr),
                        sliderX + (sliderW - this.textRenderer.getWidth(disabledStr)) / 2,
                        sliderY + 3, 0xFF666666);
            }
        }

        context.disableScissor();

        if (getMaxScroll() > 0) {
            int scrollbarX = this.width - 4;
            int visibleH = getVisibleHeight();
            int totalH = filteredTracks.size() * ROW_HEIGHT;
            int thumbH = Math.max(10, (int) ((float) visibleH / totalH * visibleH));
            int thumbY = LIST_TOP + (int) ((float) scrollOffset / getMaxScroll() * (visibleH - thumbH));

            context.fill(scrollbarX, LIST_TOP, scrollbarX + 3, getListBottom(), 0x44FFFFFF);
            context.fill(scrollbarX, thumbY, scrollbarX + 3, thumbY + thumbH, 0xAAFFFFFF);
        }

        long enabledCount = filteredTracks.stream()
                .filter(t -> !t.startsWith("header:") && Boolean.TRUE.equals(trackStates.get(t)))
                .count();
        long totalCount = filteredTracks.stream()
                .filter(t -> !t.startsWith("header:"))
                .count();
        String countText = enabledCount + "/" + totalCount + " enabled";
        context.drawTextWithShadow(this.textRenderer, Text.literal(countText),
                LEFT_MARGIN + 76, this.height - BOTTOM_BAR_HEIGHT + 10, 0xFF999999);

        super.render(context, mouseX, mouseY, partialTick);
    }

    private int getWeightColor(int weight) {
        if (weight <= 3)  return 0xCC3388AA;
        if (weight <= 5)  return 0xCC44AA55;
        if (weight <= 7)  return 0xCCBBAA33;
        return 0xCCCC5533;
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
