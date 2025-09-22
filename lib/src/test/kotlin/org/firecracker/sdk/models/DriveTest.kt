package org.firecracker.sdk.models

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json

class DriveTest : DescribeSpec({
    val json = Json { ignoreUnknownKeys = true }

    describe("Drive") {
        describe("creation") {
            it("should create drive with required fields") {
                val drive =
                    Drive(
                        driveId = "sda",
                        pathOnHost = "/path/to/drive.img",
                    )

                drive.driveId shouldBe "sda"
                drive.pathOnHost shouldBe "/path/to/drive.img"
                drive.isRootDevice shouldBe false
                drive.isReadOnly shouldBe false
                drive.cacheType shouldBe CacheType.UNSAFE
                drive.rateLimiter shouldBe null
            }

            it("should create root drive with helper") {
                val drive = Drive.rootDrive("root", "/path/to/rootfs.img")

                drive.driveId shouldBe "root"
                drive.pathOnHost shouldBe "/path/to/rootfs.img"
                drive.isRootDevice shouldBe true
                drive.isReadOnly shouldBe false
                drive.cacheType shouldBe CacheType.UNSAFE
            }

            it("should create data drive with helper") {
                val drive =
                    Drive.dataDrive(
                        "data",
                        "/path/to/data.img",
                        isReadOnly = true,
                        cacheType = CacheType.WRITEBACK,
                    )

                drive.driveId shouldBe "data"
                drive.pathOnHost shouldBe "/path/to/data.img"
                drive.isRootDevice shouldBe false
                drive.isReadOnly shouldBe true
                drive.cacheType shouldBe CacheType.WRITEBACK
            }
        }

        describe("validation") {
            it("should reject blank drive ID") {
                shouldThrow<IllegalArgumentException> {
                    Drive(driveId = "", pathOnHost = "/path/to/drive.img")
                }.message shouldContain "Drive ID cannot be blank"
            }

            it("should reject blank path on host") {
                shouldThrow<IllegalArgumentException> {
                    Drive(driveId = "sda", pathOnHost = "")
                }.message shouldContain "Path on host cannot be blank"
            }
        }

        describe("serialization") {
            it("should serialize minimal drive to JSON") {
                val drive = Drive(driveId = "sda", pathOnHost = "/path/to/drive.img")
                val jsonString = json.encodeToString(Drive.serializer(), drive)

                jsonString shouldContain "\"drive_id\":\"sda\""
                jsonString shouldContain "\"path_on_host\":\"/path/to/drive.img\""
                // Note: Default values might be omitted in JSON serialization
                // Let's verify by deserializing and checking the object
                val deserializedDrive = json.decodeFromString(Drive.serializer(), jsonString)
                deserializedDrive.isRootDevice shouldBe false
                deserializedDrive.isReadOnly shouldBe false
                deserializedDrive.cacheType shouldBe CacheType.UNSAFE
            }

            it("should serialize complete drive to JSON") {
                val drive =
                    Drive(
                        driveId = "sda",
                        pathOnHost = "/path/to/drive.img",
                        isRootDevice = true,
                        isReadOnly = true,
                        cacheType = CacheType.WRITEBACK,
                        rateLimiter =
                            DriveRateLimiter(
                                bandwidth = TokenBucket.create(1000),
                            ),
                    )
                val jsonString = json.encodeToString(Drive.serializer(), drive)

                jsonString shouldContain "\"drive_id\":\"sda\""
                jsonString shouldContain "\"is_root_device\":true"
                jsonString shouldContain "\"is_read_only\":true"
                jsonString shouldContain "\"cache_type\":\"Writeback\""
                jsonString shouldContain "\"rate_limiter\""
            }

            it("should deserialize from JSON correctly") {
                val jsonString =
                    """
                    {
                        "drive_id": "sda",
                        "path_on_host": "/path/to/drive.img",
                        "is_root_device": true,
                        "is_read_only": false,
                        "cache_type": "Unsafe"
                    }
                    """.trimIndent()

                val drive = json.decodeFromString(Drive.serializer(), jsonString)

                drive.driveId shouldBe "sda"
                drive.pathOnHost shouldBe "/path/to/drive.img"
                drive.isRootDevice shouldBe true
                drive.isReadOnly shouldBe false
                drive.cacheType shouldBe CacheType.UNSAFE
            }
        }
    }

    describe("CacheType") {
        it("should serialize correctly") {
            CacheType.UNSAFE.toString() shouldBe "UNSAFE"
            CacheType.WRITEBACK.toString() shouldBe "WRITEBACK"
        }
    }

    describe("TokenBucket") {
        describe("creation") {
            it("should create token bucket with defaults") {
                val bucket = TokenBucket.create(1000)

                bucket.size shouldBe 1000
                bucket.oneTimeBurst shouldBe 1000
                bucket.refillTime shouldBe 100
            }

            it("should create token bucket with custom values") {
                val bucket =
                    TokenBucket.create(
                        size = 2000,
                        oneTimeBurst = 3000,
                        refillTimeMs = 200,
                    )

                bucket.size shouldBe 2000
                bucket.oneTimeBurst shouldBe 3000
                bucket.refillTime shouldBe 200
            }
        }

        describe("validation") {
            it("should reject negative one time burst") {
                shouldThrow<IllegalArgumentException> {
                    TokenBucket(oneTimeBurst = -1, refillTime = 100, size = 1000)
                }.message shouldContain "One time burst must be non-negative"
            }

            it("should reject non-positive refill time") {
                shouldThrow<IllegalArgumentException> {
                    TokenBucket(oneTimeBurst = 1000, refillTime = 0, size = 1000)
                }.message shouldContain "Refill time must be positive"
            }

            it("should reject non-positive size") {
                shouldThrow<IllegalArgumentException> {
                    TokenBucket(oneTimeBurst = 1000, refillTime = 100, size = 0)
                }.message shouldContain "Size must be positive"
            }
        }
    }

    describe("DriveRateLimiter") {
        it("should create rate limiter with bandwidth only") {
            val rateLimiter =
                DriveRateLimiter(
                    bandwidth = TokenBucket.create(1000),
                )

            rateLimiter.bandwidth shouldBe TokenBucket.create(1000)
            rateLimiter.ops shouldBe null
        }

        it("should create rate limiter with ops only") {
            val rateLimiter =
                DriveRateLimiter(
                    ops = TokenBucket.create(100),
                )

            rateLimiter.bandwidth shouldBe null
            rateLimiter.ops shouldBe TokenBucket.create(100)
        }

        it("should create rate limiter with both bandwidth and ops") {
            val rateLimiter =
                DriveRateLimiter(
                    bandwidth = TokenBucket.create(1000),
                    ops = TokenBucket.create(100),
                )

            rateLimiter.bandwidth shouldBe TokenBucket.create(1000)
            rateLimiter.ops shouldBe TokenBucket.create(100)
        }
    }
})
