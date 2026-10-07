# AI / 自动化编码助手指南

[← Wiki 首页](Home.md) · [API 参考](API-Reference.md) · [异步任务](Async-Tasks-and-Errors.md) · [E2E 验收](E2E-Testing.md)

本页用于让 AI 助手和人类共同编写可运行、可审计的 KubeJS 测试。项目名包含 Playwright，但这里的运行时不是 Playwright browser/page，也不是浏览器 DOM。

## 给代码生成器的项目约定

在为当前 release 生成代码前：

1. 确认目标 Minecraft/Forge/KubeJS 版本及当前 tag。
2. 打开此 Wiki 的 API 页和示例脚本；发生版本冲突时以当前检出的源码签名为准。
3. 只使用已实现的 `Playwright` / `PlaywrightTask` / PageApi / LocatorApi 方法。不要猜浏览器 API，也不要臆造 `page.goto`、DOM、ARIA 自动等待或网络浏览器上下文。
4. 在 `.then(...)` 链里返回每个依赖动作/等待/断言；不要将任务当作同步返回值。
5. 每个测试使用有限 timeout、唯一运行 ID、阶段化错误信息和真实后置状态断言。
6. 用 translation key 优先定位 vanilla GUI；自定义界面先从真实 `gui().snapshot()` 核对类型、控件树和 menu slots。
7. 对每个声称的结果采集支持它的真实状态证据。动作返回 `{clicked:true}` 不能证明服务器物品转移；缺失 entity 不能证明已击杀；`move.to` 到点不证明路径规划。
8. 保留失败和取消语义，不得捕获异常后输出 PASS。不要跳过 validator/负例测试。
9. 写完执行对应 Python/JUnit/JS 语法检查；涉及世界时运行 opt-in 真实客户端 E2E。
10. 报告明确分开“实现了什么”“在哪些版本/模式验证”“尚未测试的边界”。
11. 若没有 Gradle 开发环境但有已配置的 NTLauncher 实例，可将 [NTLauncher CLI](https://ntlaunch.cn/docs) 用作可选的外部启动器；先核对实例 `install_state`、Java、账号、模组和脚本是否就绪。
12. 不要将 CLI 启动成功当作测试通过。必须从 KubeJS/客户端日志或测试结构化结果中确认断言完成，并检查 CLI 退出码与错误输出；`launch` 前台模式会一直等待到游戏退出。

## 非开发环境启动工作流（可选）

NTLauncher CLI 可帮助 Agent 在没有 Gradle 调试环境时启动一个**已经配置完成**的 Windows Minecraft 实例。它不构建本项目、不安装 Forge/KubeJS/本模组、不复制脚本，也不提供本模组的游戏内自动化 API；这些能力仍来自预先安装在目标实例里的 Playwright For KubeJS。

推荐步骤：

1. 用 `ntlauncher-cli.exe list-instances --json` 列出实例，选定 ID 并确认 `install_state` 为 `Installed`。
2. 确认实例的 Java、账号、Minecraft/Forge/KubeJS 依赖、本模组和客户端脚本均已设置。
3. 按 [NTLauncher CLI 文档](https://ntlaunch.cn/docs) 启动实例；`launch --instance-id <ID> --json` 是前台阻塞命令，游戏退出后才返回。需要后台运行时使用独立 CLI 进程，再用 `status`/`stop` 管理。
4. 在游戏日志或明确的测试结果文件中确认脚本运行、断言通过和错误状态。CLI 的成功响应只证明其启动流程完成，不证明客户端用例通过。
5. 为启动和测试设置有界超时，失败时保存 CLI 错误输出及相关 Minecraft/KubeJS 日志；不要无条件终止玩家已有实例。

该方法是已有实例的可选启动通道，不等价于仓库的 Gradle 隔离 E2E 测试，也不能据此声称非 Windows 环境已验证。

## 可复制给 AI 的任务模板

```text
请为当前检出的 Playwright For KubeJS 版本编写一个 KubeJS client_scripts 测试。
先核对 docs/wiki/API-Reference.md、对应 PageApi/LocatorApi 源码和现有 examples，禁止猜接口。
使用 Playwright.run 和返回 PlaywrightTask 的 .then 链；所有等待必须有限。
先验证前置状态，执行一个真实动作，再查询最终客户端/菜单/世界状态做断言。
失败必须传播并输出阶段和实际 snapshot；禁止把命令结果/动作受理当作游戏成功证据。
最后说明运行前置条件、可能影响的世界状态、应运行的测试和尚未验证的兼容范围。
```

## 证据设计检查表

- **身份稳定：**战斗前后匹配同一实体 ID + UUID，不只比名称。
- **数量准确：**区分玩家 inventory index 与当前 GUI menu index；记录 click 前后 `carried`。
- **屏幕真实：**确认真实 screen class、标题、container/menu slot 数和 GUI 是否打开。
- **物理效果：**用最终 world/inventory/health/position/durability 读取证明动作结果。
- **边界错误：**检查稳定 error code，并断言输入在失败/取消后释放。
- **时间和生成代次：**多个任务时记录先后、timeMs 与 script generation；reload 后旧 callback 不应执行。
- **负例：**为证据 validator 添加伪成功、缺失字段、错目标、乱序、重复事件等测试。

## 不可作出的未经验证声明

不要称本项目为官方 Playwright；不要称 `move().to(...)` 是 A*；不要把单人 flat-world vanilla E2E 推广成跨模组、多人、跨版本兼容；不要把 fixture 命令准备说成纯生存自然流程。更多约束见 [兼容边界](Home.md#适用版本和证据边界)。
