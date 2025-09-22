package org.firecracker.sdk.models

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class VSockTest : DescribeSpec({
    val json = Json { ignoreUnknownKeys = true }

    describe("VSock") {
        describe("creation") {
            it("should create VSock with required fields") {
                val vsock =
                    VSock(
                        guestCid = 3u,
                        udsPath = "/tmp/test.sock",
                    )

                vsock.guestCid shouldBe 3u
                vsock.udsPath shouldBe "/tmp/test.sock"
                vsock.vsockId shouldBe "vsock"
            }

            it("should create VSock with all fields") {
                val vsock =
                    VSock(
                        guestCid = 42u,
                        udsPath = "/var/run/firecracker.sock",
                        vsockId = "my-vsock",
                    )

                vsock.guestCid shouldBe 42u
                vsock.udsPath shouldBe "/var/run/firecracker.sock"
                vsock.vsockId shouldBe "my-vsock"
            }

            it("should create VSock with factory method") {
                val vsock =
                    VSock.create(
                        guestCid = 100u,
                        udsPath = "/tmp/vm.sock",
                    )

                vsock.guestCid shouldBe 100u
                vsock.udsPath shouldBe "/tmp/vm.sock"
                vsock.vsockId shouldBe "vsock"
            }

            it("should create VSock with custom device ID") {
                val vsock =
                    VSock.create(
                        guestCid = 50u,
                        udsPath = "/tmp/custom.sock",
                        vsockId = "custom-vsock",
                    )

                vsock.guestCid shouldBe 50u
                vsock.udsPath shouldBe "/tmp/custom.sock"
                vsock.vsockId shouldBe "custom-vsock"
            }

            it("should create VSock for VM") {
                val vsock = VSock.forVm("test-vm")

                // Guest CID should be generated from VM ID and be >= 100
                vsock.guestCid shouldBeGreaterThanOrEqualTo 100u
                vsock.udsPath shouldBe "/tmp/firecracker-test-vm.vsock"
                vsock.vsockId shouldBe "vsock"
            }

            it("should create VSock for VM with custom CID") {
                val vsock =
                    VSock.forVm(
                        vmId = "my-vm",
                        guestCid = 200u,
                    )

                vsock.guestCid shouldBe 200u
                vsock.udsPath shouldBe "/tmp/firecracker-my-vm.vsock"
                vsock.vsockId shouldBe "vsock"
            }

            it("should create VSock for VM with custom base directory") {
                val vsock =
                    VSock.forVm(
                        vmId = "vm1",
                        baseDir = "/var/lib/firecracker",
                    )

                vsock.udsPath shouldBe "/var/lib/firecracker/firecracker-vm1.vsock"
            }

            it("should create VSock with socket name") {
                val vsock =
                    VSock.withSocketName(
                        guestCid = 123u,
                        socketName = "my-socket.sock",
                    )

                vsock.guestCid shouldBe 123u
                vsock.udsPath shouldBe "/tmp/my-socket.sock"
                vsock.vsockId shouldBe "vsock"
            }

            it("should create VSock with socket name and custom directory") {
                val vsock =
                    VSock.withSocketName(
                        guestCid = 456u,
                        socketName = "app.vsock",
                        baseDir = "/var/run",
                    )

                vsock.guestCid shouldBe 456u
                vsock.udsPath shouldBe "/var/run/app.vsock"
                vsock.vsockId shouldBe "vsock"
            }
        }

        describe("validation") {
            it("should reject guest CID below 3") {
                shouldThrow<IllegalArgumentException> {
                    VSock.create(
                        guestCid = 2u,
                        udsPath = "/tmp/test.sock",
                    )
                }.message shouldBe "Guest CID must be >= 3 (0-2 are reserved)"
            }

            it("should reject guest CID of 0") {
                shouldThrow<IllegalArgumentException> {
                    VSock.create(
                        guestCid = 0u,
                        udsPath = "/tmp/test.sock",
                    )
                }.message shouldBe "Guest CID must be >= 3 (0-2 are reserved)"
            }

            it("should reject blank UDS path") {
                shouldThrow<IllegalArgumentException> {
                    VSock.create(
                        guestCid = 3u,
                        udsPath = "",
                    )
                }.message shouldBe "UDS path cannot be blank"
            }

            it("should reject whitespace-only UDS path") {
                shouldThrow<IllegalArgumentException> {
                    VSock.create(
                        guestCid = 3u,
                        udsPath = "   ",
                    )
                }.message shouldBe "UDS path cannot be blank"
            }

            it("should reject blank VSock ID") {
                shouldThrow<IllegalArgumentException> {
                    VSock.create(
                        guestCid = 3u,
                        udsPath = "/tmp/test.sock",
                        vsockId = "",
                    )
                }.message shouldBe "VSock ID cannot be blank"
            }

            it("should reject blank VM ID in forVm") {
                shouldThrow<IllegalArgumentException> {
                    VSock.forVm("")
                }.message shouldBe "VM ID cannot be blank"
            }

            it("should reject whitespace-only VM ID in forVm") {
                shouldThrow<IllegalArgumentException> {
                    VSock.forVm("   ")
                }.message shouldBe "VM ID cannot be blank"
            }

            it("should reject blank socket name") {
                shouldThrow<IllegalArgumentException> {
                    VSock.withSocketName(
                        guestCid = 3u,
                        socketName = "",
                    )
                }.message shouldBe "Socket name cannot be blank"
            }

            it("should reject whitespace-only socket name") {
                shouldThrow<IllegalArgumentException> {
                    VSock.withSocketName(
                        guestCid = 3u,
                        socketName = "   ",
                    )
                }.message shouldBe "Socket name cannot be blank"
            }

            it("should accept guest CID of 3") {
                val vsock =
                    VSock.create(
                        guestCid = 3u,
                        udsPath = "/tmp/test.sock",
                    )
                vsock.guestCid shouldBe 3u
            }

            it("should accept large guest CID") {
                // Max UInt value
                val vsock =
                    VSock.create(
                        guestCid = 4294967295u,
                        udsPath = "/tmp/test.sock",
                    )
                vsock.guestCid shouldBe 4294967295u
            }
        }

        describe("serialization") {
            it("should serialize VSock to JSON") {
                val vsock =
                    VSock.create(
                        guestCid = 42u,
                        udsPath = "/tmp/firecracker.sock",
                        vsockId = "main-vsock",
                    )
                val jsonString = json.encodeToString(vsock)
                val deserialized = json.decodeFromString<VSock>(jsonString)

                deserialized.guestCid shouldBe vsock.guestCid
                deserialized.udsPath shouldBe vsock.udsPath
                deserialized.vsockId shouldBe vsock.vsockId
            }

            it("should serialize minimal VSock to JSON") {
                val vsock =
                    VSock.create(
                        guestCid = 3u,
                        udsPath = "/tmp/test.sock",
                    )
                val jsonString = json.encodeToString(vsock)
                val deserialized = json.decodeFromString<VSock>(jsonString)

                deserialized.guestCid shouldBe 3u
                deserialized.udsPath shouldBe "/tmp/test.sock"
                deserialized.vsockId shouldBe "vsock"
            }

            it("should deserialize from JSON correctly") {
                val jsonString =
                    """
                    {
                        "guest_cid": 100,
                        "uds_path": "/var/run/vm.sock",
                        "vsock_id": "my-vsock"
                    }
                    """.trimIndent()

                val vsock = json.decodeFromString<VSock>(jsonString)

                vsock.guestCid shouldBe 100u
                vsock.udsPath shouldBe "/var/run/vm.sock"
                vsock.vsockId shouldBe "my-vsock"
            }

            it("should deserialize with default VSock ID") {
                val jsonString =
                    """
                    {
                        "guest_cid": 50,
                        "uds_path": "/tmp/minimal.sock"
                    }
                    """.trimIndent()

                val vsock = json.decodeFromString<VSock>(jsonString)

                vsock.guestCid shouldBe 50u
                vsock.udsPath shouldBe "/tmp/minimal.sock"
                vsock.vsockId shouldBe "vsock"
            }
        }
    }
})
