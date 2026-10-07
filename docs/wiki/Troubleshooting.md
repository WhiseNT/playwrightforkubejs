# 故障排查

[← Wiki 首页](Home.md) · [安装](Installation.md) · [异步与错误](Async-Tasks-and-Errors.md) · [GUI 定位](GUI-and-Locators.md)

先读取**第一条** Playwright/KubeJS 错误及其对应阶段，不要只看最后一串因果错误。日志一般在实例 `logs/kubejs/client.log` 与 `logs/latest.log`。

| 错误/症状 | 常见原因 | 排查步骤 |
| --- | --- | --- |
| `Playwright is not defined` | mod 没加载、装错实例、脚本放错到 server scripts | 查当前 NeoForge 启动时缺失依赖；确认 JAR 与 KubeJS 都在当前客户端实例 `mods/`；脚本放 `kubejs/client_scripts/` |
| `NOT_IN_WORLD` | 动作需要玩家/世界，但脚本仍在标题菜单/加载页 | 先 `page.status().ready(timeoutMs)`，在 `.then()` 之后再读世界 |
| `TIMEOUT` | screen/state/locator 未在期限内出现，或客户端卡顿 | 查超时阶段和当时 `gui().snapshot()`；核实真实 screen type/前置条件，再设合理有限 timeout |
| `GUI_NOT_OPEN` | 还没打开 container/菜单，或屏幕已切换 | 先等待真实 GUI screen，再对当前 screen 查询 slot |
| `INVALID_ACTION` / locator matched 0 | selector 不匹配或控件不是可操作控件 | 打印 GUI snapshot 中实际 `translationKey`、role、text、visible、enabled；使用精确 selector |
| locator matched >1 | selector 不唯一 | 用 translation、role+name 或本次 snapshot 中真实 widget path 收紧目标 |
| `SLOT_OUT_OF_RANGE` | 使用了错误的 menu/inventory slot layout | 从当前 `gui().snapshot().get("slots")` 实测 index；不要把工作台/箱子/玩家背包编号混用 |
| 物品看似没移动 | 只检查 action return 而未等服务端状态同步，或目标槽错误 | 轮询 snapshot：源槽、目标槽、`carried`；再关闭并重开容器确认服务端保存状态 |
| `SCRIPT_RELOADED` | async task 属于之前的 KubeJS client-script generation | 这是 reload 后预期保护；新 script scope 重新获取 page 并启动新任务 |
| `CANCELLED` | 有界 task 被显式取消或由其上层链取消 | 检查取消是预期测试结果还是错误处理；确认 movement/input 已释放 |
| `PATHFINDING_FAILED` | 有界移动未到目标/期限内停止 | 此 mod 的 `move.to` 是目标跟随并带局部防卡行为，不是路径规划。先检查角色、障碍与目标，不要把这个错误误诊成 A* 路径失败 |

## 当前 1.21.1 分支已修复问题

2026-10-07 已修复 `registerBindings` 误清异步任务（改在 `beforeScriptsLoaded` 清理旧 generation），以及召唤属性 NBT、准备阶段无来源 `damage` 的随机击退对战斗初始位置的污染。如果类似症状再次发生，请保留 reload 顺序、战斗前后位置和首个失败阶段；最新单次英文测试 64 个 required 阶段 PASS 不等于可靠性统计，也不保证偶发失败绝不再出现。

## 收集最小诊断信息

提供给维护者/AI 时，保留：Minecraft/loader（当前分支为 NeoForge）/Java/KubeJS/Rhino/mod 版本，以及其他已安装依赖版本、语言、准确错误码和完整首个异常、当前 screen type、最小复现脚本、断言前后观测到的状态。去掉 Microsoft/Mojang 登录数据、完整个人 `.minecraft` 目录、访问令牌和私人世界文件。

## 反馈前的安全检查

- 不要发布 `launcher_accounts.json`、`usercache.json`、服务器地址/密码、访问 token。
- `run/e2e/` 报告含本机绝对路径；分享前审阅并脱敏。
- 可复现的 vanilla 问题提供干净世界步骤、截图和最小脚本；自定义模组问题同时提供准确 mod 列表/版本。
