package tech.harmonysoft.oss.kotlin.baker.impl

import tech.harmonysoft.oss.kotlin.baker.Context
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.isSuperclassOf

class ContextImpl(
        private val dataProvider: (String) -> Any?,
        private val typeConverter: (Any, KClass<*>) -> Any,
        private val regularPropertyNameStrategy: (String, String) -> String,
        private val collectionCreator: (KClass<*>) -> MutableCollection<Any>,
        private val collectionPropertyNameStrategy: (String, Int) -> String,
        private val simpleTypes: Set<KClass<*>>,
        private val collectionTypes: Set<KClass<*>>,
        private val mapCreator: () -> MutableMap<Any, Any>,
        private val mapKeyStrategy: (String, KType) -> Set<String>,
        private val mapPropertyNameStrategy: (String, String) -> String
) : Context {

    private val _tolerateEmptyCollection = ThreadLocal.withInitial { Stack<Boolean>().apply { push(true) } }

    private val _hasMandatoryParameter = ThreadLocal.withInitial { Stack<Boolean>().apply { push(false) } }

    override val tolerateEmptyCollection: Boolean
        get() {
            return _tolerateEmptyCollection.get().peek()
        }

    override val hasMandatoryParameter: Boolean
        get() {
            return _hasMandatoryParameter.get().peek()
        }

    override fun <T> withTolerateEmptyCollection(value: Boolean, action: () -> T): T {
        _tolerateEmptyCollection.get().push(value)
        return try {
            action()
        } finally {
            _tolerateEmptyCollection.get().pop()
        }
    }

    override fun <T> withMandatoryParameter(value: Boolean, action: () -> T): T {
        _hasMandatoryParameter.get().push(value)
        return try {
            action()
        } finally {
            _hasMandatoryParameter.get().pop()
        }
    }

    override fun isSimpleType(klass: KClass<*>): Boolean {
        return simpleTypes.any {
            it.isSuperclassOf(klass)
        }
    }

    override fun isCollection(klass: KClass<*>): Boolean {
        return collectionTypes.contains(klass)
    }

    override fun convertIfNecessary(value: Any, klass: KClass<*>): Any {
        return typeConverter(value, klass)
    }

    override fun createCollection(klass: KClass<*>): MutableCollection<Any> {
        return collectionCreator(klass)
    }

    override fun getRegularPropertyName(base: String, propertyName: String): String {
        return regularPropertyNameStrategy(base, propertyName)
    }

    override fun getCollectionElementPropertyName(base: String, index: Int): String {
        return collectionPropertyNameStrategy(base, index)
    }

    override fun getPropertyValue(propertyName: String): Any? {
        return dataProvider(propertyName)
    }

    override fun getMapKeys(mapPropertyName: String, keyType: KType): Set<String> {
        return mapKeyStrategy(mapPropertyName, keyType)
    }

    override fun createMap(): MutableMap<Any, Any> {
        return mapCreator()
    }

    override fun getMapValuePropertyName(base: String, key: String): String {
        return mapPropertyNameStrategy(base, key)
    }
}