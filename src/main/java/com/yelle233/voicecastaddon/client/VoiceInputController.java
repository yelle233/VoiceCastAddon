package com.yelle233.voicecastaddon.client;

import com.yelle233.voicecastaddon.VoiceCastAddon;
import com.yelle233.voicecastaddon.network.VoiceCastPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = VoiceCastAddon.MODID, value = Dist.CLIENT)
public class VoiceInputController {
    private static boolean wasDown = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        boolean isDown = ClientKeyMappings.VOICE_CAST_KEY.isDown();

        if (isDown && !wasDown) {
            if (VoiceRecognitionManager.startListening()) {
                mc.player.displayClientMessage(Component.literal("开始语音识别..."), true);
            } else {
                mc.player.displayClientMessage(
                        Component.literal("语音识别启动失败: " + VoiceRecognitionManager.getLastError()),
                        true
                );
            }
        }

        if (!isDown && wasDown) {
            if (!VoiceRecognitionManager.isListening()) {
                wasDown = isDown;
                return;
            }

            String recognizedText = VoiceRecognitionManager.stopListeningAndRecognize();

            if (recognizedText == null || recognizedText.isBlank()) {
                mc.player.displayClientMessage(Component.literal("没有识别到内容"), true);
                wasDown = isDown;
                return;
            }

            ResourceLocation spellId = VoiceSpellAliases.match(recognizedText);
            if (spellId == null) {
                mc.player.displayClientMessage(
                        Component.literal("未匹配到法术: " + recognizedText),
                        true
                );
                wasDown = isDown;
                return;
            }

            PacketDistributor.sendToServer(new VoiceCastPayload(spellId.toString()));

            mc.player.displayClientMessage(
                    Component.literal("识别结果: " + recognizedText + " -> " + spellId),
                    true
            );
        }

        wasDown = isDown;
    }
}
