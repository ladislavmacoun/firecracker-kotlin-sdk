package org.firecracker.sdk

import org.firecracker.sdk.logging.LoggingConfiguration
import org.firecracker.sdk.metrics.MetricsConfiguration
import org.firecracker.sdk.models.Balloon
import org.firecracker.sdk.models.Bandwidth
import org.firecracker.sdk.models.BootSource
import org.firecracker.sdk.models.Drive
import org.firecracker.sdk.models.Logger
import org.firecracker.sdk.models.MachineConfiguration
import org.firecracker.sdk.models.Metrics
import org.firecracker.sdk.models.NetworkInterface
import org.firecracker.sdk.models.Operations
import org.firecracker.sdk.models.RateLimiter
import org.firecracker.sdk.models.VSock

/**
 * Main entry point for the Firecracker Kotlin/Java SDK.
 *
 * Provides a comprehensive, type-safe API for creating and managing Firecracker microVMs
 * with minimal boilerplate and maximum ergonomics. Built with Kotlin-first design principles
 * while maintaining full Java interoperability.
 *
 * ## Key Features
 *
 * **Type-Safe VM Creation**: Fluent DSL builder pattern with compile-time validation
 * - Comprehensive device configuration (drives, network, balloon, VSock)
 * - Built-in validation and sensible defaults
 * - Zero-copy serialization with kotlinx.serialization
 *
 * **Async/Await Support**: Native Kotlin coroutines integration
 * - Non-blocking VM operations with structured concurrency
 * - Functional error handling with Result<T> types
 * - Cancellation support for long-running operations
 *
 * **Production Ready**: Enterprise-grade reliability and observability
 * - Comprehensive logging and metrics collection
 * - Connection pooling and retry mechanisms
 * - Memory-efficient resource management
 *
 * ## Quick Start
 *
 * ### Basic VM Creation
 * ```kotlin
 * val vm = Firecracker.createVM {
 *     name = "web-server"
 *     vcpus = 2
 *     memory = 1024
 *     kernel = "/path/to/vmlinux"
 *     rootDrive("/path/to/rootfs.ext4")
 * }
 *
 * vm.start().getOrThrow()
 * ```
 *
 * ### Advanced Configuration
 * ```kotlin
 * val vm = Firecracker.createVM {
 *     name = "microservice"
 *     vcpus = 4
 *     memory = 2048
 *     kernel = "/boot/vmlinux-microvm"
 *
 *     // Boot configuration
 *     bootArgs = "console=ttyS0 root=/dev/vda1 rw"
 *     initrd = "/boot/initrd.img"
 *
 *     // Storage
 *     rootDrive("/data/rootfs.ext4", isReadOnly = false)
 *     addDrive {
 *         driveId = "data"
 *         pathOnHost = "/data/app-data.ext4"
 *         isRootDevice = false
 *
 *         // Rate limiting
 *         rateLimiter {
 *             bandwidth { size = 10_000_000; refillTime = 100 }
 *             ops { size = 1000; refillTime = 100 }
 *         }
 *     }
 *
 *     // Networking with rate limiting
 *     addNetworkInterface {
 *         interfaceId = "eth0"
 *         hostDeviceName = "tap0"
 *         guestMacAddress = "AA:BB:CC:DD:EE:FF"
 *
 *         rxRateLimit {
 *             bandwidth { size = 100_000_000; refillTime = 100 }
 *         }
 *     }
 *
 *     // Memory management
 *     overcommitBalloon(512) // Start with 512MB balloon for overcommit
 *
 *     // Host-guest communication
 *     autoVSock(guestCid = 42u)
 *
 *     // Observability
 *     debugLogging("/var/log/firecracker.log")
 *     autoMetrics("/var/metrics")
 * }
 * ```
 *
 * ### Java Interoperability
 * ```java
 * VirtualMachine vm = Firecracker.createVM(builder -> {
 *     builder.setName("java-vm");
 *     builder.setVcpus(2);
 *     builder.setMemory(1024);
 *     builder.setKernel("/path/to/kernel");
 *     builder.rootDrive("/path/to/rootfs.ext4");
 * });
 *
 * vm.start()
 *   .onSuccess(result -> System.out.println("Started!"))
 *   .onFailure(error -> System.err.println("Failed: " + error));
 * ```
 *
 * ## Best Practices
 *
 * **Resource Management**: Always clean up resources to prevent leaks
 * ```kotlin
 * vm.use {
 *     it.start().getOrThrow()
 *     // VM operations...
 * } // Automatically closed
 * ```
 *
 * **Error Handling**: Use Result<T> for functional error handling
 * ```kotlin
 * vm.start()
 *     .onSuccess { println("VM started successfully") }
 *     .onFailure { error ->
 *         when (error) {
 *             is ClientError.ConnectionFailed -> retry()
 *             is ClientError.InvalidState -> recreateVM()
 *             else -> logError(error)
 *         }
 *     }
 * ```
 *
 * **Memory Management**: Monitor and adjust balloon device dynamically
 * ```kotlin
 * val stats = vm.getBalloonStatistics().getOrNull()
 * if (stats?.memoryPressure ?: 0.0 > 80.0) {
 *     vm.deflateBalloon(stats.targetMib - 128) // Release 128MB
 * }
 * ```
 *
 * @see VirtualMachine Core VM lifecycle management
 * @see VMBuilder Fluent configuration builder
 * @see <a href="https://firecracker-microvm.github.io/">Firecracker Project</a>
 * @since 1.0.0
 * @author Firecracker Kotlin SDK Team
 */
