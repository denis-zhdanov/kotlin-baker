package tech.harmonysoft.oss.kotlin.baker

import tech.harmonysoft.oss.kotlin.baker.impl.ContextBuilderImpl
import kotlin.reflect.KClass
import kotlin.reflect.KType

interface Context {

    /**
     * Allows answering if given type is a *simple type* like [Int], [Long] etc.
     *
     * Properties of that types are processed in a *just parse it* way:
     * 1. [Pick up the property by name][getRegularPropertyName]
     * 2. [Normalize the value][convertIfNecessary] (e.g. from *String* to *Int*)
     */
    fun isSimpleType(klass: KClass<*>): Boolean

    /**
     * Allows answering if given type is a *collection type*, like [List], [Set] etc.
     *
     * Properties of that type are processed using the following approach:
     * 1. [Create target collection][createCollection]
     * 2. Try looking up collection elements using [collection property names][getCollectionElementPropertyName]
     * with index starting from 0. It's assumed that [tolerateEmptyCollection] is `false` during that.
     * So, as soon as a lookup for the given index fails, we assume that all collection elements were discovered
     */
    fun isCollection(klass: KClass<*>): Boolean

    /**
     * Suppose we have a class declared like `data class ListHolder(val list: List<Int>)`. As explained in
     * [isCollection], we start from index `0` and while
     * [an element at the target index][getCollectionElementPropertyName] is found, add it to the processing.
     * So, we stop as soon as there is no element for the target index.
     *
     * Consider that we have a class `data class CompositeListHolder(val list: List<ListHolder>)` - we don't know
     * how many `ListHolder` elements are in the target collection, so, we start from index `0` and increment it.
     * The thing is that we need to look up a list of `Int` to create `ListHolder` and its perfectly legal
     * to have a `ListHolder` with an empty collection. Hence, here we need a way to understand if we found
     * all `ListHolder` elements for `CompositeListHolder`. A stop criteria is a situation when `ListHolder`
     * contains an empty collection.
     *
     * Summarizing, this class defines if an empty collection is considered an 'ok scenario' or 'no data scenario'.
     */
    val tolerateEmptyCollection: Boolean

    /**
     * A strategy for building property names. Consider the following situation:
     * * `data class Inner(val innerProp: Int)`
     * * `data class Outer(val outerProp: Inner)`
     *
     * Suppose that we initially start with an empty 'base path'. Then it's expected to find an `Int` value
     * in property *'outerProp.innerProp'* (default property name strategy).
     *
     * @param base          base property path (*outerProp* in the example above)
     * @param propertyName  target property name (*innerProp* in the example above)
     */
    fun getRegularPropertyName(base: String, propertyName: String): String

    /**
     * A strategy for building collection property names. As explained in [tolerateEmptyCollection] documentation,
     * we start from index `0` and try looking up collection element for the target index.
     *
     * E.g. to build and instance of `data class ListHolder(val prop: List<ListElement>)` (where `ListElement`
     * is declared like `data class ListElement(val value: Int)`), we start from index `0`,
     * so, by default property name `prop[0].value` is used for [looking up property value][getPropertyValue],
     * then property name `prop[1].value` for index `1` etc.
     *
     * This strategy allows building property name for the target index.
     */
    fun getCollectionElementPropertyName(base: String, index: Int): String

    /**
     * Executes given action in a context where [tolerateEmptyCollection] has given value.
     */
    fun <T> withTolerateEmptyCollection(value: Boolean, action: () -> T): T

    /**
     * Base mandatory method in this interface. Looks up a property value for the given property name.
     *
     * @return `null` as an indication that no value is found for the given key; non-`null` value otherwise
     */
    fun getPropertyValue(propertyName: String): Any?

    /**
     * Allows converting given 'raw value' to the target type, e.g. *String* to *Int*
     */
    fun convertIfNecessary(value: Any, klass: KClass<*>): Any

    /**
     * Creates new mutable collection of the given base type. It's assumed that this method supports
     * all types declared in [isCollection]
     */
    fun createCollection(klass: KClass<*>): MutableCollection<Any>

    /**
     * It's not possible to automatically deduce target keys for `Map` parameter
     * (e.g. `data class MyClass(val prop: Map<String, Int>)`. Hence, the only way is to configure predefined
     * keys in context and try all of them during instantiation.
     *
     * This method exposes keys configured for the target type (if any).
     */
    fun getMapKeys(mapPropertyName: String, keyType: KType): Set<String>

