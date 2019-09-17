package tech.harmonysoft.oss.kotlin.baker

import kotlin.reflect.KType

interface KotlinCreator {

    /**
     * Creates an instance of the target type from the underlying [property source][Context.getPropertyValue].
     *
     * @param prefix        property prefix to use for the given type's creation, e.g. if we need to create
     *                      a class like `data class Target(val prop: Int)` and target property source
     *                      has a property *environment.target.prop=1*, we need to use *environment.target*
     *                      prefix
     * @param type          target type to be instantiated
     * @param context       instantiation context to use. It facades underlying
     * [property source][Context.getPropertyValue] and also has additional information used during instantiation
     */
    fun <T: Any> create(prefix: String, type: KType, context: Context): T
}