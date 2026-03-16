package com.commandpatcher;

import java.util.*;
import java.util.regex.*;

/**
 * Migrates Minecraft command syntax from pre-1.13 (and intermediate versions)
 * to 1.21.10 modern syntax. Handles command renames, selector argument changes,
 * execute chain conversion, slot name normalization, and more.
 */
public class CommandMigrator {

    // -------------------------------------------------------------------------
    // Result type
    // -------------------------------------------------------------------------

    public static class MigrationResult {
        public final String original;
        public final String migrated;
        public final List<String> changes;
        public final boolean wasModified;

        public MigrationResult(String original, String migrated, List<String> changes) {
            this.original = original;
            this.migrated = migrated;
            this.changes = Collections.unmodifiableList(changes);
            this.wasModified = !original.equals(migrated);
        }
    }

    // -------------------------------------------------------------------------
    // Static lookup maps
    // -------------------------------------------------------------------------

    /** Old entity type IDs (lowercase, no underscores) → modern minecraft: namespaced ID */
    private static final Map<String, String> ENTITY_TYPE_MAP = new LinkedHashMap<>();
    static {
        ENTITY_TYPE_MAP.put("areaeffectcloud",      "area_effect_cloud");
        ENTITY_TYPE_MAP.put("armorstand",           "armor_stand");
        ENTITY_TYPE_MAP.put("cavespider",           "cave_spider");
        ENTITY_TYPE_MAP.put("elderguardian",        "elder_guardian");
        ENTITY_TYPE_MAP.put("endcrystal",           "end_crystal");
        ENTITY_TYPE_MAP.put("enderdragon",          "ender_dragon");
        ENTITY_TYPE_MAP.put("experienceorb",        "experience_orb");
        ENTITY_TYPE_MAP.put("xporb",                "experience_orb");
        ENTITY_TYPE_MAP.put("fallingblock",         "falling_block");
        ENTITY_TYPE_MAP.put("fireworksrocketentity","firework_rocket");
        ENTITY_TYPE_MAP.put("fireworksrocket",      "firework_rocket");
        ENTITY_TYPE_MAP.put("itemframe",            "item_frame");
        ENTITY_TYPE_MAP.put("leashknot",            "leash_knot");
        ENTITY_TYPE_MAP.put("lightningbolt",        "lightning_bolt");
        ENTITY_TYPE_MAP.put("magmacube",            "magma_cube");
        ENTITY_TYPE_MAP.put("minecartchest",        "chest_minecart");
        ENTITY_TYPE_MAP.put("minecartcommandblock", "command_block_minecart");
        ENTITY_TYPE_MAP.put("minecartfurnace",      "furnace_minecart");
        ENTITY_TYPE_MAP.put("minecarthopper",       "hopper_minecart");
        ENTITY_TYPE_MAP.put("minecartrideable",     "minecart");
        ENTITY_TYPE_MAP.put("minecarttnt",          "tnt_minecart");
        ENTITY_TYPE_MAP.put("minecartspawner",      "spawner_minecart");
        ENTITY_TYPE_MAP.put("mushroomcow",          "mooshroom");
        ENTITY_TYPE_MAP.put("polarbear",            "polar_bear");
        ENTITY_TYPE_MAP.put("primedtnt",            "tnt");
        ENTITY_TYPE_MAP.put("shulkerbullet",        "shulker_bullet");
        ENTITY_TYPE_MAP.put("skeletonhorse",        "skeleton_horse");
        ENTITY_TYPE_MAP.put("smallfireball",        "small_fireball");
        ENTITY_TYPE_MAP.put("snowman",              "snow_golem");
        ENTITY_TYPE_MAP.put("spectralarrow",        "spectral_arrow");
        ENTITY_TYPE_MAP.put("thrownegg",            "egg");
        ENTITY_TYPE_MAP.put("thrownenderpearl",     "ender_pearl");
        ENTITY_TYPE_MAP.put("thrownexpbottle",      "experience_bottle");
        ENTITY_TYPE_MAP.put("thrownpotion",         "potion");
        ENTITY_TYPE_MAP.put("tropicalfish",         "tropical_fish");
        ENTITY_TYPE_MAP.put("vindicationillager",   "vindicator");
        ENTITY_TYPE_MAP.put("evocationfangs",       "evoker_fangs");
        ENTITY_TYPE_MAP.put("evocationillager",     "evoker");
        ENTITY_TYPE_MAP.put("witherboss",           "wither");
        ENTITY_TYPE_MAP.put("witherskeleton",       "wither_skeleton");
        ENTITY_TYPE_MAP.put("witherskull",          "wither_skull");
        ENTITY_TYPE_MAP.put("zombiehorse",          "zombie_horse");
        ENTITY_TYPE_MAP.put("zombiepigman",         "zombified_piglin");
        ENTITY_TYPE_MAP.put("pigzombie",            "zombified_piglin");
        ENTITY_TYPE_MAP.put("zombievillager",       "zombie_villager");
        ENTITY_TYPE_MAP.put("entityhorse",          "horse");
        ENTITY_TYPE_MAP.put("eyeofendersignal",     "eye_of_ender");
    }

    /** Gamemode short codes and numeric IDs → modern gamemode names */
    private static final Map<String, String> GAMEMODE_MAP = new LinkedHashMap<>();
    static {
        GAMEMODE_MAP.put("0",  "survival");
        GAMEMODE_MAP.put("1",  "creative");
        GAMEMODE_MAP.put("2",  "adventure");
        GAMEMODE_MAP.put("3",  "spectator");
        GAMEMODE_MAP.put("s",  "survival");
        GAMEMODE_MAP.put("c",  "creative");
        GAMEMODE_MAP.put("a",  "adventure");
        GAMEMODE_MAP.put("sp", "spectator");
    }

    /**
     * Pre-1.13 particle name (lowercase, no spaces) → 1.13+ minecraft: namespaced ID (no prefix).
     * Source: https://minecraft.wiki/w/Particle#History
     */
    private static final Map<String, String> PARTICLE_MAP = new LinkedHashMap<>();
    static {
        // Particles that were renamed in 1.13
        PARTICLE_MAP.put("angryvillager",          "angry_villager");
        PARTICLE_MAP.put("barrier",                "barrier");
        PARTICLE_MAP.put("blockcrack",             "block");          // syntax also changes – handled below
        PARTICLE_MAP.put("blockdust",              "block");
        PARTICLE_MAP.put("bubble",                 "bubble");
        PARTICLE_MAP.put("bubblecolumnup",         "bubble_column_up");
        PARTICLE_MAP.put("bubblepop",              "bubble_pop");
        PARTICLE_MAP.put("cloudy",                 "item_slime");
        PARTICLE_MAP.put("crit",                   "crit");
        PARTICLE_MAP.put("damage",                 "damage_indicator");
        PARTICLE_MAP.put("depthsuspend",           "underwater");
        PARTICLE_MAP.put("dripwater",              "dripping_water");
        PARTICLE_MAP.put("driplava",               "dripping_lava");
        PARTICLE_MAP.put("droplet",                "rain");
        PARTICLE_MAP.put("enchant",                "enchant");
        PARTICLE_MAP.put("enchantmenttable",       "enchant");
        PARTICLE_MAP.put("explode",                "poof");
        PARTICLE_MAP.put("fallingdust",            "falling_dust");
        PARTICLE_MAP.put("fireworksspark",         "firework");
        PARTICLE_MAP.put("endrod",                 "end_rod");
        PARTICLE_MAP.put("flame",                  "flame");
        PARTICLE_MAP.put("footstep",               "item_snowball");  // removed; closest substitute
        PARTICLE_MAP.put("happyvillager",          "happy_villager");
        PARTICLE_MAP.put("heart",                  "heart");
        PARTICLE_MAP.put("hugeexplosion",          "explosion_emitter");
        PARTICLE_MAP.put("instantspell",           "instant_effect");
        PARTICLE_MAP.put("itemcrack",              "item");           // syntax changes – handled below
        PARTICLE_MAP.put("largeexplode",           "explosion");
        PARTICLE_MAP.put("largesmoke",             "large_smoke");
        PARTICLE_MAP.put("lavadrip",               "dripping_lava");
        PARTICLE_MAP.put("magiccrittom",           "enchanted_hit");
        PARTICLE_MAP.put("magmiccrit",             "enchanted_hit");
        PARTICLE_MAP.put("magiccrit",              "enchanted_hit");
        PARTICLE_MAP.put("mobappearance",          "elder_guardian");
        PARTICLE_MAP.put("mobspell",               "entity_effect");
        PARTICLE_MAP.put("mobspellambient",        "ambient_entity_effect");
        PARTICLE_MAP.put("note",                   "note");
        PARTICLE_MAP.put("portal",                 "portal");
        PARTICLE_MAP.put("reddust",                "dust");           // syntax changes – handled below
        PARTICLE_MAP.put("redstone",               "dust");
        PARTICLE_MAP.put("ricochet",               "item_slime");
        PARTICLE_MAP.put("selectionIndicator",     "block_marker");
        PARTICLE_MAP.put("selectionindicator",     "block_marker");
        PARTICLE_MAP.put("showparticle",           "poof");
        PARTICLE_MAP.put("slime",                  "item_slime");
        PARTICLE_MAP.put("smoke",                  "smoke");
        PARTICLE_MAP.put("snowballpoof",           "item_snowball");
        PARTICLE_MAP.put("snowshovel",             "snowflake");
        PARTICLE_MAP.put("spell",                  "effect");
        PARTICLE_MAP.put("splash",                 "splash");
        PARTICLE_MAP.put("suspended",              "underwater");
        PARTICLE_MAP.put("suspendedtownaura",      "mycelium");
        PARTICLE_MAP.put("sweepattack",            "sweep_attack");
        PARTICLE_MAP.put("take",                   "item_snowball");
        PARTICLE_MAP.put("totem",                  "totem_of_undying");
        PARTICLE_MAP.put("tomeofundying",          "totem_of_undying");
        PARTICLE_MAP.put("townAura",               "mycelium");
        PARTICLE_MAP.put("townaura",               "mycelium");
        PARTICLE_MAP.put("villagerAngry",          "angry_villager");
        PARTICLE_MAP.put("villagerHappy",          "happy_villager");
        PARTICLE_MAP.put("waterdrip",              "dripping_water");
        PARTICLE_MAP.put("waterdrip2",             "dripping_water");
        PARTICLE_MAP.put("waterwake",              "fishing");
        PARTICLE_MAP.put("witchmagic",             "witch");
        PARTICLE_MAP.put("witchMagic",             "witch");
    }

