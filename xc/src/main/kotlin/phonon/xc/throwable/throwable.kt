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
import phonon.xc.util.mapToObject
import phonon.xc.util.IntoItemStack
import phonon.xc.util.damage.DamageType
import phonon.xc.util.particle.ParticlePacket

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
    
    // death message (note: single quote must be '')
    public val deathMessage: String = "{0} was guro''d by {1} using a {2}",

    // cooldown between throwing any throwable items
    public val throwCooldownMillis: Long = 1000,
    public val throwSpeed: Double = 1.0,

    // time before exploding in ticks (~20 ticks/s)
    public val timeToExplode: Int = 100,

    // damage holder if timer expires before throwing if > 0
    public val damageHolderOnTimerExpired: Double = 20.0,

    // damage if thrown object hits target
    public val throwDamage: Double = 0.0,
    public val throwDamageArmorReduction: Double = 0.25,
    public val throwDamageResistanceReduction: Double = 0.25,
    public val throwDamageType: DamageType = DamageType.EXPLOSIVE,
    public val throwFireTicks: Int = 0,

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
    public val soundReadyVolume: Float = 1f,
    public val soundReadyPitch: Float = 1f,
    public val soundThrow: String = "minecraft:entity.arrow.shoot",
    public val soundThrowVolume: Float = 1f,
    public val soundThrowPitch: Float = 1f,
    public val soundImpact: String = "minecraft:block.glass.break", // for hit entity or block handler
    public val soundImpactVolume: Float = 6f,
    public val soundImpactPitch: Float = 1f,
    public val soundExplosion: String = "minecraft:entity.generic.explode", // for explosion handler
    public val soundExplosionVolume: Float = 6f,
    public val soundExplosionPitch: Float = 1f,
): IntoItemStack {
    // flags for handlers, used in thrown throwable tick loop
    // to enable handling for block and entity hit detection 
    public val hasBlockHitHandler: Boolean = onBlockHitHandler !== noBlockHitHandler
    public val hasEntityHitHandler: Boolean = onEntityHitHandler !== noEntityHitHandler

    /**
     * Create a new ItemStack from properties.
     */
    public override fun toItemStack(xc: XC): ItemStack {
        val item = ItemStack(xc.config.materialThrowable, 1)
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

    /**
     * Create a new ItemStack for creating Item entity for throwing.
     */
    public fun toThrownItem(xc: XC): ItemStack {
        val item = ItemStack(xc.config.materialThrowable, 1)
        val itemMeta = item.getItemMeta()
        
        // name
        itemMeta.setDisplayName("${ChatColor.RESET}${this.itemName}")
        
        // model
        itemMeta.setCustomModelData(this.itemModelReady)

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
                
                // death message
                toml.getTable("death")?.let { death -> 
                    death.getString("message")?.let { properties["deathMessage"] = it }
                }

                // throw
                toml.getTable("throw")?.let { th ->
                    th.getLong("cooldown")?.let { properties["throwCooldownMillis"] = it }
                    th.getDouble("speed")?.let { properties["throwSpeed"] = it }
                    th.getLong("time_to_explode")?.let { properties["timeToExplode"] = it.toInt() }
                    th.getDouble("damage")?.let { properties["throwDamage"] = it }
                    th.getDouble("damage_armor_reduction")?.let { properties["throwDamageArmorReduction"] = it }
                    th.getDouble("damage_resist_reduction")?.let { properties["throwDamageResistanceReduction"] = it }
                    th.getLong("damage_fire_ticks")?.let { properties["throwFireTicks"] = it.toInt() }
                    th.getString("damage_type")?.let { name ->
                        val damageType = DamageType.match(name)
                        if ( damageType != null ) {
                            properties["throwDamageType"] = damageType
                        } else {
                            logger?.warning("Unknown damage type: ${name}")
                        }
                    }
                }

                // handlers
                toml.getTable("handlers")?.let { handlers ->
                    handlers.getString("timer_expired")?.let { handlerName ->
                        val handler = getThrowableTimerExpiredHandler(handlerName)
                        if ( handler == null ) {
                            logger?.warning("Unknown throwable timer expired handler: ${handlerName}")
                        }
                        properties["onTimerExpiredHandler"] = handler ?: noTimerExpiredHandler
                    }

                    handlers.getString("block_hit")?.let { handlerName ->
                        val handler = getThrowableBlockHitHandler(handlerName)
                        if ( handler == null ) {
                            logger?.warning("Unknown throwable block hit handler: ${handlerName}")
                        }
                        properties["onBlockHitHandler"] = handler ?: noBlockHitHandler
                    }

                    handlers.getString("entity_hit")?.let { handlerName ->
                        val handler = getThrowableEntityHitHandler(handlerName)
                        if ( handler == null ) {
                            logger?.warning("Unknown throwable entity hit handler: ${handlerName}")
                        }
                        properties["onEntityHitHandler"] = handler ?: noEntityHitHandler
                    }
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
                    if ( sound.isTable("ready") ) {
                        sound.getTable("ready")?.let { s ->
                            s.getString("name")?.let { properties["soundReady"] = it }
                            s.getDouble("volume")?.let { properties["soundReadyVolume"] = it.toFloat() }
                            s.getDouble("pitch")?.let { properties["soundReadyPitch"] = it.toFloat() }
                        }
                    } else {
                        sound.getString("ready")?.let { properties["soundReady"] = it }
                    }

                    if ( sound.isTable("throw") ) {
                        sound.getTable("throw")?.let { s ->
                            s.getString("name")?.let { properties["soundThrow"] = it }
                            s.getDouble("volume")?.let { properties["soundThrowVolume"] = it.toFloat() }
                            s.getDouble("pitch")?.let { properties["soundThrowPitch"] = it.toFloat() }
                        }
                    } else {
                        sound.getString("throw")?.let { properties["soundThrow"] = it }
                    }

                    if ( sound.isTable("impact") ) {
                        sound.getTable("impact")?.let { s ->
                            s.getString("name")?.let { properties["soundImpact"] = it }
                            s.getDouble("volume")?.let { properties["soundImpactVolume"] = it.toFloat() }
                            s.getDouble("pitch")?.let { properties["soundImpactPitch"] = it.toFloat() }
                        }
                    } else {
                        sound.getString("impact")?.let { properties["soundImpact"] = it }
                    }

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
                
                return mapToObject(properties, ThrowableItem::class)
            } catch (e: Exception) {
                logger?.warning("Failed to parse throwable file: ${source.toString()}, ${e}")
                e.printStackTrace()
                return null
            }
        }
    }
}

