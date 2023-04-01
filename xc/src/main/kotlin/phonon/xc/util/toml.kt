/**
 * Helper extension functions for toml parsing.
 */

package phonon.xc.util.toml

import org.bukkit.Particle
import org.bukkit.Material
import org.tomlj.TomlTable
import org.tomlj.TomlArray
import phonon.xc.util.EnumArrayMap

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

/**
 * Extension function to get and convert a string in toml config
 * into a material type. Returns null if the string does not match a
 * material type.
 */
fun TomlTable.getMaterial(key: String): Material? {
    return this.getString(key)?.let { s ->
        Material.matchMaterial(s)
    }
}

/**
 * Extension function to get and convert a string in toml config
 * into a particle type. Returns null if string does not match a
 * particle type.
 */
fun TomlTable.getParticle(key: String): Particle? {
    return this.getString(key)?.let { s ->
        try {
            Particle.valueOf(s.uppercase())
        } catch ( err: Exception ) {
            println("[xv] Invalid toml config particle name for key ${key}: ${s}")
            err.printStackTrace()
            null
        }
    }
}

/**
 * Parse a enum array map in the toml config, where keys are names of enum
 * type K and values are of type V.
 */
@Suppress("UNCHECKED_CAST")
inline fun <reified K: Enum<K>, reified V> TomlTable.getEnumArrayMap(defaultValue: V): EnumArrayMap<K, V> {
    val enumMap: EnumArrayMap<K, V> = EnumArrayMap.from({ _ -> defaultValue })
    
    for ( (key, value) in this.entrySet() ) {
        try {
            val enumValue = java.lang.Enum.valueOf(K::class.java, key.uppercase())
            enumMap[enumValue] = value as V
        } catch ( err: Exception ) {
            println("[xv] Invalid toml config enum name for key ${key}")
            err.printStackTrace()
        }
    }

    return enumMap
}
