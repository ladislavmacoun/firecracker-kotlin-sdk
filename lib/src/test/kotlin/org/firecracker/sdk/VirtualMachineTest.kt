package org.firecracker.sdk

import io.kotest.assertions.fail
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.instanceOf
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
                    val result = vm.start()

                    result.isFailure shouldBe true
                    result.exceptionOrNull() shouldBe instanceOf<VMException>()
                }
            }

            it("should prevent invalid state transitions") {
                runTest {
                    val vm = createTestVM()

                    val result = vm.stop()

                    result.isFailure shouldBe true
                    val exception = result.exceptionOrNull()
                    exception shouldBe instanceOf<VMException>()
                    exception?.message shouldContain "Cannot stop VM in state: NOT_STARTED"
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

        describe("idiomatic Result API") {
            it("should support Result-based error handling") {
                runTest {
                    val vm = createTestVM()

                    // Test Result pattern for invalid operations
                    val stopResult = vm.stop()
                    stopResult.isFailure shouldBe true

                    // Test chaining with fold
                    val startResult = vm.start()
                    startResult.fold(
                        onSuccess = { fail("Should not succeed without actual Firecracker") },
                        onFailure = { error ->
                            error shouldBe instanceOf<VMException>()
                        },
                    )
                }
            }

            it("should support extension functions for handling") {
                runTest {
                    val vm = createTestVM()
                    var successCalled = false
                    var failureCalled = false

                    vm.startAndHandle(
                        onSuccess = { successCalled = true },
                        onFailure = { failureCalled = true },
                    )

                    successCalled shouldBe false
                    failureCalled shouldBe true
                }
            }

            it("should support operation chaining") {
                runTest {
                    val vm = createTestVM()

                    val result =
                        vm.start()
                            .flatMap { vm.waitForState(VirtualMachine.State.RUNNING, 100) }

                    result.isFailure shouldBe true
                }
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
                bootSource = org.firecracker.sdk.models.BootSource.kernel("/path/to/kernel"),
                drives = listOf(org.firecracker.sdk.models.Drive.rootDrive("root", "/path/to/rootfs")),
                networkInterfaces = emptyList(),
            )
        }
    }
}