object Firecracker {
    /**
     * Create a new virtual machine using the fluent configuration DSL.
     *
     * Provides a type-safe, ergonomic way to configure Firecracker VMs with comprehensive
     * validation and sensible defaults. The builder pattern ensures all required
     * configuration is provided while making optional settings discoverable through IDE.
     *
     * ## Configuration Categories
     *
     * **Core Settings**: CPU, memory, and basic VM parameters
     * **Boot Configuration**: Kernel, initrd, and boot arguments
     * **Storage**: Root and additional block devices with rate limiting
     * **Networking**: Interface configuration with bandwidth controls
     * **Memory Management**: Balloon device for dynamic memory allocation
     * **Communication**: VSock devices for host-guest interaction
     * **Observability**: Logging, metrics, and debugging options
     *
     * ## Example Configurations
     *
     * **Minimal Setup**:
     * ```kotlin
     * val vm = Firecracker.createVM {
     *     name = "simple-vm"
     *     kernel = "/boot/vmlinux"
     *     rootDrive("/data/rootfs.ext4")
     * }
     * ```
     *
     * **Development Environment**:
     * ```kotlin
     * val vm = Firecracker.createVM {
     *     name = "dev-env"
     *     vcpus = 4
     *     memory = 4096
     *     kernel = "/boot/vmlinux-dev"
     *     bootArgs = "console=ttyS0 root=/dev/vda1 rw debug"
     *
     *     rootDrive("/data/dev-rootfs.ext4")
     *     addDrive {
     *         driveId = "home"
     *         pathOnHost = "/data/home.ext4"
     *     }
     *
     *     // Network with debugging
     *     addNetworkInterface {
     *         interfaceId = "eth0"
     *         hostDeviceName = "tap-dev"
     *     }
     *
     *     debugLogging("/var/log/vm-dev.log")
     *     balloonWithStats(1024, pollingIntervalS = 1)
     * }
     * ```
     *
     * **Production Microservice**:
     * ```kotlin
     * val vm = Firecracker.createVM {
     *     name = "payment-service"
     *     vcpus = 2
     *     memory = 1024
     *     enableSmt = false // Security: disable hyperthreading
     *
     *     kernel = "/boot/vmlinux-prod"
     *     bootArgs = "console=ttyS0 root=/dev/vda1 ro quiet"
     *
     *     rootDrive("/images/payment-service.ext4", isReadOnly = true)
     *
     *     addNetworkInterface {
     *         interfaceId = "eth0"
     *         hostDeviceName = "tap-payment"
     *         guestMacAddress = "AA:BB:CC:DD:EE:01"
     *
     *         // Production rate limits
     *         rxRateLimit {
     *             bandwidth {
     *                 size = 50_000_000 // 50 Mbps
     *                 refillTime = 100
     *             }
     *         }
     *         txRateLimit {
     *             ops {
     *                 size = 5000 // 5K packets/sec
     *                 refillTime = 100
     *             }
     *         }
     *     }
     *
     *     // Memory overcommit for cost efficiency
     *     forOvercommitBalloon(256)
     *
     *     // Production monitoring
     *     autoMetrics("/metrics")
     *     logging("/var/log/payment-service.log", LogLevel.Info)
     * }
     * ```
     *
     * ## Validation and Defaults
     *
     * The builder performs comprehensive validation:
     * - **Required fields**: Kernel path must be specified
     * - **Resource limits**: vCPU count (1-32), memory size validation
     * - **Path validation**: File paths must be accessible to Firecracker
     * - **Network validation**: MAC address format, interface naming
     * - **Device limits**: Maximum drive/interface counts per VM
     *
     * Default values are applied for optional settings:
     * - vCPUs: 1, Memory: 128 MiB
     * - SMT: disabled, Dirty page tracking: disabled
     * - Socket path: auto-generated in /tmp
     * - Device caching: enabled for performance
     *
     * @param configure Lambda function to configure the VM using the builder DSL
     * @return Configured VirtualMachine instance ready for lifecycle operations
     * @throws IllegalArgumentException if required configuration is missing or invalid
     * @throws IllegalStateException if builder configuration conflicts detected
     * @see VMBuilder Available configuration options
     * @see VirtualMachine.start Starting the configured VM
     * @sample org.firecracker.sdk.samples.FirecrackerSamples.basicVMCreation
     * @since 1.0.0
     */
    fun createVM(configure: VMBuilder.() -> Unit): VirtualMachine {
        val builder = VMBuilder()
        builder.configure()
        return builder.build()
    }

