package com.yelle233.voicecastaddon.compat;

import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class IsbApiCompat {
    private static final Method GET_ALL_SPELLS = findGetAllSpellsMethod();

    public static List<SpellData> getAllSpellData(ISpellContainer container) {
        try {
            Object result = GET_ALL_SPELLS.invoke(container);
            if (result == null || !result.getClass().isArray()) {
                throw new IllegalStateException("Iron's Spells getAllSpells returned a non-array value");
            }

            int length = Array.getLength(result);
            List<SpellData> spells = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                SpellData spellData = unwrapSpellData(Array.get(result, index));
                if (spellData != null) {
                    spells.add(spellData);
                }
            }
            return spells;
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Could not access Iron's Spells getAllSpells", e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Iron's Spells getAllSpells failed", e.getCause());
        }
    }

    public static void syncMana(MagicData magicData, ServerPlayer player) {
        ManaSyncHolder.SYNCER.sync(magicData, player);
    }

    private static Method findGetAllSpellsMethod() {
        try {
            return ISpellContainer.class.getMethod("getAllSpells");
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Unsupported Iron's Spells API: getAllSpells is missing", e);
        }
    }

    private static SpellData unwrapSpellData(Object entry) {
        if (entry instanceof SpellData spellData) {
            return spellData;
        }
        if (entry == null) {
            return null;
        }

        try {
            Object value = entry.getClass().getMethod("spellData").invoke(entry);
            return value instanceof SpellData spellData ? spellData : null;
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalStateException("Unsupported Iron's Spells spell slot type: " + entry.getClass().getName(), e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Could not read Iron's Spells spell slot", e.getCause());
        }
    }

    private static ManaSyncer createManaSyncer() {
        ReflectiveOperationException modernFailure;
        try {
            Class<?> packetClass = Class.forName("io.redspace.ironsspellbooks.network.SyncManaPacket");
            Constructor<?> constructor = packetClass.getConstructor(MagicData.class);
            Class<?> distributorClass = Class.forName("io.redspace.ironsspellbooks.setup.PacketDistributor");
            Method sendToPlayer = distributorClass.getMethod("sendToPlayer", ServerPlayer.class, Object.class);
            return (magicData, player) -> invokeManaSync(constructor, sendToPlayer, false, magicData, player);
        } catch (ReflectiveOperationException e) {
            modernFailure = e;
        }

        try {
            Class<?> packetClass = Class.forName("io.redspace.ironsspellbooks.network.ClientboundSyncMana");
            Constructor<?> constructor = packetClass.getConstructor(MagicData.class);
            Class<?> messagesClass = Class.forName("io.redspace.ironsspellbooks.setup.Messages");
            Method sendToPlayer = messagesClass.getMethod("sendToPlayer", Object.class, ServerPlayer.class);
            return (magicData, player) -> invokeManaSync(constructor, sendToPlayer, true, magicData, player);
        } catch (ReflectiveOperationException e) {
            IllegalStateException failure = new IllegalStateException("Unsupported Iron's Spells mana sync API", e);
            failure.addSuppressed(modernFailure);
            throw failure;
        }
    }

    private static void invokeManaSync(Constructor<?> constructor,
                                       Method sendToPlayer,
                                       boolean packetFirst,
                                       MagicData magicData,
                                       ServerPlayer player) {
        try {
            Object packet = constructor.newInstance(magicData);
            if (packetFirst) {
                sendToPlayer.invoke(null, packet, player);
            } else {
                sendToPlayer.invoke(null, player, packet);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not synchronize Iron's Spells mana", e);
        }
    }

    private interface ManaSyncer {
        void sync(MagicData magicData, ServerPlayer player);
    }

    private static final class ManaSyncHolder {
        private static final ManaSyncer SYNCER = createManaSyncer();
    }

    private IsbApiCompat() {
    }
}
