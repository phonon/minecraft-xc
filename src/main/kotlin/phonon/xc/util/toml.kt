/**
 * Helper extension functions for toml parsing.
 */

package phonon.xc.util.toml

import org.tomlj.Toml
import org.tomlj.TomlArray

/**
 * @exception TomlInvalidTypeException - If any value in the TomlArray is not a Long.
 */
fun TomlArray.toIntArray(): IntArray {
    val arr = IntArray(this.size())
    for ( i in 0 until this.size() ) {
        arr[i] = this.getLong(i).toInt()
    }
    return arr
}