    /**
     * Old sound event IDs → modern sound event IDs (without minecraft: prefix).
     * Covers renames from 1.12.2 → 1.21.10 (entity renames, restructured categories).
     */
    private static final Map<String, String> SOUND_MAP = new LinkedHashMap<>();
    static {
        // ── Zombie Pigman → Zombified Piglin (1.16) ─────────────────────────
        SOUND_MAP.put("entity.zombie_pigman.ambient",       "entity.zombified_piglin.ambient");
        SOUND_MAP.put("entity.zombie_pigman.angry",         "entity.zombified_piglin.angry");
        SOUND_MAP.put("entity.zombie_pigman.death",         "entity.zombified_piglin.death");
        SOUND_MAP.put("entity.zombie_pigman.hurt",          "entity.zombified_piglin.hurt");
        // ── Horse subtypes split (1.9+) ─────────────────────────────────────
        SOUND_MAP.put("entity.horse.donkey.ambient",        "entity.donkey.ambient");
        SOUND_MAP.put("entity.horse.donkey.angry",          "entity.donkey.angry");
        SOUND_MAP.put("entity.horse.donkey.death",          "entity.donkey.death");
        SOUND_MAP.put("entity.horse.donkey.hurt",           "entity.donkey.hurt");
        SOUND_MAP.put("entity.horse.skeleton.ambient",      "entity.skeleton_horse.ambient");
        SOUND_MAP.put("entity.horse.skeleton.death",        "entity.skeleton_horse.death");
        SOUND_MAP.put("entity.horse.skeleton.hurt",         "entity.skeleton_horse.hurt");
        SOUND_MAP.put("entity.horse.zombie.ambient",        "entity.zombie_horse.ambient");
        SOUND_MAP.put("entity.horse.zombie.death",          "entity.zombie_horse.death");
        SOUND_MAP.put("entity.horse.zombie.hurt",           "entity.zombie_horse.hurt");
        // ── Pre-1.9 legacy sound names (dot-separated) ─────────────────────
        SOUND_MAP.put("mob.zombie.say",                     "entity.zombie.ambient");
        SOUND_MAP.put("mob.zombie.hurt",                    "entity.zombie.hurt");
        SOUND_MAP.put("mob.zombie.death",                   "entity.zombie.death");
        SOUND_MAP.put("mob.zombie.step",                    "entity.zombie.step");
        SOUND_MAP.put("mob.zombie.infect",                  "entity.zombie.infect");
        SOUND_MAP.put("mob.zombie.unfect",                  "entity.zombie_villager.converted");
        SOUND_MAP.put("mob.zombie.remedy",                  "entity.zombie_villager.cure");
        SOUND_MAP.put("mob.zombie.metal",                   "entity.zombie.attack_iron_door");
        SOUND_MAP.put("mob.zombie.wood",                    "entity.zombie.attack_wooden_door");
        SOUND_MAP.put("mob.zombie.woodbreak",               "entity.zombie.break_wooden_door");
        SOUND_MAP.put("mob.skeleton.say",                   "entity.skeleton.ambient");
        SOUND_MAP.put("mob.skeleton.hurt",                  "entity.skeleton.hurt");
        SOUND_MAP.put("mob.skeleton.death",                 "entity.skeleton.death");
        SOUND_MAP.put("mob.skeleton.step",                  "entity.skeleton.step");
        SOUND_MAP.put("mob.spider.say",                     "entity.spider.ambient");
        SOUND_MAP.put("mob.spider.death",                   "entity.spider.death");
        SOUND_MAP.put("mob.spider.step",                    "entity.spider.step");
        SOUND_MAP.put("mob.creeper.say",                    "entity.creeper.hurt");
        SOUND_MAP.put("mob.creeper.death",                  "entity.creeper.death");
        SOUND_MAP.put("mob.endermen.stare",                 "entity.enderman.stare");
        SOUND_MAP.put("mob.endermen.idle",                  "entity.enderman.ambient");
        SOUND_MAP.put("mob.endermen.death",                 "entity.enderman.death");
        SOUND_MAP.put("mob.endermen.hit",                   "entity.enderman.hurt");
        SOUND_MAP.put("mob.endermen.portal",                "entity.enderman.teleport");
        SOUND_MAP.put("mob.endermen.scream",                "entity.enderman.scream");
        SOUND_MAP.put("mob.pig.say",                        "entity.pig.ambient");
        SOUND_MAP.put("mob.pig.death",                      "entity.pig.death");
        SOUND_MAP.put("mob.pig.step",                       "entity.pig.step");
        SOUND_MAP.put("mob.cow.say",                        "entity.cow.ambient");
        SOUND_MAP.put("mob.cow.hurt",                       "entity.cow.hurt");
        SOUND_MAP.put("mob.cow.step",                       "entity.cow.step");
        SOUND_MAP.put("mob.cow.death",                      "entity.cow.death");
        SOUND_MAP.put("mob.chicken.say",                    "entity.chicken.ambient");
        SOUND_MAP.put("mob.chicken.hurt",                   "entity.chicken.hurt");
        SOUND_MAP.put("mob.chicken.step",                   "entity.chicken.step");
        SOUND_MAP.put("mob.chicken.plop",                   "entity.chicken.egg");
        SOUND_MAP.put("mob.sheep.say",                      "entity.sheep.ambient");
        SOUND_MAP.put("mob.sheep.shear",                    "entity.sheep.shear");
        SOUND_MAP.put("mob.sheep.step",                     "entity.sheep.step");
        SOUND_MAP.put("mob.villager.yes",                   "entity.villager.yes");
        SOUND_MAP.put("mob.villager.no",                    "entity.villager.no");
        SOUND_MAP.put("mob.villager.haggle",                "entity.villager.trade");
        SOUND_MAP.put("mob.villager.idle",                  "entity.villager.ambient");
        SOUND_MAP.put("mob.villager.death",                 "entity.villager.death");
        SOUND_MAP.put("mob.villager.hurt",                  "entity.villager.hurt");
        SOUND_MAP.put("mob.wolf.bark",                      "entity.wolf.ambient");
        SOUND_MAP.put("mob.wolf.growl",                     "entity.wolf.growl");
        SOUND_MAP.put("mob.wolf.whine",                     "entity.wolf.whine");
        SOUND_MAP.put("mob.wolf.panting",                   "entity.wolf.pant");
        SOUND_MAP.put("mob.wolf.death",                     "entity.wolf.death");
        SOUND_MAP.put("mob.wolf.hurt",                      "entity.wolf.hurt");
        SOUND_MAP.put("mob.wolf.shake",                     "entity.wolf.shake");
        SOUND_MAP.put("mob.wolf.step",                      "entity.wolf.step");
        SOUND_MAP.put("mob.cat.meow",                       "entity.cat.ambient");
        SOUND_MAP.put("mob.cat.purr",                       "entity.cat.purr");
        SOUND_MAP.put("mob.cat.hiss",                       "entity.cat.hiss");
        SOUND_MAP.put("mob.cat.hitt",                       "entity.cat.hurt");
        SOUND_MAP.put("mob.cat.purreow",                    "entity.cat.purreow");
        SOUND_MAP.put("mob.blaze.breathe",                  "entity.blaze.ambient");
        SOUND_MAP.put("mob.blaze.death",                    "entity.blaze.death");
        SOUND_MAP.put("mob.blaze.hit",                      "entity.blaze.hurt");
        SOUND_MAP.put("mob.ghast.moan",                     "entity.ghast.ambient");
        SOUND_MAP.put("mob.ghast.scream",                   "entity.ghast.hurt");
        SOUND_MAP.put("mob.ghast.death",                    "entity.ghast.death");
        SOUND_MAP.put("mob.ghast.fireball",                 "entity.ghast.shoot");
        SOUND_MAP.put("mob.ghast.charge",                   "entity.ghast.warn");
        SOUND_MAP.put("mob.slime.big",                      "entity.slime.squish");
        SOUND_MAP.put("mob.slime.small",                    "entity.slime.squish_small");
        SOUND_MAP.put("mob.slime.attack",                   "entity.slime.attack");
        SOUND_MAP.put("mob.magmacube.big",                  "entity.magma_cube.squish");
        SOUND_MAP.put("mob.magmacube.small",                "entity.magma_cube.squish_small");
        SOUND_MAP.put("mob.magmacube.jump",                 "entity.magma_cube.jump");
        SOUND_MAP.put("mob.irongolem.death",                "entity.iron_golem.death");
        SOUND_MAP.put("mob.irongolem.hit",                  "entity.iron_golem.hurt");
        SOUND_MAP.put("mob.irongolem.throw",                "entity.iron_golem.attack");
        SOUND_MAP.put("mob.irongolem.walk",                 "entity.iron_golem.step");
        SOUND_MAP.put("mob.bat.idle",                       "entity.bat.ambient");
        SOUND_MAP.put("mob.bat.death",                      "entity.bat.death");
        SOUND_MAP.put("mob.bat.hurt",                       "entity.bat.hurt");
        SOUND_MAP.put("mob.bat.takeoff",                    "entity.bat.takeoff");
        SOUND_MAP.put("mob.bat.loop",                       "entity.bat.loop");
        SOUND_MAP.put("mob.wither.idle",                    "entity.wither.ambient");
        SOUND_MAP.put("mob.wither.death",                   "entity.wither.death");
        SOUND_MAP.put("mob.wither.hurt",                    "entity.wither.hurt");
        SOUND_MAP.put("mob.wither.shoot",                   "entity.wither.shoot");
        SOUND_MAP.put("mob.wither.spawn",                   "entity.wither.spawn");
        SOUND_MAP.put("mob.guardian.hit",                    "entity.guardian.hurt");
        SOUND_MAP.put("mob.guardian.idle",                   "entity.guardian.ambient");
        SOUND_MAP.put("mob.guardian.death",                  "entity.guardian.death");
        SOUND_MAP.put("mob.guardian.elder.idle",             "entity.elder_guardian.ambient");
        SOUND_MAP.put("mob.guardian.elder.death",            "entity.elder_guardian.death");
        SOUND_MAP.put("mob.guardian.elder.hit",              "entity.elder_guardian.hurt");
        SOUND_MAP.put("mob.guardian.curse",                  "entity.elder_guardian.curse");
        SOUND_MAP.put("mob.horse.idle",                     "entity.horse.ambient");
        SOUND_MAP.put("mob.horse.death",                    "entity.horse.death");
        SOUND_MAP.put("mob.horse.hurt",                     "entity.horse.hurt");
        SOUND_MAP.put("mob.horse.armor",                    "entity.horse.armor");
        SOUND_MAP.put("mob.horse.land",                     "entity.horse.land");
        SOUND_MAP.put("mob.horse.saddle",                   "entity.horse.saddle");
        SOUND_MAP.put("mob.horse.gallop",                   "entity.horse.gallop");
        SOUND_MAP.put("mob.horse.jump",                     "entity.horse.jump");
        SOUND_MAP.put("mob.horse.angry",                    "entity.horse.angry");
        SOUND_MAP.put("mob.horse.breathe",                  "entity.horse.breathe");
        SOUND_MAP.put("mob.horse.leather",                  "entity.horse.step");
        SOUND_MAP.put("mob.horse.wood",                     "entity.horse.step_wood");
        SOUND_MAP.put("mob.horse.soft",                     "entity.horse.step");
        SOUND_MAP.put("mob.horse.donkey.idle",              "entity.donkey.ambient");
        SOUND_MAP.put("mob.horse.donkey.death",             "entity.donkey.death");
        SOUND_MAP.put("mob.horse.donkey.hit",               "entity.donkey.hurt");
        SOUND_MAP.put("mob.horse.donkey.angry",             "entity.donkey.angry");
        SOUND_MAP.put("mob.horse.skeleton.idle",            "entity.skeleton_horse.ambient");
        SOUND_MAP.put("mob.horse.skeleton.death",           "entity.skeleton_horse.death");
        SOUND_MAP.put("mob.horse.skeleton.hit",             "entity.skeleton_horse.hurt");
        SOUND_MAP.put("mob.horse.zombie.idle",              "entity.zombie_horse.ambient");
        SOUND_MAP.put("mob.horse.zombie.death",             "entity.zombie_horse.death");
        SOUND_MAP.put("mob.horse.zombie.hit",               "entity.zombie_horse.hurt");
        // ── Pre-1.9 non-mob sounds ──────────────────────────────────────────
        SOUND_MAP.put("random.bow",                         "entity.arrow.shoot");
        SOUND_MAP.put("random.bowhit",                      "entity.arrow.hit");
        SOUND_MAP.put("random.break",                       "entity.item.break");
        SOUND_MAP.put("random.burp",                        "entity.player.burp");
        SOUND_MAP.put("random.chestclosed",                 "block.chest.close");
        SOUND_MAP.put("random.chestopen",                   "block.chest.open");
        SOUND_MAP.put("random.click",                       "block.lever.click");
        SOUND_MAP.put("random.door_close",                  "block.wooden_door.close");
        SOUND_MAP.put("random.door_open",                   "block.wooden_door.open");
        SOUND_MAP.put("random.drink",                       "entity.generic.drink");
        SOUND_MAP.put("random.eat",                         "entity.generic.eat");
        SOUND_MAP.put("random.explode",                     "entity.generic.explode");
        SOUND_MAP.put("random.fizz",                        "block.fire.extinguish");
        SOUND_MAP.put("random.fuse",                        "entity.tnt.primed");
        SOUND_MAP.put("random.glass",                       "block.glass.break");
        SOUND_MAP.put("random.levelup",                     "entity.player.levelup");
        SOUND_MAP.put("random.orb",                         "entity.experience_orb.pickup");
        SOUND_MAP.put("random.pop",                         "entity.item.pickup");
        SOUND_MAP.put("random.splash",                      "entity.generic.splash");
        SOUND_MAP.put("random.swim",                        "entity.generic.swim");
        SOUND_MAP.put("random.wood_click",                  "block.wood_button.click_on");
        SOUND_MAP.put("random.anvil_break",                 "block.anvil.destroy");
        SOUND_MAP.put("random.anvil_land",                  "block.anvil.land");
        SOUND_MAP.put("random.anvil_use",                   "block.anvil.use");
        SOUND_MAP.put("random.successful_hit",              "entity.player.attack.strong");
        SOUND_MAP.put("damage.hit",                         "entity.player.hurt");
        SOUND_MAP.put("damage.hurtflesh",                   "entity.player.hurt");
        SOUND_MAP.put("damage.fallbig",                     "entity.generic.big_fall");
        SOUND_MAP.put("damage.fallsmall",                   "entity.generic.small_fall");
        // ── Records ─────────────────────────────────────────────────────────
        SOUND_MAP.put("records.11",                         "music_disc.11");
        SOUND_MAP.put("records.13",                         "music_disc.13");
        SOUND_MAP.put("records.blocks",                     "music_disc.blocks");
        SOUND_MAP.put("records.cat",                        "music_disc.cat");
        SOUND_MAP.put("records.chirp",                      "music_disc.chirp");
        SOUND_MAP.put("records.far",                        "music_disc.far");
        SOUND_MAP.put("records.mall",                       "music_disc.mall");
        SOUND_MAP.put("records.mellohi",                    "music_disc.mellohi");
        SOUND_MAP.put("records.stal",                       "music_disc.stal");
        SOUND_MAP.put("records.strad",                      "music_disc.strad");
        SOUND_MAP.put("records.wait",                       "music_disc.wait");
        SOUND_MAP.put("records.ward",                       "music_disc.ward");
        // ── Dig/step sounds ─────────────────────────────────────────────────
        SOUND_MAP.put("dig.stone",                          "block.stone.break");
        SOUND_MAP.put("dig.wood",                           "block.wood.break");
        SOUND_MAP.put("dig.grass",                          "block.grass.break");
        SOUND_MAP.put("dig.gravel",                         "block.gravel.break");
        SOUND_MAP.put("dig.sand",                           "block.sand.break");
        SOUND_MAP.put("dig.snow",                           "block.snow.break");
        SOUND_MAP.put("dig.cloth",                          "block.wool.break");
        SOUND_MAP.put("dig.glass",                          "block.glass.break");
        SOUND_MAP.put("step.stone",                         "block.stone.step");
        SOUND_MAP.put("step.wood",                          "block.wood.step");
        SOUND_MAP.put("step.grass",                         "block.grass.step");
        SOUND_MAP.put("step.gravel",                        "block.gravel.step");
        SOUND_MAP.put("step.sand",                          "block.sand.step");
        SOUND_MAP.put("step.snow",                          "block.snow.step");
        SOUND_MAP.put("step.cloth",                         "block.wool.step");
        SOUND_MAP.put("step.ladder",                        "block.ladder.step");
        // ── Fire / liquid ───────────────────────────────────────────────────
        SOUND_MAP.put("fire.fire",                          "block.fire.ambient");
        SOUND_MAP.put("fire.ignite",                        "item.flintandsteel.use");
        SOUND_MAP.put("liquid.water",                       "block.water.ambient");
        SOUND_MAP.put("liquid.lava",                        "block.lava.ambient");
        SOUND_MAP.put("liquid.lavapop",                     "block.lava.pop");
        SOUND_MAP.put("liquid.splash",                      "entity.generic.splash");
        // ── Note blocks ─────────────────────────────────────────────────────
        SOUND_MAP.put("note.bass",                          "block.note_block.bass");
        SOUND_MAP.put("note.bassattack",                    "block.note_block.bass");
        SOUND_MAP.put("note.bd",                            "block.note_block.basedrum");
        SOUND_MAP.put("note.harp",                          "block.note_block.harp");
        SOUND_MAP.put("note.hat",                           "block.note_block.hat");
        SOUND_MAP.put("note.pling",                         "block.note_block.pling");
        SOUND_MAP.put("note.snare",                         "block.note_block.snare");
        // ── Misc ────────────────────────────────────────────────────────────
        SOUND_MAP.put("portal.portal",                      "block.portal.ambient");
        SOUND_MAP.put("portal.travel",                      "block.portal.travel");
        SOUND_MAP.put("portal.trigger",                     "block.portal.trigger");
        SOUND_MAP.put("gui.button.press",                   "ui.button.click");
        SOUND_MAP.put("tile.piston.in",                     "block.piston.contract");
        SOUND_MAP.put("tile.piston.out",                    "block.piston.extend");
        SOUND_MAP.put("fireworks.blast",                    "entity.firework_rocket.blast");
        SOUND_MAP.put("fireworks.blast_far",                "entity.firework_rocket.blast_far");
        SOUND_MAP.put("fireworks.largeBlast",               "entity.firework_rocket.large_blast");
        SOUND_MAP.put("fireworks.largeBlast_far",           "entity.firework_rocket.large_blast_far");
        SOUND_MAP.put("fireworks.launch",                   "entity.firework_rocket.launch");
        SOUND_MAP.put("fireworks.twinkle",                  "entity.firework_rocket.twinkle");
        SOUND_MAP.put("fireworks.twinkle_far",              "entity.firework_rocket.twinkle_far");
        SOUND_MAP.put("ambient.cave.cave",                  "ambient.cave");
        SOUND_MAP.put("ambient.weather.rain",               "weather.rain.above");
        SOUND_MAP.put("ambient.weather.thunder",            "entity.lightning_bolt.thunder");
        SOUND_MAP.put("minecart.base",                      "entity.minecart.riding");
        SOUND_MAP.put("minecart.inside",                    "entity.minecart.inside");
    }

    /** Numeric effect IDs (1.12.2 and earlier) → modern minecraft: namespaced effect name */
    private static final Map<String, String> EFFECT_ID_MAP = new LinkedHashMap<>();
    static {
        EFFECT_ID_MAP.put("1",  "minecraft:speed");
        EFFECT_ID_MAP.put("2",  "minecraft:slowness");
        EFFECT_ID_MAP.put("3",  "minecraft:haste");
        EFFECT_ID_MAP.put("4",  "minecraft:mining_fatigue");
        EFFECT_ID_MAP.put("5",  "minecraft:strength");
        EFFECT_ID_MAP.put("6",  "minecraft:instant_health");
        EFFECT_ID_MAP.put("7",  "minecraft:instant_damage");
        EFFECT_ID_MAP.put("8",  "minecraft:jump_boost");
        EFFECT_ID_MAP.put("9",  "minecraft:nausea");
        EFFECT_ID_MAP.put("10", "minecraft:regeneration");
        EFFECT_ID_MAP.put("11", "minecraft:resistance");
        EFFECT_ID_MAP.put("12", "minecraft:fire_resistance");
        EFFECT_ID_MAP.put("13", "minecraft:water_breathing");
        EFFECT_ID_MAP.put("14", "minecraft:invisibility");
        EFFECT_ID_MAP.put("15", "minecraft:blindness");
        EFFECT_ID_MAP.put("16", "minecraft:night_vision");
        EFFECT_ID_MAP.put("17", "minecraft:hunger");
        EFFECT_ID_MAP.put("18", "minecraft:weakness");
        EFFECT_ID_MAP.put("19", "minecraft:poison");
        EFFECT_ID_MAP.put("20", "minecraft:wither");
        EFFECT_ID_MAP.put("21", "minecraft:health_boost");
        EFFECT_ID_MAP.put("22", "minecraft:absorption");
        EFFECT_ID_MAP.put("23", "minecraft:saturation");
        EFFECT_ID_MAP.put("24", "minecraft:glowing");
        EFFECT_ID_MAP.put("25", "minecraft:levitation");
        EFFECT_ID_MAP.put("26", "minecraft:luck");
        EFFECT_ID_MAP.put("27", "minecraft:unluck");
        EFFECT_ID_MAP.put("28", "minecraft:slow_falling");
        EFFECT_ID_MAP.put("29", "minecraft:conduit_power");
        EFFECT_ID_MAP.put("30", "minecraft:dolphins_grace");
        EFFECT_ID_MAP.put("31", "minecraft:bad_omen");
        EFFECT_ID_MAP.put("32", "minecraft:hero_of_the_village");
        EFFECT_ID_MAP.put("33", "minecraft:darkness");
    }

    /** Old effect names (pre-1.13, no namespace) → modern minecraft: namespaced effect name */
    private static final Map<String, String> EFFECT_NAME_MAP = new LinkedHashMap<>();
    static {
        EFFECT_NAME_MAP.put("speed",            "minecraft:speed");
        EFFECT_NAME_MAP.put("slowness",         "minecraft:slowness");
        EFFECT_NAME_MAP.put("haste",            "minecraft:haste");
        EFFECT_NAME_MAP.put("mining_fatigue",   "minecraft:mining_fatigue");
        EFFECT_NAME_MAP.put("strength",         "minecraft:strength");
        EFFECT_NAME_MAP.put("instant_health",   "minecraft:instant_health");
        EFFECT_NAME_MAP.put("instant_damage",   "minecraft:instant_damage");
        EFFECT_NAME_MAP.put("jump_boost",       "minecraft:jump_boost");
        EFFECT_NAME_MAP.put("nausea",           "minecraft:nausea");
        EFFECT_NAME_MAP.put("regeneration",     "minecraft:regeneration");
        EFFECT_NAME_MAP.put("resistance",       "minecraft:resistance");
        EFFECT_NAME_MAP.put("fire_resistance",  "minecraft:fire_resistance");
        EFFECT_NAME_MAP.put("water_breathing",  "minecraft:water_breathing");
        EFFECT_NAME_MAP.put("invisibility",     "minecraft:invisibility");
        EFFECT_NAME_MAP.put("blindness",        "minecraft:blindness");
        EFFECT_NAME_MAP.put("night_vision",     "minecraft:night_vision");
        EFFECT_NAME_MAP.put("hunger",           "minecraft:hunger");
        EFFECT_NAME_MAP.put("weakness",         "minecraft:weakness");
        EFFECT_NAME_MAP.put("poison",           "minecraft:poison");
        EFFECT_NAME_MAP.put("wither",           "minecraft:wither");
        EFFECT_NAME_MAP.put("health_boost",     "minecraft:health_boost");
        EFFECT_NAME_MAP.put("absorption",       "minecraft:absorption");
        EFFECT_NAME_MAP.put("saturation",       "minecraft:saturation");
        EFFECT_NAME_MAP.put("glowing",          "minecraft:glowing");
        EFFECT_NAME_MAP.put("levitation",       "minecraft:levitation");
        EFFECT_NAME_MAP.put("luck",                "minecraft:luck");
        EFFECT_NAME_MAP.put("unluck",              "minecraft:unluck");
        EFFECT_NAME_MAP.put("slow_falling",        "minecraft:slow_falling");
        EFFECT_NAME_MAP.put("conduit_power",       "minecraft:conduit_power");
        EFFECT_NAME_MAP.put("dolphins_grace",      "minecraft:dolphins_grace");
        EFFECT_NAME_MAP.put("bad_omen",            "minecraft:bad_omen");
        EFFECT_NAME_MAP.put("hero_of_the_village", "minecraft:hero_of_the_village");
        EFFECT_NAME_MAP.put("darkness",            "minecraft:darkness");
        EFFECT_NAME_MAP.put("trial_omen",          "minecraft:trial_omen");
        EFFECT_NAME_MAP.put("raid_omen",           "minecraft:raid_omen");
        EFFECT_NAME_MAP.put("wind_charged",        "minecraft:wind_charged");
        EFFECT_NAME_MAP.put("weaving",             "minecraft:weaving");
        EFFECT_NAME_MAP.put("oozing",              "minecraft:oozing");
        EFFECT_NAME_MAP.put("infested",            "minecraft:infested");
        // Old alternative names used in pre-1.13 commands
        EFFECT_NAME_MAP.put("moveSpeed",           "minecraft:speed");
        EFFECT_NAME_MAP.put("moveSlowdown",        "minecraft:slowness");
        EFFECT_NAME_MAP.put("digSpeed",            "minecraft:haste");
        EFFECT_NAME_MAP.put("digSlowDown",         "minecraft:mining_fatigue");
        EFFECT_NAME_MAP.put("digSlowdown",         "minecraft:mining_fatigue");
        EFFECT_NAME_MAP.put("damageBoost",         "minecraft:strength");
        EFFECT_NAME_MAP.put("heal",                "minecraft:instant_health");
        EFFECT_NAME_MAP.put("harm",                "minecraft:instant_damage");
        EFFECT_NAME_MAP.put("jump",                "minecraft:jump_boost");
        EFFECT_NAME_MAP.put("confusion",           "minecraft:nausea");
        EFFECT_NAME_MAP.put("fireResistance",      "minecraft:fire_resistance");
        EFFECT_NAME_MAP.put("waterBreathing",      "minecraft:water_breathing");
        EFFECT_NAME_MAP.put("nightVision",         "minecraft:night_vision");
        EFFECT_NAME_MAP.put("healthBoost",         "minecraft:health_boost");
    }

