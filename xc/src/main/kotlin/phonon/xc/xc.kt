/*
 * XC Engine/API
 * 
 * A minimal minecraft custom combat engine.
 * To support guns, melee, hats, and vehicles.
 * Intended for personal server.
 */

package phonon.xc

import java.nio.file.Paths
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadLocalRandom
import java.util.EnumMap
import java.util.EnumSet
import java.util.UUID
import java.util.logging.Logger
import kotlin.math.max
import kotlin.math.min
import kotlin.math.floor
import kotlin.math.ceil
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.Particle
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Damageable
import org.bukkit.plugin.Plugin
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitTask
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector

// i hate java
import phonon.xc.ammo.*
import phonon.xc.armor.*
import phonon.xc.gun.*
import phonon.xc.gun.crawl.*
import phonon.xc.gun.TaskCalculatePlayerSpeed
import phonon.xc.landmine.*
import phonon.xc.melee.*
import phonon.xc.throwable.*
import phonon.xc.util.anticombatlog.TaskAntiCombatLog
import phonon.xc.util.anticombatlog.killCombatLoggerSystem
import phonon.xc.util.EnumArrayMap
import phonon.xc.util.mapToObject
import phonon.xc.util.Hitbox
import phonon.xc.util.HitboxSize
import phonon.xc.util.particle.*
import phonon.xc.util.recoil.*
import phonon.xc.util.sound.*
import phonon.xc.util.debug.DebugTimings
import phonon.xc.util.blockCrackAnimation.*
import phonon.xc.util.file.listDirFiles
import phonon.xc.util.WorldGuard
import phonon.xc.util.death.*

// TODO: in future need to select NMS version
import phonon.xc.nms.gun.crawl.*

// ========================================================================
// BUILT-IN ENGINE CONSTANTS TODO MAKE THESE CONFIG
// ========================================================================
public const val INVALID_ITEM_ID: Int = Int.MAX_VALUE       // sentinel value for invalid IDs

/**
 * Container for all XC custom object storage.
 * Include helper functions to get custom items from Bukkit Item stacks
 * or player inventory.
 */
public class CustomItemStorage(
    // ammo lookup
    public val ammo: Array<Ammo?>,
    public val ammoIds: IntArray,
    
    // gun storage and lookup
    public val gun: Array<Gun?>,
    public val gunIds: IntArray,

    // melee weapon storage and lookup
    public val melee: Array<MeleeWeapon?>,
    public val meleeIds: IntArray,
    
    // custom hat (helmet) storage and lookup
    public val hat: Array<Hat?>,
    public val hatIds: IntArray,
    
    // melee weapon storage and lookup
    public val throwable: Array<ThrowableItem?>,
    public val throwableIds: IntArray,
    
    // landmine storage: material => landmine properties lookup
    public val landmine: EnumMap<Material, Landmine>,
) {
    
    companion object {
        /**
         * Return a storage with zero sized arrays, as a placeholder.
         */
        public fun empty(): CustomItemStorage {
            return CustomItemStorage(
                ammo = arrayOf(),
                ammoIds = intArrayOf(),
                gun = arrayOf(),
                gunIds = intArrayOf(),
                melee = arrayOf(),
                meleeIds = intArrayOf(),
                hat = arrayOf(),
                hatIds = intArrayOf(),
                throwable = arrayOf(),
                throwableIds = intArrayOf(),
                landmine = EnumMap(Material::class.java),
            )
        }
    }
}

/**
 * XC engine global state.
 * Stores all game state and provide XC engine API.
 * This singleton instance should be stored within the plugin.
 */
