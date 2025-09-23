package org.firecracker.sdk.snapshot

import org.firecracker.sdk.models.SnapshotCreateParams
import org.firecracker.sdk.models.SnapshotLoadParams
import org.firecracker.sdk.models.SnapshotType
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Comprehensive snapshot configuration with advanced management capabilities.
 *
 * This configuration class provides high-level snapshot management features including:
 * - Automatic file naming and organization
 * - Snapshot policies and retention
 * - Migration and backup strategies
 * - Performance optimization settings
 *
 * ## Features
 *
 * **Smart File Management**: Automatic path generation with timestamps and VM identifiers
 * **Snapshot Policies**: Configurable retention policies and cleanup strategies
 * **Migration Support**: Cross-host VM migration through portable snapshots
 * **Performance Tuning**: Compression and incremental snapshot optimizations
 *
 * @property baseDirectory Base directory for all snapshot files
 * @property vmId Unique identifier for the VM (used in filenames)
 * @property snapshotType Default snapshot type (Full or Differential)
 * @property enableCompression Whether to compress snapshot files
 * @property retentionPolicy Automatic cleanup policy for old snapshots
 * @property namingStrategy Strategy for generating snapshot filenames
 * @property metadata Additional metadata to store with snapshots
 */
data class SnapshotConfiguration(
    val baseDirectory: String = "/tmp/snapshots",
    val vmId: String,
    val snapshotType: SnapshotType = SnapshotType.Full,
    val enableCompression: Boolean = false,
    val retentionPolicy: RetentionPolicy = RetentionPolicy.default(),
    val namingStrategy: NamingStrategy = NamingStrategy.Timestamp,
    val metadata: Map<String, String> = emptyMap(),
) {
    init {
        require(vmId.isNotBlank()) { "VM ID cannot be blank" }
        require(baseDirectory.isNotBlank()) { "Base directory cannot be blank" }
    }

    /**
     * Generate a unique snapshot creation configuration.
     *
     * @param type Override snapshot type for this operation
     * @param suffix Optional suffix for the snapshot name
     * @return SnapshotCreateParams with generated paths
     */
    fun createSnapshot(
        type: SnapshotType = snapshotType,
        suffix: String? = null,
    ): SnapshotCreateParams {
        val snapshotName = generateSnapshotName(suffix)
        val snapshotPath = "$baseDirectory/$snapshotName.json"
        val memoryPath = "$baseDirectory/$snapshotName.mem"

        return SnapshotCreateParams(
            snapshotType = type,
            snapshotPath = snapshotPath,
            memFilePath = memoryPath,
        )
    }

    /**
     * Generate snapshot load configuration for an existing snapshot.
     *
     * @param snapshotName Name of the snapshot to load (without extension)
     * @param resumeVm Whether to resume the VM after loading
     * @param enableDiffSnapshots Whether to enable differential snapshots after loading
     * @return SnapshotLoadParams for the specified snapshot
     */
    fun loadSnapshot(
        snapshotName: String,
        resumeVm: Boolean = false,
        enableDiffSnapshots: Boolean = false,
    ): SnapshotLoadParams {
        val snapshotPath = "$baseDirectory/$snapshotName.json"
        val memoryPath = "$baseDirectory/$snapshotName.mem"

        return SnapshotLoadParams(
            snapshotPath = snapshotPath,
            memFilePath = memoryPath,
            resumeVm = resumeVm,
            enableDiffSnapshots = enableDiffSnapshots,
        )
    }

    /**
     * Create a backup snapshot configuration for migration.
     *
     * @param targetDirectory Directory on target host for migration
     * @param migrationId Unique identifier for this migration
     * @return SnapshotCreateParams optimized for migration
     */
    fun createMigrationSnapshot(
        targetDirectory: String,
        migrationId: String = generateMigrationId(),
    ): SnapshotCreateParams {
        val snapshotName = "migration-$vmId-$migrationId"
        val snapshotPath = "$targetDirectory/$snapshotName.json"
        val memoryPath = "$targetDirectory/$snapshotName.mem"

        return SnapshotCreateParams(
            // Always full for migration
            snapshotType = SnapshotType.Full,
            snapshotPath = snapshotPath,
            memFilePath = memoryPath,
        )
    }

    /**
     * Get a list of existing snapshots for this VM.
     *
     * @return List of snapshot names (without extensions) found in the base directory
     */
    fun listSnapshots(): List<String> {
        val directory = Paths.get(baseDirectory)
        if (!directory.toFile().exists()) return emptyList()

        return directory.toFile()
            .listFiles { _, name -> name.endsWith(".json") && name.contains(vmId) }
            ?.map { it.nameWithoutExtension }
            ?.sorted()
            ?: emptyList()
    }

    /**
     * Get snapshot file paths for a given snapshot name.
     *
     * @param snapshotName Name of the snapshot
     * @return Pair of (snapshot metadata path, memory dump path)
     */
    fun getSnapshotPaths(snapshotName: String): Pair<String, String> {
        return Pair(
            "$baseDirectory/$snapshotName.json",
            "$baseDirectory/$snapshotName.mem",
        )
    }

    /**
     * Apply retention policy to clean up old snapshots.
     *
     * @return List of snapshot names that were marked for cleanup
     */
    fun applyRetentionPolicy(): List<String> {
        val snapshots = listSnapshots()
        return retentionPolicy.getSnapshotsToCleanup(snapshots)
    }

    private fun generateSnapshotName(suffix: String?): String {
        val timestamp =
            when (namingStrategy) {
                NamingStrategy.Timestamp ->
                    DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
                        .format(LocalDateTime.now())
                NamingStrategy.Sequential -> System.currentTimeMillis().toString()
            }

        return if (suffix != null) {
            "snapshot-$vmId-$timestamp-$suffix"
        } else {
            "snapshot-$vmId-$timestamp"
        }
    }

    private fun generateMigrationId(): String {
        return DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now())
    }

    companion object {
        // Default retention counts for different strategies
        private const val DEVELOPMENT_RETENTION_COUNT = 10
        private const val PRODUCTION_RETENTION_DAYS = 30
        private const val BACKUP_RETENTION_DAYS = 90
        private const val TESTING_RETENTION_COUNT = 3
        private const val MIGRATION_RETENTION_COUNT = 1
        
        /**
         * Create development snapshot configuration.
         *
         * Optimized for development workflow with frequent snapshots.
         */
        fun development(
            vmId: String,
            baseDir: String = "/tmp/dev-snapshots",
        ): SnapshotConfiguration =
            SnapshotConfiguration(
                baseDirectory = baseDir,
                vmId = vmId,
                snapshotType = SnapshotType.Diff,
                enableCompression = false,
                retentionPolicy = RetentionPolicy.keepRecent(DEVELOPMENT_RETENTION_COUNT),
                namingStrategy = NamingStrategy.Timestamp,
                metadata = mapOf("environment" to "development"),
            )

        /**
         * Create production snapshot configuration.
         *
         * Optimized for production with full snapshots and longer retention.
         */
        fun production(
            vmId: String,
            baseDir: String = "/opt/snapshots",
        ): SnapshotConfiguration =
            SnapshotConfiguration(
                baseDirectory = baseDir,
                vmId = vmId,
                snapshotType = SnapshotType.Full,
                enableCompression = true,
                retentionPolicy = RetentionPolicy.keepForDays(PRODUCTION_RETENTION_DAYS),
                namingStrategy = NamingStrategy.Timestamp,
                metadata = mapOf("environment" to "production"),
            )

        /**
         * Create backup snapshot configuration.
         *
         * Optimized for long-term backup and disaster recovery.
         */
        fun backup(
            vmId: String,
            baseDir: String = "/backup/snapshots",
        ): SnapshotConfiguration =
            SnapshotConfiguration(
                baseDirectory = baseDir,
                vmId = vmId,
                snapshotType = SnapshotType.Full,
                enableCompression = true,
                retentionPolicy = RetentionPolicy.keepForDays(BACKUP_RETENTION_DAYS),
                namingStrategy = NamingStrategy.Timestamp,
                metadata = mapOf("purpose" to "backup"),
            )

        /**
         * Create migration snapshot configuration.
         *
         * Optimized for VM migration across hosts.
         */
        fun migration(
            vmId: String,
            baseDir: String = "/tmp/migration",
        ): SnapshotConfiguration =
            SnapshotConfiguration(
                baseDirectory = baseDir,
                vmId = vmId,
                snapshotType = SnapshotType.Full,
                enableCompression = true,
                // Only keep latest migration
                retentionPolicy = RetentionPolicy.keepRecent(MIGRATION_RETENTION_COUNT),
                namingStrategy = NamingStrategy.Timestamp,
                metadata = mapOf("purpose" to "migration"),
            )

        /**
         * Create testing snapshot configuration.
         *
         * Optimized for testing with quick snapshots and aggressive cleanup.
         */
        fun testing(
            vmId: String,
            baseDir: String = "/tmp/test-snapshots",
        ): SnapshotConfiguration =
            SnapshotConfiguration(
                baseDirectory = baseDir,
                vmId = vmId,
                snapshotType = SnapshotType.Diff,
                enableCompression = false,
                retentionPolicy = RetentionPolicy.keepRecent(TESTING_RETENTION_COUNT),
                namingStrategy = NamingStrategy.Sequential,
                metadata = mapOf("environment" to "testing"),
            )
    }
}