    /**
     * Maps "baseBlockId:dataValue" → modern namespaced block ID.
     * Key format: lowercase base name (no minecraft: prefix) + ":" + data value integer.
     * Used to resolve /fill, /setblock, /clone etc. that still have trailing numeric data values.
     */
    private static final Map<String, String> BLOCK_DATA_MAP = new LinkedHashMap<>();
    static {
        // ── Wool ──────────────────────────────────────────────────────────────
        String[] wool = {"white","orange","magenta","light_blue","yellow","lime",
                         "pink","gray","light_gray","cyan","purple","blue",
                         "brown","green","red","black"};
        for (int i = 0; i < wool.length; i++) {
            BLOCK_DATA_MAP.put("wool:" + i,              "minecraft:" + wool[i] + "_wool");
            BLOCK_DATA_MAP.put("minecraft:wool:" + i,   "minecraft:" + wool[i] + "_wool");
        }

        // ── Stained Glass ─────────────────────────────────────────────────────
        for (int i = 0; i < wool.length; i++) {
            BLOCK_DATA_MAP.put("stained_glass:" + i,             "minecraft:" + wool[i] + "_stained_glass");
            BLOCK_DATA_MAP.put("minecraft:stained_glass:" + i,   "minecraft:" + wool[i] + "_stained_glass");
        }

        // ── Stained Glass Pane ────────────────────────────────────────────────
        for (int i = 0; i < wool.length; i++) {
            BLOCK_DATA_MAP.put("stained_glass_pane:" + i,           "minecraft:" + wool[i] + "_stained_glass_pane");
            BLOCK_DATA_MAP.put("minecraft:stained_glass_pane:" + i, "minecraft:" + wool[i] + "_stained_glass_pane");
        }

        // ── Concrete ──────────────────────────────────────────────────────────
        for (int i = 0; i < wool.length; i++) {
            BLOCK_DATA_MAP.put("concrete:" + i,             "minecraft:" + wool[i] + "_concrete");
            BLOCK_DATA_MAP.put("minecraft:concrete:" + i,   "minecraft:" + wool[i] + "_concrete");
        }

        // ── Concrete Powder ───────────────────────────────────────────────────
        for (int i = 0; i < wool.length; i++) {
            BLOCK_DATA_MAP.put("concrete_powder:" + i,           "minecraft:" + wool[i] + "_concrete_powder");
            BLOCK_DATA_MAP.put("minecraft:concrete_powder:" + i, "minecraft:" + wool[i] + "_concrete_powder");
        }

        // ── Carpet ────────────────────────────────────────────────────────────
        for (int i = 0; i < wool.length; i++) {
            BLOCK_DATA_MAP.put("carpet:" + i,             "minecraft:" + wool[i] + "_carpet");
            BLOCK_DATA_MAP.put("minecraft:carpet:" + i,   "minecraft:" + wool[i] + "_carpet");
        }

        // ── Terracotta (Hardened Clay) ────────────────────────────────────────
        for (int i = 0; i < wool.length; i++) {
            BLOCK_DATA_MAP.put("stained_hardened_clay:" + i,           "minecraft:" + wool[i] + "_terracotta");
            BLOCK_DATA_MAP.put("minecraft:stained_hardened_clay:" + i, "minecraft:" + wool[i] + "_terracotta");
        }

        // ── Glazed Terracotta ────────────────────────────────────────────────
        for (int i = 0; i < wool.length; i++) {
            BLOCK_DATA_MAP.put("glazed_terracotta:" + i,           "minecraft:" + wool[i] + "_glazed_terracotta");
            BLOCK_DATA_MAP.put("minecraft:glazed_terracotta:" + i, "minecraft:" + wool[i] + "_glazed_terracotta");
        }

        // ── Shulker Box ────────────────────────────────────────────────────────
        for (int i = 0; i < wool.length; i++) {
            BLOCK_DATA_MAP.put("shulker_box:" + i,           "minecraft:" + wool[i] + "_shulker_box");
            BLOCK_DATA_MAP.put("minecraft:shulker_box:" + i, "minecraft:" + wool[i] + "_shulker_box");
        }

        // ── Dye / Ink (banner colors etc.) ────────────────────────────────────
        for (int i = 0; i < wool.length; i++) {
            BLOCK_DATA_MAP.put("dye:" + i,             "minecraft:" + wool[i] + "_dye");
            BLOCK_DATA_MAP.put("minecraft:dye:" + i,   "minecraft:" + wool[i] + "_dye");
        }

        // ── Log (1.12: data 0-3 = type, 4-7 = axis) ──────────────────────────
        String[] logTypes = {"oak","spruce","birch","jungle"};
        for (int i = 0; i < logTypes.length; i++) {
            BLOCK_DATA_MAP.put("log:" + i,             "minecraft:" + logTypes[i] + "_log");
            BLOCK_DATA_MAP.put("minecraft:log:" + i,   "minecraft:" + logTypes[i] + "_log");
            BLOCK_DATA_MAP.put("log:" + (i + 4),       "minecraft:" + logTypes[i] + "_log[axis=x]");
            BLOCK_DATA_MAP.put("minecraft:log:" + (i + 4), "minecraft:" + logTypes[i] + "_log[axis=x]");
            BLOCK_DATA_MAP.put("log:" + (i + 8),       "minecraft:" + logTypes[i] + "_log[axis=z]");
            BLOCK_DATA_MAP.put("minecraft:log:" + (i + 8), "minecraft:" + logTypes[i] + "_log[axis=z]");
            BLOCK_DATA_MAP.put("log:" + (i + 12),      "minecraft:" + logTypes[i] + "_log[axis=y]");
        }
        String[] log2Types = {"acacia","dark_oak"};
        for (int i = 0; i < log2Types.length; i++) {
            BLOCK_DATA_MAP.put("log2:" + i,            "minecraft:" + log2Types[i] + "_log");
            BLOCK_DATA_MAP.put("minecraft:log2:" + i,  "minecraft:" + log2Types[i] + "_log");
        }

        // ── Planks ────────────────────────────────────────────────────────────
        String[] plankTypes = {"oak","spruce","birch","jungle","acacia","dark_oak"};
        for (int i = 0; i < plankTypes.length; i++) {
            BLOCK_DATA_MAP.put("planks:" + i,            "minecraft:" + plankTypes[i] + "_planks");
            BLOCK_DATA_MAP.put("minecraft:planks:" + i,  "minecraft:" + plankTypes[i] + "_planks");
        }

        // ── Leaves ────────────────────────────────────────────────────────────
        String[] leavesTypes = {"oak","spruce","birch","jungle"};
        for (int i = 0; i < leavesTypes.length; i++) {
            BLOCK_DATA_MAP.put("leaves:" + i,            "minecraft:" + leavesTypes[i] + "_leaves");
            BLOCK_DATA_MAP.put("minecraft:leaves:" + i,  "minecraft:" + leavesTypes[i] + "_leaves");
        }
        String[] leaves2Types = {"acacia","dark_oak"};
        for (int i = 0; i < leaves2Types.length; i++) {
            BLOCK_DATA_MAP.put("leaves2:" + i,           "minecraft:" + leaves2Types[i] + "_leaves");
            BLOCK_DATA_MAP.put("minecraft:leaves2:" + i, "minecraft:" + leaves2Types[i] + "_leaves");
        }

        // ── Saplings ──────────────────────────────────────────────────────────
        String[] saplingTypes = {"oak","spruce","birch","jungle","acacia","dark_oak"};
        for (int i = 0; i < saplingTypes.length; i++) {
            BLOCK_DATA_MAP.put("sapling:" + i,           "minecraft:" + saplingTypes[i] + "_sapling");
            BLOCK_DATA_MAP.put("minecraft:sapling:" + i, "minecraft:" + saplingTypes[i] + "_sapling");
        }

        // ── Sand ──────────────────────────────────────────────────────────────
        BLOCK_DATA_MAP.put("sand:0",             "minecraft:sand");
        BLOCK_DATA_MAP.put("minecraft:sand:0",   "minecraft:sand");
        BLOCK_DATA_MAP.put("sand:1",             "minecraft:red_sand");
        BLOCK_DATA_MAP.put("minecraft:sand:1",   "minecraft:red_sand");

        // ── Stone variants ────────────────────────────────────────────────────
        BLOCK_DATA_MAP.put("stone:0",  "minecraft:stone");
        BLOCK_DATA_MAP.put("stone:1",  "minecraft:granite");
        BLOCK_DATA_MAP.put("stone:2",  "minecraft:polished_granite");
        BLOCK_DATA_MAP.put("stone:3",  "minecraft:diorite");
        BLOCK_DATA_MAP.put("stone:4",  "minecraft:polished_diorite");
        BLOCK_DATA_MAP.put("stone:5",  "minecraft:andesite");
        BLOCK_DATA_MAP.put("stone:6",  "minecraft:polished_andesite");
        for (int i = 0; i <= 6; i++) BLOCK_DATA_MAP.put("minecraft:stone:" + i, BLOCK_DATA_MAP.get("stone:" + i));

        // ── Sandstone ────────────────────────────────────────────────────────
        BLOCK_DATA_MAP.put("sandstone:0",  "minecraft:sandstone");
        BLOCK_DATA_MAP.put("sandstone:1",  "minecraft:chiseled_sandstone");
        BLOCK_DATA_MAP.put("sandstone:2",  "minecraft:cut_sandstone");
        BLOCK_DATA_MAP.put("red_sandstone:0", "minecraft:red_sandstone");
        BLOCK_DATA_MAP.put("red_sandstone:1", "minecraft:chiseled_red_sandstone");
        BLOCK_DATA_MAP.put("red_sandstone:2", "minecraft:cut_red_sandstone");
        for (int i = 0; i <= 2; i++) {
            BLOCK_DATA_MAP.put("minecraft:sandstone:" + i,     BLOCK_DATA_MAP.get("sandstone:" + i));
            BLOCK_DATA_MAP.put("minecraft:red_sandstone:" + i, BLOCK_DATA_MAP.get("red_sandstone:" + i));
        }

        // ── Quartz ────────────────────────────────────────────────────────────
        BLOCK_DATA_MAP.put("quartz_block:0",  "minecraft:quartz_block");
        BLOCK_DATA_MAP.put("quartz_block:1",  "minecraft:chiseled_quartz_block");
        BLOCK_DATA_MAP.put("quartz_block:2",  "minecraft:quartz_pillar");
        BLOCK_DATA_MAP.put("minecraft:quartz_block:0", "minecraft:quartz_block");
        BLOCK_DATA_MAP.put("minecraft:quartz_block:1", "minecraft:chiseled_quartz_block");
        BLOCK_DATA_MAP.put("minecraft:quartz_block:2", "minecraft:quartz_pillar");

        // ── Prismarine ────────────────────────────────────────────────────────
        BLOCK_DATA_MAP.put("prismarine:0",  "minecraft:prismarine");
        BLOCK_DATA_MAP.put("prismarine:1",  "minecraft:prismarine_bricks");
        BLOCK_DATA_MAP.put("prismarine:2",  "minecraft:dark_prismarine");
        BLOCK_DATA_MAP.put("minecraft:prismarine:0", "minecraft:prismarine");
        BLOCK_DATA_MAP.put("minecraft:prismarine:1", "minecraft:prismarine_bricks");
        BLOCK_DATA_MAP.put("minecraft:prismarine:2", "minecraft:dark_prismarine");

        // ── Sponge ────────────────────────────────────────────────────────────
        BLOCK_DATA_MAP.put("sponge:0", "minecraft:sponge");
        BLOCK_DATA_MAP.put("sponge:1", "minecraft:wet_sponge");
        BLOCK_DATA_MAP.put("minecraft:sponge:0", "minecraft:sponge");
        BLOCK_DATA_MAP.put("minecraft:sponge:1", "minecraft:wet_sponge");

        // ── Dirt ──────────────────────────────────────────────────────────────
        BLOCK_DATA_MAP.put("dirt:0",  "minecraft:dirt");
        BLOCK_DATA_MAP.put("dirt:1",  "minecraft:coarse_dirt");
        BLOCK_DATA_MAP.put("dirt:2",  "minecraft:podzol");
        BLOCK_DATA_MAP.put("minecraft:dirt:0", "minecraft:dirt");
        BLOCK_DATA_MAP.put("minecraft:dirt:1", "minecraft:coarse_dirt");
        BLOCK_DATA_MAP.put("minecraft:dirt:2", "minecraft:podzol");

        // ── Cobblestone (data 0 = plain; no variants but still seen with :0) ──
        BLOCK_DATA_MAP.put("cobblestone:0",          "minecraft:cobblestone");
        BLOCK_DATA_MAP.put("minecraft:cobblestone:0","minecraft:cobblestone");

        // ── Air / data 0 stripping (common leftover) ──────────────────────────
        BLOCK_DATA_MAP.put("air:0",           "minecraft:air");
        BLOCK_DATA_MAP.put("minecraft:air:0", "minecraft:air");

        // ── Glass (no variants; data always 0) ───────────────────────────────
        BLOCK_DATA_MAP.put("glass:0",           "minecraft:glass");
        BLOCK_DATA_MAP.put("minecraft:glass:0", "minecraft:glass");

        // ── Stone Brick ───────────────────────────────────────────────────────
        BLOCK_DATA_MAP.put("stonebrick:0",  "minecraft:stone_bricks");
        BLOCK_DATA_MAP.put("stonebrick:1",  "minecraft:mossy_stone_bricks");
        BLOCK_DATA_MAP.put("stonebrick:2",  "minecraft:cracked_stone_bricks");
        BLOCK_DATA_MAP.put("stonebrick:3",  "minecraft:chiseled_stone_bricks");
        BLOCK_DATA_MAP.put("minecraft:stonebrick:0", "minecraft:stone_bricks");
        BLOCK_DATA_MAP.put("minecraft:stonebrick:1", "minecraft:mossy_stone_bricks");
        BLOCK_DATA_MAP.put("minecraft:stonebrick:2", "minecraft:cracked_stone_bricks");
        BLOCK_DATA_MAP.put("minecraft:stonebrick:3", "minecraft:chiseled_stone_bricks");

        // ── Flowers ───────────────────────────────────────────────────────────
        String[] flowers1 = {"dandelion","poppy","blue_orchid","allium",
                              "azure_bluet","red_tulip","orange_tulip",
                              "white_tulip","pink_tulip","oxeye_daisy"};
        // yellow_flower = dandelion, red_flower = poppy+variants
        BLOCK_DATA_MAP.put("yellow_flower:0",          "minecraft:dandelion");
        BLOCK_DATA_MAP.put("minecraft:yellow_flower:0","minecraft:dandelion");
        String[] redFlowers = {"poppy","blue_orchid","allium","azure_bluet",
                               "red_tulip","orange_tulip","white_tulip","pink_tulip","oxeye_daisy"};
        for (int i = 0; i < redFlowers.length; i++) {
            BLOCK_DATA_MAP.put("red_flower:" + i,           "minecraft:" + redFlowers[i]);
            BLOCK_DATA_MAP.put("minecraft:red_flower:" + i, "minecraft:" + redFlowers[i]);
        }

        // ── Double plant (tall flowers) ───────────────────────────────────────
        String[] tallPlants = {"sunflower","lilac","tall_grass","large_fern","rose_bush","peony"};
        for (int i = 0; i < tallPlants.length; i++) {
            BLOCK_DATA_MAP.put("double_plant:" + i,           "minecraft:" + tallPlants[i]);
            BLOCK_DATA_MAP.put("minecraft:double_plant:" + i, "minecraft:" + tallPlants[i]);
        }

        // ── Slab (stone_slab) ─────────────────────────────────────────────────
        String[] slabTypes = {"stone","sandstone","oak","cobblestone",
                               "brick","stone_brick","nether_brick","quartz"};
        for (int i = 0; i < slabTypes.length; i++) {
            String modern = slabTypes[i].equals("stone") ? "minecraft:smooth_stone_slab"
                          : slabTypes[i].equals("stone_brick") ? "minecraft:stone_brick_slab"
                          : slabTypes[i].equals("nether_brick") ? "minecraft:nether_brick_slab"
                          : "minecraft:" + slabTypes[i] + "_slab";
            BLOCK_DATA_MAP.put("stone_slab:" + i,           modern);
            BLOCK_DATA_MAP.put("minecraft:stone_slab:" + i, modern);
        }

        // ── Slab2 (stone_slab2) ─────────────────────────────────────────────
        String[] slab2Types = {"red_sandstone","purpur","prismarine","dark_prismarine"};
        for (int i = 0; i < slab2Types.length; i++) {
            String modern = "minecraft:" + slab2Types[i] + "_slab";
            BLOCK_DATA_MAP.put("stone_slab2:" + i,           modern);
            BLOCK_DATA_MAP.put("minecraft:stone_slab2:" + i, modern);
        }

        // ── Wooden slab (wooden_slab) ───────────────────────────────────────
        String[] woodSlabTypes = {"oak","spruce","birch","jungle","acacia","dark_oak"};
        for (int i = 0; i < woodSlabTypes.length; i++) {
            String modern = "minecraft:" + woodSlabTypes[i] + "_slab";
            BLOCK_DATA_MAP.put("wooden_slab:" + i,           modern);
            BLOCK_DATA_MAP.put("minecraft:wooden_slab:" + i, modern);
        }

        // ── Bed (data value colors) ─────────────────────────────────────────
        String[] bedColors = {"white","orange","magenta","light_blue","yellow","lime",
                              "pink","gray","light_gray","cyan","purple","blue",
                              "brown","green","red","black"};
        for (int i = 0; i < bedColors.length; i++) {
            BLOCK_DATA_MAP.put("bed:" + i,           "minecraft:" + bedColors[i] + "_bed");
            BLOCK_DATA_MAP.put("minecraft:bed:" + i, "minecraft:" + bedColors[i] + "_bed");
        }

        // ── Monster Egg → Infested blocks ───────────────────────────────────
        String[] infestedTypes = {"infested_stone","infested_cobblestone",
                                  "infested_stone_bricks","infested_mossy_stone_bricks",
                                  "infested_cracked_stone_bricks","infested_chiseled_stone_bricks"};
        for (int i = 0; i < infestedTypes.length; i++) {
            BLOCK_DATA_MAP.put("monster_egg:" + i,           "minecraft:" + infestedTypes[i]);
            BLOCK_DATA_MAP.put("minecraft:monster_egg:" + i, "minecraft:" + infestedTypes[i]);
        }

        // ── Skull → Head/Skull variants ─────────────────────────────────────
        String[] skullTypes = {"skeleton_skull","wither_skeleton_skull",
                               "zombie_head","player_head","creeper_head","dragon_head"};
        for (int i = 0; i < skullTypes.length; i++) {
            BLOCK_DATA_MAP.put("skull:" + i,           "minecraft:" + skullTypes[i]);
            BLOCK_DATA_MAP.put("minecraft:skull:" + i, "minecraft:" + skullTypes[i]);
        }

        // ── Dirt variants ───────────────────────────────────────────────────
        // dirt:0 = dirt (unchanged), dirt:1 = coarse_dirt, dirt:2 = podzol
        // (already partially covered, ensure completeness)

        // ── Flowing water/lava ──────────────────────────────────────────────
        // flowing_water/flowing_lava data values 0-15 → water/lava[level=N]
        for (int i = 0; i <= 15; i++) {
            BLOCK_DATA_MAP.put("flowing_water:" + i,           "minecraft:water[level=" + i + "]");
            BLOCK_DATA_MAP.put("minecraft:flowing_water:" + i, "minecraft:water[level=" + i + "]");
            BLOCK_DATA_MAP.put("flowing_lava:" + i,            "minecraft:lava[level=" + i + "]");
            BLOCK_DATA_MAP.put("minecraft:flowing_lava:" + i,  "minecraft:lava[level=" + i + "]");
        }
    }

