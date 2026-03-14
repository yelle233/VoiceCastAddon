package com.yelle233.voicecastaddon.command;

import com.yelle233.voicecastaddon.VoiceCastAddon;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

@EventBusSubscriber(modid = VoiceCastAddon.MODID, value = Dist.CLIENT)
public class ModCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterClientCommandsEvent event) {
        ListSpellsCommand.register(event.getDispatcher());
        GenerateConfigCommand.register(event.getDispatcher());
        ReloadConfigCommand.register(event.getDispatcher());
    }
}
