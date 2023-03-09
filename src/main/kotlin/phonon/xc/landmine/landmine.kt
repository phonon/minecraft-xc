/**
 * Landmine
 * 
 * Contains landmine and controls for handling landmines.
 */
package phonon.xc.landmine

import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.Files
import java.util.logging.Logger
import java.util.UUID
import java.util.EnumSet
import java.util.EnumMap
import java.io.File
import kotlin.math.floor
import org.tomlj.Toml
import org.bukkit.ChatColor
import org.bukkit.Color
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.block.Block
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import phonon.xc.XC
import phonon.xc.util.mapToObject
import phonon.xc.util.damage.DamageType
import phonon.xc.util.explosion.createExplosion
import phonon.xc.util.particle.ParticlePacket
import phonon.xc.util.ChunkCoord
import phonon.xc.util.ChunkCoord3D
import phonon.xc.util.Hitbox
import phonon.xc.util.IntoItemStack


internal val PRESSURE_PLATES: EnumSet<Material> = EnumSet.of(
    Material.ACACIA_PRESSURE_PLATE,
    Material.BIRCH_PRESSURE_PLATE,
    Material.CRIMSON_PRESSURE_PLATE,
    Material.DARK_OAK_PRESSURE_PLATE,
    Material.HEAVY_WEIGHTED_PRESSURE_PLATE,
    Material.JUNGLE_PRESSURE_PLATE,
    Material.LIGHT_WEIGHTED_PRESSURE_PLATE,
    Material.OAK_PRESSURE_PLATE,
    Material.POLISHED_BLACKSTONE_PRESSURE_PLATE,
    Material.SPRUCE_PRESSURE_PLATE,
    Material.STONE_PRESSURE_PLATE,
    Material.WARPED_PRESSURE_PLATE,
)

/**
 * Landmine structure
 */
