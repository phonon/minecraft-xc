/**
 * File system utils
 */

package phonon.xv.util.file

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.Path
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