package org.firecracker.sdk.models

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LoggerTest : DescribeSpec({
    val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    describe("Logger") {
        describe("creation") {
            it("should create logger with required fields") {
                val logger =
                    Logger(
                        logPath = "/tmp/firecracker.log",
                        level = LogLevel.Info,
                    )

                logger.logPath shouldBe "/tmp/firecracker.log"
                logger.level shouldBe LogLevel.Info
                logger.showLevel shouldBe false
                logger.showLogOrigin shouldBe false
            }

            it("should create logger with all fields") {
                val logger =
                    Logger(
                        logPath = "/var/log/firecracker.log",
                        level = LogLevel.Debug,
                        showLevel = true,
                        showLogOrigin = true,
                    )

                logger.logPath shouldBe "/var/log/firecracker.log"
                logger.level shouldBe LogLevel.Debug
                logger.showLevel shouldBe true
                logger.showLogOrigin shouldBe true
            }

            it("should create logger with factory method") {
                val logger = Logger.create("/tmp/app.log")

                logger.logPath shouldBe "/tmp/app.log"
                logger.level shouldBe LogLevel.Warn
                logger.showLevel shouldBe false
                logger.showLogOrigin shouldBe false
            }

            it("should create debug logger") {
                val logger = Logger.debug("/tmp/debug.log")

                logger.logPath shouldBe "/tmp/debug.log"
                logger.level shouldBe LogLevel.Debug
                logger.showLevel shouldBe true
                logger.showLogOrigin shouldBe true
            }

            it("should create trace logger") {
                val logger = Logger.trace("/tmp/trace.log")

                logger.logPath shouldBe "/tmp/trace.log"
                logger.level shouldBe LogLevel.Trace
                logger.showLevel shouldBe true
                logger.showLogOrigin shouldBe true
            }

            it("should create production logger") {
                val logger = Logger.production("/var/log/prod.log")

                logger.logPath shouldBe "/var/log/prod.log"
                logger.level shouldBe LogLevel.Error
                logger.showLevel shouldBe false
                logger.showLogOrigin shouldBe false
            }
        }

        describe("validation") {
            it("should reject blank log path") {
                shouldThrow<IllegalArgumentException> {
                    Logger(logPath = "")
                }
            }

            it("should reject whitespace-only log path") {
                shouldThrow<IllegalArgumentException> {
                    Logger(logPath = "   ")
                }
            }
        }

        describe("serialization") {
            it("should serialize logger with defaults correctly") {
                val logger = Logger.create("/tmp/firecracker.log")
                val jsonString = json.encodeToString(logger)
                val expectedJson = """
                    {
                        "log_path": "/tmp/firecracker.log",
                        "level": "Warn",
                        "show_level": false,
                        "show_log_origin": false
                    }
                """

                jsonString shouldEqualJson expectedJson
            }

            it("should serialize complete logger to JSON") {
                val logger =
                    Logger(
                        logPath = "/var/log/firecracker.log",
                        level = LogLevel.Debug,
                        showLevel = true,
                        showLogOrigin = true,
                    )
                val jsonString = json.encodeToString(logger)
                val expectedJson = """
                    {
                        "log_path": "/var/log/firecracker.log",
                        "level": "Debug",
                        "show_level": true,
                        "show_log_origin": true
                    }
                """

                jsonString shouldEqualJson expectedJson
            }

            it("should deserialize from JSON correctly") {
                val jsonString = """
                    {
                        "log_path": "/tmp/test.log",
                        "level": "Info",
                        "show_level": true,
                        "show_log_origin": false
                    }
                """

                val logger = json.decodeFromString<Logger>(jsonString)

                logger.logPath shouldBe "/tmp/test.log"
                logger.level shouldBe LogLevel.Info
                logger.showLevel shouldBe true
                logger.showLogOrigin shouldBe false
            }
        }
    }

    describe("LogLevel") {
        it("should serialize correctly") {
            json.encodeToString(LogLevel.Error) shouldBe "\"Error\""
            json.encodeToString(LogLevel.Warn) shouldBe "\"Warn\""
            json.encodeToString(LogLevel.Info) shouldBe "\"Info\""
            json.encodeToString(LogLevel.Debug) shouldBe "\"Debug\""
            json.encodeToString(LogLevel.Trace) shouldBe "\"Trace\""
        }
    }
})
