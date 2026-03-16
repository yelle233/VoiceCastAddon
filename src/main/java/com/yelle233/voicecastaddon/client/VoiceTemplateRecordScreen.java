package com.yelle233.voicecastaddon.client;

import com.yelle233.voicecastaddon.client.audio.VoiceTemplateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class VoiceTemplateRecordScreen extends Screen {
    private static final Component TITLE = Component.translatable("voicecastaddon.record.title");
    private static final Component RECORD_BUTTON = Component.translatable("voicecastaddon.record.record");
    private static final Component STOP_RECORD_BUTTON = Component.translatable("voicecastaddon.record.stop_record");
    private static final Component RECORDING = Component.translatable("voicecastaddon.record.recording");
    private static final Component DELETE_BUTTON = Component.translatable("voicecastaddon.record.delete");
    private static final Component BACK_BUTTON = Component.translatable("voicecastaddon.record.back");
    private static final Component TEST_BUTTON = Component.translatable("voicecastaddon.record.test");
    private static final Component STOP_TEST_BUTTON = Component.translatable("voicecastaddon.record.stop_test");
    private static final Component TESTING = Component.translatable("voicecastaddon.record.testing");
    private static final Component TEST_MATCHED = Component.translatable("voicecastaddon.record.test_matched");
    private static final Component TEST_WRONG = Component.translatable("voicecastaddon.record.test_wrong");
    private static final Component TEST_NO_MATCH = Component.translatable("voicecastaddon.record.test_no_match");
    private static final String TEMPLATE_MARKER = "[REC]";

    private final Screen parent;
    private SpellList spellList;
    private Button recordButton;
    private Button testButton;
    private Button deleteButton;
    private Button backButton;
    private boolean isRecording = false;
    private boolean isTesting = false;
    private boolean isProcessing = false;
    private ResourceLocation activeSpellId;
    private Component testResultMessage = Component.empty();
    private int testResultColor = 0xFFFFFF;

    public VoiceTemplateRecordScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
    }

    @Override
    protected void init() {
        List<ResourceLocation> availableSpells = loadAvailableSpells();

        if (availableSpells.isEmpty()) {
            addRenderableWidget(Button.builder(BACK_BUTTON, b -> onClose())
                    .bounds(this.width / 2 - 75, this.height / 2, 150, 20)
                    .build());
            return;
        }

        spellList = new SpellList(this.minecraft, this.width, this.height - 96, 32, 24, availableSpells);
        addRenderableWidget(spellList);

        int buttonY = this.height - 56;
        int centerX = this.width / 2;

        recordButton = addRenderableWidget(Button.builder(RECORD_BUTTON, b -> toggleRecording())
                .bounds(centerX - 155, buttonY, 75, 20)
                .build());

        testButton = addRenderableWidget(Button.builder(TEST_BUTTON, b -> toggleTesting())
                .bounds(centerX - 75, buttonY, 70, 20)
                .build());

        deleteButton = addRenderableWidget(Button.builder(DELETE_BUTTON, b -> deleteTemplates())
                .bounds(centerX + 5, buttonY, 70, 20)
                .build());

        backButton = addRenderableWidget(Button.builder(BACK_BUTTON, b -> onClose())
                .bounds(centerX + 80, buttonY, 75, 20)
                .build());

        refreshActionButtons();
    }

    private void toggleRecording() {
        if (isRecording) {
            finishRecording();
            return;
        }

        if (isTesting || isProcessing || spellList == null || spellList.getSelected() == null) {
            return;
        }

        activeSpellId = spellList.getSelected().spellId;
        isRecording = true;
        testResultMessage = Component.empty();
        refreshActionButtons();

        if (!VoiceRecognitionManager.startListening()) {
            isRecording = false;
            activeSpellId = null;
            refreshActionButtons();
        }
    }

    private void finishRecording() {
        if (!isRecording) {
            return;
        }

        ResourceLocation targetSpell = activeSpellId;
        byte[] audioBytes = VoiceRecognitionManager.stopListeningAndGetAudio();

        isRecording = false;
        activeSpellId = null;
        refreshActionButtons();

        try {
            if (audioBytes.length > 0 && targetSpell != null) {
                VoiceTemplateManager.saveTemplate(targetSpell, audioBytes);
            }
        } catch (Exception e) {
            // Silently fail
        }
    }

    private void toggleTesting() {
        if (isTesting) {
            finishTesting();
            return;
        }

        if (isRecording || isProcessing || spellList == null || spellList.getSelected() == null) {
            return;
        }

        activeSpellId = spellList.getSelected().spellId;
        isTesting = true;
        testResultMessage = Component.empty();
        refreshActionButtons();

        if (!VoiceRecognitionManager.startListening()) {
            isTesting = false;
            activeSpellId = null;
            refreshActionButtons();
        }
    }

    private void finishTesting() {
        if (!isTesting) {
            return;
        }

        ResourceLocation expectedSpell = activeSpellId;
        isTesting = false;
        isProcessing = true;
        activeSpellId = null;
        refreshActionButtons();

        Thread testThread = new Thread(() -> {
            ResourceLocation matched = VoiceRecognitionManager.stopListeningAndMatch();

            minecraft.execute(() -> {
                if (matched != null) {
                    if (matched.equals(expectedSpell)) {
                        testResultMessage = TEST_MATCHED;
                        testResultColor = 0x55FF55;
                    } else {
                        testResultMessage = TEST_WRONG;
                        testResultColor = 0xFFD24D;
                    }
                } else {
                    testResultMessage = TEST_NO_MATCH;
                    testResultColor = 0xFF6B6B;
                }
                isProcessing = false;
                refreshActionButtons();
            });
        }, "voicecastaddon-test");

        testThread.setDaemon(true);
        testThread.start();
    }

    private void deleteTemplates() {
        if (spellList.getSelected() == null) {
            return;
        }

        try {
            VoiceTemplateManager.deleteTemplates(spellList.getSelected().spellId);
            testResultMessage = Component.empty();
        } catch (Exception e) {
            // Silently fail
        }
    }

    private void refreshActionButtons() {
        if (recordButton != null) {
            recordButton.setMessage(isRecording ? STOP_RECORD_BUTTON : RECORD_BUTTON);
            recordButton.active = !isTesting && !isProcessing;
        }

        if (testButton != null) {
            testButton.setMessage(isTesting ? STOP_TEST_BUTTON : TEST_BUTTON);
            testButton.active = !isRecording && !isProcessing;
        }

        if (deleteButton != null) {
            deleteButton.active = !isRecording && !isTesting && !isProcessing;
        }

        if (backButton != null) {
            backButton.active = !isTesting && !isProcessing;
        }
    }

    private List<ResourceLocation> loadAvailableSpells() {
        List<ResourceLocation> spells = new ArrayList<>();
        try {
            for (io.redspace.ironsspellbooks.api.spells.AbstractSpell spell :
                 io.redspace.ironsspellbooks.api.registry.SpellRegistry.REGISTRY) {
                ResourceLocation spellId =
                    io.redspace.ironsspellbooks.api.registry.SpellRegistry.REGISTRY.getKey(spell);
                if (spellId != null) {
                    spells.add(spellId);
                }
            }
        } catch (Exception e) {
            // Fallback: empty list
        }
        return spells;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        // 先渲染列表
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 然后渲染文字（在最上层，不会被模糊）
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);

        if (!testResultMessage.getString().isEmpty()) {
            guiGraphics.drawString(this.font, testResultMessage, this.width / 2 + 165, this.height - 50, testResultColor);
        }

        if (isRecording) {
            guiGraphics.drawCenteredString(this.font, RECORDING, this.width / 2, this.height - 85, 0xFF0000);
        } else if (isTesting) {
            guiGraphics.drawCenteredString(this.font, TESTING, this.width / 2, this.height - 85, 0x00FF00);
        }
    }

    @Override
    public void onClose() {
        if (VoiceRecognitionManager.isListening()) {
            VoiceRecognitionManager.stopListeningAndGetAudio();
        }
        isRecording = false;
        isTesting = false;
        isProcessing = false;
        activeSpellId = null;

        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private class SpellList extends ObjectSelectionList<SpellEntry> {
        public SpellList(Minecraft minecraft, int width, int height, int y, int itemHeight, List<ResourceLocation> spells) {
            super(minecraft, width, height, y, itemHeight);
            for (ResourceLocation spellId : spells) {
                this.addEntry(new SpellEntry(spellId));
            }
        }

        @Override
        protected void renderListBackground(GuiGraphics guiGraphics) {
            int left = this.getX();
            int top = this.getY();
            int right = left + this.width;
            int bottom = top + this.height;

            guiGraphics.fill(left - 1, top - 1, right + 1, bottom + 1, 0xFF6B5D46);
            guiGraphics.fill(left, top, right, bottom, 0xF0201A14);
        }

        @Override
        protected void renderListSeparators(GuiGraphics guiGraphics) {
            // 不绘制分隔线
        }
    }

    private class SpellEntry extends ObjectSelectionList.Entry<SpellEntry> {
        private final ResourceLocation spellId;
        private final Component displayName;

        public SpellEntry(ResourceLocation spellId) {
            this.spellId = spellId;
            this.displayName = SpellNameHelper.getSpellDisplayName(spellId);
        }

        @Override
        public Component getNarration() {
            return displayName;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height,
                          int mouseX, int mouseY, boolean isMouseOver, float partialTick) {
            // 绘制选中高亮或悬停效果
            if (spellList.getSelected() == this) {
                guiGraphics.fill(left, top, left + width, top + height, 0x80FFFFFF);
            } else if (isMouseOver) {
                guiGraphics.fill(left, top, left + width, top + height, 0x40FFFFFF);
            }

            int sampleCount = VoiceTemplateManager.getSampleCount(spellId);
            String countText = sampleCount > 0 ? " (" + sampleCount + ")" : "";
            int textLeft = left + 5;
            if (sampleCount > 0) {
                guiGraphics.drawString(font, TEMPLATE_MARKER, textLeft, top + 2, 0x55FF55);
                textLeft += font.width(TEMPLATE_MARKER) + 4;
            }

            guiGraphics.drawString(font, displayName.getString() + countText, textLeft, top + 2, 0xFFFFFF);
            guiGraphics.drawString(font, spellId.toString(), left + 5, top + 12, 0x808080);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (isTesting || isProcessing) {
                return false;
            }
            if (button == 0) {
                spellList.setSelected(this);
                testResultMessage = Component.empty();
                return true;
            }
            return false;
        }
    }
}
