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

    private ClientKeyMappings() {
    }
}