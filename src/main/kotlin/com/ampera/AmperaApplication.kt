package com.ampera

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class AmperaApplication

fun main(args: Array<String>) {
    runApplication<AmperaApplication>(*args)
}
