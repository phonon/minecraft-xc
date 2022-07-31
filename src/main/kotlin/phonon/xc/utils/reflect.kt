/**
 * Reflection utility functions.
 */

package phonon.xc.utils.reflect

/**
 * Returns an enum constant by name from an enum class using reflection.
 */
public fun getEnumConstant(enumClass: Class<*>, name: String): Any? {
    if ( !enumClass.isEnum() ) {
        return null
    }

    // find enum by matching first name found
    for ( e in enumClass.getEnumConstants() ) {
        try {
            if ( name == (e as Enum<*>).name ) {
                return e
            }
        } catch (err: Exception) {
            err.printStackTrace();
        }
    }
    return null;
}