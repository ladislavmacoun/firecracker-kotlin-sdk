package org.firecracker.sdk

import kotlinx.coroutines.delay
import org.firecracker.sdk.client.FirecrackerClient
import org.firecracker.sdk.models.BootSource
import org.firecracker.sdk.models.Drive
import org.firecracker.sdk.models.MachineConfiguration
import org.firecracker.sdk.models.NetworkInterface

/**
 * Represents a Firecracker virtual machine with high-level lifecycle management.
 *
 * This class encapsulates all the complexity of managing a Firecracker VM,
 * providing a clean, ergonomic API that follows domain-driven design principles.
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
     * Current state of the virtual machine.
     */
    enum class State {
        NOT_STARTED,
        STARTING,
        RUNNING,
        STOPPING,
        STOPPED,
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
