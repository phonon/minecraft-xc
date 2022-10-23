/**
 * TODO: use XC as dependency and use its version
 */

package phonon.xv.util

import kotlin.reflect.KClass

/**
 * Return a class from a map of "param" => Type
 * which gives constructor parameters. Intended to be
 * used with classes where constructor parameters all
 * have default values. This replaces Builder pattern.
 * Used in deserializing config files to generate objects,
 * with all-optional parameters.
 */
fun <T : Any> mapToObject(map: Map<String, Any>, clazz: KClass<T>) : T {
    // Get default constructor
    val constructor = clazz.constructors.first()
    
    // Map constructor parameters to map values
    // Looks dirty...this equivalent to foreach param, get value, if exists, insert
    val args = constructor
        .parameters
        .mapNotNull { p -> map.get(p.name)?.let { value -> Pair(p, value) } }
        .toMap()
   
    //return object from constructor call
    return constructor.callBy(args)
}