# 安装与兼容版本

[← Wiki 首页](Home.md) · [新手实操](../tutorial/Getting-Started.md) · [快速开始](Quick-Start.md)

## 下载发行版

从 [GitHub Releases](https://github.com/WhiseNT/playwrightforkubejs/releases) 下载 `playwrightforkubejs-0.1.0.jar`。不要下载源码 ZIP 后改扩展名。

把 mod JAR 放到目标 Minecraft 实例的 `mods/` 目录。该实例还需要 Forge、KubeJS for Forge 及其依赖；本 mod JAR 不会打包或替代 Minecraft/Forge/KubeJS/Rhino/Architectury。

| 组件 | 当前发行配置 |
| --- | --- |
| Java | 17 |
| Minecraft | 1.20.1 |
| Forge | 47.2.0，目标 Forge 47.x |
| KubeJS for Forge | 2001.6.5-build.26 |
| Rhino for Forge | 2001.2.2-build.17 |
| Architectury for Forge | 9.2.14 |

KubeJS 的安装依赖按对应整合包/下载页要求安装。所有依赖应放在同一个 Minecraft 实例中。Forge 报缺失 mod 时先处理依赖和版本，不要尝试用其他 loader 的 JAR 替代。

### 可选：用 NTLauncher CLI 启动已安装实例

如果 Agent 没有 Gradle 开发环境，但你已有预配置的 Windows NTLauncher 实例，可使用 [NTLauncher CLI](https://ntlaunch.cn/) 启动它。先在该实例安装上表兼容环境、本模组和客户端测试脚本，并配置 Java 与账号；CLI 不替你安装依赖或部署模组。启动命令与 JSON 输出见 [NTLauncher 使用文档](https://ntlaunch.cn/docs)。启动器只负责启动 Minecraft，测试是否通过仍要检查后续 KubeJS/客户端日志或脚本结果。

## 安装自己的测试脚本

脚本放在实例下：

```text
kubejs/client_scripts/my_test.js
```

普通使用只需 `Playwright` 公共绑定。`PlaywrightTest` 是仅供仓库 E2E 使用的测试 fixture API，未启用测试运行属性时不会注册，不是一般脚本 API。

初次验证可复制 [最小 smoke 脚本](../../examples/kubejs/client_scripts/playwright_smoke.js)，进世界后检查 `logs/kubejs/client.log`。逐步安装/运行教程在[这里](../tutorial/Getting-Started.md)。

## 从源码构建

需要 Java 17 和网络可用的 Gradle/Maven 依赖下载。仓库根目录执行：

```bash
./gradlew clean build
```

Windows：

```powershell
.\gradlew.bat clean build
```

JAR 输出为 `build/libs/playwrightforkubejs-0.1.0.jar`。构建验证不自动把 JAR 放进某个玩家实例；安装时再手动复制到该实例的 `mods/`。

## 支持边界

已验证版本以当前 release tag 内容为准。当前实测范围是 Forge 1.20.1 单人客户端。Fabric/NeoForge、其他 Minecraft/KubeJS 版本、专用服务器、多人网络和任意模组屏幕均不在兼容保证内。更新 Minecraft 或 loader 时，应针对自己的模组组合运行真实 GUI 测试。
