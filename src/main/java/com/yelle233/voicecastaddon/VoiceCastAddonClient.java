package com.yelle233.voicecastaddon;

import com.yelle233.voicecastaddon.client.VoiceCastClientConfig;
import com.yelle233.voicecastaddon.client.VoiceCastSettingsScreen;
import com.yelle233.voicecastaddon.client.VoiceRecognitionManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = VoiceCastAddon.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = VoiceCastAddon.MODID, value = Dist.CLIENT)
public class VoiceCastAddonClient {
    public VoiceCastAddonClient(ModContainer container) {
        IConfigScreenFactory configScreenFactory = (modContainer, parent) -> new VoiceCastSettingsScreen(parent);
        container.registerExtensionPoint(IConfigScreenFactory.class, configScreenFactory);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        VoiceCastClientConfig.ensureClientFiles();
        VoiceRecognitionManager.initialize();
    }
}
