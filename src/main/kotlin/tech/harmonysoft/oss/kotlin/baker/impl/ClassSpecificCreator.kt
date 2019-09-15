package tech.harmonysoft.oss.kotlin.baker.impl

import tech.harmonysoft.oss.kotlin.baker.Context
import tech.harmonysoft.oss.kotlin.baker.KotlinCreator
import kotlin.reflect.KClass
import kotlin.reflect.KType

@Suppress("UNCHECKED_CAST")
class ClassSpecificCreator<T : Any>(private val type: KType) {

    private val klass: KClass<T> = type.classifier as? KClass<T> ?: throw IllegalArgumentException(
            "Can't instantiate type '$type' - its classifier is not a class"
    )
    private val instantiators: Collection<Instantiator<T>> = parseConstructors(klass).map { Instantiator(it) }

    fun create(prefix: String, creator: KotlinCreator, context: Context): T {
        val failedResults = mutableMapOf<Instantiator<T>, String>()
        val result = instantiators.mapFirstNotNull {
            val candidate = it.mayBeCreate(prefix, creator, context)
            if (candidate.success) {
                candidate.successValue
            } else {
                failedResults[it] = candidate.failureValue
                null
            }
        } ?: throw IllegalArgumentException(
                "Failed instantiating a ${klass.qualifiedName ?: klass.simpleName} instance. "
                + "None of ${instantiators.size} constructors match:\n  "
                + failedResults.entries.joinToString(separator = "\n  ") {
                    "${it.key} - ${it.value}"
                }
        )

        return result
    }

    override fun toString(): String {
        return "$type creator"
    }
}