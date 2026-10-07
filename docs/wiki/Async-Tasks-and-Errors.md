# 异步任务与错误处理

简体中文 | [English](https://github.com/WhiseNT/playwrightforkubejs/wiki/Async-Tasks-and-Errors.en)

[← Wiki 首页](https://github.com/WhiseNT/playwrightforkubejs/wiki/Home) · [快速开始](https://github.com/WhiseNT/playwrightforkubejs/wiki/Quick-Start) · [API 参考](https://github.com/WhiseNT/playwrightforkubejs/wiki/API-Reference) · [故障排查](https://github.com/WhiseNT/playwrightforkubejs/wiki/Troubleshooting)

## 所有动作/查询都是任务

KubeJS 的 Playwright API 返回 `PlaywrightTask`，不要写成同步读取：

```js
// 错误思路：这里的 value 还不是结果 Map。
var value = page.status().all()

// 正确：任务完成后才读取 Map。
return page.status().all().then(function (value) {
  console.info(value.get("status"))
  return value
})
```

`PlaywrightTask.then(callback)` 的 callback 返回普通值或另一个 PlaywrightTask 都可以；如果返回任务，外层会等待其完成后再交付结果。这让多步 GUI 流程能以因果顺序执行：

```js
return page.status().ready(120000)
  .then(function () { return page.gui().waitFor("PauseScreen", 10000) })
  .then(function () { return page.gui().getByTranslation("menu.returnToGame").click() })
  .then(function () { return page.gui().snapshot() })
```

## catchError 与失败传播

错误必须保留并可诊断：

```js
return page.status().ready(30000)
  .then(function () { return page.status().all() })
  .catchError(function (error) {
    console.error("[PW] test failed: " + String(error))
    throw error
  })
```

如果 catch 只记录并吞掉错误，测试框架或更外层链就可能把失败当作成功。只有确实完成恢复、且恢复成功条件也有断言时，才应从 `catchError` 返回正常结果。

## 有界等待

常见有限等待：

- `page.status().ready(timeoutMs)`
- `page.gui().waitFor(screenType, timeoutMs)`
- `locator.waitFor(state, timeoutMs)` / `waitForEnabled(timeoutMs)`
- `page.inventory().waitFor(item, timeoutMs)`
- `page.waitUntil(function(){ return trueOrFalse }, timeoutMs)`

`waitUntil` predicate 必须同步返回 boolean，不能返回另一个异步任务。timeout 必须有限且大于零。游戏状态通常优先于固定 sleep；若需要轮询，请加次数上限，并输出超时前的实际 snapshot。

## 取消、timeout 与 reload

- `task.cancel()` 取消仍在运行的 task；调用方应检查返回状态并等待/捕获 `CANCELLED`。
- `task.timeout(milliseconds)` 为 task 设置 wall-clock timeout，失败码为 `TIMEOUT`。
- `Playwright.reset()` 或真实 KubeJS client-script reload 会推进 generation，使旧 generation 的活动任务以 `SCRIPT_RELOADED` 失败，并释放模组追踪到的合成输入。
- 重新加载后不得让旧 scope callback 修改新一轮测试。重新获取 page 并在新 scope 里运行。

当前 1.21.1 分支在 `beforeScriptsLoaded` 清理旧 generation，而不是在 `registerBindings` 注册绑定时清理，避免新脚本刚创建的任务被误取消。

本模组专门测试过 wait、then 链、导航任务、hold 输入的真实脚本 reload 行为；这不意味着任意第三方 callback 都不会访问自己的 stale state。

## 重要错误码

`TIMEOUT`、`CANCELLED`、`SCRIPT_RELOADED`、`GUI_NOT_OPEN`、`INVALID_PARAMS`、`INVALID_ACTION`、`SLOT_OUT_OF_RANGE`、`ENTITY_NOT_FOUND`、`BLOCK_OUT_OF_RANGE`、`NOT_IN_WORLD`、`CLIENT_NOT_RUNNING`、`PATHFINDING_FAILED`。遇到不同的错误码要采取不同恢复策略，不要把所有异常重新标成一个模糊的 PASS。

## 可靠测试模板

每一步包含：**前置状态 → 一个动作 → 等待可观测后置条件 → 断言快照**。异常时输出阶段名、屏幕类型、GUI/slot snapshot、期望与实际值。否则即使异步 task 调度正确，也无法分清 fixture、客户端和断言的问题。
