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

import java.util.List;

@EventBusSubscriber(modid = VoiceCastAddon.MODID, value = Dist.CLIENT)
public class VoiceInputController {
    private static final String MSG_START_LISTENING = "\u5f00\u59cb\u8bed\u97f3\u8bc6\u522b...";
    private static final String MSG_START_FAILED = "\u8bed\u97f3\u8bc6\u522b\u542f\u52a8\u5931\u8d25: ";
    private static final String MSG_NO_CONTENT = "\u6ca1\u6709\u8bc6\u522b\u5230\u5185\u5bb9";
    private static final String MSG_MATCHED = "\u8bc6\u522b\u7ed3\u679c: ";
    private static final String MSG_UNMATCHED = "\u672a\u5339\u914d\u5230\u6cd5\u672f: ";

    private static boolean wasDown = false;
    private static volatile boolean recognitionInProgress = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        boolean isDown = ClientKeyMappings.VOICE_CAST_KEY.isDown();

        if (isDown && !wasDown && !recognitionInProgress) {
            if (VoiceRecognitionManager.startListening()) {
                mc.player.displayClientMessage(Component.literal(MSG_START_LISTENING), true);
            } else {
                mc.player.displayClientMessage(
                        Component.literal(MSG_START_FAILED + VoiceRecognitionManager.getLastError()),
                        true
                );
            }
        }

        if (!isDown && wasDown) {
            if (!VoiceRecognitionManager.isListening() || recognitionInProgress) {
                wasDown = isDown;
                return;
            }

            recognitionInProgress = true;
            Thread recognitionThread = new Thread(() -> processRecognition(mc), "voicecastaddon-recognize");
            recognitionThread.setDaemon(true);
            recognitionThread.start();
        }

        wasDown = isDown;
    }

    private static void processRecognition(Minecraft mc) {
        List<String> recognizedTexts = VoiceRecognitionManager.stopListeningAndRecognizeCandidates();

        mc.execute(() -> {
            try {
                if (mc.player == null) {
                    return;
                }

                if (recognizedTexts.isEmpty()) {
                    mc.player.displayClientMessage(Component.literal(MSG_NO_CONTENT), true);
                    return;
                }

                for (String recognizedText : recognizedTexts) {
                    ResourceLocation spellId = VoiceSpellAliases.match(recognizedText);
                    if (spellId == null) {
                        continue;
                    }

                    PacketDistributor.sendToServer(new VoiceCastPayload(spellId.toString()));
                    mc.player.displayClientMessage(
                            Component.literal(MSG_MATCHED + recognizedText + " -> " + spellId),
                            true
                    );
                    return;
                }

                mc.player.displayClientMessage(
                        Component.literal(MSG_UNMATCHED + String.join(" / ", recognizedTexts)),
                        true
                );
            } finally {
                recognitionInProgress = false;
            }
        });
    }
}
