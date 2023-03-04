/**
 * Contains systems for ammo and shooting mechanics for XC weapons.
 * Ammo maps specific ammo types to different XC weapons.
 */
package phonon.xv.system

import java.util.Queue
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.max
import kotlin.math.min
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import phonon.xc.XC
import phonon.xc.item.getCustomItemIdInHand
import phonon.xv.XV
import phonon.xv.core.ComponentsStorage
import phonon.xv.core.Vehicle
import phonon.xv.core.VehicleComponentType
import phonon.xv.core.VehicleElement
import phonon.xv.core.iter.*
import phonon.xv.component.AmmoComponent
import phonon.xv.component.AmmoFireWhenLoadedComponent
import phonon.xv.system.ShootWeaponRequest
import phonon.xv.util.TaskProgress
import phonon.xv.util.progressBar10
import phonon.xv.util.Message
import phonon.xv.util.drain
import phonon.xv.util.item.addIntId
import phonon.xv.util.item.itemInMainHandEquivalentTo

/**
 * A request to add ammo to a vehicle.
 */
public data class AmmoLoadRequest(
    val ammoComponent: AmmoComponent,
    val player: Player? = null,
    // inside or outside vehicle
    val isInside: Boolean = false,
    // if true, skip spawn timer 
    val skipTimer: Boolean = false,
)

/**
 * Signal to finish adding ammo to vehicle.
 */
public data class AmmoLoadFinish(
    val ammoComponent: AmmoComponent,
    val group: Int, // group index
    val ammoType: AmmoComponent.AmmoWeaponType,
    val player: Player? = null,
    val item: ItemStack? = null,
    val itemIdTag: Int? = null,
)


/**
 * Handle vehicle ammo requests, from player vehicle item or command.
 */
public fun XV.systemAmmoLoadVehicle(
    requests: Queue<AmmoLoadRequest>,
    finishQueue: ConcurrentLinkedQueue<AmmoLoadFinish>,
) {
    val xv = this
    val infoMessage = xv.infoMessage

    for ( request in requests.drain() ) {
        val (
            ammoComponent,
            player,
            isInside,
            skipTimer,
        ) = request

        try {
            if ( player !== null ) {
                if ( xv.isPlayerRunningTask(player) ) {
                    continue // player already running task, skip
                }

                val ammoInHandId = xc.getCustomItemIdInHand(player, XC.ITEM_TYPE_AMMO)
                if ( ammoInHandId == -1 ) {
                    continue // no ammo item in hand
                }

                // determine ammo group and ammo type index
                var ammoIdx = -1
                for ( (idx, ammoId) in ammoComponent.validAmmoIds.withIndex() ) {
                    if ( ammoInHandId == ammoId ) {
                        ammoIdx = idx
                        continue
                    }
                }

                if ( ammoIdx == -1 ) {
                    infoMessage.put(player, 2, "${ChatColor.RED}Invalid ammo type!")
                    continue
                }

                val ammoType = ammoComponent.validTypes[ammoIdx]
                val groupIdx = ammoType.group

                if ( ammoComponent.current[groupIdx] >= ammoComponent.max[groupIdx] ) {
                    // skip if ammo already full
                    continue
                }

                // default use outside settings
                var maxMoveDistance = 0.5 // TODO: make configurable
                var timeReload = ammoComponent.timeReloadOutside
                if ( isInside ) {
                    maxMoveDistance = -1.0
                    timeReload = ammoComponent.timeReloadInside
                }

                // attach id tag to item, so we can check if item changed during spawn
                val itemInHand = player.getInventory().getItemInMainHand()
                val itemIdTag = itemInHand?.addIntId()

                if ( timeReload > 0.0 && !skipTimer ) {
                    val task = TaskProgress(
                        timeTaskMillis = timeReload,
                        player = player,
                        initialItemInHand = itemInHand,
                        itemIdTag = itemIdTag,
                        maxMoveDistance = maxMoveDistance,
                        onProgress = { progress ->
                            infoMessage.put(player, 2, progressBar10(progress))
                        },
                        onCancel = { reason ->
                            when ( reason ) {
                                TaskProgress.CancelReason.ITEM_CHANGED -> infoMessage.put(player, 2, "${ChatColor.RED}Item changed, ammo reload cancelled!")
                                TaskProgress.CancelReason.MOVED -> infoMessage.put(player, 2, "${ChatColor.RED}Moved too far, ammo reload cancelled!")
                                else -> {}
                            }
                        },
                        onFinish = {
                            finishQueue.add(AmmoLoadFinish(
                                ammoComponent,
                                groupIdx,
                                ammoType,
                                player,
                                itemInHand,
                                itemIdTag,
                            ))
                        },
                    )

                    xv.startTaskForPlayer(player, task)
                } else { // no spawn, go directly to finish
                    finishQueue.add(AmmoLoadFinish(
                        ammoComponent,
                        groupIdx,
                        ammoType,
                        player,
                        itemInHand,
                        itemIdTag,
                    ))
                }
            }
        } catch ( err: Exception ) {
            err.printStackTrace()
        }
    }
}

