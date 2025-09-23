# Integration Testing with Kata Containers and Firecracker

This directory contains integration tests that demonstrate how the Firecracker Kotlin/Java SDK works with real Firecracker microVMs using Kata Containers.

## Kata Containers with Firecracker

[Kata Containers](https://katacontainers.io/) provides a secure container runtime that uses Firecracker as the Virtual Machine Monitor (VMM). This gives us:

- **Production-ready environment**: Kata is actively maintained and production-tested
- **Real Firecracker integration**: Containers actually run in Firecracker microVMs
- **Standard container interface**: Works with Docker, Kubernetes, and other container orchestrators
- **Security isolation**: Each container runs in its own lightweight VM

## Setup for Real Testing

To run these tests against actual Firecracker microVMs:

### 1. Install Kata Containers Runtime

```bash
# Install Kata Containers (varies by OS)
# On Ubuntu/Debian:
sudo apt update
sudo apt install kata-containers

# Configure Docker to use Kata runtime
sudo mkdir -p /etc/docker
echo '{
  "runtimes": {
    "kata-fc": {
      "path": "/usr/bin/kata-runtime"
    }
  }
}' | sudo tee /etc/docker/daemon.json

sudo systemctl restart docker
```

### 2. Configure Firecracker Runtime

```bash
# Configure Kata to use Firecracker
sudo mkdir -p /etc/kata-containers
echo '[hypervisor.firecracker]
kernel = "/usr/share/kata-containers/vmlinux.container"
image = "/usr/share/kata-containers/kata-containers.img"
machine_type = "microvm"' | sudo tee /etc/kata-containers/configuration.toml
```

### 3. Run Tests with Firecracker

```bash
# Run integration tests with Kata/Firecracker runtime
./gradlew integrationTest -Dkata.runtime=firecracker
```

## Current Test Status

The integration tests currently demonstrate:

- ✅ **SDK API Structure**: Shows how the Firecracker SDK API would be used
- ✅ **Container Integration**: Uses TestContainers for reproducible test environment
- ✅ **Resource Management**: Handles kernel and rootfs setup
- 🔄 **Kata Integration**: Ready for real Kata Containers setup
- 🔄 **Firecracker VMs**: Ready for actual microVM testing

## Production Use Cases

With Kata Containers + Firecracker, the SDK enables:

1. **Serverless Functions**: Fast-starting microVMs for FaaS platforms
2. **Multi-tenant Workloads**: Strong isolation between containers
3. **Edge Computing**: Lightweight VMs for edge deployments
4. **CI/CD**: Secure build environments with VM-level isolation

## References

- [Kata Containers with Firecracker](https://github.com/kata-containers/documentation/wiki/Initial-release-of-Kata-Containers-with-Firecracker-support)
- [Firecracker Documentation](https://firecracker-microvm.github.io/)
- [Kata Containers Configuration](https://github.com/kata-containers/kata-containers/blob/main/docs/how-to/containerd-kata.md)