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
    private static final Component RECORDING = Component.translatable("voicecastaddon.record.recording");
    private static final Component DELETE_BUTTON = Component.translatable("voicecastaddon.record.delete");
    private static final Component BACK_BUTTON = Component.translatable("voicecastaddon.record.back");
    private static final Component TEST_BUTTON = Component.translatable("voicecastaddon.record.test");
    private static final Component TESTING = Component.translatable("voicecastaddon.record.testing");

    private final Screen parent;
    private SpellList spellList;
    private boolean isRecording = false;
    private boolean isTesting = false;
    private String statusMessage = "";

    public VoiceTemplateRecordScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
    }

    @Override
    protected void init() {
        List<ResourceLocation> availableSpells = loadAvailableSpells();

        if (availableSpells.isEmpty()) {
            statusMessage = "No spells available. Use /voicecast list to see available spells.";
            addRenderableWidget(Button.builder(BACK_BUTTON, b -> onClose())
                    .bounds(this.width / 2 - 75, this.height / 2, 150, 20)
                    .build());
            return;
        }

        spellList = new SpellList(this.minecraft, this.width, this.height - 96, 32, 24, availableSpells);
        addWidget(spellList);

        int buttonY = this.height - 56;
        int centerX = this.width / 2;

        addRenderableWidget(Button.builder(RECORD_BUTTON, b -> startRecording())
                .bounds(centerX - 155, buttonY, 75, 20)
                .build());

        addRenderableWidget(Button.builder(TEST_BUTTON, b -> startTesting())
                .bounds(centerX - 75, buttonY, 70, 20)
                .build());

        addRenderableWidget(Button.builder(DELETE_BUTTON, b -> deleteTemplates())
                .bounds(centerX + 5, buttonY, 70, 20)
                .build());

        addRenderableWidget(Button.builder(BACK_BUTTON, b -> onClose())
                .bounds(centerX + 80, buttonY, 75, 20)
                .build());

        updateStatus();
    }

    private void startRecording() {
        if (isRecording || isTesting || spellList.getSelected() == null) {
            return;
        }

        isRecording = true;
        statusMessage = "Recording... Release to save";

        Thread recordThread = new Thread(() -> {
            if (VoiceRecognitionManager.startListening()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                while (VoiceRecognitionManager.isListening()) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                byte[] audioBytes = VoiceRecognitionManager.stopListeningAndGetAudio();

                minecraft.execute(() -> {
                    try {
                        if (audioBytes.length > 0 && spellList.getSelected() != null) {
                            VoiceTemplateManager.saveTemplate(spellList.getSelected().spellId, audioBytes);
                            statusMessage = "Template saved successfully!";
                        } else {
                            statusMessage = "Recording failed: no audio captured";
                        }
                    } catch (Exception e) {
                        statusMessage = "Error: " + e.getMessage();
                    } finally {
                        isRecording = false;
                        updateStatus();
                    }
                });
            } else {
                minecraft.execute(() -> {
                    statusMessage = "Failed to start recording";
                    isRecording = false;
                });
            }
        }, "voicecastaddon-record");

        recordThread.setDaemon(true);
        recordThread.start();
    }

    private void startTesting() {
        if (isRecording || isTesting || spellList.getSelected() == null) {
            return;
        }

        ResourceLocation expectedSpell = spellList.getSelected().spellId;
        isTesting = true;
        statusMessage = "Testing... Speak now";

        Thread testThread = new Thread(() -> {
            if (VoiceRecognitionManager.startListening()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                while (VoiceRecognitionManager.isListening()) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                ResourceLocation matched = VoiceRecognitionManager.stopListeningAndMatch();

                minecraft.execute(() -> {
                    if (matched != null) {
                        if (matched.equals(expectedSpell)) {
                            statusMessage = "Match SUCCESS: " + matched;
                        } else {
                            statusMessage = "Match WRONG: got " + matched + ", expected " + expectedSpell;
                        }
                    } else {
                        statusMessage = "No match found";
                    }
                    isTesting = false;
                    updateStatus();
                });
            } else {
                minecraft.execute(() -> {
                    statusMessage = "Failed to start testing";
                    isTesting = false;
                });
            }
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
            statusMessage = "Templates deleted";
            updateStatus();
        } catch (Exception e) {
            statusMessage = "Error: " + e.getMessage();
        }
    }

    private void updateStatus() {
        if (spellList != null && spellList.getSelected() != null) {
            int count = VoiceTemplateManager.getSampleCount(spellList.getSelected().spellId);
            if (statusMessage.isEmpty() || statusMessage.startsWith("Samples:")) {
                statusMessage = "Samples: " + count;
            }
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

        if (spellList != null) {
            spellList.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, statusMessage, this.width / 2, this.height - 70, 0xFFFFFF);

        if (isRecording) {
            guiGraphics.drawCenteredString(this.font, RECORDING, this.width / 2, this.height - 85, 0xFF0000);
        } else if (isTesting) {
            guiGraphics.drawCenteredString(this.font, TESTING, this.width / 2, this.height - 85, 0x00FF00);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
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
            guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0xC0101010);
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

            guiGraphics.drawString(font, displayName.getString() + countText, left + 5, top + 2, 0xFFFFFF);
            guiGraphics.drawString(font, spellId.toString(), left + 5, top + 12, 0x808080);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                spellList.setSelected(this);
                updateStatus();
                return true;
            }
            return false;
        }
    }
}
