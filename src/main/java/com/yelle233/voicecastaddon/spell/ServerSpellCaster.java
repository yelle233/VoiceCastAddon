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
import io.redspace.ironsspellbooks.capabilities.magic.CooldownInstance;
import net.minecraft.ChatFormatting;
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

    public static void castByVoice(ServerPlayer player, String spokenSpellIdString, boolean skipCastTime, boolean ignoreCooldown, boolean ignoreMana) {
        ResourceLocation spokenSpellId;
        try {
            spokenSpellId = ResourceLocation.parse(spokenSpellIdString);
        } catch (Exception e) {
            return;
        }

        LOGGER.debug("[VoiceCastAddon] Attempting to cast spell: {}", spokenSpellId);

        ItemStack mainHand = player.getMainHandItem();
        LOGGER.debug("[VoiceCastAddon] Main hand: {} (empty: {}, isSpellContainer: {})",
            mainHand.getItem().getClass().getSimpleName(),
            mainHand.isEmpty(),
            !mainHand.isEmpty() && ISpellContainer.isSpellContainer(mainHand));

        if (tryCastFromStack(player, mainHand, spokenSpellId, SpellSelectionManager.MAINHAND, skipCastTime, ignoreCooldown, ignoreMana)) {
            LOGGER.debug("[VoiceCastAddon] Cast from main hand");
            return;
        }

        ItemStack offHand = player.getOffhandItem();
        LOGGER.debug("[VoiceCastAddon] Off hand: {} (empty: {}, isSpellContainer: {})",
            offHand.getItem().getClass().getSimpleName(),
            offHand.isEmpty(),
            !offHand.isEmpty() && ISpellContainer.isSpellContainer(offHand));

        if (tryCastFromStack(player, offHand, spokenSpellId, SpellSelectionManager.OFFHAND, skipCastTime, ignoreCooldown, ignoreMana)) {
            LOGGER.debug("[VoiceCastAddon] Cast from off hand");
            return;
        }

        ItemStack equippedSpellbook = findEquippedSpellbook(player);
        LOGGER.debug("[VoiceCastAddon] Equipped spellbook: {} (empty: {})",
            equippedSpellbook.isEmpty() ? "none" : equippedSpellbook.getItem().getClass().getSimpleName(),
            equippedSpellbook.isEmpty());

        if (!equippedSpellbook.isEmpty() && tryCastFromStack(player, equippedSpellbook, spokenSpellId, SpellSelectionManager.OFFHAND, skipCastTime, ignoreCooldown, ignoreMana)) {
            LOGGER.debug("[VoiceCastAddon] Cast from equipped spellbook");
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

        LOGGER.debug("[VoiceCastAddon] Checking stack: {}", stack.getItem().getClass().getSimpleName());

        ISpellContainer container = ISpellContainer.getOrCreate(stack);
        SpellSlot[] allSpells = container.getAllSpells();
        LOGGER.debug("[VoiceCastAddon] Container has {} spell slots", allSpells.length);

        CastSource castSource = resolveCastSource(stack, container);
        boolean ignoreLockedStatus = castSource == CastSource.SCROLL || castSource == CastSource.SWORD;

        for (SpellSlot spellSlot : allSpells) {
            if (spellSlot == null) {
                LOGGER.debug("[VoiceCastAddon] Skipping null slot");
                continue;
            }

            // For scrolls and imbued weapons, ignore the locked status
            if (!ignoreLockedStatus && spellSlot.isLocked()) {
                LOGGER.debug("[VoiceCastAddon] Skipping locked slot");
                continue;
            }

            SpellData spellData = spellSlot.spellData();
            if (spellData == null || spellData.getSpell() == null) {
                LOGGER.debug("[VoiceCastAddon] Skipping slot with null spell data");
                continue;
            }

            ResourceLocation actualSpellId = SpellRegistry.REGISTRY.getKey(spellData.getSpell());
            LOGGER.debug("[VoiceCastAddon] Found spell: {} (looking for: {}, isLocked: {})",
                actualSpellId, spokenSpellId, spellSlot.isLocked());

            if (actualSpellId == null || !actualSpellId.equals(spokenSpellId)) {
                continue;
            }

            LOGGER.debug("[VoiceCastAddon] Match found! Attempting to cast...");
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
            if (skipCastTime) {
                // Direct cast without cast time
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

                spell.castSpell(player.level(), spellLevel, player, castSource, !ignoreMana);

                // Apply cooldown if not ignoring it
                if (!ignoreCooldown) {
                    int cooldownTicks = spell.getSpellCooldown();
                    magicData.getPlayerCooldowns().addCooldown(spell.getSpellId(), cooldownTicks, cooldownTicks);
                    magicData.getPlayerCooldowns().syncToPlayer(player);
                }

                restoreState(magicData, spell, savedCooldown, originalMana, ignoreMana, true, player);
                return true;
            } else {
                // Normal cast with cast time
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

    private static ItemStack findEquippedSpellbook(ServerPlayer player) {
        try {
            Class<?> curiosApiClass = Class.forName("top.theillusivec4.curios.api.CuriosApi");
            Method getCuriosInventory = curiosApiClass.getMethod("getCuriosInventory", net.minecraft.world.entity.LivingEntity.class);
            Object curiosInventoryOptional = getCuriosInventory.invoke(null, player);
            if (!(curiosInventoryOptional instanceof Optional<?> optionalInventory) || optionalInventory.isEmpty()) {
                return ItemStack.EMPTY;
            }

            Object inventory = optionalInventory.get();
            Method findFirstCurio = inventory.getClass().getMethod("findFirstCurio", Predicate.class, String.class);
            Predicate<ItemStack> spellbookPredicate = stack -> stack.getItem() instanceof ISpellbook;
            Object slotResultOptional = findFirstCurio.invoke(inventory, spellbookPredicate, "spellbook");
            if (!(slotResultOptional instanceof Optional<?> optionalSlotResult) || optionalSlotResult.isEmpty()) {
                return ItemStack.EMPTY;
            }

            Object slotResult = optionalSlotResult.get();
            Method stackMethod = slotResult.getClass().getMethod("stack");
            Object stack = stackMethod.invoke(slotResult);
            return stack instanceof ItemStack itemStack ? itemStack : ItemStack.EMPTY;
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }

    private ServerSpellCaster() {
    }
}
