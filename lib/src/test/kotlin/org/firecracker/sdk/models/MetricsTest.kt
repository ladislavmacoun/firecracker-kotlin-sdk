package org.firecracker.sdk.models

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MetricsTest : DescribeSpec({
    val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    describe("Metrics") {
        describe("creation") {
            it("should create metrics with required fields") {
                val metrics = Metrics(metricsPath = "/tmp/metrics.json")

                metrics.metricsPath shouldBe "/tmp/metrics.json"
            }

            it("should create metrics with factory method") {
                val metrics = Metrics.create("/var/log/metrics.json")

                metrics.metricsPath shouldBe "/var/log/metrics.json"
            }

            it("should create metrics for VM") {
                val metrics = Metrics.forVm("test-vm", "/custom/dir")

                metrics.metricsPath shouldBe "/custom/dir/firecracker-metrics-test-vm.json"
            }

            it("should create metrics for VM with default directory") {
                val metrics = Metrics.forVm("my-vm")

                metrics.metricsPath shouldBe "/tmp/firecracker-metrics-my-vm.json"
            }

            it("should create metrics with timestamp") {
                val metrics = Metrics.withTimestamp("/var/metrics")

                metrics.metricsPath shouldContain "/var/metrics/firecracker-metrics-"
                metrics.metricsPath shouldContain ".json"
            }

            it("should create metrics with timestamp in default directory") {
                val metrics = Metrics.withTimestamp()

                metrics.metricsPath shouldContain "/tmp/firecracker-metrics-"
                metrics.metricsPath shouldContain ".json"
            }
        }

        describe("validation") {
            it("should reject blank metrics path") {
                shouldThrow<IllegalArgumentException> {
                    Metrics(metricsPath = "")
                }
            }

            it("should reject whitespace-only metrics path") {
                shouldThrow<IllegalArgumentException> {
                    Metrics(metricsPath = "   ")
                }
            }

            it("should reject blank VM ID in forVm") {
                shouldThrow<IllegalArgumentException> {
                    Metrics.forVm("")
                }
            }

            it("should reject whitespace-only VM ID in forVm") {
                shouldThrow<IllegalArgumentException> {
                    Metrics.forVm("   ")
                }
            }
        }

        describe("serialization") {
            it("should serialize metrics to JSON") {
                val metrics = Metrics.create("/tmp/metrics.json")
                val jsonString = json.encodeToString(metrics)
                val expectedJson = """
                    {
                        "metrics_path": "/tmp/metrics.json"
                    }
                """

                jsonString shouldEqualJson expectedJson
            }

            it("should deserialize from JSON correctly") {
                val jsonString = """
                    {
                        "metrics_path": "/var/log/firecracker-metrics.json"
                    }
                """

                val metrics = json.decodeFromString<Metrics>(jsonString)

                metrics.metricsPath shouldBe "/var/log/firecracker-metrics.json"
            }
        }
    }
})
