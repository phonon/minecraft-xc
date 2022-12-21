
package phonon.xv.command

import java.util.EnumSet
import org.bukkit.Bukkit
import org.bukkit.ChunkSnapshot
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.EntityType
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import phonon.xv.XV
import phonon.xv.core.VehicleComponentType
import phonon.xv.core.EntityVehicleData
import phonon.xv.component.*
import phonon.xv.core.loadVehicles
import phonon.xv.core.saveVehicles
import phonon.xv.system.CreateVehicleRequest
import phonon.xv.system.CreateVehicleReason
import phonon.xv.util.Message


private val SUBCOMMANDS = listOf(
    "clear",
    "help",
    "prototype",
    "reload",
    "spawn",
    "start",
    "stop",
    "create",
    "savereload"
)

/**
 * Main /xv command. Route to subcommands.
 */
public class Command(val xv: XV) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, cmd: Command, commandLabel: String, args: Array<String>): Boolean {
        
        // val player = if ( sender is Player ) sender else null
    
        // no args, print help
        if ( args.size == 0 ) {
            this.printHelp(sender)
            return true
        }

        // parse subcommand (keep in alphabetical order)
        val arg = args[0].lowercase()
        when ( arg ) {
            "clear" -> clear(sender)
            "help" -> printHelp(sender)
            "prototype" -> prototype(sender, args)
            "reload" -> reload(sender)
            "spawn" -> spawn(sender, args)
            "start" -> start(sender)
            "stop" -> stop(sender)
            "create" -> create(sender, args)
            "savereload" -> saveReload(sender)

            else -> {
                Message.error(sender, "Invalid /xc subcommand, use /xc help")
            }
        }

        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String> {
        if ( args.size > 1 ) {
            // handle specific subcommands
            when ( args[0].lowercase() ) {
                "prototype",
                "spawn" -> {
                    if ( args.size == 2 ) {
                        if ( sender is Player && !sender.isOp() ) { // only let ops use
                            return listOf()
                        }

                        return xv.vehiclePrototypeNames
                    }
                }
                "create" -> {
                    if (args.size == 2) {
                        if ( sender is Player && !sender.isOp() ) {
                            return listOf()
                        }

                        return xv.vehiclePrototypeNames
                    }
                }
            }

            return listOf()
        }

        return SUBCOMMANDS
    }

    private fun create(sender: CommandSender, args: Array<String>) {
        if ( sender !is Player ) {
            sender.sendMessage("You must be a player to run this command!")
            return
        }

        if ( args.size < 2) {
            sender.sendMessage("Invalid Syntax: /xv create <prototype>")
            return
        }

        val prototype = xv.vehiclePrototypes.get(args[1])
        if ( prototype == null ) {
            sender.sendMessage("Invalid prototype.")
            return
        }

        xv.createRequests.add(
            CreateVehicleRequest(
                prototype,
                CreateVehicleReason.NEW,
                location = sender.location,
                player = sender,
            )
        )

        sender.sendMessage("Queued create request at your location ${sender.location}")
    }

    private fun printHelp(sender: CommandSender?) {
        Message.print(sender, "[xv] mineman vehicles!!!")
        Message.print(sender, "/xv help: help")
        Message.print(sender, "/xv reload: reload plugin config and item configs")
    }

    private fun reload(sender: CommandSender?) {
        val player = if ( sender is Player ) sender else null
        if ( player === null || player.isOp() ) {
            Message.print(sender, "[xv] Reloading...")
            xv.reload()
            Message.print(sender, "[xv] Reloaded")
        }
        else {
            Message.error(sender, "[xv] Only operators can reload")
        }
    }

    private fun saveReload(sender: CommandSender?) {
        val player = if ( sender is Player ) sender else null
        if ( player === null || player.isOp() ) {
            Message.print(sender, "[xv] Saving...")
            xv.saveVehicles(xv.config.pathSave)
            Message.print(sender, "[xv] Data saved to ${xv.config.pathSave}")
            Message.print(sender, "[xv] Reload...")
            xv.reload()
            Message.print(sender, "[xv] Reloaded")
            Message.print(sender, "[xv] Loading data from ${xv.config.pathSave}...")
            xv.loadVehicles(xv.config.pathSave)
            Message.print(sender, "[xv] Loaded")
        }
        else {
            Message.error(sender, "[xv] Only operators can reload")
        }
    }

    private fun start(sender: CommandSender?) {
        val player = if ( sender is Player ) sender else null
        if ( player === null || player.isOp() ) {
            Message.print(sender, "[xv] Starting engine...")
            xv.start()
        }
        else {
            Message.error(sender, "[xv] Only operators can /xv start")
        }
    }

    private fun stop(sender: CommandSender?) {
        val player = if ( sender is Player ) sender else null
        if ( player === null || player.isOp() ) {
            Message.print(sender, "[xv] Stopping engine...")
            xv.stop()
        }
        else {
            Message.error(sender, "[xv] Only operators can /xv stop")
        }
    }

