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

        // Overworld: worldDir/region/
        Path overworldRegion = worldDir.resolve("region");
        if (Files.isDirectory(overworldRegion)) {
            int[] counts = processRegionDir(overworldRegion, "overworld", warnings, errors);
            totalChunks += counts[0];
            totalFound += counts[1];
            totalPatched += counts[2];
            totalRegions += counts[3];
        }

        // Nether: worldDir/DIM-1/region/
        Path netherRegion = worldDir.resolve("DIM-1").resolve("region");
        if (Files.isDirectory(netherRegion)) {
            int[] counts = processRegionDir(netherRegion, "the_nether", warnings, errors);
            totalChunks += counts[0];
            totalFound += counts[1];
            totalPatched += counts[2];
            totalRegions += counts[3];
        }

        // End: worldDir/DIM1/region/
        Path endRegion = worldDir.resolve("DIM1").resolve("region");
        if (Files.isDirectory(endRegion)) {
            int[] counts = processRegionDir(endRegion, "the_end", warnings, errors);
            totalChunks += counts[0];
            totalFound += counts[1];
            totalPatched += counts[2];
            totalRegions += counts[3];
        }

        LOGGER.info("[CommandPatcher] Conversion complete: {} regions, {} chunks, {} command blocks found, {} patched.",
            totalRegions, totalChunks, totalFound, totalPatched);

        // Convert the resource pack if present
        ResourcePackConverter.ConvertResult rpResult = ResourcePackConverter.convert(worldDir);
        if (!rpResult.warnings().isEmpty()) {
            warnings.addAll(rpResult.warnings());
        }

        return new ConversionResult(totalChunks, totalFound, totalPatched, totalRegions, warnings, errors);
    }

    /**
     * Processes all .mca files in a region directory.
     * Returns int[]{chunksScanned, commandBlocksFound, commandBlocksPatched, regionFilesProcessed}.
     */
    private static int[] processRegionDir(Path regionDir, String dimensionName, List<String> warnings, List<String> errors) {
        int chunksScanned = 0;
        int commandBlocksFound = 0;
        int commandBlocksPatched = 0;
        int regionCount = 0;

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

                            for (String w : result.warnings()) {
                                warnings.add("[" + dimensionName + "] " + w);
                            }

                            if (result.patched() > 0) {
                                DataOutputStream dos = regionFile.getChunkOutputStream(chunkPos);
                                NbtIo.writeCompound(chunkNbt, dos);
                                dos.close();
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

        return new int[]{chunksScanned, commandBlocksFound, commandBlocksPatched, regionCount};
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
