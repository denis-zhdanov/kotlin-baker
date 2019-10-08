package tech.harmonysoft.oss.kotlin.baker.impl

import tech.harmonysoft.oss.kotlin.baker.Context
import tech.harmonysoft.oss.kotlin.baker.KotlinCreator
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.jvm.javaConstructor

class Instantiator<T>(private val constructor: KFunction<T>) {

    private val retrievers = constructor.parameters.map { ParameterValueRetriever(it) }
    private val error = retrievers.mapNotNull { it.error }.joinToString()

    fun mayBeCreate(prefix: String, creator: KotlinCreator, context: Context): Result<T, String> {
        if (error.isNotBlank()) {
            return Result.failure(error)
        }
        val paramLookupResults = retrievers.map {
            it to mayBeRemap(it.retrieve(prefix, creator, context), it.parameter.type, context)
        }.toMap()

        val error = paramLookupResults.values.mapNotNull {
            if (it == null || it.success) {
                null
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

    private fun mayBeRemap(result: Result<Any?, String>?, type: KType, context: Context): Result<Any?, String>? {
        if (context.tolerateEmptyCollection || (result != null && !result.success)) {
            return result
        }
        val klass = type.classifier as? KClass<*> ?: return result
        if (!context.isCollection(klass)) {
            return result
        }

        return if (result == null || (result.successValue as Collection<*>).isEmpty()) {
            Result.failure("found an empty collection parameter but current context disallows that")
        } else {
            result
        }
    }

    override fun toString(): String {
        val declaringClass = constructor.javaConstructor?.declaringClass
        return "${declaringClass?.simpleName ?: constructor.name}(" +
               constructor.parameters.joinToString { it.name.toString() } +
               ")"
    }
}