# 真实客户端 E2E 测试

简体中文 | [English](https://github.com/WhiseNT/playwrightforkubejs/wiki/E2E-Testing.en)

[← Wiki 首页](https://github.com/WhiseNT/playwrightforkubejs/wiki/Home) · [容器示例](https://github.com/WhiseNT/playwrightforkubejs/wiki/Inventory-and-Containers) · [异步错误](https://github.com/WhiseNT/playwrightforkubejs/wiki/Async-Tasks-and-Errors) · [AI 编码指南](https://github.com/WhiseNT/playwrightforkubejs/wiki/AI-Coding-Guide)

## 两类测试各自证明什么

1. `./gradlew test`：Java/JUnit 的无窗口契约测试，不启动完整 Minecraft client。
2. `python -m unittest discover -s examples -p 'test_client_e2e.py'`：Python 证据校验器单测，通过伪造、篡改、缺失和乱序事件确认 validator 会拒绝坏证据。它不模拟 Minecraft。
3. `examples/run_client_e2e.py --suite`：启动两个新的图形客户端，分别跑 `en_us`/GUI scale 2 和 `zh_cn`/scale 3，真实执行 UI、输入、游戏状态、容器和存档流程。

不要用 Python validator 单测代替游戏端测试，也不要把 GUI 还没点击、脚本刚加载当作客户端用例成功。

## 环境要求

- Java 21。
- 当前 `mc-1.21.1` 分支对应 Minecraft 1.21.1、NeoForge 21.1.256、KubeJS `2101.7.2-build.379` 与该 KubeJS 要求的 Rhino 依赖。
- Python 3 和 `psutil`（`python -m pip install psutil`）。
- 可用图形桌面和能运行 Minecraft client 的显卡/驱动。
- Gradle 8.8。runner 默认查找 Gradle 8.8 wrapper 缓存；找不到时传 `--gradle`。
- 首次下载依赖时允许联网；有完整缓存时可用默认 offline 模式。

## 运行整套验收

```bash
python examples/run_client_e2e.py \
  --suite \
  --java-home "/path/to/jdk-21" \
  --gradle "/path/to/gradle-8.8/bin/gradle" \
  --timeout 600
```

Windows PowerShell 示例（请换成本机真实路径）：

```powershell
$env:JAVA_HOME = "C:\Path\To\jdk-21"
python examples/run_client_e2e.py `
  --suite `
  --java-home $env:JAVA_HOME `
  --gradle "C:\Path\To\gradle-8.8\bin\gradle.bat" `
  --timeout 600
```

`--online` 允许 Gradle 下载缺失依赖。单次配置可以提供 `--run-id`, `--language en_us|zh_cn` 和 `--gui-scale 1..4`。E2E 每次都要求新目录，不覆盖已有 run ID；suite 的基础 run-id 最长 22 个字符。

## 非 Gradle 环境：使用 NTLauncher CLI 启动已有实例（可选）

当 Agent 所在环境没有 Gradle 开发/调试环境，但已有配置完成的 NTLauncher Minecraft 实例时，可使用 [NTLauncher CLI](https://ntlaunch.cn/) 启动该实例。NTLauncher CLI 负责启动与进程管理；本模组仅在 Minecraft 客户端加载后提供游戏内自动化 API。此路径不构建模组、不安装 NeoForge/KubeJS/依赖、不复制测试脚本，也不会自动判定模组测试通过。

前置条件：NTLauncher CLI 当前提供 Windows 可执行文件；目标实例已安装完成（`install_state == "Installed"`），配置了可用 Java 和账号，并已安装兼容的 NeoForge、KubeJS 依赖、本模组 JAR 及 `kubejs/client_scripts/` 测试脚本。

基本命令：

```powershell
$instances = & .\ntlauncher-cli.exe list-instances --json | ConvertFrom-Json
$instanceId = 1 # 替换为 list-instances 输出中的目标实例 ID
$instance = $instances.instances | Where-Object { $_.id -eq $instanceId } | Select-Object -First 1
if (-not $instance -or $instance.install_state -ne "Installed") {
  throw "目标实例不存在或尚未安装完成"
}
& .\ntlauncher-cli.exe launch --instance-id $instanceId --json
if ($LASTEXITCODE -ne 0) { throw "NTLauncher/Minecraft 启动流程失败" }
```

`launch` 在前台阻塞，直到 Minecraft 退出；因此 Agent 若要和运行中的客户端交互，需按 [CLI 官方文档](https://ntlaunch.cn/docs) 使用独立进程启动，并按需调用 `status`/`stop`。为每个 CLI 进程设置合理超时，启动失败时检查 CLI stderr 中的错误与最近 Minecraft 日志。CLI 返回成功只表示启动流程完成（通常在游戏退出后），不是 Playwright 测试 PASS；测试结论必须检查 `logs/kubejs/client.log`、Minecraft 日志或脚本产生的明确结构化结果。该方式用于已有实例的可选启动，不替代上面的隔离 Gradle E2E runner。

## 报告在哪里

每次运行保存在 `run/e2e/<run-id>/`，证据在 `test-results/`：

- `report.json`：最终 PASS/FAIL、版本、必需阶段、检查错误。
- `events.jsonl`：逐阶段状态、运行 ID、语言、GUI scale 和测量证据。
- `gradle.log`：被 runner 启动的 Gradle/client 输出。
- `*.png`：实际 framebuffer 截图。
- `saves/PW_E2E_<run-id>/`：隔离测试世界。

`run/` 被 Git 忽略；不要把本机存档或报告提交到公共源码仓库。Release 验收档案如需共享，先删去本机用户名、绝对路径和无关个人数据。

## 当前分支运行证据（2026-10-07）

Minecraft 1.21.1 / NeoForge 21.1.256 / Java 21 / KubeJS `2101.7.2-build.379` 的 `en_us`、`zh_cn` 客户端均跑通过。最新单次英文 `diag-knockback-fix` 运行中，64 个 required 阶段全部 PASS，校验错误为零，耗时 107 秒。这是单次测量结果，不是可靠性统计，不能据此承诺偶发失败绝不再出现。

本次修复将脚本 generation 清理由 `registerBindings` 移至 `beforeScriptsLoaded`，避免绑定注册误清新建任务；同时调整召唤实体属性 NBT，避免准备阶段无来源 `damage` 引起随机击退、污染战斗初始位置。历史 Forge 1.20.1 双语验收记录保留在 [README](../../README.md)，不作为当前版本全量兼容的证明。

## Fixtures 与断言

测试脚本可用命令准备固定地形/物品，但随后必须通过公开 Playwright API 对实际游戏结果做断言。例如箱子测试检查：真实 `ContainerScreen`、63 个菜单槽位、玩家槽取出 emerald 后游标有货、点击箱子槽后内容变化且游标清空、关箱/重开和 save/rejoin 后内容保留。只有 server/client 观测到的状态才是行为证据。

公共 Playwright API 尽量保持跨版本同名方法、参数、返回值和错误语义，版本差异由底层适配；原生命令、NBT、Java 和 KubeJS 版本特性可不同。测试夹具集中适配是后续方向，目前未实现跨版本统一夹具，不能把现有脚本视为已全部兼容。

当前 suite 证明的是固定 vanilla/controlled single-player 流程，不代表任意模组屏幕、多玩家网络、其他 loader 或版本普遍兼容。
