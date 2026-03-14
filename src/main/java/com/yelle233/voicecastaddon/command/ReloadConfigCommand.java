package com.yelle233.voicecastaddon.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.yelle233.voicecastaddon.client.VoiceSpellAliases;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class ReloadConfigCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("voicecast")
                        .then(Commands.literal("reload")
                                .executes(ReloadConfigCommand::reloadConfig)
                        )
        );
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            VoiceSpellAliases.reload();

            source.sendSuccess(() -> Component.translatable("voicecastaddon.command.reload.success")
                    .withStyle(ChatFormatting.GREEN), false);

            return 1;

        } catch (Exception e) {
            source.sendFailure(Component.translatable("voicecastaddon.command.reload.error", e.getMessage())
                    .withStyle(ChatFormatting.RED));
            e.printStackTrace();
            return 0;
        }
    }
}
