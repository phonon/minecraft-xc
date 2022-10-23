/**
 * Config
 * 
 * Contains global config state variables read in from 
 * plugin config.toml file
 */

package phonon.xv

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
import phonon.xv.util.mapToObject


/**
 * Immutable XV config
 */
public data class Config(
    // world settings
    val seaLevel: Double = 63.0,

    // material types for custom items    
    val materialVehicle: Material = Material.IRON_HORSE_ARMOR,

    // culling system parameters
    val cullingEnabled: Boolean = false,
    val cullingPeriod: Int = 200,
    val cullingBorderMinX: Int = -1000,
    val cullingBorderMinY: Int = -1,
    val cullingBorderMinZ: Int = -1000,
    val cullingBorderMaxX: Int = 1000,
    val cullingBorderMaxY: Int = 300,
    val cullingBorderMaxZ: Int = 1000,
    
    // paths to vehicle configs
    val pathFilesVehicles: Path = Paths.get("plugins", "xv", "vehicle"),
    // paths to vehicle save state and backups
    val pathFilesBackup: Path = Paths.get("plugins", "xv", "backup"),
    val pathSave: Path = Paths.get("plugins", "xv", "save.json"),

    // save settings
    val savePeriod: Int = 200,
    val savePipelineLength: Int = 2,
    val saveBackupPeriod: Int = 18000,
) {
    
    companion object {
        /**
         * Parse and return a Config from a config.toml file.
         */
        public fun fromToml(source: Path, logger: Logger? = null): Config {
            val toml = Toml.parse(source)

            // map with keys as Config constructor property names
            val configOptions = HashMap<String, Any>()

            // parse toml file into config parameters

            // world
            toml.getTable("world")?.let { world -> 
                world.getDouble("sea_level")?.let { configOptions["seaLevel"] = it }
            }

            // material
            toml.getTable("material")?.let { material -> 
                toml.getString("vehicle")?.let { s ->
                    Material.getMaterial(s)?.let { configOptions["materialVehicle"] = it } ?: run {
                        logger?.warning("[material.vehicle] Invalid material: ${s}")
                    }
                }
            }

            // culling
            toml.getTable("culling")?.let { culling -> 
                culling.getBoolean("enabled")?.let { configOptions["cullingEnabled"] = it }
                culling.getLong("period")?.let { configOptions["cullingPeriod"] = it.toInt() }
                culling.getDouble("border_min_x")?.let { configOptions["cullingBorderMinX"] = it }
                culling.getDouble("border_min_y")?.let { configOptions["cullingBorderMinY"] = it }
                culling.getDouble("border_min_z")?.let { configOptions["cullingBorderMinZ"] = it }
                culling.getDouble("border_max_x")?.let { configOptions["cullingBorderMaxX"] = it }
                culling.getDouble("border_max_y")?.let { configOptions["cullingBorderMaxY"] = it }
                culling.getDouble("border_max_z")?.let { configOptions["cullingBorderMaxZ"] = it }
            }

            // paths
            toml.getTable("paths")?.let { paths -> 
                val pluginDataFolder = XV.plugin!!.getDataFolder().getPath()
                paths.getString("vehicle")?.let { configOptions["pathFilesVehicles"] = Paths.get(pluginDataFolder, it) }
                paths.getString("backup")?.let { configOptions["pathFilesBackup"] = Paths.get(pluginDataFolder, it) }
                paths.getString("save")?.let { configOptions["pathSave"] = Paths.get(pluginDataFolder, it) }
            }

            // save
            toml.getTable("save")?.let { save -> 
                save.getLong("period")?.let { configOptions["savePeriod"] = it.toInt() }
                save.getLong("pipeline_length")?.let { configOptions["savePipelineLength"] = it.toInt() }
                save.getLong("backup_period")?.let { configOptions["saveBackupPeriod"] = it.toInt() }
            }

            return mapToObject(configOptions, Config::class)
        }
    }
}