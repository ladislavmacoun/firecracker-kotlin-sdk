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
 * Snapshot type enumeration.
 */
@Serializable
enum class SnapshotType {
    @SerialName("Full")
    Full,

    @SerialName("Diff")
    Diff,
}

/**
 * Configuration for creating a VM snapshot.
 *
 * Snapshots capture the complete state of a running VM including
 * memory, device states, and CPU registers, allowing for later restoration.
 *
 * @property snapshotType Type of snapshot to create (Full or Diff)
 * @property snapshotPath Path where the snapshot file will be saved
 * @property memFilePath Path where the memory dump will be saved
 * @property version Snapshot format version (optional)
 */
@Serializable
data class SnapshotCreateParams(
    @SerialName("snapshot_type")
    val snapshotType: SnapshotType,
    @SerialName("snapshot_path")
    val snapshotPath: String,
    @SerialName("mem_file_path")
    val memFilePath: String,
    @SerialName("version")
    val version: String? = null,
) {
    init {
        require(snapshotPath.isNotBlank()) { "Snapshot path cannot be blank" }
        require(memFilePath.isNotBlank()) { "Memory file path cannot be blank" }
    }

    companion object {
        /**
         * Create a full snapshot configuration.
         *
         * @param snapshotPath Path for snapshot metadata file
         * @param memFilePath Path for memory dump file
         * @return Full snapshot configuration
         */
        fun full(
            snapshotPath: String,
            memFilePath: String,
        ): SnapshotCreateParams =
            SnapshotCreateParams(
                snapshotType = SnapshotType.Full,
                snapshotPath = snapshotPath,
                memFilePath = memFilePath,
            )

        /**
         * Create a differential snapshot configuration.
         *
         * @param snapshotPath Path for snapshot metadata file
         * @param memFilePath Path for memory dump file
         * @return Differential snapshot configuration
         */
        fun diff(
            snapshotPath: String,
            memFilePath: String,
        ): SnapshotCreateParams =
            SnapshotCreateParams(
                snapshotType = SnapshotType.Diff,
                snapshotPath = snapshotPath,
                memFilePath = memFilePath,
            )

        /**
         * Create snapshot configuration for a specific VM.
         *
         * @param vmId Unique identifier for the VM
         * @param baseDir Directory where snapshot files are stored
         * @param type Type of snapshot to create
         * @return Snapshot configuration with generated filenames
         */
        fun forVm(
            vmId: String,
            baseDir: String = "/tmp",
            type: SnapshotType = SnapshotType.Full,
        ): SnapshotCreateParams {
            require(vmId.isNotBlank()) { "VM ID cannot be blank" }
            val timestamp = System.currentTimeMillis()
            return SnapshotCreateParams(
                snapshotType = type,
                snapshotPath = "$baseDir/snapshot-$vmId-$timestamp.json",
                memFilePath = "$baseDir/memory-$vmId-$timestamp.mem",
            )
        }
    }
}

/**
 * Configuration for loading a VM from snapshot.
 *
 * Allows restoring a VM from a previously created snapshot,
 * resuming execution from the exact state when the snapshot was taken.
 *
 * @property snapshotPath Path to the snapshot metadata file
 * @property memFilePath Path to the memory dump file
 * @property enableDiffSnapshots Whether to enable differential snapshots after loading
 * @property resumeVm Whether to resume the VM after loading (default: false)
 */
@Serializable
data class SnapshotLoadParams(
    @SerialName("snapshot_path")
    val snapshotPath: String,
    @SerialName("mem_file_path")
    val memFilePath: String,
    @SerialName("enable_diff_snapshots")
    val enableDiffSnapshots: Boolean = false,
    @SerialName("resume_vm")
    val resumeVm: Boolean = false,
) {
    init {
        require(snapshotPath.isNotBlank()) { "Snapshot path cannot be blank" }
        require(memFilePath.isNotBlank()) { "Memory file path cannot be blank" }
    }

    companion object {
        /**
         * Create snapshot load configuration.
         *
         * @param snapshotPath Path to snapshot metadata file
         * @param memFilePath Path to memory dump file
         * @return Snapshot load configuration
         */
        fun create(
            snapshotPath: String,
            memFilePath: String,
        ): SnapshotLoadParams =
            SnapshotLoadParams(
                snapshotPath = snapshotPath,
                memFilePath = memFilePath,
            )

        /**
         * Create snapshot load configuration with resume.
         *
         * @param snapshotPath Path to snapshot metadata file
         * @param memFilePath Path to memory dump file
         * @param enableDiffSnapshots Whether to enable differential snapshots
         * @return Snapshot load configuration that resumes the VM
         */
        fun loadAndResume(
            snapshotPath: String,
            memFilePath: String,
            enableDiffSnapshots: Boolean = false,
        ): SnapshotLoadParams =
            SnapshotLoadParams(
                snapshotPath = snapshotPath,
                memFilePath = memFilePath,
                enableDiffSnapshots = enableDiffSnapshots,
                resumeVm = true,
            )
    }
}
