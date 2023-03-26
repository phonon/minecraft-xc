package phonon.xv.system

import org.bukkit.entity.Player
import phonon.xv.util.Message
import phonon.xv.util.ConcurrentPlayerInfoMessageMap

/**
 * System for finally sending info text to players. Clears the map after
 * sending the messages.
 */
public fun systemPrintInfoMessages(
    infoMessagesMap: ConcurrentPlayerInfoMessageMap,
) {
    for ( infoMessage in infoMessagesMap.snapshot().values ) {
        Message.announcement(infoMessage.player, infoMessage.message)
    }
}