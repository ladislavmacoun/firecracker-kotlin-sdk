package org.firecracker.sdk

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
 * Main entry point for the Firecracker SDK.
 *
 * Provides a clean, high-level API for managing Firecracker microVMs
 * without exposing low-level implementation details.
 */
object Firecracker {
    /**
     * Create a new VM builder with sensible defaults.
     *
     * Example usage:
     * ```kotlin
     * val vm = Firecracker.createVM {
     *     name = "my-vm"
     *     vcpus = 2
     *     memory = 1024
     *     kernel = "/path/to/kernel"
     *     rootfs = "/path/to/rootfs"
     *
     *     addNetworkInterface {
     *         interfaceId = "eth0"
     *         hostDeviceName = "tap0"
     *         guestMacAddress = "AA:BB:CC:DD:EE:FF"
     *     }
     * }
     *
     * vm.start()
     * ```
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
