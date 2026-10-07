# Playwright For KubeJS

A client-side automation and test API for KubeJS scripts in Minecraft. It borrows familiar Playwright ideas—pages, locators, expectations, waits and asynchronous task chains—but it is **not** the official Playwright project and does not automate a browser. Actions run through the Minecraft client and its current screen, inventory, world and player.

> **Status:** experimental, version `0.1.0+mc1.21.1` on branch `mc-1.21.1`; release tag `v0.1.0-mc1.21.1`. The API and supported Minecraft/loader versions may change.

**简体中文：**这是一个面向 KubeJS 客户端脚本的 Minecraft 自动化测试模组，提供 GUI 定位、输入、背包/容器读取、世界状态查询和可链式异步任务。它不是官方 Playwright，也不是浏览器驱动器。当前分支面向 Minecraft 1.21.1 / NeoForge 21.1.256 / Java 21；2026 年 10 月 7 日英文和简体中文单人客户端测试均跑通过。历史 Forge 1.20.1 发行版仍单独保留；模组界面、多人环境及其他版本组合仍需单独验证。

## Compatibility

The checked-in build configuration targets:

| Component | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.256 |
| Java | 21 |
| KubeJS for NeoForge | 2101.7.2-build.379 |
| Rhino | Install the version required by this KubeJS build |

The mod is a NeoForge client automation bridge. Install the built mod JAR alongside the matching NeoForge/KubeJS installation and KubeJS's required dependencies. The mod JAR does not bundle Minecraft, NeoForge, KubeJS or Rhino. Architectury is not an explicit dependency of this branch.

### Cross-version policy

Public Playwright API names, parameters, return values and error semantics should remain consistent across supported branches where practical; Minecraft-specific implementation details are adapted underneath. Native commands, NBT, Java access and KubeJS version-specific features may differ. Centralized fixture adaptation is a planned direction, not an already-built cross-version fixture layer or a guarantee that every script is compatible.

## Build and install

1. Install a Java 21 JDK and Minecraft 1.21.1 with NeoForge 21.1.256.
2. Add KubeJS for NeoForge `2101.7.2-build.379` and its required dependencies (including its matching Rhino build) to the same Minecraft profile.
3. Build the mod from the repository root:

   ```bash
   ./gradlew build
   ```

   On Windows, use `gradlew.bat build`.
4. Copy `build/libs/playwrightforkubejs-0.1.0+mc1.21.1.jar` into that profile's `mods/` directory and launch the client.
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

## Download and learning resources

