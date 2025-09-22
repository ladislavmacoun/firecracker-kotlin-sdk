/*
 * Firecracker Kotlin/Java SDK
 *
 * A Kotlin-first SDK for interacting with Firecracker microVMs,
 * with full Java interoperability.
 *
 * Author: Ladislav Macoun <lada@macoun.dev>
 */

plugins {
    // Apply the Kotlin JVM plugin for Kotlin support
    alias(libs.plugins.kotlin.jvm)

    // Apply Kotlinx Serialization plugin for JSON support
    alias(libs.plugins.kotlin.serialization)

    // Apply the java-library plugin for API and implementation separation
    `java-library`

    // Apply code quality plugins
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)

    // Apply Maven publishing for distribution
    `maven-publish`
}

repositories {
    // Use Maven Central for resolving dependencies
    mavenCentral()
}

dependencies {
    // HTTP Client and Serialization
    api(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    api(libs.kotlinx.serialization.json)

    // Coroutines
    api(libs.kotlinx.coroutines.core)

    // Unix Domain Socket Support
    implementation(libs.jnr.unixsocket)

    // Logging
    implementation(libs.kotlin.logging)
    implementation(libs.logback.classic)

    // Testing Dependencies
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.kotest.assertions.json)
    testImplementation(libs.kotest.property)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.testcontainers)

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Configure Java toolchain
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }

    // Generate Javadoc and sources JARs
    withJavadocJar()
    withSourcesJar()
}

// Configure Kotlin compilation
kotlin {
    jvmToolchain(21)

    compilerOptions {
        // Temporarily disable explicit API mode for rapid development
        // explicitApi = org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode.Strict

        // Additional compiler options
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
        )
    }
}

// Configure testing
tasks.named<Test>("test") {
    useJUnitPlatform()

    // Configure test execution
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }

    // Set system properties for tests
    systemProperty("firecracker.test.timeout", "30000")
}

// Configure Detekt
detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("$projectDir/config/detekt/detekt.yml")
}

// Configure KtLint
ktlint {
    version.set("1.0.1")
    verbose.set(true)
    android.set(false)
    outputToConsole.set(true)
    coloredOutput.set(true)

    filter {
        exclude("**/generated/**")
    }
}

// Configure Maven publishing
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            artifactId = "firecracker-kotlin-sdk"

            pom {
                name.set("Firecracker Kotlin SDK")
                description.set("A Kotlin-first SDK for interacting with Firecracker microVMs")
                url.set("https://github.com/ladislavmacoun/firecracker-kotlin-sdk")

                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }

                developers {
                    developer {
                        id.set("lada-macoun")
                        name.set("Ladislav Macoun")
                        email.set("lada@macoun.dev")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/ladislavmacoun/firecracker-kotlin-sdk.git")
                    developerConnection.set("scm:git:ssh://github.com/ladislavmacoun/firecracker-kotlin-sdk.git")
                    url.set("https://github.com/ladislavmacoun/firecracker-kotlin-sdk")
                }
            }
        }
    }
}
