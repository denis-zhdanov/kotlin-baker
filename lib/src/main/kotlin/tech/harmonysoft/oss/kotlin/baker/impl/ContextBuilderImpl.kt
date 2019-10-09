package tech.harmonysoft.oss.kotlin.baker.impl

import tech.harmonysoft.oss.kotlin.baker.Context
import java.time.ZoneId
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.isSuperclassOf

class ContextBuilderImpl(private val dataProvider: (String) -> Any?) : Context.Builder {

    private val simpleTypes = DEFAULT_SIMPLE_TYPES.toMutableSet()
    private val collectionTypes = DEFAULT_COLLECTION_TYPES.toMutableSet()

    private var collectionCreator = wrapCollectionCreator(DEFAULT_COLLECTION_CREATOR)
    private var regularPropertyNameStrategy = DEFAULT_REGULAR_PROPERTY_NAME_STRATEGY
    private var collectionPropertyNameStrategy = DEFAULT_COLLECTION_ELEMENT_PROPERTY_NAME_STRATEGY
    private var typeConverter = wrapTypeConverter(DEFAULT_TYPE_CONVERTER)
    private var mapCreator: () -> MutableMap<Any, Any> = DEFAULT_MAP_CREATOR
    private var mapKeyStrategy = DEFAULT_MAP_KEY_STRATEGY
    private var mapValuePropertyNameStrategy = DEFAULT_REGULAR_PROPERTY_NAME_STRATEGY

    override fun withSimpleTypes(types: Set<KClass<*>>, replace: Boolean): Context.Builder {
        return apply {
            if (replace) {
                simpleTypes.clear()
            }
            simpleTypes.addAll(types)
        }
    }

    override fun withCollectionTypes(types: Set<KClass<*>>, replace: Boolean): Context.Builder {
        return apply {
            if (replace) {
                collectionTypes.clear()
            }
            collectionTypes += types
        }
    }

    override fun withTypeConverter(replace: Boolean, converter: (Any, KClass<*>) -> Any?): Context.Builder {
        return apply {
            typeConverter = wrapTypeConverter(if (replace) {
                converter
            } else {
                { value, targetType ->
                    DEFAULT_TYPE_CONVERTER(value, targetType) ?: converter(value, targetType)
                }
            })
        }
    }

    private fun wrapTypeConverter(converter: (Any, KClass<*>) -> Any?): (Any, KClass<*>) -> Any {
        return { value, targetType ->
            val result = converter(value, targetType)
            if (result != null && targetType.isInstance(result)) {
                result
            } else if (targetType.isInstance(value)) {
                value
            } else {
                throw IllegalArgumentException(
                        "can't convert value '$value' of type '${value::class.qualifiedName}' "
                        + "to type '${targetType.qualifiedName}'"
                )
            }
        }
    }

    override fun withRegularPropertyNameStrategy(strategy: (String, String) -> String): Context.Builder {
        return apply {
            regularPropertyNameStrategy = strategy
        }
    }

    override fun withCollectionCreator(
            replace: Boolean,
            creator: (KClass<*>) -> MutableCollection<Any>?
    ): Context.Builder {
        return apply {
            collectionCreator = if (replace) {
                wrapCollectionCreator(creator)
            } else {
                wrapCollectionCreator {
                    DEFAULT_COLLECTION_CREATOR(it) ?: creator(it)
                }
            }
        }
    }

    private fun wrapCollectionCreator(
            creator: (KClass<*>) -> MutableCollection<Any>?
    ): (KClass<*>) -> MutableCollection<Any> {
        return {
            val result = creator(it)
            if (result != null && it.isInstance(result)) {
                result
            } else {
                throw IllegalArgumentException(
                        "Failed creating a collection of type '${it.qualifiedName}'"
                )
            }
        }
    }

    override fun withCollectionElementPropertyNameStrategy(strategy: (String, Int) -> String): Context.Builder {
        return apply {
            collectionPropertyNameStrategy = strategy
        }
    }