    /**
     * Connect to an existing Firecracker VM via socket path.
     */
    fun connectTo(socketPath: String): VirtualMachine {
        return VirtualMachine.fromSocket(socketPath)
    }
}

/**
 * Builder for creating virtual machine configurations with a fluent DSL.
 */
@Suppress("TooManyFunctions") // Builder pattern requires multiple configuration methods
class VMBuilder {
    var name: String = "firecracker-vm"
    var vcpus: Int = 1
    var memory: Int = DEFAULT_MEMORY_MIB
    var kernel: String = ""
    var bootArgs: String? = null
    var initrd: String? = null
    var socketPath: String = "/tmp/firecracker-${System.currentTimeMillis()}.socket"

    // Advanced configuration
    var enableSmt: Boolean = false
    var trackDirtyPages: Boolean = false

    // Network configuration
    private val networkInterfaces = mutableListOf<NetworkInterface>()

    // Drive configuration
    private val drives = mutableListOf<Drive>()

    // Logging and metrics
    var logger: Logger? = null
    var metrics: Metrics? = null

    // VSock configuration
    var vsock: VSock? = null

    // Balloon configuration
    var balloon: Balloon? = null

    /**
     * Add a network interface to the VM configuration.
     */
    fun addNetworkInterface(configure: NetworkInterfaceBuilder.() -> Unit) {
        val builder = NetworkInterfaceBuilder()
        builder.configure()
        networkInterfaces.add(builder.build())
    }

    /**
     * Add a drive to the VM configuration.
     */
    fun addDrive(configure: DriveBuilder.() -> Unit) {
        val builder = DriveBuilder()
        builder.configure()
        drives.add(builder.build())
    }

