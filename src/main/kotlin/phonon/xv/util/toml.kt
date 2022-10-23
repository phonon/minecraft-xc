/**
 * Contain extension functions for toml parsing library.
 * 
 * Contains functions to get any formatted number as a specific type.
 * e.g. either a "69" or "69.0" as a double.
 * The toml library by default has strict formatting which is really
 * fucking annoying for a config file where u don't want to always
 * remember you need a .0 for a double or else the config parsing throws
 * an error.
 */

package phonon.xv.util.toml

import org.tomlj.TomlTable
import org.tomlj.TomlArray

/**
 * Extension function to get and convert any number format
 * from the Toml table. This is not a typesafe cast, but is suppressed.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T: Number> TomlTable.getNumberAs(key: String): T? {
    if ( this.isDouble(key) ) {
        val v = this.getDouble(key)
        return if ( v !== null ) {
            when ( T::class ) {
                Double::class -> v as T
                Float::class -> v.toFloat() as T
                Long::class -> v.toLong() as T
                Int::class -> v.toInt() as T
                Short::class -> v.toInt().toShort() as T
                Byte::class -> v.toInt().toByte() as T
                else -> null
            }
        } else {
            null
        }
    } else if ( this.isLong(key) ) {
        val v = this.getLong(key)
        return if ( v !== null ) {
            when ( T::class ) {
                Double::class -> v.toDouble() as T
                Float::class -> v.toFloat() as T
                Long::class -> v as T
                Int::class -> v.toInt() as T
                Short::class -> v.toShort() as T
                Byte::class -> v.toByte() as T
                else -> null
            }
        } else {
            null
        }
    } else {
        return null
    }
}

/**
 * Extension function to get and convert any number format
 * from the Toml table. This is not a typesafe cast, but is
 * suppressed.
 * 
 * Hope the toml array index is really a number!!
 * :^(
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified T: Number> TomlArray.getNumberAs(i: Int): T {
    // no method to check if value at location is a speific type...
    // so just need to cast the object
    val v = this.get(i)
    return if ( v is Number ) {
        when ( T::class ) {
            Double::class -> v.toDouble() as T
            Float::class -> v.toFloat() as T
            Long::class -> v.toLong() as T
            Int::class -> v.toInt() as T
            Short::class -> v.toInt().toShort() as T
            Byte::class -> v.toInt().toByte() as T
            else -> v as T // :^(
        }
    } else {
        // :^(
        v as T
    }
}