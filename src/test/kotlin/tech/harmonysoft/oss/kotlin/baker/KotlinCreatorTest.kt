package tech.harmonysoft.oss.kotlin.baker

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.harmonysoft.oss.kotlin.baker.impl.CacheAwareCreator
import tech.harmonysoft.oss.kotlin.baker.impl.enumConverter
import tech.harmonysoft.oss.kotlin.baker.impl.enumKeyProducer
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import kotlin.reflect.KClass
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSuperclassOf

internal class KotlinCreatorTest {

    private lateinit var creator: KotlinCreator

    @BeforeEach
    fun setUp() {
        creator = CacheAwareCreator()
    }

    @Test
    fun `when data class with single constructor is targeted then it's used`() {
        data class Target(val prop: Int)
        val result = doCreate(Target::class, mapOf("prop" to 1))
        assertThat(result.prop).isEqualTo(1)
    }

    @Test
    fun `when regular class with single constructor is targeted the it's used`() {
        class Target(val prop: Int)
        val result = doCreate(Target::class, mapOf("prop" to 1))
        assertThat(result.prop).isEqualTo(1)
    }

    @Test
    fun `when there is no property value for parameter with default value in a data class then default value is used`() {
        data class Target(val prop: Int = 1)
        val result = doCreate(Target::class, emptyMap())
        assertThat(result.prop).isEqualTo(1)
    }

    @Test
    fun `when there is no property value for parameter with default value in a regular class then default value is used`() {
        class Target(val prop: Int = 1)
        val result = doCreate(Target::class, emptyMap())
        assertThat(result.prop).isEqualTo(1)
    }

    @Test
    fun `when there is a property value for parameter with default value in a data class then it overrides default value`() {
        data class Target(val prop: Int = 1)
        val result = doCreate(Target::class, mapOf("prop" to 2))
        assertThat(result.prop).isEqualTo(2)
    }

    @Test
    fun `when there is a property value for parameter with default value in a regular class then it overrides default value`() {
        class Target(val prop: Int = 1)
        val result = doCreate(Target::class, mapOf("prop" to 2))
        assertThat(result.prop).isEqualTo(2)
    }

    @Test
    fun `when there is no property value for a nullable parameter in a data class then null is used`() {
        data class Target(val prop: Int?)
        val result = doCreate(Target::class, emptyMap())
        assertThat(result.prop).isNull()
    }

    @Test
    fun `when there is no property value for a nullable parameter in a regular class then null is used`() {
        class Target(val prop: Int?)
        val result = doCreate(Target::class, emptyMap())
        assertThat(result.prop).isNull()
    }

    @Test
    fun `when there is a non-primitive parameter in a data class then it's correctly instantiated`() {
        data class Inner(val prop: Int)
        data class Outer(val inner: Inner)
        val result = doCreate(Outer::class, mapOf("inner.prop" to 1))
        assertThat(result.inner.prop).isEqualTo(1)
    }

    @Test
    fun `when Boolean property has a correct value then it's supported`() {
        class Target(val prop: Boolean?)
        val result = doCreate(Target::class, mapOf("prop" to "true"))
        assertThat(result.prop).isEqualTo(true)
    }

    @Suppress("unused")
    @Test
    fun `when Boolean property value is neither 'false' nor 'true' then it's reported`() {
        class Target(val prop: Boolean?)
        assertThrows<IllegalArgumentException> {
            doCreate(Target::class, mapOf("prop" to "trues"))
        }
    }

    @Test
    fun `when Char property has a correct value then it's supported`() {
        class Target(val prop: Char?)
        val result = doCreate(Target::class, mapOf("prop" to "a"))
        assertThat(result.prop).isEqualTo('a')
    }

    @Suppress("unused")
    @Test
    fun `when Char property value is a string with more than one character then it's reported`() {
        class Target(val prop: Char?)
        assertThrows<IllegalArgumentException> {
            doCreate(Target::class, mapOf("prop" to "ab"))
        }
    }

    @Test
    fun `when Short property has a correct value then it's supported`() {
        class Target(val prop: Short?)
        val result = doCreate(Target::class, mapOf("prop" to "1"))
        assertThat(result.prop).isEqualTo(1.toShort())
    }

    @Suppress("unused")
    @Test
    fun `when Short property value can't be parsed it's reported`() {
        class Target(val prop: Short?)
        assertThrows<IllegalArgumentException> {
            doCreate(Target::class, mapOf("prop" to "1b"))
        }
    }

