/**
 * Contains different hard-coded map objects for Enum => Type
 * backed by hard-coded Array or primitive arrays (e.g. IntArray). 
 */
package phonon.xc.util


/**
 * Alternative to EnumMap where all enum values must be mapped
 * to some value. This avoids doing null key checks in EnumMap.
 * This is useful for situations where every enum requires a
 * mapping. This is backed by an array accessed directly
 * by enum.ordinal without doing any bounds or null key checks.
 */
public class EnumArrayMap<K: Enum<K>, T>(public val array: Array<T>) {
    /**
     * Getter and setter operators to allow `map[key]` style access.
     */
    operator fun get(key: K) = this.array[key.ordinal]
    
    operator fun set(key: K, value: T) {
        this.array[key.ordinal] = value
    }

    /**
     * Return array size.
     */
    public val size: Int get() = this.array.size


    companion object {
        /**
         * Wrapper to create EnumToIntMap from an initializer function.
         * Note this internally uses `enumValues` and `map` which
         * allocate and iterates full list of enums during initialization,
         * so avoid using this in hot paths.
         */
        inline fun <reified K: Enum<K>, reified T> from(init: (K) -> T): EnumArrayMap<K, T> =
            EnumArrayMap(enumValues<K>().map(init).toTypedArray())
    }
}


/**
 * Hard-coded map from Enum => Int, backed by an IntArray
 * indexed using the Enum ordinal. This is immutable only.
 * Intended as pure lookup table.
 */
@JvmInline
value class EnumToIntMap<K: Enum<K>>(
    public val array: IntArray,
) {
    /**
     * Getter operators to allow `map[key]` style access.
     */
    operator fun get(key: K) = this.array[key.ordinal]


    companion object {
        /**
         * Wrapper to create EnumToIntMap from an initializer function.
         * Note this internally uses `enumValues` and `map` which
         * allocate and iterates full list of enums during initialization,
         * so avoid using this in hot paths.
         */
        inline fun <reified K: Enum<K>> from(init: (K) -> Int): EnumToIntMap<K> =
            EnumToIntMap(enumValues<K>().map(init).toIntArray())
    }
}
