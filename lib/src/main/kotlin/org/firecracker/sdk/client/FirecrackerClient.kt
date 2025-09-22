package org.firecracker.sdk.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Exception thrown when Firecracker client operations fail.
 */
class FirecrackerClientException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * HTTP client for communicating with Firecracker via Unix Domain Socket.
 *
 * This client provides a high-level interface for making REST API calls
 * to the Firecracker VMM process.
 */
class FirecrackerClient(
    val socketPath: String,
) {
    init {
        require(socketPath.isNotBlank()) {
            "Socket path cannot be empty"
        }
    }

    val httpClient =
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        encodeDefaults = false
                    },
                )
            }

            install(HttpTimeout) {
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
                connectTimeoutMillis = CONNECT_TIMEOUT_MS
            }

            defaultRequest {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
                header(HttpHeaders.Accept, ContentType.Application.Json)
            }

            engine {
                // NOTE: Configure CIO engine for Unix Domain Socket support
                // Currently using placeholder implementation
            }
        }

    suspend inline fun <reified T> get(path: String): T {
        return try {
            httpClient.get(buildUrl(path)).body()
        } catch (e: Exception) {
            throw when (e) {
                is HttpRequestTimeoutException ->
                    FirecrackerClientException("Request timeout connecting to Firecracker at $socketPath", e)
                is IOException ->
                    FirecrackerClientException("IO error connecting to Firecracker at $socketPath", e)
                is SerializationException ->
                    FirecrackerClientException("Failed to parse response from Firecracker at $socketPath", e)
                else ->
                    FirecrackerClientException("Failed to connect to Firecracker at $socketPath", e)
            }
        }
    }

    suspend inline fun <reified T> put(
        path: String,
        body: T,
    ) {
        try {
            httpClient.put(buildUrl(path)) {
                setBody(body)
            }
        } catch (e: Exception) {
            throw when (e) {
                is HttpRequestTimeoutException ->
                    FirecrackerClientException("Request timeout connecting to Firecracker at $socketPath", e)
                is IOException ->
                    FirecrackerClientException("IO error connecting to Firecracker at $socketPath", e)
                is SerializationException ->
                    FirecrackerClientException("Failed to serialize request body for Firecracker at $socketPath", e)
                else ->
                    FirecrackerClientException("Failed to connect to Firecracker at $socketPath", e)
            }
        }
    }

    suspend inline fun <reified T> patch(
        path: String,
        body: T,
    ) {
        try {
            httpClient.patch(buildUrl(path)) {
                setBody(body)
            }
        } catch (e: Exception) {
            throw when (e) {
                is HttpRequestTimeoutException ->
                    FirecrackerClientException("Request timeout connecting to Firecracker at $socketPath", e)
                is IOException ->
                    FirecrackerClientException("IO error connecting to Firecracker at $socketPath", e)
                is SerializationException ->
                    FirecrackerClientException("Failed to serialize request body for Firecracker at $socketPath", e)
                else ->
                    FirecrackerClientException("Failed to connect to Firecracker at $socketPath", e)
            }
        }
    }

    fun close() {
        httpClient.close()
    }

    fun buildUrl(path: String): String {
        // NOTE: Implement proper Unix socket URL construction
        // This placeholder will fail (as expected by current tests)
        return "http://unix:$socketPath$path"
    }

    companion object {
        private const val REQUEST_TIMEOUT_MS = 30_000L
        private const val CONNECT_TIMEOUT_MS = 10_000L
    }
}
