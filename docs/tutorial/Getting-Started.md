# 新手实操：安装并运行第一个客户端测试

本教程面向第一次写 KubeJS 客户端测试的玩家和整合包制作者。按顺序做完后，你会在游戏中运行一个脚本：等客户端准备好、读取玩家状态、打开暂停菜单、用语言无关的控件定位器点“回到游戏”，并在客户端日志看到结果。

> 本教程针对 Minecraft 1.20.1、Forge 47.x、Java 17 和 KubeJS for Forge。自定义 GUI 和其他版本需要另行验证。模组 API 借鉴 Playwright 风格，但不是官方 Playwright，也不驱动浏览器。

## 1. 安装环境

需要：

- Java 17。
- Minecraft 1.20.1 和 Forge 47.2.0（或经验证兼容的 Forge 47.x）。
- KubeJS for Forge `2001.6.5-build.26` 及其依赖 Rhino `2001.2.2-build.17`、Architectury `9.2.14`。
- Playwright For KubeJS `0.1.0`。

下载本教程对应的 JAR：从项目 GitHub 的 `v0.1.0` Release 下载 `playwrightforkubejs-0.1.0.jar`。把 JAR 放入**同一个 Minecraft 实例**的 `mods/` 目录，同时确保上述 Forge 与 KubeJS 依赖也在该目录。启动客户端；本模组提供的是客户端自动化绑定。

不要把这个项目的 GitHub 源码 ZIP 当作 mod JAR 使用。正常安装只需要下载 `.jar` Release 附件，不必运行 Gradle。

## 2. 创建客户端脚本

在实例目录里创建文件：

```text
kubejs/client_scripts/pw_first_test.js
```

粘贴以下完整脚本：

```js
Playwright.run("first-client-test", function (page) {
  return page.status().ready(120000)
    .then(function () {
      return page.status().all()
    })
    .then(function (status) {
      console.info("[PW] Client is ready: " + String(status))
      return page.input().press("escape")
    })
    .then(function () {
      return page.gui().waitFor("PauseScreen", 10000)
    })
    .then(function () {
      return page.gui().getByTranslation("menu.returnToGame").waitForEnabled(5000)
    })
    .then(function () {
      return page.gui().getByTranslation("menu.returnToGame").click()
    })
    .then(function () {
      return page.gui().snapshot()
    })
    .then(function (gui) {
      if (String(gui.get("open")) === "true") {
        throw new Error("Expected the pause screen to close; got " + String(gui.get("type")))
      }
      console.info("[PW] Pause menu closed through a localized GUI locator")
    })
    .catchError(function (error) {
      console.error("[PW] First test failed: " + String(error))
    })
})
```

此例假设你已经进入一个世界。`escape` 打开暂停菜单；`menu.returnToGame` 是游戏内“回到游戏”控件的翻译键，所以英文、简体中文客户端都不用写不同的可见文本。


## 3. 启动并确认

1. 启动 Minecraft 客户端，进入单人世界。
2. 打开 KubeJS 客户端日志（通常是实例的 `logs/kubejs/client.log`；崩溃时也检查 `logs/latest.log`）。
3. 如刚创建或修改脚本，按客户端环境要求重新加载 KubeJS 客户端脚本，或重启客户端。
4. 查看 `[PW] Client is ready` 和 `[PW] Pause menu closed...` 消息。第一条确认状态查询已完成，第二条确认真实 GUI 定位器点击了当前屏幕中的控件。
5. 如果菜单被打开但没有关闭，查找 `First test failed` 后面的具体错误；不要只看脚本是否加载。

脚本重新加载会使旧 generation 的异步任务失效并释放模组管理的合成输入。修改后用新一代脚本重新运行，不要依赖旧 callback 延迟完成。

## 4. 这段脚本在做什么

- `Playwright.run(name, callback)`：在客户端脚本环境执行一段 Playwright 风格的任务，callback 参数是 page API。
- `page.status().ready(timeoutMs)`：有限时等待玩家/世界可用。
- `page.status().all()`：读取结构化客户端状态快照。
- `page.input().press("escape")`：发送一次已定义的合成按键。
- `page.gui().waitFor("PauseScreen", timeoutMs)`：有限时等待当前屏幕切换到指定 screen 类型。
- `getByTranslation(...).waitForEnabled(...)`：等待唯一、可见且可操作的翻译控件。
- `.click()`：通过当前 GUI 控件进行点击；屏幕变化、隐藏、禁用或定位歧义会显式报错。
- `.then(...)`：只有上一步完成后才继续；返回另一个 PlaywrightTask 时会等待它完成。
- `.catchError(...)`：记录任务链中发生的错误，方便日志排查。

## 5. 常见问题

**日志提示 `Playwright is not defined`**

确认 mod JAR 与 KubeJS 都装在客户端正在使用的实例 `mods/` 目录，查看启动日志是否报告缺少依赖，确认这是 `client_scripts` 而不是 `server_scripts`。

**`NOT_IN_WORLD` / `NOT_READY`**

本教程必须在进入世界后执行。给需要世界状态的操作加 `status().ready(...)`，并且在任务链里等待它完成。

**找不到翻译键、0 个匹配或多个匹配**

先 `page.gui().snapshot()` 查看当前屏幕类型、控件翻译键和 `matches`。不要猜 GUI 控件顺序；相同翻译键多次出现时，选择更明确的 locator 或从当前 snapshot 获取 widget ID。

**新版本安装后旧脚本报错**

检查 Java、Minecraft、Forge、KubeJS、Rhino 与 mod 版本是否匹配。其他 Minecraft 版本和 loader 当前不在发布版兼容承诺内。

**要测试容器或整合包自定义屏幕**

先从当前屏幕快照和容器 `slots`/`carried` 数据建立实测断言；本仓库 Wiki 的「容器与背包」和「E2E 测试」章节提供更完整示例。原版箱子已通过本项目的受控双语 E2E，但这不自动证明任意模组容器兼容。

## 下一步

- [Wiki 首页与目录](../wiki/Home.md)
- [快速开始](../wiki/Quick-Start.md)
- [GUI 和定位器](../wiki/GUI-and-Locators.md)
- [异步任务与错误处理](../wiki/Async-Tasks-and-Errors.md)
- [故障排查](../wiki/Troubleshooting.md)