public data class Landmine(
    // landmine block material (should be some material that can be activated
    public val material: Material = Material.WARPED_PRESSURE_PLATE,

    // item/visual properties
    public val itemName: String = "landmine",
    public val itemLore: List<String> = listOf(),

    // death message (note: single quote must be '')
    // {0} = player name
    // {1} = landmine item name
    public val deathMessage: String = "{0} was guro''d by a {1}",

    // explosion settings
    public val explosionDamage: Double = 8.0,
    public val explosionMaxDistance: Double = 8.0,        // max distance for checking entities
    public val explosionRadius: Double = 1.0,
    public val explosionFalloff: Double = 2.0,            // damage/block falloff
    public val explosionArmorReduction: Double = 0.5,     // damage/armor point
    public val explosionBlastProtReduction: Double = 1.0, // damage/blast protection
    public val explosionDamageType: DamageType = DamageType.EXPLOSIVE,
    public val explosionBlockDamagePower: Float = 0f,     // explosion block damage power level
    public val explosionFireTicks: Int = 0,               // if explosion should set targets on fire
    public val explosionParticles: ParticlePacket = ParticlePacket.placeholderExplosion(),

    // sounds
    public val soundExplosion: String = "minecraft:entity.generic.explode",
    public val soundExplosionVolume: Float = 6f,
    public val soundExplosionPitch: Float = 1f,
): IntoItemStack {

    /**
     * Create a new ItemStack from properties.
     */
    public override fun toItemStack(_xc: XC): ItemStack {
        val item = ItemStack(this.material, 1)
        val itemMeta = item.getItemMeta()
        
        // name
        itemMeta.setDisplayName("${ChatColor.RESET}${this.itemName}")
 
        // item lore description
        itemMeta.setLore(this.itemLore)

        item.setItemMeta(itemMeta)

        return item
    }


    companion object {
        /**
         * Parse and return a Landmine from a `landmine.toml` file.
         * Return null Landmine if something fails or no file found.
         */
        public fun fromToml(source: Path, logger: Logger? = null): Landmine? {
            try {
                val toml = Toml.parse(source)

                // map with keys as constructor property names
                val properties = HashMap<String, Any>()

                // materials
                toml.getString("material")?.let { s ->
                    Material.getMaterial(s)?.let { properties["material"] = it } ?: run {
                        logger?.warning("[material.gun] Invalid material: ${s}")
                    }
                }

                // item properties
                toml.getTable("item")?.let { item -> 
                    item.getString("name")?.let { properties["itemName"] = ChatColor.translateAlternateColorCodes('&', it) }
                    item.getArray("lore")?.let { properties["itemLore"] = it.toList().map { s -> s.toString() } }
                }
                
                // death message
                toml.getTable("death")?.let { death -> 
                    death.getString("message")?.let { properties["deathMessage"] = it }
                }

                // explosion
                toml.getTable("explosion")?.let { explosion ->
                    explosion.getDouble("damage")?.let { properties["explosionDamage"] = it }
                    explosion.getDouble("max_distance")?.let { properties["explosionMaxDistance"] = it }
                    explosion.getDouble("radius")?.let { properties["explosionRadius"] = it }
                    explosion.getDouble("falloff")?.let { properties["explosionFalloff"] = it }
                    explosion.getDouble("armor_reduction")?.let { properties["explosionArmorReduction"] = it }
                    explosion.getDouble("blast_prot_reduction")?.let { properties["explosionBlastProtReduction"] = it }
                    explosion.getLong("fire_ticks")?.let { properties["explosionFireTicks"] = it.toInt() }
                    explosion.getDouble("block_damage_power")?.let { properties["explosionBlockDamagePower"] = it.toFloat() }
                    explosion.getString("damage_type")?.let { name ->
                        val damageType = DamageType.match(name)
                        if ( damageType != null ) {
                            properties["explosionDamageType"] = damageType
                        } else {
                            logger?.warning("Unknown damage type: ${name}")
                        }
                    }
                }

                // explosion particles
                toml.getTable("explosion.particles")?.let { particles ->
                    val particleType = particles.getString("type")?.let { ty ->
                        try {
                            Particle.valueOf(ty)
                        } catch ( err: Exception ) {
                            err.printStackTrace()
                            Particle.EXPLOSION_LARGE
                        }
                    } ?: Particle.EXPLOSION_LARGE
                    val count = particles.getLong("count")?.toInt() ?: 1
                    val randomX = particles.getDouble("random_x") ?: 0.0
                    val randomY = particles.getDouble("random_y") ?: 0.0
                    val randomZ = particles.getDouble("random_z") ?: 0.0
                    val force = particles.getBoolean("force") ?: true
                    properties["explosionParticles"] = ParticlePacket(
                        particle = particleType,
                        count = count,
                        randomX = randomX,
                        randomY = randomY,
                        randomZ = randomZ,
                        force = force,
                    )
                }

                // sounds
                toml.getTable("sound")?.let { sound -> 
                    if ( sound.isTable("explosion") ) {
                        sound.getTable("explosion")?.let { s ->
                            s.getString("name")?.let { properties["soundExplosion"] = it }
                            s.getDouble("volume")?.let { properties["soundExplosionVolume"] = it.toFloat() }
                            s.getDouble("pitch")?.let { properties["soundExplosionPitch"] = it.toFloat() }
                        }
                    } else {
                        sound.getString("explosion")?.let { properties["soundExplosion"] = it }
                    }
                }
                
                return mapToObject(properties, Landmine::class)
            } catch (e: Exception) {
                logger?.warning("Failed to parse landmine file: ${source.toString()}, ${e}")
                e.printStackTrace()
                return null
            }
        }
    }
}

// LANDMINE CONTROLS

/**
 * Request to handle landmine activation, e.g. when player steps on pressure plate.
 */
internal data class LandmineActivationRequest(
    val block: Block,
    val landmine: Landmine,
)

/**
 * Request to handle finished landmine activation one tick afterwards.
 */
@JvmInline
internal value class LandmineFinishUseRequest(
    val block: Block,
)

/**
 * Request to create explosion at a landmine block location.
 */
internal data class LandmineExplosionRequest(
    val block: Block,
    val landmine: Landmine,
)

/**
 * Handle landmine activation (e.g. when player steps on pressure plate
 * or redstone activates any other landmine type block).
 */
