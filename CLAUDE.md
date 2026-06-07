# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

V2RayK 是 V2Ray 的桌面 GUI 客户端，基于 **JetBrains Compose Desktop** 构建。它通过调用 `v2ray-core-wrapper` 库来管理 V2Ray 进程和配置。

## 常用命令

```bash
# 运行桌面应用（开发模式）
./gradlew run

# 构建所有模块（含子模块 v2ray-core-wrapper）
./gradlew build

# 仅编译 Kotlin 源码
./gradlew compileKotlin

# 打包原生安装包（输出到 build/compose/binaries/）
./gradlew package
# 生成 DMG (macOS)、MSI (Windows)、Deb (Linux)

# 清理构建产物
./gradlew clean

# 单独构建子模块
./gradlew :modules:v2ray-core-wrapper:build
```

## 项目结构

```
v2rayk/
├── settings.gradle.kts          # 项目设置：声明子模块和插件版本
├── build.gradle.kts             # 构建配置：Compose Desktop、原生打包
├── gradle.properties            # Kotlin 2.1.0 + Compose 1.7.3 版本声明
├── .gitmodules                  # git submodule: modules/v2ray-core-wrapper
├── src/
│   └── main/kotlin/
│       ├── Main.kt              # 应用入口，创建 Window + 调用 App()
│       └── SingleColumnListWithMenu.kt  # 主界面 Composable 组件
└── modules/
    └── v2ray-core-wrapper/      # git submodule（包装 V2Ray CLI 的 Kotlin 库）
```

## 架构

### 应用入口

`Main.kt` - 使用 Compose Desktop 的 `application { Window { ... } }` 创建窗口：

```kotlin
fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        App()
    }
}
```

`App()` 是一个 `@Composable` 函数，当前维护分组数据状态（`mutableStateOf`），并将其传递给 `SingleColumnListWithMenu`。

### UI 组件

`SingleColumnListWithMenu.kt` 是目前的唯一 Composable，实现了：

- **Scaffold 布局**：TopAppBar + 内容区
- **顶栏菜单**：DropdownMenu 含"添加分组"和"关于"选项
- **分组列表**：`LazyColumn` 渲染，支持折叠/展开（通过 `groupFoldStates` map 控制）
- **条目交互**：双击条目弹出 Dialog 显示详情
- **分组设置**：每个分组右侧有齿轮按钮，点击弹出设置 Dialog
- **滚动条**：`VerticalScrollbar` 配合 `rememberScrollbarAdapter`

状态管理使用 Compose 内置的 `remember { mutableStateOf(...) }` 和 `mutableStateMapOf`。

### 依赖关系

`v2rayk` 通过 `implementation(project(":modules:v2ray-core-wrapper"))` 引用包装库。当前 GUI 尚未集成 v2ray-core-wrapper 的实际调用——`Main.kt` 中有 import `V2RayCliServer` 但未使用，界面显示的是硬编码的示例分组数据。

### 技术栈

| 依赖 | 版本 | 用途 |
|------|------|------|
| Kotlin | 2.1.0 | 语言 |
| Gradle | 8.7 | 构建系统 |
| Compose Desktop | 1.7.3 | GUI 框架 |
| Compose Material | - | Material 组件（Scaffold、TopAppBar、Dialog 等） |
| JVM Target | 21 | 编译目标 |

### 原生打包

通过 `compose.desktop.application.nativeDistributions` 配置，支持打包为：
- **macOS**: DMG
- **Windows**: MSI
- **Linux**: Deb

包名为 `v2rayk`，版本 `1.0.0`。

## 开发注意事项

- 应用主类名为 `MainKt`（Kotlin 文件级函数 `main()` 的默认类名），在 `build.gradle.kts` 和 `.run/desktop.run.xml` 中均有配置
- IDEA 运行配置位于 `.run/desktop.run.xml`，执行 `gradle run` 任务
- 修改 `modules/v2ray-core-wrapper` 后，需在 git submodule 内单独提交，然后在 v2rayk 中更新 submodule 引用
- UI 代码采用 Compose 惯用模式：状态提升（`mutableStateOf` 在父级，事件回调通过 lambda 传递）
