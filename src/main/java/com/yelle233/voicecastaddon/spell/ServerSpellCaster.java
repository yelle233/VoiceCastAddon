package com.yelle233.voicecastaddon.spell;

import com.mojang.logging.LogUtils;
import com.yelle233.voicecastaddon.compat.IsbSpells;
import io.redspace.ironsspellbooks.api.item.IScroll;
import io.redspace.ironsspellbooks.api.item.ISpellbook;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastResult;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.CooldownInstance;
import io.redspace.ironsspellbooks.network.SyncManaPacket;
import io.redspace.ironsspellbooks.setup.PacketDistributor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraftforge.common.MinecraftForge;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Predicate;

public class ServerSpellCaster {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CONTINUOUS_CAST_TYPE = "CONTINUOUS";
    private static Class<?> curiosApiClass = null;
    private static boolean curiosApiChecked = false;

    public static void castByVoice(ServerPlayer player, String spokenSpellIdString, boolean skipCastTime, boolean ignoreCooldown, boolean ignoreMana) {
        ResourceLocation spokenSpellId;
        try {

            spokenSpellId = new ResourceLocation(spokenSpellIdString);
        } catch (Exception e) {
            return;
        }

        LOGGER.debug("[VoiceCastAddon] Attempting to cast spell: {}", spokenSpellId);

        ItemStack mainHand = player.getMainHandItem();
        if (tryCastFromStack(player, mainHand, spokenSpellId, SpellSelectionManager.MAINHAND, skipCastTime, ignoreCooldown, ignoreMana)) {
            return;
        }

        ItemStack offHand = player.getOffhandItem();
        if (tryCastFromStack(player, offHand, spokenSpellId, SpellSelectionManager.OFFHAND, skipCastTime, ignoreCooldown, ignoreMana)) {
            return;
        }

        ItemStack equippedSpellbook = findEquippedSpellbook(player);
        if (!equippedSpellbook.isEmpty() && tryCastFromStack(player, equippedSpellbook, spokenSpellId, SpellSelectionManager.OFFHAND, skipCastTime, ignoreCooldown, ignoreMana)) {
            return;
        }

        LOGGER.debug("[VoiceCastAddon] Spell not found in any container");
        player.displayClientMessage(Component.translatable("voicecastaddon.server.spell_not_found"), true);
    }

    private static boolean tryCastFromStack(ServerPlayer player,
                                            ItemStack stack,
                                            ResourceLocation spokenSpellId,
                                            String castingSlot,
                                            boolean skipCastTime,
                                            boolean ignoreCooldown,
                                            boolean ignoreMana) {
        if (stack.isEmpty() || !ISpellContainer.isSpellContainer(stack)) {
            return false;
        }

        ISpellContainer container = ISpellContainer.getOrCreate(stack);

        SpellSlot[] allSpells = container.getAllSpells();

        CastSource castSource = resolveCastSource(stack, container);
        boolean ignoreLockedStatus = castSource == CastSource.SCROLL || castSource == CastSource.SWORD;

        for (SpellSlot spellSlot : allSpells) {
            if (spellSlot == null) {
                continue;
            }

            SpellData spellData = spellSlot.spellData();
            if (spellData == null || spellData.getSpell() == null) {
                continue;
            }

            if (!ignoreLockedStatus && spellSlot.isLocked()) {
                continue;
            }

            ResourceLocation actualSpellId = IsbSpells.idOf(spellData.getSpell());
            if (actualSpellId == null || !actualSpellId.equals(spokenSpellId)) {
                continue;
            }

            LOGGER.debug("[VoiceCastAddon] Found matching spell: {}", actualSpellId);
            tryCastSpell(player, stack, spellData, castingSlot, castSource, skipCastTime, ignoreCooldown, ignoreMana);

            return true;
        }

        return false;
    }

