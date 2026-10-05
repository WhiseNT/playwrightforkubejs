# Playwright For KubeJS

A client-side automation and test API for KubeJS scripts in Minecraft. It borrows familiar Playwright ideas—pages, locators, expectations, waits and asynchronous task chains—but it is **not** the official Playwright project and does not automate a browser. Actions run through the Minecraft client and its current screen, inventory, world and player.

> **Status:** experimental, version `0.1.0`. The API and supported Minecraft/Forge versions may change.

**简体中文：**这是一个面向 KubeJS 客户端脚本的 Minecraft 自动化测试模组，提供 GUI 定位、输入、背包/容器读取、世界状态查询和可链式异步任务。它不是官方 Playwright，也不是浏览器驱动器。当前已在 Minecraft 1.20.1 / Forge 47.2.0 的单人客户端上，对英文和简体中文界面运行真实 GUI 测试；其他版本、模组界面和多人环境仍需单独验证。

## Compatibility

The checked-in build configuration targets:

| Component | Version |
| --- | --- |
| Minecraft | 1.20.1 |
| Minecraft Forge | 47.2.0 (Forge 47.x) |
| Java | 17 |
| KubeJS for Forge | 2001.6.5-build.26 |
| Rhino for Forge | 2001.2.2-build.17 |
| Architectury for Forge | 9.2.14 |

The mod is a Forge client automation bridge. Install the built mod JAR alongside a compatible Forge/KubeJS installation and its required KubeJS dependencies. The published mod JAR does not bundle Minecraft, Forge, KubeJS, Rhino or Architectury.

## Build and install

1. Install a Java 17 JDK and Minecraft Forge 1.20.1.
2. Add KubeJS for Forge and its required dependencies (including Rhino and Architectury) to the same Minecraft profile.
3. Build the mod from the repository root:

   ```bash
   ./gradlew build
   ```

   On Windows, use `gradlew.bat build`.
4. Copy `build/libs/playwrightforkubejs-0.1.0.jar` into that profile's `mods/` directory and launch the client.
5. Put a test script in `kubejs/client_scripts/`. The normal public binding is `Playwright`; test-only fixtures are not registered unless the explicit E2E system property is enabled.

A first build may download Gradle and Maven dependencies. The checked-in Gradle wrapper is the recommended build entry point.

## Quick start

Save this as `kubejs/client_scripts/playwright_smoke.js`:

```js
Playwright.run("status-smoke", function (page) {
  return page.status().ready(120000)
    .then(function () {
      return page.status().all()
    })
    .then(function (snapshot) {
      return Playwright.expect(snapshot).toBeTruthy()
    })
    .then(function () {
      console.info("Playwright For KubeJS: client is ready")
    })
    .catchError(function (error) {
      console.error("Playwright For KubeJS smoke test failed: " + error)
      throw error
    })
})
```

`Playwright.run(name, callback)` invokes the callback with a page API on the Minecraft client thread. API actions and waits return `PlaywrightTask` values. Chain dependent operations with `.then(...)` and handle failures with `.catchError(...)`; callbacks may return another task, which is adopted by the chain. A client script reload invalidates tasks from the previous script generation and releases synthetic inputs.

## API overview

The public client binding exposes `Playwright.client().page()`, `Playwright.run(...)`, `Playwright.expect(value)` and `Playwright.reset()`.

The page API is organized around focused namespaces:

- **GUI and locators:** `page.gui().snapshot()`, `page.gui().waitFor("InventoryScreen", 10000)`, `page.getByTranslation("menu.singleplayer").click()`, and `page.getByRole("button", "Done").click()`.
- **Inventory and containers:** `page.inventory().snapshot()`, `page.inventory().equipment()`, `page.gui().snapshot()` for the current container menu and its carried stack, and `page.gui().click(slot, button)` for a menu slot.
- **Player and world:** `page.status().all()`, `.position()`, `.health()`, `page.block().get(position)`, and `page.entity().list(radius)`.
- **Input and movement:** `page.input().press(key)`, `.keysDown()`, `page.move().direction(direction, blocks)`, and `page.move().to(position, timeoutMs)`.
- **Waiting and evidence:** `page.waitForTimeout(ms)`, `page.waitUntil(predicate, timeoutMs)`, `page.screenshot().capture(path)`, plus chat, look, event and readiness helpers.

