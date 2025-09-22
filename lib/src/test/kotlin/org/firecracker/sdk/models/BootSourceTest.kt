package org.firecracker.sdk.models

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.Json

class BootSourceTest : DescribeSpec({
    val json = Json { ignoreUnknownKeys = true }

    describe("BootSource") {
        describe("creation") {
            it("should create boot source with kernel only") {
                val bootSource = BootSource.kernel("/path/to/kernel")

                bootSource.kernelImagePath shouldBe "/path/to/kernel"
                bootSource.bootArgs shouldBe null
                bootSource.initrdPath shouldBe null
            }

            it("should create boot source with kernel and boot args") {
                val bootSource = BootSource.kernelWithArgs("/path/to/kernel", "console=ttyS0 reboot=k")

                bootSource.kernelImagePath shouldBe "/path/to/kernel"
                bootSource.bootArgs shouldBe "console=ttyS0 reboot=k"
                bootSource.initrdPath shouldBe null
            }

            it("should create boot source with kernel and initrd") {
                val bootSource = BootSource.kernelWithInitrd("/path/to/kernel", "/path/to/initrd")

                bootSource.kernelImagePath shouldBe "/path/to/kernel"
                bootSource.bootArgs shouldBe null
                bootSource.initrdPath shouldBe "/path/to/initrd"
            }

            it("should create boot source with kernel, initrd and boot args") {
                val bootSource =
                    BootSource.kernelWithInitrd(
                        "/path/to/kernel",
                        "/path/to/initrd",
                        "console=ttyS0",
                    )

                bootSource.kernelImagePath shouldBe "/path/to/kernel"
                bootSource.bootArgs shouldBe "console=ttyS0"
                bootSource.initrdPath shouldBe "/path/to/initrd"
            }
        }

        describe("validation") {
            it("should reject blank kernel path") {
                shouldThrow<IllegalArgumentException> {
                    BootSource(kernelImagePath = "")
                }.message shouldContain "Kernel image path cannot be blank"
            }

            it("should reject whitespace-only kernel path") {
                shouldThrow<IllegalArgumentException> {
                    BootSource(kernelImagePath = "   ")
                }.message shouldContain "Kernel image path cannot be blank"
            }
        }

        describe("serialization") {
            it("should serialize minimal boot source to JSON") {
                val bootSource = BootSource.kernel("/path/to/kernel")
                val jsonString = json.encodeToString(BootSource.serializer(), bootSource)

                jsonString shouldContain "\"kernel_image_path\":\"/path/to/kernel\""
            }

            it("should serialize complete boot source to JSON") {
                val bootSource =
                    BootSource(
                        kernelImagePath = "/path/to/kernel",
                        bootArgs = "console=ttyS0 reboot=k",
                        initrdPath = "/path/to/initrd",
                    )
                val jsonString = json.encodeToString(BootSource.serializer(), bootSource)

                jsonString shouldContain "\"kernel_image_path\":\"/path/to/kernel\""
                jsonString shouldContain "\"boot_args\":\"console=ttyS0 reboot=k\""
                jsonString shouldContain "\"initrd_path\":\"/path/to/initrd\""
            }

            it("should deserialize from JSON correctly") {
                val jsonString =
                    """
                    {
                        "kernel_image_path": "/path/to/kernel",
                        "boot_args": "console=ttyS0",
                        "initrd_path": "/path/to/initrd"
                    }
                    """.trimIndent()

                val bootSource = json.decodeFromString(BootSource.serializer(), jsonString)

                bootSource.kernelImagePath shouldBe "/path/to/kernel"
                bootSource.bootArgs shouldBe "console=ttyS0"
                bootSource.initrdPath shouldBe "/path/to/initrd"
            }
        }
    }
})
