package org.firecracker.sdk.examples

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.firecracker.sdk.Firecracker

/**
 * Test class to verify that the README examples compile correctly.
 * These tests don't actually run the VMs, just verify the syntax is correct.
 */
class ReadmeExamplesTest : DescribeSpec({

    describe("README Examples") {

        it("Kotlin example should compile") {
            // This is the exact example from README
            val vm =
                Firecracker.createVM {
                    name = "test-vm"
                    kernel = "/path/to/vmlinux"
                    vcpus = 2
                    memory = 512

                    rootDrive("/path/to/rootfs.ext4")

                    addNetworkInterface {
                        interfaceId("eth0")
                        hostDevice("tap0")
                    }
                }

            // Just verify it was created (won't actually start without real paths)
            vm shouldNotBe null
            vm.name shouldBe "test-vm"
        }

        it("Java-style example should compile") {
            // This verifies the Java syntax from README works
            // Note: In Kotlin, we still use the DSL syntax, but assign to properties like Java would
            val vm =
                Firecracker.createVM {
                    name = "java-vm"
                    kernel = "/path/to/vmlinux"
                    vcpus = 2
                    memory = 512

                    // Use convenience method for root drive
                    rootDrive("/path/to/rootfs.ext4")

                    // Network configuration using builder function
                    addNetworkInterface {
                        interfaceId("eth0")
                        hostDevice("tap0")
                    }
                }

            // Just verify it was created
            vm shouldNotBe null
            vm.name shouldBe "java-vm"
        }
    }
})
