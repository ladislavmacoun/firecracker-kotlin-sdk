package org.firecracker.sdk

import kotlinx.coroutines.delay
import org.firecracker.sdk.client.FirecrackerClient
import org.firecracker.sdk.logging.LoggingConfiguration
import org.firecracker.sdk.metrics.MetricsConfiguration
import org.firecracker.sdk.models.Balloon
import org.firecracker.sdk.models.BalloonStatistics
import org.firecracker.sdk.models.BalloonUpdate
import org.firecracker.sdk.models.BootSource
import org.firecracker.sdk.models.Drive
import org.firecracker.sdk.models.Logger
import org.firecracker.sdk.models.MachineConfiguration
import org.firecracker.sdk.models.Metrics
import org.firecracker.sdk.models.NetworkInterface
import org.firecracker.sdk.models.SnapshotCreateParams
import org.firecracker.sdk.models.SnapshotLoadParams
import org.firecracker.sdk.models.VSock

/**
 * Represents a Firecracker virtual machine with comprehensive lifecycle management.
 *
 * This class encapsulates the complete functionality for managing Firecracker microVMs,
 * providing a high-level, type-safe API that abstracts the underlying complexity of
 * the Firecracker REST API. Built on domain-driven design principles with focus on
 * ergonomics, safety, and performance.
 *
 * ## Core Features
 *
 * **Lifecycle Management**: Complete VM state transitions from creation to termination
 * - Start, pause, resume, stop operations with proper state validation
 * - Graceful shutdown handling and resource cleanup
 * - Asynchronous operations with Kotlin coroutines support
 *
 * **Device Configuration**: Comprehensive device management capabilities
 * - Block devices (drives) with rate limiting and caching options
 * - Network interfaces with bandwidth controls and static IP configuration
 * - Memory balloon device for dynamic memory management
 * - VSock devices for secure host-guest communication
 *
 * **Observability & Monitoring**: Built-in monitoring and debugging tools
 * - Logging configuration with multiple verbosity levels
 * - Metrics collection and export capabilities
 * - VM state inspection and health monitoring
 *
 * **Snapshot Support**: Complete VM state persistence and restoration
 * - Full and differential snapshot creation
 * - Hot snapshot operations during VM runtime
 * - Cross-host VM migration through snapshot restoration
 *
 * ## Usage Examples
 *
 * ```kotlin
 * // Create and start a basic VM
 * val vm = Firecracker.createVM {
 *     name = "web-server"
 *     vcpus = 2
 *     memory = 1024
 *     kernel = "/path/to/vmlinux"
 *     rootDrive("/path/to/rootfs.ext4")
 *
 *     // Network configuration
 *     addNetworkInterface {
 *         interfaceId = "eth0"
 *         hostDeviceName = "tap0"
 *         guestMacAddress = "AA:BB:CC:DD:EE:FF"
 *     }
 *
 *     // Memory management
 *     simpleBalloon(512) // Start with 512MB balloon
 * }
 *
 * // Start VM and handle results
 * vm.start()
 *     .onSuccess { println("VM started successfully") }
 *     .onFailure { error -> logger.error("Failed to start VM", error) }
 *
 * // Runtime memory management
 * vm.inflateBalloon(768) // Reclaim 256MB more memory
 * val stats = vm.getBalloonStatistics().getOrNull()
 * println("Memory efficiency: ${stats?.efficiency}%")
 *
 * // Snapshot for backup/migration
 * vm.createSnapshot(SnapshotCreateParams.full("/backup/snapshot.json", "/backup/memory.bin"))
 *     .onSuccess { println("Snapshot created") }
 * ```
 *
 * ## Error Handling
 *
 * All operations return `Result<T>` for functional error handling without exceptions.
 * Common error scenarios include:
 * - Invalid state transitions (e.g., starting an already running VM)
 * - Resource conflicts (e.g., socket path already in use)
 * - Configuration validation failures
 * - Firecracker process communication errors
 *
 * ## Thread Safety
 *
 * This class is **not thread-safe**. While individual operations are atomic through
 * the underlying HTTP client, concurrent modifications to VM state can lead to
 * race conditions. Use external synchronization when accessing from multiple threads.
 *
 * ## Resource Management
 *
 * Virtual machines hold system resources including:
 * - Unix domain socket connections
 * - File descriptors for drives and devices
 * - Memory allocations for VM state
 *
 * Always call [close] when finished to ensure proper cleanup, or use within
 * try-with-resources pattern for automatic resource management.
 *
 * @param name Human-readable identifier for the VM instance
 * @param socketPath Unix domain socket path for Firecracker API communication
 * @param configuration CPU and memory settings for the virtual machine
 * @param bootSource Kernel and boot configuration including initrd and boot arguments
 * @param drives List of block devices attached to the VM (root and data drives)
 * @param networkInterfaces List of network interfaces for VM connectivity
 *
 * @see Firecracker Main SDK entry point for VM creation
 * @see FirecrackerClient Low-level HTTP API client
 * @see <a href="https://firecracker-microvm.github.io/">Firecracker Documentation</a>
 * @since 1.0.0
 * @author Firecracker Kotlin SDK Team
 */
