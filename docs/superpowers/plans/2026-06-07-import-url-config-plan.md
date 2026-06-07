# URL 导入配置功能 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** 在导航栏新增"导入"按钮，支持粘贴 vmess:// 和 ss:// 分享链接批量导入为 V2Ray 出站配置并持久化为 JSON 文件。

**Architecture:** 解析逻辑放入 v2ray-core-wrapper（策略模式：ImportStrategy 接口 + VmessStrategy / ShadowsocksStrategy 实现），v2rayk 负责 UI（ImportDialog Composable）和存储（NodeRepository 接口 + JsonFileNodeRepository）。

**Tech Stack:** Kotlin 2.1.0, Compose Desktop 1.7.3, Jackson 2.15.2, JUnit 5 (kotlin-test)

---

## 文件结构

| 动作 | 文件路径 | 职责 |
|------|----------|------|
| Create | `modules/v2ray-core-wrapper/src/main/kotlin/com/kebab/v2rayk/wrapper/config/import/ImportException.kt` | 导入异常体系 |
| Create | `modules/v2ray-core-wrapper/src/main/kotlin/com/kebab/v2rayk/wrapper/config/import/ImportStrategy.kt` | 策略接口 |
| Create | `modules/v2ray-core-wrapper/src/main/kotlin/com/kebab/v2rayk/wrapper/config/import/ImportStrategyFactory.kt` | 策略工厂（scheme 路由） |
| Create | `modules/v2ray-core-wrapper/src/main/kotlin/com/kebab/v2rayk/wrapper/config/import/VmessStrategy.kt` | vmess:// → V2rayProperties |
| Create | `modules/v2ray-core-wrapper/src/test/kotlin/com/kebab/v2rayk/wrapper/config/import/VmessStrategyTest.kt` | Vmess 解析测试 |
| Create | `modules/v2ray-core-wrapper/src/main/kotlin/com/kebab/v2rayk/wrapper/config/import/ShadowsocksStrategy.kt` | ss:// → V2rayProperties |
| Create | `modules/v2ray-core-wrapper/src/test/kotlin/com/kebab/v2rayk/wrapper/config/import/ShadowsocksStrategyTest.kt` | SS 解析测试 |
| Create | `modules/v2ray-core-wrapper/src/test/kotlin/com/kebab/v2rayk/wrapper/config/import/ImportStrategyFactoryTest.kt` | 工厂测试 |
| Modify | `src/main/kotlin/ProxyNode.kt` | 增加 configPath 字段 |
| Create | `src/main/kotlin/storage/NodeRepository.kt` | 存储接口 |
| Create | `src/main/kotlin/storage/JsonFileNodeRepository.kt` | JSON 文件存储实现 |
| Create | `src/test/kotlin/storage/JsonFileNodeRepositoryTest.kt` | 存储层测试 |
| Create | `src/main/kotlin/ui/ImportDialog.kt` | 导入对话框 UI |
| Modify | `src/main/kotlin/SingleColumnListWithMenu.kt` | TopAppBar 增加"导入"按钮 |
| Modify | `src/main/kotlin/Main.kt` | 集成 repository 和 import dialog 到状态 |

---

### Task 1: 创建导入异常体系

**Files:**
- Create: `modules/v2ray-core-wrapper/src/main/kotlin/com/kebab/v2rayk/wrapper/config/import/ImportException.kt`

- [ ] **Step 1: 创建 ImportException 异常类**

```kotlin
package com.kebab.v2rayk.wrapper.config.import

/**
 * 导入 URL 解析异常基类。
 */
open class ImportException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * 无对应策略异常（不支持的 scheme）。
 */
class UnsupportedSchemeException(scheme: String) : ImportException("不支持的链接类型: $scheme")

/**
 * URL 格式非法异常（Base64、JSON 解析失败或必填字段缺失）。
 */
class InvalidUrlFormatException(message: String, cause: Throwable? = null) : ImportException(message, cause)
```

- [ ] **Step 2: 编译验证**

```bash
cd modules/v2ray-core-wrapper && ./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
cd modules/v2ray-core-wrapper
git add src/main/kotlin/com/kebab/v2rayk/wrapper/config/import/ImportException.kt
git commit -m "feat: add ImportException hierarchy for URL import

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: 创建 ImportStrategy 接口和工厂

**Files:**
- Create: `modules/v2ray-core-wrapper/src/main/kotlin/com/kebab/v2rayk/wrapper/config/import/ImportStrategy.kt`
- Create: `modules/v2ray-core-wrapper/src/main/kotlin/com/kebab/v2rayk/wrapper/config/import/ImportStrategyFactory.kt`

- [ ] **Step 1: 创建 ImportStrategy 接口**

```kotlin
package com.kebab.v2rayk.wrapper.config.import

import com.kebab.v2rayk.wrapper.config.V2rayProperties

/**
 * URL 导入策略接口。
 * 每种协议（vmess、ss 等）实现自己的解析逻辑。
 *
 * 输入：单个 URL 字符串（已 trim()）
 * 输出：V2rayProperties（含一个 Outbound，tag = "PROXY"）
 */
interface ImportStrategy {
    /** 支持的 URL scheme 列表（不含 ://），如 ["vmess"] */
    val supportedSchemes: List<String>

    /**
     * 解析 URL 为 V2rayProperties。
     * @param url 完整的分享链接，如 vmess://... 或 ss://...
     * @throws ImportException 解析失败时抛出
     */
    fun parse(url: String): V2rayProperties
}
```

- [ ] **Step 2: 创建 ImportStrategyFactory**

```kotlin
package com.kebab.v2rayk.wrapper.config.import

/**
 * 导入策略工厂：根据 URL scheme 匹配对应的 ImportStrategy。
 * 新协议只需添加策略实现并注册到此工厂。
 */
object ImportStrategyFactory {
    private val strategies: Map<String, ImportStrategy> = listOf(
        VmessStrategy(),
        ShadowsocksStrategy(),
    ).flatMap { strategy ->
        strategy.supportedSchemes.map { scheme -> scheme to strategy }
    }.toMap()