    /**
     * Add a root drive with the specified path.
     */
    fun rootDrive(
        path: String,
        isReadOnly: Boolean = false,
    ) {
        drives.add(Drive.rootDrive("root", path, isReadOnly))
    }

    /**
     * Configure logging for the Firecracker VMM process.
     */
    fun logging(
        logPath: String,
        level: org.firecracker.sdk.models.LogLevel = org.firecracker.sdk.models.LogLevel.Warn,
        showLevel: Boolean = false,
        showLogOrigin: Boolean = false,
    ) {
        logger =
            Logger(
                logPath = logPath,
                level = level,
                showLevel = showLevel,
                showLogOrigin = showLogOrigin,
            )
    }

    /**
     * Configure debug logging with detailed output.
     */
    fun debugLogging(logPath: String) {
        logger = Logger.debug(logPath)
    }

    /**
     * Configure metrics collection.
     */
    fun metrics(metricsPath: String) {
        metrics = Metrics.create(metricsPath)
    }

    /**
     * Configure metrics collection for this VM instance with auto-generated filename.
     */
    fun autoMetrics(baseDir: String = "/tmp") {
        metrics = Metrics.forVm(name, baseDir)
    }

    /**
     * Configure advanced logging with preset configurations.
     *
     * @param preset Logging preset (development, production, monitoring, debugging, security)
     * @param vmId Optional VM identifier for unique log files (defaults to VM name)
     */
    fun loggingPreset(
        preset: LoggingPreset,
        vmId: String? = null,
    ) {
        val config =
            when (preset) {
                LoggingPreset.DEVELOPMENT -> LoggingConfiguration.development(vmId = vmId ?: name)
                LoggingPreset.PRODUCTION -> LoggingConfiguration.production(vmId = vmId ?: name)
                LoggingPreset.MONITORING -> LoggingConfiguration.monitoring(vmId = vmId ?: name)
                LoggingPreset.DEBUGGING -> LoggingConfiguration.debugging(vmId = vmId ?: name)
                LoggingPreset.SECURITY -> LoggingConfiguration.security(vmId = vmId ?: name)
            }
        logger = config.toLogger()
    }

    /**
     * Configure advanced logging with custom configuration.
     *
     * @param configure Function to configure logging with advanced options
     */
    fun advancedLogging(configure: LoggingConfigurationBuilder.() -> Unit) {
        val builder = LoggingConfigurationBuilder()
        builder.configure()
        logger = builder.build().toLogger()
    }

    /**
     * Configure advanced metrics with preset configurations.
     *
     * @param preset Metrics preset (development, production, monitoring, performance, minimal)
     * @param vmId Optional VM identifier for unique metrics files (defaults to VM name)
     */
    fun metricsPreset(
        preset: MetricsPreset,
        vmId: String? = null,
    ) {
        val config =
            when (preset) {
                MetricsPreset.DEVELOPMENT -> MetricsConfiguration.development(vmId = vmId ?: name)
                MetricsPreset.PRODUCTION -> MetricsConfiguration.production(vmId = vmId ?: name)
                MetricsPreset.MONITORING -> MetricsConfiguration.monitoring(vmId = vmId ?: name)
                MetricsPreset.PERFORMANCE -> MetricsConfiguration.performance(vmId = vmId ?: name)
                MetricsPreset.MINIMAL -> MetricsConfiguration.minimal(vmId = vmId ?: name)
            }
        metrics = config.toMetrics()
    }

    /**
     * Configure advanced metrics with custom configuration.
     *
     * @param configure Function to configure metrics with advanced options
     */
    fun advancedMetrics(configure: MetricsConfigurationBuilder.() -> Unit) {
        val builder = MetricsConfigurationBuilder()
        builder.configure()
        metrics = builder.build().toMetrics()
    }

