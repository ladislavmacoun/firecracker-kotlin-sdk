package org.firecracker.sdk

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain

class FirecrackerTest : DescribeSpec({

    describe("Firecracker SDK") {

        describe("VM creation") {
            it("should create VM with DSL builder") {
                val vm =
                    Firecracker.createVM {
                        name = "test-vm"
                        vcpus = 4
                        memory = 2048
                        kernel = "/path/to/kernel"
                        rootDrive("/path/to/rootfs")
                        enableSmt = true
                    }

                vm shouldNotBe null
                vm.name shouldBe "test-vm"
                vm.state shouldBe VirtualMachine.State.NOT_STARTED
                vm.isRunning shouldBe false
            }

            it("should require kernel path") {
                shouldThrow<IllegalArgumentException> {
                    Firecracker.createVM {
                        name = "test-vm"
                        vcpus = 2
                        memory = 1024
                        rootDrive("/path/to/rootfs")
                        // kernel not set
                    }
                }.message shouldContain "Kernel path is required"
            }

            it("should provide sensible defaults") {
                val vm =
                    Firecracker.createVM {
                        name = "minimal-vm"
                        kernel = "/path/to/kernel"
                        rootDrive("/path/to/rootfs")
                    }

                vm.name shouldBe "minimal-vm"
                // Other defaults should be applied
            }
        }

        describe("VM connection") {
            it("should connect to existing VM") {
                val vm = Firecracker.connectTo("/tmp/existing.socket")

                vm shouldNotBe null
                vm.name shouldBe "existing-vm"
            }
        }
    }
})