    private static boolean tryCastSpell(ServerPlayer player,
                                        ItemStack stack,
                                        SpellData spellData,
                                        String castingSlot,
                                        CastSource castSource,
                                        boolean skipCastTime,
                                        boolean ignoreCooldown,
                                        boolean ignoreMana) {
        AbstractSpell spell = spellData.getSpell();
        int spellLevel = spell.getLevelFor(spellData.getLevel(), player);
        MagicData magicData = MagicData.getPlayerMagicData(player);
        boolean ignoreManaCost = ignoreMana && castSource.consumesMana();

        CooldownInstance savedCooldown = null;
        if (ignoreCooldown) {
            savedCooldown = magicData.getPlayerCooldowns().getSpellCooldowns().get(spell.getSpellId());
            if (savedCooldown != null) {
                magicData.getPlayerCooldowns().removeCooldown(spell.getSpellId());
                magicData.getPlayerCooldowns().syncToPlayer(player);
            }
        }

        float originalMana = magicData.getMana();
        if (ignoreManaCost && !player.isCreative()) {
            float requiredMana = spell.getManaCost(spellLevel);
            if (originalMana < requiredMana) {
                magicData.setMana(requiredMana);
            }
        }

        try {

            boolean isContinuousCast = isContinuousCastSpell(spell);

            LOGGER.debug("[VoiceCastAddon] Cast decision: spell={}, skipCastTime={}, isContinuousCast={}, method={}",
                    spell.getSpellId(), skipCastTime, isContinuousCast,
                    (skipCastTime && !isContinuousCast) ? "instant" : "normal");

            if (skipCastTime && !isContinuousCast) {

                CastResult canCast = spell.canBeCastedBy(spellLevel, castSource, magicData, player);
                if (!canCast.isSuccess()) {
                    showCastResultMessage(player, canCast);
                    restoreState(magicData, spell, savedCooldown, originalMana, ignoreManaCost, false, player);
                    return false;
                }

                if (!spell.checkPreCastConditions(player.level(), spellLevel, player, magicData)) {
                    restoreState(magicData, spell, savedCooldown, originalMana, ignoreManaCost, false, player);
                    return false;
                }

                SpellPreCastEvent event = new SpellPreCastEvent(player, spell.getSpellId(), spellLevel, spell.getSchoolType(), castSource);

                if (MinecraftForge.EVENT_BUS.post(event)) {
                    restoreState(magicData, spell, savedCooldown, originalMana, ignoreManaCost, false, player);
                    return false;
                }

                magicData.initiateCast(spell, spellLevel, 0, castSource, castingSlot);
                magicData.setPlayerCastingItem(stack);

                if (ignoreManaCost) {
                    VoiceCastManaEvents.begin(player, spell.getSpellId(), castSource, player.level().getGameTime() + 20L);
                }

                spell.castSpell(player.level(), spellLevel, player, castSource, !ignoreMana);

                magicData.resetCastingState();

                if (!ignoreCooldown) {
                    int baseCooldown = spell.getSpellCooldown();
                    int cooldownTicks = Utils.applyCooldownReduction(baseCooldown, player);
                    magicData.getPlayerCooldowns().addCooldown(spell.getSpellId(), cooldownTicks, cooldownTicks);
                    magicData.getPlayerCooldowns().syncToPlayer(player);
                }

                restoreState(magicData, spell, savedCooldown, originalMana, ignoreManaCost, true, player);
                return true;
            } else {

                if (ignoreManaCost) {
                    VoiceCastManaEvents.begin(player, spell.getSpellId(), castSource, getManaIgnoreExpiry(player, spell, spellLevel));
                }

                boolean initiated = spell.attemptInitiateCast(stack, spellLevel, player.level(), player, castSource, !ignoreMana, castingSlot);
                if (!initiated && ignoreManaCost) {
                    VoiceCastManaEvents.cancel(player, spell.getSpellId(), castSource);
                }
                restoreState(magicData, spell, savedCooldown, originalMana, ignoreManaCost, initiated, player);
                return initiated;
            }
        } catch (Exception e) {
            LOGGER.error("[VoiceCastAddon] Failed to cast spell", e);
            if (ignoreManaCost) {
                VoiceCastManaEvents.cancel(player, spell.getSpellId(), castSource);
            }
            restoreState(magicData, spell, savedCooldown, originalMana, ignoreManaCost, false, player);
            return false;
        }
    }

    private static void restoreState(MagicData magicData,
                                     AbstractSpell spell,
                                     CooldownInstance savedCooldown,
                                     float originalMana,
                                     boolean restoreMana,
                                     boolean castSucceeded,
                                     ServerPlayer player) {

        if (savedCooldown != null && !castSucceeded) {
            magicData.getPlayerCooldowns().addCooldown(spell.getSpellId(), savedCooldown.getSpellCooldown(), savedCooldown.getCooldownRemaining());
            magicData.getPlayerCooldowns().syncToPlayer(player);
        }

        if (restoreMana) {
            restoreMana(magicData, originalMana, player);
        }
    }

