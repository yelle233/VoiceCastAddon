package com.yelle233.voicecastaddon.spell;

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

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Predicate;

public class ServerSpellCaster {
    public static void castByVoice(ServerPlayer player, String spokenSpellIdString) {
        ResourceLocation spokenSpellId;
        try {
            spokenSpellId = ResourceLocation.parse(spokenSpellIdString);
        } catch (Exception e) {
            player.sendSystemMessage(Component.translatable("voicecastaddon.server.invalid_spell_id", spokenSpellIdString));
            return;
        }

        if (tryCastFromStack(player, player.getMainHandItem(), spokenSpellId, SpellSelectionManager.MAINHAND)) {
            return;
        }

        if (tryCastFromStack(player, player.getOffhandItem(), spokenSpellId, SpellSelectionManager.OFFHAND)) {
            return;
        }

        ItemStack equippedSpellbook = findEquippedSpellbook(player);
        if (!equippedSpellbook.isEmpty() && tryCastFromStack(player, equippedSpellbook, spokenSpellId, SpellSelectionManager.OFFHAND)) {
            return;
        }

        player.sendSystemMessage(Component.translatable("voicecastaddon.server.spell_not_found"));
    }

    private static boolean tryCastFromStack(ServerPlayer player,
                                            ItemStack stack,
                                            ResourceLocation spokenSpellId,
                                            String castingSlot) {
        if (stack.isEmpty() || !ISpellContainer.isSpellContainer(stack)) {
            return false;
        }

        ISpellContainer container = ISpellContainer.getOrCreate(stack);
        for (SpellSlot spellSlot : container.getAllSpells()) {
            if (spellSlot == null || spellSlot.isLocked()) {
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

            return tryCastSpell(player, stack, spellData, castingSlot);
        }

        return false;
    }

    private static boolean tryCastSpell(ServerPlayer player,
                                        ItemStack stack,
                                        SpellData spellData,
                                        String castingSlot) {
        var spell = spellData.getSpell();
        int spellLevel = spell.getLevelFor(spellData.getLevel(), player);
        CastSource castSource = resolveCastSource(stack);

        boolean success = spell.attemptInitiateCast(
                stack,
                spellLevel,
                player.level(),
                player,
                castSource,
                false,
                castingSlot
        );

        if (!success) {
            player.sendSystemMessage(Component.translatable("voicecastaddon.server.cast_failed"));
        }

        return success;
    }

    private static CastSource resolveCastSource(ItemStack stack) {
        if (stack.getItem() instanceof ISpellbook) {
            return CastSource.SPELLBOOK;
        }
        return CastSource.SCROLL;
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
