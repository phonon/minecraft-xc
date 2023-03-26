/**
 * Helper extension functions for toml parsing.
 */

package phonon.xc.util.toml

import org.tomlj.Toml
import org.tomlj.TomlArray
import org.tomlj.TomlTable

/**
 * @exception TomlInvalidTypeException - If any value in the TomlArray is not a Long.
 */
internal fun TomlArray.toIntArray(): IntArray {
    val arr = IntArray(this.size())
    for ( i in 0 until this.size() ) {
        arr[i] = this.getLong(i).toInt()
    }
    return arr
}


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