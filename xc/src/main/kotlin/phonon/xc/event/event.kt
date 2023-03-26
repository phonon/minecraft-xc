/**
 * Contain public api custom events emitted by plugin.
 */

package phonon.xc.event

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import phonon.xc.gun.Gun
import phonon.xc.throwable.ThrowableItem
import phonon.xc.util.damage.DamageType


/**
 * Event emitted when an entity is hit directly by a projectile
 * and takes damage. This only occurs for entities that have
 * hitboxes enabled.
 */
public data class XCProjectileDamageEvent(
    public val gun: Gun,
    public val location: Location,
    public val target: Entity,
    public val source: Entity,
    public val damage: Double,
    public val distance: Double,
): Event(), Cancellable {
    // event cancelled
    private var cancelled: Boolean = false

    override public fun isCancelled(): Boolean {
        return this.cancelled
    }

    override public fun setCancelled(cancel: Boolean) {
        this.cancelled = cancel
    }

    override public fun getHandlers(): HandlerList {
        return XCProjectileDamageEvent.handlers
    }

    
    companion object {
        private val handlers: HandlerList = HandlerList()

        @JvmStatic
        public fun getHandlerList(): HandlerList {
            return handlers
        }
    }
}


/**
 * Event emitted when an entity is hit directly by a throwable item
 * and takes damage. This only occurs for entities that have
 * hitboxes enabled.
 */
public data class XCThrowableDamageEvent(
    public val throwable: ThrowableItem,
    public val location: Location,
    public val target: Entity,
    public val source: Entity,
): Event(), Cancellable {
    // event cancelled
    private var cancelled: Boolean = false

    override public fun isCancelled(): Boolean {
        return this.cancelled
    }

    override public fun setCancelled(cancel: Boolean) {
        this.cancelled = cancel
    }

    override public fun getHandlers(): HandlerList {
        return XCThrowableDamageEvent.handlers
    }

    
    companion object {
        private val handlers: HandlerList = HandlerList()

        @JvmStatic
        public fun getHandlerList(): HandlerList {
            return handlers
        }
    }
}


/**
 * Event emitted when an entity takes damage for an explosion
 * (so entity within explosion distance). This only occurs for
 * entities that have hitboxes enabled.
 */
public data class XCExplosionDamageEvent(
    public val target: Entity,
    public val damage: Double, // base damage, not modified by armor
    public val damageType: DamageType,
    public val distance: Double, // distance from explosion center
    public val source: Entity?,
    public val weaponType: Int,
    public val weaponId: Int,
    public val weaponMaterial: Material,
): Event(), Cancellable {
    // event cancelled
    private var cancelled: Boolean = false

    override public fun isCancelled(): Boolean {
        return this.cancelled
    }

    override public fun setCancelled(cancel: Boolean) {
        this.cancelled = cancel
    }

    override public fun getHandlers(): HandlerList {
        return XCExplosionDamageEvent.handlers
    }

    
    companion object {
        private val handlers: HandlerList = HandlerList()
        
        @JvmStatic
        public fun getHandlerList(): HandlerList {
            return handlers
        }
    }
}