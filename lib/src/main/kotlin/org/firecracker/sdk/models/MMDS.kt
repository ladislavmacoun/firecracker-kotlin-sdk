package org.firecracker.sdk.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.Inet4Address
import java.net.InetAddress

/**
 * Metadata Service (MMDS) configuration for providing metadata to guests.
 *
 * MMDS provides a way for guests to access metadata using HTTP requests to a well-known IP address.
 * This is commonly used for cloud-init and other initialization systems.
 *
 * @param version MMDS version (V1 or V2)
 * @param networkInterfaces Network interfaces allowed to access MMDS
 * @param ipv4Address IPv4 address for MMDS endpoint (defaults to 169.254.169.254)
 */
@Serializable
data class MMDS(
    val version: MMDSVersion,
    @SerialName("network_interfaces")
    val networkInterfaces: List<String>,
    @SerialName("ipv4_address")
    val ipv4Address: String = DEFAULT_MMDS_IPV4_ADDRESS,
) {
    init {
        require(networkInterfaces.isNotEmpty()) {
            "At least one network interface must be specified for MMDS"
        }
        networkInterfaces.forEach { interfaceId ->
            require(interfaceId.isNotBlank()) {
                "Network interface ID cannot be blank"
            }
        }
        require(ipv4Address.isNotBlank()) {
            "IPv4 address cannot be blank"
        }
        require(isValidIpv4Address(ipv4Address)) {
            "Invalid IPv4 address format: $ipv4Address"
        }
    }

    companion object {
        const val DEFAULT_MMDS_IPV4_ADDRESS = "169.254.169.254"

        /**
         * Create MMDS configuration with V2 (default) version.
         */
        fun create(
            networkInterfaces: List<String>,
            ipv4Address: String = DEFAULT_MMDS_IPV4_ADDRESS,
        ): MMDS = MMDS(MMDSVersion.V2, networkInterfaces, ipv4Address)

        /**
         * Create MMDS configuration with V1 version.
         */
        fun createV1(
            networkInterfaces: List<String>,
            ipv4Address: String = DEFAULT_MMDS_IPV4_ADDRESS,
        ): MMDS = MMDS(MMDSVersion.V1, networkInterfaces, ipv4Address)

        /**
         * Create MMDS configuration for a single network interface.
         */
        fun forInterface(
            interfaceId: String,
            ipv4Address: String = DEFAULT_MMDS_IPV4_ADDRESS,
        ): MMDS {
            require(interfaceId.isNotBlank()) { "Interface ID cannot be blank" }
            return create(listOf(interfaceId), ipv4Address)
        }

        /**
         * Create MMDS configuration for default eth0 interface.
         */
        fun forDefaultInterface(ipv4Address: String = DEFAULT_MMDS_IPV4_ADDRESS): MMDS = forInterface("eth0", ipv4Address)

        /**
         * Validate IPv4 address format using Java standard library with strict format checking.
         * Firecracker requires strict dotted-decimal notation (e.g., "192.168.1.100").
         */
        private fun isValidIpv4Address(address: String): Boolean {
            return try {
                validateIpv4Format(address) && validateWithJavaNet(address)
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Exception,
            ) {
                // Intentionally swallow exceptions - this is a validation function that should return false
                // for any invalid input, including malformed addresses that cause exceptions
                false
            }
        }

        private fun validateIpv4Format(address: String): Boolean {
            val parts = address.split(".")
            if (parts.size != IPV4_OCTET_COUNT) {
                return false
            }

            return parts.all { part ->
                validateIpv4Octet(part)
            }
        }

        private fun validateIpv4Octet(part: String): Boolean {
            val isValidFormat = part.isNotEmpty() && !(part.length > 1 && part.startsWith("0"))
            val num = if (isValidFormat) part.toIntOrNull() else null
            return num != null && num in MIN_IPV4_OCTET_VALUE..MAX_IPV4_OCTET_VALUE
        }

        private fun validateWithJavaNet(address: String): Boolean {
            val inetAddress = InetAddress.getByName(address)
            return inetAddress is Inet4Address
        }

        private const val IPV4_OCTET_COUNT = 4
        private const val MIN_IPV4_OCTET_VALUE = 0
        private const val MAX_IPV4_OCTET_VALUE = 255
    }
}

/**
 * MMDS version specification.
 */
@Serializable
enum class MMDSVersion {
    @SerialName("V1")
    V1,

    @SerialName("V2")
    V2,
}

/**
 * MMDS configuration for updating metadata content.
 */
@Serializable
data class MMDSConfig(
    val metadata: JsonElement,
) {
    companion object {
        /**
         * Create MMDS configuration with JSON object metadata.
         */
        fun create(metadata: JsonObject): MMDSConfig = MMDSConfig(metadata)

        /**
         * Create MMDS configuration with arbitrary JSON element.
         */
        fun withMetadata(metadata: JsonElement): MMDSConfig = MMDSConfig(metadata)

        /**
         * Create basic MMDS configuration with common cloud-init metadata.
         */
        fun cloudInit(
            instanceId: String,
            hostname: String,
            userData: String? = null,
        ): MMDSConfig {
            require(instanceId.isNotBlank()) { "Instance ID cannot be blank" }
            require(hostname.isNotBlank()) { "Hostname cannot be blank" }

            val metadataMap = mutableMapOf<String, JsonElement>()
            metadataMap["instance-id"] = JsonPrimitive(instanceId)
            metadataMap["local-hostname"] = JsonPrimitive(hostname)

            if (userData != null) {
                metadataMap["user-data"] = JsonPrimitive(userData)
            }

            return MMDSConfig(JsonObject(metadataMap))
        }
    }
}
