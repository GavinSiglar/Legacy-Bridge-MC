package com.commandpatcher;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.List;

/**
 * Patches command block commands in raw chunk NBT data.
 * Works at the file level — no world loading needed.
 *
 * Handles both old and modern chunk NBT formats:
 *   - Pre-1.18:  root → Level → TileEntities (id: "Control" or "minecraft:command_block")
 *   - 1.18+:     root → block_entities         (id: "minecraft:command_block")
 */
public class NbtPatcher {
    // Modern format (1.18+)
    private static final String BLOCK_ENTITIES_KEY = "block_entities";

    // Old format (pre-1.18)
    private static final String LEVEL_KEY          = "Level";
    private static final String TILE_ENTITIES_KEY  = "TileEntities";

    private static final String ID_KEY      = "id";
    private static final String COMMAND_KEY = "Command";

    public record PatchResult(int patched, int total, List<String> warnings, List<String> report, List<CommandEntry> allCommands) {}

    public record CommandEntry(String pos, String original, String migrated, boolean wasModified, List<String> changes) {}

    /**
     * Patches all command block block entities in a chunk's NBT.
     * Returns a PatchResult with counts and any warnings for problematic commands.
     */
    public static PatchResult patchChunkNbt(NbtCompound chunkNbt) {
        if (chunkNbt == null) return new PatchResult(0, 0, List.of(), List.of(), List.of());

        // Try modern format first: root.block_entities
        if (chunkNbt.contains(BLOCK_ENTITIES_KEY)) {
            NbtList blockEntities = chunkNbt.getListOrEmpty(BLOCK_ENTITIES_KEY);
            return patchEntityList(blockEntities);
        }

        // Try old format: root.Level.TileEntities
        if (chunkNbt.contains(LEVEL_KEY)) {
            NbtCompound level = chunkNbt.getCompound(LEVEL_KEY).orElse(null);
            if (level != null && level.contains(TILE_ENTITIES_KEY)) {
                NbtList tileEntities = level.getListOrEmpty(TILE_ENTITIES_KEY);
                return patchEntityList(tileEntities);
            }
        }

        // Very old format: root.TileEntities (no Level wrapper, seen in some old saves)
        if (chunkNbt.contains(TILE_ENTITIES_KEY)) {
            NbtList tileEntities = chunkNbt.getListOrEmpty(TILE_ENTITIES_KEY);
            return patchEntityList(tileEntities);
        }

        return new PatchResult(0, 0, List.of(), List.of(), List.of());
    }

    /**
     * Iterates a list of block entity NBT compounds and patches command blocks.
     */
    private static PatchResult patchEntityList(NbtList entities) {
        int patchCount = 0;
        int totalCount = 0;
        List<String> warnings = new ArrayList<>();
        List<String> report = new ArrayList<>();
        List<CommandEntry> allCommands = new ArrayList<>();

        for (int i = 0; i < entities.size(); i++) {
            NbtCompound entity = entities.getCompound(i).orElseGet(NbtCompound::new);

            String id = entity.getString(ID_KEY, "");
            if (!isCommandBlock(id)) continue;
            if (!entity.contains(COMMAND_KEY)) continue;

            String original = entity.getString(COMMAND_KEY, "");
            if (original.isBlank()) continue;

            totalCount++;

            // Get position if available for reporting
            int x = entity.getInt("x", 0);
            int y = entity.getInt("y", 0);
            int z = entity.getInt("z", 0);
            String pos = x + " " + y + " " + z;

            CommandMigrator.MigrationResult result = CommandMigrator.migrate(original);
            if (result.wasModified) {
                entity.putString(COMMAND_KEY, result.migrated);
                patchCount++;

                allCommands.add(new CommandEntry(pos, original, result.migrated, true, result.changes));

                report.add("[PATCHED] [" + pos + "]");
                report.add("  BEFORE: " + original);
                report.add("  AFTER:  " + result.migrated);
                for (String change : result.changes) {
                    report.add("  CHANGE: " + change);
                }

                // Check for warnings in the migration
                for (String change : result.changes) {
                    if (change.startsWith("WARNING") || change.contains("MANUAL_MIGRATION_NEEDED")) {
                        warnings.add("[" + pos + "] " + change + " | Command: " + original);
                    }
                }
            } else {
                allCommands.add(new CommandEntry(pos, original, original, false, List.of()));
                report.add("[UNCHANGED] [" + pos + "] " + original);
            }
        }
        return new PatchResult(patchCount, totalCount, warnings, report, allCommands);
    }

    private static boolean isCommandBlock(String id) {
        return id.equals("minecraft:command_block")
            || id.equals("minecraft:chain_command_block")
            || id.equals("minecraft:repeating_command_block")
            // Pre-1.11 block entity IDs (before namespacing)
            || id.equals("Control")
            || id.equals("command_block")
            || id.equals("chain_command_block")
            || id.equals("repeating_command_block");
    }
}
