/**
 * Contain gun definition.
 */
package phonon.xc.gun

import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.Files
import java.util.logging.Logger
import org.tomlj.Toml
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.util.Vector
import phonon.xc.gun.getGunHitEntityHandler
import phonon.xc.gun.getGunHitBlockHandler
import phonon.xc.gun.noEntityHitHandler
import phonon.xc.gun.noBlockHitHandler
import phonon.xc.utils.mapToObject
import phonon.xc.utils.damage.DamageType


/**
 * Common gun object used by all guns.
 * This is an immutable object. When properties need to change,
 * create a new gun using kotlin data class `copy()` which can
 * alter certain properties while leaving others the same.
 */
public data class Gun(
    // gun id, used for mapping custom models => gun
    public val id: Int = Int.MAX_VALUE, // invalid

    // gun item/visual properties
    public val itemName: String = "gun",
    public val itemLore: List<String>? = null,
    public val itemModelDefault: Int = 0,     // normal model (custom model data id)
    public val itemModelEmpty: Int = -1,      // when gun out of ammo
    public val itemModelReload: Int = -1,     // when gun is reloading
    public val itemModelIronsights: Int = -1, // when using iron sights
    
    // sounds
    public val soundShoot: String = "gun_shot",
    public val soundReload: String = "gun_reload",
    public val soundEmpty: String = "gun_empty",

    // equiped properties
    // slowness while equiped (if > 0)
    public val equipSlowness: Int = 0,

    // reload [ms]
    public val reloadTimeMillis: Long = 1500,

    // semiauto/regular shoot firing rate [ms]
    public val shootDelayMillis: Long = 500,

    // automatic fire rate properties
    public val autoFire: Boolean = false,     // automatic weapon
    public val autoFireDelayTicks: Int = 2,   // auto fire rate in ticks
    public val autoFireSlowness: Int = 2,     // auto fire slowness level

    // ammo
    public val ammoId: Int = -1,
    public val ammoMax: Int = 10,
    public val ammoPerReload: Int = -1,       // if -1, reload to max. otherwise: ammo + ammoPerReload
    public val ammoIgnore: Boolean = false,   // if true, ignores out of ammo
    
    // sway
    // TODO

    // recoil
    public val recoilHorizontal: Double = 0.1,
    public val recoilVertical: Double = 0.2,
    public val autoFireTimeBeforeRecoil: Long = 200,
    public val autoFireHorizontalRecoilRamp: Double = 0.05, // recoil ramp rate in recoil / millisecond
    public val autoFireVerticalRecoilRamp: Double = 0.05, // recoil ramp rate in recoil / millisecond
    
    // projectile velocity in blocks/tick => (20*vel) m/s
    // physical velocities of ~900 m/s would require vel ~ 45.0
    // but usually this is too fast ingame (makes projectiles too hitscan-y)
    // so instead opt for lower velocity + lower gravity as default
    public val projectileVelocity: Float = 16.0f,

    // projectile gravity in blocks/tick^2 => (400*g) m/s^2
    // physical world gravity would be 0.025 to give 10 m/s^2
    // NOTE: THIS IS A POSITIVE NUMBER
    public val projectileGravity: Float = 0.025f,

    // max lifetime in ticks before despawning
    public val projectileLifetime: Int = 400, // ~20 seconds

    // max projectile distance in blocks before despawning
    public val projectileMaxDistance: Float = 128.0f, // = view distance of 8 chunks

    // main projectile damage
    public val projectileDamage: Double = 4.0,
    public val projectileArmorReduction: Double = 0.5,
    public val projectileResistanceReduction: Double = 0.5,
    public val projectileDamageType: DamageType = DamageType.BULLET,

    // explosion damage and radius and falloff (unused if no explosion)
    public val explosionDamage: Double = 8.0,
    public val explosionMaxDistance: Double = 8.0,        // max distance for checking entities
    public val explosionRadius: Double = 1.0,
    public val explosionFalloff: Double = 2.0,            // damage/block falloff
    public val explosionArmorReduction: Double = 0.5,     // damage/armor point
    public val explosionBlastProtReduction: Double = 1.0, // damage/blast protection
    public val explosionDamageType: DamageType = DamageType.EXPLOSIVE_SHELL,
    public val explosionBlockDamagePower: Float = 0f,     // explosion block damage power level

    // handler on block hit
    public val hitBlockHandler: GunHitBlockHandler = noBlockHitHandler,

    // handler on entity hit
    public val hitEntityHandler: GunHitEntityHandler = entityDamageHitHandler,
) {
    /**
     * Create a projectile using this gun's properties.
     */
    public fun createProjectile(
        source: Entity,
        shootLocation: Location,
        shootDirection: Vector,
    ): Projectile {
        return Projectile(
            gun = this,
            source = source,
            x = shootLocation.x.toFloat(),
            y = shootLocation.y.toFloat(),
            z = shootLocation.z.toFloat(),
            dirX = shootDirection.x.toFloat(),
            dirY = shootDirection.y.toFloat(),
            dirZ = shootDirection.z.toFloat(),
            speed = this.projectileVelocity,
            gravity = this.projectileGravity,
            maxLifetime = this.projectileLifetime,
            maxDistance = this.projectileMaxDistance,
        )
    }
    
    companion object {
        /**
         * Parse and return a Gun from a `gun.toml` file.
         * Return null gun if something fails or no file found.
         */
        public fun fromToml(source: Path, logger: Logger? = null): Gun? {
            try {
                val toml = Toml.parse(source)

                // map with keys as constructor property names
                val properties = HashMap<String, Any>()

                // parse toml file into properties
                
                // gun id
                toml.getLong("id")?.let { properties["id"] = it.toInt() }

                // item properties
                toml.getTable("item")?.let { item -> 
                    item.getString("name")?.let { properties["itemName"] = it }
                    item.getArray("lore")?.let { properties["itemLore"] = it.toList().map { s -> s.toString() } }
                }

                // item model properties
                toml.getTable("model")?.let { model -> 
                    model.getLong("default")?.let { properties["itemModelDefault"] = it.toInt() }
                    model.getLong("empty")?.let { properties["itemModelEmpty"] = it.toInt() }
                    model.getLong("reload")?.let { properties["itemModelReload"] = it.toInt() }
                    model.getLong("ironsights")?.let { properties["itemModelIronsights"] = it.toInt()}
                }

                // sounds
                toml.getTable("sound")?.let { sound -> 
                    sound.getString("shoot")?.let { properties["soundShoot"] = it }
                    sound.getString("reload")?.let { properties["soundReload"] = it }
                    sound.getString("empty")?.let { properties["soundEmpty"] = it }
                }

                // equip
                toml.getTable("equip")?.let { equip ->
                    equip.getLong("slowness")?.let { properties["equipSlowness"] = it.toInt() }
                }

                // ammo
                toml.getTable("ammo")?.let { ammo ->
                    ammo.getLong("id")?.let { properties["ammoId"] = it.toInt() }
                    ammo.getLong("max")?.let { properties["ammoMax"] = it.toInt() }
                    ammo.getLong("per_reload")?.let { properties["ammoPerReload"] = it.toInt() }
                    ammo.getBoolean("ignore")?.let { properties["ammoIgnore"] = it }
                }

                // reloading
                toml.getTable("reload")?.let { reload ->
                    reload.getLong("time")?.let { properties["reloadTimeMillis"] = it }
                }

                // shooting (regular/semiauto)
                toml.getTable("shoot")?.let { shoot ->
                    shoot.getLong("delay")?.let { properties["shootDelayMillis"] = it }
                }

                // automatic fire
                toml.getTable("automatic")?.let { auto ->
                    auto.getBoolean("enabled")?.let { properties["autoFire"] = it }
                    auto.getLong("delay_ticks")?.let { properties["autoFireDelayTicks"] = it.toInt() }
                    auto.getLong("slowness")?.let { properties["autoFireSlowness"] = it.toInt() }
                }

                // sway
                // TODO

                // recoil
                toml.getTable("recoil")?.let { recoil ->
                    recoil.getDouble("horizontal")?.let { properties["recoilHorizontal"] = it }
                    recoil.getDouble("vertical")?.let { properties["recoilVertical"] = it }
                    recoil.getLong("auto_fire_time_before_recoil")?.let { properties["autoFireTimeBeforeRecoil"] = it }
                    recoil.getDouble("auto_fire_horizontal_ramp")?.let { properties["autoFireHorizontalRecoilRamp"] = it }
                    recoil.getDouble("auto_fire_vertical_ramp")?.let { properties["autoFireVerticalRecoilRamp"] = it }
                }

                // hit handlers
                toml.getTable("hit")?.let { hit ->
                    hit.getString("entity")?.let { handlerName ->
                        val handler = getGunHitEntityHandler(handlerName)
                        if ( handler == null ) {
                            logger?.warning("Unknown entity hit handler: ${handlerName}")
                        }
                        properties["hitEntityHandler"] = handler ?: noEntityHitHandler
                    }
                    hit.getString("block")?.let { handlerName ->
                        val handler = getGunHitBlockHandler(handlerName)
                        if ( handler == null ) {
                            logger?.warning("Unknown block hit handler: ${handlerName}")
                        }
                        properties["hitBlockHandler"] = handler ?: noBlockHitHandler
                    }
                }

                // projectile
                toml.getTable("projectile")?.let { projectile ->
                    projectile.getDouble("damage")?.let { properties["projectileDamage"] = it }
                    projectile.getDouble("armor_reduction")?.let { properties["projectileArmorReduction"] = it }
                    projectile.getDouble("resist_reduction")?.let { properties["projectileResistanceReduction"] = it }
                    projectile.getDouble("velocity")?.let { properties["projectileVelocity"] = it.toFloat() }
                    projectile.getDouble("gravity")?.let { properties["projectileGravity"] = it.toFloat() }
                    projectile.getLong("lifetime")?.let { properties["projectileLifetime"] = it.toInt() }
                    projectile.getDouble("max_distance")?.let { properties["projectileMaxDistance"] = it.toFloat() }
                    projectile.getString("damage_type")?.let { name ->
                        val damageType = DamageType.match(name)
                        if ( damageType != null ) {
                            properties["projectileDamageType"] = damageType
                        } else {
                            logger?.warning("Unknown damage type: ${name}")
                        }
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

                // println("CREATING GUN WITH PROPERTIES: ${properties}")

                return mapToObject(properties, Gun::class)
            } catch (e: Exception) {
                logger?.warning("Failed to parse gun file: ${source.toString()}, ${e}")
                return null
            }
        }
        
        /**
         * Load gun from string file, return null if path not found or
         * if loading encounters an error.
         */
        public fun loadFilename(filename: String, logger: Logger? = null): Gun? {
            val p = Paths.get(filename)
            if ( Files.isRegularFile(p) ) {
                try {
                    return Gun.fromToml(p, logger)
                } catch (err: Exception) {
                    logger?.warning("Failed to parse gun file: ${filename}, ${err}")
                    return null
                }
            } else {
                return null
            }
        }
        
        /**
         * Load gun from string file, return default gun if not found.
         */
        public fun loadFilenameOrDefault(filename: String, default: Gun, logger: Logger? = null): Gun {
            val p = Paths.get(filename)
            if ( Files.isRegularFile(p) ) {
                try {
                    return Gun.fromToml(p, logger) ?: default
                } catch (err: Exception) {
                    logger?.warning("Failed to parse gun file: ${filename}, ${err}")
                    return default
                }
            } else {
                return default
            }
        }
    }
}