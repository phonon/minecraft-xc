/**
 * File system utils
 */

package phonon.xv.util.file

import com.google.gson.*
import phonon.xv.core.loadVehicles
import java.io.FileReader
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
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

public fun readJson(dir: Path): JsonObject? {
    // IO
    val file = dir.toFile()
    if ( !file.exists() )
        return null

    return FileReader(file).use {
        JsonParser.parseReader(it)
    }.asJsonObject
}

public fun writeJson(json: JsonObject, dir: Path, prettyPrinting: Boolean) {
    // json object built, now we gotta do the IO
    val saveFile = dir.toFile()

    // create new file if not exists
    if (!saveFile.exists()) {
        saveFile.createNewFile()
    }

    // gson lib instance, handles parsing
    val gson = if ( prettyPrinting ) {
        GsonBuilder()
                .setPrettyPrinting()
                .create()
    } else {
        Gson()
    }

    // write data
    FileWriter(saveFile).use {
        gson.toJson(json, it)
    }
}

private val dateFormatter = DateTimeFormatter.ofPattern("MM-dd-yyyy-HH-mm-ss")

public fun newBackupPath(parentDir: Path): Path {
    val baseFileName = dateFormatter.format(LocalDateTime.now())
    var identifier = 1
    var path: Path?
    do {
        path = Paths.get(parentDir.toString(), "$baseFileName-$identifier.json")
        identifier++
    } while ( path!!.exists() )

    return path
}