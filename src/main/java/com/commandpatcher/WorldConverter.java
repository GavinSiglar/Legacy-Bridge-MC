package com.commandpatcher;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.storage.RegionFile;
import net.minecraft.world.storage.StorageKey;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts all command blocks in a world's region files without loading the world.
 * Reads .mca files directly, patches NBT, writes back.
 */
public class WorldConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger("commandpatcher");

    public record ConversionResult(
        int chunksScanned,
        int commandBlocksFound,
        int commandBlocksPatched,
        int regionFiles,
        List<String> warnings,
        List<String> errors
    ) {}

    /**
     * Converts all command blocks in the given world directory.
     * Scans overworld, nether, and end region folders.
     */
    public static ConversionResult convert(Path worldDir) {
        int totalChunks = 0;
        int totalFound = 0;
        int totalPatched = 0;
        int totalRegions = 0;
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> report = new ArrayList<>();
        List<NbtPatcher.CommandEntry> allCommands = new ArrayList<>();

        // Overworld: worldDir/region/
        Path overworldRegion = worldDir.resolve("region");
        if (Files.isDirectory(overworldRegion)) {
            int[] counts = processRegionDir(overworldRegion, "overworld", warnings, errors, report, allCommands);
            totalChunks += counts[0];
            totalFound += counts[1];
            totalPatched += counts[2];
            totalRegions += counts[3];
        }

        // Nether: worldDir/DIM-1/region/
        Path netherRegion = worldDir.resolve("DIM-1").resolve("region");
        if (Files.isDirectory(netherRegion)) {
            int[] counts = processRegionDir(netherRegion, "the_nether", warnings, errors, report, allCommands);
            totalChunks += counts[0];
            totalFound += counts[1];
            totalPatched += counts[2];
            totalRegions += counts[3];
        }

        // End: worldDir/DIM1/region/
        Path endRegion = worldDir.resolve("DIM1").resolve("region");
        if (Files.isDirectory(endRegion)) {
            int[] counts = processRegionDir(endRegion, "the_end", warnings, errors, report, allCommands);
            totalChunks += counts[0];
            totalFound += counts[1];
            totalPatched += counts[2];
            totalRegions += counts[3];
        }

        LOGGER.info("[CommandPatcher] Conversion complete: {} regions, {} chunks, {} command blocks found, {} patched, {} warnings, {} errors.",
            totalRegions, totalChunks, totalFound, totalPatched, warnings.size(), errors.size());

        // Write detailed report file
        writeReport(worldDir, report, totalFound, totalPatched, warnings, errors);

        // Write all commands to a txt file in the logs folder
        writeCommandDump(worldDir, allCommands);

        // Write a test datapack so MC can validate all migrated commands on /reload
        writeTestDatapack(worldDir, allCommands);

        // Convert the resource pack if present
        ResourcePackConverter.ConvertResult rpResult = ResourcePackConverter.convert(worldDir);
        if (!rpResult.warnings().isEmpty()) {
            warnings.addAll(rpResult.warnings());
        }
        if (rpResult.filesRenamed() > 0 || rpResult.foldersRenamed() > 0) {
            LOGGER.info("[CommandPatcher] Resource pack converted: {} files renamed, {} folders renamed, {} JSON files updated.",
                rpResult.filesRenamed(), rpResult.foldersRenamed(), rpResult.jsonUpdated());
        }

        return new ConversionResult(totalChunks, totalFound, totalPatched, totalRegions, warnings, errors);
    }

    /**
     * Processes all .mca files in a region directory.
     * Returns int[]{chunksScanned, commandBlocksFound, commandBlocksPatched, regionFilesProcessed}.
     */
    private static int[] processRegionDir(Path regionDir, String dimensionName, List<String> warnings, List<String> errors, List<String> report, List<NbtPatcher.CommandEntry> allCommands) {
        int chunksScanned = 0;
        int commandBlocksFound = 0;
        int commandBlocksPatched = 0;
        int regionCount = 0;

        LOGGER.info("[CommandPatcher] Scanning region dir: {} ({})", regionDir, dimensionName);

        List<Path> mcaFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(regionDir, "*.mca")) {
            for (Path p : stream) {
                mcaFiles.add(p);
            }
        } catch (IOException e) {
            errors.add("Failed to list region files in " + regionDir + ": " + e.getMessage());
            return new int[]{0, 0, 0, 0};
        }

        for (Path mcaPath : mcaFiles) {
            regionCount++;

            StorageKey storageKey = new StorageKey(
                dimensionName,
                RegistryKey.of(RegistryKeys.WORLD, Identifier.of("minecraft", dimensionName)),
                "chunk"
            );

            try (RegionFile regionFile = new RegionFile(storageKey, mcaPath, regionDir, true)) {
                String fileName = mcaPath.getFileName().toString();
                int[] regionCoords = parseRegionCoords(fileName);
                if (regionCoords == null) {
                    errors.add("Could not parse region file name: " + fileName);
                    continue;
                }

                int regionX = regionCoords[0];
                int regionZ = regionCoords[1];

                for (int cx = 0; cx < 32; cx++) {
                    for (int cz = 0; cz < 32; cz++) {
                        ChunkPos chunkPos = new ChunkPos(regionX * 32 + cx, regionZ * 32 + cz);

                        if (!regionFile.isChunkValid(chunkPos)) continue;

                        try {
                            DataInputStream dis = regionFile.getChunkInputStream(chunkPos);
                            if (dis == null) continue;

                            NbtCompound chunkNbt = NbtIo.readCompound(dis, NbtSizeTracker.ofUnlimitedBytes());
                            dis.close();

                            if (chunkNbt == null) continue;
                            chunksScanned++;

                            NbtPatcher.PatchResult result = NbtPatcher.patchChunkNbt(chunkNbt);
                            commandBlocksFound += result.total();
                            commandBlocksPatched += result.patched();

                            // Collect report lines with dimension context
                            for (String line : result.report()) {
                                report.add("[" + dimensionName + "] " + line);
                            }

                            // Collect warnings with dimension context
                            for (String w : result.warnings()) {
                                warnings.add("[" + dimensionName + "] " + w);
                            }

                            // Collect all commands for the dump file
                            allCommands.addAll(result.allCommands());

                            if (result.patched() > 0) {
                                // Write the modified chunk back
                                DataOutputStream dos = regionFile.getChunkOutputStream(chunkPos);
                                NbtIo.writeCompound(chunkNbt, dos);
                                dos.close();

                                LOGGER.info("[CommandPatcher] Patched {} command(s) in chunk [{}, {}] of {}",
                                    result.patched(), chunkPos.x, chunkPos.z, fileName);
                            }
                        } catch (Exception e) {
                            errors.add("Error processing chunk [" + chunkPos.x + ", " + chunkPos.z
                                + "] in " + fileName + ": " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                errors.add("Failed to open region file " + mcaPath.getFileName() + ": " + e.getMessage());
            }
        }

        LOGGER.info("[CommandPatcher] {} dimension: {} regions, {} chunks, {} command blocks found, {} patched.",
            dimensionName, regionCount, chunksScanned, commandBlocksFound, commandBlocksPatched);

        return new int[]{chunksScanned, commandBlocksFound, commandBlocksPatched, regionCount};
    }

    /**
     * Writes a detailed report of all command blocks found and their conversion status.
     */
    private static void writeReport(Path worldDir, List<String> report, int totalFound, int totalPatched, List<String> warnings, List<String> errors) {
        Path reportFile = worldDir.resolve("commandpatcher_report.txt");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(reportFile))) {
            pw.println("=== CommandPatcher Conversion Report ===");
            pw.println("Total command blocks found: " + totalFound);
            pw.println("Total patched: " + totalPatched);
            pw.println("Total unchanged: " + (totalFound - totalPatched));
            pw.println("Warnings: " + warnings.size());
            pw.println("Errors: " + errors.size());
            pw.println();

            // Separate patched and unchanged for easier reading
            pw.println("=== PATCHED COMMANDS ===");
            pw.println();
            for (String line : report) {
                if (line.contains("[PATCHED]") || (line.startsWith("  ") && !line.contains("[UNCHANGED]"))) {
                    pw.println(line);
                }
            }

            pw.println();
            pw.println("=== UNCHANGED COMMANDS ===");
            pw.println();
            for (String line : report) {
                if (line.contains("[UNCHANGED]")) {
                    pw.println(line);
                }
            }

            if (!warnings.isEmpty()) {
                pw.println();
                pw.println("=== WARNINGS ===");
                pw.println();
                for (String w : warnings) {
                    pw.println(w);
                }
            }

            if (!errors.isEmpty()) {
                pw.println();
                pw.println("=== ERRORS ===");
                pw.println();
                for (String e : errors) {
                    pw.println(e);
                }
            }

            LOGGER.info("[CommandPatcher] Report written to: {}", reportFile);
        } catch (IOException e) {
            LOGGER.error("[CommandPatcher] Failed to write report: {}", e.getMessage());
        }
    }

    /**
     * Writes all command block commands to a txt file in the logs folder.
     */
    /**
     * Writes a datapack into the world's datapacks folder containing all migrated
     * commands as a .mcfunction file. Minecraft validates mcfunction files on /reload,
     * so any commands that would error will show up in the game log without needing
     * to actually run them.
     */
    private static void writeTestDatapack(Path worldDir, List<NbtPatcher.CommandEntry> allCommands) {
        if (allCommands.isEmpty()) return;

        Path datapackDir = worldDir.resolve("datapacks").resolve("commandpatcher_test");
        Path functionDir = datapackDir.resolve("data").resolve("commandpatcher").resolve("function");
        Path testDir = functionDir.resolve("test");
        try {
            Files.createDirectories(testDir);
        } catch (IOException e) {
            LOGGER.error("[CommandPatcher] Failed to create test datapack directory: {}", e.getMessage());
            return;
        }

        // Write pack.mcmeta
        Path mcmeta = datapackDir.resolve("pack.mcmeta");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(mcmeta))) {
            pw.print("{\"pack\":{\"pack_format\":61,\"description\":\"CommandPatcher - migrated command validation\"}}");
        } catch (IOException e) {
            LOGGER.error("[CommandPatcher] Failed to write pack.mcmeta: {}", e.getMessage());
            return;
        }

        // Write each command as its own mcfunction file so MC reports
        // each failure individually instead of failing the whole function.
        // Also write a mapping file so the user can look up which command is which.
        Path mappingFile = datapackDir.resolve("command_mapping.txt");
        try (PrintWriter mapping = new PrintWriter(Files.newBufferedWriter(mappingFile))) {
            mapping.println("=== CommandPatcher Test Datapack — Command Mapping ===");
            mapping.println("Each command is in its own .mcfunction file.");
            mapping.println("If a file fails to load, check the log for 'Failed to load function commandpatcher:test/cmd_NNN'");
            mapping.println("Then look up cmd_NNN below to find the block position and command.");
            mapping.println();

            for (int i = 0; i < allCommands.size(); i++) {
                NbtPatcher.CommandEntry entry = allCommands.get(i);
                String cmd = entry.wasModified() ? entry.migrated() : entry.original();
                // Trim whitespace and strip leading / — mcfunction lines don't use /
                cmd = cmd.trim();
                if (cmd.startsWith("/")) {
                    cmd = cmd.substring(1);
                }

                String fileName = String.format("cmd_%03d", i);

                // Write individual mcfunction file
                Path mcfunction = testDir.resolve(fileName + ".mcfunction");
                try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(mcfunction))) {
                    pw.println(cmd);
                } catch (IOException e) {
                    LOGGER.error("[CommandPatcher] Failed to write {}: {}", fileName, e.getMessage());
                }

                // Write mapping entry
                mapping.println(fileName + " [" + entry.pos() + "] " + cmd);
            }
            LOGGER.info("[CommandPatcher] Test datapack written to: {} ({} command files)", datapackDir, allCommands.size());
        } catch (IOException e) {
            LOGGER.error("[CommandPatcher] Failed to write command mapping: {}", e.getMessage());
        }
    }

    private static void writeCommandDump(Path worldDir, List<NbtPatcher.CommandEntry> allCommands) {
        // Minecraft's logs folder is at .minecraft/logs — go up from the world save dir
        // worldDir is something like .minecraft/saves/MyWorld, so logs is at ../../logs
        Path logsDir = worldDir.resolve("..").resolve("..").resolve("logs").normalize();
        try {
            Files.createDirectories(logsDir);
        } catch (IOException e) {
            LOGGER.error("[CommandPatcher] Failed to create logs directory: {}", e.getMessage());
            return;
        }

        Path dumpFile = logsDir.resolve("commandpatcher_commands.txt");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(dumpFile))) {
            pw.println("=== CommandPatcher — All Command Block Commands ===");
            pw.println("World: " + worldDir.getFileName());
            pw.println("Total command blocks: " + allCommands.size());
            pw.println();

            for (NbtPatcher.CommandEntry entry : allCommands) {
                pw.println("[" + entry.pos() + "] " + entry.original());
            }

            LOGGER.info("[CommandPatcher] Command dump written to: {}", dumpFile);
        } catch (IOException e) {
            LOGGER.error("[CommandPatcher] Failed to write command dump: {}", e.getMessage());
        }
    }

    /**
     * Parses "r.X.Z.mca" into {X, Z}.
     */
    private static int[] parseRegionCoords(String fileName) {
        if (!fileName.startsWith("r.") || !fileName.endsWith(".mca")) return null;
        String[] parts = fileName.substring(2, fileName.length() - 4).split("\\.");
        if (parts.length != 2) return null;
        try {
            return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
