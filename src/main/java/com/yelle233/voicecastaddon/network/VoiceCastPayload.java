package com.yelle233.voicecastaddon.network;

import com.yelle233.voicecastaddon.VoiceCastAddon;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record VoiceCastPayload(String spellId, boolean skipCastTime, boolean ignoreCooldown) implements CustomPacketPayload {
    public static final Type<VoiceCastPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(VoiceCastAddon.MODID, "voice_cast"));

    public static final StreamCodec<ByteBuf, VoiceCastPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    VoiceCastPayload::spellId,
                    ByteBufCodecs.BOOL,
                    VoiceCastPayload::skipCastTime,
                    ByteBufCodecs.BOOL,
                    VoiceCastPayload::ignoreCooldown,
                    VoiceCastPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