/**
 * Handle finishing vehicle ammo requests, from player vehicle item or command.
 */
public fun XV.systemFinishAmmoLoadVehicle(
    requests: ConcurrentLinkedQueue<AmmoLoadFinish>,
) {
    val xv = this

    for ( request in requests.drain() ) {
        val (
            ammoComponent,
            group,
            ammoType,
            player,
            item,
            itemIdTag,
        ) = request

        try {
            // do final check that current ammo item in hand matches initial
            // item in player hand.
            if ( player !== null && item !== null && itemIdTag !== null ) {
                if ( !player.itemInMainHandEquivalentTo(item, itemIdTag) ) {
                    Message.announcement(player, "${ChatColor.RED}Item changed, reload cancelled!")
                    continue
                }

                val itemInHand = player.getInventory().getItemInMainHand()

                val maxAmmo = ammoComponent.max[group]
                val currentAmmo = ammoComponent.current[group]
                val amountPerItem = ammoComponent.amountPerItem[group]
                
                // calculate how many items needed to reload to max
                var itemsUsed = max(1, (maxAmmo - currentAmmo) / amountPerItem)
                // // clamp by max allowed per reload TODO
                // itemsUsed = min(itemsUsed, ammoComponent.maxAmountPerRefuel)
                // clamp by amount actually in player hand
                itemsUsed = min(itemsUsed, itemInHand.amount)

                if ( itemsUsed >= itemInHand.amount ) {
                    player.inventory.setItemInMainHand(null)
                } else {
                    itemInHand.amount -= itemsUsed
                    player.inventory.setItemInMainHand(itemInHand)
                }

                // set ammo based on amount used
                ammoComponent.current[group] = min(maxAmmo, currentAmmo + amountPerItem * itemsUsed)
                ammoComponent.currentType[group] = ammoType
                
                // mark player who loaded
                ammoComponent.playerLoadedUuid[group] = player.uniqueId

                xv.infoMessage.put(player, 2, "Loaded ammo: ${ammoComponent.current[group]}/${maxAmmo}")
            }
            else {
                // just fill to max for now, todo: configurable by command
                ammoComponent.current[group] = ammoComponent.max[group]
            }
        } catch ( err: Exception ) {
            err.printStackTrace()
        }
    }
}


/**
 * System for immediately firing ammo components when ammo is loaded
 * and available.
 */
public fun XV.systemFireWhenLoaded(
    storage: ComponentsStorage,
    shootRequests: Queue<ShootWeaponRequest>,
) {
    val xv = this

    for ( (el, ammo, ammoFireWhenLoaded) in ComponentTuple2.query<
        AmmoComponent,
        AmmoFireWhenLoadedComponent,
    >(storage) ) {
        for ( (idx, currentAmmo) in ammo.current.withIndex() ) {
            if ( currentAmmo > 0 ) {
                val playerWhoLoadedUuid = ammo.playerLoadedUuid[idx]
                val playerWhoLoaded = if ( playerWhoLoadedUuid !== null ) {
                    Bukkit.getPlayer(playerWhoLoadedUuid)
                } else {
                    null
                }

                val element = storage.getElement(el)
                if ( element === null ) {
                    continue
                }

                if ( playerWhoLoaded !== null ) {
                    xv.infoMessage.put(playerWhoLoaded, 3, "Firing!") // TODO CONFIGURE MESSAGE
                }

                val request = ShootWeaponRequest(
                    element = element,
                    component = VehicleComponentType.GUN_BARREL, // TODO: make configurable
                    group = idx,
                    player = playerWhoLoaded,
                    ignoreAmmo = false,
                )
                shootRequests.add(request)
            }
        }
    }
}
