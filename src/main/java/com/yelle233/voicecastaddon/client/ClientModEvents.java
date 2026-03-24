package com.yelle233.voicecastaddon.client;

import com.yelle233.voicecastaddon.VoiceCastAddon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

@EventBusSubscriber(
        modid = VoiceCastAddon.MODID,
        bus = EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public class ClientModEvents {

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ClientKeyMappings.VOICE_CAST_KEY);
        event.register(ClientKeyMappings.OPEN_SETTINGS_KEY);
    }
}