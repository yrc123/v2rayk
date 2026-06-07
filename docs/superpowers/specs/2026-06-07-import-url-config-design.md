# URL 导入配置功能 — 设计文档

**日期**：2026-06-07
**状态**：待评审

---

## 1. 功能概述

在导航栏新增"导入"按钮，用户可通过粘贴 V2Ray 分享链接（`vmess://` / `ss://`）将远程节点配置批量导入本地，解析为 V2rayProperties 后持久化为 JSON 文件，并自动加入对应分组。

## 2. 架构

```
┌─────────────────────────────────────────────────────┐
│ v2ray-core-wrapper (解析层)                          │
│                                                      │
│ config/import/                                       │
│ ├── ImportStrategy.kt   策略接口                     │
│ ├── VmessStrategy.kt    vmess:// → V2rayProperties   │
│ ├── ShadowsocksStrategy.kt  ss:// → V2rayProperties  │
│ └── ImportStrategyFactory.kt  按 scheme 路由策略     │
└─────────────────────────────────────────────────────┘
                         ↓ 返回 V2rayProperties
┌─────────────────────────────────────────────────────┐
│ v2rayk (应用层)                                      │
│                                                      │
│ storage/                                             │
│ ├── NodeRepository.kt   接口                        │
│ └── JsonFileNodeRepository.kt  文件系统实现          │
│     存储路径: {user.home}/.v2rayk/nodes/{group}/    │
│               {nodeName}.json                        │
│                                                      │
│ ui/                                                  │
│ └── ImportDialog.kt  导入对话框 Composable           │
│                                                      │
│ ProxyNode.kt  增加 configPath: String?                │
│ SingleColumnListWithMenu.kt  TopAppBar 增加"导入"按钮 │
└─────────────────────────────────────────────────────┘
```

**设计原则**：解析逻辑归入 wrapper 库（靠近配置类型，可复用，可被 wrapper 自身测试覆盖）；v2rayk 只负责 UI + 存储。

## 3. 数据流

```
URL 字符串
  → ImportStrategyFactory.create(url) 按 scheme 获取策略
    → ImportStrategy.parse(url) 解析 → V2rayProperties
      → NodeRepository.save(group, nodeName, properties) 写入 JSON 文件
        → ProxyNode(name, protocol, usedTrafficBytes=0, configPath=path) 加入 UI 状态
```

## 4. 组件详设

### 4.1 ImportStrategy 接口

```kotlin
package com.kebab.v2rayk.wrapper.config.import

interface ImportStrategy {
    val supportedSchemes: List<String>  // e.g. ["vmess"]
    fun parse(url: String): V2rayProperties
}
```

- 输入：单个 URL 字符串（已 `trim()`）
- 输出：V2rayProperties（outbounds 列表含一个 Outbound，tag = "PROXY"）
- 异常：`ImportException` 及其子类（`UnsupportedSchemeException`、`InvalidUrlFormatException`）

### 4.2 ImportStrategyFactory

```kotlin
object ImportStrategyFactory {
    fun create(url: String): ImportStrategy  // 根据 scheme 查找，找不到则抛 UnsupportedSchemeException
}
```

内部维护 `Map<String, ImportStrategy>`，按 scheme 路由。

### 4.3 VmessStrategy

- scheme: `["vmess"]`
- 解析流程：截取 `vmess://` 之后的部分 → Base64 解码 → Jackson 反序列化为 `VmessUrlPayload`（仅含核心字段：v/ps/add/port/id/net/type/tls/sni/host/path 等）→ 映射为 `V2rayProperties`
- 参考：`docs/reference/import-url-example/vmess-example.txt`

### 4.4 ShadowsocksStrategy

- scheme: `["ss"]`
- 解析流程：截取 `ss://` → 判断是否含 `@`（SIP002 格式）→ Base64 解码 method:password 部分 → 提取 address、port、tag（`#` 之后）
- 参考：`docs/reference/import-url-example/ss-example.txt`

### 4.5 ProxyNode 变更

```kotlin
data class ProxyNode(
    val name: String,
    val protocol: ProtocolType,
    val usedTrafficBytes: Long,
    val configPath: String? = null,  // null=非导入节点，非null=已导入配置路径
)
```

### 4.6 NodeRepository 接口

