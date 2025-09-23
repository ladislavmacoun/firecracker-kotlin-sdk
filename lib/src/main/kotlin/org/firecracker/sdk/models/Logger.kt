package org.firecracker.sdk.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Logging level enumeration for Firecracker.
 */
@Serializable
enum class LogLevel {
    @SerialName("Error")
    Error,

    @SerialName("Warn")
    Warn,

    @SerialName("Info")
    Info,

    @SerialName("Debug")
    Debug,

    @SerialName("Trace")
    Trace,
}

/**
 * Firecracker logger configuration.
 *
 * Controls logging output for the Firecracker VMM process, including
 * log level filtering and output destination.
 *
 * @property logPath Path to the log file where logs will be written
 * @property level Minimum log level that will be recorded
 * @property showLevel Whether to include log level in output (default: false)
 * @property showLogOrigin Whether to include code location in output (default: false)
 */
@Serializable
data class Logger(
    @SerialName("log_path")
    val logPath: String,
    @SerialName("level")
    val level: LogLevel = LogLevel.Warn,
    @SerialName("show_level")
    val showLevel: Boolean = false,
    @SerialName("show_log_origin")
    val showLogOrigin: Boolean = false,
) {
    init {
        require(logPath.isNotBlank()) { "Log path cannot be blank" }
    }

    companion object {
        /**
         * Create a logger configuration with default settings.
         *
         * @param logPath Path where log file will be created
         * @return Logger configuration with Warn level
         */
        fun create(logPath: String): Logger =
            Logger(
                logPath = logPath,
                level = LogLevel.Warn,
            )

        /**
         * Create a debug logger configuration for development.
         *
         * @param logPath Path where log file will be created
         * @return Logger configuration with Debug level and detailed output
         */
        fun debug(logPath: String): Logger =
            Logger(
                logPath = logPath,
                level = LogLevel.Debug,
                showLevel = true,
                showLogOrigin = true,
            )

        /**
         * Create a trace logger configuration for detailed debugging.
         *
         * @param logPath Path where log file will be created
         * @return Logger configuration with Trace level and full details
         */
        fun trace(logPath: String): Logger =
            Logger(
                logPath = logPath,
                level = LogLevel.Trace,
                showLevel = true,
                showLogOrigin = true,
            )

        /**
         * Create a production logger configuration.
         *
         * @param logPath Path where log file will be created
         * @return Logger configuration with Error level only
         */
        fun production(logPath: String): Logger =
            Logger(
                logPath = logPath,
                level = LogLevel.Error,
                showLevel = false,
                showLogOrigin = false,
            )
    }
}
