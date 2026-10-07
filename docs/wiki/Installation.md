# 安装与兼容版本

[← Wiki 首页](Home.md) · [新手实操](../tutorial/Getting-Started.md) · [快速开始](Quick-Start.md)

## 下载发行版

当前分支对应 tag `v0.1.0-mc1.21.1`，从 [对应 GitHub Release](https://github.com/WhiseNT/playwrightforkubejs/releases/tag/v0.1.0-mc1.21.1) 下载 `playwrightforkubejs-0.1.0+mc1.21.1.jar`；不要下载源码 ZIP 后改扩展名。

把 mod JAR 放到目标 Minecraft 实例的 `mods/` 目录。该实例还需要 NeoForge、KubeJS for NeoForge 及其依赖；本 mod JAR 不会打包或替代 Minecraft/NeoForge/KubeJS/Rhino。Architectury 不是当前分支明确依赖。

历史 Forge 1.20.1 发行版仍可从 [v0.1.0 下载](https://github.com/WhiseNT/playwrightforkubejs/releases/download/v0.1.0/playwrightforkubejs-0.1.0.jar)，资产为 `playwrightforkubejs-0.1.0.jar`，不要与当前 NeoForge 1.21.1 资产混用。

| 组件 | 当前发行配置 |
| --- | --- |
| Java | 21 |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.256 |
| KubeJS for NeoForge | 2101.7.2-build.379 |
| Rhino | 按该 KubeJS 构建的依赖要求安装 |

KubeJS 的安装依赖按对应整合包/下载页要求安装。所有依赖应放在同一个 Minecraft 实例中。NeoForge 报缺失 mod 时先处理依赖和版本，不要尝试用其他 loader 的 JAR 替代。

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

需要 Java 21 和网络可用的 Gradle/Maven 依赖下载。仓库根目录执行：

```bash
./gradlew clean build
```

Windows：

```powershell
.\gradlew.bat clean build
```

JAR 输出为 `build/libs/playwrightforkubejs-0.1.0+mc1.21.1.jar`。构建验证不自动把 JAR 放进某个玩家实例；安装时再手动复制到该实例的 `mods/`。

## 支持边界

已验证版本以对应 release tag 内容为准。当前实测范围是 Minecraft 1.21.1 / NeoForge 21.1.256 单人客户端；历史 `v0.1.0` 对应 Forge 1.20.1。Fabric、未验收的 Minecraft/KubeJS 组合、专用服务器、多人网络和任意模组屏幕均不在兼容保证内。更新 Minecraft 或 loader 时，应针对自己的模组组合运行真实 GUI 测试。

公共 Playwright API 尽量跨版本保持同名方法、参数、返回值和错误语义，由底层适配版本差异；原生命令、NBT、Java 访问及 KubeJS 版本特性可不同。测试夹具集中适配是方向，目前尚未实现跨版本统一夹具，不意味着所有脚本已全部兼容。
