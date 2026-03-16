package com.commandpatcher;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Main mod entrypoint. Command blocks should be converted BEFORE loading
 * the world using the "Convert Commands" button on the Edit World screen.
 *
 * In-game commands are provided for manual re-scanning if needed.
 */
public class CommandPatcher implements ModInitializer {
    public static final String MOD_ID = "commandpatcher";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[CommandPatcher] Mod initialized.");

        // ── Commands ────────────────────────────────────────────────────────
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("commandpatcher")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("scan")
                    .executes(context -> {
                        ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                        context.getSource().sendFeedback(
                            () -> Text.literal("[CommandPatcher] Scanning all worlds...")
                                .formatted(Formatting.YELLOW), false);
                        context.getSource().getServer().getWorlds().forEach(w ->
                            CommandBlockScanner.scanAndPatch(w, player));
                        return 1;
                    })
                )
                .then(CommandManager.literal("scanchunk")
                    .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                        .executes(context -> {
                            ServerPlayerEntity player = context.getSource().getPlayerOrThrow();
                            ServerWorld world = context.getSource().getWorld();
                            BlockPos blockPos = BlockPosArgumentType.getBlockPos(context, "pos");
                            ChunkPos cp = new ChunkPos(blockPos);

                            player.sendMessage(Text.literal(
                                "[CommandPatcher] Scanning chunk [" + cp.x + ", " + cp.z
                                + "] containing block " + blockPos.getX() + " " + blockPos.getY() + " " + blockPos.getZ() + "...")
                                .formatted(Formatting.YELLOW), false);

                            if (world.getChunk(cp.x, cp.z) instanceof WorldChunk chunk) {
                                int found = CommandBlockScanner.scanChunkWithChat(world, chunk, List.of(player));
                                if (found == 0) {
                                    player.sendMessage(Text.literal(
                                        "[CommandPatcher] No command blocks found in chunk [" + cp.x + ", " + cp.z + "].")
                                        .formatted(Formatting.GREEN), false);
                                }
                            } else {
                                player.sendMessage(Text.literal(
                                    "[CommandPatcher] Could not load chunk [" + cp.x + ", " + cp.z + "].")
                                    .formatted(Formatting.RED), false);
                            }
                            return 1;
                        })
                    )
                )
            );
        });
    }
}
