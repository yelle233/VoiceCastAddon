package com.yelle233.voicecastaddon.command;

import com.yelle233.voicecastaddon.VoiceCastAddon;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = VoiceCastAddon.MODID, value = Dist.CLIENT)
public class ModCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterClientCommandsEvent event) {
        ListSpellsCommand.register(event.getDispatcher());
    }
}
