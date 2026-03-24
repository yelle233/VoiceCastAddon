package com.yelle233.voicecastaddon.spell;

import com.mojang.logging.LogUtils;
import io.redspace.ironsspellbooks.api.item.IScroll;
import io.redspace.ironsspellbooks.api.item.ISpellbook;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.api.events.SpellPreCastEvent;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.CastResult;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import io.redspace.ironsspellbooks.api.util.Utils;
import io.redspace.ironsspellbooks.capabilities.magic.CooldownInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Predicate;

public class ServerSpellCaster {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CONTINUOUS_CAST_TYPE = "CONTINUOUS";
    private static final String CURIOS_SPELLBOOK_SLOT = "spellbook";

    // Curios API reflection cache
    private static Class<?> curiosApiClass = null;
    private static boolean curiosApiChecked = false;

    public static void castByVoice(ServerPlayer player, String spokenSpellIdString, boolean skipCastTime, boolean ignoreCooldown, boolean ignoreMana) {
        ResourceLocation spokenSpellId;
        try {
            spokenSpellId = ResourceLocation.parse(spokenSpellIdString);
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

            // For scrolls and imbued weapons, ignore the locked status
            if (!ignoreLockedStatus && spellSlot.isLocked()) {
                continue;
            }

            SpellData spellData = spellSlot.spellData();
            if (spellData == null || spellData.getSpell() == null) {
                continue;
            }

            ResourceLocation actualSpellId = SpellRegistry.REGISTRY.getKey(spellData.getSpell());
            if (actualSpellId == null || !actualSpellId.equals(spokenSpellId)) {
                continue;
            }

            LOGGER.debug("[VoiceCastAddon] Found matching spell: {}", actualSpellId);
            tryCastSpell(player, stack, spellData, castingSlot, castSource, skipCastTime, ignoreCooldown, ignoreMana);
            // Return true even if cast failed - we found the spell
            // Error messages are shown by tryCastSpell
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
        var spell = spellData.getSpell();
        int spellLevel = spell.getLevelFor(spellData.getLevel(), player);
        MagicData magicData = MagicData.getPlayerMagicData(player);

        // Remove cooldown if ignoreCooldown is enabled
        CooldownInstance savedCooldown = null;
        if (ignoreCooldown) {
            savedCooldown = magicData.getPlayerCooldowns().getSpellCooldowns().get(spell.getSpellId());
            if (savedCooldown != null) {
                magicData.getPlayerCooldowns().removeCooldown(spell.getSpellId());
                magicData.getPlayerCooldowns().syncToPlayer(player);
            }
        }

        // Temporarily boost mana if ignoreMana is enabled
        float originalMana = magicData.getMana();
        if (ignoreMana && castSource.consumesMana() && !player.isCreative()) {
            float requiredMana = spell.getManaCost(spellLevel);
            if (originalMana < requiredMana) {
                magicData.setMana(requiredMana);
            }
        }

        try {
            // Check if this is a continuous cast spell (like Dragon Breath)
            // Continuous cast spells need the casting animation to work properly
            boolean isContinuousCast = isContinuousCastSpell(spell);

            LOGGER.debug("[VoiceCastAddon] Cast decision: spell={}, skipCastTime={}, isContinuousCast={}, method={}",
                spell.getSpellId(), skipCastTime, isContinuousCast,
                (skipCastTime && !isContinuousCast) ? "instant" : "normal");

            if (skipCastTime && !isContinuousCast) {
                // Direct cast without cast time (only for non-continuous spells)
                CastResult canCast = spell.canBeCastedBy(spellLevel, castSource, magicData, player);
                if (!canCast.isSuccess()) {
                    showCastResultMessage(player, canCast);
                    restoreState(magicData, spell, savedCooldown, originalMana, ignoreMana, false, player);
                    return false;
                }

                if (!spell.checkPreCastConditions(player.level(), spellLevel, player, magicData)) {
                    restoreState(magicData, spell, savedCooldown, originalMana, ignoreMana, false, player);
                    return false;
                }

                SpellPreCastEvent event = new SpellPreCastEvent(player, spell.getSpellId(), spellLevel, spell.getSchoolType(), castSource);
                if (NeoForge.EVENT_BUS.post(event).isCanceled()) {
                    restoreState(magicData, spell, savedCooldown, originalMana, ignoreMana, false, player);
                    return false;
                }

                // Initialize casting state before calling castSpell to ensure additionalCastData is properly set up
                // This prevents NPE when OnClientCastPacket tries to deserialize castData on the client
                magicData.initiateCast(spell, spellLevel, 0, castSource, castingSlot);
                magicData.setPlayerCastingItem(stack);

                spell.castSpell(player.level(), spellLevel, player, castSource, !ignoreMana);

                // Reset casting state immediately after cast
                magicData.resetCastingState();

                // Apply cooldown if not ignoring it
                if (!ignoreCooldown) {
                    int baseCooldown = spell.getSpellCooldown();
                    int cooldownTicks = Utils.applyCooldownReduction(baseCooldown, player);
                    magicData.getPlayerCooldowns().addCooldown(spell.getSpellId(), cooldownTicks, cooldownTicks);
                    magicData.getPlayerCooldowns().syncToPlayer(player);
                }

                restoreState(magicData, spell, savedCooldown, originalMana, ignoreMana, true, player);
                return true;
            } else {
                // Normal cast with cast time (for continuous spells or when skipCastTime is false)
                boolean initiated = spell.attemptInitiateCast(stack, spellLevel, player.level(), player, castSource, !ignoreMana, castingSlot);
                restoreState(magicData, spell, savedCooldown, originalMana, ignoreMana, initiated, player);
                return initiated;
            }
        } catch (Exception e) {
            LOGGER.error("[VoiceCastAddon] Failed to cast spell", e);
            restoreState(magicData, spell, savedCooldown, originalMana, ignoreMana, false, player);
            return false;
        }
    }

    private static void restoreState(MagicData magicData,
                                     io.redspace.ironsspellbooks.api.spells.AbstractSpell spell,
                                     CooldownInstance savedCooldown,
                                     float originalMana,
                                     boolean ignoreMana,
                                     boolean castSucceeded,
                                     ServerPlayer player) {
        // Restore cooldown if it was removed and cast failed
        if (savedCooldown != null && !castSucceeded) {
            magicData.getPlayerCooldowns().addCooldown(spell.getSpellId(), savedCooldown.getSpellCooldown(), savedCooldown.getCooldownRemaining());
            magicData.getPlayerCooldowns().syncToPlayer(player);
        }

        // Restore mana if it was boosted and cast failed
        if (ignoreMana && !castSucceeded) {
            magicData.setMana(originalMana);
        }
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

    /**
     * Check if a spell is a continuous cast spell (like Dragon Breath).
     * Continuous cast spells need the casting animation to work properly.
     */
    private static boolean isContinuousCastSpell(io.redspace.ironsspellbooks.api.spells.AbstractSpell spell) {
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
            Method getCuriosInventory = curiosApiClass.getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class);
            Object curiosInventoryOptional = getCuriosInventory.invoke(null, player);
            if (!(curiosInventoryOptional instanceof Optional<?> optionalInventory) || optionalInventory.isEmpty()) {
                return ItemStack.EMPTY;
            }

            Object inventory = optionalInventory.get();
            Method findFirstCurio = inventory.getClass().getMethod("findFirstCurio", Predicate.class, String.class);
            Predicate<ItemStack> spellbookPredicate = stack -> stack.getItem() instanceof ISpellbook;
            Object slotResultOptional = findFirstCurio.invoke(inventory, spellbookPredicate, CURIOS_SPELLBOOK_SLOT);
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
