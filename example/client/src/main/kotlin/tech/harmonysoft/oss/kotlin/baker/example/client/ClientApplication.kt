package tech.harmonysoft.oss.kotlin.baker.example.client

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.core.env.*
import tech.harmonysoft.oss.kotlin.baker.Context
import tech.harmonysoft.oss.kotlin.baker.KotlinCreator
import tech.harmonysoft.oss.kotlin.baker.impl.CacheAwareCreator
import kotlin.reflect.full.createType

@EnableConfigurationProperties
@SpringBootApplication
open class ClientApplication {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            SpringApplication.run(ClientApplication::class.java)
        }
    }

    @Bean
    open fun creator(environment: Environment): KotlinCreator {
        return CacheAwareCreator()
    }

    @Bean
    open fun creatorContext(environment: Environment): Context {
        val allKeys = getAllPropertyKeys(environment)
        return Context.builder {
            environment[it]
        }.withMapKeyStrategy { baseName, _ ->
            val prefix = "$baseName."
            allKeys.mapNotNull { key ->
                if (key.startsWith(prefix)) {
                    val i = key.indexOf(".", prefix.length)
                    if (i < 0) {
                        key.substring(prefix.length)
                    } else {
                        key.substring(prefix.length, i)
                    }
                } else {
                    null
                }
            }.toSet()
        }.build()
    }

    @Bean
    open fun tasks(creator: KotlinCreator, context: Context): Tasks {
        return creator.create("", Tasks::class.createType(), context)
    }

    private fun getAllPropertyKeys(environment: Environment): Set<String> {
        return when (environment) {
            is StandardEnvironment -> environment.propertySources.flatMap { getAllPropertyKeys(it) }.toSet()
            else -> emptySet()
        }
    }

    private fun getAllPropertyKeys(source: PropertySource<*>): Set<String> {
        return when (source) {
            is CompositePropertySource -> source.propertySources.flatMap { getAllPropertyKeys(it) }.toSet()
            else -> (source.source as? Map<*, *>)?.keys?.map { it.toString() }?.toSet() ?: emptySet()
        }
    }
}