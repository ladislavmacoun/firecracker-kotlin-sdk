package org.firecracker.sdk.retry

import kotlinx.coroutines.delay
import org.firecracker.sdk.exceptions.ErrorHandling
import kotlin.math.min
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for retry behavior.
 *
 * @property maxAttempts Maximum number of retry attempts (including the initial attempt)
 * @property initialDelay Initial delay between retries
 * @property maxDelay Maximum delay between retries
 * @property backoffMultiplier Multiplier for exponential backoff
 * @property jitterFactor Factor for random jitter (0.0 to 1.0)
 */
data class RetryConfig(
    val maxAttempts: Int = 3,
    val initialDelay: Duration = 100.milliseconds,
    val maxDelay: Duration = 5.seconds,
    val backoffMultiplier: Double = 2.0,
    val jitterFactor: Double = 0.1,
) {
    init {
        require(maxAttempts > 0) { "maxAttempts must be positive, got: $maxAttempts" }
        require(initialDelay.isPositive()) { "initialDelay must be positive" }
        require(maxDelay >= initialDelay) { "maxDelay must be >= initialDelay" }
        require(backoffMultiplier >= 1.0) { "backoffMultiplier must be >= 1.0" }
        require(jitterFactor in 0.0..1.0) { "jitterFactor must be between 0.0 and 1.0" }
    }

    companion object {
        /**
         * Default retry configuration for most operations.
         */
        val DEFAULT = RetryConfig()

        /**
         * Aggressive retry configuration for critical operations.
         */
        val AGGRESSIVE =
            RetryConfig(
                maxAttempts = 5,
                initialDelay = 50.milliseconds,
                maxDelay = 10.seconds,
                backoffMultiplier = 1.5,
                jitterFactor = 0.2,
            )

        /**
         * Conservative retry configuration for expensive operations.
         */
        val CONSERVATIVE =
            RetryConfig(
                maxAttempts = 2,
                initialDelay = 500.milliseconds,
                maxDelay = 2.seconds,
                backoffMultiplier = 1.0,
                jitterFactor = 0.0,
            )

        /**
         * No retry - fail immediately on first error.
         */
        val NONE = RetryConfig(maxAttempts = 1)
    }
}

/**
 * Retry policy that determines when and how to retry operations.
 */
interface RetryPolicy {
    /**
     * Determine if an operation should be retried given the exception and attempt number.
     *
     * @param exception The exception that occurred
     * @param attempt The current attempt number (1-based)
     * @param config The retry configuration
     * @return true if the operation should be retried
     */
    fun shouldRetry(
        exception: Throwable,
        attempt: Int,
        config: RetryConfig,
    ): Boolean

    /**
     * Calculate the delay before the next retry attempt.
     *
     * @param attempt The current attempt number (1-based)
     * @param config The retry configuration
     * @return The delay duration
     */
    fun calculateDelay(
        attempt: Int,
        config: RetryConfig,
    ): Duration
}

/**
 * Default retry policy based on exception type and transient error detection.
 */
object DefaultRetryPolicy : RetryPolicy {
    
    private const val JITTER_CENTER = 0.5
    
    override fun shouldRetry(
        exception: Throwable,
        attempt: Int,
        config: RetryConfig,
    ): Boolean {
        // Don't retry if we've exceeded max attempts
        if (attempt >= config.maxAttempts) return false

        // Use error handling utility to determine if retryable
        return ErrorHandling.isRetryable(exception)
    }

    override fun calculateDelay(
        attempt: Int,
        config: RetryConfig,
    ): Duration {
        // Exponential backoff with jitter
        val baseDelay = config.initialDelay.inWholeMilliseconds.toDouble()
        val exponentialDelay = baseDelay * config.backoffMultiplier.pow(attempt - 1)

        // Apply maximum delay cap
        val cappedDelay = min(exponentialDelay, config.maxDelay.inWholeMilliseconds.toDouble())

        // Add random jitter to avoid thundering herd
        val jitter = cappedDelay * config.jitterFactor * (Math.random() - JITTER_CENTER)
        val finalDelay = cappedDelay + jitter

        return finalDelay.toLong().milliseconds
    }
}

/**
 * Retry operations with configurable policies and backoff strategies.
 */
