package org.firecracker.sdk.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Boot source configuration for the virtual machine.
 *
 * The boot source defines the kernel image and optional boot parameters required to start
 * a Firecracker VM. This configuration specifies how the guest operating system will be
 * loaded and initialized during the VM boot process.
 *
 * Key components:
 * - **Kernel Image**: Uncompressed Linux kernel binary (required)
 * - **Boot Arguments**: Command-line parameters passed to the kernel during boot
 * - **Initial Ramdisk**: Optional initrd/initramfs for early boot filesystem
 *
 * Example usage:
 * ```kotlin
 * // Basic kernel-only boot
 * val bootSource = BootSource.create("/path/to/vmlinux")
 *
 * // Boot with arguments and initrd
 * val bootSource = BootSource.withInitrd(
 *     kernelPath = "/path/to/vmlinux",
 *     initrdPath = "/path/to/initrd.img",
 *     bootArgs = "console=ttyS0 root=/dev/vda1 rw"
 * )
 * ```
 *
 * @property kernelImagePath Absolute path to the uncompressed Linux kernel image file.
 *                           Must be accessible to the Firecracker process and point to a
 *                           valid ELF kernel binary compatible with x86_64 architecture.
 * @property bootArgs Optional kernel command-line arguments passed during boot initialization.
 *                    Common parameters include console configuration, root filesystem
 *                    specification, and kernel debugging options. If null, kernel defaults apply.
 * @property initrdPath Optional path to initial ramdisk (initrd/initramfs) file.
 *                      Used for early boot setup, driver loading, and filesystem preparation
 *                      before mounting the root filesystem. Must be accessible to Firecracker.
 * @see <a href="https://firecracker-microvm.github.io/">Firecracker Documentation</a>
 * @since 1.0.0
 */
@Serializable
data class BootSource(
    @SerialName("kernel_image_path")
    val kernelImagePath: String,
    @SerialName("boot_args")
    val bootArgs: String? = null,
    @SerialName("initrd_path")
    val initrdPath: String? = null,
) {
    init {
        require(kernelImagePath.isNotBlank()) {
            "Kernel image path cannot be blank"
        }
    }

    companion object {
        /**
         * Create a basic boot source with only a kernel image.
         *
         * Creates the simplest possible boot configuration using just the kernel binary.
         * The kernel will boot with its default parameters and no initial ramdisk.
         * Suitable for minimal VMs or when all required drivers are built into the kernel.
         *
         * @param kernelPath Absolute path to the uncompressed Linux kernel binary
         * @return BootSource configuration with kernel only
         * @throws IllegalArgumentException if kernelPath is blank or empty
         * @sample org.firecracker.sdk.models.BootSourceSamples.basicKernel
         */
        fun kernel(kernelPath: String): BootSource =
            BootSource(
                kernelImagePath = kernelPath,
            )

        /**
         * Create a boot source with kernel and custom boot arguments.
         *
         * Allows specification of kernel command-line parameters for customizing
         * the boot process. Common use cases include setting console output,
         * root filesystem, kernel logging levels, and hardware configurations.
         *
         * @param kernelPath Absolute path to the uncompressed Linux kernel binary
         * @param bootArgs Kernel command-line arguments (e.g., "console=ttyS0 root=/dev/vda1")
         * @return BootSource configuration with custom kernel arguments
         * @throws IllegalArgumentException if kernelPath is blank or empty
         * @sample org.firecracker.sdk.models.BootSourceSamples.kernelWithArgs
         */
        fun kernelWithArgs(
            kernelPath: String,
            bootArgs: String,
        ): BootSource =
            BootSource(
                kernelImagePath = kernelPath,
                bootArgs = bootArgs,
            )

        /**
         * Create a boot source with kernel and initial ramdisk.
         *
         * Enables early boot with an initrd/initramfs for loading essential drivers,
         * filesystem setup, or custom initialization scripts before mounting the
         * root filesystem. Essential for complex boot sequences or modular kernels.
         *
         * @param kernelPath Absolute path to the uncompressed Linux kernel binary
         * @param initrdPath Absolute path to the initial ramdisk image file
         * @return BootSource configuration with kernel and initrd
         * @throws IllegalArgumentException if kernelPath is blank or empty
         * @sample org.firecracker.sdk.models.BootSourceSamples.kernelWithInitrd
         */
        fun kernelWithInitrd(
            kernelPath: String,
            initrdPath: String,
            bootArgs: String? = null,
        ): BootSource =
            BootSource(
                kernelImagePath = kernelPath,
                bootArgs = bootArgs,
                initrdPath = initrdPath,
            )

        /**
         * Create a comprehensive boot source with all configuration options.
         *
         * Provides complete control over the VM boot process by specifying kernel,
         * initial ramdisk, and custom boot arguments. Recommended for production
         * environments requiring specific boot sequences or complex initialization.
         *
         * @param kernelPath Absolute path to the uncompressed Linux kernel binary
         * @param initrdPath Absolute path to the initial ramdisk image file
         * @param bootArgs Kernel command-line arguments for boot customization
         * @return Fully configured BootSource with all parameters specified
         * @throws IllegalArgumentException if kernelPath is blank or empty
         * @sample org.firecracker.sdk.models.BootSourceSamples.fullConfiguration
         */
        fun withInitrd(
            kernelPath: String,
            initrdPath: String,
            bootArgs: String,
        ): BootSource =
            BootSource(
                kernelImagePath = kernelPath,
                bootArgs = bootArgs,
                initrdPath = initrdPath,
            )

        /**
         * Create a boot source optimized for container workloads.
         *
         * Configures boot parameters commonly used for containerized applications
         * including console redirection, simplified filesystems, and fast boot options.
         * Ideal for microservice deployments and serverless functions.
         *
         * @param kernelPath Absolute path to the container-optimized kernel binary
         * @param rootDevice Root filesystem device specification (default: "/dev/vda1")
         * @return BootSource optimized for container execution environments
         * @throws IllegalArgumentException if kernelPath is blank or empty
         * @sample org.firecracker.sdk.models.BootSourceSamples.containerOptimized
         */
        fun forContainer(
            kernelPath: String,
            rootDevice: String = "/dev/vda1",
        ): BootSource =
            BootSource(
                kernelImagePath = kernelPath,
                bootArgs = "console=ttyS0 reboot=k panic=1 pci=off root=$rootDevice rw",
            )

        /**
         * Create a boot source with debugging enabled.
         *
         * Configures comprehensive kernel logging and debugging options for
         * development and troubleshooting scenarios. Enables detailed output
         * that helps diagnose boot issues and kernel behavior.
         *
         * @param kernelPath Absolute path to the kernel binary with debug symbols
         * @param logLevel Kernel log level for debugging (default: "debug")
         * @return BootSource with debugging and verbose logging enabled
         * @throws IllegalArgumentException if kernelPath is blank or empty
         * @sample org.firecracker.sdk.models.BootSourceSamples.debugEnabled
         */
        fun withDebugging(
            kernelPath: String,
            logLevel: String = "debug",
        ): BootSource =
            BootSource(
                kernelImagePath = kernelPath,
                bootArgs = "console=ttyS0 loglevel=8 debug earlyprintk=serial,$logLevel",
            )
    }
}