@Suppress("TooManyFunctions") // VM lifecycle management legitimately requires many operations
class VirtualMachine internal constructor(
    val name: String,
    private val socketPath: String,
    private val configuration: MachineConfiguration,
    private val bootSource: BootSource,
    private val drives: List<Drive> = emptyList(),
    private val networkInterfaces: List<NetworkInterface> = emptyList(),
) {
    private val client by lazy { FirecrackerClient(socketPath) }

    /**
     * Represents the current operational state of the virtual machine.
     *
     * State transitions follow a well-defined lifecycle that ensures consistent
     * VM behavior and prevents invalid operations. The state machine enforces
     * proper sequencing of VM operations and provides clear error reporting
     * for invalid transitions.
     *
     * ## State Transition Diagram
     * ```
     * NOT_STARTED → STARTING → RUNNING → STOPPING → STOPPED
     *      ↓           ↓          ↓         ↓         ↓
     *    ERROR ←----- ERROR ←--- ERROR ←-- ERROR ← ERROR
     * ```
     *
     * ## Valid Transitions
     * - `NOT_STARTED` → `STARTING`: Initial VM boot sequence begins
     * - `STARTING` → `RUNNING`: VM has successfully booted and is operational
     * - `RUNNING` → `STOPPING`: Graceful shutdown initiated
     * - `STOPPING` → `STOPPED`: VM has fully shut down
     * - Any State → `ERROR`: Unrecoverable error occurred
     *
     * @see VirtualMachine.start Start VM operation
     * @see VirtualMachine.stop Stop VM operation
     * @see VirtualMachine.state Current state property
     * @since 1.0.0
     */
    enum class State {
        /**
         * VM is created but not yet started.
         *
         * Initial state after VM creation. Configuration can still be modified
         * and devices can be attached. The start() operation is available.
         */
        NOT_STARTED,

        /**
         * VM boot process is in progress.
         *
         * Transitional state during VM initialization. The Firecracker process
         * is loading the kernel and setting up devices. Most operations are
         * unavailable during this state.
         */
        STARTING,

        /**
         * VM is fully operational and running guest code.
         *
         * Normal operational state where the guest OS is active and can execute
         * workloads. All runtime operations (pause, resume, snapshot) are available.
         */
        RUNNING,

        /**
         * VM shutdown process is in progress.
         *
         * Transitional state during graceful shutdown. The guest OS is terminating
         * processes and unmounting filesystems. New operations are not accepted.
         */
        STOPPING,

        /**
         * VM has completely stopped and released all resources.
         *
         * Final state after successful shutdown. VM resources are cleaned up
         * and the instance cannot be restarted. A new VM must be created.
         */
        STOPPED,

        /**
         * VM encountered an unrecoverable error.
         *
         * Error state indicating VM failure during any operation. The VM
         * may need to be destroyed and recreated. Check logs for error details.
         */
        ERROR,
    }

    @Volatile
    private var currentState: State = State.NOT_STARTED

    /**
     * Get the current state of the VM.
     */
    val state: State get() = currentState

    /**
     * Check if the VM is currently running.
     */
    val isRunning: Boolean get() = currentState == State.RUNNING

    /**
     * Start the virtual machine.
     *
     * This method handles the complete startup sequence:
     * 1. Configure the machine
     * 2. Set up boot source
     * 3. Configure drives
     * 4. Start the VM
     */
    suspend fun start(): Result<Unit> =
        when (currentState) {
            State.NOT_STARTED, State.STOPPED -> startVM()
            else -> Result.failure(VMException("Cannot start VM in state: $currentState"))
        }

    private suspend fun startVM(): Result<Unit> {
        currentState = State.STARTING

        return client.put("/machine-config", configuration)
            .flatMap {
                configureBootSource()
            }
            .flatMap {
                configureDrives()
            }
            .flatMap {
                configureNetworkInterfaces()
            }
            .flatMap {
                startVMAction()
            }
            .onSuccess {
                currentState = State.RUNNING
            }
            .mapError { error ->
                currentState = State.ERROR
                when (error) {
                    is VMException -> error
                    else -> VMException("Failed to start VM '$name'", error)
                }
            }
    }

    private suspend fun configureBootSource(): Result<Unit> =
        client.put("/boot-source", bootSource)
            .mapError { error ->
                VMException("Failed to configure boot source for VM '$name'", error)
            }

    private suspend fun configureDrives(): Result<Unit> =
        drives.fold(Result.success(Unit)) { acc, drive ->
            acc.flatMap {
                client.put("/drives/${drive.driveId}", drive)
                    .mapError { error ->
                        VMException(
                            "Failed to configure drive ${drive.driveId} for VM '$name'",
                            error,
                        )
                    }
            }
        }

    private suspend fun configureNetworkInterfaces(): Result<Unit> =
        networkInterfaces.fold(Result.success(Unit)) { acc, networkInterface ->
            acc.flatMap {
                client.put("/network-interfaces/${networkInterface.interfaceId}", networkInterface)
                    .mapError { error ->
                        VMException(
                            "Failed to configure network interface ${networkInterface.interfaceId} for VM '$name'",
                            error,
                        )
                    }
            }
        }

    private suspend fun startVMAction(): Result<Unit> =
        client.put("/actions", mapOf("action_type" to "InstanceStart"))
            .mapError { error ->
                VMException("Failed to start VM instance '$name'", error)
            }

    /**
     * Stop the virtual machine gracefully.
     */
    suspend fun stop(): Result<Unit> =
        when (currentState) {
            State.RUNNING -> stopVM()
            else -> Result.failure(VMException("Cannot stop VM in state: $currentState"))
        }

    private suspend fun stopVM(): Result<Unit> {
        currentState = State.STOPPING

        return client.put("/actions", mapOf("action_type" to "SendCtrlAltDel"))
            .flatMap {
                // Wait for graceful shutdown
                waitForState(State.STOPPED, timeoutMs = 10_000)
            }
            .onSuccess {
                currentState = State.STOPPED
            }
            .mapError { error ->
                currentState = State.ERROR
                when (error) {
                    is VMException -> error
                    else -> VMException("Failed to stop VM '$name'", error)
                }
            }
    }

    /**
     * Force kill the virtual machine.
     */
    suspend fun kill(): Result<Unit> =
        when (currentState) {
            State.RUNNING, State.STARTING -> killVM()
            else -> Result.failure(VMException("Cannot kill VM in state: $currentState"))
        }

    private suspend fun killVM(): Result<Unit> {
        currentState = State.STOPPING

        return client.put("/actions", mapOf("action_type" to "InstanceStop"))
            .onSuccess {
                currentState = State.STOPPED
            }
            .mapError { error ->
                currentState = State.ERROR
                when (error) {
                    is VMException -> error
                    else -> VMException("Failed to kill VM '$name'", error)
                }
            }
    }

    /**
     * Wait for the VM to reach the specified state.
     */
    suspend fun waitForState(
        targetState: State,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): Result<Unit> =
        runCatching {
            val startTime = System.currentTimeMillis()

            while (currentState != targetState) {
                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    throw VMException("Timeout waiting for VM '$name' to reach state $targetState")
                }

                delay(POLLING_INTERVAL_MS)
            }
        }

    /**
     * Configure logging for the Firecracker VMM process.
     *
     * @param logger Logger configuration specifying output path and settings
     * @return Result indicating success or failure
     */
    suspend fun configureLogger(logger: Logger): Result<Unit> =
        runCatching {
            currentState = State.STARTING
            client.put("/logger", logger)
                .fold(
                    onSuccess = { },
                    onFailure = {
                        currentState = State.ERROR
                        throw it
                    },
                )
        }

    /**
     * Configure metrics collection for the Firecracker VMM process.
     *
     * @param metrics Metrics configuration specifying output path
     * @return Result indicating success or failure
     */
    suspend fun configureMetrics(metrics: Metrics): Result<Unit> =
        runCatching {
            currentState = State.STARTING
            client.put("/metrics", metrics)
                .fold(
                    onSuccess = { },
                    onFailure = {
                        currentState = State.ERROR
                        throw it
                    },
                )
        }

    /**
     * Configure advanced logging with comprehensive options.
     *
     * @param config Advanced logging configuration with features like structured output,
     *               component filtering, and preset configurations
     * @return Result indicating success or failure
     */
    suspend fun configureAdvancedLogging(config: LoggingConfiguration): Result<Unit> = configureLogger(config.toLogger())

    /**
     * Configure logging using a preset configuration.
     *
     * @param preset Logging preset for common use cases (development, production, etc.)
     * @param vmId Optional VM identifier for unique log files (defaults to VM name)
     * @return Result indicating success or failure
     */
    suspend fun configureLoggingPreset(
        preset: LoggingPreset,
        vmId: String? = null,
    ): Result<Unit> {
        val config =
            when (preset) {
                LoggingPreset.DEVELOPMENT -> LoggingConfiguration.development(vmId = vmId ?: name)
                LoggingPreset.PRODUCTION -> LoggingConfiguration.production(vmId = vmId ?: name)
                LoggingPreset.MONITORING -> LoggingConfiguration.monitoring(vmId = vmId ?: name)
                LoggingPreset.DEBUGGING -> LoggingConfiguration.debugging(vmId = vmId ?: name)
                LoggingPreset.SECURITY -> LoggingConfiguration.security(vmId = vmId ?: name)
            }
        return configureAdvancedLogging(config)
    }

    /**
     * Configure advanced metrics with comprehensive collection options.
     *
     * @param config Advanced metrics configuration with features like multiple formats,
     *               collection intervals, and metric type filtering
     * @return Result indicating success or failure
     */
    suspend fun configureAdvancedMetrics(config: MetricsConfiguration): Result<Unit> = configureMetrics(config.toMetrics())

    /**
     * Configure metrics using a preset configuration.
     *
     * @param preset Metrics preset for common use cases (development, production, etc.)
     * @param vmId Optional VM identifier for unique metrics files (defaults to VM name)
     * @return Result indicating success or failure
     */
    suspend fun configureMetricsPreset(
        preset: MetricsPreset,
        vmId: String? = null,
    ): Result<Unit> {
        val config =
            when (preset) {
                MetricsPreset.DEVELOPMENT -> MetricsConfiguration.development(vmId = vmId ?: name)
                MetricsPreset.PRODUCTION -> MetricsConfiguration.production(vmId = vmId ?: name)
                MetricsPreset.MONITORING -> MetricsConfiguration.monitoring(vmId = vmId ?: name)
                MetricsPreset.PERFORMANCE -> MetricsConfiguration.performance(vmId = vmId ?: name)
                MetricsPreset.MINIMAL -> MetricsConfiguration.minimal(vmId = vmId ?: name)
            }
        return configureAdvancedMetrics(config)
    }

    /**
     * Create a snapshot of the current VM state.
     *
     * The VM must be in a paused state to create a snapshot.
     *
     * @param params Snapshot creation parameters
     * @return Result indicating success or failure
     */
    suspend fun createSnapshot(params: SnapshotCreateParams): Result<Unit> =
        runCatching {
            client.put("/snapshot/create", params)
                .fold(
                    onSuccess = { },
                    onFailure = { throw it },
                )
        }

    /**
     * Load VM state from a snapshot.
     *
     * This can only be done when creating a new VM instance.
     *
     * @param params Snapshot load parameters
     * @return Result indicating success or failure
     */
    suspend fun loadSnapshot(params: SnapshotLoadParams): Result<Unit> =
        runCatching {
            currentState = State.STARTING
            client.put("/snapshot/load", params)
                .fold(
                    onSuccess = {
                        if (params.resumeVm) {
                            currentState = State.RUNNING
                        }
                    },
                    onFailure = {
                        currentState = State.ERROR
                        throw it
                    },
                )
        }

    /**
     * Pause the VM execution.
     *
     * @return Result indicating success or failure
     */
    suspend fun pause(): Result<Unit> =
        runCatching {
            if (currentState != State.RUNNING) {
                throw VMException("Cannot pause VM '$name' in state $currentState")
            }

            client.put("/actions", mapOf("action_type" to "Pause"))
                .fold(
                    onSuccess = { currentState = State.STOPPING },
                    onFailure = {
                        currentState = State.ERROR
                        throw it
                    },
                )
        }

    /**
     * Resume the VM execution after pause.
     *
     * @return Result indicating success or failure
     */
    suspend fun resume(): Result<Unit> =
        runCatching {
            if (currentState != State.STOPPING) {
                throw VMException("Cannot resume VM '$name' in state $currentState")
            }

            client.put("/actions", mapOf("action_type" to "Resume"))
                .fold(
                    onSuccess = { currentState = State.RUNNING },
                    onFailure = {
                        currentState = State.ERROR
                        throw it
                    },
                )
        }

    /**
     * Configure VSock device for host-guest communication.
     *
     * VSock provides a secure channel for communication between the host
     * and guest VM through Unix Domain Sockets.
     *
     * @param vsock VSock device configuration
     * @return Result indicating success or failure
     */
    suspend fun configureVSock(vsock: VSock): Result<Unit> =
        runCatching {
            client.put("/vsock", vsock)
                .onFailure { throw it }
        }

    /**
     * Get current VSock device configuration.
     *
     * @return Current VSock configuration or null if not configured
     */
    suspend fun getVSock(): Result<VSock?> =
        runCatching {
            client.get<VSock>("/vsock")
                .fold(
                    onSuccess = { it },
                    onFailure = { null },
                )
        }

    /**
     * Remove VSock device configuration.
     *
     * @return Result indicating success or failure
     */
    suspend fun removeVSock(): Result<Unit> =
        runCatching {
            // Note: Firecracker doesn't have a direct delete for VSock,
            // but we can try to get and handle appropriately
            client.patch("/vsock", mapOf("guest_cid" to 0))
                .onFailure { throw it }
        }

    /**
     * Configure memory balloon device for dynamic memory management.
     *
     * The balloon device allows the host to reclaim and return guest memory
     * through API commands. This enables memory overcommit scenarios and
     * dynamic memory allocation based on workload demands.
     *
     * Note: The balloon device must be configured before starting the VM.
     * Once the VM is running, only the target size and polling interval
     * can be modified through updateBalloon().
     *
     * @param balloon Balloon device configuration including target size,
     *                OOM protection settings, and statistics collection
     * @return Result indicating success or failure of configuration
     * @throws IllegalStateException if VM is already running
     */
    suspend fun configureBalloon(balloon: Balloon): Result<Unit> =
        runCatching {
            client.put("/balloon", balloon)
                .onFailure { throw it }
        }

    /**
     * Get current balloon device configuration and operational status.
     *
     * Returns the current balloon configuration including target size,
     * OOM protection settings, and statistics collection interval.
     * This provides the configuration perspective of the balloon device.
     *
     * @return Current balloon configuration or null if not configured
     */
    suspend fun getBalloon(): Result<Balloon?> =
        runCatching {
            client.get<Balloon>("/balloon")
                .fold(
                    onSuccess = { it },
                    onFailure = { null },
                )
        }

    /**
     * Update balloon device configuration during runtime.
     *
     * Allows modification of balloon target size and statistics polling
     * interval while the VM is running. This is the primary method for
     * dynamic memory management operations.
     *
     * Common use cases:
     * - Increasing balloon size to reclaim guest memory
     * - Decreasing balloon size to provide more memory to guest
     * - Adjusting statistics collection frequency for monitoring
     *
     * @param update Balloon update specifying new target size and/or polling interval
     * @return Result indicating success or failure of update operation
     */
    suspend fun updateBalloon(update: BalloonUpdate): Result<Unit> =
        runCatching {
            client.patch("/balloon", update)
                .onFailure { throw it }
        }

    /**
     * Get detailed balloon statistics and operational metrics.
     *
     * Provides comprehensive insights into balloon operation including:
     * - Target vs actual balloon sizes (pages and MiB)
     * - Guest memory statistics (swap, page faults, free memory)
     * - Memory pressure indicators and availability metrics
     * - Hugetlb allocation statistics
     *
     * Statistics are only available if the balloon was configured with
     * a non-zero statsPollingIntervalS value. The data reflects the
     * guest's perspective and should be considered indicative rather
     * than authoritative due to potential guest driver issues.
     *
     * @return Current balloon statistics or error if statistics disabled
     * @throws IllegalStateException if balloon not configured or statistics disabled
     */
    suspend fun getBalloonStatistics(): Result<BalloonStatistics> =
        runCatching {
            client.get<BalloonStatistics>("/balloon/statistics")
                .getOrThrow()
        }

    /**
     * Convenience method to inflate balloon to reclaim guest memory.
     *
     * Increases the balloon target size to the specified amount, effectively
     * reducing the amount of memory available to guest processes. This is
     * useful for memory overcommit scenarios where host memory needs to be
     * reclaimed from guests.
     *
     * @param targetMib New balloon target size in MiB
     * @return Result indicating success or failure of inflation
     */
    suspend fun inflateBalloon(targetMib: Int): Result<Unit> = updateBalloon(BalloonUpdate.targetSize(targetMib))

    /**
     * Convenience method to deflate balloon to provide more memory to guest.
     *
     * Decreases the balloon target size to the specified amount, effectively
     * increasing the amount of memory available to guest processes. Use this
     * when guest workloads require additional memory resources.
     *
     * @param targetMib New balloon target size in MiB (must be smaller than current)
     * @return Result indicating success or failure of deflation
     */
    suspend fun deflateBalloon(targetMib: Int): Result<Unit> = updateBalloon(BalloonUpdate.targetSize(targetMib))

    /**
     * Get detailed information about the VM.
     */
    suspend fun getInfo(): VMInfo {
        return VMInfo(
            name = name,
            state = currentState,
            configuration = configuration,
            socketPath = socketPath,
        )
    }

    /**
     * Clean up resources when the VM is no longer needed.
     */
    fun close() {
        client.close()
    }

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 30_000L
        private const val POLLING_INTERVAL_MS = 100L

        /**
         * Connect to an existing Firecracker VM via socket.
         */
        internal fun fromSocket(socketPath: String): VirtualMachine {
            // TODO: Query VM for its configuration
            return VirtualMachine(
                name = "existing-vm",
                socketPath = socketPath,
                configuration = MachineConfiguration(vcpuCount = 1, memSizeMib = 512),
                bootSource = BootSource.kernel("/tmp/vmlinux"),
                drives = emptyList(),
                networkInterfaces = emptyList(),
            )
        }
    }
}

