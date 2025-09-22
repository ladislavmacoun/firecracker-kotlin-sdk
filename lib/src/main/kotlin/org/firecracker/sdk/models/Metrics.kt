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
 * Firecracker metrics configuration.
 *
 * Controls metrics collection and output for the Firecracker VMM process.
 * Metrics include CPU usage, memory usage, network I/O, disk I/O, and other
 * performance indicators useful for monitoring VM performance.
 *
 * @property metricsPath Path to the file where metrics will be written
 */
@Serializable
data class Metrics(
    @SerialName("metrics_path")
    val metricsPath: String,
) {
    init {
        require(metricsPath.isNotBlank()) { "Metrics path cannot be blank" }
    }

    companion object {
        /**
         * Create a metrics configuration.
         *
         * @param metricsPath Path where metrics file will be created
         * @return Metrics configuration
         */
        fun create(metricsPath: String): Metrics =
            Metrics(
                metricsPath = metricsPath,
            )

        /**
         * Create metrics configuration for a specific VM instance.
         *
         * @param vmId Unique identifier for the VM
         * @param baseDir Directory where metrics files are stored
         * @return Metrics configuration with generated filename
         */
        fun forVm(
            vmId: String,
            baseDir: String = "/tmp",
        ): Metrics {
            require(vmId.isNotBlank()) { "VM ID cannot be blank" }
            return Metrics(
                metricsPath = "$baseDir/firecracker-metrics-$vmId.json",
            )
        }

        /**
         * Create metrics configuration with timestamp.
         *
         * @param baseDir Directory where metrics files are stored
         * @return Metrics configuration with timestamped filename
         */
        fun withTimestamp(baseDir: String = "/tmp"): Metrics {
            val timestamp = System.currentTimeMillis()
            return Metrics(
                metricsPath = "$baseDir/firecracker-metrics-$timestamp.json",
            )
        }
    }
}
