package org.firecracker.sdk.metrics

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.nio.file.Paths
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Comprehensive metrics configuration with advanced collection and export capabilities.
 *
 * Provides fine-grained control over metrics collection, aggregation, and export
 * for monitoring, alerting, and performance analysis of Firecracker VMs.
 */
@Serializable
data class MetricsConfiguration(
    @SerialName("metrics_path")
    val metricsPath: String,
    @SerialName("collection_interval")
    val collectionInterval: Long = 1000,
    @SerialName("retention_period")
    val retentionPeriod: Long = 3600000,
    @SerialName("enabled_metrics")
    val enabledMetrics: Set<MetricType> = MetricType.entries.toSet(),
    @SerialName("aggregation_window")
    val aggregationWindow: Long = 60000,
    @SerialName("export_format")
    val exportFormat: MetricsFormat = MetricsFormat.JSON,
) {
    init {
        require(metricsPath.isNotBlank()) { "Metrics path cannot be blank" }
        require(collectionInterval > 0) { "Collection interval must be positive" }
        require(retentionPeriod > 0) { "Retention period must be positive" }
        require(aggregationWindow > 0) { "Aggregation window must be positive" }
        require(enabledMetrics.isNotEmpty()) { "At least one metric type must be enabled" }
    }

    /**
     * Convert to basic Metrics configuration for Firecracker API.
     */
    fun toMetrics(): org.firecracker.sdk.models.Metrics =
        org.firecracker.sdk.models.Metrics(
            metricsPath = metricsPath,
        )

    companion object {
        /**
         * Create development metrics configuration with comprehensive collection.
         */
        fun development(
            metricsPath: String = "/tmp/firecracker-metrics.json",
            vmId: String? = null,
        ): MetricsConfiguration {
            val finalPath =
                vmId?.let {
                    val path = Paths.get(metricsPath)
                    val parent = path.parent ?: Paths.get("/tmp")
                    val name = path.fileName.toString()
                    val baseName = name.substringBeforeLast('.')
                    val extension = name.substringAfterLast('.', "json")
                    parent.resolve("$baseName-$vmId.$extension").toString()
                } ?: metricsPath

            return MetricsConfiguration(
                metricsPath = finalPath,
                collectionInterval = 1000,
                retentionPeriod = 1800000,
                enabledMetrics = MetricType.entries.toSet(),
                aggregationWindow = 10000,
                exportFormat = MetricsFormat.JSON,
            )
        }

        /**
         * Create production metrics configuration optimized for performance.
         */
        fun production(
            metricsPath: String = "/var/log/firecracker/metrics.json",
            vmId: String? = null,
        ): MetricsConfiguration {
            val finalPath =
                vmId?.let {
                    val path = Paths.get(metricsPath)
                    val parent = path.parent ?: Paths.get("/var/log/firecracker")
                    val name = path.fileName.toString()
                    val baseName = name.substringBeforeLast('.')
                    val extension = name.substringAfterLast('.', "json")
                    parent.resolve("$baseName-$vmId.$extension").toString()
                } ?: metricsPath

            return MetricsConfiguration(
                metricsPath = finalPath,
                collectionInterval = 5000,
                retentionPeriod = 7200000,
                enabledMetrics =
                    setOf(
                        MetricType.CPU_UTILIZATION,
                        MetricType.MEMORY_UTILIZATION,
                        MetricType.NETWORK_IO,
                        MetricType.DISK_IO,
                        MetricType.API_REQUESTS,
                    ),
                aggregationWindow = 60000,
                exportFormat = MetricsFormat.PROMETHEUS,
            )
        }

        /**
         * Create monitoring-focused metrics configuration.
         */
        fun monitoring(
            metricsPath: String = "/var/log/firecracker/monitoring.json",
            vmId: String? = null,
        ): MetricsConfiguration {
            val finalPath =
                vmId?.let {
                    val path = Paths.get(metricsPath)
                    val parent = path.parent ?: Paths.get("/var/log/firecracker")
                    val name = path.fileName.toString()
                    val baseName = name.substringBeforeLast('.')
                    val extension = name.substringAfterLast('.', "json")
                    parent.resolve("$baseName-$vmId.$extension").toString()
                } ?: metricsPath

            return MetricsConfiguration(
                metricsPath = finalPath,
                collectionInterval = 2000,
                retentionPeriod = 14400000,
                enabledMetrics =
                    setOf(
                        MetricType.CPU_UTILIZATION,
                        MetricType.MEMORY_UTILIZATION,
                        MetricType.MEMORY_BALLOON,
                        MetricType.NETWORK_IO,
                        MetricType.DISK_IO,
                        MetricType.GUEST_MEMORY,
                        MetricType.API_REQUESTS,
                        MetricType.SECCOMP_FILTERS,
                    ),
                aggregationWindow = 30000,
                exportFormat = MetricsFormat.JSON,
            )
        }

        /**
         * Create performance analysis metrics configuration.
         */
        fun performance(
            metricsPath: String = "/tmp/firecracker-perf.json",
            vmId: String? = null,
        ): MetricsConfiguration {
            val finalPath =
                vmId?.let {
                    val path = Paths.get(metricsPath)
                    val parent = path.parent ?: Paths.get("/tmp")
                    val name = path.fileName.toString()
                    val baseName = name.substringBeforeLast('.')
                    val extension = name.substringAfterLast('.', "json")
                    parent.resolve("$baseName-$vmId.$extension").toString()
                } ?: metricsPath

            return MetricsConfiguration(
                metricsPath = finalPath,
                collectionInterval = 100,
                retentionPeriod = 900000,
                enabledMetrics = MetricType.entries.toSet(),
                aggregationWindow = 1000,
                exportFormat = MetricsFormat.CSV,
            )
        }

        /**
         * Create minimal metrics configuration for resource-constrained environments.
         */
        fun minimal(
            metricsPath: String = "/tmp/firecracker-basic.json",
            vmId: String? = null,
        ): MetricsConfiguration {
            val finalPath =
                vmId?.let {
                    val path = Paths.get(metricsPath)
                    val parent = path.parent ?: Paths.get("/tmp")
                    val name = path.fileName.toString()
                    val baseName = name.substringBeforeLast('.')
                    val extension = name.substringAfterLast('.', "json")
                    parent.resolve("$baseName-$vmId.$extension").toString()
                } ?: metricsPath

            return MetricsConfiguration(
                metricsPath = finalPath,
                collectionInterval = 10000,
                retentionPeriod = 1800000,
                enabledMetrics =
                    setOf(
                        MetricType.CPU_UTILIZATION,
                        MetricType.MEMORY_UTILIZATION,
                        MetricType.API_REQUESTS,
                    ),
                aggregationWindow = 300000,
                exportFormat = MetricsFormat.JSON,
            )
        }
    }
}