    /**
     * 根据 URL 的 scheme 返回对应的策略。
     * @throws UnsupportedSchemeException 无匹配策略时抛出
     */
    fun create(url: String): ImportStrategy {
        val scheme = url.substringBefore("://")
        return strategies[scheme.lowercase()]
            ?: throw UnsupportedSchemeException(scheme)
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
cd modules/v2ray-core-wrapper && ./gradlew compileKotlin
```

Expected: BUILD FAILED (VmessStrategy 和 ShadowsocksStrategy 尚未创建)

- [ ] **Step 4: 提交**

```bash
cd modules/v2ray-core-wrapper
git add src/main/kotlin/com/kebab/v2rayk/wrapper/config/import/ImportStrategy.kt \
        src/main/kotlin/com/kebab/v2rayk/wrapper/config/import/ImportStrategyFactory.kt
git commit -m "feat: add ImportStrategy interface and factory

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: 实现 VmessStrategy

**Files:**
- Create: `modules/v2ray-core-wrapper/src/main/kotlin/com/kebab/v2rayk/wrapper/config/import/VmessStrategy.kt`

- [ ] **Step 1: 创建 VmessStrategy**

```kotlin
package com.kebab.v2rayk.wrapper.config.import

import com.kebab.v2rayk.wrapper.config.Outbound
import com.kebab.v2rayk.wrapper.config.V2rayProperties
import com.kebab.v2rayk.wrapper.config.bound.ProtocolType
import com.kebab.v2rayk.wrapper.config.bound.settings.VmessOutboundSettings
import com.kebab.v2rayk.wrapper.config.bound.settings.VmessServer
import com.kebab.v2rayk.wrapper.config.bound.settings.VmessUser
import com.kebab.v2rayk.wrapper.config.bound.settings.VmessSecurity
import com.kebab.v2rayk.wrapper.config.bound.stream.StreamSettings
import com.kebab.v2rayk.wrapper.config.bound.stream.TransportNetwork
import com.kebab.v2rayk.wrapper.config.bound.stream.TlsSettings
import java.net.URI
import java.util.Base64

/**
 * VMess 分享链接解析策略。
 *
 * 支持两种格式：
 * 1. V2RayN 明文格式: vmess://[{network}:]{uuid}-{aid}@{host}:{port}/{path}#{tag}
 * 2. 标准 Base64 格式: vmess://<base64-encoded JSON>
 */
class VmessStrategy : ImportStrategy {
    override val supportedSchemes: List<String> = listOf("vmess")

    override fun parse(url: String): V2rayProperties {
        val content = url.removePrefix("vmess://").trim()

        val parsed: VmessParsed = try {
            parseStandardFormat(content)
        } catch (e: Exception) {
            throw InvalidUrlFormatException("VMess URL 解析失败: ${e.message}", e)
        }

        val nodeName = parsed.nodeName ?: "${parsed.host}:${parsed.port}"

        val outbound = Outbound(
            protocol = ProtocolType.VMESS,
            sendThrough = "0.0.0.0",
            tag = nodeName,
            settings = VmessOutboundSettings(
                vnext = listOf(
                    VmessServer(
                        address = parsed.host,
                        port = parsed.port,
                        users = listOf(
                            VmessUser(
                                id = parsed.uuid,
                                alterId = parsed.aid,
                                security = VmessSecurity.AES_128_GCM,
                            )
                        ),
                    )
                ),
            ),
            streamSettings = StreamSettings(
                network = TransportNetwork.TCP,
                tlsSettings = TlsSettings(disableSystemRoot = false),
            ),
        )

        return V2rayProperties(outbounds = listOf(outbound))
    }

    private fun parseStandardFormat(content: String): VmessParsed {
        // Try Base64-encoded JSON format first
        // Standard vmess:// format: base64-encoded JSON with fields v/ps/add/port/id/aid/net/type/tls/...
        if (!content.contains("@") && !content.contains(":")) {
            return try {
                val json = String(Base64.getDecoder().decode(content))
                val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                val node = mapper.readTree(json)
                VmessParsed(
                    host = node["add"]?.asText() ?: throw InvalidUrlFormatException("缺少 add 字段"),
                    port = node["port"]?.asText()?.toIntOrNull() ?: throw InvalidUrlFormatException("缺少或非法 port 字段"),
                    uuid = node["id"]?.asText() ?: throw InvalidUrlFormatException("缺少 id 字段"),
                    aid = node["aid"]?.asText()?.toIntOrNull() ?: 0,
                    nodeName = node["ps"]?.asText(),
                )
            } catch (e: InvalidUrlFormatException) {
                throw e
            } catch (e: Exception) {
                throw InvalidUrlFormatException("Base64 解码或 JSON 解析失败", e)
            }
        }

        // V2RayN 明文格式: [{network}:]{uuid}-{aid}@{host}:{port}/{path}#{fragment}
        var remaining = content

        // Extract optional network prefix (e.g., "tcp:", "ws:")
        var network: TransportNetwork = TransportNetwork.TCP
        if (":" in remaining && remaining.substringBefore(":") in setOf("tcp", "ws", "kcp", "quic", "grpc", "http")) {
            val netStr = remaining.substringBefore(":")
            network = try {
                TransportNetwork.valueOf(netStr.uppercase())
            } catch (_: Exception) {
                TransportNetwork.TCP
            }
            remaining = remaining.substringAfter(":")
        }

        // Parse uuid-aid@host:port/path#fragment
        val atIndex = remaining.indexOf('@')
        if (atIndex == -1) throw InvalidUrlFormatException("VMess URL 缺少 @ 分隔符")

        val userInfo = remaining.substring(0, atIndex)
        // uuid is the first 36 chars (standard UUID format with hyphens)
        val lastDashInUser = userInfo.lastIndexOf('-')
        val uuid: String
        val aid: Int
        if (lastDashInUser > 0 && userInfo.length - lastDashInUser <= 3) {
            uuid = userInfo.substring(0, lastDashInUser)
            aid = userInfo.substring(lastDashInUser + 1).toIntOrNull() ?: 0
        } else {
            uuid = userInfo
            aid = 0
        }

        val hostPart = remaining.substring(atIndex + 1)
        val host: String
        val port: Int
        val nodeName: String?

        // Extract fragment (#tag) if present
        val fragmentIndex = hostPart.indexOf('#')
        val fragment: String?
        if (fragmentIndex >= 0) {
            val rawFragment = hostPart.substring(fragmentIndex + 1)
            // If fragment contains @, use the first part as the display name
            fragment = rawFragment.substringBefore("@").ifBlank { rawFragment }
            nodeName = fragment
        } else {
            fragment = null
            nodeName = null
        }

        val hostWithoutFragment = if (fragmentIndex >= 0) hostPart.substring(0, fragmentIndex) else hostPart
        val pathIndex = hostWithoutFragment.indexOf('/')
        val hostPort = if (pathIndex >= 0) hostWithoutFragment.substring(0, pathIndex) else hostWithoutFragment

        val colonIndex = hostPort.lastIndexOf(':')
        if (colonIndex >= 0) {
            host = hostPort.substring(0, colonIndex)
            port = hostPort.substring(colonIndex + 1).toIntOrNull()
                ?: throw InvalidUrlFormatException("非法端口: ${hostPort.substring(colonIndex + 1)}")
        } else {
            host = hostPort
            port = 443
        }

        if (port !in 1..65535) throw InvalidUrlFormatException("端口超出范围: $port")

        return VmessParsed(host = host, port = port, uuid = uuid, aid = aid, nodeName = nodeName)
    }

    private data class VmessParsed(
        val host: String,
        val port: Int,
        val uuid: String,
        val aid: Int,
        val nodeName: String?,
    )
}
```

- [ ] **Step 2: 编译验证**

```bash
cd modules/v2ray-core-wrapper && ./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
cd modules/v2ray-core-wrapper
git add src/main/kotlin/com/kebab/v2rayk/wrapper/config/import/VmessStrategy.kt
git commit -m "feat: add VmessStrategy for vmess:// URL import

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: VmessStrategy 单元测试

**Files:**
- Create: `modules/v2ray-core-wrapper/src/test/kotlin/com/kebab/v2rayk/wrapper/config/import/VmessStrategyTest.kt`

- [ ] **Step 1: 创建 VmessStrategyTest**

```kotlin
package com.kebab.v2rayk.wrapper.config.import

import com.kebab.v2rayk.wrapper.config.bound.ProtocolType
import com.kebab.v2rayk.wrapper.config.bound.settings.VmessOutboundSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class VmessStrategyTest {
    private val strategy = VmessStrategy()

    @Test
    fun `parse valid V2RayN format vmess URL`() {
        val url = "vmess://tcp:ecdd5fb7-867f-4218-8eb6-7c5be4aa06e1-0@c7s3.portablesubmarines.com:39120/#JMS-143136@c7s3.portablesubmarines.com:39120"
        val result = strategy.parse(url)

        val outbounds = result.outbounds
        assertNotNull(outbounds)
        assertEquals(1, outbounds.size)

        val outbound = outbounds[0]
        assertEquals(ProtocolType.VMESS, outbound.protocol)
        assertEquals("JMS-143136", outbound.tag)
        assertEquals("0.0.0.0", outbound.sendThrough)

        val settings = outbound.settings as VmessOutboundSettings
        assertEquals(1, settings.vnext.size)
        assertEquals("c7s3.portablesubmarines.com", settings.vnext[0].address)
        assertEquals(39120, settings.vnext[0].port)
        assertEquals("ecdd5fb7-867f-4218-8eb6-7c5be4aa06e1", settings.vnext[0].users[0].id)
        assertEquals(0, settings.vnext[0].users[0].alterId)
    }

    @Test
    fun `parse vmess URL without network prefix`() {
        val url = "vmess://ecdd5fb7-867f-4218-8eb6-7c5be4aa06e1-0@c7s3.portablesubmarines.com:39120/#MyNode"
        val result = strategy.parse(url)

        val settings = result.outbounds!![0].settings as VmessOutboundSettings
        assertEquals("c7s3.portablesubmarines.com", settings.vnext[0].address)
        assertEquals(39120, settings.vnext[0].port)
        assertEquals("MyNode", result.outbounds!![0].tag)
    }

    @Test
    fun `parse vmess URL without fragment uses host_port as tag`() {
        val url = "vmess://ecdd5fb7-867f-4218-8eb6-7c5be4aa06e1-0@1.2.3.4:8080/"
        val result = strategy.parse(url)
        assertEquals("1.2.3.4:8080", result.outbounds!![0].tag)
    }

    @Test
    fun `parse vmess URL uses fragment before at as tag`() {
        val url = "vmess://ecdd5fb7-867f-4218-8eb6-7c5be4aa06e1-0@1.2.3.4:8080/#NodeName@1.2.3.4:8080"
        val result = strategy.parse(url)
        assertEquals("NodeName", result.outbounds!![0].tag)
    }

    @Test
    fun `parse vmess URL without aid`() {
        val url = "vmess://ecdd5fb7-867f-4218-8eb6-7c5be4aa06e1@1.2.3.4:8080/#Test"
        val result = strategy.parse(url)
        val settings = result.outbounds!![0].settings as VmessOutboundSettings
        assertEquals(0, settings.vnext[0].users[0].alterId)
    }

    @Test
    fun `invalid vmess URL missing at sign throws InvalidUrlFormatException`() {
        assertFailsWith<InvalidUrlFormatException> {
            strategy.parse("vmess://this-is-not-a-valid-url")
        }
    }

    @Test
    fun `invalid vmess URL bad port throws InvalidUrlFormatException`() {
        assertFailsWith<InvalidUrlFormatException> {
            strategy.parse("vmess://ecdd5fb7-867f-4218-8eb6-7c5be4aa06e1-0@host:abc/#Test")
        }
    }

    @Test
    fun `invalid vmess URL port out of range throws InvalidUrlFormatException`() {
        assertFailsWith<InvalidUrlFormatException> {
            strategy.parse("vmess://ecdd5fb7-867f-4218-8eb6-7c5be4aa06e1-0@host:99999/#Test")
        }
    }
}
```

- [ ] **Step 2: 运行测试**

```bash
cd modules/v2ray-core-wrapper && ./gradlew test --tests "com.kebab.v2rayk.wrapper.config.import.VmessStrategyTest"
```

- [ ] **Step 3: 提交**

```bash
cd modules/v2ray-core-wrapper
git add src/test/kotlin/com/kebab/v2rayk/wrapper/config/import/VmessStrategyTest.kt
git commit -m "test: add VmessStrategy unit tests

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 5: 实现 ShadowsocksStrategy

**Files:**
- Create: `modules/v2ray-core-wrapper/src/main/kotlin/com/kebab/v2rayk/wrapper/config/import/ShadowsocksStrategy.kt`

- [ ] **Step 1: 创建 ShadowsocksStrategy**

```kotlin
package com.kebab.v2rayk.wrapper.config.import

import com.kebab.v2rayk.wrapper.config.Outbound
import com.kebab.v2rayk.wrapper.config.V2rayProperties
import com.kebab.v2rayk.wrapper.config.bound.ProtocolType
import com.kebab.v2rayk.wrapper.config.bound.settings.ShadowsocksOutboundSettings
import com.kebab.v2rayk.wrapper.config.bound.settings.ShadowsocksServer
import java.util.Base64

/**
 * Shadowsocks 分享链接解析策略（SIP002 格式）。
 *
 * 格式: ss://<base64(method:password)>@<host>:<port>#<tag>
 */
class ShadowsocksStrategy : ImportStrategy {
    override val supportedSchemes: List<String> = listOf("ss")

    override fun parse(url: String): V2rayProperties {
        val content = url.removePrefix("ss://").trim()

        val parsed = try {
            parseSip002(content)
        } catch (e: ImportException) {
            throw e
        } catch (e: Exception) {
            throw InvalidUrlFormatException("Shadowsocks URL 解析失败: ${e.message}", e)
        }

        val nodeName = parsed.nodeName ?: "${parsed.host}:${parsed.port}"

        val outbound = Outbound(
            protocol = ProtocolType.SHADOWSOCKS,
            sendThrough = "0.0.0.0",
            tag = nodeName,
            settings = ShadowsocksOutboundSettings(
                servers = listOf(
                    ShadowsocksServer(
                        address = parsed.host,
                        port = parsed.port,
                        method = parsed.method,
                        password = parsed.password,
                    )
                ),
            ),
        )

        return V2rayProperties(outbounds = listOf(outbound))
    }

    /**
     * 解析 SIP002 格式: <base64(method:password)>@<host>:<port>#<tag>
     */
    private fun parseSip002(content: String): SSParsed {
        // Extract fragment (#tag) first
        val fragmentIndex = content.indexOf('#')
        val fragment: String?
        val bodyWithoutFragment: String
        if (fragmentIndex >= 0) {
            bodyWithoutFragment = content.substring(0, fragmentIndex)
            val rawFragment = content.substring(fragmentIndex + 1)
            fragment = rawFragment.substringBefore("@").ifBlank { rawFragment }
        } else {
            bodyWithoutFragment = content
            fragment = null
        }

        val atIndex = bodyWithoutFragment.indexOf('@')
        if (atIndex == -1) throw InvalidUrlFormatException("SS URL 缺少 @ 分隔符")

        // Base64 decode method:password
        val encodedUserInfo = bodyWithoutFragment.substring(0, atIndex)
        val decodedUserInfo = try {
            String(Base64.getDecoder().decode(encodedUserInfo))
        } catch (e: Exception) {
            throw InvalidUrlFormatException("Base64 解码失败: ${e.message}", e)
        }

        val colonIndex = decodedUserInfo.indexOf(':')
        if (colonIndex == -1) throw InvalidUrlFormatException("SS 用户信息格式错误，缺少 method:password 分隔符")
        val method = decodedUserInfo.substring(0, colonIndex)
        val password = decodedUserInfo.substring(colonIndex + 1)

        // Parse host:port
        val hostPort = bodyWithoutFragment.substring(atIndex + 1)
        val lastColonIndex = hostPort.lastIndexOf(':')
        val host: String
        val port: Int
        if (lastColonIndex >= 0) {
            host = hostPort.substring(0, lastColonIndex)
            port = hostPort.substring(lastColonIndex + 1).toIntOrNull()
                ?: throw InvalidUrlFormatException("非法端口: ${hostPort.substring(lastColonIndex + 1)}")
        } else {
            host = hostPort
            port = 8388 // SS default port
        }

        if (port !in 1..65535) throw InvalidUrlFormatException("端口超出范围: $port")

        return SSParsed(
            host = host,
            port = port,
            method = method,
            password = password,
            nodeName = fragment,
        )
    }

    private data class SSParsed(
        val host: String,
        val port: Int,
        val method: String,
        val password: String,
        val nodeName: String?,
    )
}
```

- [ ] **Step 2: 编译验证**

```bash
cd modules/v2ray-core-wrapper && ./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
cd modules/v2ray-core-wrapper
git add src/main/kotlin/com/kebab/v2rayk/wrapper/config/import/ShadowsocksStrategy.kt
git commit -m "feat: add ShadowsocksStrategy for ss:// URL import

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 6: ShadowsocksStrategy 单元测试

**Files:**
- Create: `modules/v2ray-core-wrapper/src/test/kotlin/com/kebab/v2rayk/wrapper/config/import/ShadowsocksStrategyTest.kt`

- [ ] **Step 1: 创建 ShadowsocksStrategyTest**

```kotlin
package com.kebab.v2rayk.wrapper.config.import

import com.kebab.v2rayk.wrapper.config.bound.ProtocolType
import com.kebab.v2rayk.wrapper.config.bound.settings.ShadowsocksOutboundSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class ShadowsocksStrategyTest {
    private val strategy = ShadowsocksStrategy()

    @Test
    fun `parse valid SIP002 SS URL`() {
        val url = "ss://YWVzLTI1Ni1nY206YnlrQ29ManN5Vw@c7s1.portablesubmarines.com:39120#JMS-143136@c7s1.portablesubmarines.com:39120"
        val result = strategy.parse(url)

        val outbounds = result.outbounds
        assertNotNull(outbounds)
        assertEquals(1, outbounds.size)

        val outbound = outbounds[0]
        assertEquals(ProtocolType.SHADOWSOCKS, outbound.protocol)
        assertEquals("JMS-143136", outbound.tag)

        val settings = outbound.settings as ShadowsocksOutboundSettings
        assertEquals(1, settings.servers.size)
        assertEquals("c7s1.portablesubmarines.com", settings.servers[0].address)
        assertEquals(39120, settings.servers[0].port)
        assertEquals("aes-256-gcm", settings.servers[0].method)
        assertEquals("bykCoLjsyW", settings.servers[0].password)
    }

    @Test
    fun `parse SS URL without fragment uses host_port as tag`() {
        val url = "ss://YWVzLTI1Ni1nY206YnlrQ29ManN5Vw@1.2.3.4:8080"
        val result = strategy.parse(url)
        assertEquals("1.2.3.4:8080", result.outbounds!![0].tag)
    }

    @Test
    fun `parse SS URL without port defaults to 8388`() {
        val url = "ss://YWVzLTI1Ni1nY206YnlrQ29ManN5Vw@1.2.3.4#MyNode"
        val result = strategy.parse(url)
        val settings = result.outbounds!![0].settings as ShadowsocksOutboundSettings
        assertEquals(8388, settings.servers[0].port)
    }

    @Test
    fun `parse SS URL with simple tag`() {
        val url = "ss://YWVzLTI1Ni1nY206cGFzc3dvcmQ@1.2.3.4:8388#MySimpleTag"
        val result = strategy.parse(url)
        assertEquals("MySimpleTag", result.outbounds!![0].tag)
    }

    @Test
    fun `parse SS URL with another cipher`() {
        val url = "ss://Y2hhY2hhMjAtaWV0Zi1wb2x5MTMwNTp0ZXN0QDEyMw@1.2.3.4:8388#Test"
        val result = strategy.parse(url)
        val settings = result.outbounds!![0].settings as ShadowsocksOutboundSettings
        assertEquals("chacha20-ietf-poly1305", settings.servers[0].method)
        assertEquals("test@123", settings.servers[0].password)
    }

    @Test
    fun `invalid SS URL missing at sign throws InvalidUrlFormatException`() {
        assertFailsWith<InvalidUrlFormatException> {
            strategy.parse("ss://this-is-not-valid")
        }
    }

    @Test
    fun `invalid SS URL bad base64 throws InvalidUrlFormatException`() {
        assertFailsWith<InvalidUrlFormatException> {
            strategy.parse("ss://!!!not-base64!!!@host:123")
        }
    }

    @Test
    fun `invalid SS URL missing method_password separator throws InvalidUrlFormatException`() {
        // Base64 of "nocolon" (method without password)
        val url = "ss://bm9jb2xvbg@host:123"
        assertFailsWith<InvalidUrlFormatException> {
            strategy.parse(url)
        }
    }
}
```

- [ ] **Step 2: 运行测试**

```bash
cd modules/v2ray-core-wrapper && ./gradlew test --tests "com.kebab.v2rayk.wrapper.config.import.ShadowsocksStrategyTest"
```

- [ ] **Step 3: 提交**

```bash
cd modules/v2ray-core-wrapper
git add src/test/kotlin/com/kebab/v2rayk/wrapper/config/import/ShadowsocksStrategyTest.kt
git commit -m "test: add ShadowsocksStrategy unit tests

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 7: ImportStrategyFactory 单元测试

**Files:**
- Create: `modules/v2ray-core-wrapper/src/test/kotlin/com/kebab/v2rayk/wrapper/config/import/ImportStrategyFactoryTest.kt`

- [ ] **Step 1: 创建 ImportStrategyFactoryTest**

```kotlin
package com.kebab.v2rayk.wrapper.config.import

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ImportStrategyFactoryTest {

    @Test
    fun `factory returns VmessStrategy for vmess URL`() {
        val url = "vmess://ecdd5fb7-867f-4218-8eb6-7c5be4aa06e1-0@host:443/#Test"
        val strategy = ImportStrategyFactory.create(url)
        assertTrue(strategy is VmessStrategy)
    }

    @Test
    fun `factory returns ShadowsocksStrategy for ss URL`() {
        val url = "ss://YWVzLTI1Ni1nY206YnlrQ29ManN5Vw@host:39120#Test"
        val strategy = ImportStrategyFactory.create(url)
        assertTrue(strategy is ShadowsocksStrategy)
    }

    @Test
    fun `factory throws UnsupportedSchemeException for unknown scheme`() {
        assertFailsWith<UnsupportedSchemeException> {
            ImportStrategyFactory.create("trojan://host:443#Test")
        }
    }

    @Test
    fun `factory throws UnsupportedSchemeException for http URL`() {
        assertFailsWith<UnsupportedSchemeException> {
            ImportStrategyFactory.create("http://example.com")
        }
    }

    @Test
    fun `factory is case-insensitive for scheme`() {
        val url = "VMESS://ecdd5fb7-867f-4218-8eb6-7c5be4aa06e1-0@host:443/#Test"
        val strategy = ImportStrategyFactory.create(url)
        assertTrue(strategy is VmessStrategy)
    }
}
```

- [ ] **Step 2: 运行测试**

```bash
cd modules/v2ray-core-wrapper && ./gradlew test --tests "com.kebab.v2rayk.wrapper.config.import.ImportStrategyFactoryTest"
```

- [ ] **Step 3: 运行全部 import 测试确认通过**

```bash
cd modules/v2ray-core-wrapper && ./gradlew test --tests "com.kebab.v2rayk.wrapper.config.import.*"
```

- [ ] **Step 4: 提交**

```bash
cd modules/v2ray-core-wrapper
git add src/test/kotlin/com/kebab/v2rayk/wrapper/config/import/ImportStrategyFactoryTest.kt
git commit -m "test: add ImportStrategyFactory unit tests

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

> **检查点：v2ray-core-wrapper 层完成。确认 wrapper 构建和所有测试通过后继续。**

---

### Task 8: 修改 ProxyNode 增加 configPath

**Files:**
- Modify: `src/main/kotlin/ProxyNode.kt`

- [ ] **Step 1: 给 ProxyNode 增加 configPath 字段**

```kotlin
import com.kebab.v2rayk.wrapper.config.bound.ProtocolType

/**
 * GUI 层代理节点模型。
 * 直接导入的节点包含 configPath，mock 阶段节点 configPath 为 null。
 */
data class ProxyNode(
    val name: String,            // 节点名称，列表主标题
    val protocol: ProtocolType,  // 复用 wrapper 的协议枚举
    val usedTrafficBytes: Long,  // 已用流量（字节），UI 层格式化后展示
    val configPath: String? = null,  // 导入配置的 JSON 文件路径，null 表示非导入节点
)
```

- [ ] **Step 2: 编译验证**

```bash
./gradlew compileKotlin
```

- [ ] **Step 3: 提交**

```bash
git add src/main/kotlin/ProxyNode.kt
git commit -m "feat: add configPath field to ProxyNode for imported nodes

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 9: 创建 NodeRepository 存储接口

**Files:**
- Create: `src/main/kotlin/storage/NodeRepository.kt`

- [ ] **Step 1: 创建 NodeRepository 接口**

```kotlin
package storage

import com.kebab.v2rayk.wrapper.config.V2rayProperties

/**
 * 节点配置持久化接口。
 * 抽象存储实现，方便后续迁移到数据库等方案。
 */
interface NodeRepository {
    /**
     * 保存节点配置到持久化存储。
     * @param group 分组名称
     * @param nodeName 节点名称
     * @param config V2rayProperties 配置对象
     * @return 配置文件的绝对路径
     */
    fun save(group: String, nodeName: String, config: V2rayProperties): String

    /**
     * 加载所有节点配置。
     * @return 分组 → [(节点名, 配置)] 的映射
     */
    fun loadAll(): Map<String, List<Pair<String, V2rayProperties>>>

    /**
     * 删除指定节点的配置。
     * @param group 分组名称
     * @param nodeName 节点名称
     */
    fun delete(group: String, nodeName: String)
}
```

- [ ] **Step 2: 编译验证**（`./gradlew compileKotlin`，预期 **编译失败**——因为 JsonFileNodeRepository 尚未创建，但接口本身无依赖问题，此处仅验证接口语法）

```bash
./gradlew compileKotlin
```

- [ ] **Step 3: 提交**

```bash
git add src/main/kotlin/storage/NodeRepository.kt
git commit -m "feat: add NodeRepository interface for node persistence

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 10: 实现 JsonFileNodeRepository

**Files:**
- Create: `src/main/kotlin/storage/JsonFileNodeRepository.kt`

- [ ] **Step 1: 创建 JsonFileNodeRepository**

```kotlin
package storage

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.kebab.v2rayk.wrapper.config.V2rayProperties
import java.io.File
import java.io.IOException

/**
 * 基于 JSON 文件的节点配置存储实现。
 *
 * 文件结构：{user.home}/.v2rayk/nodes/{group}/{nodeName}.json
 * 序列化时使用 Jackson 将 V2rayProperties 写入 JSON 文件。
 */
class JsonFileNodeRepository : NodeRepository {

    private val baseDir: File by lazy {
        val home = System.getProperty("user.home")
            ?: throw IllegalStateException("无法获取 user.home 系统属性")
        File(home, ".v2rayk/nodes").also { it.mkdirs() }
    }

    private val mapper = jacksonObjectMapper()

    override fun save(group: String, nodeName: String, config: V2rayProperties): String {
        val groupDir = File(baseDir, sanitize(group))
        if (!groupDir.exists()) {
            val created = groupDir.mkdirs()
            if (!created) throw IOException("无法创建目录: ${groupDir.absolutePath}")
        }

        val configFile = File(groupDir, "${sanitize(nodeName)}.json")
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(configFile, config)
        } catch (e: Exception) {
            throw IOException("写入配置文件失败: ${configFile.absolutePath}", e)
        }
        return configFile.absolutePath
    }

    override fun loadAll(): Map<String, List<Pair<String, V2rayProperties>>> {
        if (!baseDir.exists() || !baseDir.isDirectory) return emptyMap()

        val result = mutableMapOf<String, List<Pair<String, V2rayProperties>>>()

        baseDir.listFiles()?.filter { it.isDirectory }?.forEach { groupDir ->
            val groupName = groupDir.name
            val nodes = groupDir.listFiles()
                ?.filter { it.extension == "json" }
                ?.mapNotNull { configFile ->
                    try {
                        val config: V2rayProperties = mapper.readValue(configFile)
                        val nodeName = configFile.nameWithoutExtension
                        nodeName to config
                    } catch (e: Exception) {
                        System.err.println("[WARN] 跳过损坏的配置文件: ${configFile.absolutePath} - ${e.message}")
                        null
                    }
                } ?: emptyList()
            if (nodes.isNotEmpty()) {
                result[groupName] = nodes
            }
        }

        return result
    }

    override fun delete(group: String, nodeName: String) {
        val configFile = File(baseDir, "${sanitize(group)}/${sanitize(nodeName)}.json")
        if (configFile.exists()) {
            val deleted = configFile.delete()
            if (!deleted) System.err.println("[WARN] 删除配置文件失败: ${configFile.absolutePath}")
        }
    }

    /**
     * 清理文件名中的非法字符，替换为下划线。
     */
    private fun sanitize(name: String): String {
        return name.replace(Regex("""[<>:"/\\|?*]"""), "_").trim()
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add src/main/kotlin/storage/JsonFileNodeRepository.kt
git commit -m "feat: add JsonFileNodeRepository for JSON file node persistence

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 11: JsonFileNodeRepository 单元测试

**Files:**
- Create: `src/test/kotlin/storage/JsonFileNodeRepositoryTest.kt`

- [ ] **Step 1: 创建 JsonFileNodeRepositoryTest**

```kotlin
package storage

import com.kebab.v2rayk.wrapper.config.Outbound
import com.kebab.v2rayk.wrapper.config.V2rayProperties
import com.kebab.v2rayk.wrapper.config.bound.ProtocolType
import com.kebab.v2rayk.wrapper.config.bound.settings.ShadowsocksOutboundSettings
import com.kebab.v2rayk.wrapper.config.bound.settings.ShadowsocksServer
import kotlin.test.*
import java.io.File

class JsonFileNodeRepositoryTest {
    private lateinit var repo: JsonFileNodeRepository
    private lateinit var testBaseDir: File

    @BeforeTest
    fun setUp() {
        // 覆盖默认的 user.home 路径到临时目录
        val tmpDir = System.getProperty("java.io.tmpdir")
        testBaseDir = File(tmpDir, ".v2rayk-nodes-test-${System.currentTimeMillis()}")
        testBaseDir.mkdirs()
        // 使用反射或直接调用 setBaseDir？由于 baseDir 是 lazy，使用 System property hack
        System.setProperty("user.home", tmpDir)
        repo = JsonFileNodeRepository()
    }

    @AfterTest
    fun tearDown() {
        testBaseDir.deleteRecursively()
    }

    // 注意：由于 JsonFileNodeRepository 硬编码从 user.home 读取路径，
    // 实际测试中 user.home 会被临时修改（在 @BeforeTest 中改为 tmpDir），
    // 测试结束后恢复。此测试依赖真实的文件系统操作。

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
        assertTrue(file.readText().contains("shadowsocks"))
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
                    settings = com.kebab.v2rayk.wrapper.config.bound.settings.VmessOutboundSettings(
                        vnext = listOf(
                            com.kebab.v2rayk.wrapper.config.bound.settings.VmessServer(
                                address = "new.example.com",
                                port = 443,
                                users = listOf(
                                    com.kebab.v2rayk.wrapper.config.bound.settings.VmessUser(
                                        id = "new-uuid"
                                    )
                                ),
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
        // Delete the test dir to simulate empty state
        if (testBaseDir.exists()) testBaseDir.deleteRecursively()
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
```

> **注意**：由于 `JsonFileNodeRepository` 使用 `System.getProperty("user.home")` 获取基础目录，测试会在 `@BeforeTest` 中临时修改该属性指向 `java.io.tmpdir` 下的临时目录。`@AfterTest` 中清理。

- [ ] **Step 2: 运行测试**

```bash
./gradlew test --tests "storage.JsonFileNodeRepositoryTest"
```

- [ ] **Step 3: 提交**

```bash
git add src/test/kotlin/storage/JsonFileNodeRepositoryTest.kt
git commit -m "test: add JsonFileNodeRepository unit tests

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 12: 创建 ImportDialog Composable

**Files:**
- Create: `src/main/kotlin/ui/ImportDialog.kt`

- [ ] **Step 1: 创建 ImportDialog**

```kotlin
package ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.kebab.v2rayk.wrapper.config.import.ImportException
import com.kebab.v2rayk.wrapper.config.import.ImportStrategyFactory
import storage.NodeRepository

/**
 * 导入配置对话框。
 *
 * @param groups 当前已有分组名称列表
 * @param repository 节点存储仓库
 * @param onImportComplete 导入完成回调，传入 (groupName, importedNodes) 列表
 * @param onDismiss 关闭对话框回调
 */
@Composable
fun ImportDialog(
    groups: List<String>,
    repository: NodeRepository,
    onImportComplete: (String, List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedGroup by remember { mutableStateOf(groups.firstOrNull() ?: "") }
    var groupDropdownExpanded by remember { mutableStateOf(false) }
    var isCreatingNewGroup by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var urlText by remember { mutableStateOf("") }
    var importErrors by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var isImporting by remember { mutableStateOf(false) }

    val effectiveGroup: String
        get() = if (isCreatingNewGroup) newGroupName.trim() else selectedGroup

    val canImport: Boolean
        get() = urlText.isNotBlank() && effectiveGroup.isNotBlank() && !isImporting

    Dialog(onDismissRequest = { if (!isImporting) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colors.surface,
            elevation = 8.dp,
            modifier = Modifier.width(520.dp)
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("导入配置", style = MaterialTheme.typography.h6)
                Spacer(Modifier.height(16.dp))

                // 目标分组选择
                Text("目标分组", style = MaterialTheme.typography.subtitle2)
                Spacer(Modifier.height(4.dp))
                Box {
                    OutlinedButton(
                        onClick = { groupDropdownExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (isCreatingNewGroup) "≡ 新建分组: $newGroupName"
                            else selectedGroup.ifBlank { "请选择分组" }
                        )
                        Spacer(Modifier.weight(1f))
                        Text(" ▼")
                    }
                    DropdownMenu(
                        expanded = groupDropdownExpanded,
                        onDismissRequest = { groupDropdownExpanded = false }
                    ) {
                        groups.forEach { group ->
                            DropdownMenuItem(onClick = {
                                selectedGroup = group
                                isCreatingNewGroup = false
                                groupDropdownExpanded = false
                            }) {
                                Text(group)
                            }
                        }
                        DropdownMenuItem(onClick = {
                            isCreatingNewGroup = true
                            newGroupName = ""
                            groupDropdownExpanded = false
                        }) {
                            Text("≡ 新建分组", color = MaterialTheme.colors.primary)
                        }
                    }
                }

                // 新建分组名输入框
                if (isCreatingNewGroup) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        label = { Text("新分组名称") },
                        isError = newGroupName.isBlank() && urlText.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(16.dp))

                // 多行链接输入框
                Text("配置链接", style = MaterialTheme.typography.subtitle2)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it; importErrors = emptyMap() },
                    placeholder = { Text("每行一个分享链接，如：\nvmess://...\nss://...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 200.dp),
                    maxLines = 10,
                )

                // 错误信息
                if (importErrors.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    importErrors.forEach { (line, error) ->
                        Text(
                            "第 ${line + 1} 行: $error",
                            color = MaterialTheme.colors.error,
                            style = MaterialTheme.typography.caption,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 底部按钮
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = onDismiss,
                        enabled = !isImporting,
                    ) {
                        Text("取消")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            isImporting = true
                            val errors = mutableMapOf<Int, String>()
                            val importedNodes = mutableListOf<String>()

                            val lines = urlText.lines().map { it.trim() }.filter { it.isNotBlank() }
                            val targetGroup = effectiveGroup

                            lines.forEachIndexed { index, line ->
                                try {
                                    val strategy = ImportStrategyFactory.create(line)
                                    val config = strategy.parse(line)
                                    val nodeName = config.outbounds?.firstOrNull()?.tag ?: "imported-${index + 1}"
                                    repository.save(targetGroup, nodeName, config)
                                    importedNodes.add(nodeName)
                                } catch (e: ImportException) {
                                    errors[index] = e.message ?: "未知解析错误"
                                } catch (e: Exception) {
                                    errors[index] = "保存失败: ${e.message}"
                                }
                            }

                            if (importedNodes.isNotEmpty()) {
                                onImportComplete(targetGroup, importedNodes)
                            }
                            if (errors.isNotEmpty()) {
                                importErrors = errors
                            } else if (importedNodes.isNotEmpty()) {
                                onDismiss()
                            }
                            isImporting = false
                        },
                        enabled = canImport,
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text("导入")
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add src/main/kotlin/ui/ImportDialog.kt
git commit -m "feat: add ImportDialog Composable for URL import UI

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 13: 集成导入按钮到 SingleColumnListWithMenu

**Files:**
- Modify: `src/main/kotlin/SingleColumnListWithMenu.kt`

- [ ] **Step 1: 修改 SingleColumnListWithMenu，添加 onImportClick 回调和"导入"按钮**

在 TopAppBar 的 `actions` 中，在"选项"按钮前增加"导入"按钮。同时增加 `onImportClick` 回调参数。

**变更点 1 — 函数签名**（约第 17-26 行）：在 `onGroupSettings` 后新增 `onImportClick` 参数：

```kotlin
@Composable
fun SingleColumnListWithMenu(
    groupItems: Map<String, List<ProxyNode>>,
    selectedNode: ProxyNode?,
    connectedNode: ProxyNode?,
    onSelectNode: (ProxyNode) -> Unit,
    onConnectNode: (ProxyNode) -> Unit,
    onAddGroup: (String) -> Unit,
    onGroupSettings: (String) -> Unit,
    onImportClick: () -> Unit,  // 新增：导入按钮回调
) {
```

**变更点 2 — TopAppBar actions**（约第 46-67 行，将整个 `actions` 块替换为）：

```kotlin
                    actions = {
                        // 导入按钮
                        IconButton(onClick = onImportClick) {
                            Text("导入")
                        }
                        // 选项菜单
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Text("选项")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(onClick = {
                                    menuExpanded = false
                                    showAddGroupDialog = true
                                }) {
                                    Text("添加分组")
                                }
                                DropdownMenuItem(onClick = {
                                    menuExpanded = false
                                }) {
                                    Text("关于")
                                }
                            }
                        }
                    }
```

- [ ] **Step 2: 编译验证**

```bash
./gradlew compileKotlin
```

Expected: BUILD FAILED — `Main.kt` 中调用 `SingleColumnListWithMenu(...)` 缺少新增参数 `onImportClick`

- [ ] **Step 3: 提交**

```bash
git add src/main/kotlin/SingleColumnListWithMenu.kt
git commit -m "feat: add import button and onImportClick callback to TopAppBar

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 14: 集成 repository 和 import dialog 到 App()

**Files:**
- Modify: `src/main/kotlin/Main.kt`

- [ ] **Step 1: 修改 App() 集成导入功能**

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.kebab.v2rayk.wrapper.config.bound.ProtocolType
import com.kebab.v2rayk.wrapper.config.import.ImportStrategyFactory
import storage.JsonFileNodeRepository
import ui.ImportDialog

@Composable
fun App() {
    val repository = remember { JsonFileNodeRepository() }

    var groups by remember {
        // 启动时从存储加载已导入的节点
        val persisted = repository.loadAll()
        val initial = if (persisted.isNotEmpty()) {
            val map = persisted.mapValues { (_, nodes) ->
                nodes.map { (name, config) ->
                    ProxyNode(
                        name = name,
                        protocol = config.outbounds?.firstOrNull()?.protocol ?: ProtocolType.VMESS,
                        usedTrafficBytes = 0L,
                        configPath = System.getProperty("user.home") + "/.v2rayk/nodes/" +
                            name.map { if (it in "<>:\"/\\|?*") '_' else it }.joinToString("") +
                            "/" + name.map { if (it in "<>:\"/\\|?*") '_' else it }.joinToString("") + ".json",
                    )
                }
            }.toMutableMap()
            // Merge with default mock groups for now
            map.apply {
                putIfAbsent("香港节点", listOf(
                    ProxyNode("HK-01 IEPL 专线", ProtocolType.VMESS, 1536L),
                    ProxyNode("HK-02 标准", ProtocolType.SHADOWSOCKS, 1048576L),
                ))
                putIfAbsent("日本节点", listOf(
                    ProxyNode("JP-01 东京", ProtocolType.VMESS, 1073741824L),
                    ProxyNode("JP-02 大阪", ProtocolType.SOCKS, 512L),
                ))
            }
        } else {
            mutableMapOf(
                "香港节点" to listOf(
                    ProxyNode("HK-01 IEPL 专线", ProtocolType.VMESS, 1536L),
                    ProxyNode("HK-02 标准", ProtocolType.SHADOWSOCKS, 1048576L),
                ),
                "日本节点" to listOf(
                    ProxyNode("JP-01 东京", ProtocolType.VMESS, 1073741824L),
                    ProxyNode("JP-02 大阪", ProtocolType.SOCKS, 512L),
                ),
            )
        }
        mutableStateOf(map)
    }
    var selectedNode by remember { mutableStateOf<ProxyNode?>(null) }
    var connectedNode by remember { mutableStateOf<ProxyNode?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }

    val groupNames by remember { derivedStateOf { groups.keys.toList() } }

    SingleColumnListWithMenu(
        groupItems = groups,
        selectedNode = selectedNode,
        connectedNode = connectedNode,
        onSelectNode = { node -> selectedNode = node },
        onConnectNode = { node -> connectedNode = node },
        onAddGroup = { groupName ->
            if (groupName.isNotBlank()) {
                groups = groups + (groupName to emptyList())
            }
        },
        onGroupSettings = { groupName ->
            // 后续迭代接入 wrapper
        },
        onImportClick = {
            showImportDialog = true
        },
    )

    if (showImportDialog) {
        ImportDialog(
            groups = groupNames,
            repository = repository,
            onImportComplete = { groupName, nodeNames ->
                val existingNodes = groups[groupName] ?: emptyList()
                val newNodes = nodeNames.map { name ->
                    ProxyNode(
                        name = name,
                        protocol = ProtocolType.SHADOWSOCKS, // 后续可从 config 解析，暂时默认
                        usedTrafficBytes = 0L,
                        configPath = "${System.getProperty("user.home")}/.v2rayk/nodes/$groupName/$name.json",
                    )
                }
                groups = groups + (groupName to (existingNodes + newNodes))
            },
            onDismiss = { showImportDialog = false },
        )
    }
}

fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        App()
    }
}
```

> 注意：上述 `configPath` 构造逻辑较粗略。下一轮迭代应直接从 `repository.save()` 返回值获取路径（当前 ImportDialog 内部调用 save 但未返回路径信息——可优化为 ImportDialog 回调传入路径列表）。

- [ ] **Step 2: 编译验证**

```bash
./gradlew compileKotlin
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add src/main/kotlin/Main.kt
git commit -m "feat: integrate import dialog with NodeRepository into App()

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 15: 端到端验证 + 更新子模块引用

**Files:**
- Modify: `modules/v2ray-core-wrapper` (git submodule reference)

- [ ] **Step 1: 运行 v2ray-core-wrapper 全部测试**

```bash
cd modules/v2ray-core-wrapper && ./gradlew test
```

Expected: ALL TESTS PASS

- [ ] **Step 2: 运行 v2rayk 全部测试**

```bash
./gradlew test
```

Expected: ALL TESTS PASS

- [ ] **Step 3: 运行完整构建**

```bash
./gradlew build
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 更新 v2rayk 中 submodule 引用并提交**

```bash
cd modules/v2ray-core-wrapper
git log --oneline -5  # 确认 wrapper 的提交记录
cd ../..
git add modules/v2ray-core-wrapper
git commit -m "chore: update v2ray-core-wrapper submodule for import feature

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## 实施顺序小结

| 步骤 | 层 | 任务 | 可并行 |
|------|-----|------|--------|
| 1 | wrapper | ImportException | - |
| 2 | wrapper | ImportStrategy + Factory | - |
| 3 | wrapper | VmessStrategy | - |
| 4 | wrapper | VmessStrategyTest | - |
| 5 | wrapper | ShadowsocksStrategy | - |
| 6 | wrapper | ShadowsocksStrategyTest | 与 4 并行 |
| 7 | wrapper | FactoryTest | 与 4/6 可并行 |
| 8 | v2rayk | ProxyNode 修改 | - |
| 9 | v2rayk | NodeRepository 接口 | 与 8 可并行 |
| 10 | v2rayk | JsonFileNodeRepository | 与 8/9 可并行 |
| 11 | v2rayk | RepositoryTest | - |
| 12 | v2rayk | ImportDialog | - |
| 13 | v2rayk | TopAppBar 按钮 | - |
| 14 | v2rayk | App() 集成 | - |
| 15 | both | E2E 验证 | - |

**建议**：先完成 wrapper 层（Task 1-7），全部测试通过后，再启动 v2rayk 层（Task 8-14），最后端到端验证。