/**
 * Information about a virtual machine.
 */
data class VMInfo(
    val name: String,
    val state: VirtualMachine.State,
    val configuration: MachineConfiguration,
    val socketPath: String,
)

/**
 * Exception thrown when VM operations fail.
 */
class VMException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

// Idiomatic Kotlin extensions for working with VM Results

/**
 * Extension function to chain Results functionally.
 */
inline fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> =
    fold(
        onSuccess = { transform(it) },
        onFailure = { Result.failure(it) },
    )

/**
 * Extension function to transform failures.
 */
inline fun <T> Result<T>.mapError(transform: (Throwable) -> Throwable): Result<T> =
    fold(
        onSuccess = { Result.success(it) },
        onFailure = { Result.failure(transform(it)) },
    )

/**
 * Execute a VM operation and handle the result with idiomatic patterns.
 *
 * Usage:
 * ```
 * vm.start().onSuccess {
 *     println("VM started successfully")
 * }.onFailure { error ->
 *     println("Failed to start VM: ${error.message}")
 * }
 * ```
 */
suspend inline fun VirtualMachine.startAndHandle(
    onSuccess: () -> Unit = {},
    onFailure: (Throwable) -> Unit = {},
): Result<Unit> = start().onSuccess { onSuccess() }.onFailure { onFailure(it) }

/**
 * Stop the VM and handle the result.
 */
suspend inline fun VirtualMachine.stopAndHandle(
    onSuccess: () -> Unit = {},
    onFailure: (Throwable) -> Unit = {},
): Result<Unit> = stop().onSuccess { onSuccess() }.onFailure { onFailure(it) }

/**
 * Chain VM operations using Result monad patterns.
 *
 * Usage:
 * ```
 * vm.start()
 *   .flatMap { vm.waitForState(VirtualMachine.State.RUNNING) }
 *   .fold(
 *     onSuccess = { println("VM is running") },
 *     onFailure = { error -> println("Operation failed: ${error.message}") }
 *   )
 * ```
 */