    private static long getManaIgnoreExpiry(ServerPlayer player, AbstractSpell spell, int spellLevel) {
        int castTime = Math.max(0, spell.getEffectiveCastTime(spellLevel, player));
        return player.level().getGameTime() + Math.max(40L, castTime + 80L);
    }

    private static void restoreMana(MagicData magicData, float originalMana, ServerPlayer player) {
        if (Float.compare(magicData.getMana(), originalMana) == 0) {
            return;
        }

        magicData.setMana(originalMana);
        PacketDistributor.sendToPlayer(player, new SyncManaPacket(magicData));
    }

    private static void showCastResultMessage(ServerPlayer player, CastResult castResult) {
        if (castResult.message != null) {
            player.displayClientMessage(castResult.message, true);
        }
    }

    private static CastSource resolveCastSource(ItemStack stack, ISpellContainer container) {
        if (stack.getItem() instanceof ISpellbook) {
            return CastSource.SPELLBOOK;
        }
        if (stack.getItem() instanceof IScroll) {
            return CastSource.SCROLL;
        }
        if (stack.getItem() instanceof SwordItem) {
            return CastSource.SWORD;
        }
        if (hasOnlyLockedSpells(container)) {
            return CastSource.SCROLL;
        }
        return CastSource.SPELLBOOK;
    }

    private static boolean hasOnlyLockedSpells(ISpellContainer container) {
        boolean sawSpell = false;

        for (SpellSlot spellSlot : container.getAllSpells()) {
            if (spellSlot == null || spellSlot.spellData() == null || spellSlot.spellData().getSpell() == null) {
                continue;
            }

            sawSpell = true;
            if (!spellSlot.isLocked()) {
                return false;
            }
        }

        return sawSpell;
    }

    private static boolean isContinuousCastSpell(AbstractSpell spell) {
        try {
            Method getCastType = spell.getClass().getMethod("getCastType");
            Object castType = getCastType.invoke(spell);
            if (castType != null && CONTINUOUS_CAST_TYPE.equals(castType.toString())) {
                LOGGER.debug("[VoiceCastAddon] Spell {} is CONTINUOUS cast type", spell.getSpellId());
                return true;
            }
        } catch (Exception e) {
            LOGGER.debug("[VoiceCastAddon] Could not check getCastType for {}: {}", spell.getSpellId(), e.getMessage());
        }
        return false;
    }

    private static ItemStack findEquippedSpellbook(ServerPlayer player) {
        if (!curiosApiChecked) {
            try {
                curiosApiClass = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            } catch (ClassNotFoundException e) {
                LOGGER.debug("[VoiceCastAddon] Curios API not found, spellbook slot detection disabled");
            } catch (Exception e) {
                LOGGER.error("[VoiceCastAddon] Unexpected error loading Curios API", e);
            }
            curiosApiChecked = true;
        }

        if (curiosApiClass == null) {
            return ItemStack.EMPTY;
        }

        try {
            Method getCuriosHelper = curiosApiClass.getMethod("getCuriosHelper");
            Object curiosHelper = getCuriosHelper.invoke(null);
            Method findFirstCurio = curiosHelper.getClass().getMethod("findFirstCurio", net.minecraft.world.entity.LivingEntity.class, Predicate.class);
            Predicate<ItemStack> spellbookPredicate = stack -> stack.getItem() instanceof ISpellbook;
            Object slotResultOptional = findFirstCurio.invoke(curiosHelper, player, spellbookPredicate);
            if (!(slotResultOptional instanceof Optional<?> optionalSlotResult) || optionalSlotResult.isEmpty()) {
                return ItemStack.EMPTY;
            }

            Object slotResult = optionalSlotResult.get();
            Method stackMethod = slotResult.getClass().getMethod("stack");
            Object stack = stackMethod.invoke(slotResult);
            return stack instanceof ItemStack itemStack ? itemStack : ItemStack.EMPTY;
        } catch (Exception e) {
            LOGGER.debug("[VoiceCastAddon] Could not access Curios inventory: {}", e.getMessage());
            return ItemStack.EMPTY;
        }
    }

    private ServerSpellCaster() {
    }
}
