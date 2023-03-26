/**
 * File system utils
 */

package phonon.xv.util.file

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.exists
import kotlin.streams.toList

/**
 * Return list of files in a directory.
 */
public fun listDirFiles(dir: Path): List<Path> {
    if ( !Files.isDirectory(dir) ) {
        return listOf()
    }

    try {
        return Files.list(dir)
            .filter({file -> !Files.isDirectory(file)})
            .map(Path::getFileName)
            .toList()
    } catch (err: Exception) {
        System.err.println("Error getting files in ${dir}")
        return listOf()
    }
}

/**
 * Helper to read json file into a JsonObject.
 * Return null if file does not exist.
 */
public fun readJson(path: Path): JsonObject? {
    if ( !Files.exists(path) ) return null

    return Files.newBufferedReader(path).use {
        JsonParser.parseReader(it)
    }.asJsonObject
}

/**
 * Helper to write json object to a file.
 */
public fun writeJson(
    json: JsonObject,
    path: Path,
    prettyPrinting: Boolean = false,
) {
    // gson lib instance, handles parsing
    val gson = if ( prettyPrinting ) {
        GsonBuilder()
            .setPrettyPrinting()
            .create()
    } else {
        Gson()
    }

    Files.write(
        path,
        gson.toJson(json).toByteArray(),
        StandardOpenOption.WRITE,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
    )
}

// backup format
private val BACKUP_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd.HH.mm.ss")

/**
 * Generate a timestamped backup path using current local time.
 */
public fun newBackupPath(parentDir: Path): Path {
    val dateTimestamp = BACKUP_DATE_FORMATTER.format(LocalDateTime.now())
    return parentDir.resolve("vehiclesave.${dateTimestamp}.json")
}