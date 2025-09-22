package org.firecracker.sdk.models

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json

class NetworkInterfaceTest : DescribeSpec({

    val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

    describe("NetworkInterface") {

        describe("creation") {
            it("should create network interface with required fields") {
                val netif =
                    NetworkInterface(
                        interfaceId = "eth0",
                        hostDeviceName = "tap0",
                    )

                netif.interfaceId shouldBe "eth0"
                netif.hostDeviceName shouldBe "tap0"
                netif.guestMacAddress shouldBe null
                netif.rxRateLimiter shouldBe null
                netif.txRateLimiter shouldBe null
            }

            it("should create network interface with all fields") {
                val rxLimiter =
                    RateLimiter(
                        bandwidth = Bandwidth(size = 1000000, refillTimeMs = 100),
                        operations = Operations(size = 1000, refillTimeMs = 100),
                    )
                val txLimiter =
                    RateLimiter(
                        bandwidth = Bandwidth(size = 500000, refillTimeMs = 200),
                    )

                val netif =
                    NetworkInterface(
                        interfaceId = "eth0",
                        hostDeviceName = "tap0",
                        guestMacAddress = "AA:BB:CC:DD:EE:FF",
                        rxRateLimiter = rxLimiter,
                        txRateLimiter = txLimiter,
                    )

                netif.interfaceId shouldBe "eth0"
                netif.hostDeviceName shouldBe "tap0"
                netif.guestMacAddress shouldBe "AA:BB:CC:DD:EE:FF"
                netif.rxRateLimiter shouldBe rxLimiter
                netif.txRateLimiter shouldBe txLimiter
            }
        }

        describe("validation") {
            it("should reject blank interface ID") {
                shouldThrow<IllegalArgumentException> {
                    NetworkInterface(
                        interfaceId = "",
                        hostDeviceName = "tap0",
                    )
                }.message shouldContain "Interface ID cannot be blank"
            }

            it("should reject blank host device name") {
                shouldThrow<IllegalArgumentException> {
                    NetworkInterface(
                        interfaceId = "eth0",
                        hostDeviceName = "",
                    )
                }.message shouldContain "Host device name cannot be blank"
            }

            it("should reject invalid MAC address format") {
                shouldThrow<IllegalArgumentException> {
                    NetworkInterface(
                        interfaceId = "eth0",
                        hostDeviceName = "tap0",
                        guestMacAddress = "invalid-mac",
                    )
                }.message shouldContain "Invalid MAC address format"
            }

            it("should accept valid MAC address formats") {
                // Test colon-separated format
                val netif1 =
                    NetworkInterface(
                        interfaceId = "eth0",
                        hostDeviceName = "tap0",
                        guestMacAddress = "AA:BB:CC:DD:EE:FF",
                    )
                netif1.guestMacAddress shouldBe "AA:BB:CC:DD:EE:FF"

                // Test hyphen-separated format
                val netif2 =
                    NetworkInterface(
                        interfaceId = "eth0",
                        hostDeviceName = "tap0",
                        guestMacAddress = "aa-bb-cc-dd-ee-ff",
                    )
                netif2.guestMacAddress shouldBe "aa-bb-cc-dd-ee-ff"
            }
        }

        describe("serialization") {
            it("should serialize minimal network interface to JSON") {
                val netif =
                    NetworkInterface(
                        interfaceId = "eth0",
                        hostDeviceName = "tap0",
                    )

                val jsonString = json.encodeToString(NetworkInterface.serializer(), netif)
                val expectedJson = """{"iface_id":"eth0","host_dev_name":"tap0"}"""

                jsonString shouldBe expectedJson
            }

            it("should serialize complete network interface to JSON") {
                val netif =
                    NetworkInterface(
                        interfaceId = "eth0",
                        hostDeviceName = "tap0",
                        guestMacAddress = "AA:BB:CC:DD:EE:FF",
                        rxRateLimiter =
                            RateLimiter(
                                bandwidth = Bandwidth(size = 1000000, refillTimeMs = 100),
                            ),
                        txRateLimiter =
                            RateLimiter(
                                operations = Operations(size = 1000, refillTimeMs = 100),
                            ),
                    )

                val jsonString = json.encodeToString(NetworkInterface.serializer(), netif)

                // Verify it contains expected fields
                jsonString shouldContain """"iface_id":"eth0""""
                jsonString shouldContain """"host_dev_name":"tap0""""
                jsonString shouldContain """"guest_mac":"AA:BB:CC:DD:EE:FF""""
                jsonString shouldContain """"rx_rate_limiter":"""
                jsonString shouldContain """"tx_rate_limiter":"""
            }

            it("should deserialize from JSON correctly") {
                val jsonString =
                    """
                    {
                        "iface_id": "eth0",
                        "host_dev_name": "tap0",
                        "guest_mac": "AA:BB:CC:DD:EE:FF"
                    }
                    """.trimIndent()

                val netif = json.decodeFromString(NetworkInterface.serializer(), jsonString)

                netif.interfaceId shouldBe "eth0"
                netif.hostDeviceName shouldBe "tap0"
                netif.guestMacAddress shouldBe "AA:BB:CC:DD:EE:FF"
                netif.rxRateLimiter shouldBe null
                netif.txRateLimiter shouldBe null
            }
        }
    }

    describe("RateLimiter") {

        describe("creation") {
            it("should create rate limiter with bandwidth only") {
                val limiter =
                    RateLimiter(
                        bandwidth = Bandwidth(size = 1000000, refillTimeMs = 100),
                    )

                limiter.bandwidth?.size shouldBe 1000000
                limiter.bandwidth?.refillTimeMs shouldBe 100
                limiter.operations shouldBe null
            }

            it("should create rate limiter with operations only") {
                val limiter =
                    RateLimiter(
                        operations = Operations(size = 1000, refillTimeMs = 100),
                    )

                limiter.operations?.size shouldBe 1000
                limiter.operations?.refillTimeMs shouldBe 100
                limiter.bandwidth shouldBe null
            }

            it("should create rate limiter with both bandwidth and operations") {
                val limiter =
                    RateLimiter(
                        bandwidth = Bandwidth(size = 1000000, refillTimeMs = 100),
                        operations = Operations(size = 1000, refillTimeMs = 200),
                    )

                limiter.bandwidth?.size shouldBe 1000000
                limiter.operations?.size shouldBe 1000
            }
        }
    }

    describe("Bandwidth") {

        describe("validation") {
            it("should reject non-positive size") {
                shouldThrow<IllegalArgumentException> {
                    Bandwidth(size = 0, refillTimeMs = 100)
                }.message shouldContain "Bandwidth size must be positive"

                shouldThrow<IllegalArgumentException> {
                    Bandwidth(size = -1, refillTimeMs = 100)
                }.message shouldContain "Bandwidth size must be positive"
            }

            it("should reject non-positive refill time") {
                shouldThrow<IllegalArgumentException> {
                    Bandwidth(size = 1000, refillTimeMs = 0)
                }.message shouldContain "Refill time must be positive"

                shouldThrow<IllegalArgumentException> {
                    Bandwidth(size = 1000, refillTimeMs = -1)
                }.message shouldContain "Refill time must be positive"
            }

            it("should reject negative one time burst") {
                shouldThrow<IllegalArgumentException> {
                    Bandwidth(size = 1000, refillTimeMs = 100, oneTimeBurst = -1)
                }.message shouldContain "One time burst must be non-negative"
            }

            it("should accept valid configuration") {
                val bandwidth =
                    Bandwidth(
                        size = 1000000,
                        refillTimeMs = 100,
                        oneTimeBurst = 500000,
                    )

                bandwidth.size shouldBe 1000000
                bandwidth.refillTimeMs shouldBe 100
                bandwidth.oneTimeBurst shouldBe 500000
            }
        }
    }

    describe("Operations") {

        describe("validation") {
            it("should reject non-positive size") {
                shouldThrow<IllegalArgumentException> {
                    Operations(size = 0, refillTimeMs = 100)
                }.message shouldContain "Operations size must be positive"
            }

            it("should reject non-positive refill time") {
                shouldThrow<IllegalArgumentException> {
                    Operations(size = 1000, refillTimeMs = 0)
                }.message shouldContain "Refill time must be positive"
            }

            it("should reject negative one time burst") {
                shouldThrow<IllegalArgumentException> {
                    Operations(size = 1000, refillTimeMs = 100, oneTimeBurst = -1)
                }.message shouldContain "One time burst must be non-negative"
            }

            it("should accept valid configuration") {
                val operations =
                    Operations(
                        size = 1000,
                        refillTimeMs = 100,
                        oneTimeBurst = 500,
                    )

                operations.size shouldBe 1000
                operations.refillTimeMs shouldBe 100
                operations.oneTimeBurst shouldBe 500
            }
        }
    }
})