object Retry {
    /**
     * Execute a suspending operation with retry logic.
     *
     * @param config Retry configuration
     * @param policy Retry policy (defaults to DefaultRetryPolicy)
     * @param operation The operation to execute
     * @return Result of the operation
     */
    suspend fun <T> withRetry(
        config: RetryConfig = RetryConfig.DEFAULT,
        policy: RetryPolicy = DefaultRetryPolicy,
        operation: suspend () -> Result<T>,
    ): Result<T> = executeWithRetry(config, policy, operation, ::delay)

    /**
     * Execute a regular (non-suspending) operation with retry logic.
     *
     * @param config Retry configuration
     * @param policy Retry policy (defaults to DefaultRetryPolicy)
     * @param operation The operation to execute
     * @return Result of the operation
     */
    fun <T> withRetryBlocking(
        config: RetryConfig = RetryConfig.DEFAULT,
        policy: RetryPolicy = DefaultRetryPolicy,
        operation: () -> Result<T>,
    ): Result<T> = executeWithRetryBlocking(config, policy, operation) { Thread.sleep(it.inWholeMilliseconds) }

    private suspend fun <T> executeWithRetry(
        config: RetryConfig,
        policy: RetryPolicy,
        operation: suspend () -> Result<T>,
        delayFunc: suspend (Duration) -> Unit,
    ): Result<T> {
        var lastException: Throwable? = null

        repeat(config.maxAttempts) { attempt ->
            val attemptNumber = attempt + 1
            val result = runCatching { operation() }.getOrElse { exception ->
                lastException = exception
                return@repeat handleException(exception, attemptNumber, config, policy, delayFunc)
            }

            if (result.isSuccess) return result

            val exception = result.exceptionOrNull() ?: return result
            lastException = exception
            
            if (!policy.shouldRetry(exception, attemptNumber, config)) return result
            
            runCatching { 
                delayFunc(policy.calculateDelay(attemptNumber, config))
            }.getOrElse { return Result.failure(it) }
        }

        return Result.failure(lastException ?: RuntimeException("All retry attempts exhausted"))
    }

    private fun <T> executeWithRetryBlocking(
        config: RetryConfig,
        policy: RetryPolicy,
        operation: () -> Result<T>,
        delayFunc: (Duration) -> Unit,
    ): Result<T> {
        var lastException: Throwable? = null

        repeat(config.maxAttempts) { attempt ->
            val attemptNumber = attempt + 1
            val result = runCatching { operation() }.getOrElse { exception ->
                lastException = exception
                return@repeat handleExceptionBlocking(exception, attemptNumber, config, policy, delayFunc)
            }

            if (result.isSuccess) return result

            val exception = result.exceptionOrNull() ?: return result
            lastException = exception
            
            if (!policy.shouldRetry(exception, attemptNumber, config)) return result
            
            runCatching { 
                delayFunc(policy.calculateDelay(attemptNumber, config))
            }.getOrElse { return Result.failure(it) }
        }

        return Result.failure(lastException ?: RuntimeException("All retry attempts exhausted"))
    }

    private suspend fun handleException(
        exception: Throwable,
        attemptNumber: Int,
        config: RetryConfig,
        policy: RetryPolicy,
        delayFunc: suspend (Duration) -> Unit,
    ) {
        if (policy.shouldRetry(exception, attemptNumber, config)) {
            runCatching { 
                delayFunc(policy.calculateDelay(attemptNumber, config))
            }
        }
    }

    private fun handleExceptionBlocking(
        exception: Throwable,
        attemptNumber: Int,
        config: RetryConfig,
        policy: RetryPolicy,
        delayFunc: (Duration) -> Unit,
    ) {
        if (policy.shouldRetry(exception, attemptNumber, config)) {
            runCatching { 
                delayFunc(policy.calculateDelay(attemptNumber, config))
            }
        }
    }
}

/**
 * Extension function to add retry behavior to Result-returning operations.
 */
suspend fun <T> (suspend () -> Result<T>).withRetry(
    config: RetryConfig = RetryConfig.DEFAULT,
    policy: RetryPolicy = DefaultRetryPolicy,
): Result<T> = Retry.withRetry(config, policy, this)

/**
 * Extension function to add retry behavior to blocking Result-returning operations.
 */
fun <T> (() -> Result<T>).withRetryBlocking(
    config: RetryConfig = RetryConfig.DEFAULT,
    policy: RetryPolicy = DefaultRetryPolicy,
): Result<T> = Retry.withRetryBlocking(config, policy, this)
