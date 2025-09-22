# Firecracker Kotlin SDK

A Kotlin-first SDK for interacting with [Firecracker microVMs](https://firecracker-microvm.github.io/), with full Java interoperability.

[![CI](https://github.com/ladislavmacoun/firecracker-kotlin-sdk/actions/workflows/ci.yml/badge.svg)](https://github.com/ladislavmacoun/firecracker-kotlin-sdk/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

## Features

- ✅ **Complete VM Lifecycle**: Boot sources, drives, machine configuration, network interfaces
- ✅ **Rate Limiting**: Network and drive rate limiting with token bucket algorithms
- ✅ **Type Safety**: Comprehensive Kotlin type system with validation
- ✅ **Coroutine Support**: Async/await patterns for non-blocking operations
- ✅ **JSON Serialization**: Full kotlinx.serialization support matching Firecracker API
- ✅ **DSL Builder**: Idiomatic Kotlin builder pattern for easy VM creation
- ✅ **Result API**: Functional error handling with Kotlin Result types
- ✅ **Java Interop**: Seamless usage from Java projects

## Quick Start

### Kotlin Usage

```kotlin
import org.firecracker.sdk.*

// Create a VM using the DSL builder
val vm = Firecracker.build {
    kernelPath = "/path/to/vmlinux"
    machineConfig {
        vcpuCount = 2
        memSizeMib = 512
    }
    rootDrive("/path/to/rootfs.ext4")
    networkInterface {
        interfaceId = "eth0"
        hostDevName = "tap0"
    }
}

// Start the VM
vm.start().onSuccess {
    println("VM started successfully!")
}.onFailure { error ->
    println("Failed to start VM: ${error.message}")
}
```

### Java Usage

```java
import org.firecracker.sdk.*;

// Create VM with builder pattern
VirtualMachine vm = Firecracker.builder()
    .kernelPath("/path/to/vmlinux")
    .machineConfig(config -> config
        .vcpuCount(2)
        .memSizeMib(512))
    .rootDrive("/path/to/rootfs.ext4")
    .networkInterface(net -> net
        .interfaceId("eth0")
        .hostDevName("tap0"))
    .build();

// Start the VM with Result handling
vm.start()
    .onSuccess(result -> System.out.println("VM started!"))
    .onFailure(error -> System.err.println("Failed: " + error.getMessage()));
```

## Requirements

- **Java 21** or higher
- **Firecracker binary** installed and accessible
- **Linux environment** (Firecracker requirement)

## Installation

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("org.firecracker:firecracker-kotlin-sdk:1.0.0")
}
```

### Gradle (Groovy DSL)

```groovy
dependencies {
    implementation 'org.firecracker:firecracker-kotlin-sdk:1.0.0'
}
```

### Maven

```xml
<dependency>
    <groupId>org.firecracker</groupId>
    <artifactId>firecracker-kotlin-sdk</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Development

### Building

```bash
./gradlew build
```

### Running Tests

```bash
./gradlew test
```

### Code Quality

The project uses automated quality checks:

```bash
# Format code
./gradlew ktlintFormat

# Run static analysis
./gradlew detekt

# Check all quality rules
./gradlew check
```

### Pre-commit Hooks

Pre-commit hooks automatically run:

- ktlint formatting
- detekt static analysis
- unit tests

## API Reference

### Core Classes

- **`VirtualMachine`**: Main VM lifecycle management
- **`FirecrackerClient`**: Low-level HTTP API client
- **`BootSource`**: Kernel and boot configuration
- **`Drive`**: Block device management with rate limiting
- **`MachineConfiguration`**: CPU and memory settings
- **`NetworkInterface`**: Network configuration with rate limiting

### Models

All models support:

- JSON serialization/deserialization
- Input validation
- Builder patterns
- Immutable data classes

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes
4. Run tests and quality checks (`./gradlew check`)
5. Commit your changes (`git commit -m 'Add amazing feature'`)
6. Push to the branch (`git push origin feature/amazing-feature`)
7. Open a Pull Request

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- [Firecracker](https://firecracker-microvm.github.io/) team for the amazing microVM technology
- [Kotlin](https://kotlinlang.org/) team for the excellent language and ecosystem
