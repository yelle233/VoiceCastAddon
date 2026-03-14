package com.yelle233.voicecastaddon.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.yelle233.voicecastaddon.client.SpellNameHelper;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class ListSpellsCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("voicecast")
                        .then(Commands.literal("list")
                                .executes(ListSpellsCommand::listSpells)
                        )
        );
    }

    private static int listSpells(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        List<ResourceLocation> spellIds = new ArrayList<>();
        for (AbstractSpell spell : SpellRegistry.REGISTRY) {
            ResourceLocation spellId = SpellRegistry.REGISTRY.getKey(spell);
            if (spellId != null) {
                spellIds.add(spellId);
            }
        }

        spellIds.sort((a, b) -> {
            int nsCompare = a.getNamespace().compareTo(b.getNamespace());
            if (nsCompare != 0) {
                return nsCompare;
            }
            return a.getPath().compareTo(b.getPath());
        });

        source.sendSuccess(() -> Component.literal("=== ")
                .append(Component.translatable("voicecastaddon.command.list.title")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                .append(" ==="), false);

        source.sendSuccess(() -> Component.translatable("voicecastaddon.command.list.count", spellIds.size())
                .withStyle(ChatFormatting.GRAY), false);

        source.sendSuccess(() -> Component.literal(""), false);

        String currentNamespace = "";
        for (ResourceLocation spellId : spellIds) {
            if (!spellId.getNamespace().equals(currentNamespace)) {
                currentNamespace = spellId.getNamespace();
                String finalNamespace = currentNamespace;
                source.sendSuccess(() -> Component.literal("--- ")
                        .append(Component.literal(finalNamespace)
                                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                        .append(" ---"), false);
            }

            AbstractSpell spell = SpellRegistry.REGISTRY.get(spellId);
            Component spellName = SpellNameHelper.getSpellDisplayName(spellId);

            Component message = Component.literal("  • ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(spellId.toString())
                            .withStyle(Style.EMPTY
                                    .withColor(ChatFormatting.YELLOW)
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, spellId.toString()))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                            Component.translatable("voicecastaddon.command.list.click_to_copy")))))
                    .append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(spellName.copy().withStyle(ChatFormatting.WHITE));

            source.sendSuccess(() -> message, false);
        }

        source.sendSuccess(() -> Component.literal(""), false);
        source.sendSuccess(() -> Component.translatable("voicecastaddon.command.list.footer")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);

        return spellIds.size();
    }
}