    override fun withMapCreator(creator: () -> MutableMap<Any, Any>): Context.Builder {
        return apply {
            mapCreator = creator
        }
    }

    override fun withMapKeyStrategy(strategy: (String, KType) -> Set<String>): Context.Builder {
        return apply {
            mapKeyStrategy = strategy
        }
    }

    override fun withMapValuePropertyNameStrategy(strategy: (String, String) -> String): Context.Builder {
        return apply {
            mapValuePropertyNameStrategy = strategy
        }
    }

    override fun build(): Context {
        return ContextImpl(
                dataProvider = dataProvider,
                typeConverter = typeConverter,
                regularPropertyNameStrategy = regularPropertyNameStrategy,
                collectionCreator = collectionCreator,
                collectionPropertyNameStrategy = collectionPropertyNameStrategy,
                simpleTypes = simpleTypes,
                collectionTypes = collectionTypes,
                mapCreator = mapCreator,
                mapKeyStrategy = mapKeyStrategy,
                mapPropertyNameStrategy = mapValuePropertyNameStrategy
        )
    }

    companion object {
        val DEFAULT_SIMPLE_TYPES = setOf<KClass<*>>(
                Boolean::class, Short::class, Char::class, Int::class, Long::class, Float::class, Double::class,
                String::class, ZoneId::class
        )

        val DEFAULT_COLLECTION_TYPES: Set<KClass<*>> = setOf(
                List::class, Set::class, Collection::class, Iterable::class
        )

        val DEFAULT_COLLECTION_CREATOR: (KClass<*>) -> MutableCollection<Any>? = { klass ->
            when {
                List::class.isSuperclassOf(klass) -> mutableListOf()
                Set::class.isSuperclassOf(klass) -> mutableSetOf()
                Collection::class.isSuperclassOf(klass) -> mutableListOf()
                Iterable::class.isSuperclassOf(klass) -> mutableListOf()
                else -> null
            }
        }

        val DEFAULT_COLLECTION_ELEMENT_PROPERTY_NAME_STRATEGY: (String, Int) -> String = { baseName, index ->
            "$baseName[$index]"
        }

        val DEFAULT_REGULAR_PROPERTY_NAME_STRATEGY: (String, String) -> String = { baseName, propertyName ->
            if (baseName.isBlank()) {
                propertyName
            } else {
                "$baseName.$propertyName"
            }
        }

        val DEFAULT_TYPE_CONVERTER: (Any, KClass<*>) -> Any? = { rawValue, targetClass ->
            if (targetClass.isInstance(rawValue)) {
                rawValue
            } else {
                val trimmedValue = rawValue.toString().trim()
                when (targetClass) {
                    Boolean::class -> when {
                        trimmedValue.equals("true", true) -> true
                        trimmedValue.equals("false", true) -> false
                        else -> throw IllegalArgumentException(
                                "can't convert value '$trimmedValue' of type '${trimmedValue::class.qualifiedName}' "
                                + "to type '${targetClass.qualifiedName}'"
                        )
                    }
                    Short::class -> trimmedValue.toShort()
                    Char::class -> if (trimmedValue.length == 1) {
                        trimmedValue[0]
                    } else {
                        throw IllegalArgumentException(
                                "can't convert value '$trimmedValue' of type '${trimmedValue::class.qualifiedName}' "
                                + "to type '${targetClass.qualifiedName}'"
                        )
                    }
                    Int::class -> trimmedValue.toInt()
                    Long::class -> trimmedValue.toLong()
                    Float::class -> trimmedValue.toFloat()
                    Double::class -> trimmedValue.toDouble()
                    ZoneId::class -> ZoneId.of(rawValue.toString())
                    else -> null
                }
            }
        }

        val DEFAULT_MAP_CREATOR: () -> MutableMap<Any, Any> = {
            mutableMapOf()
        }

        val DEFAULT_MAP_KEY_STRATEGY: (String, KType) -> Set<String> = { _, _ ->
            emptySet()
        }
    }
}