    /** Block/item IDs renamed in the 1.13 flattening (no data-value involved). */
    private static final Map<String, String> BLOCK_ITEM_RENAME = new LinkedHashMap<>();
    static {
        // Wood-type renames (wooden → oak)
        BLOCK_ITEM_RENAME.put("wooden_button",         "oak_button");
        BLOCK_ITEM_RENAME.put("wooden_pressure_plate",  "oak_pressure_plate");
        BLOCK_ITEM_RENAME.put("wooden_door",            "oak_door");
        BLOCK_ITEM_RENAME.put("wooden_slab",            "oak_slab");
        BLOCK_ITEM_RENAME.put("fence",                  "oak_fence");
        BLOCK_ITEM_RENAME.put("fence_gate",             "oak_fence_gate");
        BLOCK_ITEM_RENAME.put("wooden_stairs",          "oak_stairs");
        // Nether brick renames
        BLOCK_ITEM_RENAME.put("nether_brick_fence",     "nether_brick_fence");
        // Stone variants
        BLOCK_ITEM_RENAME.put("stone_stairs",           "cobblestone_stairs");
        BLOCK_ITEM_RENAME.put("brick_stairs",           "brick_stairs");
        BLOCK_ITEM_RENAME.put("stone_brick_stairs",     "stone_brick_stairs");
        // Misc block renames
        BLOCK_ITEM_RENAME.put("lit_pumpkin",            "jack_o_lantern");
        BLOCK_ITEM_RENAME.put("lit_furnace",            "furnace");
        BLOCK_ITEM_RENAME.put("lit_redstone_lamp",      "redstone_lamp");
        BLOCK_ITEM_RENAME.put("lit_redstone_ore",       "redstone_ore");
        BLOCK_ITEM_RENAME.put("unlit_redstone_torch",   "redstone_torch");
        BLOCK_ITEM_RENAME.put("powered_comparator",     "comparator");
        BLOCK_ITEM_RENAME.put("unpowered_comparator",   "comparator");
        BLOCK_ITEM_RENAME.put("powered_repeater",       "repeater");
        BLOCK_ITEM_RENAME.put("unpowered_repeater",     "repeater");
        BLOCK_ITEM_RENAME.put("standing_sign",          "oak_sign");
        BLOCK_ITEM_RENAME.put("wall_sign",              "oak_wall_sign");
        BLOCK_ITEM_RENAME.put("standing_banner",        "white_banner");
        BLOCK_ITEM_RENAME.put("wall_banner",            "white_wall_banner");
        BLOCK_ITEM_RENAME.put("daylight_detector_inverted", "daylight_detector");
        BLOCK_ITEM_RENAME.put("deadbush",               "dead_bush");
        BLOCK_ITEM_RENAME.put("tallgrass",              "short_grass");
        BLOCK_ITEM_RENAME.put("grass",                  "grass_block");
        BLOCK_ITEM_RENAME.put("snow_layer",             "snow");
        BLOCK_ITEM_RENAME.put("snow",                   "snow_block");
        BLOCK_ITEM_RENAME.put("waterlily",              "lily_pad");
        BLOCK_ITEM_RENAME.put("web",                    "cobweb");
        BLOCK_ITEM_RENAME.put("netherbrick",            "nether_bricks");
        BLOCK_ITEM_RENAME.put("hardened_clay",          "terracotta");
        BLOCK_ITEM_RENAME.put("noteblock",              "note_block");
        BLOCK_ITEM_RENAME.put("mob_spawner",            "spawner");
        BLOCK_ITEM_RENAME.put("trapdoor",               "oak_trapdoor");
        BLOCK_ITEM_RENAME.put("golden_rail",            "powered_rail");
        BLOCK_ITEM_RENAME.put("magma",                  "magma_block");
        BLOCK_ITEM_RENAME.put("slime",                  "slime_block");
        BLOCK_ITEM_RENAME.put("end_bricks",             "end_stone_bricks");
        BLOCK_ITEM_RENAME.put("red_nether_brick",       "red_nether_bricks");
        BLOCK_ITEM_RENAME.put("melon_block",            "melon");
        BLOCK_ITEM_RENAME.put("pumpkin_stem",           "pumpkin_stem");
        BLOCK_ITEM_RENAME.put("melon_stem",             "melon_stem");
        // Double slab / technical blocks
        BLOCK_ITEM_RENAME.put("double_stone_slab",      "smooth_stone");
        BLOCK_ITEM_RENAME.put("double_stone_slab2",     "smooth_red_sandstone");
        BLOCK_ITEM_RENAME.put("double_wooden_slab",     "oak_slab");
        // Item renames
        BLOCK_ITEM_RENAME.put("reeds",                  "sugar_cane");
        BLOCK_ITEM_RENAME.put("melon_seeds",            "melon_seeds");
        BLOCK_ITEM_RENAME.put("pumpkin_seeds",          "pumpkin_seeds");
        BLOCK_ITEM_RENAME.put("speckled_melon",         "glistering_melon_slice");
        BLOCK_ITEM_RENAME.put("fireworks",              "firework_rocket");
        BLOCK_ITEM_RENAME.put("firework_charge",        "firework_star");
        BLOCK_ITEM_RENAME.put("netherbrick_item",       "nether_brick");
        BLOCK_ITEM_RENAME.put("record_13",              "music_disc_13");
        BLOCK_ITEM_RENAME.put("record_cat",             "music_disc_cat");
        BLOCK_ITEM_RENAME.put("record_blocks",          "music_disc_blocks");
        BLOCK_ITEM_RENAME.put("record_chirp",           "music_disc_chirp");
        BLOCK_ITEM_RENAME.put("record_far",             "music_disc_far");
        BLOCK_ITEM_RENAME.put("record_mall",            "music_disc_mall");
        BLOCK_ITEM_RENAME.put("record_mellohi",         "music_disc_mellohi");
        BLOCK_ITEM_RENAME.put("record_stal",            "music_disc_stal");
        BLOCK_ITEM_RENAME.put("record_strad",           "music_disc_strad");
        BLOCK_ITEM_RENAME.put("record_ward",            "music_disc_ward");
        BLOCK_ITEM_RENAME.put("record_11",              "music_disc_11");
        BLOCK_ITEM_RENAME.put("record_wait",            "music_disc_wait");
        BLOCK_ITEM_RENAME.put("boat",                   "oak_boat");
        BLOCK_ITEM_RENAME.put("bed",                    "red_bed");
        BLOCK_ITEM_RENAME.put("chorus_fruit_popped",    "popped_chorus_fruit");
        BLOCK_ITEM_RENAME.put("stonebrick",             "stone_bricks");
        BLOCK_ITEM_RENAME.put("brick_block",            "bricks");
        BLOCK_ITEM_RENAME.put("nether_brick",           "nether_bricks");
        // ── More block renames from 1.12.2 flattening ───────────────────────
        BLOCK_ITEM_RENAME.put("monster_egg",            "infested_stone");
        BLOCK_ITEM_RENAME.put("skull",                  "skeleton_skull");
        BLOCK_ITEM_RENAME.put("silver_shulker_box",     "light_gray_shulker_box");
        BLOCK_ITEM_RENAME.put("silver_glazed_terracotta", "light_gray_glazed_terracotta");
        BLOCK_ITEM_RENAME.put("stained_hardened_clay",  "white_terracotta");
        BLOCK_ITEM_RENAME.put("log2",                   "acacia_log");
        BLOCK_ITEM_RENAME.put("leaves2",                "acacia_leaves");
        BLOCK_ITEM_RENAME.put("wooden_door",            "oak_door");
        BLOCK_ITEM_RENAME.put("stone_stairs",           "cobblestone_stairs");
        BLOCK_ITEM_RENAME.put("wooden_stairs",          "oak_stairs");
        BLOCK_ITEM_RENAME.put("piston_extension",       "piston_head");
        BLOCK_ITEM_RENAME.put("map",                    "filled_map");
        // ── Flattened blocks: default (data value 0) mappings ─────────────
        // These blocks were split by color/variant in 1.13. When used without
        // a data value, they default to the data-value-0 variant.
        BLOCK_ITEM_RENAME.put("wool",                    "white_wool");
        BLOCK_ITEM_RENAME.put("stained_glass",           "white_stained_glass");
        BLOCK_ITEM_RENAME.put("stained_glass_pane",      "white_stained_glass_pane");
        BLOCK_ITEM_RENAME.put("concrete",                "white_concrete");
        BLOCK_ITEM_RENAME.put("concrete_powder",         "white_concrete_powder");
        BLOCK_ITEM_RENAME.put("carpet",                  "white_carpet");
        BLOCK_ITEM_RENAME.put("shulker_box",             "white_shulker_box");
        BLOCK_ITEM_RENAME.put("glazed_terracotta",       "white_glazed_terracotta");
        BLOCK_ITEM_RENAME.put("dye",                     "white_dye");
        BLOCK_ITEM_RENAME.put("log",                     "oak_log");
        BLOCK_ITEM_RENAME.put("planks",                  "oak_planks");
        BLOCK_ITEM_RENAME.put("leaves",                  "oak_leaves");
        BLOCK_ITEM_RENAME.put("sapling",                 "oak_sapling");
        BLOCK_ITEM_RENAME.put("stone_slab",              "smooth_stone_slab");
        BLOCK_ITEM_RENAME.put("stone_slab2",             "red_sandstone_slab");
        BLOCK_ITEM_RENAME.put("wooden_slab",             "oak_slab");
        BLOCK_ITEM_RENAME.put("red_flower",              "poppy");
        BLOCK_ITEM_RENAME.put("yellow_flower",           "dandelion");
        BLOCK_ITEM_RENAME.put("double_plant",            "sunflower");
        BLOCK_ITEM_RENAME.put("flowing_water",           "water");
        BLOCK_ITEM_RENAME.put("flowing_lava",            "lava");
    }

    /** New-style /execute subcommand keywords (signals this is already 1.13+ syntax) */
    private static final Set<String> NEW_EXECUTE_KEYWORDS = new HashSet<>(Arrays.asList(
        "as", "at", "in", "facing", "anchored", "if", "unless",
        "store", "run", "positioned", "rotated", "align", "summon", "on"
    ));

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Main entry point. Migrates a single command string to 1.21.10 syntax.
     * Leading slash is preserved if present.
     */
    public static MigrationResult migrate(String rawCommand) {
        if (rawCommand == null || rawCommand.isBlank()) {
            return new MigrationResult(rawCommand, rawCommand, new ArrayList<>());
        }

        String cmd = rawCommand.trim();
        List<String> changes = new ArrayList<>();

        // Strip leading/trailing whitespace — MC 1.12.2 tolerated it, 1.21.10 does not
        if (!rawCommand.equals(cmd)) {
            changes.add("Stripped leading/trailing whitespace from command");
        }

        // Step 1: Top-level command name / structure migrations
        cmd = migrateTopLevel(cmd, changes);

        // Step 2: Migrate selector arguments anywhere in the command
        cmd = migrateSelectorsInString(cmd, changes);

        // Step 3: Migrate text component JSON keys anywhere in the command
        cmd = migrateTextComponents(cmd, changes);

        // Step 4: Rename old block/item IDs (e.g. minecraft:wooden_button → minecraft:oak_button)
        cmd = renameBlockItemIds(cmd, changes);

        return new MigrationResult(rawCommand, cmd, changes);
    }

    // -------------------------------------------------------------------------
    // Top-level command migrations
    // -------------------------------------------------------------------------

