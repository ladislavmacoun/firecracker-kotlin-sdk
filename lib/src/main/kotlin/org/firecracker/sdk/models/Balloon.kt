/*
 * Firecracker Kotlin SDK
 *
 * A Kotlin-first SDK for interacting with Firecracker microVMs,
 * with full Java interoperability.
 *
 * Author: Ladislav Macoun <lada@macoun.dev>
 */

package org.firecracker.sdk.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration for Firecracker memory balloon device.
 *
 * The balloon device enables dynamic memory management by allowing the host to reclaim
 * and return guest memory through API commands. The device allocates memory in the guest
 * and reports addresses to the host, enabling memory overcommit scenarios.
 *
 * Key features:
 * - Dynamic memory inflation/deflation based on target size
 * - Optional OOM (Out of Memory) protection through automatic deflation
 * - Statistics collection for monitoring memory usage patterns
 * - Virtio-based communication between host and guest
 *
 * Security considerations:
 * - Requires guest cooperation through virtio balloon driver
 * - Host should monitor actual memory usage beyond balloon statistics
 * - Guest driver compromise can bypass balloon restrictions
 *
 * @property amountMib Target balloon size in MiB (mebibytes)
 * @property deflateOnOom Whether to deflate balloon during guest OOM conditions
 * @property statsPollingIntervalS Statistics polling interval in seconds (0 disables stats)
 */
@Serializable
data class Balloon(
    @SerialName("amount_mib")
    val amountMib: Int,
    @SerialName("deflate_on_oom")
    val deflateOnOom: Boolean = false,
    @SerialName("stats_polling_interval_s")
    val statsPollingIntervalS: Int = 0,
) {
    init {
        require(amountMib >= 0) {
            "Balloon amount must be non-negative, got: $amountMib MiB"
        }
        require(statsPollingIntervalS >= 0) {
            "Statistics polling interval must be non-negative, got: $statsPollingIntervalS seconds"
        }
    }

    companion object {
        private const val DEFAULT_OVERCOMMIT_POLLING_INTERVAL_S = 5

        /**
         * Create a balloon device with the specified target size.
         *
         * Creates a basic balloon configuration with OOM protection disabled
         * and statistics collection disabled by default.
         *
         * @param amountMib Target balloon size in MiB
         * @return Balloon configuration with minimal settings
         * @throws IllegalArgumentException if amountMib is negative
         */
        fun create(amountMib: Int): Balloon =
            Balloon(
                amountMib = amountMib,
                deflateOnOom = false,
                statsPollingIntervalS = 0,
            )

        /**
         * Create a balloon device with OOM protection enabled.
         *
         * When deflateOnOom is true, the balloon will automatically release memory
         * to prevent guest OOM conditions. This helps maintain guest stability
         * at the cost of reduced memory efficiency.
         *
         * @param amountMib Target balloon size in MiB
         * @return Balloon configuration with OOM protection enabled
         * @throws IllegalArgumentException if amountMib is negative
         */
        fun withOomProtection(amountMib: Int): Balloon =
            Balloon(
                amountMib = amountMib,
                deflateOnOom = true,
                statsPollingIntervalS = 0,
            )

        /**
         * Create a balloon device with statistics collection enabled.
         *
         * Statistics provide insights into guest memory usage patterns including
         * page faults, swap activity, and memory availability. Useful for monitoring
         * and capacity planning.
         *
         * @param amountMib Target balloon size in MiB
         * @param pollingIntervalS Statistics polling interval in seconds (minimum 1)
         * @return Balloon configuration with statistics enabled
         * @throws IllegalArgumentException if amountMib is negative or pollingIntervalS is 0
         */
        fun withStatistics(
            amountMib: Int,
            pollingIntervalS: Int,
        ): Balloon {
            require(pollingIntervalS > 0) {
                "Statistics polling interval must be positive to enable collection, got: $pollingIntervalS"
            }
            return Balloon(
                amountMib = amountMib,
                deflateOnOom = false,
                statsPollingIntervalS = pollingIntervalS,
            )
        }

        /**
         * Create a fully configured balloon device with all options.
         *
         * Provides complete control over balloon behavior including target size,
         * OOM protection, and statistics collection. Recommended for production
         * environments requiring fine-grained memory management.
         *
         * @param amountMib Target balloon size in MiB
         * @param deflateOnOom Enable automatic deflation during OOM conditions
         * @param pollingIntervalS Statistics polling interval in seconds (0 disables)
         * @return Fully configured balloon device
         * @throws IllegalArgumentException if amountMib or pollingIntervalS is negative
         */
        fun withFullConfig(
            amountMib: Int,
            deflateOnOom: Boolean,
            pollingIntervalS: Int,
        ): Balloon =
            Balloon(
                amountMib = amountMib,
                deflateOnOom = deflateOnOom,
                statsPollingIntervalS = pollingIntervalS,
            )

        /**
         * Create a balloon for memory overcommit scenarios.
         *
         * Optimized for environments where host memory is limited and guest
         * memory needs to be dynamically managed. Includes OOM protection
         * and frequent statistics collection for monitoring.
         *
         * @param amountMib Initial balloon size in MiB
         * @return Balloon optimized for overcommit scenarios
         * @throws IllegalArgumentException if amountMib is negative
         */
        fun forOvercommit(amountMib: Int): Balloon =
            Balloon(
                amountMib = amountMib,
                deflateOnOom = true,
                // 5 second polling for active monitoring
                statsPollingIntervalS = DEFAULT_OVERCOMMIT_POLLING_INTERVAL_S,
            )

        /**
         * Create an empty balloon for initial VM setup.
         *
         * Creates a balloon with zero target size, suitable for VM initialization
         * where memory will be managed dynamically after boot. The balloon can
         * be resized later through PATCH operations.
         *
         * @return Empty balloon configuration
         */
        fun empty(): Balloon =
            Balloon(
                amountMib = 0,
                deflateOnOom = false,
                statsPollingIntervalS = 0,
            )
    }
}