    /**
     * A strategy for building map value property names, e.g. if we have a declaration like
     * `data class MyClass(val prop: Map<String, Int>)` and want to check if there is a value for key `ONE`,
     * then we call this method and it might return property name `prop.ONE`.
     */
    fun getMapValuePropertyName(base: String, key: String): String

    /**
     * Creates mutable map to use for a map property.
     */
    fun createMap(): MutableMap<Any, Any>

    companion object {

        fun builder(dataProvider: (String) -> Any?): Builder {
            return ContextBuilderImpl(dataProvider)
        }
    }

    interface Builder {

        /**
         * ['Simple types'][Context.isSimpleType] to use.
         *
         * [ContextBuilderImpl.DEFAULT_SIMPLE_TYPES] are used by default.
         *
         * @param types     'simple types' to use
         * @param replace   defines if given types should be added to the default types or replace them
         */
        fun withSimpleTypes(types: Set<KClass<*>>, replace: Boolean = false): Builder

        /**
         * Strategy for converting 'raw values' to the target type, e.g. `String` to `Int`.
         *
         * [ContextBuilderImpl.DEFAULT_TYPE_CONVERTER] is used by default.
         *
         * @param replace       defines whether given converter should be used as a complement to the
         *                      built-in converter or should completely replace it
         * @param converter     converter to use
         */
        fun withTypeConverter(replace: Boolean = false, converter: (Any, KClass<*>) -> Any?): Builder

        /**
         * Strategy for building 'regular property' names (as opposed to
         * [collection property names][Context.getCollectionElementPropertyName]).
         *
         * [ContextBuilderImpl.DEFAULT_REGULAR_PROPERTY_NAME_STRATEGY] is used by default.
         */
        fun withRegularPropertyNameStrategy(strategy: (String, String) -> String): Builder

        /**
         * ['Collection types'][Context.isCollection] to use.
         *
         * [ContextBuilderImpl.DEFAULT_COLLECTION_TYPES] are used by default.
         *
         * @param types     'collection types' to use
         * @param replace   defines if given types should be added to the default types or replace them
         */
        fun withCollectionTypes(types: Set<KClass<*>>, replace: Boolean = false): Builder

        /**
         * Strategy for creating ['collection types'][Context.isCollection].
         *
         * [ContextBuilderImpl.DEFAULT_COLLECTION_CREATOR] is used by default.
         *
         * @param replace       defines if given strategy should be used only if default strategy fails creating
         *                      a collection of the target type
         * @param creator       the actual strategy; returns `null` as an indication that it doesn't know how to
         *                      create a collection of the target type
         */
        fun withCollectionCreator(
                replace: Boolean = false,
                creator: (KClass<*>) -> MutableCollection<Any>?
        ): Builder

        /**
         * Strategy for creating [collection property name][Context.getCollectionElementPropertyName].
         *
         * [ContextBuilderImpl.DEFAULT_COLLECTION_ELEMENT_PROPERTY_NAME_STRATEGY] is used by default.
         */
        fun withCollectionElementPropertyNameStrategy(strategy: (String, Int) -> String): Builder

        /**
         * Strategy for map property creator.
         *
         * [ContextBuilderImpl.DEFAULT_MAP_CREATOR] is used by default.
         */
        fun withMapCreator(creator: () -> MutableMap<Any, Any>): Builder

        /**
         * Strategy for keys to try for the target key type (see [Context.getMapKeys]).
         *
         * [ContextBuilderImpl.DEFAULT_MAP_KEY_STRATEGY] is used by default.
         *
         * @param replace       defines whether given strategy should be used as a complement to the
         *                      built-in strategy or should completely replace it
         * @param strategy      the actual strategy; returns empty set as an indication that it doesn't
         *                      know how to map target keys
         */
        fun withMapKeyStrategy(replace: Boolean = false, strategy: (String, KType) -> Set<String>): Builder

        /**
         * Strategy for map value property name strategy (see [Context.getMapValuePropertyName]).
         *
         * [ContextBuilderImpl.DEFAULT_REGULAR_PROPERTY_NAME_STRATEGY] is used by default.
         */
        fun withMapValuePropertyNameStrategy(strategy: (String, String) -> String): Builder

        fun build(): Context
    }
}