    @Test
    fun `when Int property has a correct value then it's supported`() {
        class Target(val prop: Int?)
        val result = doCreate(Target::class, mapOf("prop" to "1"))
        assertThat(result.prop).isEqualTo(1)
    }

    @Suppress("unused")
    @Test
    fun `when Int property value can't be parsed it's reported`() {
        class Target(val prop: Int?)
        assertThrows<IllegalArgumentException> {
            doCreate(Target::class, mapOf("prop" to "1b"))
        }
    }

    @Test
    fun `when Long property has a correct value then it's supported`() {
        class Target(val prop: Long?)
        val result = doCreate(Target::class, mapOf("prop" to "1"))
        assertThat(result.prop).isEqualTo(1L)
    }

    @Suppress("unused")
    @Test
    fun `when Long property value can't be parsed it's reported`() {
        class Target(val prop: Long?)
        assertThrows<IllegalArgumentException> {
            doCreate(Target::class, mapOf("prop" to "1b"))
        }
    }

    @Test
    fun `when Float property has a correct value then it's supported`() {
        class Target(val prop: Float?)
        val result = doCreate(Target::class, mapOf("prop" to "1.2"))
        assertThat(result.prop).isEqualTo(1.2f)
    }

    @Suppress("unused")
    @Test
    fun `when Float property value can't be parsed it's reported`() {
        class Target(val prop: Float?)
        assertThrows<IllegalArgumentException> {
            doCreate(Target::class, mapOf("prop" to "1.r"))
        }
    }

    @Test
    fun `when Double property has a correct value then it's supported`() {
        class Target(val prop: Double?)
        val result = doCreate(Target::class, mapOf("prop" to "1.2"))
        assertThat(result.prop).isEqualTo(1.2)
    }

    @Suppress("unused")
    @Test
    fun `when Double property value can't be parsed it's reported`() {
        class Target(val prop: Double?)
        assertThrows<IllegalArgumentException> {
            doCreate(Target::class, mapOf("prop" to "1.r"))
        }
    }

    @Test
    fun `when List property of simple values is used then it's correctly applied`() {
        data class Target(val prop: List<Int>)
        val result = doCreate(Target::class, mapOf(
                "prop[0]" to "1",
                "prop[1]" to 2,
                "prop[2]" to '3'
        ))
        assertThat(result.prop).containsOnly(1, 2, 3)
    }

    @Test
    fun `when Set property of simple values is used then it's correctly applied`() {
        data class Target(val prop: Set<Int>)
        val result = doCreate(Target::class, mapOf(
                "prop[0]" to "1",
                "prop[1]" to 2,
                "prop[2]" to '3'
        ))
        assertThat(result.prop).containsOnly(1, 2, 3)
    }

    @Test
    fun `when List property of non-simple values is used then it's correctly applied`() {
        val result = doCreate(NonSimpleTypeListHolder::class, mapOf(
                "prop[0].value" to 1,
                "prop[1].value" to "2",
                "prop[2].value" to '3'
        ))
        assertThat(result.prop).containsOnly(ListElement(1), ListElement(2), ListElement(3))
    }

    @Test
    fun `when composite structure with non-simple type is defined then it's correctly created`() {
        val result = doCreate(CompositeNonSimpleListHolder::class, mapOf(
                "prop2[0].prop[0].value" to 1,
                "prop2[0].prop[1].value" to 2,
                "prop2[1].prop[0].value" to "3"
        ))
        assertThat(result).isEqualTo(CompositeNonSimpleListHolder(listOf(
                NonSimpleTypeListHolder(listOf(
                        ListElement(1),
                        ListElement(2)
                )),
                NonSimpleTypeListHolder(listOf(ListElement(3)))
        )))
    }

    @Test
    fun `when composite structure with simple type is defined then it's correctly created`() {
        val result = doCreate(CompositeSimpleListHolder::class, mapOf(
                "prop[0].prop[0]" to 1,
                "prop[0].prop[1]" to 2,
                "prop[1].prop[0]" to "3"
        ))
        assertThat(result).isEqualTo(CompositeSimpleListHolder(listOf(
                SimpleTypeListHolder(listOf(1, 2)),
                SimpleTypeListHolder(listOf(3))
        )))
    }

