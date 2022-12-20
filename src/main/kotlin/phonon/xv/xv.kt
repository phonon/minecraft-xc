/**
 * Contain main engine global state and core api.
 */

package phonon.xv

import java.nio.file.Paths
import java.nio.file.Files
import java.util.UUID
import java.util.logging.Logger
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import phonon.xv.core.*
import phonon.xv.system.*
import phonon.xv.common.UserInput
import phonon.xv.util.file.listDirFiles
import java.util.LinkedList
import java.util.Queue

public const val MAX_VEHICLES = 5000

/**
 * XV engine global state.
 * Stores all game state and provide XV engine api.
 */
public class XV (
    // spigot plugin variables links
    internal val plugin: Plugin,
    internal val logger: Logger,
) {
    // ========================================================================
    // STORAGE
    // ========================================================================
    internal var config: Config = Config()
    
    // vehicle base prototypes
    internal var vehiclePrototypes: Map<String, VehiclePrototype> = mapOf()
    internal var vehiclePrototypeNames: List<String> = listOf() // for tab completion
    // vehicle element prototypes
    // the keys in this map are <vehicle.name>.<element.name>, not just the element name
    internal var vehicleElementPrototypes: Map<String, VehicleElementPrototype> = mapOf()
    // list of <vehicle.name>.<element.name>
    internal var vehicleElementPrototypeNames: List<String> = listOf()

    // components
    internal val storage: ComponentsStorage = ComponentsStorage()
    internal val vehicleStorage: VehicleStorage = VehicleStorage(MAX_VEHICLES)

    // vehicle skins/decals definitions
    internal var skins: SkinStorage = SkinStorage.empty()

    // user input controls when mounted on entities
    internal val userInputs: HashMap<UUID, UserInput> = HashMap()

    // entity uuid => vehicle element data
    internal val entityVehicleData: HashMap<UUID, EntityVehicleData> = HashMap()

    // element uuid -> element
    internal val uuidToElement: HashMap<UUID, VehicleElement> = HashMap()
    // vehicle uuid -> element
    internal val uuidToVehicle: HashMap<UUID, Vehicle> = HashMap()

    // player mount and dismount requests
    internal var mountRequests: ArrayList<MountVehicleRequest> = ArrayList()
    internal var dismountRequests: ArrayList<DismountVehicleRequest> = ArrayList()
    // vehicle creation requests
    internal var createRequests: Queue<CreateVehicleRequest> = LinkedList()

    // ========================================================================
    // RUNNING TASKS
    // ========================================================================
    internal var engineTask: BukkitTask? = null

    /**
     * Re-initialize storages and re-load config.
     * TODO: async
     */
    internal fun reload() {
        val timeStart = System.currentTimeMillis()

        // load main plugin config
        val pluginDataFolder = Paths.get(this.plugin.getDataFolder().getPath())
        val pathConfigToml = pluginDataFolder.resolve("config.toml")
        val config = if ( Files.exists(pathConfigToml) ) {
            Config.fromToml(pathConfigToml, pluginDataFolder, this.logger)
        } else {
            this.logger.info("Creating default config.toml")
            this.plugin.saveResource("config.toml", false)
            Config()
        }

        this.config = config

        // clear current component/archetype storage
        storage.clear()

        // load vehicle skin files (do before vehicles because vehicles
        // may reference skins)
        val skinConfigFiles = listDirFiles(config.pathFilesSkins)
            .filter { f -> f.toString().lowercase().endsWith(".toml") }
            .map { f -> config.pathFilesSkins.resolve(f) }
        this.skins = SkinStorage.fromTomlFiles(skinConfigFiles, this.logger)

        // load vehicle config files

        // wtf why isnt it saving this shit automatically??
        listOf(
            "vehicle/debug_cannon.toml",
            "vehicle/debug_car.toml",
            "vehicle/debug_mortar.toml",
            "vehicle/debug_multi_turret.toml",
            "vehicle/debug_tank.toml",
        ).forEach { p -> this.plugin.saveResource(p, false) }

        val elementPrototypes = mutableMapOf<String, VehicleElementPrototype>()

        val vehiclePrototypes: Map<String, VehiclePrototype> = listDirFiles(config.pathFilesVehicles)
            .map { f -> VehiclePrototype.fromTomlFile(config.pathFilesVehicles.resolve(f), this.logger) }
            .filterNotNull()
            .map { v -> // add layouts of each element prototype
                // perhaps we can relegate this to when vehicles are spawned
                // first vehicle to spawn w/ new layout gets ArchetypeStorage for that
                // layout added on creation
                v.elements.forEach { e ->
                    if (!storage.lookup.containsKey(e.layout))
                        storage.addLayout(e.layout)
                    elementPrototypes["${v.name}.${e.name}"] = e
                }
                v.name to v
            }
            .toMap()

        this.vehicleElementPrototypes = elementPrototypes
        this.vehicleElementPrototypeNames = this.vehicleElementPrototypes.keys.toList()
        this.vehiclePrototypes = vehiclePrototypes
        this.vehiclePrototypeNames = vehiclePrototypes.keys.toList()
        
        // finish: print stats
        val timeEnd = System.currentTimeMillis()
        val timeLoad = timeEnd - timeStart
        this.logger.info("Reloaded in ${timeLoad}ms")
        this.logger.info("- Prototypes: ${vehiclePrototypes.size}")
        this.logger.info("- Skins: ${this.skins.size}")
    }

    /**
     * Starts running engine task
     */
    internal fun start() {
        if ( this.engineTask == null ) {
            val xv = this // alias for lambda
            
            this.engineTask = Bukkit.getScheduler().runTaskTimer(this.plugin, object: Runnable {
                // number of successive errors caught on each update
                var errorAccumulator = 0

                // max errors before resetting engine to clean slate
                val maxErrorsBeforeCleanup = 100

                override fun run() {
                    // wrap update in try catch...
                    // right now engine is very fragile, a single in loop can 
                    // basically cause loop to keep failing at same spot...need to make
                    // each system more resilient with internal try/catch...
                    // but for now just catch an err, accumulate error count, then after
                    // threshold hit, reset engine to a clean state
                    try {
                        xv.update()
                        errorAccumulator = 0
                    } catch ( e: Exception ) {
                        logger.severe("Engine update failed:")
                        e.printStackTrace()
                        
                        // accumulate errors. if we reach past threshold,
                        // reset engine to clean slate
                        errorAccumulator += 1
                        if ( errorAccumulator >= maxErrorsBeforeCleanup ) {
                            logger.severe("Engine reached error threshold...resetting to clean slate")
                            // cleanup() // TODO
                            // initializeSystems() // TODO
                            errorAccumulator = 0
                        }
                    }
                }
            }, 0, 0)

            this.logger.info("Starting engine")
        }
        else {
            this.logger.warning("Engine already running")
        }
    }

    /**
     * Stop running engine task
     */
    internal fun stop() {
        val task = this.engineTask
        if ( task != null ) {
            task.cancel()
            this.engineTask = null
            this.logger.info("Stopping engine")
        } else {
            this.logger.warning("Engine not running")
        }
    }

    /**
     * Main engine update, runs on each tick
     * 
     * TODO: MULTI-WORLD
     * If we want vehicles in overworld, nether, end...
     * Need to run each world simulation separately
     * Make per world storages and request queues
     *     World => {
     *      storage: ComponentsStorage,
     *      playerMountRequests: ArrayList<MountVehicleRequest>,
     *      ...
     *     }
     * (Some stuff still shared, like global UserInputs)
     * 
     * TODO: EVENTUAL OPTIMIZATION
     * We will have MANY systems, some of which will be extremely specific
     * for vehicle combinations that may not even exist in configs.
     * An optimization is to convert systems into a singleton functions
     * with layout metadata and/or queries, e.g.
     * 
     * interface System {
     *   val layout: EnumSet<VehicleComponentType>?  // describes what components needed
     *   var valid: Boolean   // cache if system valid for current config archetypes
     * 
     *   fun execute() {
     *     // existing system function
     *   }
     * }
     * 
     * object SystemLandMovement: System {
     *    override val layout = EnumSet.of(
     *        VehicleComponentType.TRANSFORM,
     *        VehicleComponentType.MODEL,
     *        VehicleComponentType.LAND_MOVEMENT_CONTROLS,
     *   )
     *   override val valid = true // engine will update this
     *   
     *   override fun execute() { ... }
     * }
     * 
     * And eventually convert update into a Schedule object that is
     * re-created each time engine is reloaded, which updates each
     * system's metadata to turn off invalid systems.
     */
    internal fun update() {
        // mount and dismount request handlers
        mountRequests = systemMountVehicle(storage, mountRequests) // filters direct interaction mounting
        dismountRequests = systemDismountVehicle(storage, dismountRequests)

        // player vehicle movement controls
        systemLandMovement(storage, userInputs)

        // update vehicle models after transforms updated
        // MUST BE RUN AFTER ALL MOVEMENT CONTROLLERS
        systemUpdateModels(storage)
        systemUpdateSeats(storage, userInputs)

        // seat raycast mounting
        mountRequests = systemMountSeatRaycast(storage, mountRequests)

        // create vehicle handlers
        systemCreateVehicle(storage, createRequests)
    }
}