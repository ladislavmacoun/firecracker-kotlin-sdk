package org.firecracker.sdk.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Boot source configuration for the virtual machine.
 *
 * @property kernelImagePath Path to the kernel image (required)
 * @property bootArgs Additional kernel boot arguments (optional)
 * @property initrdPath Path to the initial ramdisk (optional)
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
         * Create a basic boot source with just a kernel image.
         */
        fun kernel(kernelPath: String): BootSource =
            BootSource(
                kernelImagePath = kernelPath,
            )

        /**
         * Create a boot source with kernel and boot arguments.
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
         * Create a boot source with kernel and initrd.
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
    }
}
