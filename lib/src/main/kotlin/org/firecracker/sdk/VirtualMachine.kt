package org.firecracker.sdk

import kotlinx.coroutines.delay
import org.firecracker.sdk.client.FirecrackerClient
import org.firecracker.sdk.models.MachineConfiguration

/**
 * Represents a Firecracker virtual machine with high-level lifecycle management.
 *
 * This class encapsulates all the complexity of managing a Firecracker VM,
 * providing a clean, ergonomic API that follows domain-driven design principles.
 */
class VirtualMachine internal constructor(
    val name: String,
    private val socketPath: String,
    private val configuration: MachineConfiguration,
    private val kernelPath: String,
    private val rootfsPath: String
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
        ERROR
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
     *
     * @throws VMException if startup fails
     */
    suspend fun start() {
        if (currentState != State.NOT_STARTED && currentState != State.STOPPED) {
            throw VMException("Cannot start VM in state: $currentState")
        }

        try {
            currentState = State.STARTING

            // Configure machine
            client.put("/machine-config", configuration)

            // TODO: Configure boot source
            // TODO: Configure drives
            // TODO: Start VM

            currentState = State.RUNNING
        } catch (e: Exception) {
            currentState = State.ERROR
            throw VMException("Failed to start VM '$name'", e)
        }
    }

    /**
     * Stop the virtual machine gracefully.
     */
    suspend fun stop() {
        if (currentState != State.RUNNING) {
            throw VMException("Cannot stop VM in state: $currentState")
        }

        try {
            currentState = State.STOPPING

            // TODO: Send shutdown signal
            // TODO: Wait for graceful shutdown

            currentState = State.STOPPED
        } catch (e: Exception) {
            currentState = State.ERROR
            throw VMException("Failed to stop VM '$name'", e)
        }
    }

    /**
     * Force kill the virtual machine.
     */
    suspend fun kill() {
        try {
            // TODO: Send kill signal
            currentState = State.STOPPED
        } catch (e: Exception) {
            currentState = State.ERROR
            throw VMException("Failed to kill VM '$name'", e)
        }
    }

    /**
     * Wait for the VM to reach the specified state.
     */
    suspend fun waitForState(targetState: State, timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
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
            socketPath = socketPath
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
                kernelPath = "",
                rootfsPath = ""
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
    val socketPath: String
)

/**
 * Exception thrown when VM operations fail.
 */
class VMException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)