package org.firecracker.sdk.models

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BalloonTest : DescribeSpec({
    val json =
        Json {
            encodeDefaults = true
            prettyPrint = false
        }

    describe("Balloon") {
        describe("construction") {
            it("should create balloon with valid parameters") {
                val balloon =
                    Balloon(
                        amountMib = 512,
                        deflateOnOom = true,
                        statsPollingIntervalS = 10,
                    )

                balloon.amountMib shouldBe 512
                balloon.deflateOnOom shouldBe true
                balloon.statsPollingIntervalS shouldBe 10
            }

            it("should use default values") {
                val balloon = Balloon(amountMib = 256)

                balloon.amountMib shouldBe 256
                balloon.deflateOnOom shouldBe false
                balloon.statsPollingIntervalS shouldBe 0
            }

            it("should accept zero amount") {
                val balloon = Balloon(amountMib = 0)

                balloon.amountMib shouldBe 0
            }

            it("should accept zero polling interval") {
                val balloon =
                    Balloon(
                        amountMib = 100,
                        statsPollingIntervalS = 0,
                    )

                balloon.statsPollingIntervalS shouldBe 0
            }
        }

        describe("validation") {
            it("should reject negative amount") {
                shouldThrow<IllegalArgumentException> {
                    Balloon(amountMib = -1)
                }.message shouldContain "Balloon amount must be non-negative"
            }

            it("should reject negative polling interval") {
                shouldThrow<IllegalArgumentException> {
                    Balloon(
                        amountMib = 100,
                        statsPollingIntervalS = -1,
                    )
                }.message shouldContain "Statistics polling interval must be non-negative"
            }
        }

        describe("factory methods") {
            describe("create") {
                it("should create basic balloon") {
                    val balloon = Balloon.create(256)

                    balloon.amountMib shouldBe 256
                    balloon.deflateOnOom shouldBe false
                    balloon.statsPollingIntervalS shouldBe 0
                }

                it("should reject negative amount") {
                    shouldThrow<IllegalArgumentException> {
                        Balloon.create(-1)
                    }.message shouldContain "Balloon amount must be non-negative"
                }
            }

            describe("withOomProtection") {
                it("should enable OOM protection") {
                    val balloon = Balloon.withOomProtection(512)

                    balloon.amountMib shouldBe 512
                    balloon.deflateOnOom shouldBe true
                    balloon.statsPollingIntervalS shouldBe 0
                }

                it("should reject negative amount") {
                    shouldThrow<IllegalArgumentException> {
                        Balloon.withOomProtection(-1)
                    }.message shouldContain "Balloon amount must be non-negative"
                }
            }

            describe("withStatistics") {
                it("should enable statistics collection") {
                    val balloon = Balloon.withStatistics(1024, 5)

                    balloon.amountMib shouldBe 1024
                    balloon.deflateOnOom shouldBe false
                    balloon.statsPollingIntervalS shouldBe 5
                }

                it("should reject zero polling interval") {
                    shouldThrow<IllegalArgumentException> {
                        Balloon.withStatistics(256, 0)
                    }.message shouldContain "Statistics polling interval must be positive"
                }

                it("should reject negative polling interval") {
                    shouldThrow<IllegalArgumentException> {
                        Balloon.withStatistics(256, -1)
                    }.message shouldContain "Statistics polling interval must be positive"
                }

                it("should reject negative amount") {
                    shouldThrow<IllegalArgumentException> {
                        Balloon.withStatistics(-1, 10)
                    }.message shouldContain "Balloon amount must be non-negative"
                }
            }

            describe("withFullConfig") {
                it("should create fully configured balloon") {
                    val balloon =
                        Balloon.withFullConfig(
                            amountMib = 2048,
                            deflateOnOom = true,
                            pollingIntervalS = 15,
                        )

                    balloon.amountMib shouldBe 2048
                    balloon.deflateOnOom shouldBe true
                    balloon.statsPollingIntervalS shouldBe 15
                }

                it("should allow disabled statistics") {
                    val balloon =
                        Balloon.withFullConfig(
                            amountMib = 1024,
                            deflateOnOom = false,
                            pollingIntervalS = 0,
                        )

                    balloon.statsPollingIntervalS shouldBe 0
                }
            }

            describe("forOvercommit") {
                it("should create overcommit-optimized balloon") {
                    val balloon = Balloon.forOvercommit(512)

                    balloon.amountMib shouldBe 512
                    balloon.deflateOnOom shouldBe true
                    balloon.statsPollingIntervalS shouldBe 5
                }

                it("should reject negative amount") {
                    shouldThrow<IllegalArgumentException> {
                        Balloon.forOvercommit(-1)
                    }.message shouldContain "Balloon amount must be non-negative"
                }
            }

            describe("empty") {
                it("should create empty balloon") {
                    val balloon = Balloon.empty()

                    balloon.amountMib shouldBe 0
                    balloon.deflateOnOom shouldBe false
                    balloon.statsPollingIntervalS shouldBe 0
                }
            }
        }

        describe("serialization") {
            it("should serialize basic balloon correctly") {
                val balloon = Balloon.create(256)
                val jsonString = json.encodeToString(balloon)

                jsonString shouldEqualJson """
                {
                    "amount_mib": 256,
                    "deflate_on_oom": false,
                    "stats_polling_interval_s": 0
                }
                """
            }

            it("should serialize full balloon configuration") {
                val balloon =
                    Balloon.withFullConfig(
                        amountMib = 1024,
                        deflateOnOom = true,
                        pollingIntervalS = 10,
                    )
                val jsonString = json.encodeToString(balloon)

                jsonString shouldEqualJson """
                {
                    "amount_mib": 1024,
                    "deflate_on_oom": true,
                    "stats_polling_interval_s": 10
                }
                """
            }

            it("should deserialize balloon correctly") {
                val jsonString = """
                {
                    "amount_mib": 512,
                    "deflate_on_oom": true,
                    "stats_polling_interval_s": 5
                }
                """
                val balloon = json.decodeFromString<Balloon>(jsonString)

                balloon.amountMib shouldBe 512
                balloon.deflateOnOom shouldBe true
                balloon.statsPollingIntervalS shouldBe 5
            }

            it("should handle missing optional fields") {
                val jsonString = """
                {
                    "amount_mib": 256
                }
                """
                val balloon = json.decodeFromString<Balloon>(jsonString)

                balloon.amountMib shouldBe 256
                balloon.deflateOnOom shouldBe false
                balloon.statsPollingIntervalS shouldBe 0
            }

            it("should serialize and deserialize consistently") {
                val originalBalloon = Balloon.forOvercommit(2048)
                val jsonString = json.encodeToString(originalBalloon)
                val deserializedBalloon = json.decodeFromString<Balloon>(jsonString)

                deserializedBalloon shouldBe originalBalloon
            }
        }
    }

    describe("BalloonStatistics") {
        describe("construction") {
            it("should create statistics with required fields") {
                val stats =
                    BalloonStatistics(
                        targetPages = 1024u,
                        actualPages = 512u,
                        targetMib = 4,
                        actualMib = 2,
                    )

                stats.targetPages shouldBe 1024u
                stats.actualPages shouldBe 512u
                stats.targetMib shouldBe 4
                stats.actualMib shouldBe 2
            }

            it("should create statistics with all fields") {
                val stats =
                    BalloonStatistics(
                        targetPages = 2048u,
                        actualPages = 2048u,
                        targetMib = 8,
                        actualMib = 8,
                        swapIn = 1024000UL,
                        swapOut = 512000UL,
                        majorFaults = 100UL,
                        minorFaults = 5000UL,
                        freeMemory = 104857600UL,
                        totalMemory = 1073741824UL,
                        availableMemory = 536870912UL,
                        diskCaches = 268435456UL,
                        hugetlbAllocations = 10UL,
                        hugetlbFailures = 2UL,
                    )

                stats shouldNotBe null
                stats.swapIn shouldBe 1024000UL
                stats.hugetlbFailures shouldBe 2UL
            }
        }

        describe("computed properties") {
            describe("efficiency") {
                it("should calculate efficiency when target is positive") {
                    val stats =
                        BalloonStatistics(
                            targetPages = 1000u,
                            actualPages = 750u,
                            targetMib = 4,
                            actualMib = 3,
                        )

                    stats.efficiency shouldBe 75.0
                }

                it("should return 100% efficiency when target is zero") {
                    val stats =
                        BalloonStatistics(
                            targetPages = 0u,
                            actualPages = 0u,
                            targetMib = 0,
                            actualMib = 0,
                        )

                    stats.efficiency shouldBe 100.0
                }

                it("should handle over-inflation correctly") {
                    val stats =
                        BalloonStatistics(
                            targetPages = 500u,
                            actualPages = 750u,
                            targetMib = 2,
                            actualMib = 3,
                        )

                    stats.efficiency shouldBe 150.0
                }
            }

            describe("isAtTarget") {
                it("should return true when at target") {
                    val stats =
                        BalloonStatistics(
                            targetPages = 1024u,
                            actualPages = 1024u,
                            targetMib = 4,
                            actualMib = 4,
                        )

                    stats.isAtTarget shouldBe true
                }

                it("should return false when below target") {
                    val stats =
                        BalloonStatistics(
                            targetPages = 1024u,
                            actualPages = 512u,
                            targetMib = 4,
                            actualMib = 2,
                        )

                    stats.isAtTarget shouldBe false
                }

                it("should return false when above target") {
                    val stats =
                        BalloonStatistics(
                            targetPages = 512u,
                            actualPages = 1024u,
                            targetMib = 2,
                            actualMib = 4,
                        )

                    stats.isAtTarget shouldBe false
                }
            }

            describe("memoryPressure") {
                it("should calculate memory pressure when data available") {
                    val stats =
                        BalloonStatistics(
                            targetPages = 1024u,
                            actualPages = 1024u,
                            targetMib = 4,
                            actualMib = 4,
                            // 1GB
                            totalMemory = 1073741824UL,
                            // 256MB
                            availableMemory = 268435456UL,
                        )

                    stats.memoryPressure shouldBe 75.0
                }

                it("should return null when total memory is unavailable") {
                    val stats =
                        BalloonStatistics(
                            targetPages = 1024u,
                            actualPages = 1024u,
                            targetMib = 4,
                            actualMib = 4,
                            availableMemory = 268435456UL,
                        )

                    stats.memoryPressure shouldBe null
                }

                it("should return null when available memory is unavailable") {
                    val stats =
                        BalloonStatistics(
                            targetPages = 1024u,
                            actualPages = 1024u,
                            targetMib = 4,
                            actualMib = 4,
                            totalMemory = 1073741824UL,
                        )

                    stats.memoryPressure shouldBe null
                }

                it("should handle zero total memory") {
                    val stats =
                        BalloonStatistics(
                            targetPages = 1024u,
                            actualPages = 1024u,
                            targetMib = 4,
                            actualMib = 4,
                            totalMemory = 0UL,
                            availableMemory = 0UL,
                        )

                    stats.memoryPressure shouldBe null
                }
            }
        }

        describe("serialization") {
            it("should serialize minimal statistics") {
                val stats =
                    BalloonStatistics(
                        targetPages = 1024u,
                        actualPages = 512u,
                        targetMib = 4,
                        actualMib = 2,
                    )
                val jsonString = json.encodeToString(stats)

                jsonString shouldEqualJson """
                {
                    "target_pages": 1024,
                    "actual_pages": 512,
                    "target_mib": 4,
                    "actual_mib": 2,
                    "swap_in": null,
                    "swap_out": null,
                    "major_faults": null,
                    "minor_faults": null,
                    "free_mem": null,
                    "total_mem": null,
                    "available_mem": null,
                    "disk_caches": null,
                    "hugetlb_allocations": null,
                    "hugetlb_failures": null
                }
                """
            }

            it("should serialize complete statistics") {
                val stats =
                    BalloonStatistics(
                        targetPages = 2048u,
                        actualPages = 1024u,
                        targetMib = 8,
                        actualMib = 4,
                        swapIn = 1024000UL,
                        swapOut = 512000UL,
                        majorFaults = 100UL,
                        minorFaults = 5000UL,
                        freeMemory = 104857600UL,
                        totalMemory = 1073741824UL,
                        availableMemory = 536870912UL,
                        diskCaches = 268435456UL,
                        hugetlbAllocations = 10UL,
                        hugetlbFailures = 2UL,
                    )
                val jsonString = json.encodeToString(stats)

                jsonString shouldEqualJson """
                {
                    "target_pages": 2048,
                    "actual_pages": 1024,
                    "target_mib": 8,
                    "actual_mib": 4,
                    "swap_in": 1024000,
                    "swap_out": 512000,
                    "major_faults": 100,
                    "minor_faults": 5000,
                    "free_mem": 104857600,
                    "total_mem": 1073741824,
                    "available_mem": 536870912,
                    "disk_caches": 268435456,
                    "hugetlb_allocations": 10,
                    "hugetlb_failures": 2
                }
                """
            }

            it("should deserialize statistics correctly") {
                val jsonString = """
                {
                    "target_pages": 1024,
                    "actual_pages": 512,
                    "target_mib": 4,
                    "actual_mib": 2,
                    "swap_in": 1000,
                    "total_mem": 1073741824
                }
                """
                val stats = json.decodeFromString<BalloonStatistics>(jsonString)

                stats.targetPages shouldBe 1024u
                stats.actualPages shouldBe 512u
                stats.swapIn shouldBe 1000UL
                stats.totalMemory shouldBe 1073741824UL
                stats.swapOut shouldBe null
            }
        }
    }

    describe("BalloonUpdate") {
        describe("construction") {
            it("should create update with amount only") {
                val update = BalloonUpdate(amountMib = 1024)

                update.amountMib shouldBe 1024
                update.statsPollingIntervalS shouldBe null
            }

            it("should create update with polling interval only") {
                val update = BalloonUpdate(statsPollingIntervalS = 10)

                update.amountMib shouldBe null
                update.statsPollingIntervalS shouldBe 10
            }

            it("should create update with both fields") {
                val update =
                    BalloonUpdate(
                        amountMib = 512,
                        statsPollingIntervalS = 5,
                    )

                update.amountMib shouldBe 512
                update.statsPollingIntervalS shouldBe 5
            }
        }

        describe("validation") {
            it("should reject negative amount") {
                shouldThrow<IllegalArgumentException> {
                    BalloonUpdate(amountMib = -1)
                }.message shouldContain "Balloon amount must be non-negative"
            }

            it("should reject negative polling interval") {
                shouldThrow<IllegalArgumentException> {
                    BalloonUpdate(statsPollingIntervalS = -1)
                }.message shouldContain "Statistics polling interval must be non-negative"
            }

            it("should require at least one field") {
                shouldThrow<IllegalArgumentException> {
                    BalloonUpdate()
                }.message shouldContain "At least one field must be specified"
            }

            it("should accept zero values") {
                val update =
                    BalloonUpdate(
                        amountMib = 0,
                        statsPollingIntervalS = 0,
                    )

                update.amountMib shouldBe 0
                update.statsPollingIntervalS shouldBe 0
            }
        }

        describe("factory methods") {
            describe("targetSize") {
                it("should create target size update") {
                    val update = BalloonUpdate.targetSize(1024)

                    update.amountMib shouldBe 1024
                    update.statsPollingIntervalS shouldBe null
                }

                it("should reject negative amount") {
                    shouldThrow<IllegalArgumentException> {
                        BalloonUpdate.targetSize(-1)
                    }.message shouldContain "Balloon amount must be non-negative"
                }
            }

            describe("pollingInterval") {
                it("should create polling interval update") {
                    val update = BalloonUpdate.pollingInterval(15)

                    update.amountMib shouldBe null
                    update.statsPollingIntervalS shouldBe 15
                }

                it("should accept zero interval") {
                    val update = BalloonUpdate.pollingInterval(0)

                    update.statsPollingIntervalS shouldBe 0
                }

                it("should reject negative interval") {
                    shouldThrow<IllegalArgumentException> {
                        BalloonUpdate.pollingInterval(-1)
                    }.message shouldContain "Statistics polling interval must be non-negative"
                }
            }

            describe("both") {
                it("should create update with both fields") {
                    val update = BalloonUpdate.both(2048, 20)

                    update.amountMib shouldBe 2048
                    update.statsPollingIntervalS shouldBe 20
                }

                it("should reject negative amount") {
                    shouldThrow<IllegalArgumentException> {
                        BalloonUpdate.both(-1, 10)
                    }.message shouldContain "Balloon amount must be non-negative"
                }

                it("should reject negative interval") {
                    shouldThrow<IllegalArgumentException> {
                        BalloonUpdate.both(1024, -1)
                    }.message shouldContain "Statistics polling interval must be non-negative"
                }
            }
        }

        describe("serialization") {
            it("should serialize target size update") {
                val update = BalloonUpdate.targetSize(512)
                val jsonString = json.encodeToString(update)

                jsonString shouldEqualJson """
                {
                    "amount_mib": 512,
                    "stats_polling_interval_s": null
                }
                """
            }

            it("should serialize polling interval update") {
                val update = BalloonUpdate.pollingInterval(30)
                val jsonString = json.encodeToString(update)

                jsonString shouldEqualJson """
                {
                    "amount_mib": null,
                    "stats_polling_interval_s": 30
                }
                """
            }

            it("should serialize both fields update") {
                val update = BalloonUpdate.both(1024, 5)
                val jsonString = json.encodeToString(update)

                jsonString shouldEqualJson """
                {
                    "amount_mib": 1024,
                    "stats_polling_interval_s": 5
                }
                """
            }

            it("should deserialize update correctly") {
                val jsonString = """
                {
                    "amount_mib": 256,
                    "stats_polling_interval_s": 10
                }
                """
                val update = json.decodeFromString<BalloonUpdate>(jsonString)

                update.amountMib shouldBe 256
                update.statsPollingIntervalS shouldBe 10
            }

            it("should serialize and deserialize consistently") {
                val originalUpdate = BalloonUpdate.both(4096, 2)
                val jsonString = json.encodeToString(originalUpdate)
                val deserializedUpdate = json.decodeFromString<BalloonUpdate>(jsonString)

                deserializedUpdate shouldBe originalUpdate
            }
        }
    }
})