/**
 * Memory balloon statistics from the virtio balloon device.
 *
 * Provides detailed insights into guest memory usage patterns, page fault statistics,
 * and balloon operational data. Statistics are collected at configurable intervals
 * and reflect the guest's perspective on memory utilization.
 *
 * Note: Statistics depend on proper guest driver functionality and should be viewed
 * as indicators rather than authoritative memory state information.
 *
 * @property targetPages Target balloon size in 4K pages
 * @property actualPages Current balloon size in 4K pages
 * @property targetMib Target balloon size in MiB
 * @property actualMib Current balloon size in MiB
 * @property swapIn Amount of memory swapped in (bytes)
 * @property swapOut Amount of memory swapped out (bytes)
 * @property majorFaults Number of major page faults
 * @property minorFaults Number of minor page faults
 * @property freeMemory Amount of unused memory (bytes)
 * @property totalMemory Total memory available (bytes)
 * @property availableMemory Memory available for new allocations (bytes)
 * @property diskCaches Memory used for disk caching (bytes)
 * @property hugetlbAllocations Successful hugetlb page allocations
 * @property hugetlbFailures Failed hugetlb page allocations
 */
@Serializable
data class BalloonStatistics(
    @SerialName("target_pages")
    val targetPages: UInt,
    @SerialName("actual_pages")
    val actualPages: UInt,
    @SerialName("target_mib")
    val targetMib: Int,
    @SerialName("actual_mib")
    val actualMib: Int,
    @SerialName("swap_in")
    val swapIn: ULong? = null,
    @SerialName("swap_out")
    val swapOut: ULong? = null,
    @SerialName("major_faults")
    val majorFaults: ULong? = null,
    @SerialName("minor_faults")
    val minorFaults: ULong? = null,
    @SerialName("free_mem")
    val freeMemory: ULong? = null,
    @SerialName("total_mem")
    val totalMemory: ULong? = null,
    @SerialName("available_mem")
    val availableMemory: ULong? = null,
    @SerialName("disk_caches")
    val diskCaches: ULong? = null,
    @SerialName("hugetlb_allocations")
    val hugetlbAllocations: ULong? = null,
    @SerialName("hugetlb_failures")
    val hugetlbFailures: ULong? = null,
) {
    /**
     * Calculate balloon efficiency as percentage of target achieved.
     *
     * @return Efficiency percentage (0.0 to 100.0)
     */
    val efficiency: Double
        get() =
            if (targetPages > 0u) {
                (actualPages.toDouble() / targetPages.toDouble()) * PERCENT_MULTIPLIER
            } else {
                FULL_EFFICIENCY_PERCENT
            }

    /**
     * Check if balloon has reached its target size.
     *
     * @return true if actual size matches target size
     */
    val isAtTarget: Boolean
        get() = actualPages == targetPages

    /**
     * Calculate memory pressure based on available memory and total memory.
     *
     * @return Memory pressure percentage (0.0 to 100.0), or null if data unavailable
     */
    val memoryPressure: Double?
        get() =
            if (totalMemory != null && availableMemory != null && totalMemory > 0UL) {
                ((totalMemory - availableMemory).toDouble() / totalMemory.toDouble()) * PERCENT_MULTIPLIER
            } else {
                null
            }

    companion object {
        private const val PERCENT_MULTIPLIER = 100.0
        private const val FULL_EFFICIENCY_PERCENT = 100.0
    }
}

/**
 * Request to update balloon configuration.
 *
 * Used for PATCH operations to modify balloon target size or statistics
 * polling interval during runtime. Only specified fields will be updated.
 *
 * @property amountMib New target balloon size in MiB (optional)
 * @property statsPollingIntervalS New statistics polling interval in seconds (optional)
 */
@Serializable
data class BalloonUpdate(
    @SerialName("amount_mib")
    val amountMib: Int? = null,
    @SerialName("stats_polling_interval_s")
    val statsPollingIntervalS: Int? = null,
) {
    init {
        amountMib?.let { amount ->
            require(amount >= 0) {
                "Balloon amount must be non-negative, got: $amount MiB"
            }
        }
        statsPollingIntervalS?.let { interval ->
            require(interval >= 0) {
                "Statistics polling interval must be non-negative, got: $interval seconds"
            }
        }
        require(amountMib != null || statsPollingIntervalS != null) {
            "At least one field must be specified for balloon update"
        }
    }

    companion object {
        /**
         * Create update request to change balloon target size.
         *
         * @param amountMib New target size in MiB
         * @return Update request for target size change
         * @throws IllegalArgumentException if amountMib is negative
         */
        fun targetSize(amountMib: Int): BalloonUpdate = BalloonUpdate(amountMib = amountMib)

        /**
         * Create update request to change statistics polling interval.
         *
         * @param intervalS New polling interval in seconds
         * @return Update request for statistics interval change
         * @throws IllegalArgumentException if intervalS is negative
         */
        fun pollingInterval(intervalS: Int): BalloonUpdate = BalloonUpdate(statsPollingIntervalS = intervalS)

        /**
         * Create update request to change both target size and polling interval.
         *
         * @param amountMib New target size in MiB
         * @param intervalS New polling interval in seconds
         * @return Update request for both fields
         * @throws IllegalArgumentException if either parameter is negative
         */
        fun both(
            amountMib: Int,
            intervalS: Int,
        ): BalloonUpdate =
            BalloonUpdate(
                amountMib = amountMib,
                statsPollingIntervalS = intervalS,
            )
    }
}
