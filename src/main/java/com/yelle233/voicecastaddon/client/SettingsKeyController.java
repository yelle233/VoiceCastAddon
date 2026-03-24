package com.yelle233.voicecastaddon.client;

import com.yelle233.voicecastaddon.VoiceCastAddon;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = VoiceCastAddon.MODID, value = Dist.CLIENT)
public class SettingsKeyController {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        // consumeClick() returns true once when the key is pressed, then consumes the event
        if (ClientKeyMappings.OPEN_SETTINGS_KEY.consumeClick()) {
            mc.setScreen(new VoiceCastSettingsScreen(mc.screen));
        }
    }
}
