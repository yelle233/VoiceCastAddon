package com.yelle233.voicecastaddon.network;

import com.mojang.logging.LogUtils;
import com.yelle233.voicecastaddon.server.VoiceCastServerConfig;
import com.yelle233.voicecastaddon.spell.ServerSpellCaster;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ServerPayloadHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long RATE_LIMIT_MS = 50; // Minimum 50ms between casts
    private static final Map<UUID, Long> lastCastTime = new ConcurrentHashMap<>();

    public static void handle(final VoiceCastPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            // Rate limiting
            UUID playerId = player.getUUID();
            long now = System.currentTimeMillis();
            Long last = lastCastTime.get(playerId);
            if (last != null && (now - last) < RATE_LIMIT_MS) {
                LOGGER.warn("[VoiceCastAddon] Player {} is sending voice cast packets too quickly", player.getScoreboardName());
                return;
            }
            lastCastTime.put(playerId, now);

            // Validate spell ID format
            String spellId = payload.spellId();
            if (spellId == null || spellId.isEmpty() || spellId.length() > 256) {
                LOGGER.warn("[VoiceCastAddon] Invalid spell ID from player {}: {}", player.getScoreboardName(), spellId);
                return;
            }

            // Read server-side configuration (cached)
            boolean skipCastTime = VoiceCastServerConfig.getSkipCastTime();
            boolean ignoreCooldown = VoiceCastServerConfig.getIgnoreCooldown();
            boolean ignoreMana = VoiceCastServerConfig.getIgnoreManaRequirement();

            ServerSpellCaster.castByVoice(player, spellId, skipCastTime, ignoreCooldown, ignoreMana);
        }).exceptionally(ex -> {
            LOGGER.error("[VoiceCastAddon] Error handling voice cast payload", ex);
            return null;
        });
    }
}