    /**
     * Configure VSock device for host-guest communication.
     *
     * @param guestCid Context identifier for the guest (must be >= 3)
     * @param udsPath Path to Unix Domain Socket
     * @param vsockId Device identifier (optional)
     */
    fun vsock(
        guestCid: UInt,
        udsPath: String,
        vsockId: String = "vsock",
    ) {
        vsock = VSock.create(guestCid, udsPath, vsockId)
    }

    /**
     * Configure VSock device for this VM instance with auto-generated socket path.
     *
     * @param guestCid Context identifier for the guest (optional, auto-generated if not provided)
     * @param baseDir Base directory for socket files (defaults to /tmp)
     */
    fun autoVSock(
        guestCid: UInt? = null,
        baseDir: String = "/tmp",
    ) {
        vsock = VSock.forVm(name, baseDir, guestCid)
    }

    /**
     * Configure VSock device with custom socket name.
     *
     * @param guestCid Context identifier for the guest
     * @param socketName Socket filename
     * @param baseDir Base directory for socket files (defaults to /tmp)
     */
    fun vsockSocket(
        guestCid: UInt,
        socketName: String,
        baseDir: String = "/tmp",
    ) {
        vsock = VSock.withSocketName(guestCid, socketName, baseDir)
    }

    /**
     * Configure memory balloon device for dynamic memory management.
     *
     * The balloon device enables the host to reclaim and return guest memory
     * through API commands, supporting memory overcommit scenarios.
     *
     * @param amountMib Initial balloon target size in MiB
     * @param deflateOnOom Enable automatic deflation during guest OOM conditions
     * @param statsPollingIntervalS Statistics collection interval in seconds (0 disables)
     */
    fun balloon(
        amountMib: Int,
        deflateOnOom: Boolean = false,
        statsPollingIntervalS: Int = 0,
    ) {
        balloon = Balloon.withFullConfig(amountMib, deflateOnOom, statsPollingIntervalS)
    }

    /**
     * Configure memory balloon with basic settings.
     *
     * Creates a balloon device with specified target size and default settings
     * (no OOM protection, no statistics collection).
     *
     * @param amountMib Balloon target size in MiB
     */
    fun simpleBalloon(amountMib: Int) {
        balloon = Balloon.create(amountMib)
    }

    /**
     * Configure memory balloon optimized for overcommit scenarios.
     *
     * Creates a balloon with OOM protection enabled and frequent statistics
     * collection, suitable for environments with limited host memory.
     *
     * @param amountMib Initial balloon target size in MiB
     */
    fun overcommitBalloon(amountMib: Int) {
        balloon = Balloon.forOvercommit(amountMib)
    }

    /**
     * Configure memory balloon with statistics collection enabled.
     *
     * Enables detailed memory usage monitoring through periodic statistics
     * collection from the guest balloon driver.
     *
     * @param amountMib Balloon target size in MiB
     * @param pollingIntervalS Statistics polling interval in seconds
     */
    fun balloonWithStats(
        amountMib: Int,
        pollingIntervalS: Int,
    ) {
        balloon = Balloon.withStatistics(amountMib, pollingIntervalS)
    }

    /**
     * Configure memory balloon with OOM protection only.
     *
     * Enables automatic balloon deflation during guest out-of-memory
     * conditions to help maintain guest stability.
     *
     * @param amountMib Balloon target size in MiB
     */
    fun balloonWithOomProtection(amountMib: Int) {
        balloon = Balloon.withOomProtection(amountMib)
    }

    /**
     * Build the virtual machine with the configured settings.
     */
    fun build(): VirtualMachine {
        require(kernel.isNotEmpty()) {
            "Kernel path is required"
        }

        val configuration =
            MachineConfiguration(
                vcpuCount = vcpus,
                memSizeMib = memory,
                smt = enableSmt,
                trackDirtyPages = trackDirtyPages,
            )

        val bootSource =
            if (initrd != null) {
                BootSource.kernelWithInitrd(kernel, initrd!!, bootArgs)
            } else if (bootArgs != null) {
                BootSource.kernelWithArgs(kernel, bootArgs!!)
            } else {
                BootSource.kernel(kernel)
            }

        return VirtualMachine(
            name = name,
            socketPath = socketPath,
            configuration = configuration,
            bootSource = bootSource,
            drives = drives.toList(),
            networkInterfaces = networkInterfaces.toList(),
        )
    }