    private static String migrateTopLevel(String cmd, List<String> changes) {
        boolean slash = cmd.startsWith("/");
        String body = slash ? cmd.substring(1) : cmd;
        String lower = body.toLowerCase();


        // ── /fill <x1> <y1> <z1> <x2> <y2> <z2> <block> [dataVal] [mode]
        if (lower.startsWith("fill ")) {
            String[] p = tokenize(body.substring(5).trim());
            if (p.length >= 7) {
                String coords = p[0]+" "+p[1]+" "+p[2]+" "+p[3]+" "+p[4]+" "+p[5];
                String remaining = joinFrom(p, 7);
                String[] resolved = resolveBlockAndData(p[6], remaining, changes);
                // In 1.21, only "replace" mode accepts a filter block.
                // Convert "destroy <filter>" or "keep <filter>" to "replace <filter>"
                // since the filter is the essential part.
                String rest = resolved[1];
                String[] restTokens = tokenize(rest);
                if (restTokens.length >= 2) {
                    String mode = restTokens[0].toLowerCase();
                    if (mode.equals("destroy") || mode.equals("keep")) {
                        String filterBlock = normalizeBlockId(restTokens[1]);
                        rest = "replace " + filterBlock;
                        changes.add("Converted fill mode '" + mode + "' with filter block to 'replace' (1.21 only supports filter with replace)");
                    }
                }
                if (!resolved[0].equalsIgnoreCase(p[6]) || !rest.equals(remaining.trim())) {
                    return prefix(slash) + "fill " + coords + " " + resolved[0]
                        + (rest.isEmpty() ? "" : " " + rest);
                }
            }
        }

        // ── /setblock <x> <y> <z> <block> [dataVal] [mode]
        if (lower.startsWith("setblock ")) {
            String[] p = tokenize(body.substring(9).trim());
            if (p.length >= 4) {
                String coords = p[0]+" "+p[1]+" "+p[2];
                String remaining = joinFrom(p, 4);
                String[] resolved = resolveBlockAndData(p[3], remaining, changes);
                if (!resolved[0].equalsIgnoreCase(p[3]) || !resolved[1].equals(remaining.trim())) {
                    return prefix(slash) + "setblock " + coords + " " + resolved[0]
                        + (resolved[1].isEmpty() ? "" : " " + resolved[1]);
                }
            }
        }

        // ── /particle <name> <x> <y> <z> <xd> <yd> <zd> <speed> [count] [force|normal] ──
        // Old 1.12: /particle witchMagic x y z xd yd zd speed [count] [mode]
        // New 1.13: /particle minecraft:witch x y z xd yd zd speed [count] [force|normal]
        // Special cases: reddust → dust r g b size  |  blockcrack/blockdust → block <blockstate>
        //                itemcrack id damage         → item minecraft:id
        if (lower.startsWith("particle ")) {
            String rest = body.substring(9).trim();
            String[] p = tokenize(rest);
            if (p.length >= 1) {
                String rawName = p[0];
                String nameLower = rawName.toLowerCase();

                // Check for renames — even if already namespaced (e.g. minecraft:endrod → minecraft:end_rod)
                String lookupKey = nameLower.startsWith("minecraft:") ? nameLower.substring(10) : nameLower;
                String lookupKeyRaw = rawName.startsWith("minecraft:") ? rawName.substring(10) : rawName;
                boolean wasNamespaced = nameLower.contains(":");

                if (!wasNamespaced || PARTICLE_MAP.containsKey(lookupKey) || PARTICLE_MAP.containsKey(lookupKeyRaw)) {
                    String mapped = PARTICLE_MAP.get(lookupKeyRaw);
                    if (mapped == null) mapped = PARTICLE_MAP.get(lookupKey);

                    if (mapped != null) {
                        String newName = "minecraft:" + mapped;
                        String rebuilt;

                        // ── dust (was reddust): needs r g b size appended ──────
                        if (mapped.equals("dust") && p.length >= 8) {
                            // old: reddust x y z xd yd zd speed [count]
                            // 1.21+: particle dust{color:[R,G,B],scale:SIZE} x y z dx dy dz speed count
                            // We use xd yd zd as r g b (they were color params in legacy), speed as size
                            String coords = p[1] + " " + p[2] + " " + p[3];
                            String r = p[4], g = p[5], b = p[6];
                            String size = p[7];
                            if (size.equals("0")) size = "1.0";
                            String count = p.length > 8 ? " " + p[8] : " 0";
                            rebuilt = prefix(slash) + "particle " + newName
                                + "{color:[" + r + "," + g + "," + b + "],scale:" + size + "}"
                                + " " + coords + " 0 0 0 0" + count;
                            changes.add("Converted /particle reddust → /particle minecraft:dust with r g b size");
                        }
                        // ── block (was blockcrack / blockdust) ────────────────
                        else if (mapped.equals("block") && p.length >= 4) {
                            // extra params p[1..3]=coords, p[4..6]=delta, p[7]=speed, blockId comes after
                            // old blockcrack: blockcrack x y z xd yd zd speed blockId meta
                            // new: minecraft:block <block_state> x y z xd yd zd speed count
                            String blockId = p.length > 8 ? normalizeBlockId(p[8]) : "minecraft:stone";
                            String rest2 = p[1] + " " + p[2] + " " + p[3] + " "
                                         + p[4] + " " + p[5] + " " + p[6] + " " + p[7];
                            String count = p.length > 9 ? " " + p[9] : " 1";
                            rebuilt = prefix(slash) + "particle " + newName + " " + blockId
                                + " " + rest2 + count;
                            changes.add("Converted /particle " + rawName + " → /particle minecraft:block <blockstate>");
                        }
                        // ── item (was itemcrack id damage) ───────────────────
                        else if (mapped.equals("item") && p.length >= 4) {
                            // old: itemcrack x y z xd yd zd speed itemId damage
                            String itemId = p.length > 8 ? normalizeItemId(p[8]) : "minecraft:stone";
                            String rest2 = p[1] + " " + p[2] + " " + p[3] + " "
                                         + p[4] + " " + p[5] + " " + p[6] + " " + p[7];
                            String count = p.length > 9 ? " " + p[9] : " 1";
                            rebuilt = prefix(slash) + "particle " + newName + " " + itemId
                                + " " + rest2 + count;
                            changes.add("Converted /particle itemcrack → /particle minecraft:item <item>");
                        }
                        // ── Standard rename: keep all other args as-is ────────
                        else {
                            String argsAfterName = rest.substring(rawName.length()).trim();
                            rebuilt = prefix(slash) + "particle " + newName
                                + (argsAfterName.isEmpty() ? "" : " " + argsAfterName);
                            changes.add("Converted /particle " + rawName + " → /particle " + newName);
                        }

                        return rebuilt;
                    }
                    // If not in our map and not already namespaced, add minecraft: prefix
                    if (!wasNamespaced) {
                        String argsAfterName = rest.substring(rawName.length()).trim();
                        changes.add("Added minecraft: namespace to unknown particle: " + rawName);
                        return prefix(slash) + "particle minecraft:" + lookupKey
                            + (argsAfterName.isEmpty() ? "" : " " + argsAfterName);
                    }
                }
            }
        }

        // ── /playsound <sound> <source> <player> [x y z] [volume] [pitch] [minVolume]
        if (lower.startsWith("playsound ")) {
            String rest = body.substring(10).trim();
            String[] p = tokenize(rest);
            if (p.length >= 1) {
                String rawSound = p[0];
                String soundLower = rawSound.toLowerCase();

                // Strip minecraft: prefix for lookup, then re-add it
                String bareSound = soundLower.startsWith("minecraft:") ? soundLower.substring(10) : soundLower;
                String mapped = SOUND_MAP.get(bareSound);
                // Also try original case for mixed-case legacy names
                if (mapped == null) mapped = SOUND_MAP.get(rawSound.startsWith("minecraft:") ? rawSound.substring(10) : rawSound);

                boolean wasRenamed = (mapped != null);
                String newSound = "minecraft:" + (mapped != null ? mapped : bareSound);
                boolean wasNamespaced = rawSound.toLowerCase().startsWith("minecraft:");

                if (wasRenamed || !wasNamespaced) {
                    String argsAfterSound = rest.substring(rawSound.length()).trim();
                    if (wasRenamed) {
                        changes.add("Converted /playsound " + rawSound + " → " + newSound);
                    } else {
                        changes.add("Added minecraft: namespace to /playsound sound: " + rawSound);
                    }
                    return prefix(slash) + "playsound " + newSound
                        + (argsAfterSound.isEmpty() ? "" : " " + argsAfterSound);
                }
            }
        }

        // ── /stopsound <player> [source] [sound] ───────────────────────────
        if (lower.startsWith("stopsound ")) {
            String rest = body.substring(10).trim();
            String[] p = tokenize(rest);
            // stopsound <player> [source] [sound]  — sound is p[2] if present
            if (p.length >= 3) {
                String rawSound = p[2];
                String soundLower = rawSound.toLowerCase();
                String bareSound = soundLower.startsWith("minecraft:") ? soundLower.substring(10) : soundLower;
                String mapped = SOUND_MAP.get(bareSound);
                if (mapped == null) mapped = SOUND_MAP.get(rawSound.startsWith("minecraft:") ? rawSound.substring(10) : rawSound);

                boolean wasRenamed = (mapped != null);
                String newSound = "minecraft:" + (mapped != null ? mapped : bareSound);
                boolean wasNamespaced = rawSound.toLowerCase().startsWith("minecraft:");

                if (wasRenamed || !wasNamespaced) {
                    if (wasRenamed) {
                        changes.add("Converted /stopsound sound " + rawSound + " → " + newSound);
                    } else {
                        changes.add("Added minecraft: namespace to /stopsound sound: " + rawSound);
                    }
                    return prefix(slash) + "stopsound " + p[0] + " " + p[1] + " " + newSound;
                }
            }
        }

        // ── /testfor <selector> [dataTag] ─────────────────────────────────────
        if (lower.startsWith("testfor ")) {
            String args = body.substring(8).trim();
            String selector = extractSelector(args);
            String afterSel = args.substring(selector.length()).trim();
            if (!afterSel.isEmpty() && afterSel.charAt(0) == '{') {
                String nbtSelector = mergeNbtIntoSelector(selector, afterSel);
                changes.add("Converted /testfor with NBT dataTag to /execute if entity");
                return prefix(slash) + "execute if entity " + nbtSelector;
            }
            changes.add("Converted /testfor to /execute if entity");
            return prefix(slash) + "execute if entity " + selector;
        }

        // ── /testforblock <x> <y> <z> <block> [dataValue] [mode] ─────────────
        if (lower.startsWith("testforblock ")) {
            String[] p = tokenize(body.substring(13).trim());
            if (p.length >= 4) {
                String block = normalizeBlockId(p[3]);
                // Convert data value to block state properties if present
                if (p.length >= 5) {
                    try {
                        int dataValue = Integer.parseInt(p[4]);
                        block = dataValueToBlockState(block, dataValue, changes);
                    } catch (NumberFormatException ignored) {
                        // p[4] might be the mode string, not a data value
                    }
                }
                changes.add("Converted /testforblock to /execute if block");
                return prefix(slash) + "execute if block " + p[0] + " " + p[1] + " " + p[2] + " " + block;
            }
        }

        // ── /testforblocks <x1> <y1> <z1> <x2> <y2> <z2> <x> <y> <z> [mode] ─
        if (lower.startsWith("testforblocks ")) {
            String[] p = tokenize(body.substring(14).trim());
            if (p.length >= 9) {
                String mode = (p.length >= 10 && p[9].equalsIgnoreCase("masked")) ? "masked" : "all";
                changes.add("Converted /testforblocks to /execute if blocks");
                return prefix(slash) + "execute if blocks "
                    + p[0] + " " + p[1] + " " + p[2] + " "
                    + p[3] + " " + p[4] + " " + p[5] + " "
                    + p[6] + " " + p[7] + " " + p[8] + " " + mode;
            }
        }

        // ── /blockdata <x> <y> <z> <dataTag> ─────────────────────────────────
        if (lower.startsWith("blockdata ")) {
            String[] p = tokenize(body.substring(10).trim());
            if (p.length >= 4) {
                String coords = p[0] + " " + p[1] + " " + p[2];
                String nbt = joinFrom(p, 3);
                changes.add("Converted /blockdata to /data merge block");
                return prefix(slash) + "data merge block " + coords + " " + nbt;
            }
        }

        // ── /entitydata <selector> <dataTag> ─────────────────────────────────
        if (lower.startsWith("entitydata ")) {
            String rest = body.substring(11).trim();
            String selector = extractSelector(rest);
            String nbt = rest.substring(selector.length()).trim();
            changes.add("Converted /entitydata to /data merge entity");
            if (selector.startsWith("@e")) {
                String enforced = enforceSingleEntity(selector);
                if (!enforced.equals(selector)) {
                    changes.add("Added limit=1,sort=nearest to @e selector in /data command (requires single entity)");
                    selector = enforced;
                }
            }
            return prefix(slash) + "data merge entity " + selector + " " + nbt;
        }

        // ── /data (merge|get|modify|remove) entity @e[...] ───────────────────
        // In 1.13+ /data entity commands require exactly one entity.
        // If the selector is @e without limit=1, inject limit=1,sort=nearest.
        if (lower.startsWith("data ")) {
            String rest = body.substring(5).trim();
            String[] p = tokenize(rest);
            // p[0] = subcommand (merge/get/modify/remove), p[1] = "entity" or "block"
            if (p.length >= 3 && p[1].equalsIgnoreCase("entity")) {
                String afterEntityKw = rest.substring(p[0].length()).trim(); // "entity @e[...] ..."
                afterEntityKw = afterEntityKw.substring("entity".length()).trim(); // "@e[...] ..."
                String selector = extractSelector(afterEntityKw);
                if (selector.startsWith("@e")) {
                    String enforced = enforceSingleEntity(selector);
                    if (!enforced.equals(selector)) {
                        String afterSelector = afterEntityKw.substring(selector.length());
                        changes.add("Added limit=1,sort=nearest to @e selector in /data command (requires single entity)");
                        return prefix(slash) + "data " + p[0] + " entity " + enforced + afterSelector;
                    }
                }
            }
        }

        // ── /clear <player> [item] [data] [maxCount] ────────────────────────
        // Old 1.12: /clear <player> <item> <dataValue> <maxCount>
        // New 1.13+: /clear <targets> [<item>] [<maxCount>]
        // Data value is dropped; maxCount is preserved if present.
        if (lower.startsWith("clear ")) {
            String rest = body.substring(6).trim();
            String selector = extractSelector(rest);
            String afterSel = rest.substring(selector.length()).trim();
            String[] p = tokenize(afterSel);
            // p[0]=item, p[1]=dataValue, p[2]=maxCount
            if (p.length >= 2) {
                String item = normalizeItemId(p[0]);
                if (p.length >= 3) {
                    // Has data value AND maxCount — drop data value, keep maxCount
                    String maxCount = p[2];
                    changes.add("Converted old /clear syntax: dropped data value " + p[1] + ", kept maxCount");
                    return prefix(slash) + "clear " + selector + " " + item + " " + maxCount;
                } else {
                    // Has data value only — drop it
                    changes.add("Converted old /clear syntax: dropped data value " + p[1]);
                    return prefix(slash) + "clear " + selector + " " + item;
                }
            }
        }

        // ── /replaceitem ──────────────────────────────────────────────────────
        if (lower.startsWith("replaceitem ")) {
            String[] p = tokenize(body.substring(12).trim());
            if (p.length >= 5 && p[0].equalsIgnoreCase("block")) {
                // replaceitem block <x> <y> <z> <slot> <item> [amount] [data] [nbt]
                String coords = p[1] + " " + p[2] + " " + p[3];
                String slot = normalizeSlot(p[4]);
                String item = p.length > 5 ? normalizeItemId(p[5]) : "air";
                changes.add("Converted /replaceitem to /item replace block");
                return prefix(slash) + "item replace block " + coords + " " + slot + " with " + item;
            } else if (p.length >= 4 && p[0].equalsIgnoreCase("entity")) {
                // replaceitem entity <selector> <slot> <item> [amount] [data] [nbt]
                String selector = p[1];
                String slot = normalizeSlot(p[2]);
                String item = p.length > 3 ? normalizeItemId(p[3]) : "air";
                changes.add("Converted /replaceitem to /item replace entity");
                return prefix(slash) + "item replace entity " + selector + " " + slot + " with " + item;
            }
        }

        // ── /effect old syntax ────────────────────────────────────────────────
        // Old: /effect <entity> <effect> [seconds] [amplifier] [hideParticles]
        // Old: /effect <entity> clear
        // New: /effect give <entity> <effect> ...  /  /effect clear <entity>
        // Also handles numeric effect IDs and missing namespaces in both old and new style.
        if (lower.startsWith("effect ")) {
            String rest = body.substring(7).trim();
            String[] p = tokenize(rest);
            if (p.length >= 1) {
                String firstLower = p[0].toLowerCase();
                if (!firstLower.equals("give") && !firstLower.equals("clear")) {
                    // Old style — p[0] is the selector/player
                    String selector = extractSelector(rest);
                    String afterSelector = rest.substring(selector.length()).trim();
                    if (afterSelector.equalsIgnoreCase("clear")) {
                        changes.add("Converted /effect <entity> clear to /effect clear <entity>");
                        return prefix(slash) + "effect clear " + selector;
                    } else if (!afterSelector.isEmpty()) {
                        String[] ep = tokenize(afterSelector);
                        String resolvedEffect = resolveEffectName(ep[0]);
                        String remaining = afterSelector.substring(ep[0].length()).trim();
                        // Duration 0 means clear in old syntax
                        if (remaining.equals("0") || remaining.startsWith("0 ")) {
                            if (!resolvedEffect.equals(ep[0])) {
                                changes.add("Converted effect ID/name " + ep[0] + " → " + resolvedEffect);
                            }
                            changes.add("Converted /effect with duration 0 to /effect clear");
                            return prefix(slash) + "effect clear " + selector + " " + resolvedEffect;
                        }
                        if (!resolvedEffect.equals(ep[0])) {
                            changes.add("Converted effect ID/name " + ep[0] + " → " + resolvedEffect);
                        }
                        changes.add("Converted /effect <entity> <effect> to /effect give <entity> <effect>");
                        return prefix(slash) + "effect give " + selector + " " + resolvedEffect
                            + (remaining.isEmpty() ? "" : " " + remaining);
                    }
                } else if (firstLower.equals("give") && p.length >= 3) {
                    // New style but may have numeric ID or missing namespace
                    // effect give <selector> <effect> [duration] [amplifier] [hideParticles]
                    String selector = extractSelector(rest.substring(p[0].length()).trim());
                    String afterGiveSel = rest.substring(p[0].length()).trim()
                        .substring(selector.length()).trim();
                    String[] ep = tokenize(afterGiveSel);
                    if (ep.length >= 1) {
                        String resolvedEffect = resolveEffectName(ep[0]);
                        String remaining = afterGiveSel.substring(ep[0].length()).trim();
                        // Duration 0 means clear in old syntax
                        if (remaining.equals("0") || remaining.startsWith("0 ")) {
                            changes.add("Converted /effect give with duration 0 to /effect clear");
                            return prefix(slash) + "effect clear " + selector + " " + resolvedEffect;
                        }
                        if (!resolvedEffect.equals(ep[0])) {
                            changes.add("Converted effect ID/name " + ep[0] + " → " + resolvedEffect);
                            return prefix(slash) + "effect give " + selector + " " + resolvedEffect
                                + (remaining.isEmpty() ? "" : " " + remaining);
                        }
                    }
                }
            }
        }

        // ── /tp / /teleport — enforce single-entity selectors ────────────────
        // In 1.13+ /tp <target> <destination> requires single entities for
        // the destination when using an entity selector.
        if (lower.startsWith("tp ") || lower.startsWith("teleport ")) {
            String cmdName = lower.startsWith("tp ") ? "tp" : "teleport";
            String rest = body.substring(cmdName.length() + 1).trim();
            String selector1 = extractSelector(rest);
            String afterSel1 = rest.substring(selector1.length()).trim();
            if (!afterSel1.isEmpty()) {
                String selector2 = extractSelector(afterSel1);
                String afterSel2 = afterSel1.substring(selector2.length()).trim();
                // If second arg is an @e selector (entity-to-entity tp), enforce limit=1
                boolean changed = false;
                String newSel1 = selector1;
                String newSel2 = selector2;
                if (selector1.startsWith("@e")) {
                    String enforced = enforceSingleEntity(selector1);
                    if (!enforced.equals(selector1)) { newSel1 = enforced; changed = true; }
                }
                if (selector2.startsWith("@e") && afterSel2.isEmpty()) {
                    // Second arg is entity destination (not coords)
                    String enforced = enforceSingleEntity(selector2);
                    if (!enforced.equals(selector2)) { newSel2 = enforced; changed = true; }
                }
                if (changed) {
                    changes.add("Added limit=1,sort=nearest to @e selector(s) in /tp (requires single entity)");
                    return prefix(slash) + cmdName + " " + newSel1 + " " + newSel2
                        + (afterSel2.isEmpty() ? "" : " " + afterSel2);
                }
            }
        }

        // ── /summon <entity> [x y z] [nbt] ─────────────────────────────────
        // Migrate old NBT tags in summon commands:
        //   - Remove Damage tags from Item entities
        //   - Convert display Name strings to JSON text components
        if (lower.startsWith("summon ")) {
            String rest = body.substring(7).trim();
            String[] p = tokenize(rest);
            if (p.length >= 1) {
                // Migrate entity type ID (e.g. vindication_illager → vindicator)
                String entityType = p[0];
                String normalizedType = normalizeEntityType(entityType);
                boolean typeChanged = !normalizedType.equals(entityType);
                if (typeChanged) {
                    changes.add("Entity type: " + entityType + " → " + normalizedType);
                    rest = normalizedType + rest.substring(entityType.length());
                }

                // Find the NBT portion (starts with {)
                int nbtStart = rest.indexOf('{');
                if (nbtStart >= 0) {
                    String beforeNbt = rest.substring(0, nbtStart);
                    String nbt = rest.substring(nbtStart);
                    String migratedNbt = migrateSummonNbt(nbt, changes);
                    if (typeChanged || !migratedNbt.equals(nbt)) {
                        return prefix(slash) + "summon " + beforeNbt + migratedNbt;
                    }
                }
                if (typeChanged) {
                    return prefix(slash) + "summon " + rest;
                }
            }
        }

        // ── /stats (complex — flag for manual review) ─────────────────────────
        if (lower.startsWith("stats ")) {
            changes.add("WARNING: /stats was removed in 1.13. Needs manual conversion to /execute store.");
            return prefix(slash) + body + " ## MANUAL_MIGRATION_NEEDED: use /execute store";
        }

        // ── /give <selector> <item>{old NBT} ─────────────────────────────────────
        // In 1.21+ item NBT uses component syntax: item[component=value]
        // Old: minecraft:birch_sign{BlockEntityTag:{Text1:'...',...},display:{Name:'...'}}
        // New: minecraft:birch_sign[minecraft:block_entity_data={front_text:{messages:[...]}},minecraft:custom_name=...]
        if (lower.startsWith("give ")) {
            String rest = body.substring(5).trim();
            String selector = extractSelector(rest);
            String afterSel = rest.substring(selector.length()).trim();
            int braceIdx = -1;
            for (int i = 0; i < afterSel.length(); i++) {
                char c = afterSel.charAt(i);
                if (c == '[') break; // already component syntax
                if (c == '{') { braceIdx = i; break; }
            }
            if (braceIdx >= 0) {
                String rawItemPart = afterSel.substring(0, braceIdx).trim();
                String nbt    = afterSel.substring(braceIdx);
                // rawItemPart may contain old amount/data, e.g. "minecraft:gold_nugget 1 0"
                // Split to extract just the item ID and preserve amount
                String[] itemTokens = rawItemPart.split("\\s+");
                String itemId = normalizeItemId(itemTokens[0]);
                String amount = itemTokens.length >= 2 ? itemTokens[1] : null;
                boolean hadDataValue = itemTokens.length >= 3;
                // itemTokens[2] (data value) is dropped

                // Migrate block IDs inside CanPlaceOn/CanDestroy NBT arrays
                String migratedNbt = migrateNbtBlockLists(nbt, changes);

                String converted = convertGiveNbt(itemId, migratedNbt, changes);
                if (converted != null) {
                    String result = prefix(slash) + "give " + selector + " " + converted;
                    if (amount != null) result += " " + amount;
                    return result;
                }

                // convertGiveNbt returned null (no display/BlockEntityTag to convert),
                // but we still need to strip the data value and keep the NBT
                if (hadDataValue || !migratedNbt.equals(nbt) || !itemId.equals(itemTokens[0])) {
                    if (hadDataValue) changes.add("Stripped old data value from /give command");
                    String result = prefix(slash) + "give " + selector + " " + itemId + migratedNbt;
                    if (amount != null) result += " " + amount;
                    return result;
                }
            }
            // Old /give with amount and/or data value but no NBT/components, e.g.
            // "give @p minecraft:gold_nugget 1 0" → "give @p minecraft:gold_nugget 1"
            if (braceIdx < 0) {
                String[] tokens = afterSel.split("\\s+");
                if (tokens.length >= 3) {
                    // tokens[0]=item, tokens[1]=amount, tokens[2]=dataValue (dropped)
                    String item = normalizeItemId(tokens[0]);
                    changes.add("Stripped old data value from /give command");
                    return prefix(slash) + "give " + selector + " " + item + " " + tokens[1];
                }
            }
        }

        // ── /tellraw <selector> <json> [trailing garbage] ─────────────────────
        // Old map editors sometimes appended UI hint text (e.g. "Styled text only
        // (Minecraft JSON Format)") after the closing bracket of the JSON argument.
        // Strip anything after the top-level closing ] or }.
        if (lower.startsWith("tellraw ")) {
            String rest = body.substring(8).trim();
            String selector = extractSelector(rest);
            String afterSel = rest.substring(selector.length()).trim();
            if (!afterSel.isEmpty() && (afterSel.charAt(0) == '[' || afterSel.charAt(0) == '{')) {
                String cleaned = stripTrailingGarbage(afterSel);
                if (!cleaned.equals(afterSel)) changes.add("Stripped trailing non-JSON text from /tellraw");
                String migrated = migrateTextComponents(cleaned, changes);
                if (!migrated.equals(afterSel)) {
                    return prefix(slash) + "tellraw " + selector + " " + migrated;
                }
            }
        }

        // ── /title <selector> <action> [json] [trailing garbage] ──────────────
        // title/subtitle/actionbar subcommands take a JSON text component that
        // old editors also appended hint text after.
        if (lower.startsWith("title ")) {
            String rest = body.substring(6).trim();
            String selector = extractSelector(rest);
            String afterSel = rest.substring(selector.length()).trim();
            String[] p = tokenize(afterSel);
            if (p.length >= 2) {
                String action = p[0].toLowerCase();
                // Fix known typo: "actionbr" → "actionbar"
                if (action.equals("actionbr")) {
                    action = "actionbar";
                    changes.add("Fixed typo: actionbr → actionbar");
                }
                if (action.equals("title") || action.equals("subtitle") || action.equals("actionbar")) {
                    String jsonPart = afterSel.substring(p[0].length()).trim();
                    if (!jsonPart.isEmpty() && (jsonPart.charAt(0) == '[' || jsonPart.charAt(0) == '{')) {
                        String cleaned = stripTrailingGarbage(jsonPart);
                        if (!cleaned.equals(jsonPart)) changes.add("Stripped trailing non-JSON text from /title");
                        String migrated = migrateTextComponents(cleaned, changes);
                        if (!migrated.equals(jsonPart)) {
                            return prefix(slash) + "title " + selector + " " + action + " " + migrated;
                        }
                    }
                }
            }
        }

        // ── /gamemode <mode> [player] ────────────────────────────────────────
        // Old 1.12: /gamemode 0|1|2|3|s|c|a|sp [player]
        // New 1.13+: /gamemode survival|creative|adventure|spectator [player]
        if (lower.startsWith("gamemode ")) {
            String rest = body.substring(9).trim();
            String[] p = tokenize(rest);
            if (p.length >= 1) {
                String mode = GAMEMODE_MAP.get(p[0].toLowerCase());
                if (mode != null) {
                    String afterMode = rest.substring(p[0].length()).trim();
                    changes.add("Converted gamemode shorthand: " + p[0] + " → " + mode);
                    return prefix(slash) + "gamemode " + mode + (afterMode.isEmpty() ? "" : " " + afterMode);
                }
            }
        }

        // ── /defaultgamemode <mode> ─────────────────────────────────────────
        if (lower.startsWith("defaultgamemode ")) {
            String rest = body.substring(16).trim();
            String mode = GAMEMODE_MAP.get(rest.toLowerCase());
            if (mode != null) {
                changes.add("Converted defaultgamemode shorthand: " + rest + " → " + mode);
                return prefix(slash) + "defaultgamemode " + mode;
            }
        }

        // ── /difficulty <0|1|2|3> ────────────────────────────────────────────
        // Old 1.12: /difficulty 0|1|2|3
        // New 1.13+: /difficulty peaceful|easy|normal|hard
        if (lower.startsWith("difficulty ")) {
            String rest = body.substring(11).trim();
            String[] diffMap = {"peaceful", "easy", "normal", "hard"};
            String val = rest.split("\\s+")[0];
            if (val.matches("[0-3]")) {
                int idx = Integer.parseInt(val);
                changes.add("Converted difficulty numeric: " + val + " → " + diffMap[idx]);
                return prefix(slash) + "difficulty " + diffMap[idx];
            }
        }

        // ── /xp <amount>[L] [player] ───────────────────────────────────────
        // Old 1.12: /xp <amount> [player]     (gives experience points)
        //           /xp <amount>L [player]    (gives experience levels)
        // New 1.13+: /experience add <targets> <amount> [points|levels]
        if (lower.startsWith("xp ")) {
            String rest = body.substring(3).trim();
            String[] p = tokenize(rest);
            if (p.length >= 1) {
                String amountStr = p[0];
                boolean isLevels = amountStr.toUpperCase().endsWith("L");
                String amount = isLevels ? amountStr.substring(0, amountStr.length() - 1) : amountStr;
                String unit = isLevels ? "levels" : "points";
                String target = p.length >= 2 ? p[1] : "@s";
                changes.add("Converted /xp to /experience add (" + unit + ")");
                return prefix(slash) + "experience add " + target + " " + amount + " " + unit;
            }
        }

        // ── /toggledownfall ────────────────────────────────────────────────────
        if (lower.equals("toggledownfall")) {
            changes.add("Converted /toggledownfall → /weather clear (was toggle; pick rain/clear/thunder as needed)");
            return prefix(slash) + "weather clear";
        }

        // ── /locatebiome <biome> ───────────────────────────────────────────────
        if (lower.startsWith("locatebiome ")) {
            String biome = body.substring(12).trim();
            changes.add("Converted /locatebiome to /locate biome");
            return prefix(slash) + "locate biome " + biome;
        }

        // ── /scoreboard players set/add/remove with trailing NBT dataTag ─────
        // Old: /scoreboard players set <selector> <objective> <score> {nbt}
        // New: /execute as <selector>[nbt={nbt}] run scoreboard players set @s <objective> <score>
        if (lower.startsWith("scoreboard players set ") ||
            lower.startsWith("scoreboard players add ") ||
            lower.startsWith("scoreboard players remove ")) {
            String sub = body.substring(19, body.indexOf(' ', 19)); // set/add/remove
            String rest = body.substring(19 + sub.length()).trim();
            String selector = extractSelector(rest);
            String afterSel = rest.substring(selector.length()).trim();
            String[] p = tokenize(afterSel);
            // p[0]=objective, p[1]=score, p[2..]=possibly {nbt}
            if (p.length >= 2) {
                String objective = p[0];
                String score = p[1];
                String trailing = afterSel.substring(
                    (p[0] + " " + p[1]).length()).trim();
                if (!trailing.isEmpty() && trailing.charAt(0) == '{') {
                    // Has NBT data tag — fold into selector and wrap with execute
                    String nbtSelector = mergeNbtIntoSelector(selector, trailing);
                    changes.add("Converted /scoreboard players " + sub
                        + " with NBT dataTag to /execute as ... run");
                    return prefix(slash) + "execute as " + nbtSelector
                        + " run scoreboard players " + sub + " @s "
                        + objective + " " + score;
                }
            }
        }

        // ── /scoreboard players test → /execute if score ─────────────────────
        // Old: /scoreboard players test <selector> <objective> <min> [max]
        // New: /execute if score <selector> <objective> matches <min>..[max]
        if (lower.startsWith("scoreboard players test ")) {
            String rest = body.substring(24).trim();
            String selector = extractSelector(rest);
            String afterSel = rest.substring(selector.length()).trim();
            String[] p = tokenize(afterSel);
            if (p.length >= 2) {
                String objective = p[0];
                String min = p[1];
                String max = p.length >= 3 ? p[2] : "";
                String range;
                if (max.isEmpty() || max.equals("*")) {
                    range = min + "..";
                } else if (min.equals("*")) {
                    range = ".." + max;
                } else if (min.equals(max)) {
                    range = min;
                } else {
                    range = min + ".." + max;
                }
                changes.add("Converted /scoreboard players test to /execute if score");
                return prefix(slash) + "execute if score " + selector
                    + " " + objective + " matches " + range;
            }
        }

        // ── /scoreboard players tag → /tag ────────────────────────────────────
        if (lower.startsWith("scoreboard players tag ")) {
            String rest = body.substring(23).trim();
            String selector = extractSelector(rest);
            String afterSel = rest.substring(selector.length()).trim();
            String[] p = tokenize(afterSel);
            if (p.length >= 1) {
                String action = p[0].toLowerCase();
                String tag = p.length > 1 ? p[1] : "";
                // Check for trailing NBT data tag after tag name
                String trailing = "";
                if (p.length > 1) {
                    trailing = afterSel.substring(
                        (p[0] + " " + p[1]).length()).trim();
                }
                if (!trailing.isEmpty() && trailing.charAt(0) == '{') {
                    String nbtSelector = mergeNbtIntoSelector(selector, trailing);
                    changes.add("Converted /scoreboard players tag to /tag with NBT filter");
                    return prefix(slash) + "tag " + nbtSelector + " " + action
                        + (tag.isEmpty() ? "" : " " + tag);
                }
                changes.add("Converted /scoreboard players tag to /tag");
                return prefix(slash) + "tag " + selector + " " + action
                    + (tag.isEmpty() ? "" : " " + tag);
            }
        }

        // ── /scoreboard teams → /team ─────────────────────────────────────────
        if (lower.startsWith("scoreboard teams ")) {
            String rest = body.substring(17).trim();
            String[] p = tokenize(rest);
            if (p.length >= 1) {
                String sub = p[0].toLowerCase();
                String restOfArgs = joinFrom(p, 1);
                // "option" was renamed to "modify"
                if (sub.equals("option")) {
                    changes.add("Converted /scoreboard teams option to /team modify");
                    return prefix(slash) + "team modify " + restOfArgs;
                } else {
                    changes.add("Converted /scoreboard teams " + sub + " to /team " + sub);
                    return prefix(slash) + "team " + sub + (restOfArgs.isEmpty() ? "" : " " + restOfArgs);
                }
            }
        }

        // ── Old /execute <selector> <x> <y> <z> [detect ...] <command> ───────
        if (lower.startsWith("execute ")) {
            String rest = body.substring(8).trim();
            if (!rest.isEmpty() && isOldStyleExecute(rest)) {
                String converted = convertOldExecute(slash, rest, changes);
                if (converted != null) return converted;
            }
            // ── Modern /execute ... run <subcommand> ────────────────────────
            // Recursively migrate the sub-command after "run"
            int runIdx = findRunKeyword(rest);
            if (runIdx >= 0) {
                String beforeRun = rest.substring(0, runIdx);
                String subCommand = rest.substring(runIdx + 4).trim(); // skip "run "
                if (!subCommand.isEmpty()) {
                    MigrationResult sub = migrate(subCommand);
                    if (sub.wasModified) {
                        changes.addAll(sub.changes);
                        return prefix(slash) + "execute " + beforeRun + "run " + sub.migrated;
                    }
                }
            }
        }

        return cmd;
    }

