package com.commandpatcher;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.CommandBlockBlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.BlockPos;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class CommandBlockScanner {

    private static final Pattern OLD_CLICK_EVENT  = Pattern.compile("\"clickEvent\"");
    private static final Pattern OLD_HOVER_EVENT  = Pattern.compile("\"hoverEvent\"");
    private static final Pattern OLD_FILL_DATA    = Pattern.compile("(?:fill|setblock|clone)\\s.*minecraft:\\w+\\s+\\d{1,2}(?:\\s|$)");

    private static boolean looksProblematic(String cmd) {
        String lower = cmd.toLowerCase();
        return OLD_CLICK_EVENT.matcher(cmd).find()
            || OLD_HOVER_EVENT.matcher(cmd).find()
            || OLD_FILL_DATA.matcher(lower).find();
    }

    private record PendingPatch(
        CommandBlockBlockEntity commandBlock,
        CommandMigrator.MigrationResult result
    ) {}

    private record SuspiciousBlock(
        CommandBlockBlockEntity commandBlock,
        String command
    ) {}

    /**
     * Scans a chunk for command blocks. Reports every command block to chat
     * with its coordinates and command. Migrates any that need it and shows
     * before/after. Returns the number of command blocks found.
     */
    public static int scanChunkWithChat(ServerWorld world, WorldChunk chunk, List<ServerPlayerEntity> players) {
        List<BlockEntity> allBlockEntities = new ArrayList<>(chunk.getBlockEntities().values());
        int commandBlockCount = 0;

        for (BlockEntity blockEntity : allBlockEntities) {
            if (!(blockEntity instanceof CommandBlockBlockEntity commandBlock)) continue;
            commandBlockCount++;

            String originalCommand = commandBlock.getCommandExecutor().getCommand();
            BlockPos pos = commandBlock.getPos();
            String coords = pos.getX() + " " + pos.getY() + " " + pos.getZ();

            if (originalCommand == null || originalCommand.isBlank()) {
                // Empty command block — report it but skip migration
                for (ServerPlayerEntity player : players) {
                    player.sendMessage(Text.literal(
                        "[CommandPatcher] Command block at " + coords + " (empty)")
                        .formatted(Formatting.GRAY), false);
                }
                continue;
            }

            CommandMigrator.MigrationResult result = CommandMigrator.migrate(originalCommand);

            if (result.wasModified) {
                // Migrate and show before/after
                commandBlock.getCommandExecutor().setCommand(result.migrated);
                commandBlock.markDirty();

                boolean hasWarning = result.changes.stream()
                    .anyMatch(c -> c.startsWith("WARNING") || c.contains("MANUAL_MIGRATION_NEEDED") || c.contains("manual review"));

                CommandPatcher.LOGGER.info("[CommandPatcher] Patched at {} in {}: {}",
                    pos, world.getRegistryKey().getValue(), String.join("; ", result.changes));

                for (ServerPlayerEntity player : players) {
                    if (hasWarning) {
                        player.sendMessage(Text.literal(
                            "[CommandPatcher] Migrated (needs review) at " + coords)
                            .formatted(Formatting.RED), false);
                    } else {
                        player.sendMessage(Text.literal(
                            "[CommandPatcher] Migrated at " + coords)
                            .formatted(Formatting.GREEN), false);
                    }
                    player.sendMessage(Text.literal(
                        "  Before: " + result.original)
                        .formatted(Formatting.GRAY), false);
                    player.sendMessage(Text.literal(
                        "  After:  " + result.migrated)
                        .formatted(Formatting.AQUA), false);
                }
            } else {
                // Already modern — just report its existence
                for (ServerPlayerEntity player : players) {
                    player.sendMessage(Text.literal(
                        "[CommandPatcher] Command block at " + coords)
                        .formatted(Formatting.DARK_GREEN), false);
                    player.sendMessage(Text.literal(
                        "  Command: " + originalCommand)
                        .formatted(Formatting.GRAY), false);
                }
            }
        }

        return commandBlockCount;
    }

    /**
     * Scans a chunk silently (no chat). Used as fallback.
     */
    public static int scanChunk(ServerWorld world, WorldChunk chunk) {
        return scanChunkWithChat(world, chunk, List.of());
    }

    /**
     * Full scan + patch across all view-distance and force-loaded chunks.
     * Used by /commandpatcher scan command.
     */
    public static void scanAndPatch(ServerWorld world, ServerPlayerEntity player) {
        Set<ChunkPos> positions = new HashSet<>();
        world.getForcedChunks().forEach(longPos -> positions.add(new ChunkPos(longPos)));

        int viewDist = world.getServer().getPlayerManager().getViewDistance();
        ChunkPos center = player.getChunkPos();
        for (int dx = -viewDist; dx <= viewDist; dx++)
            for (int dz = -viewDist; dz <= viewDist; dz++)
                positions.add(new ChunkPos(center.x + dx, center.z + dz));

        int[] counts = scanAndPatchAll(world, positions, player);
        CommandPatcher.LOGGER.info("[CommandPatcher] scanAndPatch: {} patched / {} scanned.", counts[1], counts[0]);
    }

    /**
     * Scans and patches all command blocks in the given set of chunk positions.
     * Applies patches immediately (caller is responsible for freezing the world).
     * Returns int[]{totalScanned, totalPatched}.
     */
    public static int[] scanAndPatchAll(ServerWorld world, Set<ChunkPos> positions, ServerPlayerEntity player) {
        String dim = world.getRegistryKey().getValue().getPath();

        CommandPatcher.LOGGER.info("[CommandPatcher] Scanning {} chunks in {}...",
            positions.size(), world.getRegistryKey().getValue());

        List<PendingPatch> patches = new ArrayList<>();
        List<SuspiciousBlock> suspicious = new ArrayList<>();
        int commandBlockCount = 0;

        for (ChunkPos pos : positions) {
            if (!(world.getChunk(pos.x, pos.z) instanceof WorldChunk chunk)) continue;

            List<BlockEntity> blockEntities = new ArrayList<>(chunk.getBlockEntities().values());

            for (BlockEntity blockEntity : blockEntities) {
                if (!(blockEntity instanceof CommandBlockBlockEntity commandBlock)) continue;
                commandBlockCount++;

                String originalCommand = commandBlock.getCommandExecutor().getCommand();
                if (originalCommand == null || originalCommand.isBlank()) continue;

                CommandMigrator.MigrationResult result = CommandMigrator.migrate(originalCommand);
                if (result.wasModified) {
                    patches.add(new PendingPatch(commandBlock, result));
                } else if (looksProblematic(originalCommand)) {
                    suspicious.add(new SuspiciousBlock(commandBlock, originalCommand));
                }
            }
        }

        int patchCount = patches.size();

        CommandPatcher.LOGGER.info(
            "[CommandPatcher] {} scan: {} found, {} to patch, {} suspicious.",
            dim, commandBlockCount, patchCount, suspicious.size());

        // Apply patches immediately — world is frozen, so this is safe
        for (PendingPatch p : patches) {
            p.commandBlock().getCommandExecutor().setCommand(p.result().migrated);
            p.commandBlock().markDirty();

            BlockPos pos = p.commandBlock().getPos();
            String coords = pos.getX() + " " + pos.getY() + " " + pos.getZ();

            boolean hasWarning = p.result().changes.stream()
                .anyMatch(c -> c.startsWith("WARNING") || c.contains("MANUAL_MIGRATION_NEEDED") || c.contains("manual review"));

            if (hasWarning) {
                player.sendMessage(Text.literal(
                    "[CommandPatcher] Migrated (needs review) at " + coords)
                    .formatted(Formatting.RED), false);
            } else {
                player.sendMessage(Text.literal(
                    "[CommandPatcher] Migrated at " + coords)
                    .formatted(Formatting.GREEN), false);
            }
            player.sendMessage(Text.literal(
                "  Before: " + p.result().original)
                .formatted(Formatting.GRAY), false);
            player.sendMessage(Text.literal(
                "  After:  " + p.result().migrated)
                .formatted(Formatting.AQUA), false);

            CommandPatcher.LOGGER.info("[CommandPatcher] Patched at {}:", pos);
            CommandPatcher.LOGGER.info("    Before: {}", p.result().original);
            CommandPatcher.LOGGER.info("    After:  {}", p.result().migrated);
        }

        if (!suspicious.isEmpty()) {
            player.sendMessage(Text.literal(
                "[CommandPatcher] " + dim + ": " + suspicious.size()
                + " block(s) may still have old syntax (not auto-fixed):")
                .formatted(Formatting.RED), false);

            for (SuspiciousBlock s : suspicious) {
                BlockPos pos = s.commandBlock().getPos();
                String coords = pos.getX() + " " + pos.getY() + " " + pos.getZ();
                player.sendMessage(Text.literal(
                    "  ! " + coords + " — " + s.command())
                    .formatted(Formatting.RED), false);
            }
        }

        if (patchCount == 0 && suspicious.isEmpty()) {
            player.sendMessage(Text.literal(
                "[CommandPatcher] " + dim + ": " + commandBlockCount + " blocks scanned, none needed patching.")
                .formatted(Formatting.GREEN), false);
        }

        return new int[]{commandBlockCount, patchCount};
    }

    /**
     * Silent version of scanAndPatchAll — logs only, no player chat.
     */
    public static void scanAndPatchAllSilent(ServerWorld world, Set<ChunkPos> positions) {
        String dim = world.getRegistryKey().getValue().getPath();
        int commandBlockCount = 0;
        int patchCount = 0;

        for (ChunkPos pos : positions) {
            if (!(world.getChunk(pos.x, pos.z) instanceof WorldChunk chunk)) continue;

            List<BlockEntity> blockEntities = new ArrayList<>(chunk.getBlockEntities().values());

            for (BlockEntity blockEntity : blockEntities) {
                if (!(blockEntity instanceof CommandBlockBlockEntity commandBlock)) continue;
                commandBlockCount++;

                String originalCommand = commandBlock.getCommandExecutor().getCommand();
                if (originalCommand == null || originalCommand.isBlank()) continue;

                CommandMigrator.MigrationResult result = CommandMigrator.migrate(originalCommand);
                if (result.wasModified) {
                    commandBlock.getCommandExecutor().setCommand(result.migrated);
                    commandBlock.markDirty();
                    patchCount++;

                    CommandPatcher.LOGGER.info("[CommandPatcher] Patched at {}:", commandBlock.getPos());
                    CommandPatcher.LOGGER.info("    Before: {}", result.original);
                    CommandPatcher.LOGGER.info("    After:  {}", result.migrated);
                }
            }
        }

        CommandPatcher.LOGGER.info("[CommandPatcher] {} silent scan: {} found, {} patched.", dim, commandBlockCount, patchCount);
    }
}
