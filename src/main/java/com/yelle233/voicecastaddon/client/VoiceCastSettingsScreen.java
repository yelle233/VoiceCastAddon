package com.yelle233.voicecastaddon.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class VoiceCastSettingsScreen extends Screen {
    private static final Component TITLE = Component.literal("Voice Cast Addon");
    private static final Component DEVICE_LABEL = Component.literal("\u8bed\u97f3\u8f93\u5165\u8bbe\u5907");
    private static final Component REFRESH_LABEL = Component.literal("\u5237\u65b0\u8bbe\u5907\u5217\u8868");
    private static final Component SAVE_LABEL = Component.literal("\u4fdd\u5b58");
    private static final Component BACK_LABEL = Component.literal("\u8fd4\u56de");
    private static final Component NOTE_1 = Component.literal("\u4fee\u6539\u540e\u5c06\u5728\u4e0b\u6b21\u5f00\u59cb\u8bed\u97f3\u8bc6\u522b\u65f6\u751f\u6548\u3002");
    private static final Component NOTE_2 = Component.literal("\u5982\u679c\u9009\u62e9\u7684\u8bbe\u5907\u4e0d\u53ef\u7528\uff0c\u4f1a\u81ea\u52a8\u56de\u9000\u5230\u7cfb\u7edf\u9ed8\u8ba4\u8bbe\u5907\u3002");

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
        int startY = this.height / 2 - 40;

        addRenderableWidget(CycleButton.<VoiceAudioDeviceManager.AudioInputDevice>builder(device ->
                        Component.literal(device.displayName()))
                .withValues(devices)
                .withInitialValue(selectedDevice)
                .displayOnlyValue()
                .create(centerX - 150, startY, 300, 20, DEVICE_LABEL, (button, value) -> selectedDevice = value));

        addRenderableWidget(Button.builder(REFRESH_LABEL, button -> reloadWidgets())
                .bounds(centerX - 150, startY + 30, 300, 20)
                .build());

        addRenderableWidget(Button.builder(SAVE_LABEL, button -> {
                    VoiceCastClientConfig.setPreferredInputDeviceId(selectedDevice.id());
                    onClose();
                }).bounds(centerX - 150, startY + 70, 145, 20)
                .build());

        addRenderableWidget(Button.builder(BACK_LABEL, button -> onClose())
                .bounds(centerX + 5, startY + 70, 145, 20)
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
        guiGraphics.drawCenteredString(this.font, NOTE_1, this.width / 2, this.height / 2 + 40, 0xA0A0A0);
        guiGraphics.drawCenteredString(this.font, NOTE_2, this.width / 2, this.height / 2 + 55, 0xA0A0A0);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }
}
