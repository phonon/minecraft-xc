/**
 * Config
 * 
 * Contains global config state variables read in from 
 * plugin config.toml file
 */

package phonon.xc

import java.nio.file.Paths
import java.nio.file.Path
import java.util.UUID
import java.util.EnumSet
import java.util.EnumMap
import java.util.logging.Logger
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.tomlj.Toml
import phonon.xc.util.mapToObject
import phonon.xc.util.EnumArrayMap
import phonon.xc.util.EnumToIntMap
import phonon.xc.util.Hitbox
import phonon.xc.util.HitboxSize
import phonon.xc.util.BlockCollisionHandler
import phonon.xc.util.blockCollisionHandlers
import phonon.xc.util.blockCollisionHandlersPassthroughDoors
import phonon.xc.util.toml.getNumberAs


/**
 * Immutable XC config
 */
public data class Config(
    // paths to item config folders
    public val pathFilesAmmo: Path = Paths.get("plugins", "xc", "ammo"),
    public val pathFilesArmor: Path = Paths.get("plugins", "xc", "armor"),
    public val pathFilesGun: Path = Paths.get("plugins", "xc", "gun"),
    public val pathFilesLandmine: Path = Paths.get("plugins", "xc", "landmine"),
    public val pathFilesMelee: Path = Paths.get("plugins", "xc", "melee"),
    public val pathFilesMisc: Path = Paths.get("plugins", "xc", "misc"),
    public val pathFilesThrowable: Path = Paths.get("plugins", "xc", "throwable"),

    // flag that entity targetable
    // TODO: make configurable
    public val entityTargetable: EnumArrayMap<EntityType, Boolean> = Hitbox.defaultEntityTargetable(),
    
    // entity hitbox sizes
    // TODO: make configurable
    public val entityHitboxSizes: EnumArrayMap<EntityType, HitboxSize> = Hitbox.defaultEntityHitboxSizes(),
    
    // block collision handlers
    // TODO: make configurable
    public val blockCollision: EnumArrayMap<Material, BlockCollisionHandler> = blockCollisionHandlers(),
    
    // max number of item types, used to size storage arrays
    // value = max allowed item custom model id, which indexes 
    // directly to the item in storage arrays
    public val maxAmmoTypes: Int = 512,
    public val maxGunTypes: Int = 1024,
    public val maxMeleeTypes: Int = 1024,
    public val maxThrowableTypes: Int = 1024,
    public val maxHatTypes: Int = 1024,

    // material types for custom items
    public val materialAimDownSights: Material = Material.CARROT_ON_A_STICK, // phantom model for ads
    public val materialAmmo: Material = Material.SNOWBALL,
    public val materialArmor: Material = Material.LEATHER_HORSE_ARMOR,
    public val materialGun: Material = Material.WARPED_FUNGUS_ON_A_STICK,
    public val materialMelee: Material = Material.IRON_SWORD,
    public val materialThrowable: Material = Material.GOLDEN_HORSE_ARMOR,
    
    // ============================================================
    // GUN HANDLING/CONTROLS CONFIG 
    
    // auto fire max ticks before stopping
    public val autoFireMaxTicksSinceLastRequest: Int = 4,

    // recoil recovery rate per tick
    public val recoilRecoveryRate: Double = 0.2,
    
    // auto reload guns when empty and player fires
    public val autoReloadGuns: Boolean = true,
    
    // how many auto fire ticks to wait before starting auto-reload
    public val autoFireTicksBeforeReload: Int = 2,
    
    // ============================================================
    // LANDMINE CONFIG

    // landmine settings
    public val landmineMinRedstoneCurrent: Int = 5, // DEPRECATED, UNUSED
    // disable landmine item drop when block destroyed by player
    public val landmineDisableDrop: Boolean = true,

    // ============================================================

    // block damage
    public val blockDamageExplosion: Boolean = true,

    // particle effects
    
    // number of bullet impact particles to spawn
    public val particleBulletTrailSpacing: Double = 0.2,
    public val particleBulletTrailMinDistance: Double = 0.3,
    public val particleBulletImpactCount: Int = 12,

    // death messages (note: single quote must be '')
    public val deathMessageExplosion: String = "{0} was guro''d in an explosion",
    public val deathMessageWither: String = "{0} suffocated in poison gas",
    public val deathDropHead: Boolean = true, // drop player head on death

    // armor settings
    public val armorEnforce: Boolean = false, // enforce server-side armor values
    public val armorValues: EnumMap<Material, Int> = EnumMap<Material, Int>(Material::class.java), // armor values

    // global sound effect settings
    // note sound volume controls distance players can hear sound:
    // https://hub.spigotmc.org/stash/projects/SPIGOT/repos/craftbukkit/browse/src/main/java/org/bukkit/craftbukkit/CraftWorld.java#1566
    public val soundOnHitEnabled: Boolean = true, // flag t oplay sound when shooting entity
    public val soundOnHit: String = "",           // sound to play when successfully hitting entity
    public val soundOnHitVolume: Float = 1.0f,    // volume of sound when successfully hitting entity
    
    // ============================================================
    // sway settings
    public val swayMovementSpeedDecay: Double = 0.5, // exponential moving avg factor in calculating move speed
    public val swayMovementThreshold: Double = 3.0, // threshold
    public val playersBeforePipelinedSway: Int = 4, // number of players before pipelined sway system enabled
    
    // ============================================================
    // Built-in anti combat logging module
    public val antiCombatLogEnabled: Boolean = true, // use built in anti combat logging
    public val antiCombatLogTimeout: Double = 20.0,  // time in seconds to punish player for combat logging

    // ============================================================
    // ADVANCED

    // stops player crawling when switching to a non-crawl to shoot weapon
    public val crawlOnlyAllowedOnCrawlWeapons: Boolean = false,

    // debug timings default value
    public val defaultDoDebugTimings: Boolean = false,

    // how often in ticks to schedule saving player death stats
    // default = 1 minute
    public val playerDeathRecordSaveInterval: Int = 20*60,

    // path to save player deaths stats to
    public val playerDeathLogSaveDir: String = "plugins/xc/logs",
    
    // use async tasks to send packets to players
    public val asyncPackets: Boolean = true,

    // number of threads to use for parallelizing projectiles
    public val numProjectileThreads: Int = 4,
) {
    // modified block collision handlers that passthrough doors
    // since doors/trapdoors extremely common, this hardcoded set of
    // handlers lets specific weapons pass through doors.
    public val blockCollisionPassthroughDoors: EnumArrayMap<Material, BlockCollisionHandler>
        = blockCollisionHandlersPassthroughDoors(blockCollision)

    // Lookup table for Material => Int constant for plugin custom
    // item types defined by XC.ITEM_TYPE_* constants.
    public val materialToCustomItemType: EnumToIntMap<Material>

    init {
        materialToCustomItemType = EnumToIntMap.from<Material>({ mat ->
            when ( mat ) {
                materialAmmo -> XC.ITEM_TYPE_AMMO
                materialArmor -> XC.ITEM_TYPE_HAT
                materialGun -> XC.ITEM_TYPE_GUN
                materialMelee -> XC.ITEM_TYPE_MELEE
                materialThrowable -> XC.ITEM_TYPE_THROWABLE
                else -> XC.ITEM_TYPE_INVALID
            }
        })
    }

    companion object {
        /**
         * Parse and return a Config from a config.toml file.
         */
        public fun fromToml(
            source: Path,
            pluginDataFolder: String,
            logger: Logger,
        ): Config {
            val toml = Toml.parse(source)

            // map with keys as Config constructor property names
            val configOptions = HashMap<String, Any>()

            // parse toml file into configOptions

            // item config folder paths
            toml.getTable("configs")?.let { configsPaths ->
                configsPaths.getString("ammo")?.let { path -> configOptions["pathFilesAmmo"] = Paths.get(pluginDataFolder, path) }
                configsPaths.getString("armor")?.let { path -> configOptions["pathFilesArmor"] = Paths.get(pluginDataFolder, path) }
                configsPaths.getString("gun")?.let { path -> configOptions["pathFilesGun"] = Paths.get(pluginDataFolder, path) }
                configsPaths.getString("landmine")?.let { path -> configOptions["pathFilesLandmine"] = Paths.get(pluginDataFolder, path) }
                configsPaths.getString("melee")?.let { path -> configOptions["pathFilesMelee"] = Paths.get(pluginDataFolder, path) }
                configsPaths.getString("misc")?.let { path -> configOptions["pathFilesMisc"] = Paths.get(pluginDataFolder, path) }
                configsPaths.getString("throwable")?.let { path -> configOptions["pathFilesThrowable"] = Paths.get(pluginDataFolder, path) }
            }

            // materials (for item stacks)
            toml.getTable("material")?.let { materials ->
                materials.getString("gun")?.let { s ->
                    Material.getMaterial(s)?.let { configOptions["materialGun"] = it } ?: run {
                        logger.warning("[material.gun] Invalid material: ${s}")
                    }
                }
                materials.getString("aim_down_sights")?.let { s ->
                    Material.getMaterial(s)?.let { configOptions["materialAimDownSights"] = it } ?: run {
                        logger.warning("[material.aim_down_sights] Invalid material: ${s}")
                    }
                }
                materials.getString("melee")?.let { s ->
                    Material.getMaterial(s)?.let { configOptions["materialMelee"] = it } ?: run {
                        logger.warning("[material.melee] Invalid material: ${s}")
                    }
                }
                materials.getString("throwable")?.let { s ->
                    Material.getMaterial(s)?.let { configOptions["materialThrowable"] = it } ?: run {
                        logger.warning("[material.throwable] Invalid material: ${s}")
                    }
                }
                materials.getString("ammo")?.let { s ->
                    Material.getMaterial(s)?.let { configOptions["materialAmmo"] = it } ?: run {
                        logger.warning("[material.ammo] Invalid material: ${s}")
                    }
                }
                materials.getString("armor")?.let { s ->
                    Material.getMaterial(s)?.let { configOptions["materialArmor"] = it } ?: run {
                        logger.warning("[material.armor] Invalid material: ${s}")
                    }
                }
            }

            // max item types
            toml.getTable("max_types")?.let { maxTypes ->
                maxTypes.getLong("ammo")?.let { configOptions["maxAmmoTypes"] = it.toInt() }
                maxTypes.getLong("gun")?.let { configOptions["maxGunTypes"] = it.toInt() }
                maxTypes.getLong("melee")?.let { configOptions["maxMeleeTypes"] = it.toInt() }
                maxTypes.getLong("throwable")?.let { configOptions["maxThrowableTypes"] = it.toInt() }
                maxTypes.getLong("hat")?.let { configOptions["maxHatTypes"] = it.toInt() }
            }

            // gun configs
            toml.getTable("gun")?.let { gunConfig ->
                // auto fire max ticks since last request config
                gunConfig.getLong("auto_fire_max_ticks_since_last_request")?.let { configOptions["autoFireMaxTicksSinceLastRequest"] = it.toInt() }
                // recoil recovery rate
                gunConfig.getDouble("recoil_recovery_rate")?.let { configOptions["recoilRecoveryRate"] = it }
                // auto reload guns
                gunConfig.getBoolean("auto_reload_guns")?.let { configOptions["autoReloadGuns"] = it }
                // auto fire ticks before auto reload
                gunConfig.getLong("auto_fire_ticks_before_reload")?.let { configOptions["autoFireTicksBeforeReload"] = it.toInt() }
            }
            
            // landmine config
            toml.getTable("landmine")?.let { landmineConfig ->
                landmineConfig.getLong("min_redstone_current")?.let { configOptions["landmineMinRedstoneCurrent"] = it.toInt() }
                landmineConfig.getBoolean("disable_drop")?.let { configOptions["landmineDisableDrop"] = it }
            }
            
            // crawl config
            toml.getBoolean("crawl.only_allowed_on_crawl_weapons")?.let { configOptions["crawlOnlyAllowedOnCrawlWeapons"] = it }

            // block damage config
            toml.getBoolean("block_damage.explosion")?.let { configOptions["blockDamageExplosion"] = it }

            // player death messages and death record saving
            toml.getTable("deaths")?.let { deaths ->
                deaths.getString("message_explosion")?.let { configOptions["deathMessageExplosion"] = it }
                deaths.getString("message_wither")?.let { configOptions["deathMessageWither"] = it }
                deaths.getString("log_save_dir")?.let { configOptions["playerDeathLogSaveDir"] = it }
                deaths.getLong("save_interval")?.let { configOptions["playerDeathRecordSaveInterval"] = it.toInt() }
                deaths.getBoolean("drop_head")?.let { configOptions["deathDropHead"] = it }
            }

            // global sound effects
            toml.getTable("sound")?.let { sound ->
                sound.getString("on_hit")?.let { configOptions["soundOnHit"] = it }
                sound.getBoolean("on_hit_enabled")?.let { configOptions["soundOnHitEnabled"] = it }
                sound.getDouble("on_hit_volume")?.let { configOptions["soundOnHitVolume"] = it.toFloat() }
            }

            // armor enforcement settings
            toml.getTable("armor")?.let { armor ->
                armor.getBoolean("enforce")?.let { configOptions["armorEnforce"] = it }
                armor.getTable("values")?.let { armorValues ->
                    val values = EnumMap<Material, Int>(Material::class.java)
                    for ( key in armorValues.keySet() ) {
                        Material.matchMaterial(key)?.let { mat ->
                            armorValues.getLong(key)?.let { it ->
                                values[mat] = it.toInt()
                            }
                        } ?: run {
                            logger.warning("[armor.values] Invalid material: ${key}")
                        }
                    }
                    configOptions["armorValues"] = values
                }
            }
                        
            // sway config
            toml.getTable("sway")?.let { swayConfig ->
                swayConfig.getDouble("movement_speed_decay")?.let { configOptions["swayMovementSpeedDecay"] = it }
                swayConfig.getDouble("movement_threshold")?.let { configOptions["swayMovementThreshold"] = it }
                swayConfig.getLong("sway.players_before_pipelined_sway")?.let { configOptions["playersBeforePipelinedSway"] = it.toInt() }
            }
            
            // anti combat logging config
            toml.getTable("anti_combat_log")?.let { antiCombatLog ->
                antiCombatLog.getBoolean("enabled")?.let { configOptions["antiCombatLogEnabled"] = it }
                antiCombatLog.getNumberAs<Double>("timeout")?.let { configOptions["antiCombatLogTimeout"] = it }
            }

            // default debug timings config
            toml.getBoolean("debug.do_timings_default")?.let { configOptions["defaultDoDebugTimings"] = it }

            // random advanced options
            toml.getBoolean("experimental.async_packets")?.let { configOptions["asyncPackets"] = it }
            toml.getLong("experimental.num_projectile_threads")?.let { configOptions["numProjectileThreads"] = it.toInt() }


            return mapToObject(configOptions, Config::class)
        }
    }
}