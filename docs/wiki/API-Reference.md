# API 参考

[← Wiki 首页](Home.md) · [快速开始](Quick-Start.md) · [GUI 与 Locator](GUI-and-Locators.md) · [容器](Inventory-and-Containers.md)

此表面向当前 `mc-1.21.1` 分支（发布目标 `v0.1.0-mc1.21.1`）；细节以对应 tag 内 Java API 和实际客户端行为为准。所有命名空间由 `page` 提供，所有动作/查询返回可异步链式处理的 `PlaywrightTask`。

公共 Playwright API 尽量跨版本保持同名方法、参数、返回值和错误语义，底层适配 Minecraft 差异。这不保证原生命令、NBT、Java 访问或 KubeJS 版本特性一致，也不代表跨版本统一测试夹具已实现。环境依赖见 [安装](Installation.md)。

## 根对象

| 调用 | 作用 |
| --- | --- |
| `Playwright.client()` | 获取客户端 PlaywrightClient |
| `Playwright.client().page()` | 获取客户端 PageApi |
| `Playwright.run(name, function(page) {...})` | 在客户端 dispatcher 上执行任务链 |
| `Playwright.expect(value)` | 创建异步 expectation |
| `Playwright.reset()` | 主动重置脚本 generation、使旧任务失效并释放输入 |

## PageApi 与 Locator

| 调用 | 作用 |
| --- | --- |
| `page.locator(selector)` | 用 selector 创建 LocatorApi |
| `page.getByGuiText(text)` | 精确匹配当前 GUI 的显示文本 |
| `page.getByTranslation(key)` | 匹配当前 GUI 的 translation key |
| `page.getByRole(role[, name])` | 按 GUI role 与可选显示名定位 |
| `page.getByWorld(nameOrId)` | 定位世界列表条目 |
| `page.getByWidget(path)` | 按当前屏幕的 widget path 定位 |
| `page.getBySlot(index)` | 定位当前容器菜单的菜单槽 |
| `page.getByItem(itemId)` / `getByEntity(type)` | 建立物品/实体只读查询 locator |
| `page.waitUntil(predicate, timeoutMs)` | 在有限期限内轮询同步 boolean predicate |
| `page.waitForTimeout(ms)` | 等待指定毫秒数；优先用有状态条件替代固定睡眠 |
| `page.waitForChat(match, timeoutMs)` | 等待聊天记录匹配 |
| `page.waitForGui(timeoutMs)` | 等待某个 GUI 打开 |

Locator 支持 `snapshot()`, `count()`, `textContent()`, `click()`, `fill(text)`, `waitFor(state, timeoutMs)`, `waitForVisible`, `waitForEnabled`, `screenshot(output)`。等待状态是 `attached`、`visible`、`enabled`、`hidden` 或 `detached`。`click()` 只接受 GUI 控件/菜单槽；`fill()` 只接受可编辑文本框。

## GUI 与容器

`page.gui()` 提供：`waitFor(screenType, timeoutMs)`、`waitForOpen(timeoutMs)`、`waitForUpdate(timeoutMs)`、`snapshot()`、`info()`、`layout()`、`slot(index)`、`click(slot[, button])`、`drag(slots, button)`、`close()`。

当前为 `AbstractContainerScreen` 的屏幕时，snapshot 额外含有 `containerId`、菜单 `slots` 列表与 `carried` 鼠标游标物品。菜单 slot index 和玩家 inventory slot index 不是同一概念；应使用本次 screen 的实测菜单 slot。

## 游戏状态与动作

| Namespace | 代表方法 |
| --- | --- |
| `page.status()` | `ready(timeoutMs)`, `all()`, `position()`, `rotation()`, `health()`, `world()` |
| `page.inventory()` | `snapshot()`, `slot(index)`, `held()`, `equipment()`, `waitFor(item, timeoutMs)`, `selectHotbar(slot)`, `use()`, `swapHands()` |
| `page.block()` | `get(position)`, `breakBlock(position)`, `place(position, face)`, `interact(position[, face])` |
| `page.entity()` | `list(radius)`, `info(id)`, `attack(target)`, `interact(target)`, `mount(target)`, `dismount()` |
| `page.input()` | `press(key)`, `hold(key, durationMs)`, `combo(keys)`, `down(key)`, `up(key)`, `keysDown()`, `click(button)`, `type(text)` |
| `page.move()` | `direction(direction, blocks)`, `to({x,y,z}[, timeoutMs])`, `jump()`, `sneak(enabled)`, `sprint(enabled)` |
| `page.look()` | `set(yaw,pitch)`, `at(position)`, `entity(filter)`, `current()` |
| `page.chat()` | `send(message)`, `command(command)`, `history(last)`, `waitFor(match, timeoutMs)` |
| `page.screenshot()` | `capture(output)` |
| `page.waitApi()` | `ticks(ticks)`, `timeout(ms)`, `event(name, timeoutMs)`, `guiOpen(timeoutMs)` |

实体 target 支持整数 ID，或非空 filter map（可含 `type`、`name`、`maxDistance` 等筛选）。具体错误码与返回 Map 字段应在对应代码和游戏版本上核实。

## Expect

公开 expectation：`toBeTruthy()`, `toEqual(expected)`, `toHaveText(expected)`, `toHaveCount(count)`, `toHaveItem(itemId)`, `toBeVisible()`。它们返回任务；必须把它们返回到 `.then(...)` 链中，才会等待断言并传播失败。

## 值与线程

Rhino 暴露的 Java `Map` 可通过 `value.get("key")` 读取。执行异步操作时遵循 Promise 风格：返回任务、按顺序链式读取；不要把异步返回值当作同步 Map。GUI 输入会在客户端主线程执行，屏幕切换期间旧 screen locator 可能失效，应在 transition 后重新查询。