public class XC(
    // spigot plugin variables links
    internal val plugin: Plugin,
    internal val logger: Logger,
) {
    // CONSTANTS
    companion object {
        // item types (using int const instead of enum)
        public const val ITEM_TYPE_INVALID: Int = -1
        public const val ITEM_TYPE_AMMO: Int = 0
        public const val ITEM_TYPE_GUN: Int = 1
        public const val ITEM_TYPE_MELEE: Int = 2
        public const val ITEM_TYPE_THROWABLE: Int = 3
        public const val ITEM_TYPE_HAT: Int = 4
        public const val ITEM_TYPE_LANDMINE: Int = 5
        
        // constant int for indicating player combat logged, instead of
        // an int. used for player death events...yes this is dirty and
        // not "type safe"
        public const val COMBAT_LOGGED: Int = 1000
    }
    
    // namespaced keys for custom item stack properties
    internal val namespaceKeyItemAmmo = NamespacedKey(plugin, "ammo")                  // key for item ammo value
    internal val namespaceKeyItemReloading = NamespacedKey(plugin, "reloading")        // key for item is reloading (0 or 1)
    internal val namespaceKeyItemReloadId = NamespacedKey(plugin, "reloadId")          // key for item reload id
    internal val namespaceKeyItemReloadTimestamp = NamespacedKey(plugin, "reloadTime") // key for item reload timestamp
    internal val namespaceKeyItemBurstFireId = NamespacedKey(plugin, "burstId")        // key for item burst firing id
    internal val namespaceKeyItemAutoFireId = NamespacedKey(plugin, "autoId")          // key for item auto firing id
    internal val namespaceKeyItemCrawlToShootId = NamespacedKey(plugin, "crawlId")     // key for item crawl to shoot id
    internal val namespaceKeyItemThrowableId = NamespacedKey(plugin, "throwId")        // key for item throwable id

    // string form of namespaced keys, for raw nbt lookups
    internal val nbtKeyItemAmmo: String = namespaceKeyItemAmmo.toString()                       // raw nbt key string for item ammo value
    internal val nbtKeyItemReloading: String = namespaceKeyItemReloading.toString()             // raw nbt key string for item is reloading (0 or 1)
    internal val nbtKeyItemReloadId: String = namespaceKeyItemReloadId.toString()               // raw nbt key string for item reload id
    internal val nbtKeyItemReloadTimestamp: String = namespaceKeyItemReloadTimestamp.toString() // raw nbt key string for item reload timestamp
    internal val nbtKeyItemBurstFireId: String = namespaceKeyItemBurstFireId.toString()         // raw nbt key for item burst firing id
    internal val nbtKeyItemAutoFireId: String = namespaceKeyItemAutoFireId.toString()           // raw nbt key for item auto firing id
    internal val nbtKeyItemCrawlToShootId: String = namespaceKeyItemCrawlToShootId.toString()   // raw nbt key for crawl to shoot id
    internal val nbtKeyItemThrowableId: String = namespaceKeyItemThrowableId.toString()         // raw nbt key for throwable id
        
    // ========================================================================
    // EXTERNAL PLUGIN APIs
    // ========================================================================
    internal var usingWorldGuard: Boolean = false
        private set
    
    // ========================================================================
    // STORAGE (allow public get)
    // ========================================================================
    public var config: Config = Config()
        private set

    // storage contains all guns, ammo, hats, etc. and helpers to map items to them
    public var storage: CustomItemStorage = CustomItemStorage.empty()
        private set

    // ========================================================================
    // INTERNAL PLUGIN SPECIFIC STORAGE
    // ========================================================================
    // custom hitboxes for armor stand custom models, maps EntityId => HitboxSize
    internal var customModelHitboxes: HashMap<UUID, HitboxSize> = HashMap()
        private set
    
    // map of vehicle entity UUIDs where passengers have extra armor
    // to combat damage (guns, melee, etc.)
    // map vehicle uuid => additional armor
    internal var vehiclePassengerArmor: HashMap<UUID, Double> = HashMap()
        private set
    
    // projectile systems for each world, map world uuid => ProjectileSystem
    public val projectileSystems: HashMap<UUID, ProjectileSystem> = HashMap(4) // initial capacity 4 worlds

    // map of players and aim down sights settings
    internal val dontUseAimDownSights: HashSet<UUID> = HashSet()
    
    // map of player => gun shoot delays
    internal val playerShootDelay: HashMap<UUID, ShootDelay> = HashMap()

    // map of players => recoil multiplier
    internal var playerRecoil: HashMap<UUID, Double> = HashMap()
    
    // immutable map of players => speed for sway multiplier (in blocks/tick)
    internal var playerSpeed: Map<UUID, Double> = HashMap() 

    // immutable map of players => previous location
    internal var playerPreviousLocation: Map<UUID, Location> = HashMap()
    
    // map of players => custom death messages
    // currently public so other plugins can use (e.g. vehicle library)
    public val deathEvents: HashMap<UUID, XcPlayerDeathEvent> = HashMap()

    // list of saved death events for statistics
    internal var playerDeathRecords: ArrayList<PlayerDeathRecord> = ArrayList()

    // counter for running player death record saving
    internal var playerDeathRecordSaveCounter: Int = 0
        private set
    
    // When gun item reloads, it gets assigned a unique id from this counter.
    // When reload is complete, gun item id is checked with this to make sure
    // player did not swap items during reload that plugin failed to catch.
    // Using int instead of atomic int since mineman is single threaded.
    internal var gunReloadIdCounter: Int = 0
        private set
    
    // Burst and auto fire ID counter. Used to detect if player is firing
    // the same weapon in a burst or auto fire sequence.
    // Using int instead of atomic int since mineman is single threaded.
    internal var burstFireIdCounter: Int = 0
        private set
    internal var autoFireIdCounter: Int = 0
        private set
    
    // ID counter for crawl to shoot request.
    internal var crawlToShootIdCounter: Int = 0
        private set
    
    // id counters for crawl refresh "load balancing"
    // hard coded for only 2 ticks
    internal var crawlRefreshTick0Count: Int = 0
        private set
    internal var crawlRefreshTick1Count: Int = 0
        private set
    
    // id counter for throwable items (when they are readied)
    internal var throwableIdCounter: Int = 0
        private set
    
    // id counter for explosion tags
    internal var explosionIdCounter: Int = 0
        private set

    // player death message storage, death event checks this for custom messages
    internal val playerDeathMessages: HashMap<UUID, String> = HashMap()

    // queue of player controls requests
    // Note: do reload before shoot. Shoot request when ammo <= 0 queue
    // a new reload request, which should be handled next tick
    internal var playerAimDownSightsRequests: ArrayList<PlayerAimDownSightsRequest> = ArrayList()
    internal var playerGunSelectRequests: ArrayList<PlayerGunSelectRequest> = ArrayList()
    internal var playerReloadRequests: ArrayList<PlayerGunReloadRequest> = ArrayList()
    internal var playerShootRequests: ArrayList<PlayerGunShootRequest> = ArrayList()
    internal var playerAutoFireRequests: ArrayList<PlayerAutoFireRequest> = ArrayList()
    internal var playerGunCleanupRequests: ArrayList<PlayerGunCleanupRequest> = ArrayList()
    internal var itemGunCleanupRequests: ArrayList<ItemGunCleanupRequest> = ArrayList()
    // burst firing queue: map entity uuid -> burst fire state
    internal var burstFiringPackets: HashMap<UUID, BurstFire> = HashMap()
    // automatic firing queue: map entity uuid -> automatic fire state
    internal var autoFiringPackets: HashMap<UUID, AutoFire> = HashMap()
    // crawling system queues
    internal var crawlRequestTasks: HashMap<UUID, CrawlToShootRequestTask> = HashMap()
    internal var crawlStartQueue: ArrayList<CrawlStart> = ArrayList()
    internal var crawlStopQueue: ArrayList<CrawlStop> = ArrayList()
    internal var crawling: HashMap<UUID, Crawling> = HashMap()
    internal var crawlToShootRequestQueue: ArrayList<CrawlToShootRequest> = ArrayList()
    internal val crawlingAndReadyToShoot: HashMap<UUID, Boolean> = HashMap() // map of players => is crawling and ready to shoot
    // throwable systems
    internal var readyThrowableRequests: ArrayList<ReadyThrowableRequest> = ArrayList()
    internal var throwThrowableRequests: ArrayList<ThrowThrowableRequest> = ArrayList()
    internal var droppedThrowables: ArrayList<DroppedThrowable> = ArrayList()
    internal var readyThrowables: HashMap<Int, ReadyThrowable> = HashMap()
    // per-world throwables queues, map world uuid => queue
    internal var expiredThrowables: HashMap<UUID, ArrayList<ExpiredThrowable>> = HashMap()
    public var thrownThrowables: HashMap<UUID, ArrayList<ThrownThrowable>> = HashMap() // public so other plugins can add throwables
        private set
    // landmine systems
    internal var landmineActivationRequests: ArrayList<LandmineActivationRequest> = ArrayList(0)
    internal var landmineFinishUseRequests: ArrayList<LandmineFinishUseRequest> = ArrayList(0)
    internal var landmineExplosions: HashMap<UUID, ArrayList<LandmineExplosionRequest>> = HashMap() // per world landmine explosion queues, world uuid => explosions
    // task finish queues
    internal val playerReloadTaskQueue: LinkedBlockingQueue<PlayerReloadTask> = LinkedBlockingQueue()
    internal val playerReloadCancelledTaskQueue: LinkedBlockingQueue<PlayerReloadCancelledTask> = LinkedBlockingQueue()
    internal val playerCrawlRequestFinishQueue: LinkedBlockingQueue<CrawlToShootRequestFinish> = LinkedBlockingQueue()
    internal val playerCrawlRequestCancelQueue: LinkedBlockingQueue<CrawlToShootRequestCancel> = LinkedBlockingQueue()

    // hats
    internal var wearHatRequests: ArrayList<PlayerWearHatRequest> = ArrayList()

    // player ids who combat logged >:(((
    // they need to be killed when they login
    internal var combatLoggers: HashSet<UUID> = HashSet()

    // ========================================================================
    // Async packet queues
    // ========================================================================
    // particle packet spawn queues
    internal var particleBulletTrailQueue: ArrayList<ParticleBulletTrail> = ArrayList()
    internal var particleBulletBlockImpactQueue: ArrayList<ParticleBulletBlockImpact> = ArrayList()
    internal var particleBulletHitboxImpactQueue: ArrayList<ParticleBulletHitboxImpact> = ArrayList()
    internal var particleExplosionQueue: ArrayList<ParticleExplosion> = ArrayList()

    // block cracking packet animation queue
    internal var blockCrackAnimationQueue: ArrayList<BlockCrackAnimation> = ArrayList()

    // gun ammo message packets
    internal var gunAmmoInfoMessageQueue: ArrayList<AmmoInfoMessagePacket> = ArrayList()

    // sounds queue, public so other plugins can insert
    public var soundQueue: ArrayList<SoundPacket> = ArrayList()
        private set
        
    // recoil packets, public so other plugins can insert
    public var recoilQueue: ArrayList<RecoilPacket> = ArrayList()
        private set

    // ========================================================================
    // Debug/benchmarking
    // ========================================================================
    // For debugging timings
    internal var doDebugTimings: Boolean = true
    internal val debugTimings: DebugTimings = DebugTimings(200) // 200 ticks of timings history
    
    // benchmarking projectiles
    internal var doBenchmark: Boolean = false
    internal var benchmarkProjectileCount: Int = 0
    internal var benchmarkPlayer: Player? = null

    // Built-in guns for debug/benchmark
    internal val gunBenchmarking: Gun = Gun()
    internal var gunDebug: Gun = Gun()

    // ========================================================================
    // TASKS AND BACKGROUND ASYNC TASKS
    // ========================================================================
    // common threadpool for systems. mainly used for parallelizing the projectile system
    private var threadpool: ExecutorService = Executors.newSingleThreadExecutor() // single thread until config loaded
    private var threadpoolSize: Int = 1

    // main engine task
    private var engineTask: BukkitTask? = null
    private var calculatePlayerSpeedTask: TaskCalculatePlayerSpeed = TaskCalculatePlayerSpeed(this)
    private var antiCombatLogTask: TaskAntiCombatLog = TaskAntiCombatLog(this, this.config.antiCombatLogTimeout)

    /**
     * Setter for `usingWorldGuard` flag, set when plugin enabled and
     * WorldGuard is detected.
     */
    internal fun usingWorldGuard(state: Boolean) {
        usingWorldGuard = state
    }

    /**
     * Remove hooks to plugins and external APIs
     */
    internal fun onDisable() {
        cleanup()
    }

    /**
     * Cleanup resources before reload or disabling plugin. 
     */
    internal fun cleanup() {
        // cleanup crawl fake entity/packets
        for ( (_playerId, crawlState) in crawling ) {
            try {
                crawlState.cleanup()
                crawlState.player.removePotionEffect(PotionEffectType.JUMP)
                crawlState.player.setWalkSpeed(0.2f) // default speed
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // flush death message and stats tracking
        try {
            TaskSavePlayerDeathRecords(playerDeathRecords, config.playerDeathLogSaveDir).run()
        } catch ( e: Exception ) {
            e.printStackTrace()
        }

        // if these are not running, these will cause IllegalStateException...ignore!
        try {
            calculatePlayerSpeedTask.cancel()
        } catch ( e: Exception ) {
            logger.warning("Ignore error from cancelling calculatePlayerSpeedTask: ${e.message}")
        }

        try {
            antiCombatLogTask.cancel()
        } catch ( e: Exception ) {
            logger.warning("Ignore error from cancelling antiCombatLogTask: ${e.message}")
        }

        // clear counters
        playerDeathRecordSaveCounter = 0
        gunReloadIdCounter = 0
        burstFireIdCounter = 0
        autoFireIdCounter = 0
        crawlToShootIdCounter = 0
        crawlRefreshTick0Count = 0
        crawlRefreshTick1Count = 0
        throwableIdCounter = 0
        explosionIdCounter = 0

        // clear all queues/maps
        customModelHitboxes = HashMap()
        vehiclePassengerArmor = HashMap()
        
        projectileSystems.clear()

        playerShootDelay.clear()
        playerRecoil = HashMap()
        playerSpeed = HashMap()
        playerPreviousLocation = HashMap()
        
        deathEvents.clear()
        playerDeathMessages.clear()
        playerDeathRecords = ArrayList()

        playerAimDownSightsRequests = ArrayList()
        playerGunSelectRequests = ArrayList()
        playerReloadRequests = ArrayList()
        playerShootRequests = ArrayList()
        playerAutoFireRequests = ArrayList()
        playerGunCleanupRequests = ArrayList()
        itemGunCleanupRequests = ArrayList()
        burstFiringPackets = HashMap()
        autoFiringPackets = HashMap()

        crawlRequestTasks = HashMap()
        crawlStartQueue = ArrayList()
        crawlStopQueue = ArrayList()
        crawling = HashMap()
        crawlToShootRequestQueue = ArrayList()
        crawlingAndReadyToShoot.clear()

        readyThrowableRequests = ArrayList()
        throwThrowableRequests = ArrayList()
        droppedThrowables = ArrayList()
        readyThrowables = HashMap()

        expiredThrowables.clear()
        thrownThrowables.clear()

        landmineActivationRequests = ArrayList(0)
        landmineFinishUseRequests = ArrayList(0)
        landmineExplosions = HashMap()
        playerReloadTaskQueue.clear()
        playerReloadCancelledTaskQueue.clear()
        playerCrawlRequestFinishQueue.clear()
        playerCrawlRequestCancelQueue.clear()

        wearHatRequests = ArrayList()

        particleBulletTrailQueue = ArrayList()
        particleBulletBlockImpactQueue = ArrayList()
        particleBulletHitboxImpactQueue = ArrayList()
        particleExplosionQueue = ArrayList()
        blockCrackAnimationQueue = ArrayList()
        gunAmmoInfoMessageQueue = ArrayList()
        soundQueue = ArrayList()
        recoilQueue = ArrayList()

        combatLoggers = HashSet()
    }

    /**
     * Inserts world specific queues for projectiles/weapons systems
     */
    internal fun initializeSystems() {
        // create per-world systems
        Bukkit.getWorlds().forEach { world ->
            val worldId = world.getUID()
            projectileSystems.put(worldId, ProjectileSystem(world, this.threadpool, this.threadpoolSize))
            thrownThrowables.put(worldId, ArrayList())
            expiredThrowables.put(worldId, ArrayList())
            landmineExplosions.put(worldId, ArrayList())
        }
        
        // start player movement speed background async task
        val newCalculatePlayerSpeedTask = TaskCalculatePlayerSpeed(this)
        newCalculatePlayerSpeedTask.runTaskTimerAsynchronously(plugin, 0L, 0L)
        calculatePlayerSpeedTask = newCalculatePlayerSpeedTask

        // start anti-combat log background async task
        if ( config.antiCombatLogEnabled ) {
            val newAntiCombatLogTask = TaskAntiCombatLog(this, config.antiCombatLogTimeout)
            newAntiCombatLogTask.runTaskTimerAsynchronously(plugin, 0L, 0L)
            antiCombatLogTask = newAntiCombatLogTask
        }
    }

    /**
     * Re-initialize storages and re-load config.
     * TODO: async
     */
    internal fun reload(async: Boolean = false) {
        val timeStart = System.currentTimeMillis()
        this.cleanup()
        
        // reload main plugin config
        val pathConfigToml = Paths.get(plugin.getDataFolder().getPath(), "config.toml")
        val config = if ( Files.exists(pathConfigToml) ) {
            Config.fromToml(pathConfigToml, plugin.getDataFolder().getPath(), logger)
        } else {
            logger.info("Creating default config.toml")
            plugin.saveResource("config.toml", false)
            Config()
        }

        // load from toml config files

        val filesGuns = listDirFiles(config.pathFilesGun)
        val gunsLoaded: List<Gun> = filesGuns
            .map { file -> Gun.fromToml(config.pathFilesGun.resolve(file), logger) }
            .filterNotNull()

        val filesAmmo = listDirFiles(config.pathFilesAmmo)
        val ammoLoaded: List<Ammo> = filesAmmo
            .map { file -> Ammo.fromToml(config.pathFilesAmmo.resolve(file), logger) }
            .filterNotNull()

        val filesMelee = listDirFiles(config.pathFilesMelee)
        val meleeLoaded: List<MeleeWeapon> = filesMelee
            .map { file -> MeleeWeapon.fromToml(config.pathFilesMelee.resolve(file), logger) }
            .filterNotNull()
        
        val filesThrowable = listDirFiles(config.pathFilesThrowable)
        val throwableLoaded: List<ThrowableItem> = filesThrowable
            .map { file -> ThrowableItem.fromToml(config.pathFilesThrowable.resolve(file), logger) }
            .filterNotNull()
        
        val filesHats = listDirFiles(config.pathFilesArmor)
        val hatsLoaded: List<Hat> = filesHats
            .map { file -> Hat.listFromToml(config.pathFilesArmor.resolve(file), logger) }
            .filterNotNull()
            .flatten() // this flattens a List<List<Hat>> -> List<Hat>
        
        val filesLandmine = listDirFiles(config.pathFilesLandmine)
        val landminesLoaded: List<Landmine> = filesLandmine
            .map { file -> Landmine.fromToml(config.pathFilesLandmine.resolve(file), logger) }
            .filterNotNull()
        
        // MAP GUN CUSTOM MODEL ID => GUN
        val guns: Array<Gun?> = Array(config.maxGunTypes, { _ -> null })
        val validGunIds = mutableSetOf<Int>()
        loadgun@ for ( item in gunsLoaded ) {
            // map all gun custom model ids => gun
            val gunModels = arrayOf(
                item.itemModelDefault,
                item.itemModelEmpty,
                item.itemModelReload,
                item.itemModelAimDownSights,
            )

            // validate required id (itemModelDefault) within [0, MAX_ID)
            val gunId = item.itemModelDefault
            if ( gunId < 0 || gunId >= config.maxGunTypes ) {
                logger.severe("Gun ${item.itemName} has invalid custom model id ${gunId}, must be within [0, ${config.maxGunTypes})")
                continue@loadgun
            }

            // validate all custom model ids are < MAX_ID
            // (negative values allowed for optional models, indicates no model)
            for ( modelId in gunModels ) {
                if ( modelId >= config.maxGunTypes ) {
                    logger.severe("Gun ${item.itemName} has invalid custom model id ${modelId}, must be < ${config.maxGunTypes}")
                    continue@loadgun
                }
            }

            // model ids are valid, now map each id => object
            for ( modelId in gunModels ) {
                if ( modelId >= 0 ) { // ignore negative values
                    guns[modelId]?.let { old -> logger.warning("Gun ${item.itemName} (${modelId}) overwrites gun ${old.itemName}") }
                    guns[modelId] = item
                }
            }

            // add default gun model id to validGunIds (this overwrites duplicates)
            validGunIds.add(item.itemModelDefault)
        }

        // MAP MELEE CUSTOM MODEL ID => MELEE WEAPON
        val melee: Array<MeleeWeapon?> = Array(config.maxMeleeTypes, { _ -> null })
        val validMeleeIds = mutableSetOf<Int>()
        loadmelee@ for ( item in meleeLoaded ) {
            // map all custom model ids => item
            val models = arrayOf(
                item.itemModelDefault,
            )

            // validate required id (itemModelDefault) within [0, MAX_ID)
            val meleeId = item.itemModelDefault
            if ( meleeId < 0 || meleeId >= config.maxMeleeTypes ) {
                logger.severe("Melee weapon ${item.itemName} has invalid custom model id ${meleeId}, must be within [0, ${config.maxMeleeTypes})")
                continue@loadmelee
            }

            // validate all custom model ids are < MAX_ID
            // (negative values allowed for optional models, indicates no model)
            for ( modelId in models ) {
                if ( modelId >= config.maxMeleeTypes ) {
                    logger.severe("Melee weapon ${item.itemName} has invalid custom model id ${modelId}, must be < ${config.maxMeleeTypes}")
                    continue@loadmelee
                }
            }

            // model ids are valid, now map each id => object
            for ( modelId in models ) {
                melee[modelId]?.let { old -> logger.warning("Melee weapon ${item.itemName} (${modelId}) overwrites ${old.itemName}") }
                melee[modelId] = item
            }
            
            validMeleeIds.add(item.itemModelDefault)
        }

        // map throwable ids => throwable weapon
        val throwable: Array<ThrowableItem?> = Array(config.maxThrowableTypes, { _ -> null })
        val validThrowableIds = mutableSetOf<Int>()
        loadthrow@ for ( item in throwableLoaded ) {
            // map all custom model ids => item
            val models = arrayOf(
                item.itemModelDefault,
                item.itemModelReady,
            )

            // validate required id (itemModelDefault) within [0, MAX_ID)
            val throwableId = item.itemModelDefault
            if ( throwableId < 0 || throwableId >= config.maxThrowableTypes ) {
                logger.severe("Melee weapon ${item.itemName} has invalid custom model id ${throwableId}, must be within [0, ${config.maxThrowableTypes})")
                continue@loadthrow
            }
            
            // validate all custom model ids are < MAX_ID
            // (negative values allowed for optional models, indicates no model)
            for ( modelId in models ) {
                if ( modelId < 0 || modelId >= config.maxThrowableTypes ) {
                    logger.severe("Throwable ${item.itemName} has invalid custom model id ${modelId}, must be within [0, ${config.maxThrowableTypes})")
                    continue@loadthrow
                }
            }

            // model ids are valid, now map each id => object
            for ( modelId in models ) {
                throwable[modelId]?.let { old -> logger.warning("Throwable ${item.itemName} (${modelId}) overwrites ${old.itemName}") }
                throwable[modelId] = item
            }

            validThrowableIds.add(item.itemModelDefault)
        }

        // map ammo id => ammo
        val ammo: Array<Ammo?> = Array(config.maxAmmoTypes, { _ -> null })
        val validAmmoIds = mutableSetOf<Int>()
        for ( item in ammoLoaded ) {
            val modelId = item.id // TODO: replace with just custom model id

            // validate ammo id (custom model id) within range [0, MAX_ID)
            if ( modelId < 0 || modelId >= config.maxAmmoTypes ) {
                logger.severe("Ammo ${item.itemName} has invalid custom model id ${modelId}, must be within [0, ${config.maxAmmoTypes})")
                continue
            }

            // model id valid, map id => object
            ammo[modelId]?.let { old -> logger.warning("Ammo ${item.itemName} (${modelId}) overwrites ${old.itemName}") }
            ammo[modelId] = item
            validAmmoIds.add(item.id)
        }
        
        // map hat id => hat
        val hats: Array<Hat?> = Array(config.maxHatTypes, { _ -> null })
        val validHatIds = mutableSetOf<Int>()
        for ( item in hatsLoaded ) {
            val modelId = item.itemModel

            // validate custom model ids are in range [0, MAX_ID)
            if ( modelId < 0 || modelId >= config.maxHatTypes ) {
                logger.severe("Hat ${item.itemName} has invalid custom model id ${modelId}, must be within [0, ${config.maxHatTypes})")
                continue
            }

            // model ids are valid, now map each id => object
            hats[modelId]?.let { old -> logger.warning("Hat ${item.itemName} (${modelId}) overwrites ${old.itemName}") }
            hats[modelId] = item
            validHatIds.add(item.id)
        }

        // add landmines to enum map
        val landmines: EnumMap<Material, Landmine> = EnumMap(Material::class.java)
        for ( l in landminesLoaded ) {
            landmines[l.material]?.let { old -> logger.warning("Landmine ${l.itemName} (${l.material}) overwrites ${old.itemName}") }
            landmines[l.material] = l
        }
        
        // set config and storage
        this.config = config
        this.doDebugTimings = config.defaultDoDebugTimings
        this.storage = CustomItemStorage(
            ammo = ammo,
            ammoIds = validAmmoIds.toIntArray().sortedArray(),
            gun = guns,
            gunIds = validGunIds.toIntArray().sortedArray(),
            hat = hats,
            hatIds = validHatIds.toIntArray().sortedArray(),
            melee = melee,
            meleeIds = validMeleeIds.toIntArray().sortedArray(),
            throwable = throwable,
            throwableIds = validThrowableIds.toIntArray().sortedArray(),
            landmine = landmines,
        )

        // create threadpool
        val numThreadsHint = config.numProjectileThreads
        val numThreads = if ( numThreadsHint < 0 ) {
            // use number of cores
            Runtime.getRuntime().availableProcessors()
        } else if ( numThreadsHint > 0 ) {
            // try to use hint
            min(Runtime.getRuntime().availableProcessors(), numThreadsHint)
        } else {
            logger.warning("Invalid number of threads hint ${numThreadsHint}, using 1 thread")
            1
        }

        if ( numThreads == 1 ) {
            this.threadpool = Executors.newSingleThreadExecutor() 
            this.threadpoolSize = 1
        } else {
            this.threadpool = Executors.newFixedThreadPool(numThreads)
            this.threadpoolSize = numThreads
        }

        // create projectile and throwable systems for each world
        this.initializeSystems()

        // start new engine runnable
        val timeEnd = System.currentTimeMillis()
        val timeLoad = timeEnd - timeStart
        logger.info("Reloaded in ${timeLoad}ms")
        logger.info("Using ${numThreads} threads for projectiles")
        logger.info("- Guns: ${validGunIds.size}")
        logger.info("- Ammo: ${validAmmoIds.size}")
        logger.info("- Melee: ${validMeleeIds.size}")
        logger.info("- Throwable: ${validThrowableIds.size}")
        logger.info("- Hats: ${validHatIds.size}")
        logger.info("- Landmines: ${landmines.size}")
    }

    /**
     * Function to run after finishing an async scheduled part of a reload.
     */
    internal fun reloadFinishAsync() {
        cleanup()
        // TODO
    }

    /**
     * Starts running engine task
     */
    internal fun start() {
        if ( engineTask == null ) {
            engineTask = Bukkit.getScheduler().runTaskTimer(plugin, object: Runnable {
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
                        update()
                        errorAccumulator = 0 // clear on successful update
                    } catch ( e: Exception ) {
                        logger.severe("Engine update failed:")
                        e.printStackTrace()
                        
                        // accumulate errors. if we reach past threshold,
                        // reset engine to clean slate
                        errorAccumulator += 1
                        if ( errorAccumulator >= maxErrorsBeforeCleanup ) {
                            logger.severe("Engine reached error threshold...resetting to clean slate")
                            cleanup()
                            initializeSystems()
                            errorAccumulator = 0
                        }
                    }
                }
            }, 0, 0)

            logger.info("Starting engine")
        }
        else {
            logger.warning("Engine already running")
        }
    }

    /**
     * Stop running engine task
     */
    internal fun stop() {
        val task = engineTask
        if ( task != null ) {
            task.cancel()
            engineTask = null
            logger.info("Stopping engine")
        } else {
            logger.warning("Engine not running")
        }
    }

    /**
     * Get current reload id counter for reload task.
     */
    internal fun newReloadId(): Int {
        val id = gunReloadIdCounter
        gunReloadIdCounter = max(0, id + 1)
        return id
    }

    /**
     * Get current burst id counter for burst firing.
     * Used to detect if same gun is being fired in burst mode.
     */
    internal fun newBurstFireId(): Int {
        val id = burstFireIdCounter
        burstFireIdCounter = max(0, id + 1)
        return id
    }

    /**
     * Get current auto fire id counter for auto firing
     * Used to detect if same gun is being fired in automatic mode.
     */
    internal fun newAutoFireId(): Int {
        val id = autoFireIdCounter
        autoFireIdCounter = max(0, id + 1)
        return id
    }

    /**
     * Get current crawl to shoot id counter for crawling.
     */
    internal fun newCrawlToShootId(): Int {
        val id = crawlToShootIdCounter
        crawlToShootIdCounter = max(0, id + 1)
        return id
    }

    /**
     * Assign a crawl refresh id.
     * E.g. for two allowed tick assignments,
     * each refresh system tick counts 0..1..0..1..0..
     * If the `crawling.id == refresh tick id`, then refresh
     * will run. Assign each crawling id to the refresh tick
     * that currently has the LEAST number of crawling ids.
     */
    internal fun newCrawlRefreshId(): Int {
        if ( crawlRefreshTick0Count < crawlRefreshTick1Count ) {
            return 0
        } else {
            return 1
        }
    }

    /**
     * Frees crawl refresh id.
     * Decrement the index for this tick id.
     */
    internal fun freeCrawlRefreshId(index: Int) {
        if ( index == 0 ) {
            crawlRefreshTick0Count = max(0, crawlRefreshTick0Count - 1)
        } else {
            crawlRefreshTick1Count = max(0, crawlRefreshTick1Count - 1)
        }
    }

    /**
     * Create new throwable id from global counter.
     * 
     * Note: throwable ids are used inside the map
     *      readyThrowable[throwId] -> ReadyThrowable
     * It's technically possible for this to overflow and overwrite.
     * But extremely unlikely, since throwables have a lifetime and
     * should be removed from this map before 
     * Integer.MAX_VALUE new throwables are created to overflow and
     * overwrite the key.
     */
    internal fun newThrowableId(): Int {
        val id = throwableIdCounter
        throwableIdCounter = max(0, id + 1)
        return id
    }

    /**
     * Create new explosion id from global counter (explosion ids used to
     * tag hitboxes so that hitboxes do not get damaged by same explosion
     * twice). See `createExplosion` for usage. These should be reset
     * on each update tick using `resetExplosionIds`.
     */
    internal fun nextExplosionId(): Int {
        val id = explosionIdCounter
        explosionIdCounter += 1
        return id
    }

    /**
     * Create new explosion id from global counter (explosion ids used to
     * tag hitboxes so that hitboxes do not get damaged by same explosion
     * twice). See `createExplosion` for usage. These should be reset
     * on each update tick using `resetExplosionIds`.
     */
    internal fun resetExplosionIdCounter() {
        explosionIdCounter = 0
    }

    /**
     * Protection check if location allows player pvp damage.
     * In future, this should add other hooked plugin checks.
     */
    internal fun canPvpAt(loc: Location): Boolean {
        if ( usingWorldGuard ) {
            return WorldGuard.canPvpAt(loc)
        }

        return true
    }

    /**
     * Protection check if location allows explosions.
     * This includes all explosion behavior (custom damage, particles,
     * and block damage).
     * In future, this should add other hooked plugin checks.
     */
    internal fun canExplodeAt(loc: Location): Boolean {
        if ( usingWorldGuard ) {
            return WorldGuard.canExplodeAt(loc)
        }

        return true
    }

    /**
     * Protection check if location allows fire.
     */
    internal fun canCreateFireAt(loc: Location): Boolean {
        if ( usingWorldGuard ) {
            return WorldGuard.canCreateFireAt(loc)
        }

        return true
    }

    /**
     * Helper for marking that player entered combat,
     * if anti-combat logging enabled, sends message to combat logging
     * thread.
     */
    fun addPlayerToCombatLogging(player: Player) {
        if ( this.config.antiCombatLogEnabled ) {
            this.antiCombatLogTask.playerTookDamage.add(player)
        }
    }

    /**
     * Helper for marking that player entered combat,
     * if anti-combat logging enabled, sends message to combat logging
     * thread.
     */
    fun removeDeadPlayerFromCombatLogging(player: Player) {
        if ( this.config.antiCombatLogEnabled ) {
            this.antiCombatLogTask.playerDied.add(player)
        }
    }

    /**
     * Wrapper for System.nanoTime(), only runs call
     * if debug timings are on.
     */
    internal fun debugNanoTime(): Long {
        if ( doDebugTimings ) {
            return System.nanoTime()
        } else {
            return 0L
        }
    }

    /**
     * Creates or stops benchmarking task.
     */
    public fun setBenchmark(state: Boolean, numProjectiles: Int = 100, player: Player? = null) {
        if ( state == false ) {
            doBenchmark = false
        }
        else { // begin benchmark task, if player valid
            if ( numProjectiles < 1 || player == null ) {
                logger.warning("Invalid benchmark parameters: numProjectiles=${numProjectiles}, player=${player}")
                doBenchmark = false
                return
            }

            doBenchmark = true
            benchmarkProjectileCount = numProjectiles
            benchmarkPlayer = player
        }
    }

    /**
     * Run benchmark task. This should run before projectile system update.
     */
    public fun runBenchmarkProjectiles() {
        if ( doBenchmark == false ) return

        val player = benchmarkPlayer
        if ( player == null ) return
        
        val world = player.world
        val projectileSystem = projectileSystems[world.getUID()]
        if ( projectileSystem == null ) return

        val currNumProjectiles = projectileSystem.size()
        val numToCreate = benchmarkProjectileCount - currNumProjectiles
        if ( numToCreate <= 0 ) return

        val loc = player.location
        val eyeHeight = player.eyeHeight
        val shootPosition = loc.clone().add(0.0, eyeHeight, 0.0)

        val gun = gunDebug
        
        val random = ThreadLocalRandom.current()

        val projectiles = ArrayList<Projectile>(numToCreate)
        for ( _i in 0 until numToCreate ) {

            // randomize shoot direction
            val shootDirX = random.nextDouble(-1.0, 1.0)
            val shootDirZ = random.nextDouble(-1.0, 1.0)
            val shootDirY = random.nextDouble(-0.1, 0.5)

            projectiles.add(Projectile(
                gun = gun,
                source = player,
                x = shootPosition.x.toFloat(),
                y = shootPosition.y.toFloat(),
                z = shootPosition.z.toFloat(),
                dirX = shootDirX.toFloat(),
                dirY = shootDirY.toFloat(),
                dirZ = shootDirZ.toFloat(),
                gravity = gun.projectileGravity,
                speed = gun.projectileVelocity,
                maxLifetime = gun.projectileLifetime,
                maxDistance = gun.projectileMaxDistance,
                // gravity = 0.0f,
                // speed = 1.0f,
                // maxLifetime = 100,
                // maxDistance = 200.0f,
            ))
        }
        projectileSystem.addProjectiles(projectiles)
    }

    /**
     * Map an uuid to a custom hitbox size. UUID flexible, can be
     * entity unique id, or uuid managed by other systems.
     */
    public fun addHitbox(uuid: UUID, hitbox: HitboxSize) {
        customModelHitboxes[uuid] = hitbox
    }

    /**
     * Remove custom hitbox from uuid if it exists.
     */
    public fun removeHitbox(uuid: UUID) {
        customModelHitboxes.remove(uuid)
    }

    /**
     * Set vehicle passenger armor mapping.
     */
    public fun addVehiclePassengerArmor(uuid: UUID, armor: Double) {
        vehiclePassengerArmor[uuid] = armor
    }

    /**
     * Remove vehicle passenger armor mapping.
     */
    public fun removeVehiclePassengerArmor(uuid: UUID) {
        vehiclePassengerArmor.remove(uuid)
    }

    /**
     * Adds projectile to projectile system if it exists.
     */
    public fun addProjectile(world: World, projectile: Projectile) {
        projectileSystems[world.getUID()]?.let { sys ->
            sys.addProjectile(projectile)
        }
    }

    /**
     * Add player hitbox debug request
     */
    public fun debugHitboxRequest(player: Player, range: Int) {
        val world = player.world
        val loc = player.location

        val cxmin = (floor(loc.x).toInt() shr 4) - range
        val cxmax = (ceil(loc.x).toInt() shr 4) + range
        val czmin = (floor(loc.z).toInt() shr 4) - range
        val czmax = (ceil(loc.z).toInt() shr 4) + range
        
        for ( cx in cxmin..cxmax ) {
            for ( cz in czmin..czmax ) {
                if ( world.isChunkLoaded(cx, cz) ) {
                    val chunk = world.getChunkAt(cx, cz)

                    for ( entity in chunk.getEntities() ) {
                        // special handling for custom model hitboxes
                        if ( entity.type == EntityType.ARMOR_STAND ) {
                            val hitboxSize = customModelHitboxes.get(entity.getUniqueId())
                            if ( hitboxSize != null ) {
                                Hitbox.from(entity, hitboxSize).visualize(world, Particle.VILLAGER_HAPPY)
                                continue
                            }
                        }
        
                        // regular entities
                        if ( config.entityTargetable[entity.type] ) {
                            Hitbox.from(entity, config.entityHitboxSizes[entity.type]).visualize(world, Particle.VILLAGER_HAPPY)
                        }
                    }
                }
            }
        }
    }

    /**
     * Set a player's aim down sights setting.
     */
    public fun setAimDownSights(player: Player, use: Boolean) {
        if ( use ) {
            dontUseAimDownSights.remove(player.getUniqueId())
        } else {
            dontUseAimDownSights.add(player.getUniqueId())
        }
    }
    
    /**
     * This adds an aim down sights model to the player's offhand,
     * for aim down sights visual.
     * 
     * TODO: USE SET_SLOT PACKET INSTEAD OF A REAL ITEM!!!
     */
    internal fun createAimDownSightsOffhandModel(gun: Gun, player: Player) {
        // println("createAimDownSightsOffhandModel")
        val equipment = player.getInventory()
        
        // drop current offhand item
        val itemOffhand = equipment.getItemInOffHand()
        if ( itemOffhand != null && itemOffhand.type != Material.AIR ) {
            // drop offhand item in world and remove item in offhand
            player.getWorld().dropItem(player.getLocation(), itemOffhand)
            equipment.setItemInOffHand(null)
            
            // DEPRECATED
            // if this is an existing aim down sights model, ignore (ADS models can be glitchy)
            // val itemMeta = itemOffhand.getItemMeta()
            // if ( itemOffhand.type != config.materialAimDownSights || ( itemMeta.hasCustomModelData() && itemMeta.getCustomModelData() >= MAX_GUN_CUSTOM_MODEL_ID ) ) {
            //     player.getWorld().dropItem(player.getLocation(), itemOffhand)
            // }
        }

        // create new offhand item
        gun.aimDownSightsModel.create(player)
    }

    /**
     * Removes any aim down sights custom model in player's offhand.
     */
    internal fun removeAimDownSightsOffhandModel(player: Player) {
        // println("removeAimDownSightsOffhandModel")
        val equipment = player.getInventory()
        
        // remove offhand item if its an aim down sights model
        val itemOffhand = equipment.getItemInOffHand()
        if ( itemOffhand == null || itemOffhand.type == Material.AIR ) { // no real item here, send packet removing fake ads item
            AimDownSightsModel.destroy(player)
        }
    }

    /**
     * Create aim down sights model using a real item.
     * Deprecated because using pure packets to make a fake item.
     * No real item is created in offhand, so this is unnecessary.
     */
    @Deprecated(message = "Use PacketPlayOutSetSlot instead of a real ItemStack")
    private fun createAimDownSightsOffhandModelItemStack(gun: Gun): ItemStack {
        val item = ItemStack(config.materialAimDownSights, 1)
        val itemMeta = item.getItemMeta()

        itemMeta.setDisplayName("Aim down sights")
        itemMeta.setCustomModelData(gun.itemModelAimDownSights)

        item.setItemMeta(itemMeta)

        return item
    }
    

    /**
     * Remove aim down sights item stack in offhand.
     * Deprecated because using pure packets, no real item is created.
     */
    @Deprecated(message = "Use PacketPlayOutSetSlot instead of a real ItemStack")
    private fun removeAimDownSightsOffhandModelItemStack(player: Player) {
        // println("removeAimDownSightsOffhandModelItemStack")
        val equipment = player.getInventory()
        val itemOffhand = equipment.getItemInOffHand()
        if ( itemOffhand != null && itemOffhand.type == config.materialAimDownSights ) {
            val itemMeta = itemOffhand.getItemMeta()
            if ( itemMeta.hasCustomModelData() && itemMeta.getCustomModelData() < config.maxGunTypes ) {
                equipment.setItemInOffHand(ItemStack(Material.AIR, 1))
            }
        }
    }


    /**
     * Return true if item stack is an aim down sights model.
     */
    public fun isAimDownSightsModel(item: ItemStack): Boolean {
        if ( item.getType() == config.materialAimDownSights ) {
            val itemMeta = item.getItemMeta()
            if ( itemMeta.hasCustomModelData() ) {
                return itemMeta.getCustomModelData() < config.maxGunTypes
            }
        }

        return false
    }

    /**
     * Return true if player is crawling.
     */
    public fun isCrawling(player: Player): Boolean {
        return crawling.contains(player.getUniqueId())
    }

    /**
     * Main engine update, runs on each tick.
     * 
     * Not happy with blackbox update functions, cant see which systems
     * mutate what storage state...but o well :^(
     * 
     * NOTE: the "systems" below are extension functions on the XC
     * instance, each system is really doing `systemFn(this)`.
     * Each system has full mutation control over this XC instance.
     */
    internal fun update() {
        val tUpdateStart = debugNanoTime() // timing probe

        debugTimings.tick()
        runBenchmarkProjectiles() // debugging

        val tShootSystem = debugNanoTime() // timing probe

        // timestamp for beginning update tick
        val timestamp = System.currentTimeMillis()

        // clear explosion tag id counter
        resetExplosionIdCounter()

        // kill combat loggers
        if ( config.antiCombatLogEnabled ) {
            killCombatLoggerSystem(this.antiCombatLogTask.detectedPlayerCombatLogged)
        }

        // wear hats
        wearHatSystem(wearHatRequests)

        // DEPRECATED: run pipelined player movement check, for sway modifier
        // val (playerNewSpeed, playerNewLocation) = playerSpeedSystem(playerSpeed, playerPreviousLocation)
        // playerSpeed = playerNewSpeed
        // playerPreviousLocation = playerNewLocation

        // crawl systems
        startCrawlSystem(crawlStartQueue, crawling)
        stopCrawlSystem(crawlStopQueue, crawling, crawlRequestTasks, crawlingAndReadyToShoot)
        crawlRefreshSystem(crawling, crawlStopQueue)
        // finish crawl to shoot requests
        val crawlRequestFinishTasks = ArrayList<CrawlToShootRequestFinish>()
        val crawlRequestCancelTasks = ArrayList<CrawlToShootRequestCancel>()
        playerCrawlRequestFinishQueue.drainTo(crawlRequestFinishTasks)
        playerCrawlRequestCancelQueue.drainTo(crawlRequestCancelTasks)
        finishCrawlToShootRequestSystem(crawlRequestFinishTasks, crawling, crawlRequestTasks, crawlingAndReadyToShoot)
        cancelCrawlToShootRequestSystem(crawlRequestCancelTasks, crawling, crawlRequestTasks, crawlStopQueue)

        // run gun controls systems (these emit new queues for next tick)
        gunAimDownSightsSystem(playerAimDownSightsRequests)
        playerGunCleanupSystem(playerGunCleanupRequests)
        gunItemCleanupSystem(itemGunCleanupRequests)
        gunSelectSystem(
            playerGunSelectRequests,
            autoFiringPackets,
            playerShootDelay,
            timestamp,
        )
        gunPlayerReloadSystem(
            playerReloadRequests,
            autoFiringPackets,
            burstFiringPackets,
            playerReloadTaskQueue,
            playerReloadCancelledTaskQueue,
            timestamp,
        ) 
        autoFireRequestSystem( // do auto fire request before single/burst fire
            playerAutoFireRequests,
            autoFiringPackets,
            playerShootDelay,
            crawlingAndReadyToShoot,
            crawlToShootRequestQueue,
            timestamp,
        ) 
        gunPlayerShootSystem(
            playerShootRequests,
            autoFiringPackets,
            burstFiringPackets,
            playerShootDelay,
            playerReloadRequests,
            crawlingAndReadyToShoot,
            crawlToShootRequestQueue,
            projectileSystems,
            timestamp,
        )
        burstFireSystem(
            burstFiringPackets,
            playerShootDelay,
            playerReloadRequests,
            projectileSystems,
            timestamp,
        )
        autoFireSystem(
            autoFiringPackets,
            playerReloadRequests,
            projectileSystems,
        )
        recoilRecoverySystem(
            playerRecoil,
            autoFiringPackets,
            burstFiringPackets,
        )
        requestCrawlToShootSystem(
            crawlToShootRequestQueue,
            crawlStartQueue,
            crawlRequestTasks,
            playerCrawlRequestFinishQueue,
            playerCrawlRequestCancelQueue,
            timestamp,
        )

        // ready and throw throwable systems
        // (tick for thrown throwable objects done with projectiles
        // because hitboxes needed)
        requestReadyThrowableSystem(readyThrowableRequests, readyThrowables)
        requestThrowThrowableSystem(throwThrowableRequests, readyThrowables, thrownThrowables)
        droppedThrowableSystem(droppedThrowables, readyThrowables, thrownThrowables)
        tickReadyThrowableSystem(readyThrowables, expiredThrowables)

        // landmine systems
        // (explosion handling done after hitboxes created in projectiles update block)
        landmineFinishUseSystem(landmineFinishUseRequests) // note: finishes PREVIOUS tick's requests (a 1-tick delayed system)
        landmineActivationSystem(landmineActivationRequests, landmineFinishUseRequests, landmineExplosions)
        
        // finish gun reloading tasks
        val tReloadSystem = debugNanoTime() // timing probe
        val gunReloadTasks = ArrayList<PlayerReloadTask>()
        val gunReloadCancelledTasks = ArrayList<PlayerReloadCancelledTask>()
        playerReloadTaskQueue.drainTo(gunReloadTasks)
        playerReloadCancelledTaskQueue.drainTo(gunReloadCancelledTasks)
        doGunReload(gunReloadTasks)
        doGunReloadCancelled(gunReloadCancelledTasks)

        val tProjectileSystem = debugNanoTime() // timing probe

        // update projectile systems for each world
        for ( (worldId, projSys) in this.projectileSystems ) {
            // first gather visited chunks for throwable items
            // (for potential explosion/entity hit calculations)
            val visitedChunks = thrownThrowables[worldId]?.let { throwables -> getThrownThrowableVisitedChunksSystem(throwables) } ?: HashSet()
            landmineExplosions[worldId]?.let { explosions -> getLandmineExplosionVisitedChunksSystem(explosions, visitedChunks) }

            // run projectile system
            val (hitboxes, hitBlocksQueue, hitEntitiesQueue) = projSys.update(this, visitedChunks)
            
            // handle hit blocks and entities
            // if ( hitBlocksQueue.size > 0 ) println("HIT BLOCKS: ${hitBlocksQueue}")
            // if ( hitEntitiesQueue.size > 0 ) println("HIT ENTITIES: ${hitEntitiesQueue}")

            for ( hitBlock in hitBlocksQueue ) {
                hitBlock.gun.hitBlockHandler(this, hitboxes, hitBlock.gun, hitBlock.location, hitBlock.block, hitBlock.source)
            }

            for ( hitEntity in hitEntitiesQueue ) {
                hitEntity.gun.hitEntityHandler(this, hitboxes, hitEntity.gun, hitEntity.location, hitEntity.entity, hitEntity.source, hitEntity.distance)
            }

            // per-world throwable tick systems (needs hitboxes)
            expiredThrowables[worldId] = handleExpiredThrowableSystem(expiredThrowables[worldId] ?: listOf(), hitboxes)
            thrownThrowables[worldId] = tickThrownThrowableSystem(thrownThrowables[worldId] ?: listOf(), hitboxes)

            // per-world landmine tick systems (needs hitboxes)
            landmineExplosions[worldId] = landmineHandleExplosionSystem(landmineExplosions[worldId] ?: listOf(), hitboxes)
        }

        // ================================================
        // SCHEDULE ALL ASYNC TASKS (particles, packets)
        // ================================================
        val tStartPackets = debugNanoTime() // timing probe

        val particleBulletTrails = particleBulletTrailQueue
        val particleBulletBlockImpacts = particleBulletBlockImpactQueue
        val particleBulletHitboxImpacts = particleBulletHitboxImpactQueue
        val particleExplosions = particleExplosionQueue
        val gunAmmoInfoMessages = gunAmmoInfoMessageQueue
        val soundPackets = soundQueue
        val recoilPackets = recoilQueue
        val blockCrackAnimations = blockCrackAnimationQueue

        particleBulletTrailQueue = ArrayList()
        particleBulletBlockImpactQueue = ArrayList()
        particleBulletHitboxImpactQueue = ArrayList()
        particleExplosionQueue = ArrayList()
        gunAmmoInfoMessageQueue = ArrayList()
        soundQueue = ArrayList()
        recoilQueue = ArrayList()
        blockCrackAnimationQueue = ArrayList()

        if ( config.asyncPackets ) {
            Bukkit.getScheduler().runTaskAsynchronously(
                plugin,
                TaskBroadcastBlockCrackAnimations(blockCrackAnimations),
            )
            Bukkit.getScheduler().runTaskAsynchronously(
                plugin,
                TaskRecoil(recoilPackets),
            )
            Bukkit.getScheduler().runTaskAsynchronously(
                plugin,
                TaskSpawnParticleBulletTrails(particleBulletTrails),
            )
            Bukkit.getScheduler().runTaskAsynchronously(
                plugin,
                TaskSpawnParticleBulletBlockImpacts(particleBulletBlockImpacts),
            )
            Bukkit.getScheduler().runTaskAsynchronously(
                plugin,
                TaskSpawnParticleBulletHitboxImpacts(particleBulletHitboxImpacts),
            )
            Bukkit.getScheduler().runTaskAsynchronously(
                plugin,
                TaskSpawnParticleExplosion(particleExplosions),
            )
            Bukkit.getScheduler().runTaskAsynchronously(
                plugin,
                TaskAmmoInfoMessages(gunAmmoInfoMessages),
            )
            Bukkit.getScheduler().runTaskAsynchronously(
                plugin,
                TaskSounds(soundPackets),
            )
        }
        else {
            // sync
            TaskBroadcastBlockCrackAnimations(blockCrackAnimations).run()
            TaskRecoil(recoilPackets).run()
            TaskSpawnParticleBulletTrails(particleBulletTrails).run()
            TaskSpawnParticleBulletBlockImpacts(particleBulletBlockImpacts).run()
            TaskSpawnParticleBulletHitboxImpacts(particleBulletHitboxImpacts).run()
            TaskSpawnParticleExplosion(particleExplosions).run()
            TaskAmmoInfoMessages(gunAmmoInfoMessages).run()
            TaskSounds(soundPackets).run()
        }

        // save kill/death stats system
        playerDeathRecordSaveCounter -= 1
        if ( playerDeathRecordSaveCounter <= 0 ) {
            playerDeathRecordSaveCounter = config.playerDeathRecordSaveInterval
            
            if ( playerDeathRecords.size > 0 ) {
                val deathRecords = playerDeathRecords
                playerDeathRecords = ArrayList()

                Bukkit.getScheduler().runTaskAsynchronously(
                    plugin,
                    TaskSavePlayerDeathRecords(deathRecords, config.playerDeathLogSaveDir),
                )
            }
        }

        
        // timings
        if ( doDebugTimings ) {
            val tEndPackets = debugNanoTime()
            val tUpdateEnd = debugNanoTime()

            debugTimings.add("shoot", tReloadSystem - tShootSystem)
            debugTimings.add("reload", tProjectileSystem - tReloadSystem)
            debugTimings.add("packets", tEndPackets - tStartPackets)
            debugTimings.add("total", tUpdateEnd - tUpdateStart)
        }
    }
}