    companion object {
        private const val DEFAULT_MEMORY_MIB = 512
    }
}

/**
 * Builder for creating network interface configurations.
 */
class NetworkInterfaceBuilder {
    private var ifaceId: String = ""
    private var guestMac: String? = null
    private var hostDevName: String = ""
    private var rxRateLimiter: RateLimiter? = null
    private var txRateLimiter: RateLimiter? = null

    /**
     * Set the interface ID (required).
     */
    fun interfaceId(id: String) {
        this.ifaceId = id
    }

    /**
     * Set the guest MAC address (optional, will be generated if not provided).
     */
    fun guestMac(mac: String) {
        this.guestMac = mac
    }

    /**
     * Set the host device name (required).
     */
    fun hostDevice(name: String) {
        this.hostDevName = name
    }

    /**
     * Configure receive rate limiting.
     */
    fun rxRateLimit(configure: RateLimiterBuilder.() -> Unit) {
        val builder = RateLimiterBuilder()
        builder.configure()
        this.rxRateLimiter = builder.build()
    }

    /**
     * Configure transmit rate limiting.
     */
    fun txRateLimit(configure: RateLimiterBuilder.() -> Unit) {
        val builder = RateLimiterBuilder()
        builder.configure()
        this.txRateLimiter = builder.build()
    }

    internal fun build(): NetworkInterface {
        require(ifaceId.isNotEmpty()) {
            "Interface ID must be specified"
        }
        require(hostDevName.isNotEmpty()) {
            "Host device name must be specified"
        }

        return NetworkInterface(
            interfaceId = ifaceId,
            hostDeviceName = hostDevName,
            guestMacAddress = guestMac,
            rxRateLimiter = rxRateLimiter,
            txRateLimiter = txRateLimiter,
        )
    }
}

/**
 * Builder for creating rate limiter configurations.
 */
class RateLimiterBuilder {
    private var bandwidth: Bandwidth? = null
    private var ops: Operations? = null

    /**
     * Configure bandwidth limiting.
     */
    fun bandwidth(configure: BandwidthBuilder.() -> Unit) {
        val builder = BandwidthBuilder()
        builder.configure()
        this.bandwidth = builder.build()
    }

    /**
     * Configure operations limiting.
     */
    fun operations(configure: OperationsBuilder.() -> Unit) {
        val builder = OperationsBuilder()
        builder.configure()
        this.ops = builder.build()
    }

    internal fun build(): RateLimiter {
        return RateLimiter(
            bandwidth = bandwidth,
            operations = ops,
        )
    }
}

/**
 * Builder for bandwidth configuration.
 */
class BandwidthBuilder {
    private var size: Long = 0
    private var refillTime: Long = 0

    /**
     * Set the token bucket size in bytes.
     */
    fun size(bytes: Long) {
        this.size = bytes
    }

    /**
     * Set the refill time in milliseconds.
     */
    fun refillTime(ms: Long) {
        this.refillTime = ms
    }

    internal fun build(): Bandwidth {
        return Bandwidth(
            size = size,
            refillTimeMs = refillTime,
        )
    }
}

/**
 * Builder for operations configuration.
 */
class OperationsBuilder {
    private var size: Long = 0
    private var refillTime: Long = 0

    /**
     * Set the operations token bucket size.
     */
    fun size(count: Long) {
        this.size = count
    }

    /**
     * Set the refill time in milliseconds.
     */
    fun refillTime(ms: Long) {
        this.refillTime = ms
    }

    internal fun build(): Operations {
        return Operations(
            size = size,
            refillTimeMs = refillTime,
        )
    }
}

/**
 * Builder for creating drive configurations.
 */
