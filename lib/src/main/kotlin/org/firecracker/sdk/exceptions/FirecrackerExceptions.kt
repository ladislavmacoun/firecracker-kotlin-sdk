package org.firecracker.sdk.exceptions

import org.firecracker.sdk.client.ClientError

/**
 * Base exception for all Firecracker SDK operations.
 *
 * This provides a hierarchical exception system that allows for precise
 * error handling while maintaining compatibility with existing Result<T> patterns.
 */
sealed class FirecrackerException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * VM lifecycle and operation exceptions.
 */
sealed class VMException(
    message: String,
    cause: Throwable? = null,
) : FirecrackerException(message, cause) {
    /**
     * Invalid state transition attempted.
     *
     * @property currentState The current VM state
     * @property attemptedOperation The operation that was attempted
     */
    data class InvalidStateTransition(
        val currentState: String,
        val attemptedOperation: String,
        val vmName: String,
    ) : VMException(
            "Cannot perform '$attemptedOperation' on VM '$vmName' in state '$currentState'",
        )

    /**
     * VM configuration is invalid.
     *
     * @property field The configuration field that is invalid
     * @property reason The reason why it's invalid
     */
    data class InvalidConfiguration(
        val field: String,
        val reason: String,
        val vmName: String,
    ) : VMException(
            "Invalid configuration for VM '$vmName': $field - $reason",
        )

    /**
     * VM resource allocation failed.
     *
     * @property resource The resource that failed to allocate
     * @property details Additional details about the failure
     */
    data class ResourceAllocationFailed(
        val resource: String,
        val details: String,
        val vmName: String,
    ) : VMException(
            "Failed to allocate $resource for VM '$vmName': $details",
        )

    /**
     * VM operation timeout.
     *
     * @property operation The operation that timed out
     * @property timeoutMs The timeout duration in milliseconds
     */
    data class OperationTimeout(
        val operation: String,
        val timeoutMs: Long,
        val vmName: String,
    ) : VMException(
            "Operation '$operation' timed out after ${timeoutMs}ms for VM '$vmName'",
        )

    /**
     * General VM operation failure.
     *
     * @property operation The operation that failed
     */
    data class OperationFailed(
        val operation: String,
        val vmName: String,
        override val cause: Throwable?,
    ) : VMException(
            "Operation '$operation' failed for VM '$vmName': ${cause?.message}",
            cause,
        )
}

/**
 * Configuration validation exceptions.
 */
sealed class ValidationException(
    message: String,
    cause: Throwable? = null,
) : FirecrackerException(message, cause) {
    /**
     * Required field is missing.
     *
     * @property fieldName The name of the missing field
     * @property context The context where the field is required
     */
    data class MissingRequiredField(
        val fieldName: String,
        val context: String,
    ) : ValidationException(
            "Required field '$fieldName' is missing in $context",
        )

    /**
     * Field value is out of valid range.
     *
     * @property fieldName The name of the field
     * @property value The invalid value
     * @property validRange Description of the valid range
     */
    data class InvalidRange(
        val fieldName: String,
        val value: Any,
        val validRange: String,
    ) : ValidationException(
            "Field '$fieldName' value '$value' is not in valid range: $validRange",
        )

    /**
     * Field format is invalid.
     *
     * @property fieldName The name of the field
     * @property value The invalid value
     * @property expectedFormat Description of expected format
     */
    data class InvalidFormat(
        val fieldName: String,
        val value: String,
        val expectedFormat: String,
    ) : ValidationException(
            "Field '$fieldName' value '$value' has invalid format. Expected: $expectedFormat",
        )

    /**
     * Resource path is invalid or inaccessible.
     *
     * @property path The invalid path
     * @property reason The reason why it's invalid
     */
    data class InvalidPath(
        val path: String,
        val reason: String,
    ) : ValidationException(
            "Invalid path '$path': $reason",
        )
}

/**
 * Resource management exceptions.
 */
sealed class ResourceException(
    message: String,
    cause: Throwable? = null,
) : FirecrackerException(message, cause) {
    /**
     * Resource is not available or already in use.
     *
     * @property resourceType The type of resource
     * @property resourceId The identifier of the resource
     */
    data class ResourceUnavailable(
        val resourceType: String,
        val resourceId: String,
    ) : ResourceException(
            "$resourceType '$resourceId' is not available or already in use",
        )

    /**
     * Resource limit exceeded.
     *
     * @property resourceType The type of resource
     * @property limit The limit that was exceeded
     * @property current The current usage
     */
    data class ResourceLimitExceeded(
        val resourceType: String,
        val limit: Long,
        val current: Long,
    ) : ResourceException(
            "$resourceType limit exceeded: current=$current, limit=$limit",
        )

    /**
     * File or socket operations failed.
     *
     * @property operation The operation that failed
     * @property path The path that was involved
     */
    data class FileSystemError(
        val operation: String,
        val path: String,
        override val cause: Throwable?,
    ) : ResourceException(
            "File system operation '$operation' failed for path '$path': ${cause?.message}",
            cause,
        )
}

/**
 * API communication exceptions.
 */
