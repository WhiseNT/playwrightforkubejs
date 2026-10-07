# 快速开始

简体中文 | [English](Quick-Start.en.md)

[← Wiki 首页](Home.md) · [安装](Installation.md) · [完整 API](API-Reference.md) · [新手分步教程](../tutorial/Getting-Started.md)

## 第一个状态检查

在 `kubejs/client_scripts/pw_status.js` 放入：

```js
Playwright.run("status-check", function (page) {
  return page.status().ready(120000)
    .then(function () {
      return page.status().all()
    })
    .then(function (status) {
      console.info("[PW] Minecraft client ready: " + String(status))
      return status
    })
    .catchError(function (error) {
      console.error("[PW] status-check failed: " + String(error))
      throw error
    })
})
```

进入世界后观察 `logs/kubejs/client.log`。只有日志明确显示状态检查通过才算成功；脚本文件存在不等于测试已运行。

## 用翻译键点击当前暂停菜单

```js
Playwright.run("close-pause-menu", function (page) {
  return page.input().press("escape")
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
      console.info("[PW] menu.returnToGame click closed the current screen")
    })
    .catchError(function (error) {
      console.error("[PW] close-pause-menu failed: " + String(error))
      throw error
    })
})
```

运行此用例时应处在游戏世界。`escape` 打开真实 PauseScreen；`menu.returnToGame` 是跨语言翻译键；最后读取新 GUI 状态并断言菜单关闭。控件不存在或不唯一时，locator 会报错而不是偷偷点击别的控件。

## 编写脚本的顺序

1. 先等客户端/世界 ready。
2. 查询当前状态或屏幕 snapshot，确定前置条件。
3. 定位一个明确的控件或目标。
4. 执行一个动作，并等待动作任务完成。
5. 再读取游戏的真实状态作为断言。
6. 为整条任务链设置可诊断的错误处理。

**不要**在脚本加载时直接同步假定 GUI/世界已经就绪，不要只因为 action task 成功就假定游戏状态改变了。更完整的 API 见[参考表](API-Reference.md)；箱子示例见[背包与容器](Inventory-and-Containers.md)。
