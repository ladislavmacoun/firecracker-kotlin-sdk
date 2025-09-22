package org.firecracker.sdk.models

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class MMDSTest : DescribeSpec({
    val json =
        Json {
            prettyPrint = true
            encodeDefaults = true
        }

    describe("MMDS") {
        describe("creation") {
            it("should create MMDS with required fields") {
                val mmds = MMDS.create(listOf("eth0"))

                mmds.version shouldBe MMDSVersion.V2
                mmds.networkInterfaces shouldBe listOf("eth0")
                mmds.ipv4Address shouldBe MMDS.DEFAULT_MMDS_IPV4_ADDRESS
            }

            it("should create MMDS with custom IPv4 address") {
                val mmds =
                    MMDS.create(
                        networkInterfaces = listOf("eth0"),
                        ipv4Address = "192.168.1.1",
                    )

                mmds.version shouldBe MMDSVersion.V2
                mmds.networkInterfaces shouldBe listOf("eth0")
                mmds.ipv4Address shouldBe "192.168.1.1"
            }

            it("should create MMDS with multiple interfaces") {
                val mmds = MMDS.create(listOf("eth0", "eth1"))

                mmds.networkInterfaces shouldBe listOf("eth0", "eth1")
            }

            it("should create MMDS with V1 version") {
                val mmds = MMDS.createV1(listOf("eth0"))

                mmds.version shouldBe MMDSVersion.V1
                mmds.networkInterfaces shouldBe listOf("eth0")
            }

            it("should create MMDS for single interface") {
                val mmds = MMDS.forInterface("eth1")

                mmds.version shouldBe MMDSVersion.V2
                mmds.networkInterfaces shouldBe listOf("eth1")
                mmds.ipv4Address shouldBe MMDS.DEFAULT_MMDS_IPV4_ADDRESS
            }

            it("should create MMDS for default interface") {
                val mmds = MMDS.forDefaultInterface()

                mmds.version shouldBe MMDSVersion.V2
                mmds.networkInterfaces shouldBe listOf("eth0")
                mmds.ipv4Address shouldBe MMDS.DEFAULT_MMDS_IPV4_ADDRESS
            }

            it("should create MMDS for default interface with custom IP") {
                val mmds = MMDS.forDefaultInterface("10.0.0.1")

                mmds.networkInterfaces shouldBe listOf("eth0")
                mmds.ipv4Address shouldBe "10.0.0.1"
            }
        }

        describe("validation") {
            it("should reject empty network interfaces list") {
                shouldThrow<IllegalArgumentException> {
                    MMDS.create(emptyList())
                }.message shouldBe "At least one network interface must be specified for MMDS"
            }

            it("should reject blank network interface ID") {
                shouldThrow<IllegalArgumentException> {
                    MMDS.create(listOf("eth0", ""))
                }.message shouldBe "Network interface ID cannot be blank"
            }

            it("should reject whitespace-only network interface ID") {
                shouldThrow<IllegalArgumentException> {
                    MMDS.create(listOf("eth0", "   "))
                }.message shouldBe "Network interface ID cannot be blank"
            }

            it("should reject blank IPv4 address") {
                shouldThrow<IllegalArgumentException> {
                    MMDS.create(listOf("eth0"), "")
                }.message shouldBe "IPv4 address cannot be blank"
            }

            it("should reject invalid IPv4 address format") {
                shouldThrow<IllegalArgumentException> {
                    MMDS.create(listOf("eth0"), "invalid-ip")
                }.message shouldBe "Invalid IPv4 address format: invalid-ip"
            }

            it("should reject IPv4 address with invalid octets") {
                shouldThrow<IllegalArgumentException> {
                    MMDS.create(listOf("eth0"), "256.1.1.1")
                }.message shouldBe "Invalid IPv4 address format: 256.1.1.1"
            }

            it("should reject IPv4 address with too few octets") {
                shouldThrow<IllegalArgumentException> {
                    MMDS.create(listOf("eth0"), "192.168.1")
                }.message shouldBe "Invalid IPv4 address format: 192.168.1"
            }

            it("should reject blank interface ID in forInterface") {
                shouldThrow<IllegalArgumentException> {
                    MMDS.forInterface("")
                }.message shouldBe "Interface ID cannot be blank"
            }

            it("should accept valid IPv4 addresses") {
                MMDS.create(listOf("eth0"), "0.0.0.0").ipv4Address shouldBe "0.0.0.0"
                MMDS.create(listOf("eth0"), "127.0.0.1").ipv4Address shouldBe "127.0.0.1"
                MMDS.create(listOf("eth0"), "255.255.255.255").ipv4Address shouldBe "255.255.255.255"
                MMDS.create(listOf("eth0"), "192.168.0.1").ipv4Address shouldBe "192.168.0.1"
            }
        }

        describe("serialization") {
            it("should serialize MMDS to JSON") {
                val mmds =
                    MMDS.create(
                        networkInterfaces = listOf("eth0", "eth1"),
                        ipv4Address = "192.168.1.100",
                    )

                val jsonString = json.encodeToString(mmds)
                val expectedJson =
                    """
                    {
                        "version": "V2",
                        "network_interfaces": [
                            "eth0",
                            "eth1"
                        ],
                        "ipv4_address": "192.168.1.100"
                    }
                    """.trimIndent()

                Json.parseToJsonElement(jsonString) shouldBe Json.parseToJsonElement(expectedJson)
            }

            it("should serialize MMDS with default IPv4") {
                val mmds = MMDS.forDefaultInterface()

                val jsonString = json.encodeToString(mmds)
                val expectedJson = """
                    {
                        "version": "V2",
                        "network_interfaces": ["eth0"],
                        "ipv4_address": "169.254.169.254"
                    }
                """

                jsonString shouldEqualJson expectedJson
            }

            it("should deserialize from JSON correctly") {
                val jsonString =
                    """
                    {
                        "version": "V1",
                        "network_interfaces": ["eth0"],
                        "ipv4_address": "169.254.169.254"
                    }
                    """.trimIndent()

                val mmds = json.decodeFromString<MMDS>(jsonString)
                mmds.version shouldBe MMDSVersion.V1
                mmds.networkInterfaces shouldBe listOf("eth0")
                mmds.ipv4Address shouldBe "169.254.169.254"
            }
        }
    }

    describe("MMDSVersion") {
        it("should serialize correctly") {
            json.encodeToString(MMDSVersion.V1) shouldBe "\"V1\""
            json.encodeToString(MMDSVersion.V2) shouldBe "\"V2\""
        }
    }

    describe("MMDSConfig") {
        describe("creation") {
            it("should create MMDS config with JSON object") {
                val metadata =
                    JsonObject(
                        mapOf(
                            "instance-id" to JsonPrimitive("i-1234567890abcdef0"),
                            "local-hostname" to JsonPrimitive("my-instance"),
                        ),
                    )

                val config = MMDSConfig.create(metadata)
                config.metadata shouldBe metadata
            }

            it("should create MMDS config with metadata") {
                val metadata = JsonPrimitive("simple-value")
                val config = MMDSConfig.withMetadata(metadata)
                config.metadata shouldBe metadata
            }

            it("should create cloud-init MMDS config") {
                val config =
                    MMDSConfig.cloudInit(
                        instanceId = "i-1234567890abcdef0",
                        hostname = "my-instance",
                    )

                val expectedMetadata =
                    JsonObject(
                        mapOf(
                            "instance-id" to JsonPrimitive("i-1234567890abcdef0"),
                            "local-hostname" to JsonPrimitive("my-instance"),
                        ),
                    )

                config.metadata shouldBe expectedMetadata
            }

            it("should create cloud-init MMDS config with user data") {
                val userData = "#cloud-config\npackages:\n  - nginx"
                val config =
                    MMDSConfig.cloudInit(
                        instanceId = "i-1234567890abcdef0",
                        hostname = "my-instance",
                        userData = userData,
                    )

                val expectedMetadata =
                    JsonObject(
                        mapOf(
                            "instance-id" to JsonPrimitive("i-1234567890abcdef0"),
                            "local-hostname" to JsonPrimitive("my-instance"),
                            "user-data" to JsonPrimitive(userData),
                        ),
                    )

                config.metadata shouldBe expectedMetadata
            }
        }

        describe("validation") {
            it("should reject blank instance ID in cloud-init") {
                shouldThrow<IllegalArgumentException> {
                    MMDSConfig.cloudInit("", "hostname")
                }.message shouldBe "Instance ID cannot be blank"
            }

            it("should reject blank hostname in cloud-init") {
                shouldThrow<IllegalArgumentException> {
                    MMDSConfig.cloudInit("instance-id", "")
                }.message shouldBe "Hostname cannot be blank"
            }

            it("should reject whitespace-only instance ID in cloud-init") {
                shouldThrow<IllegalArgumentException> {
                    MMDSConfig.cloudInit("   ", "hostname")
                }.message shouldBe "Instance ID cannot be blank"
            }

            it("should reject whitespace-only hostname in cloud-init") {
                shouldThrow<IllegalArgumentException> {
                    MMDSConfig.cloudInit("instance-id", "   ")
                }.message shouldBe "Hostname cannot be blank"
            }
        }

        describe("serialization") {
            it("should serialize MMDS config to JSON") {
                val metadata =
                    JsonObject(
                        mapOf(
                            "instance-id" to JsonPrimitive("i-1234567890abcdef0"),
                            "local-hostname" to JsonPrimitive("my-instance"),
                            "ami-id" to JsonPrimitive("ami-12345678"),
                        ),
                    )

                val config = MMDSConfig.create(metadata)
                val jsonString = json.encodeToString(config)

                val expectedJson =
                    """
                    {
                        "metadata": {
                            "instance-id": "i-1234567890abcdef0",
                            "local-hostname": "my-instance",
                            "ami-id": "ami-12345678"
                        }
                    }
                    """.trimIndent()

                Json.parseToJsonElement(jsonString) shouldBe Json.parseToJsonElement(expectedJson)
            }

            it("should deserialize from JSON correctly") {
                val jsonString =
                    """
                    {
                        "metadata": {
                            "instance-id": "i-1234567890abcdef0",
                            "local-hostname": "my-instance"
                        }
                    }
                    """.trimIndent()

                val config = json.decodeFromString<MMDSConfig>(jsonString)
                val expectedMetadata =
                    JsonObject(
                        mapOf(
                            "instance-id" to JsonPrimitive("i-1234567890abcdef0"),
                            "local-hostname" to JsonPrimitive("my-instance"),
                        ),
                    )

                config.metadata shouldBe expectedMetadata
            }
        }
    }
})
