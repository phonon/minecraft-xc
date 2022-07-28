/**
 * Contain throwable item object.
 */

package phonon.xc.throwable


import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.Files
import java.util.logging.Logger
import java.util.UUID
import org.tomlj.Toml
import org.bukkit.Color
import org.bukkit.ChatColor
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.inventory.ItemStack
import phonon.xc.XC
import phonon.xc.utils.mapToObject
import phonon.xc.utils.damage.DamageType
import phonon.xc.utils.particle.ParticlePacket

/**
 * Wrapper for all throwable item types.
 * Must support different style of throwables:
 * - grenade (prime, throw, delayed explosion)
 * - molotov (prime, throw, explode on impact)
 * 
 * NOTE: must be called a "ThrowableItem" because "Throwable" is a
 * java interface.
 */
public data class ThrowableItem(
    // gun item/visual properties
    public val itemName: String = "throwable",
    public val itemLore: List<String> = listOf(),
    public val itemModelDefault: Int = 0,     // default normal model (custom model data id)
    public val itemModelReady: Int = -1,      // when throwable is "primed" (e.g. grenade pin out)

    // cooldown between throwing any throwable items
    public val throwCooldownMillis: Long = 1000,
    public val throwSpeed: Double = 1.0,

    // time before exploding in ticks (~20 ticks/s)
    public val timeToExplode: Int = 100,

    // damage holder if timer expires before throwing if > 0
    public val damageHolderOnTimerExpired: Double = 20.0,

    // explosion damage and radius and falloff (unused if no explosion)
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

    // handlers
    public val onTimerExpiredHandler: ThrowableTimerExpiredHandler = noTimerExpiredHandler,
    public val onBlockHitHandler: ThrowableBlockHitHandler = noBlockHitHandler,
    public val onEntityHitHandler: ThrowableEntityHitHandler = noEntityHitHandler,

    // sounds
    public val soundReady: String = "minecraft:block.lever.click",
    public val soundThrow: String = "minecraft:entity.arrow.shoot",
    public val soundImpact: String = "minecraft:block.glass.break", // for hit entity or block handler
    public val soundExplosion: String = "minecraft:entity.generic.explode", // for explosion handler
) {

    /**
     * Create a new ItemStack from properties.
     */
    public fun toItemStack(): ItemStack {
        val item = ItemStack(XC.config.materialThrowable, 1)
        val itemMeta = item.getItemMeta()
        
        // name
        itemMeta.setDisplayName("${ChatColor.RESET}${this.itemName}")
        
        // model
        itemMeta.setCustomModelData(this.itemModelDefault)
 
        // item lore description
        itemMeta.setLore(this.itemLore)

        item.setItemMeta(itemMeta)

        return item
    }

    companion object {
        /**
         * Parse and return a Throwable from a `throwable.toml` file.
         * Return null Throwable if something fails or no file found.
         */
        public fun fromToml(source: Path, logger: Logger? = null): ThrowableItem? {
            try {
                val toml = Toml.parse(source)

                // map with keys as constructor property names
                val properties = HashMap<String, Any>()

                // item properties
                toml.getTable("item")?.let { item -> 
                    item.getString("name")?.let { properties["itemName"] = ChatColor.translateAlternateColorCodes('&', it) }
                    item.getArray("lore")?.let { properties["itemLore"] = it.toList().map { s -> s.toString() } }
                }

                // item model properties
                toml.getTable("model")?.let { model -> 
                    model.getLong("default")?.let { properties["itemModelDefault"] = it.toInt() }
                    model.getLong("ready")?.let { properties["itemModelReady"] = it.toInt() }
                }
                
                // throw
                toml.getTable("throw")?.let { th ->
                    th.getLong("cooldown")?.let { properties["throwCooldownMillis"] = it }
                    th.getDouble("speed")?.let { properties["throwSpeed"] = it }
                    th.getLong("time_to_explode")?.let { properties["timeToExplode"] = it.toInt() }
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
                    explosion.getString("damage_type")?.let { name ->
                        val damageType = DamageType.match(name)
                        if ( damageType != null ) {
                            properties["explosionDamageType"] = damageType
                        } else {
                            logger?.warning("Unknown damage type: ${name}")
                        }
                    }
                    explosion.getDouble("block_damage_power")?.let { properties["explosionBlockDamagePower"] = it.toFloat() }
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
                    properties["explosionParticles"] = ParticlePacket(
                        particle = particleType,
                        count = count,
                        randomX = randomX,
                        randomY = randomY,
                        randomZ = randomZ
                    )
                }
                
                return mapToObject(properties, ThrowableItem::class)
            } catch (e: Exception) {
                logger?.warning("Failed to parse throwable file: ${source.toString()}, ${e}")
                e.printStackTrace()
                return null
            }
        }
    }
}

