package org.firecracker.sdk.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * CPU Template defines a set of flags to be disabled from the microVM
 * so that the features exposed to the guest are the same as in the selected instance type.
 */
@Serializable
public enum class CpuTemplate {
    @SerialName("C3")
    C3,

    @SerialName("T2")
    T2,

    @SerialName("T2S")
    T2S,

    @SerialName("T2CL")
    T2CL,

    @SerialName("T2A")
    T2A,

    @SerialName("V1N1")
    V1N1,

    @SerialName("None")
    None,
}

/**
 * Huge pages configuration for backing guest memory.
 */
@Serializable
public enum class HugePages {
    @SerialName("None")
    None,

    @SerialName("2M")
    M2,
}

/**
 * Machine configuration describing the number of vCPUs, memory size,
 * SMT capabilities, huge page configuration and CPU template.
 */
@Serializable
public data class MachineConfiguration(
    @SerialName("vcpu_count")
    val vcpuCount: Int,
    @SerialName("mem_size_mib")
    val memSizeMib: Int,
    @SerialName("smt")
    val smt: Boolean = false,
    @SerialName("track_dirty_pages")
    val trackDirtyPages: Boolean = false,
    @SerialName("cpu_template")
    val cpuTemplate: CpuTemplate = CpuTemplate.None,
    @SerialName("huge_pages")
    val hugePages: HugePages = HugePages.None,
) {
    init {
        validateConfiguration()
    }

    private fun validateConfiguration() {
        require(vcpuCount in MIN_VCPU_COUNT..MAX_VCPU_COUNT) {
            "vCPU count must be between $MIN_VCPU_COUNT and $MAX_VCPU_COUNT, got: $vcpuCount"
        }

        require(memSizeMib > 0) {
            "Memory size must be positive, got: $memSizeMib MiB"
        }

        if (smt && vcpuCount > 1) {
            require(vcpuCount % EVEN_DIVISOR == 0) {
                "When SMT is enabled, vCPU count must be 1 or an even number, got: $vcpuCount"
            }
        }

        if (hugePages == HugePages.M2) {
            require(memSizeMib % EVEN_DIVISOR == 0) {
                "When using 2M huge pages, memory size must be a multiple of $EVEN_DIVISOR, got: $memSizeMib MiB"
            }
        }
    }

    companion object {
        private const val MIN_VCPU_COUNT = 1
        private const val MAX_VCPU_COUNT = 32
        private const val EVEN_DIVISOR = 2
    }
}