    /**
     * Returns true if the argument string looks like old-style /execute
     * (first token is a selector, not a new-style keyword).
     */
    private static boolean isOldStyleExecute(String rest) {
        String[] p = tokenize(rest);
        if (p.length == 0) return false;
        String first = p[0].toLowerCase();
        // New keywords never start with @; old style always starts with selector
        if (first.startsWith("@")) return true;
        // Old style could also be a player name literal; new keywords are known words
        return !NEW_EXECUTE_KEYWORDS.contains(first);
    }

    /**
     * Converts old-style /execute into the modern chained form.
     * Old: execute <selector> <x> <y> <z> [detect <bx> <by> <bz> <block> <data>] <command>
     */
    private static String convertOldExecute(boolean slash, String rest, List<String> changes) {
        String selector = extractSelector(rest);
        if (selector.isEmpty()) return null;
        String afterSel = rest.substring(selector.length()).trim();
        String[] p = tokenize(afterSel);
        if (p.length < 4) return null;

        String x = p[0], y = p[1], z = p[2];
        boolean relativePos = x.equals("~") && y.equals("~") && z.equals("~");

        String detectClause = "";
        String subCommand;

        if (p.length >= 4 && p[3].equalsIgnoreCase("detect")) {
            // detect <bx> <by> <bz> <block> <data> <command...>
            if (p.length < 9) return null;
            String bx = p[4], by = p[5], bz = p[6];
            String block = normalizeBlockId(p[7]);
            // p[8] = data value (ignored in 1.13+)
            subCommand = joinFrom(p, 9);
            detectClause = " if block " + bx + " " + by + " " + bz + " " + block;
        } else {
            subCommand = joinFrom(p, 3);
        }

        // Strip leading slash from sub-command before recursing
        if (subCommand.startsWith("/")) subCommand = subCommand.substring(1);

        // Recursively migrate the nested command
        MigrationResult sub = migrate(subCommand);
        if (sub.wasModified) changes.addAll(sub.changes);
        String migratedSub = sub.wasModified ? sub.migrated : subCommand;

        String posClause = relativePos ? "at @s" : "positioned " + x + " " + y + " " + z;
        String result = "execute as " + selector + " " + posClause + detectClause + " run " + migratedSub;
        changes.add(0, "Converted old /execute to new chained syntax");
        return prefix(slash) + result;
    }

