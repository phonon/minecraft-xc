/**
 * Death tracking for:
 * - kill/death ratio stats tracking
 * - custom death messages
 * 
 * Standard death message format convention:
 * MessageFormat.format(
 *  deathMessage,
 *  playerName,
 *  killerName,
 *  killerWeapon,
 * )
 * 
 * So death message format should use:
 * {0}: player name
 * {1}: killer name
 * {2}: killer weapon name
 */

package phonon.xc.util.death

import java.time.LocalDateTime
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta


/**
 * Death event for XC plugin specific death tracking.
 */
public data class XcPlayerDeathEvent(
    val player: Player,
    val killer: Entity?,
    val weaponType: Int, // use XC.ITEM_TYPE_*
    val weaponId: Int,   // to get weapon id in array
    val weaponMaterial: Material, // used for weapons attached to a material (e.g. landmine)
)

/**
 * Record for saving player death event.
 */
public data class PlayerDeathRecord(
    val timestamp: LocalDateTime,
    val playerName: String,
    val playerUUID: String,
    val killerName: String,
    val killerUUID: String,
    val deathCause: String,
    val deathMessage: String,
) {
    /**
     * Return JSON formatted string.
     */
    public fun toJson(): String {
        val s = StringBuilder()

        s.append("{")
        s.append("\"timestamp\":\"").append(timestamp).append("\",")
        s.append("\"playerName\":\"").append(playerName).append("\",")
        s.append("\"playerUUID\":\"").append(playerUUID).append("\",")
        s.append("\"killerName\":\"").append(killerName).append("\",")
        s.append("\"killerUUID\":\"").append(killerUUID).append("\",")
        s.append("\"deathCause\":\"").append(deathCause).append("\",")
        s.append("\"deathMessage\":\"").append(deathMessage).append("\"")
        s.append("}")

        return s.toString()
    }

    /**
     * Return CSV formatted string.
     */
    public fun toCsv(): String {
        val s = StringBuilder()

        s.append(timestamp).append(",")
        s.append(playerName).append(",")
        s.append(playerUUID).append(",")
        s.append(killerName).append(",")
        s.append(killerUUID).append(",")
        s.append(deathCause).append(",")
        s.append("\"$deathMessage\"") // need quotes since death message usually a string
        
        return s.toString()
    }
}


/**
 * Runnable task to save player death records.
 */
public class TaskSavePlayerDeathRecords(
    val deathRecords: ArrayList<PlayerDeathRecord>,
    val saveDir: String,
): Runnable {
    override fun run() {
        // println("SAVING DEATH RECORDS $deathRecords")

        // first merge all strings saves in specific files
        // to avoid re-loading files many times
        // map filename => StringBuilder with string data
        val dataToSave = HashMap<String, StringBuilder>()

        for ( record in deathRecords ) {
            val timestamp = record.timestamp
            val filename = "deaths.${timestamp.getYear()}.${timestamp.getMonthValue()}.${timestamp.getDayOfMonth()}.csv"
            val s = dataToSave[filename] ?: StringBuilder()
            s.append(record.toCsv()).append("\n")
            dataToSave[filename] = s
        }
        
        // create save directory
        if ( !Files.exists(Path.of(saveDir)) ) {
            Files.createDirectories(Path.of(saveDir))
        }

        for ( (filename, s) in dataToSave ) {
            Files.writeString(
                Path.of(saveDir, filename),
                s.toString(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
        }
    }
}

/**
 * Create a player skull item from a player. Used to
 * drop player heads when they are killed by another player
 * (as a trophy).
 */
internal fun Player.createHeadItem(msg: String?): ItemStack {
    val playerHead = ItemStack(Material.PLAYER_HEAD, 1)
    val skullMeta = playerHead.getItemMeta() as SkullMeta
    skullMeta.setOwningPlayer(this)
    skullMeta.setDisplayName("${ChatColor.RESET}${this.getName()}")
    if ( msg !== null ) {
        skullMeta.setLore(listOf(msg))
    }
    playerHead.setItemMeta(skullMeta)

    return playerHead
}