# Async Tasks and Error Handling

[简体中文](https://github.com/WhiseNT/playwrightforkubejs/wiki/Async-Tasks-and-Errors) | English

[← Wiki Home](https://github.com/WhiseNT/playwrightforkubejs/wiki/Home.en) · [Quick Start](https://github.com/WhiseNT/playwrightforkubejs/wiki/Quick-Start.en) · [API Reference](https://github.com/WhiseNT/playwrightforkubejs/wiki/API-Reference.en) · [Troubleshooting](https://github.com/WhiseNT/playwrightforkubejs/wiki/Troubleshooting.en)

## All actions/queries are tasks

The KubeJS Playwright API returns `PlaywrightTask`. Do not read it synchronously:

```js
// Incorrect approach: value is not yet the result Map.
var value = page.status().all()

// Correct: read the Map only after the task completes.
return page.status().all().then(function (value) {
  console.info(value.get("status"))
  return value
})
```

The callback passed to `PlaywrightTask.then(callback)` can return either an ordinary value or another PlaywrightTask. If it returns a task, the outer task waits for that task to complete before delivering its result. This allows multistep GUI flows to execute in causal order:

```js
return page.status().ready(120000)
  .then(function () { return page.gui().waitFor("PauseScreen", 10000) })
  .then(function () { return page.gui().getByTranslation("menu.returnToGame").click() })
  .then(function () { return page.gui().snapshot() })
```

## catchError and failure propagation

Errors must be preserved and diagnosable:

```js
return page.status().ready(30000)
  .then(function () { return page.status().all() })
  .catchError(function (error) {
    console.error("[PW] test failed: " + String(error))
    throw error
  })
```

If a catch handler only logs and swallows the error, the test framework or an outer chain may treat the failure as a success. Return a normal result from `catchError` only when recovery has actually completed and its success conditions have also been asserted.

## Bounded waits

Common bounded waits:

- `page.status().ready(timeoutMs)`
- `page.gui().waitFor(screenType, timeoutMs)`
- `locator.waitFor(state, timeoutMs)` / `waitForEnabled(timeoutMs)`
- `page.inventory().waitFor(item, timeoutMs)`
- `page.waitUntil(function(){ return trueOrFalse }, timeoutMs)`

A `waitUntil` predicate must synchronously return a boolean, not another asynchronous task. The timeout must be finite and greater than zero. Game-state conditions are generally preferable to fixed sleeps. If polling is needed, limit the number of attempts and log the actual snapshot before timing out.

## Cancellation, timeout, and reload

- `task.cancel()` cancels a task that is still running. The caller should check the returned status and await/handle `CANCELLED`.
- `task.timeout(milliseconds)` sets a wall-clock timeout for the task; the failure code is `TIMEOUT`.
- `Playwright.reset()` or an actual KubeJS client-script reload advances the generation, causing active tasks from the old generation to fail with `SCRIPT_RELOADED` and releasing synthetic input tracked by the mod.
- After a reload, callbacks from the old scope must not modify the new test run. Obtain a new page and run in a new scope.

The current 1.21.1 branch cleans up the old generation in `beforeScriptsLoaded`, rather than during binding registration in `registerBindings`, to avoid accidentally cancelling tasks just created by new scripts.

The mod has specifically tested actual script-reload behavior for waits, then chains, navigation tasks, and held input. This does not mean that arbitrary third-party callbacks cannot access their own stale state.

## Important error codes

`TIMEOUT`, `CANCELLED`, `SCRIPT_RELOADED`, `GUI_NOT_OPEN`, `INVALID_PARAMS`, `INVALID_ACTION`, `SLOT_OUT_OF_RANGE`, `ENTITY_NOT_FOUND`, `BLOCK_OUT_OF_RANGE`, `NOT_IN_WORLD`, `CLIENT_NOT_RUNNING`, and `PATHFINDING_FAILED`. Different error codes require different recovery strategies; do not relabel every exception as a vague PASS.

## Reliable test template

Each step includes: **precondition state → one action → wait for an observable postcondition → assert the snapshot**. On an exception, log the stage name, screen type, GUI/slot snapshot, and expected and actual values. Otherwise, even correct asynchronous task scheduling cannot distinguish fixture issues from client or assertion issues.
