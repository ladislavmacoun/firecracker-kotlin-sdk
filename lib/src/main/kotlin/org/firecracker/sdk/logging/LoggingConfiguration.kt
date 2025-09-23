package org.firecracker.sdk.logging

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.firecracker.sdk.models.LogLevel
import java.nio.file.Paths
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Structured logging configuration with advanced features.
 *
 * Provides comprehensive logging configuration including log rotation,
 * filtering, structured output, and integration with monitoring systems.
 */
@Serializable
data class LoggingConfiguration(
    @SerialName("log_path")
    val logPath: String,
    @SerialName("level")
    val level: LogLevel = LogLevel.Info,
    @SerialName("show_level")
    val showLevel: Boolean = true,
    @SerialName("show_log_origin")
    val showLogOrigin: Boolean = false,
    @SerialName("structured")
    val structured: Boolean = false,
    @SerialName("component_filter")
    val componentFilter: Set<String> = emptySet(),
    @SerialName("exclude_filter")
    val excludeFilter: Set<String> = emptySet(),
) {
    init {
        require(logPath.isNotBlank()) { "Log path cannot be blank" }
    }

    /**
     * Convert to basic Logger configuration for Firecracker API.
     */
    fun toLogger(): org.firecracker.sdk.models.Logger =
        org.firecracker.sdk.models.Logger(
            logPath = logPath,
            level = level,
            showLevel = showLevel,
            showLogOrigin = showLogOrigin,
        )

    companion object {
        /**
         * Create development logging configuration with comprehensive output.
         */
        fun development(
            logPath: String = "/tmp/firecracker-dev.log",
            vmId: String? = null,
        ): LoggingConfiguration {
            val finalPath =
                vmId?.let {
                    val path = Paths.get(logPath)
                    val parent = path.parent ?: Paths.get("/tmp")
                    val name = path.fileName.toString()
                    val baseName = name.substringBeforeLast('.')
                    val extension = name.substringAfterLast('.', "log")
                    parent.resolve("$baseName-$vmId.$extension").toString()
                } ?: logPath

            return LoggingConfiguration(
                logPath = finalPath,
                level = LogLevel.Debug,
                showLevel = true,
                showLogOrigin = true,
                structured = false,
                componentFilter = emptySet(),
                excludeFilter = emptySet(),
            )
        }

        /**
         * Create production logging configuration optimized for performance.
         */
        fun production(
            logPath: String = "/var/log/firecracker/firecracker.log",
            vmId: String? = null,
        ): LoggingConfiguration {
            val finalPath =
                vmId?.let {
                    val path = Paths.get(logPath)
                    val parent = path.parent ?: Paths.get("/var/log/firecracker")
                    val name = path.fileName.toString()
                    val baseName = name.substringBeforeLast('.')
                    val extension = name.substringAfterLast('.', "log")
                    parent.resolve("$baseName-$vmId.$extension").toString()
                } ?: logPath

            return LoggingConfiguration(
                logPath = finalPath,
                level = LogLevel.Warn,
                showLevel = false,
                showLogOrigin = false,
                structured = true,
                componentFilter = emptySet(),
                excludeFilter = setOf("debug", "trace"),
            )
        }

        /**
         * Create monitoring-optimized logging configuration.
         */
        fun monitoring(
            logPath: String = "/var/log/firecracker/monitoring.log",
            vmId: String? = null,
        ): LoggingConfiguration {
            val finalPath =
                vmId?.let {
                    val path = Paths.get(logPath)
                    val parent = path.parent ?: Paths.get("/var/log/firecracker")
                    val name = path.fileName.toString()
                    val baseName = name.substringBeforeLast('.')
                    val extension = name.substringAfterLast('.', "log")
                    parent.resolve("$baseName-$vmId.$extension").toString()
                } ?: logPath

            return LoggingConfiguration(
                logPath = finalPath,
                level = LogLevel.Info,
                showLevel = true,
                showLogOrigin = false,
                structured = true,
                componentFilter = setOf("metrics", "network", "block", "seccomp"),
                excludeFilter = emptySet(),
            )
        }

        /**
         * Create debugging logging configuration with verbose output.
         */
        fun debugging(
            logPath: String = "/tmp/firecracker-debug.log",
            vmId: String? = null,
            components: Set<String> = emptySet(),
        ): LoggingConfiguration {
            val finalPath =
                vmId?.let {
                    val path = Paths.get(logPath)
                    val parent = path.parent ?: Paths.get("/tmp")
                    val name = path.fileName.toString()
                    val baseName = name.substringBeforeLast('.')
                    val extension = name.substringAfterLast('.', "log")
                    parent.resolve("$baseName-$vmId.$extension").toString()
                } ?: logPath

            return LoggingConfiguration(
                logPath = finalPath,
                level = LogLevel.Trace,
                showLevel = true,
                showLogOrigin = true,
                structured = false,
                componentFilter = components,
                excludeFilter = emptySet(),
            )
        }

        /**
         * Create security-focused logging configuration.
         */
        fun security(
            logPath: String = "/var/log/firecracker/security.log",
            vmId: String? = null,
        ): LoggingConfiguration {
            val finalPath =
                vmId?.let {
                    val path = Paths.get(logPath)
                    val parent = path.parent ?: Paths.get("/var/log/firecracker")
                    val name = path.fileName.toString()
                    val baseName = name.substringBeforeLast('.')
                    val extension = name.substringAfterLast('.', "log")
                    parent.resolve("$baseName-$vmId.$extension").toString()
                } ?: logPath

            return LoggingConfiguration(
                logPath = finalPath,
                level = LogLevel.Warn,
                showLevel = true,
                showLogOrigin = true,
                structured = true,
                componentFilter = setOf("seccomp", "jailer", "device", "api"),
                excludeFilter = emptySet(),
            )
        }
    }
}

