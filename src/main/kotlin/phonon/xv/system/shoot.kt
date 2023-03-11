/**
 * Contains systems for ammo and shooting mechanics for XC weapons.
 * Ammo maps specific ammo types to different XC weapons.
 */
package phonon.xv.system

import java.util.Queue
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.min
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import phonon.xc.XC
import phonon.xc.gun.Gun
import phonon.xc.gun.Projectile
import phonon.xc.gun.ProjectileSystem
import phonon.xc.util.sound.SoundPacket
import phonon.xc.throwable.ThrownThrowable
import phonon.xv.XV
import phonon.xv.core.Vehicle
import phonon.xv.core.VehicleComponentType
import phonon.xv.core.VehicleElement
import phonon.xv.component.AmmoComponent
import phonon.xv.component.GunBarrelComponent
import phonon.xv.component.GunTurretComponent
import phonon.xv.component.TransformComponent
import phonon.xv.util.drain

/**
 * Request to shoot weapon in vehicle, corresponding to some ammo group.
 */
public data class ShootWeaponRequest(
    // which vehicle element is firing
    val element: VehicleElement,
    // which component is firing
    val component: VehicleComponentType,
    // which ammo group is firing
    val group: Int = 0,
    // if not null, this is a player shooting the weapon (for tracking shooter)
    val player: Player? = null,
    // if true, ignore ammo check
    val ignoreAmmo: Boolean = false,
    // vehicle entity source of weapon
    val source: ArmorStand? = null,
    // location to spawn projectile
    val shootPosition: Location? = null,
    // direction for projectile
    val shootDirection: Vector? = null,
)

/**
 * Handle vehicle ammo requests, from player vehicle item or command.
 */
public fun XV.systemShootWeapon(
    requests: Queue<ShootWeaponRequest>,
) {
    val xv = this

    val rng = ThreadLocalRandom.current()

    for ( request in requests.drain() ) {
        val (
            element,
            component,
            group,
            player,
            ignoreAmmo,
            reqSource,
            reqShootPosition,
            reqShootDirection,
        ) = request

        val ammo = element.components.ammo
        if ( ammo === null ) {
            continue
        }

        try {
            if ( ammo.currentType.size <= group ) {
                continue
            }

            val ammoType = ammo.currentType[group]
            val ammoAmount = ammo.current[group]
            
            if ( !ignoreAmmo && ammoAmount <= 0 ) {
                if ( player !== null ) {
                    xv.infoMessage.put(player, 2, "${ChatColor.RED}OUT OF AMMO")
                }
                continue
            }

            ammo.current[group] = ammoAmount - 1

            // xv.logger.info("Shooting weapon: ${ammoType}")

            // if no transform close
            val transform = element.components.transform
            if ( transform === null ) {
                xv.logger.warning("TODO: Cannot shoot weapon without transform yet")
                continue
            }

            // find entity source of bullet
            var source: ArmorStand? = reqSource
            var shootPosition: Location? = reqShootPosition
            var shootDirection: Vector? = reqShootDirection
            if ( component == VehicleComponentType.GUN_TURRET ) {
                val gunTurret = element.components.gunTurret!!
                source = gunTurret.armorstandTurret
                if ( source === null ) {
                    xv.logger.warning("TODO: Cannot shoot weapon without gun turret yet: ${gunTurret.armorstandTurret}")
                    continue
                }
                shootPosition = Location(
                    source.world,
                    transform.x + gunTurret.turretYawCos * gunTurret.turretX - gunTurret.turretYawSin * gunTurret.turretZ,
                    transform.y + gunTurret.turretY + gunTurret.shootOffsetY,
                    transform.z + gunTurret.turretYawSin * gunTurret.turretX + gunTurret.turretYawCos * gunTurret.turretZ,
                    gunTurret.barrelYawf,
                    gunTurret.barrelPitchf,
                )
                shootDirection = shootPosition.direction
                shootPosition.add(shootDirection.clone().multiply(2.0))
            }
            else if ( component == VehicleComponentType.GUN_BARREL ) {
                val gunBarrel = element.components.gunBarrel!!
                source = gunBarrel.armorstand
                if ( source === null ) {
                    xv.logger.warning("TODO: Cannot shoot weapon without gun turret yet")
                    continue
                }
                shootPosition = Location(
                    source.world,
                    transform.x + transform.yawCos * gunBarrel.barrelX - transform.yawSin * gunBarrel.barrelZ,
                    transform.y + gunBarrel.barrelY,
                    transform.z + transform.yawSin * gunBarrel.barrelX + transform.yawCos * gunBarrel.barrelZ,
                    gunBarrel.yawf,
                    gunBarrel.pitchf,
                )
                shootDirection = shootPosition.direction
                shootPosition.add(shootDirection.clone().multiply(2.0))
            }
            else if ( component == VehicleComponentType.AIRPLANE ) {

                
            }
            else {
                xv.logger.warning("TODO: Cannot shoot weapon without gun turret or gun barrel yet")
                continue
            }

            if ( source === null || shootPosition === null || shootDirection === null ) {
                xv.logger.warning("TODO: Cannot shoot weapon without source, shoot position, or shoot direction yet")
                continue
            }

            // xv.logger.info("Shooting weapon: ${ammoType} from ${shootPosition} in direction ${shootDirection} (source: ${source})")

            if ( ammoType.weaponType == XC.ITEM_TYPE_GUN ) {
                val gunType = xv.xc.storage.gun[ammoType.weaponId]
                if ( gunType !== null && player !== null ) {
                    xv.gunShoot(
                        source = source,
                        player = player,
                        shootPosition = shootPosition,
                        shootDirection = shootDirection,
                        gun = gunType,
                        random = rng,
                    ) 
                } else {
                    xv.logger.warning("TODO: Cannot shoot gun without player yet")
                }
            }
            else if ( ammoType.weaponType == XC.ITEM_TYPE_THROWABLE ) {
                val throwableType = xv.xc.storage.throwable[ammoType.weaponId]
                if ( throwableType !== null && player !== null ) {
                    // spawn throwable item entity
                    val itemThrown = throwableType.toThrownItem(xv.xc)
                    val itemEntity = source.world.dropItem(shootPosition, itemThrown)
                    itemEntity.setPickupDelay(Integer.MAX_VALUE)
                    itemEntity.setVelocity(shootDirection.multiply(throwableType.throwSpeed))

                    // add throwable
                    xc.thrownThrowables[source.world.getUID()]?.let { throwables ->
                        throwables.add(ThrownThrowable(
                            throwable = throwableType,
                            id = 0, // unused
                            ticksElapsed = 0,
                            itemEntity = itemEntity,
                            source = source,
                            thrower = player,
                        ))
                    }
                } else {
                    xv.logger.warning("TODO: Cannot shoot gun without player yet")
                }
                
            }
            
        } catch ( err: Exception ) {
            if ( xv.debug) {
                err.printStackTrace()
            }
        }
    }
}


