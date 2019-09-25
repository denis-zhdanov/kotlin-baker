package tech.harmonysoft.oss.kotlin.baker.impl

import tech.harmonysoft.oss.kotlin.baker.Context
import tech.harmonysoft.oss.kotlin.baker.KotlinCreator
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.isSuperclassOf

class KotlinCreatorImpl : KotlinCreator {

    private val cache = ConcurrentHashMap<KType, ClassSpecificCreator<Any>>()

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> create(prefix: String, type: KType, context: Context): T {
        val klass = type.classifier as? KClass<*>
        if (klass != null && Enum::class.isSuperclassOf(klass)) {
            val enumMembersByName = (klass.java.enumConstants as Array<Enum<*>>).map {
                it.name to it
            }.toMap()
            val enumName = context.getPropertyValue(prefix)?.toString() ?: throw IllegalArgumentException(
                    "Failed instantiating a enum of type '${klass.qualifiedName}' - its name is not specified "
                    + "under property '$prefix'"
            )
            return enumMembersByName[enumName] as? T ?: throw IllegalArgumentException(
                    "Failed finding a member of enum ${klass.qualifiedName} with name '$enumName'. "
                    + "Known names: ${enumMembersByName.keys}"
            )
        }
        return cache.computeIfAbsent(type) {
            ClassSpecificCreator(type)
        }.create(prefix, this, context) as T
    }
}