/**
 * System for pipelined saving of vehicles and backup timer counting.
 */

package phonon.xv.system

import kotlin.math.max
import com.google.gson.JsonObject
import phonon.xv.XV

fun XV.systemPipelinedSave() {
    val xv = this

    // save process not running
    if ( !xv.savingVehicles ) {
        // decrement backup timer
        xv.saveBackupTimer = max(0, xv.saveBackupTimer - 1)
        xv.saveTimer = max(0, xv.saveTimer - 1)

        if ( xv.saveTimer == 0 ) {
            // start pipelined save process
            xv.savingVehicles = true

            // get vehicles to save
            val vehiclesToSave = xv.vehicleStorage.getAllVehicles()

            // max number of vehicles to save per tick
            // take either a minimum number of vehicles per tick
            // or the number of vehicles divided by the number of pipeline ticks
            val savesPerTick = max(xv.config.saveMinVehiclesPerTick, (vehiclesToSave.size / xv.config.savePipelineTicks) + 1)

            xv.saveVehiclesQueue = vehiclesToSave
            xv.saveVehiclesQueueIndex = 0
            xv.saveVehiclesPerTick = savesPerTick
            xv.saveVehiclesJsonBuffer = ArrayList<JsonObject>(vehiclesToSave.size)
        }
    }

    // run pipelined save process
    if ( xv.savingVehicles ) {
        val vehiclesToSave = xv.saveVehiclesQueue
        val savesPerTick = xv.saveVehiclesPerTick
        val vehiclesJsonBuffer = xv.saveVehiclesJsonBuffer
        var n = xv.saveVehiclesQueueIndex

        //// DEBUG
        // println("Running vehicle save, index n=${n}, savesPerTick=${savesPerTick}, vehiclesToSave.size=${vehiclesToSave.size}")

        // run pipelined vehicle json serialization, limits number of vehicles
        // serialized per tick to reduce per tick performance impact
        var i = 0
        while ( i < savesPerTick && n < vehiclesToSave.size ) {
            val vehicle = vehiclesToSave[n]
            if ( vehicle !== null ) {
                try {
                        vehiclesJsonBuffer.add(vehicle.toJson())
                } catch ( err: Exception ) {
                    xv.logger.severe("Failed to save vehicle ${vehicle} to json: ${err.message}")
                    err.printStackTrace()
                }
            }
            i += 1
            n += 1
        }

        // all vehicles serialized so pipeline finished, schedules json file
        // write to disk on an async thread, and backup if timer reached.
        if ( n >= vehiclesToSave.size ) {
            val doBackup = if ( xv.saveBackupTimer == 0 ) {
                xv.saveBackupTimer = xv.config.saveBackupPeriod
                true
            } else {
                false
            }
            
            xv.saveVehiclesJson(
                vehiclesJson = vehiclesJsonBuffer,
                async = true,
                backup = doBackup,
            )

            // clear buffers to prevent memory leaks
            xv.saveVehiclesQueue = arrayOf()
            xv.saveVehiclesJsonBuffer = arrayListOf()
            xv.saveVehiclesQueueIndex = 0
            xv.saveVehiclesPerTick = xv.config.saveMinVehiclesPerTick
            xv.saveTimer = xv.config.savePeriod
            xv.savingVehicles = false
        } else {
            xv.saveVehiclesQueueIndex = n
        }
    }
}