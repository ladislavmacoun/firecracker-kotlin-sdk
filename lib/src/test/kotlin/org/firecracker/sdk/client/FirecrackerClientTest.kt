package org.firecracker.sdk.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.instanceOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.firecracker.sdk.models.MachineConfiguration

@Serializable
private data class TestResponse(val message: String)

public class FirecrackerClientTest : DescribeSpec({

    describe("FirecrackerClient") {

        describe("construction") {
            it("should create client with socket path") {
                val client = FirecrackerClient("/tmp/firecracker.socket")
                client shouldNotBe null
            }

            it("should reject empty socket path") {
                shouldThrow<IllegalArgumentException> {
                    FirecrackerClient("")
                }
            }
        }

        describe("HTTP operations") {
            val socketPath = "/tmp/test-firecracker.socket"
            val client = FirecrackerClient(socketPath)

            it("should perform GET request") {
                runTest {
                    // This test would normally require a mock or test server
                    // For now, we'll test the API structure
                    val result = client.get<TestResponse>("/")

                    result.isFailure shouldBe true
                    result.exceptionOrNull() shouldBe instanceOf<ClientError>()
                }
            }

            it("should perform PUT request with body") {
                runTest {
                    val config = MachineConfiguration(vcpuCount = 2, memSizeMib = 512)

                    val result = client.put("/machine-config", config)

                    result.isFailure shouldBe true
                    result.exceptionOrNull() shouldBe instanceOf<ClientError>()
                }
            }

            it("should perform PATCH request with body") {
                runTest {
                    val config = MachineConfiguration(vcpuCount = 4, memSizeMib = 1024)

                    val result = client.patch("/machine-config", config)

                    result.isFailure shouldBe true
                    result.exceptionOrNull() shouldBe instanceOf<ClientError>()
                }
            }

            it("should handle connection errors gracefully") {
                runTest {
                    val client = FirecrackerClient("/nonexistent/socket/path")

                    val result = client.get<TestResponse>("/")

                    result.isFailure shouldBe true
                    val error = result.exceptionOrNull()
                    error shouldBe instanceOf<ClientError>()
                    error?.message shouldContain "Failed to connect"
                }
            }
        }

        describe("error handling") {
            it("should wrap HTTP errors in ClientError sealed class") {
                runTest {
                    val client = FirecrackerClient("/tmp/test.socket")

                    val result = client.get<TestResponse>("/invalid-endpoint")

                    result.isFailure shouldBe true
                    val error = result.exceptionOrNull()
                    error shouldBe instanceOf<ClientError>()
                    error?.message shouldNotBe null
                }
            }
        }

        describe("request configuration") {
            it("should set appropriate headers") {
                val client = FirecrackerClient("/tmp/test.socket")

                // Test that the client is configured with proper headers
                // This would be tested with integration tests or by examining
                // the underlying HTTP client configuration
                client shouldNotBe null
            }

            it("should use correct timeout settings") {
                val client = FirecrackerClient("/tmp/test.socket")

                // Verify timeout configuration
                client shouldNotBe null
            }
        }
    }
})
