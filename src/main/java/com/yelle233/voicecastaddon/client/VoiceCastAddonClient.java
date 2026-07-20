package com.yelle233.voicecastaddon.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

public final class VoiceCastAddonClient {

    private VoiceCastAddonClient() {
    }

    public static void init() {
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> newSettingsScreen(parent)));
    }

    private static Screen newSettingsScreen(Screen parent) {
        return new VoiceCastSettingsScreen(parent);
    }
}
