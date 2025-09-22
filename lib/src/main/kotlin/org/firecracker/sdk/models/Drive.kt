package org.firecracker.sdk.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Drive configuration for the virtual machine.
 *
 * @property driveId Unique identifier for the drive (required)
 * @property pathOnHost Path to the backing file on the host (required)
 * @property isRootDevice Whether this drive is the root device (default: false)
 * @property isReadOnly Whether the drive is read-only (default: false)
 * @property cacheType Cache type for the drive (default: Unsafe)
 * @property rateLimiter Rate limiter configuration for the drive (optional)
 */
@Serializable
data class Drive(
    @SerialName("drive_id")
    val driveId: String,
    @SerialName("path_on_host")
    val pathOnHost: String,
    @SerialName("is_root_device")
    val isRootDevice: Boolean = false,
    @SerialName("is_read_only")
    val isReadOnly: Boolean = false,
    @SerialName("cache_type")
    val cacheType: CacheType = CacheType.UNSAFE,
    @SerialName("rate_limiter")
    val rateLimiter: DriveRateLimiter? = null,
) {
    init {
        require(driveId.isNotBlank()) {
            "Drive ID cannot be blank"
        }
        require(pathOnHost.isNotBlank()) {
            "Path on host cannot be blank"
        }
    }

    companion object {
        /**
         * Create a root drive configuration.
         */
        fun rootDrive(
            driveId: String,
            pathOnHost: String,
            isReadOnly: Boolean = false,
        ): Drive =
            Drive(
                driveId = driveId,
                pathOnHost = pathOnHost,
                isRootDevice = true,
                isReadOnly = isReadOnly,
            )

        /**
         * Create a data drive configuration.
         */
        fun dataDrive(
            driveId: String,
            pathOnHost: String,
            isReadOnly: Boolean = false,
            cacheType: CacheType = CacheType.UNSAFE,
        ): Drive =
            Drive(
                driveId = driveId,
                pathOnHost = pathOnHost,
                isRootDevice = false,
                isReadOnly = isReadOnly,
                cacheType = cacheType,
            )
    }
}

/**
 * Cache type for drives.
 */
@Serializable
enum class CacheType {
    @SerialName("Unsafe")
    UNSAFE,

    @SerialName("Writeback")
    WRITEBACK,
}

/**
 * Rate limiter configuration for drives.
 *
 * @property bandwidth Token bucket bandwidth configuration
 * @property ops Token bucket operations configuration
 */
@Serializable
data class DriveRateLimiter(
    val bandwidth: TokenBucket? = null,
    val ops: TokenBucket? = null,
)

/**
 * Token bucket configuration for rate limiting.
 *
 * @property oneTimeBurst Initial burst budget (required)
 * @property refillTime Interval between refills in milliseconds (required)
 * @property size Token bucket size (required)
 */
@Serializable
data class TokenBucket(
    @SerialName("one_time_burst")
    val oneTimeBurst: Long,
    @SerialName("refill_time")
    val refillTime: Long,
    val size: Long,
) {
    init {
        require(oneTimeBurst >= 0) {
            "One time burst must be non-negative"
        }
        require(refillTime > 0) {
            "Refill time must be positive"
        }
        require(size > 0) {
            "Size must be positive"
        }
    }

    companion object {
        /**
         * Create a token bucket with basic configuration.
         */
        fun create(
            size: Long,
            oneTimeBurst: Long = size,
            refillTimeMs: Long = 100,
        ): TokenBucket =
            TokenBucket(
                oneTimeBurst = oneTimeBurst,
                refillTime = refillTimeMs,
                size = size,
            )
    }
}
