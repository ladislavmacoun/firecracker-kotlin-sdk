package org.firecracker.sdk.examples;

import org.firecracker.sdk.Firecracker;
import org.firecracker.sdk.VirtualMachine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Java test class to verify that the README Java examples compile correctly.
 * These tests don't actually run the VMs, just verify the syntax is correct.
 */
public class JavaReadmeExamplesTest {

    @Test
    public void testJavaExample() {
        // Corrected Java example - accessing Kotlin object properly
        VirtualMachine vm = Firecracker.INSTANCE.createVM(builder -> {
            builder.setName("java-vm");
            builder.setKernel("/path/to/vmlinux");
            builder.setVcpus(2);
            builder.setMemory(512);

            // Use convenience method for root drive (needs both path and readonly flag)
            builder.rootDrive("/path/to/rootfs.ext4", false);

            // Network configuration using builder function
            builder.addNetworkInterface(net -> {
                net.interfaceId("eth0");
                net.hostDevice("tap0");
                return null; // Required for Java lambda
            });

            return null; // Required for Java lambda
        });

        // Just verify it was created (won't actually start without real paths)
        assertNotNull(vm);
        assertEquals("java-vm", vm.getName());
    }

    @Test
    public void testSimplestJavaExample() {
        // Simplest possible Java example
        VirtualMachine vm = Firecracker.INSTANCE.createVM(builder -> {
            builder.setName("simple-vm");
            builder.setKernel("/path/to/vmlinux");
            builder.rootDrive("/path/to/rootfs.ext4", false);
            return null;
        });

        assertNotNull(vm);
        assertEquals("simple-vm", vm.getName());
    }
}