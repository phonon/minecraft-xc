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
import phonon.xc.utils.mapToObject
import phonon.xc.utils.EnumArrayMap
import phonon.xc.utils.Hitbox
import phonon.xc.utils.HitboxSize
import phonon.xc.utils.BlockCollisionHandler
import phonon.xc.utils.blockCollisionHandlers


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
    public val entityTargetable: EnumArrayMap<EntityType, Boolean> = Hitbox.defaultEntityTargetable(),
    
    // entity hitbox sizes
    public val entityHitboxSizes: EnumArrayMap<EntityType, HitboxSize> = Hitbox.defaultEntityHitboxSizes(),
    
    // block collision handlers
    public val blockCollision: EnumArrayMap<Material, BlockCollisionHandler> = blockCollisionHandlers(),
    
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
    public val landmineMinRedstoneCurrent: Int = 5,
    
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

    // armor settings
    public val armorEnforce: Boolean = false, // enforce server-side armor values
    public val armorValues: EnumMap<Material, Int> = EnumMap<Material, Int>(Material::class.java), // armor values

    // global sound effect settings
    public val soundOnHitEnabled: Boolean = true, // flag t oplay sound when shooting entity
    public val soundOnHit: String = "",           // sound to play when successfully hitting entity
    public val soundOnHitVolume: Float = 1.0f,    // volume of sound when successfully hitting entity

    // ADVANCED

    // stops player crawling when switching to a non-crawl to shoot weapon
    public val crawlOnlyAllowedOnCrawlWeapons: Boolean = false,
    
    // number of players before pipelined sway system enabled
    public val playersBeforePipelinedSway: Int = 4,

    // debug timings default value
    public val defaultDoDebugTimings: Boolean = false,

    // use async tasks to send packets to players
    public val asyncPackets: Boolean = true,

    // how often in ticks to schedule saving player death stats
    // default = 1 minute
    public val playerDeathRecordSaveInterval: Int = 20*60,

    // path to save player deaths stats to
    public val playerDeathLogSaveDir: String = "plugins/xc/logs",
) {

    companion object {
        /**
         * Parse and return a Config from a config.toml file.
         */
        public fun fromToml(source: Path, logger: Logger? = null): Config {
            val toml = Toml.parse(source)

            // map with keys as Config constructor property names
            val configOptions = HashMap<String, Any>()

            // parse toml file into configOptions

            // item config folder paths
            toml.getTable("configs")?.let { configsPaths -> 
                val pluginDataFolder = XC.plugin!!.getDataFolder().getPath()
                configsPaths.getString("ammo")?.let { path -> configOptions["pathFilesAmmo"] = Paths.get(pluginDataFolder, path) }
                configsPaths.getString("armor")?.let { path -> configOptions["pathFilesArmor"] = Paths.get(pluginDataFolder, path) }
                configsPaths.getString("gun")?.let { path -> configOptions["pathFilesGun"] = Paths.get(pluginDataFolder, path) }
                configsPaths.getString("landmine")?.let { path -> configOptions["pathFilesLandmine"] = Paths.get(pluginDataFolder, path) }
                configsPaths.getString("melee")?.let { path -> configOptions["pathFilesMelee"] = Paths.get(pluginDataFolder, path) }
                configsPaths.getString("misc")?.let { path -> configOptions["pathFilesMisc"] = Paths.get(pluginDataFolder, path) }
                configsPaths.getString("throwable")?.let { path -> configOptions["pathFilesThrowable"] = Paths.get(pluginDataFolder, path) }
            }

            // materials
            toml.getString("material.gun")?.let { s ->
                Material.getMaterial(s)?.let { configOptions["materialGun"] = it } ?: run {
                    logger?.warning("[material.gun] Invalid material: ${s}")
                }
            }
            toml.getString("material.aim_down_sights")?.let { s ->
                Material.getMaterial(s)?.let { configOptions["materialAimDownSights"] = it } ?: run {
                    logger?.warning("[material.aim_down_sights] Invalid material: ${s}")
                }
            }
            toml.getString("material.melee")?.let { s ->
                Material.getMaterial(s)?.let { configOptions["materialMelee"] = it } ?: run {
                    logger?.warning("[material.melee] Invalid material: ${s}")
                }
            }
            toml.getString("material.throwable")?.let { s ->
                Material.getMaterial(s)?.let { configOptions["materialThrowable"] = it } ?: run {
                    logger?.warning("[material.throwable] Invalid material: ${s}")
                }
            }
            toml.getString("material.ammo")?.let { s ->
                Material.getMaterial(s)?.let { configOptions["materialAmmo"] = it } ?: run {
                    logger?.warning("[material.ammo] Invalid material: ${s}")
                }
            }
            toml.getString("material.armor")?.let { s ->
                Material.getMaterial(s)?.let { configOptions["materialArmor"] = it } ?: run {
                    logger?.warning("[material.armor] Invalid material: ${s}")
                }
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
            }
            
            // crawl config
            toml.getBoolean("crawl.only_allowed_on_crawl_weapons")?.let { configOptions["crawlOnlyAllowedOnCrawlWeapons"] = it }

            // block damage config
            toml.getBoolean("block_damage.explosion")?.let { configOptions["blockDamageExplosion"] = it }
            
            // sway config
            toml.getLong("sway.players_before_pipelined_sway")?.let { configOptions["playersBeforePipelinedSway"] = it.toInt() }
            
            // player death messages and death record saving
            toml.getTable("deaths")?.let { deaths ->
                deaths.getString("message_explosion")?.let { configOptions["deathMessageExplosion"] = it }
                deaths.getString("message_wither")?.let { configOptions["deathMessageWither"] = it }
                deaths.getString("log_save_dir")?.let { configOptions["playerDeathLogSaveDir"] = it }
                deaths.getLong("save_interval")?.let { configOptions["playerDeathRecordSaveInterval"] = it.toInt() }
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
                            logger?.warning("[armor.values] Invalid material: ${key}")
                        }
                    }
                    configOptions["armorValues"] = values
                }
            }

            // default debug timings config
            toml.getBoolean("debug.do_timings_default")?.let { configOptions["defaultDoDebugTimings"] = it }

            // random advanced options
            toml.getBoolean("experimental.async_packets")?.let { configOptions["asyncPackets"] = it }


            return mapToObject(configOptions, Config::class)
        }
    }
}