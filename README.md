[![Maven Central](https://img.shields.io/maven-central/v/tech.harmonysoft/kotlin-baker)](https://img.shields.io/maven-central/v/tech.harmonysoft/kotlin-baker)
[![Build Status](https://travis-ci.org/denis-zhdanov/kotlin-baker.svg?branch=master)](https://travis-ci.org/denis-zhdanov/kotlin-baker)

## Tl;DR

Create Kotlin data classes in a highly customizable way from Spring Cloud Config.

## Description

[Spring Cloud Config](https://spring.io/projects/spring-cloud-config) is a great way to manage application configuration. Kotlin is one of the best JVM languages, but there is a gap between that two - even though Spring provides `@ConfiugrationProperties`, they don't really work with [data classes](https://kotlinlang.org/docs/reference/data-classes.html).

Another concern is that it's hard to achieve hierarchical configuration with collection/map properties.

*Kotlin Baker* library facilitates data classes creation with highly customizable setup and support for hierarchical configuration.

## Example

Consider that we have the following domain:

```kotlin
data class Tasks(val tasks: Map<String, TaskConfig>)

data class TaskConfig(val id: String, val properties: Map<String, Any>)
``` 

We can create configs hierarchy for it:
* [common config (for all profiles)](example/repo/my-app/my-app.yml)
* [common production config (for all production environments)](example/repo/my-app/production/my-app-production.yml)
* [production profile-specific config](example/repo/my-app/APAC/my-app-APAC.yml)

Now we can configure our domain class in Spring using *Kotlin Baker*:

```kotlin
@Bean
open fun tasks(creator: KotlinCreator, context: Context): Tasks {
    return creator.create("", Tasks::class.createType(), context)
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
```

When Spring context with profiles *production,APAC* is initialized, we can see that
* two tasks are picked up from the configuration in runtime
* *APAC* profile config values are preferred to *common production* values and *common* values
* *common production* values are preferred to *common* values

*Note: the last declared profile has higher precedence, i.e. if we want 'APAC' values to override 'common production' values, we need to define profiles like 'production,APAC'. Feel free to read more about that in [Spring documentation](https://cloud.spring.io/spring-cloud-config/multi/multi__spring_cloud_config_server.html)*

The example above can be found [here](example):
1. Launch [ServerApplication](example/server/src/main/kotlin/tech/harmonysoft/oss/kotlin/baker/example/server/ServerApplication.kt)
2. Launch [ClientApplication](example/client/src/main/kotlin/tech/harmonysoft/oss/kotlin/baker/example/client/ClientApplication.kt)
3. Check that client application reports the following domain class setup:
```
Found the following tasks: Tasks(tasks={task2=TaskConfig(id=task2, properties={prop2=apacTask2Prop2, prop1=commonTask2Prop1}), task1=TaskConfig(id=task1, properties={prop2=productionTask1Prop2, prop1=commonTask1Prop1})})
```

## Releases

[Release Notes](RELEASE.md)

## How to Contribute

* [report a problem/ask for enhancement](https://github.com/denis-zhdanov/kotlin-baker/issues)
* [submit a pull request](https://github.com/denis-zhdanov/kotlin-baker/pulls)