- **Minecraft 1.21.1 / NeoForge:** [Download the v0.1.0-mc1.21.1 JAR](https://github.com/WhiseNT/playwrightforkubejs/releases/download/v0.1.0-mc1.21.1/playwrightforkubejs-0.1.0%2Bmc1.21.1.jar) · [All releases](https://github.com/WhiseNT/playwrightforkubejs/releases). Source builds use the instructions above.
- **Historical Forge 1.20.1 release only:** [Download the v0.1.0 JAR](https://github.com/WhiseNT/playwrightforkubejs/releases/download/v0.1.0/playwrightforkubejs-0.1.0.jar). This is not the NeoForge 1.21.1 artifact.
- **New user:** [Step-by-step installation and first test](docs/tutorial/Getting-Started.md)
- **Wiki:** [Online Wiki home](https://github.com/WhiseNT/playwrightforkubejs/wiki) · [Quick start](https://github.com/WhiseNT/playwrightforkubejs/wiki/Quick-Start) · [API reference](https://github.com/WhiseNT/playwrightforkubejs/wiki/API-Reference) · [GUI locators](https://github.com/WhiseNT/playwrightforkubejs/wiki/GUI-and-Locators) · [Containers](https://github.com/WhiseNT/playwrightforkubejs/wiki/Inventory-and-Containers) · [AI coding guide](https://github.com/WhiseNT/playwrightforkubejs/wiki/AI-Coding-Guide) · [Troubleshooting](https://github.com/WhiseNT/playwrightforkubejs/wiki/Troubleshooting)

Published release JARs are attached to their matching GitHub Releases; choose the correct Minecraft/loader artifact rather than rebuilding merely to install it. The Wiki source pages remain available under [`docs/wiki/`](docs/wiki/Home.md) for offline browsing and code review.

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

Requirements: Java 21, Python 3 with `psutil`, a working graphical desktop, and Gradle 8.8. If Gradle 8.8 is not on the runner's detection path, pass its executable with `--gradle`.

```bash
python examples/run_client_e2e.py \
  --suite \
  --java-home "/path/to/jdk-21" \
  --gradle "/path/to/gradle-8.8/bin/gradle" \
  --timeout 600
```

On Windows, pass the Java and Gradle executable paths for that machine. Add `--online` if Gradle must download dependencies; by default the runner uses offline mode. Each run prints a JSON result and writes its report, event log, client log and screenshots under `run/e2e/<run-id>/test-results/`. Choose a fresh run ID when running a single configuration with `--run-id`; existing run directories are never overwritten.

### Launch an installed instance with NTLauncher CLI (optional)

If an agent has no Gradle development environment, it can use [NTLauncher](https://ntlaunch.cn/)'s Windows CLI to launch an already-installed Minecraft instance. The CLI starts/manages the process; this mod still provides automation only after Minecraft loads it. The instance must already contain a compatible NeoForge/KubeJS setup, this mod JAR, and the client test scripts. Configure a usable account and Java runtime in NTLauncher first. The CLI does not build the mod, install dependencies, copy scripts, or report that a Playwright test passed.

The documented workflow is to run `ntlauncher-cli.exe list-instances --json`, select an instance whose `install_state` is `Installed`, then run `ntlauncher-cli.exe launch --instance-id <ID> --json`. `launch` is a foreground, blocking command that returns when the game exits; use a separate CLI process and its `status`/`stop` commands for controlled background runs. Check both the CLI exit result and the Minecraft/KubeJS client log or a test-generated result before reporting test success. See the [NTLauncher CLI documentation](https://ntlaunch.cn/docs) for PowerShell examples, timeouts, and process handling.

This is an optional launch path for preconfigured instances, not a replacement for the Gradle-based isolated E2E suite above. NTLauncher currently documents a Windows CLI release.

## What has been verified

### Current Minecraft 1.21.1 branch

On October 7, 2026, real-client tests passed with both `en_us` and `zh_cn` on Minecraft 1.21.1 / NeoForge 21.1.256. The latest single English run, `diag-knockback-fix`, passed all 64 required stages with zero validator errors in 107 seconds. This is a single-run result, not a reliability statistic.

The fixes move script-generation reset from `registerBindings` to `beforeScriptsLoaded`, avoiding accidental cancellation of newly created tasks, and correct summoned-entity attribute NBT and the random knockback caused by source-less `damage` during combat setup. These fixes address the observed failures; they do not establish that intermittent failures can never recur.

### Historical Minecraft 1.20.1 release

On October 5, 2026, the full real-client suite passed in two fresh Minecraft 1.20.1 / Forge 47.2.0 runs:

- English (`en_us`, GUI scale 2) and Simplified Chinese (`zh_cn`, GUI scale 3).
- Real menu-based world creation, GUI locators, inventory, crafting, equipment, combat, movement, save/rejoin, and client-script reload.
- A vanilla chest flow that opens the real container screen, moves an emerald stack between measured player/chest menu slots, closes and reopens the chest, then re-joins the saved world and verifies the same chest contents.
- Structured evidence validation that rejects missing, contradictory, duplicate or reordered chest events and fabricated slot/item state.

The Java unit suite and Python evidence tests also passed in the same verification run. These results establish tested scenarios on this specific client stack—not universal compatibility with arbitrary mods or test plans.

## Known scope and limitations

- The current supported and exercised target is Minecraft 1.21.1 on NeoForge 21.1.256 with KubeJS `2101.7.2-build.379`. The historical Forge 1.20.1 release remains separately documented; other versions, loaders, KubeJS builds and mod combinations are not claimed compatible.
- The client E2E tests run in a controlled single-player flat world. Multiplayer, dedicated-server operation, latency, permissions and client/server race behavior have not been established by this suite.
- GUI coverage includes vanilla menus and containers used by the fixtures. Custom mod screens may expose nonstandard widgets or interaction behavior and need their own real-client tests.
- `move().to(...)` is a bounded target follower with local anti-stall behavior; it does **not** plan a route (no A* pathfinding claim).
- The E2E suite uses commands to prepare deterministic fixtures. It then checks actual client-side state and actions; this should not be confused with a test of natural progression without fixtures.
- The project has not yet established broad compatibility or reliability statistics across a representative collection of unrelated mods.

## License

The project is distributed under the MIT License; see [LICENSE](LICENSE). Third-party Minecraft/NeoForge/Forge/KubeJS dependencies retain their own licenses.