    private fun clear(sender: CommandSender?) {
        val player = if ( sender is Player ) sender else null
        if ( player === null || player.isOp() ) {
            Message.print(sender, "[xv] Clearing archetypes...")
            for ( archetype in xv.storage.archetypes ) {
                archetype.clear()
            }
        }
        else {
            Message.error(sender, "[xv] Only operators can /xv clear")
        }
    }

    /**
     * Debug print prototypes. Usage:
     * /xv prototype: print out list of all prototype names
     * /xv prototype [name]: print out a prototype name, print all its vehicle elements
     */
    private fun prototype(sender: CommandSender?, args: Array<String>) {
        // Only let op use this command ingame
        // TODO: give permissions node to view this data
        val player = if ( sender is Player ) sender else null
        if ( player !== null && !player.isOp() ) {
            Message.error(sender, "[xv] Only operators can use this command")
            return
        }

        if ( args.size == 1 ) {
            Message.print(sender, "[xv] Prototypes:")
            for ( (name, prototype) in xv.vehiclePrototypes ) {
                Message.print(sender, "  - ${name}")
            }
        }
        else if ( args.size == 2 ) {
            val name = args[1]
            val prototype = xv.vehiclePrototypes[name]
            if ( prototype === null ) {
                Message.error(sender, "[xv] Prototype ${name} not found")
            }
            else {
                Message.print(sender, "[xv] Prototype '${name}':")
                for ( element in prototype.elements ) {
                    Message.print(sender, "  - ${element.name}")

                    if ( element.parent !== null ) {
                        Message.print(sender, "    - Parent: ${element.parent}")
                    }

                    for ( ty in element.layout ) {
                        Message.print(sender, "    - ${ty}")
                    }
                }
            }
        }
        else {
            Message.error(sender, "[xv] Invalid usage")
            Message.error(sender, "[xv] /xv prototype: print out list of all prototype names and # of elements")
            Message.error(sender, "[xv] /xv prototype [name]: print out a prototype and its vehicle elements")
        }
    }

    /**
     * Spawn vehicle for testing
     */
    private fun spawn(sender: CommandSender?, args: Array<String>) {
        // Only let op use this command ingame
        // TODO: give permissions node to view this data
        val player = if ( sender is Player ) sender else null
        if ( player === null ) {
            Message.error(sender, "[xv] /xv spawn [name] must be used ingame")
            return
        }

        val vehicleName = if ( args.size == 2 ) {
            args[1]
        } else {
            "debug_car"
        }

        val prototype = xv.vehiclePrototypes[vehicleName]
        if ( prototype === null ) {
            Message.error(sender, "[xv] Prototype ${vehicleName} not found")
            return
        }

        val loc = player.location

        // manually injecting [transform, model, seats, seatsRaycast, landMovement] into archetype
        // to test the movement system

        for ( element in prototype.elements ) {
            val layout = EnumSet.of(
                VehicleComponentType.TRANSFORM,
                VehicleComponentType.MODEL,
                VehicleComponentType.SEATS,
                VehicleComponentType.SEATS_RAYCAST,
                VehicleComponentType.LAND_MOVEMENT_CONTROLS,
            )
            xv.storage.addLayout(layout)
            val archetype = xv.storage.lookup[layout]!!
            
            // only create vehicle for ptotoypes with [transform, model, landMovement]
            // TODO: a more generalized creation, likely needs codegen + delegation
            if ( element.layout.containsAll(layout) ) {
                val armorstand: ArmorStand = loc.world!!.spawn(loc, ArmorStand::class.java)
                armorstand.setGravity(false)
                armorstand.setVisible(true)
                // armorstand.getEquipment()!!.setHelmet(createModel(Tank.modelMaterial, this.modelDataBody))
                armorstand.setRotation(loc.yaw, 0f)
                
                // temporary: using single archetype, just pushing element id
                // to the end of a single archetype array
                val elementId = archetype.size

                xv.entityVehicleData[armorstand.getUniqueId()] = EntityVehicleData(
                    vehicleId = 0,
                    elementId = elementId,
                    layout = element.layout,
                    componentType = VehicleComponentType.MODEL,
                )
                
                archetype.elements[archetype.size] = elementId
                
                // need better way to inject initialization parameters
                // also MAKE SURE its a copy(), not a reference to
                // original components (or else it will be shared)
                archetype.transform!!.add(element.transform!!.copy(
                    world = loc.world,
                    x = loc.x,
                    y = loc.y,
                    z = loc.z,
                    yaw = loc.yaw.toDouble(),
                ))
                //element.seats?.copy()
                archetype.seats!!.add(element.seats!!.copy())
                archetype.seatsRaycast!!.add(element.seatsRaycast!!.copy())
                archetype.model!!.add(element.model!!.copy(
                    armorstand = armorstand,
                ))
                archetype.landMovementControls!!.add(element.landMovementControls!!.copy(
                    // speed = 2.0, // inject initial speed for testing
                ))
                archetype.size += 1 // added 1 element
            }
        }

    }
}

