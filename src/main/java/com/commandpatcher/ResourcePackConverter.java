package com.commandpatcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.zip.*;

/**
 * Converts a 1.12.2 resource pack (resources.zip inside a world save)
 * to be compatible with 1.21.10.
 *
 * Handles:
 *   - pack.mcmeta pack_format update
 *   - Folder renames (textures/items/ → textures/item/, textures/blocks/ → textures/block/)
 *   - Texture file renames matching the block/item flattening
 *   - Model JSON path reference updates
 *   - Sound JSON reference updates
 */
public class ResourcePackConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger("commandpatcher");

    // 1.21.10 resource pack format
    private static final int TARGET_PACK_FORMAT = 46;

    // Texture file renames: old name (without extension) → new name
    // Covers the 1.13 flattening renames for textures
    private static final Map<String, String> TEXTURE_RENAME = new LinkedHashMap<>();
    static {
        // ── Block texture renames ──────────────────────────────────────────
        TEXTURE_RENAME.put("trapdoor", "oak_trapdoor");
        TEXTURE_RENAME.put("deadbush", "dead_bush");
        TEXTURE_RENAME.put("tallgrass", "short_grass");
        TEXTURE_RENAME.put("grass_top", "grass_block_top");
        TEXTURE_RENAME.put("grass_side", "grass_block_side");
        TEXTURE_RENAME.put("grass_side_overlay", "grass_block_side_overlay");
        TEXTURE_RENAME.put("grass_side_snowed", "grass_block_snow");
        TEXTURE_RENAME.put("grass_path_side", "dirt_path_side");
        TEXTURE_RENAME.put("grass_path_top", "dirt_path_top");
        TEXTURE_RENAME.put("waterlily", "lily_pad");
        TEXTURE_RENAME.put("web", "cobweb");
        TEXTURE_RENAME.put("netherbrick", "nether_bricks");
        TEXTURE_RENAME.put("hardened_clay", "terracotta");
        TEXTURE_RENAME.put("noteblock", "note_block");
        TEXTURE_RENAME.put("mob_spawner", "spawner");
        TEXTURE_RENAME.put("magma", "magma_block");
        TEXTURE_RENAME.put("slime", "slime_block");
        TEXTURE_RENAME.put("end_bricks", "end_stone_bricks");
        TEXTURE_RENAME.put("red_nether_brick", "red_nether_bricks");
        TEXTURE_RENAME.put("stonebrick", "stone_bricks");
        TEXTURE_RENAME.put("stonebrick_carved", "chiseled_stone_bricks");
        TEXTURE_RENAME.put("stonebrick_cracked", "cracked_stone_bricks");
        TEXTURE_RENAME.put("stonebrick_mossy", "mossy_stone_bricks");
        TEXTURE_RENAME.put("brick", "bricks");
        TEXTURE_RENAME.put("cobblestone_mossy", "mossy_cobblestone");
        TEXTURE_RENAME.put("ice_packed", "packed_ice");
        TEXTURE_RENAME.put("torch_on", "torch");
        TEXTURE_RENAME.put("redstone_torch_on", "redstone_torch");

        // Rail renames
        TEXTURE_RENAME.put("rail_golden", "powered_rail");
        TEXTURE_RENAME.put("rail_golden_powered", "powered_rail_on");
        TEXTURE_RENAME.put("rail_normal", "rail");
        TEXTURE_RENAME.put("rail_normal_turned", "rail_corner");

        // Stone variants
        TEXTURE_RENAME.put("stone_andesite", "andesite");
        TEXTURE_RENAME.put("stone_andesite_smooth", "polished_andesite");
        TEXTURE_RENAME.put("stone_diorite", "diorite");
        TEXTURE_RENAME.put("stone_diorite_smooth", "polished_diorite");
        TEXTURE_RENAME.put("stone_granite", "granite");
        TEXTURE_RENAME.put("stone_granite_smooth", "polished_granite");

        // Sandstone variants
        TEXTURE_RENAME.put("sandstone_normal", "sandstone");
        TEXTURE_RENAME.put("sandstone_carved", "chiseled_sandstone");
        TEXTURE_RENAME.put("sandstone_smooth", "cut_sandstone");
        TEXTURE_RENAME.put("red_sandstone_normal", "red_sandstone");
        TEXTURE_RENAME.put("red_sandstone_carved", "chiseled_red_sandstone");
        TEXTURE_RENAME.put("red_sandstone_smooth", "cut_red_sandstone");

        // Quartz variants
        TEXTURE_RENAME.put("quartz_block_chiseled", "chiseled_quartz_block");
        TEXTURE_RENAME.put("quartz_block_chiseled_top", "chiseled_quartz_block_top");
        TEXTURE_RENAME.put("quartz_block_lines", "quartz_pillar");
        TEXTURE_RENAME.put("quartz_block_lines_top", "quartz_pillar_top");

        // Podzol
        TEXTURE_RENAME.put("dirt_podzol_side", "podzol_side");
        TEXTURE_RENAME.put("dirt_podzol_top", "podzol_top");

        // Prismarine
        TEXTURE_RENAME.put("prismarine_dark", "dark_prismarine");
        TEXTURE_RENAME.put("prismarine_rough", "prismarine");

        // Pumpkin/melon
        TEXTURE_RENAME.put("pumpkin_face_off", "carved_pumpkin");
        TEXTURE_RENAME.put("pumpkin_face_on", "jack_o_lantern");

        // Doors (block textures)
        TEXTURE_RENAME.put("door_wood_lower", "oak_door_bottom");
        TEXTURE_RENAME.put("door_wood_upper", "oak_door_top");
        TEXTURE_RENAME.put("door_acacia_lower", "acacia_door_bottom");
        TEXTURE_RENAME.put("door_acacia_upper", "acacia_door_top");
        TEXTURE_RENAME.put("door_birch_lower", "birch_door_bottom");
        TEXTURE_RENAME.put("door_birch_upper", "birch_door_top");
        TEXTURE_RENAME.put("door_dark_oak_lower", "dark_oak_door_bottom");
        TEXTURE_RENAME.put("door_dark_oak_upper", "dark_oak_door_top");
        TEXTURE_RENAME.put("door_jungle_lower", "jungle_door_bottom");
        TEXTURE_RENAME.put("door_jungle_upper", "jungle_door_top");
        TEXTURE_RENAME.put("door_spruce_lower", "spruce_door_bottom");
        TEXTURE_RENAME.put("door_spruce_upper", "spruce_door_top");
        TEXTURE_RENAME.put("door_iron_lower", "iron_door_bottom");
        TEXTURE_RENAME.put("door_iron_upper", "iron_door_top");

        // Flowers
        TEXTURE_RENAME.put("flower_rose", "poppy");
        TEXTURE_RENAME.put("flower_dandelion", "dandelion");
        TEXTURE_RENAME.put("flower_houstonia", "azure_bluet");
        TEXTURE_RENAME.put("flower_allium", "allium");
        TEXTURE_RENAME.put("flower_blue_orchid", "blue_orchid");
        TEXTURE_RENAME.put("flower_oxeye_daisy", "oxeye_daisy");
        TEXTURE_RENAME.put("flower_tulip_red", "red_tulip");
        TEXTURE_RENAME.put("flower_tulip_orange", "orange_tulip");
        TEXTURE_RENAME.put("flower_tulip_white", "white_tulip");
        TEXTURE_RENAME.put("flower_tulip_pink", "pink_tulip");
        TEXTURE_RENAME.put("flower_paeonia", "peony");

        // Double plants
        TEXTURE_RENAME.put("double_plant_sunflower_front", "sunflower_front");
        TEXTURE_RENAME.put("double_plant_sunflower_back", "sunflower_back");
        TEXTURE_RENAME.put("double_plant_sunflower_bottom", "sunflower_bottom");
        TEXTURE_RENAME.put("double_plant_sunflower_top", "sunflower_top");
        TEXTURE_RENAME.put("double_plant_syringa_bottom", "lilac_bottom");
        TEXTURE_RENAME.put("double_plant_syringa_top", "lilac_top");
        TEXTURE_RENAME.put("double_plant_rose_bottom", "rose_bush_bottom");
        TEXTURE_RENAME.put("double_plant_rose_top", "rose_bush_top");
        TEXTURE_RENAME.put("double_plant_paeonia_bottom", "peony_bottom");
        TEXTURE_RENAME.put("double_plant_paeonia_top", "peony_top");
        TEXTURE_RENAME.put("double_plant_grass_bottom", "tall_grass_bottom");
        TEXTURE_RENAME.put("double_plant_grass_top", "tall_grass_top");
        TEXTURE_RENAME.put("double_plant_fern_bottom", "large_fern_bottom");
        TEXTURE_RENAME.put("double_plant_fern_top", "large_fern_top");

        // Mushroom blocks
        TEXTURE_RENAME.put("mushroom_block_skin_brown", "brown_mushroom_block");
        TEXTURE_RENAME.put("mushroom_block_skin_red", "red_mushroom_block");
        TEXTURE_RENAME.put("mushroom_block_skin_stem", "mushroom_stem");
        TEXTURE_RENAME.put("mushroom_block_inside", "mushroom_block_inside");

        // Color-variant blocks (generated)
        String[] colors = {"white", "orange", "magenta", "light_blue", "yellow", "lime",
            "pink", "gray", "silver", "cyan", "purple", "blue", "brown", "green", "red", "black"};
        for (String c : colors) {
            String texColor = c.equals("silver") ? "light_gray" : c;
            TEXTURE_RENAME.put("wool_colored_" + c, texColor + "_wool");
            TEXTURE_RENAME.put("glass_" + c, texColor + "_stained_glass");
            TEXTURE_RENAME.put("glass_pane_top_" + c, texColor + "_stained_glass_pane_top");
            TEXTURE_RENAME.put("concrete_" + c, texColor + "_concrete");
            TEXTURE_RENAME.put("concrete_powder_" + c, texColor + "_concrete_powder");
            TEXTURE_RENAME.put("shulker_top_" + c, texColor + "_shulker_box_top");
            TEXTURE_RENAME.put("hardened_clay_stained_" + c, texColor + "_terracotta");
            TEXTURE_RENAME.put("glazed_terracotta_" + c, texColor + "_glazed_terracotta");
        }

        // Log renames
        TEXTURE_RENAME.put("log_oak", "oak_log");
        TEXTURE_RENAME.put("log_oak_top", "oak_log_top");
        TEXTURE_RENAME.put("log_spruce", "spruce_log");
        TEXTURE_RENAME.put("log_spruce_top", "spruce_log_top");
        TEXTURE_RENAME.put("log_birch", "birch_log");
        TEXTURE_RENAME.put("log_birch_top", "birch_log_top");
        TEXTURE_RENAME.put("log_jungle", "jungle_log");
        TEXTURE_RENAME.put("log_jungle_top", "jungle_log_top");
        TEXTURE_RENAME.put("log_acacia", "acacia_log");
        TEXTURE_RENAME.put("log_acacia_top", "acacia_log_top");
        TEXTURE_RENAME.put("log_big_oak", "dark_oak_log");
        TEXTURE_RENAME.put("log_big_oak_top", "dark_oak_log_top");

        // Planks renames
        TEXTURE_RENAME.put("planks_oak", "oak_planks");
        TEXTURE_RENAME.put("planks_spruce", "spruce_planks");
        TEXTURE_RENAME.put("planks_birch", "birch_planks");
        TEXTURE_RENAME.put("planks_jungle", "jungle_planks");
        TEXTURE_RENAME.put("planks_acacia", "acacia_planks");
        TEXTURE_RENAME.put("planks_big_oak", "dark_oak_planks");

        // Leaves renames
        TEXTURE_RENAME.put("leaves_oak", "oak_leaves");
        TEXTURE_RENAME.put("leaves_spruce", "spruce_leaves");
        TEXTURE_RENAME.put("leaves_birch", "birch_leaves");
        TEXTURE_RENAME.put("leaves_jungle", "jungle_leaves");
        TEXTURE_RENAME.put("leaves_acacia", "acacia_leaves");
        TEXTURE_RENAME.put("leaves_big_oak", "dark_oak_leaves");

        // Sapling renames
        TEXTURE_RENAME.put("sapling_oak", "oak_sapling");
        TEXTURE_RENAME.put("sapling_spruce", "spruce_sapling");
        TEXTURE_RENAME.put("sapling_birch", "birch_sapling");
        TEXTURE_RENAME.put("sapling_jungle", "jungle_sapling");
        TEXTURE_RENAME.put("sapling_acacia", "acacia_sapling");
        TEXTURE_RENAME.put("sapling_roofed_oak", "dark_oak_sapling");

        // ── Item texture renames ───────────────────────────────────────────
        TEXTURE_RENAME.put("potion_bottle_empty", "glass_bottle");
        TEXTURE_RENAME.put("potion_bottle_drinkable", "potion");
        TEXTURE_RENAME.put("potion_bottle_splash", "splash_potion");
        TEXTURE_RENAME.put("potion_bottle_lingering", "lingering_potion");
        TEXTURE_RENAME.put("boat", "oak_boat");
        TEXTURE_RENAME.put("bed", "red_bed");
        TEXTURE_RENAME.put("door_wood", "oak_door");
        TEXTURE_RENAME.put("door_iron", "iron_door");
        TEXTURE_RENAME.put("door_acacia", "acacia_door");
        TEXTURE_RENAME.put("door_birch", "birch_door");
        TEXTURE_RENAME.put("door_dark_oak", "dark_oak_door");
        TEXTURE_RENAME.put("door_jungle", "jungle_door");
        TEXTURE_RENAME.put("door_spruce", "spruce_door");
        TEXTURE_RENAME.put("reeds", "sugar_cane");
        TEXTURE_RENAME.put("melon_speckled", "glistering_melon_slice");
        TEXTURE_RENAME.put("fireworks", "firework_rocket");
        TEXTURE_RENAME.put("fireworks_charge", "firework_star");
        TEXTURE_RENAME.put("fireworks_charge_overlay", "firework_star_overlay");
        TEXTURE_RENAME.put("chorus_fruit_popped", "popped_chorus_fruit");
        TEXTURE_RENAME.put("totem", "totem_of_undying");
        TEXTURE_RENAME.put("slimeball", "slime_ball");

        // Food renames
        TEXTURE_RENAME.put("apple_golden", "golden_apple");
        TEXTURE_RENAME.put("beef_cooked", "cooked_beef");
        TEXTURE_RENAME.put("beef_raw", "beef");
        TEXTURE_RENAME.put("chicken_cooked", "cooked_chicken");
        TEXTURE_RENAME.put("chicken_raw", "chicken");
        TEXTURE_RENAME.put("porkchop_cooked", "cooked_porkchop");
        TEXTURE_RENAME.put("porkchop_raw", "porkchop");
        TEXTURE_RENAME.put("mutton_cooked", "cooked_mutton");
        TEXTURE_RENAME.put("mutton_raw", "mutton");
        TEXTURE_RENAME.put("rabbit_cooked", "cooked_rabbit");
        TEXTURE_RENAME.put("rabbit_raw", "rabbit");
        TEXTURE_RENAME.put("carrot_golden", "golden_carrot");
        TEXTURE_RENAME.put("fish_cod_raw", "cod");
        TEXTURE_RENAME.put("fish_cod_cooked", "cooked_cod");
        TEXTURE_RENAME.put("fish_salmon_raw", "salmon");
        TEXTURE_RENAME.put("fish_salmon_cooked", "cooked_salmon");
        TEXTURE_RENAME.put("fish_clownfish_raw", "tropical_fish");
        TEXTURE_RENAME.put("fish_pufferfish_raw", "pufferfish");

        // Gold → golden tool/armor renames
        TEXTURE_RENAME.put("gold_axe", "golden_axe");
        TEXTURE_RENAME.put("gold_hoe", "golden_hoe");
        TEXTURE_RENAME.put("gold_pickaxe", "golden_pickaxe");
        TEXTURE_RENAME.put("gold_shovel", "golden_shovel");
        TEXTURE_RENAME.put("gold_sword", "golden_sword");
        TEXTURE_RENAME.put("gold_helmet", "golden_helmet");
        TEXTURE_RENAME.put("gold_chestplate", "golden_chestplate");
        TEXTURE_RENAME.put("gold_leggings", "golden_leggings");
        TEXTURE_RENAME.put("gold_boots", "golden_boots");
        TEXTURE_RENAME.put("gold_horse_armor", "golden_horse_armor");

        // Wood → wooden tool renames
        TEXTURE_RENAME.put("wood_axe", "wooden_axe");
        TEXTURE_RENAME.put("wood_hoe", "wooden_hoe");
        TEXTURE_RENAME.put("wood_pickaxe", "wooden_pickaxe");
        TEXTURE_RENAME.put("wood_shovel", "wooden_shovel");
        TEXTURE_RENAME.put("wood_sword", "wooden_sword");

        // Book renames
        TEXTURE_RENAME.put("book_normal", "book");
        TEXTURE_RENAME.put("book_enchanted", "enchanted_book");
        TEXTURE_RENAME.put("book_writable", "writable_book");
        TEXTURE_RENAME.put("book_written", "written_book");

        // Bucket renames
        TEXTURE_RENAME.put("bucket_empty", "bucket");
        TEXTURE_RENAME.put("bucket_water", "water_bucket");
        TEXTURE_RENAME.put("bucket_lava", "lava_bucket");
        TEXTURE_RENAME.put("bucket_milk", "milk_bucket");

        // Seed renames
        TEXTURE_RENAME.put("seeds_wheat", "wheat_seeds");
        TEXTURE_RENAME.put("seeds_melon", "melon_seeds");
        TEXTURE_RENAME.put("seeds_pumpkin", "pumpkin_seeds");

        // Dye renames
        TEXTURE_RENAME.put("dye_powder_white", "white_dye");
        TEXTURE_RENAME.put("dye_powder_orange", "orange_dye");
        TEXTURE_RENAME.put("dye_powder_magenta", "magenta_dye");
        TEXTURE_RENAME.put("dye_powder_light_blue", "light_blue_dye");
        TEXTURE_RENAME.put("dye_powder_yellow", "yellow_dye");
        TEXTURE_RENAME.put("dye_powder_lime", "lime_dye");
        TEXTURE_RENAME.put("dye_powder_pink", "pink_dye");
        TEXTURE_RENAME.put("dye_powder_gray", "gray_dye");
        TEXTURE_RENAME.put("dye_powder_silver", "light_gray_dye");
        TEXTURE_RENAME.put("dye_powder_cyan", "cyan_dye");
        TEXTURE_RENAME.put("dye_powder_purple", "purple_dye");
        TEXTURE_RENAME.put("dye_powder_blue", "blue_dye");
        TEXTURE_RENAME.put("dye_powder_brown", "brown_dye");
        TEXTURE_RENAME.put("dye_powder_green", "green_dye");
        TEXTURE_RENAME.put("dye_powder_red", "red_dye");
        TEXTURE_RENAME.put("dye_powder_black", "black_dye");

        // Music disc renames
        TEXTURE_RENAME.put("record_13", "music_disc_13");
        TEXTURE_RENAME.put("record_cat", "music_disc_cat");
        TEXTURE_RENAME.put("record_blocks", "music_disc_blocks");
        TEXTURE_RENAME.put("record_chirp", "music_disc_chirp");
        TEXTURE_RENAME.put("record_far", "music_disc_far");
        TEXTURE_RENAME.put("record_mall", "music_disc_mall");
        TEXTURE_RENAME.put("record_mellohi", "music_disc_mellohi");
        TEXTURE_RENAME.put("record_stal", "music_disc_stal");
        TEXTURE_RENAME.put("record_strad", "music_disc_strad");
        TEXTURE_RENAME.put("record_ward", "music_disc_ward");
        TEXTURE_RENAME.put("record_11", "music_disc_11");
        TEXTURE_RENAME.put("record_wait", "music_disc_wait");
    }

    // Folder path renames within assets/minecraft/textures/
    private static final Map<String, String> FOLDER_RENAME = new LinkedHashMap<>();
    static {
        FOLDER_RENAME.put("textures/blocks", "textures/block");
        FOLDER_RENAME.put("textures/items", "textures/item");
    }

    public record ConvertResult(int filesRenamed, int foldersRenamed, int jsonUpdated, List<String> warnings) {}

    /**
     * Converts a resource pack in the world save directory.
     * Looks for resources.zip, extracts, converts, and re-zips.
     */
    public static ConvertResult convert(Path worldDir) {
        Path resourcesZip = worldDir.resolve("resources.zip");
        List<String> warnings = new ArrayList<>();

        if (!Files.exists(resourcesZip)) {
            LOGGER.info("[CommandPatcher] No resources.zip found in world save, skipping resource pack conversion.");
            return new ConvertResult(0, 0, 0, warnings);
        }

        LOGGER.info("[CommandPatcher] Found resources.zip, converting resource pack...");

        // Extract to temp dir
        Path tempDir;
        try {
            tempDir = Files.createTempDirectory("commandpatcher_rp_");
        } catch (IOException e) {
            warnings.add("Failed to create temp directory for resource pack conversion: " + e.getMessage());
            return new ConvertResult(0, 0, 0, warnings);
        }

        try {
            // Extract zip
            extractZip(resourcesZip, tempDir);

            int[] counts = new int[3]; // filesRenamed, foldersRenamed, jsonUpdated

            // 1. Update pack.mcmeta
            updatePackMcmeta(tempDir, warnings);

            // 2. Rename folders (textures/blocks → textures/block, etc.)
            Path assetsMinecraft = tempDir.resolve("assets").resolve("minecraft");
            if (Files.isDirectory(assetsMinecraft)) {
                for (Map.Entry<String, String> entry : FOLDER_RENAME.entrySet()) {
                    Path oldFolder = assetsMinecraft.resolve(entry.getKey());
                    Path newFolder = assetsMinecraft.resolve(entry.getValue());
                    if (Files.isDirectory(oldFolder) && !Files.exists(newFolder)) {
                        try {
                            moveDirectory(oldFolder, newFolder);
                            counts[1]++;
                            LOGGER.info("[CommandPatcher] Renamed folder: {} → {}", entry.getKey(), entry.getValue());
                        } catch (IOException e) {
                            warnings.add("Failed to rename folder " + entry.getKey() + ": " + e.getMessage());
                        }
                    }
                }

                // 3. Rename texture files
                counts[0] += renameTextureFiles(assetsMinecraft.resolve("textures"), warnings);

                // 4. Update JSON references in model files
                Path modelsDir = assetsMinecraft.resolve("models");
                if (Files.isDirectory(modelsDir)) {
                    counts[2] += updateJsonReferences(modelsDir, warnings);
                }

                // 5. Update JSON references in blockstates
                Path blockstatesDir = assetsMinecraft.resolve("blockstates");
                if (Files.isDirectory(blockstatesDir)) {
                    counts[2] += updateJsonReferences(blockstatesDir, warnings);
                }
            }

            // Back up old zip and create new one
            Path backup = worldDir.resolve("resources_1.12.2_backup.zip");
            if (!Files.exists(backup)) {
                Files.copy(resourcesZip, backup);
                LOGGER.info("[CommandPatcher] Backed up original resource pack to: {}", backup.getFileName());
            }

            // Re-zip
            createZip(tempDir, resourcesZip);

            LOGGER.info("[CommandPatcher] Resource pack conversion complete: {} files renamed, {} folders renamed, {} JSON files updated.",
                counts[0], counts[1], counts[2]);

            return new ConvertResult(counts[0], counts[1], counts[2], warnings);

        } catch (IOException e) {
            warnings.add("Resource pack conversion failed: " + e.getMessage());
            return new ConvertResult(0, 0, 0, warnings);
        } finally {
            // Clean up temp dir
            try {
                deleteDirectory(tempDir);
            } catch (IOException ignored) {}
        }
    }

    private static void updatePackMcmeta(Path dir, List<String> warnings) {
        Path mcmeta = dir.resolve("pack.mcmeta");
        if (!Files.exists(mcmeta)) {
            // Create one if missing
            try {
                Files.writeString(mcmeta,
                    "{\"pack\":{\"pack_format\":" + TARGET_PACK_FORMAT
                    + ",\"description\":\"Converted by CommandPatcher\"}}");
                LOGGER.info("[CommandPatcher] Created pack.mcmeta with pack_format {}", TARGET_PACK_FORMAT);
            } catch (IOException e) {
                warnings.add("Failed to create pack.mcmeta: " + e.getMessage());
            }
            return;
        }

        try {
            String content = Files.readString(mcmeta, StandardCharsets.UTF_8);
            // Replace pack_format value
            String updated = content.replaceAll("\"pack_format\"\\s*:\\s*\\d+", "\"pack_format\":" + TARGET_PACK_FORMAT);
            // Remove pack_version if present (not a valid field)
            updated = updated.replaceAll(",?\\s*\"pack_version\"\\s*:\\s*[\\d.]+", "");
            updated = updated.replaceAll("\"pack_version\"\\s*:\\s*[\\d.]+\\s*,?", "");
            Files.writeString(mcmeta, updated, StandardCharsets.UTF_8);
            LOGGER.info("[CommandPatcher] Updated pack.mcmeta pack_format to {}", TARGET_PACK_FORMAT);
        } catch (IOException e) {
            warnings.add("Failed to update pack.mcmeta: " + e.getMessage());
        }
    }

    /**
     * Renames texture files in the textures directory tree based on the rename map.
     */
    private static int renameTextureFiles(Path texturesDir, List<String> warnings) {
        if (!Files.isDirectory(texturesDir)) return 0;

        int count = 0;
        try {
            List<Path> files = new ArrayList<>();
            Files.walkFileTree(texturesDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.toString().endsWith(".png") || file.toString().endsWith(".png.mcmeta")) {
                        files.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            for (Path file : files) {
                String fileName = file.getFileName().toString();
                String baseName;
                String extension;

                if (fileName.endsWith(".png.mcmeta")) {
                    baseName = fileName.substring(0, fileName.length() - 11); // strip .png.mcmeta
                    extension = ".png.mcmeta";
                } else {
                    baseName = fileName.substring(0, fileName.length() - 4); // strip .png
                    extension = ".png";
                }

                String newName = TEXTURE_RENAME.get(baseName);
                if (newName != null) {
                    Path target = file.resolveSibling(newName + extension);
                    if (!Files.exists(target)) {
                        Files.move(file, target);
                        count++;
                    }
                }
            }
        } catch (IOException e) {
            warnings.add("Error scanning texture files: " + e.getMessage());
        }
        return count;
    }

    /**
     * Updates texture/model path references in JSON files (models, blockstates).
     */
    private static int updateJsonReferences(Path dir, List<String> warnings) {
        if (!Files.isDirectory(dir)) return 0;

        int count = 0;
        try {
            List<Path> jsonFiles = new ArrayList<>();
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.toString().endsWith(".json")) {
                        jsonFiles.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            for (Path jsonFile : jsonFiles) {
                String content = Files.readString(jsonFile, StandardCharsets.UTF_8);
                String updated = content;

                // Update folder references
                updated = updated.replace("textures/blocks/", "textures/block/");
                updated = updated.replace("textures/items/", "textures/item/");
                updated = updated.replace("\"blocks/", "\"block/");
                updated = updated.replace("\"items/", "\"item/");

                // Update individual texture references
                for (Map.Entry<String, String> entry : TEXTURE_RENAME.entrySet()) {
                    // Match references like "minecraft:blocks/old_name" or "blocks/old_name"
                    updated = updated.replace("block/" + entry.getKey(), "block/" + entry.getValue());
                    updated = updated.replace("item/" + entry.getKey(), "item/" + entry.getValue());
                    // Also handle direct references without folder prefix
                    updated = updated.replace("\"" + entry.getKey() + "\"", "\"" + entry.getValue() + "\"");
                }

                if (!updated.equals(content)) {
                    Files.writeString(jsonFile, updated, StandardCharsets.UTF_8);
                    count++;
                }
            }
        } catch (IOException e) {
            warnings.add("Error updating JSON references: " + e.getMessage());
        }
        return count;
    }

    // ── Zip utilities ──────────────────────────────────────────────────────

    private static void extractZip(Path zipFile, Path destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path entryPath = destDir.resolve(entry.getName()).normalize();
                // Security: prevent zip slip
                if (!entryPath.startsWith(destDir)) {
                    throw new IOException("Zip entry outside target dir: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    try (OutputStream os = Files.newOutputStream(entryPath)) {
                        zis.transferTo(os);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private static void createZip(Path sourceDir, Path zipFile) throws IOException {
        // Delete existing zip first
        Files.deleteIfExists(zipFile);

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile.toFile()))) {
            Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String entryName = sourceDir.relativize(file).toString().replace('\\', '/');
                    zos.putNextEntry(new ZipEntry(entryName));
                    Files.copy(file, zos);
                    zos.closeEntry();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (!dir.equals(sourceDir)) {
                        String entryName = sourceDir.relativize(dir).toString().replace('\\', '/') + "/";
                        zos.putNextEntry(new ZipEntry(entryName));
                        zos.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private static void moveDirectory(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path dest = target.resolve(source.relativize(dir));
                Files.createDirectories(dest);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path dest = target.resolve(source.relativize(file));
                Files.move(file, dest, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