```kotlin
interface NodeRepository {
    fun save(group: String, nodeName: String, config: V2rayProperties): String  // 返回存储路径
    fun loadAll(): Map<String, List<Pair<String, V2rayProperties>>>  // group → [(name, config)]
    fun delete(group: String, nodeName: String)
}
```

### 4.7 JsonFileNodeRepository

- 存储根目录：`{user.home}/.v2rayk/nodes/`
- 文件结构：`{group}/{nodeName}.json`（group 名中的非法文件名字符需 sanitize）
- 序列化：Jackson `ObjectMapper` 写入 `V2rayProperties`
- 写入策略：目录自动创建，同名覆盖
- 读取策略：递归扫描 group 目录，跳过损坏的 JSON（warning 日志）

## 5. UI 设计

### 5.1 入口

TopAppBar 右侧新增"导入"按钮，与现有"选项"按钮并列。

### 5.2 ImportDialog 布局

```
┌──────────────────────────────────────┐
│  导入配置                        [X] │
│                                      │
│  目标分组                            │
│  ┌──────────────────────────────┐    │
│  │ ▼ 香港节点                   │    │
│  └──────────────────────────────┘    │
│  （下拉框：已有分组 + "新建分组"）    │
│                                      │
│  配置链接                            │
│  ┌──────────────────────────────┐    │
│  │ vmess://...                  │    │
│  │                              │    │
│  │                              │    │
│  └──────────────────────────────┘    │
│  （多行输入框，每行一个链接）          │
│                                      │
│         [取消]       [导入]          │
└──────────────────────────────────────┘
```

### 5.3 交互流程

1. 点击导航栏"导入"按钮 → 弹出对话框
2. 下拉框选中目标分组（已有分组列表 + "≡ 新建分组"）
3. 选择"新建分组"时，下方出现分组名输入框
4. 多行文本框粘贴链接，每行一个，`trim()` 后过滤空行
5. "导入"按钮仅在分组已选定且输入框非空时可点击
6. 点击"导入"：
   - 逐行调用 `ImportStrategyFactory.create(url).parse(url)`
   - 调用 `repository.save(group, nodeName, config)`
   - 成功的节点加入 groups 状态
   - 失败的行显示行内红色错误提示
7. 关闭对话框，列表刷新

## 6. 错误处理

### 解析层异常体系

```
ImportException (RuntimeException)
├── UnsupportedSchemeException    scheme 无对应策略
└── InvalidUrlFormatException      Base64/JSON/字段校验失败
```

| 情况 | 处理 |
|------|------|
| 未知 scheme | `UnsupportedSchemeException` |
| Base64 解码失败 | `InvalidUrlFormatException` |
| JSON 结构非法 | `InvalidUrlFormatException`，携带原始异常 |
| 必填字段缺失 | `InvalidUrlFormatException("缺少必填字段: xxx")` |
| 端口非法 | `InvalidUrlFormatException("非法端口: xxx")` |

### 存储层

| 情况 | 处理 |
|------|------|
| 目录不存在 | 自动 `mkdirs()` |
| 同名节点已存在 | 覆盖 |
| 写入失败（权限/磁盘满） | IOException，UI 提示 |
| 读取时文件损坏 | 跳过，warning 日志 |

### UI 层

- 解析失败：该行下方显示红色错误文本，按钮保持可用
- 批量导入：成功的写入，失败的显示行内错误
- 空输入/未选分组：按钮置灰
- 新建分组名为空：输入框红色边框警告

## 7. 测试策略

| 测试类 | 内容 |
|--------|------|
| `VmessStrategyTest` | 正常 URL、畸形 URL、缺字段、边界端口值 |
| `ShadowsocksStrategyTest` | SIP002 格式、Base64 错误、缺 tag |
| `ImportStrategyFactoryTest` | scheme 路由、未知 scheme |
| `JsonFileNodeRepositoryTest` | save/load/delete/覆盖/空目录 |

## 8. 范围边界

**v1 范围**：VMess + Shadowsocks 两种协议，JSON 文件存储。

**明确排除**（后续迭代）：
- VLESS、Trojan、Hysteria2 等其他协议
- 数据库存储（SQLite 等）
- 导入历史记录
- 批量 URL 的去重检测
- 导入后测试连接功能
