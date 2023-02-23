/**
 * Contain main engine global state and core api.
 */

package phonon.xv

import java.nio.ByteBuffer
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.Files
import java.util.Queue
import java.util.ArrayDeque
import java.util.UUID
import java.util.logging.Logger
import java.util.LinkedList
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import org.bukkit.inventory.ItemStack
import phonon.xv.core.*
import phonon.xv.system.*
import phonon.xv.common.UserInput
import phonon.xv.util.entity.reassociateEntities
import phonon.xv.util.file.listDirFiles
import phonon.xv.util.file.newBackupPath
import phonon.xv.util.file.readJson
import phonon.xv.util.file.writeJson
import phonon.xv.util.TaskProgress


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
    
    //// VEHICLE BASE PROTOTYPE CONFIGS
    internal var vehiclePrototypes: Map<String, VehiclePrototype> = mapOf()
    internal var vehiclePrototypeNames: List<String> = listOf() // for tab completion
    // vehicle prototype default spawn item stacks
    internal var vehiclePrototypeSpawnItem: Map<String, ItemStack> = mapOf()
    internal var vehiclePrototypeSpawnItemList: List<ItemStack> = listOf() // flattened list of `vehiclePrototypeSpawnItem`
    // vehicle element prototypes
    // the keys in this map are <vehicle.name>.<element.name>, not just the element name
    internal var vehicleElementPrototypes: Map<String, VehicleElementPrototype> = mapOf()
    // list of <vehicle.name>.<element.name>
    internal var vehicleElementPrototypeNames: List<String> = listOf()

    //// VEHICLE ELEMENT COMPONENT INSTANCE STORAGE
    internal val storage: ComponentsStorage = ComponentsStorage()
    internal val vehicleStorage: VehicleStorage = VehicleStorage(config.maxVehicles)

    //// VEHICLE SKINS/DECALS STORAGE
    internal var skins: SkinStorage = SkinStorage.empty()

    //// ENGINE STATE
    // if vehicles state has been loaded at least once
    internal var isLoaded: Boolean = false

    // user input controls when mounted on entities
    internal val userInputs: HashMap<UUID, UserInput> = HashMap()

    // entity uuid => vehicle element data
    internal val entityVehicleData: HashMap<UUID, EntityVehicleData> = HashMap()

    // element uuid -> element
    internal val uuidToElement: HashMap<UUID, VehicleElement> = HashMap()
    // vehicle uuid -> element
    internal val uuidToVehicle: HashMap<UUID, Vehicle> = HashMap()
    
    // player uuid -> task
    internal val playerTasks: HashMap<UUID, TaskProgress> = HashMap()

    // save system (includes save pipeline state)
    internal var savingVehicles: Boolean = false
    internal var saveVehiclesQueue: Array<Vehicle?> = arrayOf()
    internal var saveVehiclesJsonBuffer: ArrayList<JsonObject> = arrayListOf()
    internal var saveVehiclesQueueIndex: Int = 0
    internal var saveVehiclesPerTick: Int = config.saveMinVehiclesPerTick
    internal var saveTimer: Int = config.savePeriod
    internal var saveBackupTimer: Int = config.saveBackupPeriod

    // Systems state
    // player mount and dismount requests
    internal var mountRequests: ArrayList<MountVehicleRequest> = ArrayList()
    internal var dismountRequests: ArrayList<DismountVehicleRequest> = ArrayList()
    // vehicle creation/deletion requests
    internal var createRequests: Queue<CreateVehicleRequest> = LinkedList()
    internal var deleteRequests: Queue<DestroyVehicleRequest> = LinkedList()
    // vehicle spawn/despawn system queues
    // finish queues are pushed from async threads so must be thread safe
    internal var spawnRequests: Queue<SpawnVehicleRequest> = ArrayDeque()
    internal var spawnFinishQueue: ConcurrentLinkedQueue<SpawnVehicleFinish> = ConcurrentLinkedQueue()
    internal var despawnRequests: Queue<DespawnVehicleRequest> = ArrayDeque()
    internal var despawnFinishQueue: ConcurrentLinkedQueue<DespawnVehicleFinish> = ConcurrentLinkedQueue()
    // vehicle interaction queue
    internal var interactRequests: Queue<VehicleInteract> = ArrayDeque()
    // fuel system state
    internal var fuelRequests: Queue<FuelVehicleRequest> = ArrayDeque()
    internal var fuelFinishQueue: ConcurrentLinkedQueue<FuelVehicleFinish> = ConcurrentLinkedQueue()

    // ========================================================================
    // RUNNING TASKS
    // ========================================================================
    internal var engineTask: BukkitTask? = null

    internal fun clearState() {
        // clear storage and lookup
        storage.clear()
        vehicleStorage.clear()
        userInputs.clear()
        entityVehicleData.clear()
        uuidToVehicle.clear()
        uuidToElement.clear()
        // clear system state
        mountRequests.clear()
        dismountRequests.clear()
        createRequests.clear()
    }

    /**
     * Re-initialize storages and re-load config.
     * TODO: async
     */
    internal fun reload() {
        val timeStart = System.currentTimeMillis()

        // save existing vehicles state
        if ( this.isLoaded ) {
            this.saveVehiclesJson()
        }

        clearState()

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

        // reset vehicle save timers
        this.saveTimer = config.savePeriod
        this.saveBackupTimer = config.saveBackupPeriod

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
            "vehicle/debug_humvee.toml",
            "vehicle/debug_mg34.toml",
            "vehicle/debug_mortar.toml",
            "vehicle/debug_multi_turret.toml",
            "vehicle/debug_tank.toml",
            "vehicle/debug_ship.toml",
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

        // create default vehicle prototype spawn items (for admins to browse and view)
        val newVehiclePrototypeSpawnItem = HashMap<String, ItemStack>()
        for ( (name, proto) in vehiclePrototypes ) {
            try {
                newVehiclePrototypeSpawnItem[name] = proto.toItemStack(this.config.materialVehicle)
            } catch (err: Exception) {
                this.logger.warning("Failed to create spawn item for vehicle prototype '${name}': ${err.message}")
                err.printStackTrace()
                continue
            }
        }

        // save loaded state
        this.vehicleElementPrototypes = elementPrototypes
        this.vehicleElementPrototypeNames = this.vehicleElementPrototypes.keys.toList()
        this.vehiclePrototypes = vehiclePrototypes
        this.vehiclePrototypeNames = vehiclePrototypes.keys.toList()
        this.vehiclePrototypeSpawnItem = newVehiclePrototypeSpawnItem
        this.vehiclePrototypeSpawnItemList = newVehiclePrototypeSpawnItem.values.toList()

        // load vehicles from json
        this.loadVehiclesJson(this.config.pathSave)

        // reassociate armorstands with newly loaded components
        Bukkit.getWorlds().forEach { w ->
            reassociateEntities(this, w.entities)
        }

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
            this.logger.info("Stopping engine.")
        } else {
            this.logger.warning("Engine not running.")
        }
    }

    /**
     * Create a new vehicle and insert into archetype storages.
     */
    public fun createVehicle(
        vehicleBuilder: VehicleBuilder,
    ) {
        val prototype = vehicleBuilder.prototype
        val elementBuilders = vehicleBuilder.elements

        // try to insert each prototype into its archetype
        val elementIds: List<VehicleElementId?> = elementBuilders.map { elem ->
            storage.lookup[elem.components.layout]!!.insert(elem.components)
        }
        
        // if any are null, creation failed. remove non-null created elements
        // from their archetypes
        if ( elementIds.any { it === null } ) {
            logger.severe("Failed to create vehicle ${prototype.name}")

            elementIds.forEachIndexed { index, id ->
                if ( id !== null ) {
                    storage.lookup[elementBuilders[index].components.layout]?.free(id)
                } else {
                    logger.severe("Failed to create element ${elementBuilders[index].prototype.name}")
                }
            }

            // failed, early exit
            return
        }

        // create vehicle elements from ids
        val elements = elementBuilders.mapIndexed { idx, elem ->
            val id = elementIds[idx]!!
            val elem = VehicleElement(
                name="${prototype.name}.${elem.prototype.name}.${id}",
                uuid=elem.uuid,
                id=id,
                prototype=elem.prototype,
                layout=elem.components.layout,
                components=elem.components,
            )
            this.uuidToElement[elem.uuid] = elem
            elem
        }
        
        // set parent/children hierarchy
        for ( (idx, elem) in elements.withIndex() ) {
            val parentIdx = prototype.parentIndex[idx]
            if ( parentIdx != -1 ) {
                elem.parent = elements[parentIdx]
            }
            
            val childrenIdx = prototype.childrenIndices[idx]
            if ( childrenIdx.isNotEmpty() ) {
                elem.children = childrenIdx.map { elements[it] }
            }
        }

        // insert new vehicle
        val vehicle = this.vehicleStorage.insert(
            vehicleBuilder.uuid,
            prototype=prototype,
            elements=elements,
        )
        // this should never happen, but check
        if ( vehicle === null ) {
            logger.severe("Failed to create vehicle ${prototype.name}: vehicle storage full")
            // free elements
            elements.forEach { elem -> this.storage.lookup[elem.layout]?.free(elem.id) }
            return
        }

        this.uuidToVehicle[vehicle.uuid] = vehicle
        // xv.logger.info("Created vehicle with uuid $vehicleUuid")

        // do element post-processing (note: can use prototype because
        // it still holds references to components)
        // for elements with armorstands models, this does entity -> element mapping
        for ( elem in elements ) {
            elem.components.afterVehicleCreated(
                vehicle=vehicle,
                element=elem,
                entityVehicleData=this.entityVehicleData,
            )
        }
    }

    /**
     * Remove a vehicle and remove from archetype storages.
     */
    public fun deleteVehicle(
        vehicle: Vehicle,
        despawn: Boolean = false,
    ) {
        // free vehicle
        vehicleStorage.free(vehicle.id)
        // free vehicle elements
        vehicle.elements.forEach { element ->
            // handle component specific deletion handlers
            element.components.delete(
                vehicle,
                element,
                this.entityVehicleData,
                despawn,
            )
            // free from archetype
            val archetype = this.storage.lookup[element.layout]!!
            archetype.free(element.id)
        }
    }

    /**
     * Serialize input list of vehicles json into a full output file.
     * If input `vehiclesJson` is null, this will serialize all vehicles
     * in the storage.
     * 
     * Internally a wrapper around a save function runnable task
     * `TaskSaveJson` to allow running save either on main thread or
     * async thread.
     */
    internal fun saveVehiclesJson(
        vehiclesJson: List<JsonObject>? = null,
        async: Boolean = false,
        backup: Boolean = false,
    ) {
        // convert to minimal save state object
        val vehiclesJson = vehiclesJson ?: this.vehicleStorage.map { v -> v.toJson() }

        //// DEBUGGING
        // println("Saving vehicles ${vehiclesJson}")

        val task = TaskSaveJson(
            this.config.pathSave,
            vehiclesJson,
            prettyPrint = this.config.savePrettyPrintingJson,
            writeBackup = backup,
            pathBackupFolder = this.config.pathFilesBackup,
        )

        if ( async ) {
            Bukkit.getScheduler().runTaskAsynchronously(this.plugin, task)
        } else {
            task.run()
        }
    }

    /**
     * Internal task to finish serializing all vehicles to json and save
     * json to disk.
     */
    internal class TaskSaveJson(
        val path: Path,
        val vehiclesJson: List<JsonObject>,
        val prettyPrint: Boolean = false,
        val writeBackup: Boolean = false,
        val pathBackupFolder: Path? = null,
    ): Runnable {
        override fun run() {
            val json = JsonObject()
            val vehiclesJsonArray = JsonArray()
            vehiclesJson.forEach { vehiclesJsonArray.add(it) }
            json.add("vehicles", vehiclesJsonArray)

            try {
                writeJson(json, path, prettyPrint)
            }
            catch ( err: Exception ) {
                err.printStackTrace()
            }

            // write backup
            if ( writeBackup && pathBackupFolder !== null ) {
                val pathBackup = newBackupPath(pathBackupFolder)
                
                // create backup folder if does not exist
                if ( !Files.exists(pathBackupFolder) ) {
                    Files.createDirectories(pathBackupFolder)
                }

                try {
                    writeJson(json, pathBackup, prettyPrint)
                }
                catch ( err: Exception ) {
                    err.printStackTrace()
                }
            }
        }
    }

    /**
     * Load vehicles from save json file.
     */
    internal fun loadVehiclesJson(
        pathJson: Path,
    ) {
        val vehicleBuilders = ArrayList<VehicleBuilder>()

        try {
            val json = readJson(pathJson)
            
            if ( json === null ) {
                logger.warning("No vehicle save file found at $pathJson, skipping load")
                return
            }

            for ( jsonElem in json.get("vehicles").asJsonArray ) {
                try {
                    val vehicleJson = jsonElem.asJsonObject
                    val vehicleUuid = UUID.fromString(vehicleJson["uuid"].asString)
                    val vehiclePrototypeName = vehicleJson["prototype"].asString
                    val vehiclePrototype = this.vehiclePrototypes[vehiclePrototypeName]
                    if ( vehiclePrototype === null ) {
                        logger.severe("Failed to load vehicle in json ${jsonElem} with invalid prototype ${vehiclePrototypeName}")
                        continue
                    }
                    
                    val vehicleElementsJson = vehicleJson["elements"].asJsonObject
                    
                    // inject json properties into all element prototypes
                    val elementBuilders: List<VehicleElementBuilder> = vehiclePrototype.elements.map { elemPrototype ->
                        var components = elemPrototype.components.clone()
                        
                        val elemJson = vehicleElementsJson?.get(elemPrototype.name)?.asJsonObject
                        if ( elemJson !== null ) {
                            val componentsJson = elemJson["components"].asJsonObject
                            VehicleElementBuilder(
                                prototype = elemPrototype,
                                uuid = UUID.fromString(elemJson["uuid"].asString),
                                components = components.injectJsonProperties(componentsJson),
                            )
                        } else { // no json for this element, can occur if a vehicle config was modified with new component
                            VehicleElementBuilder(
                                prototype = elemPrototype,
                                uuid = UUID.randomUUID(),
                                components = components,
                            )
                        }
                    }

                    vehicleBuilders.add(
                        VehicleBuilder(
                            prototype = vehiclePrototype,
                            uuid = vehicleUuid,
                            elements = elementBuilders,
                        )
                    )

                } catch ( err: Exception ) {
                    logger.severe("Failed to load vehicle in json: $jsonElem")
                    err.printStackTrace()
                }
            }

        } catch ( err: Exception ) {
            logger.severe("Failed to load vehicles from $pathJson")
            err.printStackTrace()
        }

        // try to create vehicles
        for ( vehicleBuilder in vehicleBuilders ) {
            try {
                this.createVehicle(vehicleBuilder)
            } catch ( err: Exception ) {
                logger.severe("Failed to load vehicle from json: ${vehicleBuilder}")
                err.printStackTrace()
            }
        }
    }

    /**
     * Start task and track task for player.
     */
    public fun startTaskForPlayer(
        player: Player,
        task: TaskProgress,
        period: Long = 2,
        delay: Long = 2,
    ) {
        val playerUuid = player.getUniqueId()
        // cancel current task if it exists
        val currTask = this.playerTasks[playerUuid]
        if ( currTask !== null ) {
            if ( !currTask.isCancelled() ) {
                currTask.cancel() // make sure cancelled
            }
        }
        task.runTaskTimerAsynchronously(this.plugin, delay, period)
        this.playerTasks[playerUuid] = task
    }

    /**
     * Remove current task for player if it exists.
     */
    public fun removeTaskForPlayer(
        player: Player,
    ) {
        val currTask = this.playerTasks[player.getUniqueId()]
        if ( currTask !== null ) {
            if ( !currTask.isCancelled() ) {
                currTask.cancel() // make sure cancelled
            }
            this.playerTasks.remove(player.getUniqueId())
        } 
    }

    /**
     * Check if player is currently running a `TaskProgress`.
     * Do two checks:
     * 1. Check if player has a task in `playerTasks`
     * 2. Check if task seems stale but uncollected, e.g. current time is
     *    much greater than `tFinish` for the task.
     */
    public fun isPlayerRunningTask(
        player: Player,
    ): Boolean {
        val currTask = this.playerTasks[player.getUniqueId()]
        if ( currTask !== null ) {
            if ( currTask.isCancelled() ) {
                this.playerTasks.remove(player.getUniqueId())
                return false
            }
            
            val t = System.currentTimeMillis()
            if ( t > currTask.tFinish + 1000 ) { // >1s after task supposed to be finished, must be stale
                currTask.cancel() // make sure cancelled
                this.playerTasks.remove(player.getUniqueId())
                return false
            } else {
                return true
            }
        } else {
            return false
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
        // handling vehicle saving timer
        systemPipelinedSave()

        // spawn and create vehicle handlers
        systemSpawnVehicle(spawnRequests, spawnFinishQueue)
        systemFinishSpawnVehicle(spawnFinishQueue, createRequests)
        systemCreateVehicle(storage, createRequests)

        // vehicle interact requests
        systemInteract(interactRequests)
        
        // mount and dismount request handlers
        mountRequests = systemMountVehicle(storage, mountRequests) // filters direct interaction mounting
        dismountRequests = systemDismountVehicle(storage, dismountRequests)

        // vehicle fuel loading systems
        systemFuelVehicle(fuelRequests, fuelFinishQueue)
        systemFinishFuelVehicle(fuelFinishQueue)

        // player vehicle movement controls
        systemLandMovementFuel(storage)
        systemLandMovement(storage, userInputs)
        systemShipMovement(storage, userInputs)

        // vehicle gun controls
        systemGunBarrelControls(storage, userInputs)
        systemGunTurretControls(storage, userInputs)
        
        // update vehicle models after transforms updated
        // MUST BE RUN AFTER ALL MOVEMENT CONTROLLERS
        systemUpdateModels(storage)
        systemUpdateSeats(storage, userInputs)

        // seat raycast mounting
        mountRequests = systemMountSeatRaycast(storage, mountRequests)
        
        // sends info text to players
        systemLandVehicleInfoText(storage)

        systemDespawnVehicle(despawnRequests, despawnFinishQueue)
        systemFinishDespawnVehicle(despawnFinishQueue, deleteRequests)
        systemDestroyVehicle(vehicleStorage, storage, deleteRequests)
    }
}