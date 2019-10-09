package tech.harmonysoft.oss.kotlin.baker.impl

import tech.harmonysoft.oss.kotlin.baker.Context
import tech.harmonysoft.oss.kotlin.baker.KotlinCreator
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.isSuperclassOf

class ParameterValueRetriever(val parameter: KParameter) {

    private val name = parameter.name

    val error: String? = if (name == null) {
        "can't extract name for parameter #${parameter.index}"
    } else {
        null
    }

    /**
     * @param prefix        prefix to use for the property lookup, e.g. if prefix is *'my'* then for class
     *                      like `data class MyClass(val counter: Int)` property *'my.counter'* would be checked
     * @param creator       non-primitive types creator
     * @param context       instantiation context
     * @return              `null` as an indication that there is no explicit value for the given argument and it's
     *                      optional/has default value
     */
    fun retrieve(prefix: String, creator: KotlinCreator, context: Context): Result<Any?, String>? {
        if (name == null) {
            throw IllegalStateException("Can't retrieve a value of parameter $parameter for path '$prefix' "
                                        + "- the parameter doesn't expose its name")
        }

        val klass = parameter.type.classifier as? KClass<*> ?: return Result.failure(
                "type '${parameter.type}' for argument '$name' for path '$prefix' "
                + "is not a ${KClass::class.qualifiedName}"
        )

        val propertyName = context.getRegularPropertyName(prefix, name)
        if (context.isSimpleType(klass)) {
            return retrieveSimpleValue(propertyName, klass, context)
        }

        if (context.isCollection(klass)) {
            return retrieveCollection(klass, propertyName, creator, context)
        }

        if (Map::class.isSuperclassOf(klass)) {
            return retrieveMap(propertyName, creator, context)
        }

        return try {
            Result.success(creator.create(propertyName, parameter.type, context))
        } catch (e: Exception) {
            if (parameter.isOptional) {
                null
            } else {
                throw e
            }
        }
    }

    private fun retrieveSimpleValue(
            propertyName: String,
            klass: KClass<*>,
            context: Context
    ): Result<Any?, String>? {
        val rawValue = context.getPropertyValue(propertyName)
        return if (rawValue == null) {
            when {
                parameter.type.isMarkedNullable -> Result.success<Any?, String>(null)
                parameter.isOptional -> null
                else -> Result.failure("no value for non-nullable parameter '$propertyName'")
            }
        } else {
            Result.success(context.convertIfNecessary(rawValue, klass))
        }
    }

    private fun retrieveCollection(
            collectionClass: KClass<*>,
            propertyName: String,
            creator: KotlinCreator,
            context: Context
    ) : Result<Any?, String>? {
        val invalidValue = context.getPropertyValue(propertyName)
        if (invalidValue != null) {
            throw IllegalArgumentException(
                    "Expected to find collection data as a parameter '${parameter.name}' of type ${parameter.type} "
                    + "under base property '$propertyName' but found a simple value '$invalidValue' instead"
            )
        }

        val typeArguments = parameter.type.arguments
        if (typeArguments.size != 1) {
            throw IllegalArgumentException(
                    "Failed retrieving value of a '${parameter.type}' property for path '$propertyName' - expected "
                    + "to find a single type argument, but found ${typeArguments.size}: $typeArguments"
            )
        }

        val type = typeArguments[0].type ?: return Result.failure(
                "can't derive collection type for property '$propertyName' of type ${parameter.type}"
        )
        val typeClass = type.classifier as? KClass<*> ?: return Result.failure(
                "can't derive type parameter class for property '$propertyName' of type ${parameter.type}"
        )

        var i = 0
        val parameters = context.createCollection(collectionClass)
        while (true) {
            val collectionElementPropertyName = context.getCollectionElementPropertyName(propertyName, i)
            i++
            if (context.isSimpleType(typeClass)) {
                val rawValue = context.getPropertyValue(collectionElementPropertyName) ?: break
                parameters.add(context.convertIfNecessary(rawValue, typeClass))
                continue
            } else if (context.isCollection(typeClass)) {
                val r = retrieveCollection(typeClass, collectionElementPropertyName, creator, context)
                if (r != null && r.success) {
                    parameters.add(r)
                } else {
                    return Result.failure("can't create a collection for property "
                                          + "$collectionElementPropertyName - ${r?.failureValue}")
                }
            } else {
                try {
                    context.withTolerateEmptyCollection(false) {
                        val element = creator.create<Any>(collectionElementPropertyName, type, context)
                        parameters.add(element)
                    }
                } catch (e: Exception) {
                    break
                }
            }
        }

        return when {
            parameters.isEmpty() -> {
                when {
                    parameter.type.isMarkedNullable -> Result.success<Any?, String>(null)
                    parameter.isOptional -> null
                    else -> Result.failure(
                            "Can't instantiate collection property '${parameter.name}' for type "
                            + "${parameter.type} - no data is defined for it and the property is "
                            + "mandatory (non-nullable and doesn't have default value). Tried to find the "
                            + "value using key '${context.getCollectionElementPropertyName(propertyName, 0)}'")
                }
            }
            else -> Result.success(parameters)
        }
    }

    private fun retrieveMap(
            propertyName: String,
            creator: KotlinCreator,
            context: Context
    ): Result<Any?, String>? {
        val invalidValue = context.getPropertyValue(propertyName)
        if (invalidValue != null) {
            throw IllegalArgumentException(
                    "Expected to find map data as a parameter '${parameter.name}' of type ${parameter.type} "
                    + "under base property '$propertyName' but found a simple value '$invalidValue' instead"
            )
        }

        val keyType = parameter.type.arguments[0].type ?: throw IllegalArgumentException(
                "Failed instantiating a Map property '$propertyName' - no key type info is available for $parameter"
        )
        val keyClass = keyType.classifier as? KClass<*> ?: throw IllegalArgumentException(
                "Failed instantiating a Map property '$propertyName' - can't derive key class for $parameter"
        )
        val valueType = parameter.type.arguments[1].type ?: throw IllegalArgumentException(
                "Failed instantiating a Map property '$propertyName' - no value type info is available for $parameter"
        )
        val valueClass = valueType.classifier as? KClass<*> ?: throw IllegalArgumentException(
                "Failed instantiating a Map property '$propertyName' - can't derive value class for $parameter"
        )
        val map = context.createMap()
        for (key in context.getMapKeys(propertyName, keyType)) {
            val valuePropertyName = context.getMapValuePropertyName(propertyName, key)
            if (context.isSimpleType(valueClass)) {
                val rawValue = context.getPropertyValue(valuePropertyName)
                if (rawValue != null) {
                    val convertedValue = context.convertIfNecessary(rawValue, valueClass)
                    map[context.convertIfNecessary(key, keyClass)] = convertedValue
                }
            } else {
                try {
                    val value = creator.create<Any>(valuePropertyName, valueType, context)
                    map[context.convertIfNecessary(key, keyClass)] = value
                } catch (ignore: Exception) {
                }
            }
        }
        return Result.success(map)
    }

    override fun toString(): String {
        return "$parameter value retriever"
    }
}