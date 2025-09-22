package org.firecracker.sdk.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Network interface configuration for Firecracker VMs.
 *
 * Represents a network interface that can be attached to a VM,
 * including tap device configuration and optional rate limiting.
 */
@Serializable
data class NetworkInterface(
    @SerialName("iface_id")
    val interfaceId: String,
    @SerialName("host_dev_name")
    val hostDeviceName: String,
    @SerialName("guest_mac")
    val guestMacAddress: String? = null,
    @SerialName("rx_rate_limiter")
    val rxRateLimiter: RateLimiter? = null,
    @SerialName("tx_rate_limiter")
    val txRateLimiter: RateLimiter? = null,
) {
    init {
        require(interfaceId.isNotBlank()) {
            "Interface ID cannot be blank"
        }
        require(hostDeviceName.isNotBlank()) {
            "Host device name cannot be blank"
        }
        guestMacAddress?.let { mac ->
            require(isValidMacAddress(mac)) {
                "Invalid MAC address format: $mac"
            }
        }
    }

    private fun isValidMacAddress(mac: String): Boolean {
        val macPattern = Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$")
        return mac.matches(macPattern)
    }
}

/**
 * Rate limiter configuration for network interfaces.
 */
@Serializable
data class RateLimiter(
    @SerialName("bandwidth")
    val bandwidth: Bandwidth? = null,
    @SerialName("ops")
    val operations: Operations? = null,
)

/**
 * Bandwidth limiting configuration.
 */
@Serializable
data class Bandwidth(
    @SerialName("size")
    val size: Long,
    @SerialName("one_time_burst")
    val oneTimeBurst: Long? = null,
    @SerialName("refill_time")
    val refillTimeMs: Long,
) {
    init {
        require(size > 0) {
            "Bandwidth size must be positive, got: $size"
        }
        require(refillTimeMs > 0) {
            "Refill time must be positive, got: $refillTimeMs"
        }
        oneTimeBurst?.let { burst ->
            require(burst >= 0) {
                "One time burst must be non-negative, got: $burst"
            }
        }
    }
}

/**
 * Operations per second limiting configuration.
 */
@Serializable
data class Operations(
    @SerialName("size")
    val size: Long,
    @SerialName("one_time_burst")
    val oneTimeBurst: Long? = null,
    @SerialName("refill_time")
    val refillTimeMs: Long,
) {
    init {
        require(size > 0) {
            "Operations size must be positive, got: $size"
        }
        require(refillTimeMs > 0) {
            "Refill time must be positive, got: $refillTimeMs"
        }
        oneTimeBurst?.let { burst ->
            require(burst >= 0) {
                "One time burst must be non-negative, got: $burst"
            }
        }
    }
}