sealed class ApiException(
    message: String,
    cause: Throwable? = null,
) : FirecrackerException(message, cause) {
    /**
     * HTTP request failed with specific status code.
     *
     * @property statusCode The HTTP status code
     * @property operation The API operation that failed
     * @property responseBody The response body if available
     */
    data class HttpError(
        val statusCode: Int,
        val operation: String,
        val responseBody: String? = null,
    ) : ApiException(
            "HTTP $statusCode error for operation '$operation'${responseBody?.let { ": $it" } ?: ""}",
        )

    /**
     * Request serialization failed.
     *
     * @property operation The operation being attempted
     */
    data class SerializationError(
        val operation: String,
        override val cause: Throwable,
    ) : ApiException(
            "Failed to serialize request for operation '$operation': ${cause.message}",
            cause,
        )

    /**
     * Response deserialization failed.
     *
     * @property operation The operation being attempted
     * @property responseBody The raw response body
     */
    data class DeserializationError(
        val operation: String,
        val responseBody: String,
        override val cause: Throwable,
    ) : ApiException(
            "Failed to parse response for operation '$operation': ${cause.message}",
            cause,
        )

    /**
     * Connection to Firecracker failed.
     *
     * @property socketPath The socket path that failed
     * @property clientError The underlying client error
     */
    data class ConnectionFailed(
        val socketPath: String,
        val clientError: ClientError,
    ) : ApiException(
            "Connection failed to Firecracker at '$socketPath': ${clientError.message}",
            clientError,
        )
}

/**
 * Snapshot operation exceptions.
 */
sealed class SnapshotException(
    message: String,
    cause: Throwable? = null,
) : FirecrackerException(message, cause) {
    /**
     * Snapshot file is corrupted or invalid.
     *
     * @property snapshotPath The path to the snapshot file
     * @property reason The reason why it's invalid
     */
    data class InvalidSnapshot(
        val snapshotPath: String,
        val reason: String,
    ) : SnapshotException(
            "Invalid snapshot file '$snapshotPath': $reason",
        )

    /**
     * Snapshot creation failed.
     *
     * @property targetPath The target path for the snapshot
     */
    data class CreationFailed(
        val targetPath: String,
        override val cause: Throwable?,
    ) : SnapshotException(
            "Failed to create snapshot at '$targetPath': ${cause?.message}",
            cause,
        )

    /**
     * Snapshot restoration failed.
     *
     * @property snapshotPath The path to the snapshot file
     */
    data class RestorationFailed(
        val snapshotPath: String,
        override val cause: Throwable?,
    ) : SnapshotException(
            "Failed to restore from snapshot '$snapshotPath': ${cause?.message}",
            cause,
        )
}

/**
 * Utility functions for error handling and recovery.
 */
object ErrorHandling {
    
    // HTTP status code constants
    private const val HTTP_SERVER_ERROR_START = 500
    private const val HTTP_SERVER_ERROR_END = 599
    private const val HTTP_TOO_MANY_REQUESTS = 429
    private const val HTTP_SERVICE_UNAVAILABLE = 503
    private const val HTTP_GATEWAY_TIMEOUT = 504
    
    /**
     * Check if an exception is retryable.
     *
     * @param exception The exception to check
     * @return true if the operation should be retried
     */
    fun isRetryable(exception: Throwable): Boolean =
        when (exception) {
            is ClientError.NetworkTimeout -> true
            is ClientError.NetworkIO -> true
            is ApiException.HttpError -> exception.statusCode in HTTP_SERVER_ERROR_START..HTTP_SERVER_ERROR_END
            is VMException.OperationTimeout -> false // Don't retry timeouts
            is ValidationException -> false // Don't retry validation errors
            else -> false
        }

    /**
     * Determine if an exception indicates a transient error.
     *
     * @param exception The exception to check
     * @return true if the error is likely transient
     */
    fun isTransient(exception: Throwable): Boolean =
        when (exception) {
            is ClientError.NetworkTimeout -> true
            is ClientError.NetworkIO -> true
            is ResourceException.ResourceUnavailable -> true
            is ApiException.HttpError -> exception.statusCode in listOf(
                HTTP_TOO_MANY_REQUESTS,
                HTTP_SERVICE_UNAVAILABLE,
                HTTP_GATEWAY_TIMEOUT
            )
            else -> false
        }

    /**
     * Get a user-friendly error message for an exception.
     *
     * @param exception The exception to format
     * @return A user-friendly error message
     */
    fun getUserFriendlyMessage(exception: Throwable): String =
        when (exception) {
            is VMException.InvalidStateTransition ->
                "The VM is currently ${exception.currentState} and cannot ${exception.attemptedOperation}. " +
                    "Please wait for the current operation to complete or stop the VM first."

            is ValidationException.MissingRequiredField ->
                "Configuration error: ${exception.fieldName} is required for ${exception.context}."

            is ResourceException.ResourceUnavailable ->
                "The ${exception.resourceType} '${exception.resourceId}' is currently unavailable. " +
                    "It may be in use by another VM or process."

            is ApiException.ConnectionFailed ->
                "Unable to connect to Firecracker. Please ensure the Firecracker process is running " +
                    "and accessible at ${exception.socketPath}."

            else -> exception.message ?: "An unexpected error occurred: ${exception.javaClass.simpleName}"
        }

    /**
     * Extract recovery suggestions for common errors.
     *
     * @param exception The exception to analyze
     * @return A list of suggested recovery actions
     */
    fun getRecoverySuggestions(exception: Throwable): List<String> =
        when (exception) {
            is VMException.InvalidStateTransition ->
                listOf(
                    "Wait for the current operation to complete",
                    "Stop the VM and try again",
                    "Check VM state before performing operations",
                )

            is ValidationException.InvalidRange ->
                listOf(
                    "Adjust the value to be within the valid range: ${exception.validRange}",
                    "Check the documentation for valid values",
                )

            is ResourceException.ResourceUnavailable ->
                listOf(
                    "Stop other VMs that might be using this resource",
                    "Use a different resource identifier",
                    "Wait and retry the operation",
                )

            is ApiException.ConnectionFailed ->
                listOf(
                    "Ensure Firecracker process is running",
                    "Check socket path permissions",
                    "Verify socket path exists and is accessible",
                )

            else -> listOf("Check logs for more details", "Retry the operation")
        }
}
