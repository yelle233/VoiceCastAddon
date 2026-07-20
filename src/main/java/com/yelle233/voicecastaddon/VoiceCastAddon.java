package com.yelle233.voicecastaddon;

import com.yelle233.voicecastaddon.network.ModNetworking;
import com.yelle233.voicecastaddon.server.VoiceCastServerConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.event.server.ServerStartingEvent;

@Mod(VoiceCastAddon.MODID)
public class VoiceCastAddon {
    public static final String MODID = "voicecastaddon";

    public VoiceCastAddon() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);

        MinecraftForge.EVENT_BUS.register(this);

        ModNetworking.register();

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> com.yelle233.voicecastaddon.client.VoiceCastAddonClient::init);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {

        VoiceCastServerConfig.ensureServerFiles();
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {

        VoiceCastServerConfig.ensureServerFiles();
    }
}
