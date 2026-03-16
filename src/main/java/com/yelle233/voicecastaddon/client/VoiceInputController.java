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
    private static volatile boolean recognitionInProgress = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            // Don't process voice input when any GUI is open (including recording screen)
            if (wasDown && VoiceRecognitionManager.isListening()) {
                VoiceRecognitionManager.stopListeningAndGetAudio();
            }
            wasDown = false;
            return;
        }

        boolean isDown = ClientKeyMappings.VOICE_CAST_KEY.isDown();

        if (isDown && !wasDown && !recognitionInProgress) {
            if (VoiceRecognitionManager.startListening()) {
                mc.player.displayClientMessage(Component.translatable("voicecastaddon.message.start_listening"), true);
            } else {
                mc.player.displayClientMessage(
                        Component.translatable("voicecastaddon.message.start_failed", VoiceRecognitionManager.getLastError()),
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
        try {
            ResourceLocation spellId = VoiceRecognitionManager.stopListeningAndMatch();

            mc.execute(() -> {
                try {
                    if (mc.player == null) {
                        return;
                    }

                    if (spellId == null) {
                        mc.player.displayClientMessage(Component.translatable("voicecastaddon.message.no_match"), true);
                        return;
                    }

                    PacketDistributor.sendToServer(new VoiceCastPayload(spellId.toString()));
                    Component spellName = SpellNameHelper.getSpellDisplayName(spellId);
                    mc.player.displayClientMessage(
                            Component.translatable("voicecastaddon.message.matched", spellName),
                            true
                    );
                } finally {
                    recognitionInProgress = false;
                }
            });
        } catch (Exception e) {
            recognitionInProgress = false;
            throw e;
        }
    }
}