    // -------------------------------------------------------------------------
    // Selector migration
    // -------------------------------------------------------------------------

    /**
     * Scans the command string for @a/@e/@p/@r/@s[...] blocks and migrates
     * the selector arguments inside them.
     */
    private static String migrateSelectorsInString(String cmd, List<String> changes) {
        // Matches @a, @e, @p, @r, @s with optional [...] block
        Pattern pat = Pattern.compile("(@[aeprs])(\\[([^\\]]*?)\\])?");
        Matcher m = pat.matcher(cmd);
        StringBuffer sb = new StringBuffer();

        while (m.find()) {
            String atPart = m.group(1);
            String inner  = m.group(3); // null if no brackets

            if (inner == null || inner.isEmpty()) {
                m.appendReplacement(sb, Matcher.quoteReplacement(atPart));
                continue;
            }

            String migrated = migrateSelectorArgs(inner, changes);
            m.appendReplacement(sb, Matcher.quoteReplacement(atPart + "[" + migrated + "]"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Migrates the contents of a selector bracket: [r=5,m=1,score_kills=3,...]
     * Returns the new bracket contents.
     */
    private static String migrateSelectorArgs(String inner, List<String> outerChanges) {
        List<String> pairs = splitSelectorArgs(inner);

        // Collect range pairs that need merging
        String distanceMin = null, distanceMax = null;
        String levelMin    = null, levelMax    = null;
        String xRotMin     = null, xRotMax     = null;
        String yRotMin     = null, yRotMax     = null;

        // score_Obj_min / score_Obj maps
        Map<String, String> scoreMin = new LinkedHashMap<>();
        Map<String, String> scoreMax = new LinkedHashMap<>();

        // Processed output — preserving order using a list of "key=value" or special tokens
        List<String> output   = new ArrayList<>();
        List<String> myChanges = new ArrayList<>();
        boolean hasScores = false;

        for (String pair : pairs) {
            int eq = pair.indexOf('=');
            if (eq < 0) { output.add(pair); continue; }

            String key = pair.substring(0, eq).trim();
            String val = pair.substring(eq + 1).trim();
            String kl  = key.toLowerCase();

            // ── Score selectors ────────────────────────────────────────────────
            if (kl.startsWith("score_") && kl.endsWith("_min")) {
                String obj = key.substring(6, key.length() - 4);
                scoreMin.put(obj, val);
                if (!hasScores) { output.add("__SCORES__"); hasScores = true; }
                continue;
            }
            if (kl.startsWith("score_")) {
                String obj = key.substring(6);
                scoreMax.put(obj, val);
                if (!hasScores) { output.add("__SCORES__"); hasScores = true; }
                continue;
            }

            // ── Range pair accumulation ────────────────────────────────────────
            switch (kl) {
                case "r":   distanceMax = val; continue;
                case "rm":  distanceMin = val; continue;
                case "l":   levelMax    = val; continue;
                case "lm":  levelMin    = val; continue;
                case "rx":  xRotMax     = val; continue;
                case "rxm": xRotMin     = val; continue;
                case "ry":  yRotMax     = val; continue;
                case "rym": yRotMin     = val; continue;
            }

            // ── One-to-one conversions ─────────────────────────────────────────
            switch (kl) {
                case "c": {
                    output.add("limit=" + val);
                    myChanges.add("Selector: c → limit");
                    break;
                }
                case "m": {
                    String gm = GAMEMODE_MAP.getOrDefault(val.toLowerCase(), val);
                    output.add("gamemode=" + gm);
                    myChanges.add("Selector: m → gamemode (" + val + " → " + gm + ")");
                    break;
                }
                case "type": {
                    String norm = normalizeEntityType(val);
                    output.add("type=" + norm);
                    if (!norm.equals(val)) myChanges.add("Selector: entity type " + val + " → " + norm);
                    break;
                }
                default:
                    output.add(key + "=" + val);
            }
        }

        // ── Emit merged range args in sensible position (before other args) ────
        List<String> rangeArgs = new ArrayList<>();
        if (distanceMin != null || distanceMax != null) {
            rangeArgs.add("distance=" + range(distanceMin, distanceMax));
            myChanges.add("Selector: r/rm → distance=" + range(distanceMin, distanceMax));
        }
        if (levelMin != null || levelMax != null) {
            rangeArgs.add("level=" + range(levelMin, levelMax));
            myChanges.add("Selector: l/lm → level=" + range(levelMin, levelMax));
        }
        if (xRotMin != null || xRotMax != null) {
            rangeArgs.add("x_rotation=" + range(xRotMin, xRotMax));
            myChanges.add("Selector: rx/rxm → x_rotation");
        }
        if (yRotMin != null || yRotMax != null) {
            rangeArgs.add("y_rotation=" + range(yRotMin, yRotMax));
            myChanges.add("Selector: ry/rym → y_rotation");
        }

        // ── Replace __SCORES__ token with actual scores={...} ────────────────
        List<String> finalParts = new ArrayList<>(rangeArgs);
        for (String part : output) {
            if (part.equals("__SCORES__")) {
                Set<String> allObjs = new LinkedHashSet<>();
                allObjs.addAll(scoreMax.keySet());
                allObjs.addAll(scoreMin.keySet());
                List<String> scoreParts = new ArrayList<>();
                for (String obj : allObjs) {
                    String mn = scoreMin.get(obj);
                    String mx = scoreMax.get(obj);
                    scoreParts.add(obj + "=" + range(mn, mx));
                }
                finalParts.add("scores={" + String.join(",", scoreParts) + "}");
                myChanges.add("Selector: score_X/score_X_min → scores={}");
            } else {
                finalParts.add(part);
            }
        }

        outerChanges.addAll(myChanges);
        return String.join(",", finalParts);
    }

    // -------------------------------------------------------------------------
    // Normalisation helpers
    // -------------------------------------------------------------------------

    /** Builds a range string: "min..max", "..max", "min.." as appropriate. */
    private static String range(String min, String max) {
        if (min != null && max != null) return min + ".." + max;
        if (min != null)               return min + "..";
        return                                ".." + max;
    }

    /**
     * Resolves a block ID that may have a trailing numeric data value.
     * e.g. "minecraft:stained_glass 5"  → "minecraft:lime_stained_glass"
     *      "minecraft:air 0 destroy"    → ["minecraft:air", "destroy"]  (data stripped, rest returned)
     *
     * Returns a String[2]: [resolvedBlockId, remainingArgs]
     * remainingArgs may be empty string if nothing was left.
     */
    private static String[] resolveBlockAndData(String blockToken, String remainingAfterBlock, List<String> changes) {
        // Check if the very next token after the block ID is a pure integer (data value)
        String[] remaining = tokenize(remainingAfterBlock.trim());
        if (remaining.length == 0) return new String[]{normalizeBlockId(blockToken), ""};

        String nextToken = remaining[0];
        if (!nextToken.matches("-?\\d+")) {
            // No data value present — just normalize
            return new String[]{normalizeBlockId(blockToken), remainingAfterBlock.trim()};
        }

        int dataVal = Integer.parseInt(nextToken);
        String restAfterData = joinFrom(remaining, 1);

        // Try lookup in BLOCK_DATA_MAP
        String bareBlock = blockToken.toLowerCase();
        String lookupKey = bareBlock + ":" + dataVal;
        // Also try without minecraft: prefix
        String shortKey  = bareBlock.startsWith("minecraft:") ? bareBlock.substring(10) + ":" + dataVal : lookupKey;

        String resolved = BLOCK_DATA_MAP.get(lookupKey);
        if (resolved == null) resolved = BLOCK_DATA_MAP.get(shortKey);

        if (resolved != null) {
            changes.add("Resolved block data value: " + blockToken + " " + dataVal + " → " + resolved);
            return new String[]{resolved, restAfterData};
        }

        // Not in map — at least strip the data value and warn
        String normalised = normalizeBlockId(blockToken);
        changes.add("Stripped unknown data value " + dataVal + " from " + blockToken + " (may need manual review)");
        return new String[]{normalised, restAfterData};
    }

    /**
     * Handles CamelCase legacy IDs, numeric IDs, and adds minecraft: namespace.
     */
    private static String normalizeEntityType(String type) {
        if (type.isEmpty()) return type;
        // Invert selector (e.g. !Cow)
        String prefix = "";
        String bare   = type;
        if (type.startsWith("!")) { prefix = "!"; bare = type.substring(1); }

        String ns = "";
        String id = bare;
        if (bare.contains(":")) {
            int colon = bare.indexOf(':');
            ns = bare.substring(0, colon + 1);
            id = bare.substring(colon + 1);
        }

        // Try lookup by stripped lowercase key
        String lookupKey = id.toLowerCase().replace("_", "").replace("-", "");
        String mapped    = ENTITY_TYPE_MAP.get(lookupKey);
        if (mapped != null) {
            return prefix + "minecraft:" + mapped;
        }

        // Fallback: just lowercase and add minecraft: if missing namespace
        String normalised = id.toLowerCase();
        if (ns.isEmpty()) {
            return prefix + "minecraft:" + normalised;
        }
        return prefix + ns + normalised;
    }

    /**
     * Migrates 1.12-era JSON text component syntax to 1.21.4+ format:
     *   "clickEvent" → "click_event"
     *   "hoverEvent" → "hover_event"
     *   "value":"/cmd" inside click_event → "command":"cmd" (leading slash stripped)
     */
    /** Converts /give item NBT syntax to 1.21 component syntax. Returns null if no conversion needed. */
    /**
     * Migrates NBT in /summon commands:
     *   - Removes Damage:Xs tags (data values removed in 1.13)
     *   - Converts plain-string Name to JSON text component format
     *   - Converts plain-string Lore entries to JSON text component format
     */
    private static String migrateSummonNbt(String nbt, List<String> changes) {
        String result = nbt;
        boolean changed = false;

        // Remove Damage tag (e.g. Damage:0s, Damage:5s, Damage:0)
        String beforeDamage = result;
        result = result.replaceAll(",?Damage:\\d+[sb]?", "");
        result = result.replaceAll("Damage:\\d+[sb]?,?", "");
        if (!result.equals(beforeDamage)) {
            changes.add("Removed obsolete Damage tag from NBT (data values removed in 1.13)");
            changed = true;
        }

        // Convert plain display Name strings to JSON text component
        // Uses negative lookbehind to NOT match CustomName (plain string in 1.21.10)
        Pattern namePat = Pattern.compile("(?<!Custom)Name:\"([^\"]*?)\"");
        Matcher nameM = namePat.matcher(result);
        StringBuffer sb = new StringBuffer();
        boolean nameChanged = false;
        while (nameM.find()) {
            String nameVal = nameM.group(1);
            // Already JSON? (starts with { after quotes)
            if (nameVal.startsWith("{") || nameVal.startsWith("\\{")) continue;
            String escapedName = nameVal.replace("'", "\\'");
            String jsonName = "'{\"text\":\"" + escapedName + "\"}'";

            nameM.appendReplacement(sb, Matcher.quoteReplacement("Name:" + jsonName));
            nameChanged = true;
        }
        nameM.appendTail(sb);
        if (nameChanged) {
            result = sb.toString();
            changes.add("Converted display Name to JSON text component format");
            changed = true;
        }

        // Convert plain Lore strings to JSON text component
        // Matches individual lore entries like "some text" inside Lore:[...]
        Pattern lorePat = Pattern.compile("Lore:\\[(.*?)\\]");
        Matcher loreM = lorePat.matcher(result);
        sb = new StringBuffer();
        boolean loreChanged = false;
        while (loreM.find()) {
            String loreContent = loreM.group(1);
            // Convert each "string" entry to '{"text":"string"}'
            Pattern entryPat = Pattern.compile("\"([^\"]*?)\"");
            Matcher entryM = entryPat.matcher(loreContent);
            StringBuffer loreSb = new StringBuffer();
            boolean anyEntry = false;
            while (entryM.find()) {
                String entry = entryM.group(1);
                if (!entry.startsWith("{")) {
                    String escapedEntry = entry.replace("'", "\\'");
                    entryM.appendReplacement(loreSb, Matcher.quoteReplacement("'{\"text\":\"" + escapedEntry + "\"}'"));
                    anyEntry = true;
                }
            }
            entryM.appendTail(loreSb);
            if (anyEntry) {
                loreM.appendReplacement(sb, Matcher.quoteReplacement("Lore:[" + loreSb.toString() + "]"));
                loreChanged = true;
            }
        }
        loreM.appendTail(sb);
        if (loreChanged) {
            result = sb.toString();
            changes.add("Converted Lore entries to JSON text component format");
            changed = true;
        }

        // Migrate CanPlaceOn/CanDestroy block IDs
        String beforeBlocks = result;
        result = migrateNbtBlockLists(result, changes);
        if (!result.equals(beforeBlocks)) changed = true;

        // Clean up any empty comma artifacts like {,  or  ,,  or  ,}
        result = result.replaceAll("\\{,", "{");
        result = result.replaceAll(",\\}", "}");
        result = result.replaceAll(",,+", ",");

        return changed ? result : nbt;
    }

    /**
     * Migrates block/item IDs inside CanPlaceOn and CanDestroy NBT arrays.
     * e.g. {CanPlaceOn:["minecraft:planks"]} → {CanPlaceOn:["minecraft:oak_planks"]}
     */
    private static String migrateNbtBlockLists(String nbt, List<String> changes) {
        // Match CanPlaceOn or CanDestroy followed by a [...] array
        Pattern listPat = Pattern.compile("(CanPlaceOn|CanDestroy):\\s*\\[([^\\]]*?)\\]");
        Matcher m = listPat.matcher(nbt);
        StringBuffer sb = new StringBuffer();
        boolean changed = false;

        while (m.find()) {
            String tag = m.group(1);
            String entries = m.group(2);
            // Migrate each quoted block ID in the array
            Pattern idPat = Pattern.compile("\"(.*?)\"");
            Matcher idM = idPat.matcher(entries);
            StringBuffer entrySb = new StringBuffer();
            while (idM.find()) {
                String blockId = idM.group(1);
                String normalized = normalizeBlockId(blockId);
                if (!normalized.equals(blockId)) {
                    changes.add("Migrated " + tag + " block ID: " + blockId + " → " + normalized);
                    changed = true;
                }
                idM.appendReplacement(entrySb, "\"" + Matcher.quoteReplacement(normalized) + "\"");
            }
            idM.appendTail(entrySb);
            m.appendReplacement(sb, Matcher.quoteReplacement(tag + ":[" + entrySb + "]"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String convertGiveNbt(String itemId, String nbt, List<String> changes) {
        Map<String, String> top = parseTopLevelNbt(nbt);
        if (top == null || top.isEmpty()) return null;

        List<String> components = new ArrayList<>();

        if (top.containsKey("BlockEntityTag")) {
            Map<String, String> bet = parseTopLevelNbt(top.get("BlockEntityTag"));
            if (bet != null && bet.containsKey("Text1")) {
                String t1 = migrateSignText(bet.getOrDefault("Text1", "'{\"text\":\"\"}'"), changes);
                String t2 = migrateSignText(bet.getOrDefault("Text2", "'{\"text\":\"\"}'"), changes);
                String t3 = migrateSignText(bet.getOrDefault("Text3", "'{\"text\":\"\"}'"), changes);
                String t4 = migrateSignText(bet.getOrDefault("Text4", "'{\"text\":\"\"}'"), changes);
                components.add("minecraft:block_entity_data={front_text:{messages:[" + t1 + "," + t2 + "," + t3 + "," + t4 + "]}}");
            } else {
                components.add("minecraft:block_entity_data=" + top.get("BlockEntityTag"));
            }
        }
        if (top.containsKey("display")) {
            Map<String, String> disp = parseTopLevelNbt(top.get("display"));
            if (disp != null) {
                if (disp.containsKey("Name")) {
                    String name = disp.get("Name");
                    // If plain string (not already JSON), convert to JSON text component
                    if (!name.startsWith("'") && !name.startsWith("{")) {
                        // Strip surrounding quotes if present
                        if (name.startsWith("\"") && name.endsWith("\"")) {
                            name = name.substring(1, name.length() - 1);
                        }
                        String escaped = name.replace("'", "\\'");
                        name = "'{\"text\":\"" + escaped + "\"}'";
                    }
                    components.add("minecraft:custom_name=" + name);
                }
                if (disp.containsKey("Lore")) {
                    String loreRaw = disp.get("Lore");
                    // Lore is an array like ["line1","line2"]
                    if (loreRaw.startsWith("[") && loreRaw.endsWith("]")) {
                        String inner = loreRaw.substring(1, loreRaw.length() - 1);
                        Pattern entryPat = Pattern.compile("\"([^\"]*?)\"");
                        Matcher entryM = entryPat.matcher(inner);
                        List<String> loreEntries = new ArrayList<>();
                        while (entryM.find()) {
                            String entry = entryM.group(1);
                            String escaped = entry.replace("'", "\\'");
                            loreEntries.add("'{\"text\":\"" + escaped + "\"}'");
                        }
                        if (!loreEntries.isEmpty()) {
                            components.add("minecraft:lore=[" + String.join(",", loreEntries) + "]");
                        }
                    }
                }
            }
        }

        if (top.containsKey("CanPlaceOn")) {
            String raw = top.get("CanPlaceOn");
            // raw is like ["block1","block2"] — migrate block IDs inside
            String migrated = migrateNbtBlockLists("CanPlaceOn:" + raw, changes);
            // Extract just the array part back
            String arr = migrated.substring(migrated.indexOf('['));
            components.add("minecraft:can_place_on={blocks:" + arr + "}");
        }
        if (top.containsKey("CanDestroy")) {
            String raw = top.get("CanDestroy");
            String migrated = migrateNbtBlockLists("CanDestroy:" + raw, changes);
            String arr = migrated.substring(migrated.indexOf('['));
            components.add("minecraft:can_break={blocks:" + arr + "}");
        }
        if (top.containsKey("Unbreakable")) {
            components.add("minecraft:unbreakable={}");
        }

        if (components.isEmpty()) return null;
        changes.add("Migrated /give item NBT to 1.21 component syntax");
        return itemId + "[" + String.join(",", components) + "]";
    }

    /** Applies text component migration to a single-quoted NBT sign text value. */
    private static String migrateSignText(String singleQuoted, List<String> changes) {
        if (!singleQuoted.startsWith("'") || !singleQuoted.endsWith("'")) return singleQuoted;
        String inner = singleQuoted.substring(1, singleQuoted.length() - 1);
        String migrated = migrateTextComponents(inner, changes);
        return "'" + migrated + "'";
    }

    /**
     * Parses the top-level key-value pairs from an NBT compound string like
     * {Key1:{...}, Key2:'...'} into a LinkedHashMap preserving insertion order.
     */
    private static Map<String, String> parseTopLevelNbt(String compound) {
        if (compound == null) return null;
        compound = compound.trim();
        if (!compound.startsWith("{") || !compound.endsWith("}")) return null;
        String inner = compound.substring(1, compound.length() - 1);
        Map<String, String> result = new LinkedHashMap<>();
        int i = 0;
        while (i < inner.length()) {
            while (i < inner.length() && (inner.charAt(i) == ',' || Character.isWhitespace(inner.charAt(i)))) i++;
            if (i >= inner.length()) break;
            // Read key
            StringBuilder key = new StringBuilder();
            while (i < inner.length() && inner.charAt(i) != ':' && !Character.isWhitespace(inner.charAt(i))) key.append(inner.charAt(i++));
            while (i < inner.length() && (Character.isWhitespace(inner.charAt(i)) || inner.charAt(i) == ':')) i++;
            // Read value (depth + quote aware)
            StringBuilder val = new StringBuilder();
            int depth = 0; boolean inS = false, inD = false, esc = false;
            while (i < inner.length()) {
                char c = inner.charAt(i);
                if (esc) { esc = false; val.append(c); i++; continue; }
                if (c == '\\') { esc = true; val.append(c); i++; continue; }
                if (c == '\'' && !inD) { inS = !inS; val.append(c); i++; continue; }
                if (c == '"'  && !inS) { inD = !inD; val.append(c); i++; continue; }
                if (!inS && !inD) {
                    if (c == '{' || c == '[') { depth++; val.append(c); i++; continue; }
                    if (c == '}' || c == ']') { depth--; val.append(c); i++; continue; }
                    if (c == ',' && depth == 0) break;
                }
                val.append(c); i++;
            }
            if (key.length() > 0) result.put(key.toString(), val.toString().trim());
        }
        return result;
    }

    /**
     * Scans the command for any minecraft:old_name IDs and renames them.
     * Handles IDs that appear anywhere (block arguments, NBT, etc.).
     */
    private static String renameBlockItemIds(String cmd, List<String> changes) {
        String result = cmd;
        boolean modified = false;
        for (Map.Entry<String, String> entry : BLOCK_ITEM_RENAME.entrySet()) {
            String oldId = "minecraft:" + entry.getKey();
            String newId = "minecraft:" + entry.getValue();
            // Use word-boundary matching: the old ID must not be followed by
            // a letter, digit, or underscore (to avoid partial matches like
            // nether_brick matching inside nether_brick_fence)
            Pattern p = Pattern.compile(Pattern.quote(oldId) + "(?![a-zA-Z0-9_:])");
            Matcher m = p.matcher(result);
            if (m.find()) {
                result = m.replaceAll(Matcher.quoteReplacement(newId));
                modified = true;
            }
        }
        if (modified) {
            changes.add("Renamed old block/item IDs to modern names");
        }
        return result;
    }

    private static String migrateTextComponents(String s, List<String> changes) {
        String result = s;

        // Rename camelCase event keys — both regular and NBT-escaped (\" prefix) forms
        result = result.replace("\"clickEvent\"",     "\"click_event\"");
        result = result.replace("\\\"clickEvent\\\"", "\\\"click_event\\\"");
        result = result.replace("\"hoverEvent\"",     "\"hover_event\"");
        result = result.replace("\\\"hoverEvent\\\"", "\\\"hover_event\\\"");

        // open_url: "value":"https://..." → "url":"https://..."
        result = result.replaceAll("\"value\"\\s*:\\s*\"(https?://[^\"]*)\"", "\"url\":\"$1\"");
        result = result.replace("\\\"value\\\":\\\"https://", "\\\"url\\\":\\\"https://");

        // run_command with leading slash: "value":"/cmd" → "command":"cmd"
        result = result.replaceAll("\"value\"\\s*:\\s*\"/([^\"]*)\"", "\"command\":\"$1\"");
        result = result.replace("\\\"value\\\":\\\"/", "\\\"command\\\":\\\"/");

        // run_command without leading slash — only when inside a click_event object
        if (result.contains("\"click_event\"") || result.contains("\\\"click_event\\\"")) {
            result = result.replaceAll("\"value\"\\s*:\\s*\"([^\"]+)\"", "\"command\":\"$1\"");
            result = result.replace("\\\"value\\\":\\\"", "\\\"command\\\":\\\"");
        }

        if (!result.equals(s)) {
            changes.add("Migrated text component events to 1.21.4+ format (clickEvent→click_event, value→command/url)");
        }
        return result;
    }

    /**
     * Walks a JSON string (starting with [ or {) and returns only the portion
     * up to and including the matching closing bracket, discarding any trailing text.
     */
    private static String stripTrailingGarbage(String s) {
        if (s.isEmpty()) return s;
        char open  = s.charAt(0);
        char close = open == '[' ? ']' : '}';
        int depth = 0;
        boolean inString = false;
        boolean escape   = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escape)              { escape = false; continue; }
            if (c == '\\' && inString) { escape = true;  continue; }
            if (c == '"')            { inString = !inString; continue; }
            if (inString)            continue;
            if (c == open)           depth++;
            else if (c == close)     { depth--; if (depth == 0) return s.substring(0, i + 1); }
        }
        return s; // malformed JSON — return as-is
    }

    /**
     * Resolves an effect identifier — numeric ID, unnamespaced name, or already-valid name.
     * Returns a minecraft:-prefixed effect name.
     */
    private static String resolveEffectName(String effect) {
        // Numeric ID?
        String fromId = EFFECT_ID_MAP.get(effect);
        if (fromId != null) return fromId;
        // Already namespaced?
        if (effect.contains(":")) return effect;
        // Unnamespaced name?
        String fromName = EFFECT_NAME_MAP.get(effect.toLowerCase());
        if (fromName != null) return fromName;
        // Unknown — add namespace anyway
        return "minecraft:" + effect.toLowerCase();
    }

    /**
     * Normalises a block ID — lowercases and adds minecraft: if missing.
     * Preserves block state [...] and NBT {...} suffixes.
     */
    private static String normalizeBlockId(String block) {
        // Find state/nbt suffix
        int bracket = block.indexOf('[');
        int brace   = block.indexOf('{');
        int split   = bracket >= 0 ? bracket : brace;

        String base;
        String suffix;
        if (split >= 0) {
            base = block.substring(0, split).toLowerCase();
            suffix = block.substring(split);
        } else {
            base = block.toLowerCase();
            suffix = "";
        }

        // Strip minecraft: prefix for rename lookup, then re-add
        String bare = base.startsWith("minecraft:") ? base.substring(10) : base;
        String renamed = BLOCK_ITEM_RENAME.getOrDefault(bare, bare);
        return "minecraft:" + renamed + suffix;
    }

    /**
     * Normalises an item ID — lowercases, adds minecraft:, and applies 1.13 renames.
     */
    private static String normalizeItemId(String item) {
        String lower = item.toLowerCase();
        String bare = lower.startsWith("minecraft:") ? lower.substring(10) : lower;
        String renamed = BLOCK_ITEM_RENAME.getOrDefault(bare, bare);
        return "minecraft:" + renamed;
    }

    /**
     * Normalises slot names: removes legacy "slot." prefix if present.
     * e.g. slot.armor.head → armor.head
     */
    private static String normalizeSlot(String slot) {
        if (slot.toLowerCase().startsWith("slot.")) return slot.substring(5);
        return slot;
    }

    // -------------------------------------------------------------------------
    // Parsing helpers
    // -------------------------------------------------------------------------

    /**
     * Merges an NBT data tag into a selector by adding an nbt= argument.
     * e.g. "@e[type=minecraft:item]" + "{Item:{id:\"minecraft:barrier\"}}"
     *   → "@e[type=minecraft:item,nbt={Item:{id:\"minecraft:barrier\"}}]"
     * If the selector has no brackets (e.g. "@a"), wraps with [nbt=...].
     */
    private static String mergeNbtIntoSelector(String selector, String nbt) {
        if (selector.length() >= 2 && selector.charAt(0) == '@') {
            if (selector.endsWith("]")) {
                // Already has arguments — insert nbt before closing bracket
                return selector.substring(0, selector.length() - 1)
                    + ",nbt=" + nbt + "]";
            } else {
                // No arguments — add [nbt=...]
                return selector + "[nbt=" + nbt + "]";
            }
        }
        // Plain player name — can't add nbt filter, return as-is
        return selector;
    }

    /**
     * Extracts the first selector (@a, @e[...], @p, @r, @s) or plain token
     * from the start of a string.
     */
    private static String extractSelector(String s) {
        if (s.isEmpty()) return "";
        if (s.length() >= 2 && s.charAt(0) == '@') {
            char t = s.charAt(1);
            if (t == 'a' || t == 'e' || t == 'p' || t == 'r' || t == 's') {
                if (s.length() > 2 && s.charAt(2) == '[') {
                    int depth = 0;
                    for (int i = 2; i < s.length(); i++) {
                        if (s.charAt(i) == '[') depth++;
                        else if (s.charAt(i) == ']') { depth--; if (depth == 0) return s.substring(0, i + 1); }
                    }
                }
                return s.substring(0, 2);
            }
        }
        // Plain token
        int sp = s.indexOf(' ');
        return sp < 0 ? s : s.substring(0, sp);
    }

    /**
     * Finds the position of the " run " keyword in an execute chain.
     * Returns the index of the space before "run", or -1 if not found.
     * Looks for the last occurrence to handle nested execute chains correctly.
     */
    private static int findRunKeyword(String executeArgs) {
        // Find last " run " — that's where the sub-command starts
        int idx = executeArgs.lastIndexOf(" run ");
        if (idx >= 0) return idx + 1; // return index of 'r' in "run"
        return -1;
    }

    /**
     * Ensures an @e selector targets at most one entity by injecting
     * limit=1,sort=nearest if not already present.
     */
    private static String enforceSingleEntity(String selector) {
        if (!selector.startsWith("@e")) return selector;
        String lowerSel = selector.toLowerCase();
        // Already has limit — don't touch it
        if (lowerSel.contains("limit=")) return selector;
        // @e with brackets — inject before closing bracket
        if (selector.length() > 2 && selector.charAt(2) == '[') {
            String inner = selector.substring(3, selector.length() - 1);
            if (inner.isEmpty()) {
                return "@e[limit=1,sort=nearest]";
            }
            return "@e[" + inner + ",limit=1,sort=nearest]";
        }
        // @e with no brackets
        return "@e[limit=1,sort=nearest]";
    }

    /**
     * Splits a string into whitespace-separated tokens, respecting
     * quoted strings ("" or ''), nested {} and [] brackets.
     */
    private static String[] tokenize(String s) {
        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int depth = 0;
        boolean inDouble = false, inSingle = false;

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if      (c == '"'  && !inSingle) { inDouble = !inDouble; cur.append(c); }
            else if (c == '\'' && !inDouble) { inSingle = !inSingle; cur.append(c); }
            else if ((c == '{' || c == '[') && !inDouble && !inSingle) { depth++; cur.append(c); }
            else if ((c == '}' || c == ']') && !inDouble && !inSingle) { depth--; cur.append(c); }
            else if (c == ' ' && depth == 0 && !inDouble && !inSingle) {
                if (cur.length() > 0) { tokens.add(cur.toString()); cur.setLength(0); }
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) tokens.add(cur.toString());
        return tokens.toArray(new String[0]);
    }

    /**
     * Splits selector argument string by top-level commas (ignoring commas
     * inside nested {} blocks).
     */
    private static List<String> splitSelectorArgs(String s) {
        List<String> parts = new ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if      (c == '{') depth++;
            else if (c == '}') depth--;
            else if (c == ',' && depth == 0) {
                parts.add(s.substring(start, i).trim());
                start = i + 1;
            }
        }
        if (start < s.length()) parts.add(s.substring(start).trim());
        return parts;
    }

    /** Joins array elements from index {@code from} with spaces. */
    private static String joinFrom(String[] parts, int from) {
        if (from >= parts.length) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < parts.length; i++) {
            if (i > from) sb.append(' ');
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    private static String prefix(boolean slash) {
        return slash ? "/" : "";
    }

    /**
     * Converts a 1.12.2 block data value to 1.13+ block state properties
     * for interactive/redstone blocks where state matters for logic.
     * Returns the block ID with state appended (e.g. "minecraft:lever[powered=true]"),
     * or just the block ID if no meaningful state conversion is possible.
     */
    private static String dataValueToBlockState(String blockId, int dataValue, List<String> changes) {
        String base = blockId.replace("minecraft:", "");

        // Buttons: bit 3 (value 8) = powered
        if (base.endsWith("_button")) {
            if ((dataValue & 8) != 0) {
                changes.add("Converted data value " + dataValue + " to powered=true for " + base);
                return blockId + "[powered=true]";
            }
            return blockId;
        }

        // Lever: bit 3 (value 8) = powered
        if (base.equals("lever")) {
            if ((dataValue & 8) != 0) {
                changes.add("Converted data value " + dataValue + " to powered=true for lever");
                return blockId + "[powered=true]";
            }
            return blockId;
        }

        // Trapdoors: bit 2 (value 4) = open, bit 3 (value 8) = top half
        if (base.endsWith("_trapdoor")) {
            List<String> states = new ArrayList<>();
            if ((dataValue & 4) != 0) states.add("open=true");
            if ((dataValue & 8) != 0) states.add("half=top");
            if (!states.isEmpty()) {
                String stateStr = String.join(",", states);
                changes.add("Converted data value " + dataValue + " to " + stateStr + " for " + base);
                return blockId + "[" + stateStr + "]";
            }
            return blockId;
        }

        // Doors: bit 3 (value 8) = upper half; lower half: bit 2 (value 4) = open
        if (base.endsWith("_door") && !base.equals("trapdoor")) {
            if ((dataValue & 8) == 0) {
                if ((dataValue & 4) != 0) {
                    changes.add("Converted data value " + dataValue + " to open=true,half=lower for " + base);
                    return blockId + "[open=true,half=lower]";
                }
                return blockId + "[half=lower]";
            } else {
                return blockId + "[half=upper]";
            }
        }

        // Fence gates: bit 2 (value 4) = open
        if (base.endsWith("_fence_gate")) {
            if ((dataValue & 4) != 0) {
                changes.add("Converted data value " + dataValue + " to open=true for " + base);
                return blockId + "[open=true]";
            }
            return blockId;
        }

        // Pressure plates: 0 = unpowered, 1+ = powered
        if (base.endsWith("_pressure_plate")) {
            if (dataValue > 0) {
                changes.add("Converted data value " + dataValue + " to powered=true for " + base);
                return blockId + "[powered=true]";
            }
            return blockId;
        }

        // Piston/sticky piston: bit 3 (value 8) = extended
        if (base.equals("piston") || base.equals("sticky_piston")) {
            if ((dataValue & 8) != 0) {
                changes.add("Converted data value " + dataValue + " to extended=true for " + base);
                return blockId + "[extended=true]";
            }
            return blockId;
        }

        // Dispenser/dropper: bit 3 (value 8) = triggered
        if (base.equals("dispenser") || base.equals("dropper")) {
            if ((dataValue & 8) != 0) {
                changes.add("Converted data value " + dataValue + " to triggered=true for " + base);
                return blockId + "[triggered=true]";
            }
            return blockId;
        }

        // Hopper: bit 3 (value 8) = disabled
        if (base.equals("hopper")) {
            if ((dataValue & 8) != 0) {
                changes.add("Converted data value " + dataValue + " to enabled=false for hopper");
                return blockId + "[enabled=false]";
            }
            return blockId;
        }

        // Repeater: bits 2-3 = delay (0-3 → 1-4 ticks)
        if (base.equals("repeater")) {
            int delay = ((dataValue >> 2) & 3) + 1;
            if (delay > 1) {
                changes.add("Converted data value " + dataValue + " to delay=" + delay + " for repeater");
                return blockId + "[delay=" + delay + "]";
            }
            return blockId;
        }

        // For other blocks, data value is not converted (usually orientation/color
        // which is already handled by block ID normalization)
        return blockId;
    }
}
