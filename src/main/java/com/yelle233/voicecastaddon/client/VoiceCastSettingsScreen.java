package com.yelle233.voicecastaddon.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class VoiceCastSettingsScreen extends Screen {
    private static final Component TITLE = Component.translatable("voicecastaddon.settings.title");
    private static final Component DEVICE_LABEL = Component.translatable("voicecastaddon.settings.device_label");
    private static final Component REFRESH_LABEL = Component.translatable("voicecastaddon.settings.refresh");
    private static final Component RECORD_LABEL = Component.translatable("voicecastaddon.settings.record");
    private static final Component SAVE_LABEL = Component.translatable("voicecastaddon.settings.save");
    private static final Component BACK_LABEL = Component.translatable("voicecastaddon.settings.back");
    private static final Component NOTE = Component.translatable("voicecastaddon.settings.note_record");

    private final Screen parent;
    private List<VoiceAudioDeviceManager.AudioInputDevice> devices = List.of();
    private VoiceAudioDeviceManager.AudioInputDevice selectedDevice;

    public VoiceCastSettingsScreen(Screen parent) {
        super(TITLE);
        this.parent = parent;
    }

    @Override
    protected void init() {
        reloadWidgets();
    }

    private void reloadWidgets() {
        clearWidgets();
        devices = VoiceAudioDeviceManager.listInputDevices();
        selectedDevice = resolveSelectedDevice();

        int centerX = this.width / 2;
        int startY = this.height / 2 - 60;

        addRenderableWidget(CycleButton.<VoiceAudioDeviceManager.AudioInputDevice>builder(device ->
                        Component.literal(device.displayName()))
                .withValues(devices)
                .withInitialValue(selectedDevice)
                .displayOnlyValue()
                .create(centerX - 150, startY, 300, 20, DEVICE_LABEL, (button, value) -> selectedDevice = value));

        addRenderableWidget(Button.builder(REFRESH_LABEL, button -> reloadWidgets())
                .bounds(centerX - 150, startY + 30, 300, 20)
                .build());

        addRenderableWidget(Button.builder(RECORD_LABEL, button -> {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(new VoiceTemplateRecordScreen(this));
                    }
                }).bounds(centerX - 150, startY + 60, 300, 20)
                .build());

        addRenderableWidget(Button.builder(SAVE_LABEL, button -> {
                    VoiceCastClientConfig.setPreferredInputDeviceId(selectedDevice.id());
                    onClose();
                }).bounds(centerX - 150, startY + 100, 145, 20)
                .build());

        addRenderableWidget(Button.builder(BACK_LABEL, button -> onClose())
                .bounds(centerX + 5, startY + 100, 145, 20)
                .build());
    }

    private VoiceAudioDeviceManager.AudioInputDevice resolveSelectedDevice() {
        String preferredId = VoiceCastClientConfig.getPreferredInputDeviceId();
        return devices.stream()
                .filter(device -> device.id().equals(preferredId))
                .findFirst()
                .orElse(devices.getFirst());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 40, 0xFFFFFF);
        guiGraphics.drawCenteredString(this.font, NOTE, this.width / 2, this.height / 2 + 50, 0xA0A0A0);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }
}
