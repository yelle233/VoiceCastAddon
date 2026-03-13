package com.yelle233.voicecastaddon.network;

import com.yelle233.voicecastaddon.VoiceCastAddon;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(
        modid = VoiceCastAddon.MODID,
        bus = EventBusSubscriber.Bus.MOD
)
public class ModPayloads {

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(
                VoiceCastPayload.TYPE,
                VoiceCastPayload.STREAM_CODEC,
                ServerPayloadHandler::handle
        );
    }
}
