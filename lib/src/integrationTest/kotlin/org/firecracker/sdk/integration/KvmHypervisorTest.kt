package org.firecracker.sdk.integration

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.firecracker.sdk.Firecracker
import java.io.File
import java.net.URI
import java.nio.file.Files

/**
 * KVM hypervisor integration test.
 *
 * This test runs actual Firecracker processes when the environment is available.
 * Requirements:
 * - Firecracker binary in PATH or /usr/local/bin/firecracker
 * - KVM support (/dev/kvm accessible)
 * - Root/sudo privileges (for VM operations)
 *
 * Set environment variable HYPERVISOR_SUPPORT_ENABLED=true to enable.
 */
class KvmHypervisorTest : DescribeSpec({

    describe("Firecracker Integration") {

        it("should create VM and verify configuration when environment is available") {
            // Only run if explicitly enabled and environment is ready
            if (!hasHypervisorSupport()) {
                println("⚠️ Hypervisor tests disabled or environment not ready")
                return@it
            }

            // Create a temporary socket path
            val tempDir = Files.createTempDirectory("firecracker-test")
            val apiSocketPath = tempDir.resolve("firecracker.sock").toString()

            try {
                println("🔥 Testing with Firecracker (simplified)")

                // Start Firecracker process in background
                val firecrackerBinary = findFirecrackerBinary()
                val process =
                    ProcessBuilder(
                        firecrackerBinary,
                        "--api-sock",
                        apiSocketPath,
                    ).start()

                // Give Firecracker a moment to start
                Thread.sleep(2000)

                // Download kernel and rootfs for testing
                val kernelPath = downloadTestKernel(tempDir)
                val rootfsPath = downloadTestRootfs(tempDir)

                // Test VM creation with actual kernel
                val vm =
                    Firecracker.createVM {
                        socketPath = apiSocketPath
                        memory = 128
                        vcpus = 1
                        kernel = kernelPath
                        rootDrive(rootfsPath)
                    }

                // Verify VM info
                val vmInfo = vm.getInfo()
                vmInfo.socketPath shouldBe apiSocketPath
                vmInfo.configuration.memSizeMib shouldBe 128
                vmInfo.configuration.vcpuCount shouldBe 1

                println("✅ Firecracker VM created successfully!")
                println("   Socket: ${vmInfo.socketPath}")
                println("   Memory: ${vmInfo.configuration.memSizeMib} MiB")
                println("   vCPUs: ${vmInfo.configuration.vcpuCount}")
                println("   Kernel: $kernelPath")
                println("   Rootfs: $rootfsPath")

                // Test starting the VM
                vm.start()
                println("✅ VM started successfully!")

                // Test pausing the VM
                vm.pause()
                println("✅ VM paused successfully!")

                // Test resuming the VM
                vm.resume()
                println("✅ VM resumed successfully!")

                // Clean shutdown
                process.destroyForcibly()
                process.waitFor()
            } finally {
                // Cleanup
                tempDir.toFile().deleteRecursively()
            }
        }
    }
}) {
    companion object {
        /**
         * Check if hypervisor support is available and enabled.
         */
        private fun hasHypervisorSupport(): Boolean {
            // Must be explicitly enabled
            val enabled = System.getenv("HYPERVISOR_SUPPORT_ENABLED")?.toBoolean() ?: false
            if (!enabled) return false

            // Check for Firecracker binary
            val binary = findFirecrackerBinary()
            if (binary == null) {
                println("⚠️ Firecracker binary not found")
                return false
            }

            // Check for KVM (not strictly required but recommended)
            val hasKvm = File("/dev/kvm").exists()
            if (!hasKvm) {
                println("⚠️ KVM not available (/dev/kvm not found)")
                println("   Tests may fail without hardware virtualization")
            }

            println("✅ Real Firecracker test environment ready:")
            println("   - Firecracker binary: $binary")
            println("   - KVM available: $hasKvm")

            return true
        }

        /**
         * Find Firecracker binary in common locations.
         */
        private fun findFirecrackerBinary(): String? {
            val locations =
                listOf(
                    "/usr/local/bin/firecracker",
                    "/usr/bin/firecracker",
                    // In PATH
                    "firecracker",
                )

            for (location in locations) {
                try {
                    val process = ProcessBuilder(location, "--version").start()
                    if (process.waitFor() == 0) {
                        return location
                    }
                } catch (e: Exception) {
                    // Continue searching
                }
            }

            return null
        }

        /**
         * Download test kernel for Firecracker.
         */
        private fun downloadTestKernel(tempDir: java.nio.file.Path): String {
            val kernelUrl = "https://s3.amazonaws.com/spec.ccfc.min/img/hello/kernel/hello-vmlinux.bin"
            val kernelPath = tempDir.resolve("hello-vmlinux.bin")

            if (!kernelPath.toFile().exists()) {
                println("📥 Downloading test kernel...")
                URI.create(kernelUrl).toURL().openStream().use { input ->
                    Files.copy(input, kernelPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }
                println("✅ Kernel downloaded: $kernelPath")
            }

            return kernelPath.toString()
        }

        /**
         * Download test rootfs for Firecracker.
         */
        private fun downloadTestRootfs(tempDir: java.nio.file.Path): String {
            val rootfsUrl = "https://s3.amazonaws.com/spec.ccfc.min/img/hello/fsfiles/hello-rootfs.ext4"
            val rootfsPath = tempDir.resolve("hello-rootfs.ext4")

            if (!rootfsPath.toFile().exists()) {
                println("📥 Downloading test rootfs...")
                URI.create(rootfsUrl).toURL().openStream().use { input ->
                    Files.copy(input, rootfsPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }
                println("✅ Rootfs downloaded: $rootfsPath")
            }

            return rootfsPath.toString()
        }
    }
}
