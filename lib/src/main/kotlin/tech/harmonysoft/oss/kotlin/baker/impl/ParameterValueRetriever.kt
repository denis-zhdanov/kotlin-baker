package tech.harmonysoft.oss.kotlin.baker.impl

import tech.harmonysoft.oss.kotlin.baker.Context
import tech.harmonysoft.oss.kotlin.baker.KotlinCreator
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSuperclassOf

class ParameterValueRetriever(val parameter: KParameter) {

    private val name = parameter.name
    private val remainingMapDiscoveryDepth = ThreadLocal.withInitial { AtomicInteger(5) }

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
            throw KotlinBakerException("Can't retrieve a value of parameter $parameter for path '$prefix' "
                                        + "- the parameter doesn't expose its name")
        }

        val klass = parameter.type.classifier as? KClass<*> ?: return Result.failure(
                "type '${parameter.type}' for argument '$name' for path '$prefix' "
                + "is not a ${KClass::class.qualifiedName}"
        )

        val propertyName = context.getRegularPropertyName(prefix, name)
        return doRetrieve(propertyName = propertyName,
                          creator = creator,
                          context = context,
                          type = parameter.type,
                          klass = klass,
                          optional = parameter.isOptional || parameter.type.isMarkedNullable)
    }

    private fun doRetrieve(
        propertyName: String,
        creator: KotlinCreator,
        context: Context,
        type: KType,
        klass: KClass<*>,
        optional: Boolean
    ): Result<Any?, String>? {
        if (klass == Any::class) {
            return retrieveAny(propertyName, creator, context)
        }

        if (context.isSimpleType(klass)) {
            return retrieveSimpleValue(propertyName, klass, context)
        }

        if (context.isCollection(klass)) {
            return retrieveCollection(klass, type, propertyName, creator, context)
        }

        if (Map::class.isSuperclassOf(klass)) {
            return retrieveMap(propertyName, creator, context)
        }

        return try {
            Result.success(creator.create(propertyName, type, context))
        } catch (e: Exception) {
            if (optional) {
                null
            } else {
                throw e
            }
        }
    }

    /**
     * We want to be smart enough in case of [Any] type and differentiate between the following possible result types:
     * * [String]
     * * [List<String>][List]
     * * [Map<String, Any>][Map]
     */
    private fun retrieveAny(propertyName: String, creator: KotlinCreator, context: Context): Result<Any?, String>? {
        return when {
            isMapLike(propertyName, context) -> retrieveMap(propertyName = propertyName,
                                                            creator = creator,
                                                            context = context,
                                                            keyType = STRING_TYPE,
                                                            keyClass = String::class,
                                                            valueType = ANY_TYPE,
                                                            valueClass = Any::class,
                                                            optional = false,
                                                            nullable = false)
            isCollectionLike(propertyName, context) -> retrieveCollection(collectionClass = List::class,
                                                                          propertyName = propertyName,
                                                                          creator = creator,
                                                                          context = context,
                                                                          valueType = ANY_TYPE,
                                                                          valueClass = Any::class,
                                                                          optional = false,
                                                                          nullable = false)
            else -> retrieveSimpleValue(propertyName, Any::class, context)
        }
    }

    private fun isMapLike(propertyName: String, context: Context): Boolean {
        val remainingDepth = remainingMapDiscoveryDepth.get()
        if (remainingDepth.decrementAndGet() < 0) {
            remainingDepth.incrementAndGet()
            return false
        }
        try {
            val keys = context.getMapKeys(propertyName, STRING_TYPE).map {
                context.getMapValuePropertyName(propertyName, it)
            }
            for (key in keys) {
                if (context.getPropertyValue(key) != null
                    || context.getPropertyValue(context.getCollectionElementPropertyName(key, 0)) != null
                ) {
                    return true
                }
            }
            return keys.any {
                isMapLike(it, context)
            }
        } finally {
            remainingDepth.incrementAndGet()
        }
    }

    private fun isCollectionLike(propertyName: String, context: Context): Boolean {
        val name = context.getCollectionElementPropertyName(propertyName, 0)
        if (context.getPropertyValue(name) != null) {
            return true
        }
        return isMapLike(name, context)
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
            collectionType: KType,
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

        val typeArguments = collectionType.arguments
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

        return retrieveCollection(collectionClass = collectionClass,
                                  propertyName = propertyName,
                                  creator = creator,
                                  context = context,
                                  valueType = type,
                                  valueClass = typeClass,
                                  optional = parameter.isOptional,
                                  nullable = parameter.type.isMarkedNullable)
    }

    private fun retrieveCollection(
        collectionClass: KClass<*>,
        propertyName: String,
        creator: KotlinCreator,
        context: Context,
        valueType: KType,
        valueClass: KClass<*>,
        nullable: Boolean,
        optional: Boolean
    ) : Result<Any?, String>? {
        var i = 0
        val parameters = context.createCollection(collectionClass)
        while (true) {
            val collectionElementPropertyName = context.getCollectionElementPropertyName(propertyName, i)
            i++
            var stop = true
            context.withTolerateEmptyCollection(false) {
                doRetrieve(propertyName = collectionElementPropertyName,
                           creator = creator,
                           context = context,
                           type = valueType,
                           klass = valueClass,
                           optional = true
                )?.takeIf {
                    it.success
                }?.let {
                    it.successValue
                }?.apply {
                    parameters += this
                    stop = false
                }
            }
            if (stop) {
                break
            }
        }

        return when {
            parameters.isEmpty() -> {
                when {
                    nullable -> Result.success<Any?, String>(null)
                    optional -> null
                    else -> Result.failure(
                            "Can't instantiate collection property '$propertyName' for type "
                            + "$collectionClass - no data is defined for it and the property is "
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
        return retrieveMap(propertyName = propertyName,
                           creator = creator,
                           context = context,
                           keyType = keyType,
                           keyClass = keyClass,
                           valueType = valueType,
                           valueClass = valueClass,
                           optional = parameter.isOptional,
                           nullable = parameter.type.isMarkedNullable)
    }

    private fun retrieveMap(
            propertyName: String,
            creator: KotlinCreator,
            context: Context,
            keyType: KType,
            keyClass: KClass<*>,
            valueType: KType,
            valueClass: KClass<*>,
            optional: Boolean,
            nullable: Boolean
    ): Result<Any?, String>? {
        val map = context.createMap()
        for (key in context.getMapKeys(propertyName, keyType)) {
            val valuePropertyName = context.getMapValuePropertyName(propertyName, key)
            try {
                doRetrieve(propertyName = valuePropertyName,
                           creator = creator,
                           context = context,
                           type = valueType,
                           klass = valueClass,
                           optional = false
                )?.takeIf {
                    it.success
                }?.let {
                    val value = it.successValue
                    if (value != null) {
                        map[context.convertIfNecessary(key, keyClass)] = value
                    }
                }
            } catch (ignore: Exception) {
            }
        }
        return when {
            map.isEmpty() -> when {
                optional -> Result.success<Any?, String>(null)
                nullable -> null
                else -> throw IllegalArgumentException(
                        "Can't build a Map<${keyClass.simpleName}, ${valueClass.simpleName}> for base property "
                        + "'$propertyName' - it's not optional and no key-value pairs for it are found"
                )
            }
            else -> Result.success(map)
        }
    }

    override fun toString(): String {
        return "$parameter value retriever"
    }

    companion object {
        private val STRING_TYPE = String::class.createType()
        private val ANY_TYPE = Any::class.createType()
    }
}