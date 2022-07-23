/**
 * Contain all player throwable item (grenade, molotov, etc.)
 * controls systems and helper classes.
 */

package phonon.xc.throwable

import java.util.concurrent.ThreadLocalRandom
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.entity.Item
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.scheduler.BukkitRunnable
import phonon.xc.XC
import phonon.xc.utils.Message