    @Test
    fun `when custom property name strategy is defined then it's respected`() {
        val input = mapOf(
                "prop[0]-value" to '1',
                "prop[1]-value" to "2"
        )
        val context = Context.builder {
            input[it]
        }.withRegularPropertyNameStrategy { base, propertyName ->
            if (base.isBlank()) {
                propertyName
            } else {
                "$base-$propertyName"
            }
        }.build()
        val actual = creator.create<NonSimpleTypeListHolder>(
                "", NonSimpleTypeListHolder::class.createType(), context
        )
        assertThat(actual).isEqualTo(NonSimpleTypeListHolder(listOf(
                ListElement(1), ListElement(2)
        )))
    }

    @Test
    fun `when custom simple type is defined then it's respected`() {
        data class Element(val value: Int)
        data class Composite(val first: Element, val second: Element)
        val input = mapOf(
                "first" to "1",
                "second" to "2"
        )
        val context = Context.builder {
            input[it]
        }.withTypeConverter { value, targetType ->
            if (targetType == Element::class) {
                Element(value.toString().toInt())
            } else {
                null
            }
        }.withSimpleTypes(setOf(Element::class)).build()

        val actual = creator.create<Composite>("", Composite::class.createType(), context)
        assertThat(actual).isEqualTo(Composite(Element(1), Element(2)))
    }

    @Test
    fun `when custom collection type is defined then it's respected`() {
        data class Target(val queue: BlockingQueue<Int>)
        val input = mapOf(
                "queue[0]" to "1",
                "queue[1]" to "2"
        )
        val context = Context.builder {
            input[it]
        }.withCollectionCreator { collectionClass ->
            if (BlockingQueue::class.isSuperclassOf(collectionClass)) {
                LinkedBlockingQueue()
            } else {
                null
            }
        }.withCollectionTypes(setOf(BlockingQueue::class)).build()

        val actual = creator.create<Target>("", Target::class.createType(), context)
        assertThat(actual.queue).containsOnly(1, 2)
    }

    @Test
    fun `when custom collection property name strategy is defined then it's respected`() {
        val input = mapOf(
                "prop<1>.value" to "1",
                "prop<2>.value" to "2"
        )
        val context = Context
                .builder { input[it] }
                .withCollectionElementPropertyNameStrategy { base, index ->  "$base<${index + 1}>"}
                .build()
        val actual = creator.create<NonSimpleTypeListHolder>(
                "", NonSimpleTypeListHolder::class.createType(), context
        )
        assertThat(actual).isEqualTo(NonSimpleTypeListHolder(listOf(ListElement(1), ListElement(2))))
    }

    @Test
    fun `when map property is declared then it's correctly populated`() {
        val input = mapOf(
                "prop.FIRST.prop.SECOND" to "1",
                "prop.SECOND.prop.FIRST" to "2",
                "prop.SECOND.prop.SECOND" to "3"
        )
        val context = Context
                .builder { input[it] }
                .withTypeConverter(false, enumConverter(enumValues<Key>()))
                .withMapKeyStrategy(enumKeyProducer<Key>())
                .build()
        val actual = creator.create<Any>("", CompositeMapHolder::class.createType(), context)
        assertThat(actual).isEqualTo(CompositeMapHolder(mapOf(
                Key.FIRST to MapHolder(mapOf(Key.SECOND to 1)),
                Key.SECOND to MapHolder(mapOf(Key.FIRST to 2, Key.SECOND to 3))
        )))
    }

    private fun <T : Any> doCreate(klass: KClass<T>, data: Map<String, Any>): T {
        return creator.create("", klass.createType(), Context.builder { data[it] }.build())
    }

    // We define this classes not in corresponding methods because of https://youtrack.jetbrains.com/issue/KT-10397
    // - getting an exception during test execution otherwise

    enum class Key { FIRST, SECOND }

    data class ListElement(val value: Int)

    data class NonSimpleTypeListHolder(val prop: List<ListElement>)

    data class CompositeNonSimpleListHolder(val prop2: List<NonSimpleTypeListHolder>)

    data class SimpleTypeListHolder(val prop: List<Int>)

    data class CompositeSimpleListHolder(val prop: List<SimpleTypeListHolder>)

    data class MapHolder(val prop: Map<Key, Int>)

    data class CompositeMapHolder(val prop: Map<Key, MapHolder>)
}