class DriveBuilder {
    private var driveId: String = ""
    private var pathOnHost: String = ""
    private var isRootDevice: Boolean = false
    private var isReadOnly: Boolean = false
    private var cacheType: org.firecracker.sdk.models.CacheType = org.firecracker.sdk.models.CacheType.UNSAFE
    private var rateLimiter: org.firecracker.sdk.models.DriveRateLimiter? = null

    /**
     * Set the drive ID (required).
     */
    fun driveId(id: String) {
        this.driveId = id
    }

    /**
     * Set the path to the backing file on the host (required).
     */
    fun pathOnHost(path: String) {
        this.pathOnHost = path
    }

    /**
     * Set whether this is the root device.
     */
    fun isRootDevice(root: Boolean = true) {
        this.isRootDevice = root
    }

    /**
     * Set whether the drive is read-only.
     */
    fun isReadOnly(readOnly: Boolean = true) {
        this.isReadOnly = readOnly
    }

    /**
     * Set the cache type for the drive.
     */
    fun cacheType(type: org.firecracker.sdk.models.CacheType) {
        this.cacheType = type
    }

    /**
     * Configure rate limiting for the drive.
     */
    fun rateLimiter(configure: DriveRateLimiterBuilder.() -> Unit) {
        val builder = DriveRateLimiterBuilder()
        builder.configure()
        this.rateLimiter = builder.build()
    }

    internal fun build(): Drive {
        require(driveId.isNotEmpty()) {
            "Drive ID is required"
        }
        require(pathOnHost.isNotEmpty()) {
            "Path on host is required"
        }

        return Drive(
            driveId = driveId,
            pathOnHost = pathOnHost,
            isRootDevice = isRootDevice,
            isReadOnly = isReadOnly,
            cacheType = cacheType,
            rateLimiter = rateLimiter,
        )
    }
}

/**
 * Builder for drive rate limiter configurations.
 */
class DriveRateLimiterBuilder {
    private var bandwidth: org.firecracker.sdk.models.TokenBucket? = null
    private var ops: org.firecracker.sdk.models.TokenBucket? = null

    /**
     * Configure bandwidth rate limiting.
     */
    fun bandwidth(
        size: Long,
        oneTimeBurst: Long = size,
        refillTimeMs: Long = 100,
    ) {
        this.bandwidth = org.firecracker.sdk.models.TokenBucket.create(size, oneTimeBurst, refillTimeMs)
    }

    /**
     * Configure operations rate limiting.
     */
    fun operations(
        size: Long,
        oneTimeBurst: Long = size,
        refillTimeMs: Long = 100,
    ) {
        this.ops = org.firecracker.sdk.models.TokenBucket.create(size, oneTimeBurst, refillTimeMs)
    }

    internal fun build(): org.firecracker.sdk.models.DriveRateLimiter {
        return org.firecracker.sdk.models.DriveRateLimiter(
            bandwidth = bandwidth,
            ops = ops,
        )
    }
}

/**
 * Preset logging configurations for common use cases.
 */
enum class LoggingPreset {
    /** Development logging with debug level and verbose output */
    DEVELOPMENT,

    /** Production logging optimized for performance */
    PRODUCTION,

    /** Monitoring-focused logging for observability */
    MONITORING,

    /** Debugging logging with trace level and detailed output */
    DEBUGGING,

    /** Security-focused logging for audit trails */
    SECURITY,
}

/**
 * Preset metrics configurations for common use cases.
 */
enum class MetricsPreset {
    /** Development metrics with comprehensive collection */
    DEVELOPMENT,

    /** Production metrics optimized for performance */
    PRODUCTION,

    /** Monitoring-focused metrics collection */
    MONITORING,

    /** Performance analysis metrics with high frequency */
    PERFORMANCE,

    /** Minimal metrics for resource-constrained environments */
    MINIMAL,
}

/**
 * Builder for advanced logging configuration.
 */
