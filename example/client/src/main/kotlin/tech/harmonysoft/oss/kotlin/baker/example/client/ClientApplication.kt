package tech.harmonysoft.oss.kotlin.baker.example.client

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment
import org.springframework.core.env.get
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
    open fun servers(environment: Environment): Servers {
        val creator = CacheAwareCreator()
        val context = Context.builder {
            environment[it]
        }.build()
        return creator.create("", Servers::class.createType(), context)
    }
}