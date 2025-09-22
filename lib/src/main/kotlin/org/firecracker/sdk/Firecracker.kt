package org.firecracker.sdk

import org.firecracker.sdk.models.MachineConfiguration

/**
 * Main entry point for the Firecracker SDK.
 *
 * Provides a clean, high-level API for managing Firecracker microVMs
 * without exposing low-level implementation details.
 */
object Firecracker {

    /**
     * Create a new VM builder with sensible defaults.
     *
     * Example usage:
     * ```kotlin
     * val vm = Firecracker.createVM {
     *     name = "my-vm"
     *     vcpus = 2
     *     memory = 1024
     *     kernel = "/path/to/kernel"
     *     rootfs = "/path/to/rootfs"
     * }
     *
     * vm.start()
     * ```
     */
    fun createVM(configure: VMBuilder.() -> Unit): VirtualMachine {
        val builder = VMBuilder()
        builder.configure()
        return builder.build()
    }

    /**
     * Connect to an existing Firecracker VM via socket path.
     */
    fun connectTo(socketPath: String): VirtualMachine {
        return VirtualMachine.fromSocket(socketPath)
    }
}

/**
 * Builder for creating virtual machine configurations with a fluent DSL.
 */
class VMBuilder {
    var name: String = "firecracker-vm"
    var vcpus: Int = 1
    var memory: Int = DEFAULT_MEMORY_MIB
    var kernel: String = ""
    var rootfs: String = ""
    var socketPath: String = "/tmp/firecracker-${System.currentTimeMillis()}.socket"

    // Advanced configuration
    var enableSmt: Boolean = false
    var trackDirtyPages: Boolean = false

    internal fun build(): VirtualMachine {
        require(kernel.isNotBlank()) { "Kernel path is required" }
        require(rootfs.isNotBlank()) { "Root filesystem path is required" }

        val config = MachineConfiguration(
            vcpuCount = vcpus,
            memSizeMib = memory,
            smt = enableSmt,
            trackDirtyPages = trackDirtyPages
        )

        return VirtualMachine(
            name = name,
            socketPath = socketPath,
            configuration = config,
            kernelPath = kernel,
            rootfsPath = rootfs
        )
    }

    companion object {
        private const val DEFAULT_MEMORY_MIB = 512
    }
}