package org.firecracker.sdk.models

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

public class MachineConfigurationTest : DescribeSpec({
    val json = Json { ignoreUnknownKeys = true }

    describe("MachineConfiguration") {
        
        describe("creation") {
            it("should create machine config with valid vCPU count and memory") {
                val config = MachineConfiguration(
                    vcpuCount = 2,
                    memSizeMib = 512
                )
                
                config.vcpuCount shouldBe 2
                config.memSizeMib shouldBe 512
                config.smt shouldBe false // default value
                config.trackDirtyPages shouldBe false // default value
                config.cpuTemplate shouldBe CpuTemplate.None // default value
                config.hugePages shouldBe HugePages.None // default value
            }
            
            it("should create machine config with all parameters specified") {
                val config = MachineConfiguration(
                    vcpuCount = 4,
                    memSizeMib = 1024,
                    smt = true,
                    trackDirtyPages = true,
                    cpuTemplate = CpuTemplate.C3,
                    hugePages = HugePages.M2
                )
                
                config.vcpuCount shouldBe 4
                config.memSizeMib shouldBe 1024
                config.smt shouldBe true
                config.trackDirtyPages shouldBe true
                config.cpuTemplate shouldBe CpuTemplate.C3
                config.hugePages shouldBe HugePages.M2
            }
        }
        
        describe("validation") {
            it("should reject invalid vCPU count (0)") {
                shouldThrow<IllegalArgumentException> {
                    MachineConfiguration(vcpuCount = 0, memSizeMib = 512)
                }
            }
            
            it("should reject invalid vCPU count (> 32)") {
                shouldThrow<IllegalArgumentException> {
                    MachineConfiguration(vcpuCount = 33, memSizeMib = 512)
                }
            }
            
            it("should reject odd vCPU count when SMT is enabled") {
                shouldThrow<IllegalArgumentException> {
                    MachineConfiguration(vcpuCount = 3, memSizeMib = 512, smt = true)
                }
            }
            
            it("should reject invalid memory size (0)") {
                shouldThrow<IllegalArgumentException> {
                    MachineConfiguration(vcpuCount = 1, memSizeMib = 0)
                }
            }
            
            it("should reject non-even memory size with 2M huge pages") {
                shouldThrow<IllegalArgumentException> {
                    MachineConfiguration(
                        vcpuCount = 1, 
                        memSizeMib = 513, // odd number
                        hugePages = HugePages.M2
                    )
                }
            }
        }
        
        describe("serialization") {
            it("should serialize to JSON correctly") {
                val config = MachineConfiguration(
                    vcpuCount = 2,
                    memSizeMib = 512,
                    smt = false,
                    trackDirtyPages = true
                )
                
                val jsonString = json.encodeToString(config)
                // Default values are omitted in kotlinx.serialization
                val expectedJson = """{"vcpu_count":2,"mem_size_mib":512,"track_dirty_pages":true}"""
                
                jsonString shouldBe expectedJson
            }
            
            it("should serialize non-default values") {
                val config = MachineConfiguration(
                    vcpuCount = 4,
                    memSizeMib = 1024,
                    smt = true,
                    trackDirtyPages = false,
                    cpuTemplate = CpuTemplate.T2,
                    hugePages = HugePages.M2
                )
                
                val jsonString = json.encodeToString(config)
                val expectedJson = """{"vcpu_count":4,"mem_size_mib":1024,"smt":true,"cpu_template":"T2","huge_pages":"2M"}"""
                
                jsonString shouldBe expectedJson
            }
            
            it("should deserialize from JSON correctly") {
                val jsonString = """
                    {
                        "vcpu_count": 4,
                        "mem_size_mib": 1024,
                        "smt": true,
                        "track_dirty_pages": false,
                        "cpu_template": "T2",
                        "huge_pages": "2M"
                    }
                """
                
                val config = json.decodeFromString<MachineConfiguration>(jsonString)
                
                config.vcpuCount shouldBe 4
                config.memSizeMib shouldBe 1024
                config.smt shouldBe true
                config.trackDirtyPages shouldBe false
                config.cpuTemplate shouldBe CpuTemplate.T2
                config.hugePages shouldBe HugePages.M2
            }
            
            it("should deserialize with missing optional fields") {
                val jsonString = """
                    {
                        "vcpu_count": 1,
                        "mem_size_mib": 256
                    }
                """
                
                val config = json.decodeFromString<MachineConfiguration>(jsonString)
                
                config.vcpuCount shouldBe 1
                config.memSizeMib shouldBe 256
                config.smt shouldBe false
                config.trackDirtyPages shouldBe false
                config.cpuTemplate shouldBe CpuTemplate.None
                config.hugePages shouldBe HugePages.None
            }
        }
    }
})