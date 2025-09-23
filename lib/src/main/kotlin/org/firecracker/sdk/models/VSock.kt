package org.firecracker.sdk.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Configuration for Firecracker VSock (Virtual Socket) device.
 *
 * VSock enables communication between the host and guest VM through
 * AF_VSOCK sockets. It provides a secure channel for data exchange
 * that bypasses the network stack.
 *
 * @property guestCid Unique context identifier for the guest VM (must be >= 3)
 * @property udsPath Path to Unix Domain Socket for host-guest communication
 * @property vsockId Optional device identifier (defaults to "vsock")
 */
@Serializable
data class VSock(
    @SerialName("guest_cid")
    val guestCid: UInt,
    @SerialName("uds_path")
    val udsPath: String,
    @SerialName("vsock_id")
    val vsockId: String = "vsock",
) {
    init {
        require(guestCid >= 3u) { "Guest CID must be >= 3 (0-2 are reserved)" }
        require(udsPath.isNotBlank()) { "UDS path cannot be blank" }
        require(vsockId.isNotBlank()) { "VSock ID cannot be blank" }
    }

    companion object {
        /**
         * Create a VSock device configuration.
         *
         * @param guestCid Context identifier for the guest (must be >= 3)
         * @param udsPath Path where Unix Domain Socket will be created
         * @param vsockId Device identifier (optional, defaults to "vsock")
         * @return VSock configuration
         */
        fun create(
            guestCid: UInt,
            udsPath: String,
            vsockId: String = "vsock",
        ): VSock =
            VSock(
                guestCid = guestCid,
                udsPath = udsPath,
                vsockId = vsockId,
            )

        /**
         * Create VSock device for a specific VM.
         *
         * Generates a unique UDS path based on VM ID and uses
         * a reasonable guest CID value.
         *
         * @param vmId Virtual machine identifier
         * @param baseDir Base directory for socket files (defaults to /tmp)
         * @param guestCid Guest context ID (defaults to auto-generated value)
         * @return VSock configuration with VM-specific socket path
         */
        fun forVm(
            vmId: String,
            baseDir: String = "/tmp",
            guestCid: UInt? = null,
        ): VSock {
            require(vmId.isNotBlank()) { "VM ID cannot be blank" }
            val actualCid = guestCid ?: (vmId.hashCode().toUInt() % 1000u + 100u)
            return VSock(
                guestCid = actualCid,
                udsPath = "$baseDir/firecracker-$vmId.vsock",
                vsockId = "vsock",
            )
        }

        /**
         * Create VSock device with automatic path generation.
         *
         * @param guestCid Context identifier for the guest
         * @param socketName Socket filename (without directory)
         * @param baseDir Base directory for socket files (defaults to /tmp)
         * @return VSock configuration with generated socket path
         */
        fun withSocketName(
            guestCid: UInt,
            socketName: String,
            baseDir: String = "/tmp",
        ): VSock {
            require(socketName.isNotBlank()) { "Socket name cannot be blank" }
            return VSock(
                guestCid = guestCid,
                udsPath = "$baseDir/$socketName",
                vsockId = "vsock",
            )
        }
    }
}
