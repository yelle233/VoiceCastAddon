package com.yelle233.voicecastaddon.client;

import com.yelle233.voicecastaddon.VoiceCastAddon;
import com.yelle233.voicecastaddon.network.ModNetworking;
import com.yelle233.voicecastaddon.network.VoiceCastPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = VoiceCastAddon.MODID, value = Dist.CLIENT)
public class VoiceInputController {
    private static boolean wasDown = false;
    private static volatile boolean recognitionInProgress = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {

        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {

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

                    ModNetworking.sendToServer(new VoiceCastPacket(spellId.toString()));
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