/**
 * Log rotation configuration for managing log file size and retention.
 */
data class LogRotationConfig(
    val maxSize: Long = DEFAULT_MAX_SIZE,
    val maxFiles: Int = DEFAULT_MAX_FILES,
    val rotateInterval: Duration = 24.hours,
    val compressOld: Boolean = true,
) {
    companion object {
        private const val DEFAULT_MAX_SIZE = 100L * 1024 * 1024
        private const val DEFAULT_MAX_FILES = 5
        private const val DEV_MAX_SIZE = 50L * 1024 * 1024
        private const val DEV_MAX_FILES = 3
        private const val PROD_MAX_SIZE = 200L * 1024 * 1024
        private const val PROD_MAX_FILES = 10
        private const val HIGH_VOLUME_MAX_SIZE = 1024L * 1024 * 1024
        private const val HIGH_VOLUME_MAX_FILES = 20

        /**
         * Default log rotation for development environments.
         */
        fun development(): LogRotationConfig =
            LogRotationConfig(
                maxSize = DEV_MAX_SIZE,
                maxFiles = DEV_MAX_FILES,
                rotateInterval = 12.hours,
                compressOld = false,
            )

        /**
         * Log rotation configuration for production environments.
         */
        fun production(): LogRotationConfig =
            LogRotationConfig(
                maxSize = PROD_MAX_SIZE,
                maxFiles = PROD_MAX_FILES,
                rotateInterval = 24.hours,
                compressOld = true,
            )

        /**
         * Log rotation for high-volume logging scenarios.
         */
        fun highVolume(): LogRotationConfig =
            LogRotationConfig(
                maxSize = HIGH_VOLUME_MAX_SIZE,
                maxFiles = HIGH_VOLUME_MAX_FILES,
                rotateInterval = 6.hours,
                compressOld = true,
            )
    }

    init {
        require(maxSize > 0) { "Max size must be positive" }
        require(maxFiles > 0) { "Max files must be positive" }
        require(!rotateInterval.isNegative()) { "Rotate interval cannot be negative" }
    }
}

/**
 * Structured logging format configuration.
 */
enum class LogFormat {
    /** Plain text format for human readability */
    PLAIN,

    /** JSON format for structured processing */
    JSON,

    /** Logfmt format for key-value pairs */
    LOGFMT,

    /** Syslog RFC5424 format */
    SYSLOG,
}

/**
 * Log output destination configuration.
 */
sealed class LogOutput {
    /** Output to a file */
    data class File(val path: String, val rotation: LogRotationConfig? = null) : LogOutput()

    /** Output to standard streams */
    data class Console(val stderr: Boolean = false) : LogOutput()

    /** Output to syslog */
    data class Syslog(val facility: String = "local0", val tag: String = "firecracker") : LogOutput()

    /** Output to multiple destinations */
    data class Multiple(val outputs: List<LogOutput>) : LogOutput()
}
