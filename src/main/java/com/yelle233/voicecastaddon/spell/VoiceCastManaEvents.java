package com.yelle233.voicecastaddon.spell;

import com.yelle233.voicecastaddon.VoiceCastAddon;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = VoiceCastAddon.MODID)
public final class VoiceCastManaEvents {
    private static final Map<Key, PendingCast> PENDING_CASTS = new ConcurrentHashMap<>();

    public static void begin(ServerPlayer player, String spellId, CastSource castSource, long expiresAt) {
        PENDING_CASTS.put(new Key(player.getUUID(), spellId, castSource), new PendingCast(expiresAt));
    }

    public static void cancel(ServerPlayer player, String spellId, CastSource castSource) {
        PENDING_CASTS.remove(new Key(player.getUUID(), spellId, castSource));
    }

    @SubscribeEvent
    public static void onSpellCast(SpellOnCastEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        Key key = new Key(player.getUUID(), event.getSpellId(), event.getCastSource());
        PendingCast pendingCast = PENDING_CASTS.remove(key);
        if (pendingCast == null) {
            return;
        }

        if (player.level().getGameTime() <= pendingCast.expiresAt()) {
            event.setManaCost(0);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        UUID playerId = player.getUUID();
        long now = player.level().getGameTime();
        boolean isCasting = MagicData.getPlayerMagicData(player).isCasting();

        PENDING_CASTS.entrySet().removeIf(entry -> entry.getKey().playerId().equals(playerId)
                && (!isCasting || entry.getValue().expiresAt() < now));
    }

    private record Key(UUID playerId, String spellId, CastSource castSource) {
    }

    private record PendingCast(long expiresAt) {
    }

    private VoiceCastManaEvents() {
    }
}
