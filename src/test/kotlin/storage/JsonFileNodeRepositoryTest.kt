package storage

import com.kebab.v2rayk.wrapper.config.Outbound
import com.kebab.v2rayk.wrapper.config.V2rayProperties
import com.kebab.v2rayk.wrapper.config.bound.ProtocolType
import com.kebab.v2rayk.wrapper.config.bound.settings.ShadowsocksOutboundSettings
import com.kebab.v2rayk.wrapper.config.bound.settings.ShadowsocksServer
import com.kebab.v2rayk.wrapper.config.bound.settings.VmessOutboundSettings
import com.kebab.v2rayk.wrapper.config.bound.settings.VmessServer
import com.kebab.v2rayk.wrapper.config.bound.settings.VmessUser
import kotlin.test.*
import java.io.File

class JsonFileNodeRepositoryTest {
    private lateinit var repo: JsonFileNodeRepository
    private lateinit var testBaseDir: File
    private var originalUserHome: String? = null

    @BeforeTest
    fun setUp() {
        // 保存原始 user.home 并指向临时目录进行测试
        originalUserHome = System.getProperty("user.home")
        val tmpDir = System.getProperty("java.io.tmpdir")
        // 使用唯一子目录避免并行测试冲突
        testBaseDir = File(tmpDir, ".v2rayk-nodes-test-${System.currentTimeMillis()}")
        System.setProperty("user.home", testBaseDir.absolutePath)
        repo = JsonFileNodeRepository()
    }

    @AfterTest
    fun tearDown() {
        // 清理测试目录（JsonFileNodeRepository 将数据存于 {user.home}/.v2rayk/nodes）
        if (::testBaseDir.isInitialized) {
            val nodesDir = File(testBaseDir, ".v2rayk/nodes")
            if (nodesDir.exists()) nodesDir.deleteRecursively()
            if (testBaseDir.exists()) testBaseDir.deleteRecursively()
        }
        originalUserHome?.let { System.setProperty("user.home", it) }
    }

    private fun createTestConfig(): V2rayProperties {
        return V2rayProperties(
            outbounds = listOf(
                Outbound(
                    protocol = ProtocolType.SHADOWSOCKS,
                    tag = "PROXY",
                    settings = ShadowsocksOutboundSettings(
                        servers = listOf(
                            ShadowsocksServer(
                                address = "test.example.com",
                                port = 8388,
                                method = "aes-256-gcm",
                                password = "testpass",
                            )
                        )
                    ),
                )
            )
        )
    }

    @Test
    fun `save writes config to JSON file and returns path`() {
        val config = createTestConfig()
        val path = repo.save("Test Group", "Test Node", config)

        val file = File(path)
        assertTrue(file.exists())
        assertEquals("json", file.extension)
        assertTrue(file.readText().contains("test.example.com"))
    }

    @Test
    fun `save overwrites existing config with same name`() {
        val config1 = createTestConfig()
        repo.save("GroupA", "NodeX", config1)

        val config2 = createTestConfig().copy(
            outbounds = listOf(
                Outbound(
                    protocol = ProtocolType.VMESS,
                    tag = "NEW_TAG",
                    settings = VmessOutboundSettings(
                        vnext = listOf(
                            VmessServer(
                                address = "new.example.com",
                                port = 443,
                                users = listOf(VmessUser(id = "new-uuid")),
                            )
                        )
                    ),
                )
            )
        )
        repo.save("GroupA", "NodeX", config2)

        val all = repo.loadAll()
        val nodes = all["GroupA"]!!
        assertEquals(1, nodes.size)
        assertEquals("vmess", nodes[0].second.outbounds!![0].protocol.protocolName)
    }

    @Test
    fun `loadAll returns empty map for non-existent directory`() {
        // 先清理已有数据目录确保空状态
        val nodesDir = File(testBaseDir, ".v2rayk/nodes")
        if (nodesDir.exists()) nodesDir.deleteRecursively()
        val result = repo.loadAll()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `loadAll returns grouped nodes`() {
        val config = createTestConfig()
        repo.save("Group1", "Node1", config)
        repo.save("Group1", "Node2", config)
        repo.save("Group2", "Node3", config)

        val all = repo.loadAll()
        assertEquals(2, all.size)
        assertEquals(2, all["Group1"]!!.size)
        assertEquals(1, all["Group2"]!!.size)
        assertEquals("Node1", all["Group1"]!![0].first)
        assertEquals("Node2", all["Group1"]!![1].first)
    }

    @Test
    fun `delete removes config file`() {
        val config = createTestConfig()
        val path = repo.save("GroupD", "NodeD", config)
        assertTrue(File(path).exists())

        repo.delete("GroupD", "NodeD")
        assertFalse(File(path).exists())
    }

    @Test
    fun `delete non-existent node does not throw`() {
        repo.delete("NoGroup", "NoNode")
        // Should not throw
    }

    @Test
    fun `save sanitizes group and node names with illegal chars`() {
        val config = createTestConfig()
        val path = repo.save("Group:A<B>C", "Node:1*2?3", config)
        val file = File(path)
        assertTrue(file.exists())
        assertFalse(file.name.contains(":"))
        assertFalse(file.name.contains("<"))
    }
}