/**
 * Snapshot file naming strategies.
 */
enum class NamingStrategy {
    /** Use timestamp-based naming (human-readable) */
    Timestamp,

    /** Use sequential numbering (for performance) */
    Sequential,
}

/**
 * Retention policy for automatic snapshot cleanup.
 *
 * @property maxSnapshots Maximum number of snapshots to keep
 * @property maxAgeDays Maximum age of snapshots in days
 */
data class RetentionPolicy(
    val maxSnapshots: Int? = null,
    val maxAgeDays: Int? = null,
) {
    /**
     * Determine which snapshots should be cleaned up based on this policy.
     *
     * @param snapshots List of snapshot names
     * @return List of snapshot names to clean up
     */
    fun getSnapshotsToCleanup(snapshots: List<String>): List<String> {
        val sortedSnapshots = snapshots.sorted()
        val toCleanup = mutableListOf<String>()

        // Apply max snapshots policy
        maxSnapshots?.let { max ->
            if (sortedSnapshots.size > max) {
                toCleanup.addAll(sortedSnapshots.take(sortedSnapshots.size - max))
            }
        }

        // Apply max age policy
        maxAgeDays?.let {
            // Note: Age-based cleanup would require parsing timestamp from filename
            // For now, we'll skip age-based cleanup in this simple implementation
            // Future enhancement: parse timestamp from filename and apply age filter
        }

        return toCleanup.distinct()
    }

    companion object {
        private const val DEFAULT_RETENTION_COUNT = 5
        
        /** Default retention policy (keep 5 most recent snapshots) */
        fun default(): RetentionPolicy = RetentionPolicy(maxSnapshots = DEFAULT_RETENTION_COUNT)

        /** Keep only the N most recent snapshots */
        fun keepRecent(count: Int): RetentionPolicy = RetentionPolicy(maxSnapshots = count)

        /** Keep snapshots for a specified number of days */
        fun keepForDays(days: Int): RetentionPolicy = RetentionPolicy(maxAgeDays = days)

        /** Keep snapshots for a specified number of days AND maximum count */
        fun keepRecentAndForDays(
            count: Int,
            days: Int,
        ): RetentionPolicy = RetentionPolicy(maxSnapshots = count, maxAgeDays = days)

        /** Keep all snapshots (no cleanup) */
        fun keepAll(): RetentionPolicy = RetentionPolicy()
    }
}

/**
 * Snapshot management strategy enum for easy configuration.
 */
enum class SnapshotStrategy {
    DEVELOPMENT,
    PRODUCTION,
    BACKUP,
    MIGRATION,
    TESTING,
}

/**
 * Information about a snapshot.
 */
data class SnapshotInfo(
    val name: String,
    val vmId: String,
    val type: SnapshotType,
    val createdAt: LocalDateTime,
    val snapshotPath: String,
    val memoryPath: String,
    val sizeBytes: Long,
    val metadata: Map<String, String> = emptyMap(),
)
