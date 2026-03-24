package com.yelle233.voicecastaddon.client;

import com.yelle233.voicecastaddon.VoiceCastAddon;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class ClientKeyMappings {
    public static final KeyMapping VOICE_CAST_KEY = new KeyMapping(
            "key." + VoiceCastAddon.MODID + ".voice_cast",
            GLFW.GLFW_KEY_T,
            "key.categories." + VoiceCastAddon.MODID
    );

    public static final KeyMapping OPEN_SETTINGS_KEY = new KeyMapping(
            "key." + VoiceCastAddon.MODID + ".open_settings",
            GLFW.GLFW_KEY_UNKNOWN,  // No default binding
            "key.categories." + VoiceCastAddon.MODID
    );

    private ClientKeyMappings() {
    }
}