internal fun XC.landmineActivationSystem(
    landmineActivationRequests: List<LandmineActivationRequest>,
    landmineFinishUseRequests: ArrayList<LandmineFinishUseRequest>,
    landmineExplosions: HashMap<UUID, ArrayList<LandmineExplosionRequest>>,
) {
    for ( request in landmineActivationRequests ) {
        // unpack
        val (
            block,
            landmine,
        ) = request

        landmineExplosions[block.world.getUID()]?.add(LandmineExplosionRequest(
            block = block,
            landmine = landmine,
        ))

        landmineFinishUseRequests.add(LandmineFinishUseRequest(
            block = block,
        ))
    }
    
    // clear requests for next tick
    this.landmineActivationRequests = ArrayList(0)
}

/**
 * System for gathering all visited chunks for thrown throwables.
 * This is needed for throwable entity hit detection and explosions
 */
internal fun getLandmineExplosionVisitedChunksSystem(
    requests: List<LandmineExplosionRequest>,
    visitedChunks: HashSet<ChunkCoord>,
) {
    for ( r in requests ) {
        // unpack
        val block = r.block
        val world = block.getWorld()
        val location = block.getLocation()

        // get item entity's mineman chunk coords (divides by 16)
        val cx0 = floor(location.x).toInt() shr 4
        val cz0 = floor(location.z).toInt() shr 4
        
        // add 1 chunk margin in case hitboxes spans multiple chunks
        val cxmin = cx0 - 1
        val czmin = cz0 - 1
        val cxmax = cx0 + 1
        val czmax = cz0 + 1
        
        for ( cx in cxmin..cxmax ) {
            for ( cz in czmin..czmax ) {
                // only add if chunk loaded
                if ( world.isChunkLoaded(cx, cz) ) {
                    visitedChunks.add(ChunkCoord(cx, cz))
                }
            }
        }
    }
}

/**
 * Handle landmine activation (e.g. when player steps on pressure plate
 * or redstone activates any other landmine type block).
 * Returns empty request queue for next tick. 
 */
internal fun XC.landmineHandleExplosionSystem(
    requests: List<LandmineExplosionRequest>,
    hitboxes: Map<ChunkCoord3D, List<Hitbox>>,
): ArrayList<LandmineExplosionRequest> {
    for ( request in requests ) {
        // unpack
        val (
            block,
            landmine,
         ) = request

        val location = block.getLocation()

        // play explosion sound
        // playing sound can fail if sound string formatted improperly
        try {
            val world = block.getWorld()
            world.playSound(
                location,
                landmine.soundExplosion,
                landmine.soundExplosionVolume,
                landmine.soundExplosionPitch,
            )
        } catch ( e: Exception ) {
            e.printStackTrace()
            this.logger.severe("Failed to play sound: ${landmine.soundExplosion}")
        }

        // summon explosion effect at location
        this.createExplosion(
            hitboxes,
            location,
            null,
            landmine.explosionMaxDistance,
            landmine.explosionDamage,
            landmine.explosionRadius,
            landmine.explosionFalloff,
            landmine.explosionArmorReduction,
            landmine.explosionBlastProtReduction,
            landmine.explosionDamageType,
            landmine.explosionBlockDamagePower,
            landmine.explosionFireTicks,
            landmine.explosionParticles,
            XC.ITEM_TYPE_LANDMINE,
            0,
            landmine.material,
        )
    }

    // return empty queue for next tick
    return ArrayList(0)
}

/**
 * Handle finished landmine activation one tick afterwards.
 * This is used to set landmine block to air. Server sets
 * pressed pressure plates to a PRESSED state on same tick. So removing
 * pressure plate (e.g. set to AIR) must be done manually on next tick.
 */
internal fun XC.landmineFinishUseSystem(
    landmineFinishUseRequests: List<LandmineFinishUseRequest>,
) {
    for ( request in landmineFinishUseRequests ) {
        // unpack
        val block = request.block

        block.setType(Material.AIR)
    }

    this.landmineFinishUseRequests = ArrayList()
}