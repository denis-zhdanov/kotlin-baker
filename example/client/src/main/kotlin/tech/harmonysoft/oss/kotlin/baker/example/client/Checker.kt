package tech.harmonysoft.oss.kotlin.baker.example.client

import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

@Component
class Checker(private val tasks: Tasks) {

    @PostConstruct
    fun check() {
        println("Found the following tasks: $tasks")
    }
}