class LoggingConfigurationBuilder {
    private var logPath: String = "/tmp/firecracker.log"
    private var level: org.firecracker.sdk.models.LogLevel = org.firecracker.sdk.models.LogLevel.Info
    private var showLevel: Boolean = true
    private var showLogOrigin: Boolean = false
    private var structured: Boolean = false
    private var componentFilter: Set<String> = emptySet()
    private var excludeFilter: Set<String> = emptySet()

    /**
     * Set the log file path.
     */
    fun logPath(path: String) {
        this.logPath = path
    }

    /**
     * Set the logging level.
     */
    fun level(level: org.firecracker.sdk.models.LogLevel) {
        this.level = level
    }

    /**
     * Configure whether to show log levels in output.
     */
    fun showLevel(show: Boolean = true) {
        this.showLevel = show
    }

    /**
     * Configure whether to show log origin information.
     */
    fun showLogOrigin(show: Boolean = true) {
        this.showLogOrigin = show
    }

    /**
     * Enable structured logging output.
     */
    fun structured(enabled: Boolean = true) {
        this.structured = enabled
    }

    /**
     * Filter logs to include only specific components.
     */
    fun componentFilter(vararg components: String) {
        this.componentFilter = components.toSet()
    }

    /**
     * Filter logs to exclude specific components.
     */
    fun excludeFilter(vararg components: String) {
        this.excludeFilter = components.toSet()
    }

    internal fun build(): LoggingConfiguration {
        return LoggingConfiguration(
            logPath = logPath,
            level = level,
            showLevel = showLevel,
            showLogOrigin = showLogOrigin,
            structured = structured,
            componentFilter = componentFilter,
            excludeFilter = excludeFilter,
        )
    }
}

/**
 * Builder for advanced metrics configuration.
 */
class MetricsConfigurationBuilder {
    companion object {
        private const val DEFAULT_COLLECTION_INTERVAL = 1000L
        private const val DEFAULT_RETENTION_PERIOD = 3600000L
        private const val DEFAULT_AGGREGATION_WINDOW = 60000L
    }

    private var metricsPath: String = "/tmp/firecracker-metrics.json"
    private var collectionInterval: Long = DEFAULT_COLLECTION_INTERVAL
    private var retentionPeriod: Long = DEFAULT_RETENTION_PERIOD
    private var enabledMetrics: Set<org.firecracker.sdk.metrics.MetricType> = org.firecracker.sdk.metrics.MetricType.entries.toSet()
    private var aggregationWindow: Long = DEFAULT_AGGREGATION_WINDOW
    private var exportFormat: org.firecracker.sdk.metrics.MetricsFormat = org.firecracker.sdk.metrics.MetricsFormat.JSON

    /**
     * Set the metrics file path.
     */
    fun metricsPath(path: String) {
        this.metricsPath = path
    }

    /**
     * Set the collection interval in milliseconds.
     */
    fun collectionInterval(interval: Long) {
        this.collectionInterval = interval
    }

    /**
     * Set the retention period in milliseconds.
     */
    fun retentionPeriod(period: Long) {
        this.retentionPeriod = period
    }

    /**
     * Configure which metrics to collect.
     */
    fun enabledMetrics(vararg metrics: org.firecracker.sdk.metrics.MetricType) {
        this.enabledMetrics = metrics.toSet()
    }

    /**
     * Set the aggregation window in milliseconds.
     */
    fun aggregationWindow(window: Long) {
        this.aggregationWindow = window
    }

    /**
     * Set the export format for metrics.
     */
    fun exportFormat(format: org.firecracker.sdk.metrics.MetricsFormat) {
        this.exportFormat = format
    }

    internal fun build(): MetricsConfiguration {
        return MetricsConfiguration(
            metricsPath = metricsPath,
            collectionInterval = collectionInterval,
            retentionPeriod = retentionPeriod,
            enabledMetrics = enabledMetrics,
            aggregationWindow = aggregationWindow,
            exportFormat = exportFormat,
        )
    }
}
