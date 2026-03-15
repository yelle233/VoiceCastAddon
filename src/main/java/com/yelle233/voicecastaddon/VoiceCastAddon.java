package com.yelle233.voicecastaddon;

import com.yelle233.voicecastaddon.server.VoiceCastServerConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

@Mod(VoiceCastAddon.MODID)
public class VoiceCastAddon {
    public static final String MODID = "voicecastaddon";

    public VoiceCastAddon(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        // Initialize server config files
        VoiceCastServerConfig.ensureServerFiles();
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Ensure server config is available when server starts
        VoiceCastServerConfig.ensureServerFiles();
    }
}
