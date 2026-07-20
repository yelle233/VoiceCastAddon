package com.yelle233.voicecastaddon.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class VoiceCastPacket {

    private static final int MAX_SPELL_ID_LENGTH = 256;

    private final String spellId;

    public VoiceCastPacket(String spellId) {
        this.spellId = spellId;
    }

    public VoiceCastPacket(FriendlyByteBuf buf) {
        this.spellId = buf.readUtf(MAX_SPELL_ID_LENGTH);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(this.spellId, MAX_SPELL_ID_LENGTH);
    }

    public String spellId() {
        return this.spellId;
    }

    public static void handle(VoiceCastPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPayloadHandler.handle(packet, context);

        context.setPacketHandled(true);
    }
}
