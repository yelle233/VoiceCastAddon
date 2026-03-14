package com.yelle233.voicecastaddon.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.yelle233.voicecastaddon.client.SpellNameHelper;
import com.yelle233.voicecastaddon.client.VoiceCastClientConfig;
import com.yelle233.voicecastaddon.client.VoiceSpellAliases;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public class GenerateConfigCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("voicecast")
                        .then(Commands.literal("generate")
                                .executes(GenerateConfigCommand::generateConfig)
                        )
        );
    }

    private static int generateConfig(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        source.sendSuccess(() -> Component.translatable("voicecastaddon.command.generate.start")
                .withStyle(ChatFormatting.YELLOW), false);

        try {
            Map<String, List<String>> currentAliases = VoiceCastClientConfig.loadSpellAliases();
            Map<String, List<String>> newAliases = new LinkedHashMap<>(currentAliases);

            int addedCount = 0;
            int skippedCount = 0;

            for (AbstractSpell spell : SpellRegistry.REGISTRY) {
                ResourceLocation spellId = SpellRegistry.REGISTRY.getKey(spell);
                if (spellId == null) {
                    continue;
                }

                String spellIdString = spellId.toString();

                if (currentAliases.containsKey(spellIdString)) {
                    skippedCount++;
                    continue;
                }

                List<String> aliases = generateAliasesForSpell(spellId);
                newAliases.put(spellIdString, aliases);
                addedCount++;
            }

            if (addedCount > 0) {
                VoiceCastClientConfig.saveSpellAliases(newAliases);

                int finalAddedCount = addedCount;
                source.sendSuccess(() -> Component.translatable("voicecastaddon.command.generate.success", finalAddedCount)
                        .withStyle(ChatFormatting.GREEN), false);

                VoiceSpellAliases.reload();
                source.sendSuccess(() -> Component.translatable("voicecastaddon.command.generate.reloaded")
                        .withStyle(ChatFormatting.GREEN), false);
            } else {
                source.sendSuccess(() -> Component.translatable("voicecastaddon.command.generate.no_new")
                        .withStyle(ChatFormatting.GRAY), false);
            }

            int finalSkippedCount = skippedCount;
            source.sendSuccess(() -> Component.translatable("voicecastaddon.command.generate.skipped", finalSkippedCount)
                    .withStyle(ChatFormatting.GRAY), false);

            return addedCount;

        } catch (Exception e) {
            source.sendFailure(Component.translatable("voicecastaddon.command.generate.error", e.getMessage())
                    .withStyle(ChatFormatting.RED));
            e.printStackTrace();
            return 0;
        }
    }

    private static List<String> generateAliasesForSpell(ResourceLocation spellId) {
        List<String> aliases = new ArrayList<>();

        Component displayName = SpellNameHelper.getSpellDisplayName(spellId);
        String displayNameString = displayName.getString();

        String englishName = spellId.getPath().replace("_", " ");

        aliases.add(englishName);

        if (!displayNameString.equals(englishName) && !displayNameString.equals(spellId.toString())) {
            aliases.add(displayNameString);
        }

        return aliases;
    }
}
