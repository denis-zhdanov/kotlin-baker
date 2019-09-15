package tech.harmonysoft.oss.kotlin.baker.impl

import tech.harmonysoft.oss.kotlin.baker.Context
import tech.harmonysoft.oss.kotlin.baker.KotlinCreator
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KType

class CacheAwareCreator : KotlinCreator {

    private val cache = ConcurrentHashMap<KType, ClassSpecificCreator<Any>>()

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> create(prefix: String, type: KType, context: Context): T {
        return cache.computeIfAbsent(type) {
            ClassSpecificCreator(type)
        }.create(prefix, this, context) as T
    }
}