/**
 * Common function to run a single gun shot for single, burst, and auto
 * firing modes.
 */
private fun XV.gunShoot(
    source: ArmorStand,
    player: Player?,
    shootPosition: Location,
    shootDirection: Vector,
    gun: Gun,
    random: ThreadLocalRandom,
) {
    val xv = this

    // get projectile system from player world
    val world = source.world
    val projectileSystem = xv.xc.projectileSystems[world.getUID()]
    if ( projectileSystem === null ) {
        return
    }

    // for vehicle, use built-in sway in gun
    val sway = gun.swayBase

    for ( _i in 0 until gun.projectileCount ) {
        var shootDirX = shootDirection.x
        var shootDirY = shootDirection.y
        var shootDirZ = shootDirection.z
        if ( sway > 0.0 ) {
            shootDirX += random.nextDouble(-sway, sway)
            shootDirY += random.nextDouble(-sway, sway)
            shootDirZ += random.nextDouble(-sway, sway)
        }

        // creating projectile here manually since shoot direction
        // is modulated by random sway
        val projectile = Projectile(
            gun = gun,
            source = source,
            x = shootPosition.x.toFloat(),
            y = shootPosition.y.toFloat(),
            z = shootPosition.z.toFloat(),
            dirX = shootDirX.toFloat(),
            dirY = shootDirY.toFloat(),
            dirZ = shootDirZ.toFloat(),
            speed = gun.projectileVelocity,
            gravity = gun.projectileGravity,
            maxLifetime = gun.projectileLifetime,
            maxDistance = gun.projectileMaxDistance,
            proximity = gun.projectileProximity,
            shooter = player ?: source,
        )

        projectileSystem.addProjectile(projectile)
    }

    // shoot sound
    xv.xc.soundQueue.add(SoundPacket(
        sound = gun.soundShoot,
        world = shootPosition.world,
        location = shootPosition,
        volume = gun.soundShootVolume,
        pitch = gun.soundShootPitch,
    ))
}