Supported GUI locator forms include `gui-text=...` (exact displayed text), `translation=...` (language-independent translation key), `role=...` (optionally with a name), `widget=...` (current screen widget path), and `world=...` (save ID or unambiguous name). Existing `text=...`, `slot=...`, `item=...`, `entity=...` and `screen=...` forms remain distinct query locators. GUI click/fill requires a unique actionable target; ambiguous, hidden or disabled controls fail explicitly.

The API is intentionally bounded to client-visible Minecraft state and the operations implemented by this mod. It is not a drop-in implementation of the complete browser Playwright API.

## Tests

Run the Java unit suite:

```bash
./gradlew test
```

The Python evidence-validator suite requires Python 3 and `psutil`:

```bash
python -m pip install psutil
python -m unittest discover -s examples -p 'test_client_e2e.py'
```

### Optional real-client E2E

The graphical client suite is opt-in and creates a new isolated run directory and world for every run. It exercises the UI rather than calling Minecraft's world-loading internals. The suite runner also checks event order, measured evidence, process exit, screenshots and world-save output.

Requirements: Java 17, Python 3 with `psutil`, a working graphical desktop, and Gradle 8.8. If Gradle 8.8 is not on the runner's detection path, pass its executable with `--gradle`.

```bash
python examples/run_client_e2e.py \
  --suite \
  --java-home "/path/to/jdk-17" \
  --gradle "/path/to/gradle-8.8/bin/gradle" \
  --timeout 600
```

On Windows, pass the Java and Gradle executable paths for that machine. Add `--online` if Gradle must download dependencies; by default the runner uses offline mode. Each run prints a JSON result and writes its report, event log, client log and screenshots under `run/e2e/<run-id>/test-results/`. Choose a fresh run ID when running a single configuration with `--run-id`; existing run directories are never overwritten.

## What has been verified

On October 5, 2026, the full real-client suite passed in two fresh Minecraft 1.20.1 / Forge 47.2.0 runs:

- English (`en_us`, GUI scale 2) and Simplified Chinese (`zh_cn`, GUI scale 3).
- Real menu-based world creation, GUI locators, inventory, crafting, equipment, combat, movement, save/rejoin, and client-script reload.
- A vanilla chest flow that opens the real container screen, moves an emerald stack between measured player/chest menu slots, closes and reopens the chest, then re-joins the saved world and verifies the same chest contents.
- Structured evidence validation that rejects missing, contradictory, duplicate or reordered chest events and fabricated slot/item state.

The Java unit suite and Python evidence tests also passed in the same verification run. These results establish tested scenarios on this specific client stack—not universal compatibility with arbitrary mods or test plans.

## Known scope and limitations

- The supported and exercised Minecraft target is 1.20.1 on Forge. Other Minecraft versions, loaders, KubeJS builds and mod combinations are not claimed compatible.
- The client E2E tests run in a controlled single-player flat world. Multiplayer, dedicated-server operation, latency, permissions and client/server race behavior have not been established by this suite.
- GUI coverage includes vanilla menus and containers used by the fixtures. Custom mod screens may expose nonstandard widgets or interaction behavior and need their own real-client tests.
- `move().to(...)` is a bounded target follower with local anti-stall behavior; it does **not** plan a route (no A* pathfinding claim).
- The E2E suite uses commands to prepare deterministic fixtures. It then checks actual client-side state and actions; this should not be confused with a test of natural progression without fixtures.
- The project has not yet established broad compatibility or reliability statistics across a representative collection of unrelated mods.

## License

The project is distributed under the MIT License; see [LICENSE](LICENSE). Third-party Minecraft/Forge/KubeJS dependencies retain their own licenses.
