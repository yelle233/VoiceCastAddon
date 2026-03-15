package com.yelle233.voicecastaddon.network;

import com.yelle233.voicecastaddon.server.VoiceCastServerConfig;
import com.yelle233.voicecastaddon.spell.ServerSpellCaster;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ServerPayloadHandler {

    public static void handle(final VoiceCastPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            // Read server-side configuration
            boolean skipCastTime = VoiceCastServerConfig.getSkipCastTime();
            boolean ignoreCooldown = VoiceCastServerConfig.getIgnoreCooldown();
            boolean ignoreMana = VoiceCastServerConfig.getIgnoreManaRequirement();

            ServerSpellCaster.castByVoice(player, payload.spellId(), skipCastTime, ignoreCooldown, ignoreMana);
        }).exceptionally(ex -> {
            ex.printStackTrace();
            return null;
        });
    }
}