/**
 * Available metric types for collection.
 */
@Serializable
enum class MetricType {
    @SerialName("cpu_utilization")
    CPU_UTILIZATION,

    @SerialName("memory_utilization")
    MEMORY_UTILIZATION,

    @SerialName("memory_balloon")
    MEMORY_BALLOON,

    @SerialName("guest_memory")
    GUEST_MEMORY,

    @SerialName("network_io")
    NETWORK_IO,

    @SerialName("disk_io")
    DISK_IO,

    @SerialName("api_requests")
    API_REQUESTS,

    @SerialName("vm_state_transitions")
    VM_STATE_TRANSITIONS,

    @SerialName("device_events")
    DEVICE_EVENTS,

    @SerialName("seccomp_filters")
    SECCOMP_FILTERS,

    @SerialName("signal_handling")
    SIGNAL_HANDLING,

    @SerialName("exit_codes")
    EXIT_CODES,
}

/**
 * Supported metrics export formats.
 */
@Serializable
enum class MetricsFormat {
    @SerialName("json")
    JSON,

    @SerialName("prometheus")
    PROMETHEUS,

    @SerialName("csv")
    CSV,

    @SerialName("influxdb")
    INFLUXDB,

    @SerialName("statsd")
    STATSD,
}

/**
 * Metrics aggregation functions.
 */
@Serializable
enum class AggregationFunction {
    @SerialName("avg")
    AVERAGE,

    @SerialName("sum")
    SUM,

    @SerialName("min")
    MINIMUM,

    @SerialName("max")
    MAXIMUM,

    @SerialName("count")
    COUNT,

    @SerialName("p50")
    PERCENTILE_50,

    @SerialName("p95")
    PERCENTILE_95,

    @SerialName("p99")
    PERCENTILE_99,
}

/**
 * Metrics collection policy configuration.
 */
data class MetricsPolicy(
    val bufferSize: Int = 1000,
    val flushInterval: Duration = 30.seconds,
    val errorRetryCount: Int = 3,
    val compressionEnabled: Boolean = true,
    val alertThresholds: Map<MetricType, Double> = emptyMap(),
) {
    init {
        require(bufferSize > 0) { "Buffer size must be positive" }
        require(!flushInterval.isNegative()) { "Flush interval cannot be negative" }
        require(errorRetryCount >= 0) { "Error retry count cannot be negative" }
    }

    companion object {
        private const val CPU_THRESHOLD = 80.0
        private const val MEMORY_THRESHOLD = 85.0

        /**
         * Default policy for development environments.
         */
        fun development(): MetricsPolicy =
            MetricsPolicy(
                bufferSize = 100,
                flushInterval = 10.seconds,
                errorRetryCount = 1,
                compressionEnabled = false,
                alertThresholds = emptyMap(),
            )

        /**
         * Policy optimized for production environments.
         */
        fun production(): MetricsPolicy =
            MetricsPolicy(
                bufferSize = 5000,
                flushInterval = 60.seconds,
                errorRetryCount = 5,
                compressionEnabled = true,
                alertThresholds =
                    mapOf(
                        MetricType.CPU_UTILIZATION to CPU_THRESHOLD,
                        MetricType.MEMORY_UTILIZATION to MEMORY_THRESHOLD,
                    ),
            )

        /**
         * High-throughput policy for performance monitoring.
         */
        fun highThroughput(): MetricsPolicy =
            MetricsPolicy(
                bufferSize = 10000,
                flushInterval = 5.seconds,
                errorRetryCount = 3,
                compressionEnabled = true,
                alertThresholds = emptyMap(),
            )
    }
}

/**
 * Real-time metrics streaming configuration.
 */
data class MetricsStreaming(
    val enabled: Boolean = false,
    val endpoint: String? = null,
    val protocol: StreamingProtocol = StreamingProtocol.HTTP,
    val authentication: StreamingAuth? = null,
    val batchSize: Int = 100,
    val streamingInterval: Duration = 5.seconds,
) {
    init {
        if (enabled) {
            require(!endpoint.isNullOrBlank()) { "Endpoint is required when streaming is enabled" }
        }
        require(batchSize > 0) { "Batch size must be positive" }
        require(!streamingInterval.isNegative()) { "Streaming interval cannot be negative" }
    }
}

/**
 * Streaming protocols for real-time metrics.
 */
enum class StreamingProtocol {
    HTTP,
    HTTPS,
    WEBSOCKET,
    TCP,
    UDP,
}

/**
 * Authentication configuration for metrics streaming.
 */
sealed class StreamingAuth {
    data class ApiKey(val key: String) : StreamingAuth()

    data class Bearer(val token: String) : StreamingAuth()

    data class Basic(val username: String, val password: String) : StreamingAuth()
}
