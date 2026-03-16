package com.yelle233.voicecastaddon.spell;

import com.mojang.logging.LogUtils;
import io.redspace.ironsspellbooks.api.item.IScroll;
import io.redspace.ironsspellbooks.api.item.ISpellbook;
import io.redspace.ironsspellbooks.api.magic.SpellSelectionManager;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.CastSource;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import io.redspace.ironsspellbooks.api.spells.SpellSlot;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
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
            return tryCastSpell(player, stack, spellData, castingSlot, castSource, skipCastTime, ignoreCooldown, ignoreMana);
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

        if (skipCastTime && ignoreCooldown) {
            // Skip cast time and cooldown: directly cast the spell
            try {
                spell.castSpell(
                        player.level(),
                        spellLevel,
                        player,
                        castSource,
                        !ignoreMana  // true = validate mana, false = ignore mana
                );
                return true;
            } catch (Exception e) {
                // If direct cast fails, fallback to normal cast
            }
        }

        // Normal cast with cast time (attemptInitiateCast handles cooldown check internally)
        spell.attemptInitiateCast(
                stack,
                spellLevel,
                player.level(),
                player,
                castSource,
                !ignoreMana,  // true = validate mana, false = ignore mana
                castingSlot
        );

        return true;
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
