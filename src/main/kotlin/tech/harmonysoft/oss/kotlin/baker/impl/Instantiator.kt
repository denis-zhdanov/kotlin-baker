package tech.harmonysoft.oss.kotlin.baker.impl

import tech.harmonysoft.oss.kotlin.baker.Context
import tech.harmonysoft.oss.kotlin.baker.KotlinCreator
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaConstructor

class Instantiator<T>(private val constructor: KFunction<T>) {

    private val retrievers = constructor.parameters.map { ParameterValueRetriever(it) }
    private val error = retrievers.mapNotNull { it.error }.joinToString()

    fun mayBeCreate(prefix: String, creator: KotlinCreator, context: Context): Result<T, String> {
        if (error.isNotBlank()) {
            return Result.failure(error)
        }
        val paramLookupResults = retrievers.map {
            it to it.retrieve(prefix, creator, context)
        }.toMap()

        val error = paramLookupResults.values.mapNotNull {
            if (it == null) {
                null
            } else if (it.success) {
                val successValue = it.successValue
                if (!context.tolerateEmptyCollection && successValue is Collection<*> && successValue.isEmpty()) {
                    "found an empty collection parameter but current context disallows that"
                } else {
                    null
                }
            } else {
                it.failureValue
            }
        }.joinToString()

        if (error.isNotBlank()) {
            return Result.failure(error)
        }

        val arguments = paramLookupResults.filter {
            it.value != null
        }.map {
            it.key.parameter to it.value!!.successValue
        }
        return try {
            Result.success(constructor.callBy(arguments.toMap()))
        } catch (e: Exception) {
            Result.failure("${e.javaClass.name}: ${e.message} for parameters ${arguments.joinToString {
                "${it.first.name}=${it.second}"
            }}")
        }
    }

    override fun toString(): String {
        val declaringClass = constructor.javaConstructor?.declaringClass
        return "${declaringClass?.simpleName ?: constructor.name}(" +
               constructor.parameters.joinToString { it.name.toString() } +
               ")"
    }
}