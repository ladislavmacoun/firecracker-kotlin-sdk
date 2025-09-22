package org.firecracker.sdk

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import org.firecracker.sdk.models.MachineConfiguration

class VirtualMachineTest : DescribeSpec({

    describe("VirtualMachine") {

        describe("lifecycle management") {
            it("should start with NOT_STARTED state") {
                val vm = createTestVM()

                vm.state shouldBe VirtualMachine.State.NOT_STARTED
                vm.isRunning shouldBe false
            }

            it("should handle start operation") {
                runTest {
                    val vm = createTestVM()

                    // Note: This will fail without actual Firecracker process
                    // but tests the API structure
                    shouldThrow<VMException> {
                        vm.start()
                    }
                }
            }

            it("should prevent invalid state transitions") {
                runTest {
                    val vm = createTestVM()

                    shouldThrow<VMException> {
                        vm.stop()
                    }.message shouldContain "Cannot stop VM in state: NOT_STARTED"
                }
            }
        }

        describe("VM information") {
            it("should provide VM info") {
                runTest {
                    val vm = createTestVM()

                    val info = vm.getInfo()

                    info.name shouldBe "test-vm"
                    info.state shouldBe VirtualMachine.State.NOT_STARTED
                    info.configuration shouldNotBe null
                    info.socketPath shouldContain "tmp"
                }
            }
        }

        describe("resource management") {
            it("should clean up resources") {
                val vm = createTestVM()

                // Should not throw
                vm.close()
            }
        }
    }
}) {
    companion object {
        private fun createTestVM(): VirtualMachine {
            return VirtualMachine(
                name = "test-vm",
                socketPath = "/tmp/test.socket",
                configuration = MachineConfiguration(vcpuCount = 2, memSizeMib = 1024),
                kernelPath = "/path/to/kernel",
                rootfsPath = "/path/to/rootfs"
            )
        }
    }
}