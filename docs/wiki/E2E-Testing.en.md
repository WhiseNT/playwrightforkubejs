# Real-Client E2E Testing

[简体中文](E2E-Testing.md) | English

[← Wiki Home](Home.en.md) · [Container Examples](Inventory-and-Containers.en.md) · [Async Errors](Async-Tasks-and-Errors.en.md) · [AI Coding Guide](AI-Coding-Guide.en.md)

## What the Different Types of Tests Prove

1. `./gradlew test`: Headless Java/JUnit contract tests. They do not start a full Minecraft client.
2. `python -m unittest discover -s examples -p 'test_client_e2e.py'`: Unit tests for the Python evidence validator. Fabricated, tampered, missing, and out-of-order events confirm that the validator rejects bad evidence. These tests do not simulate Minecraft.
3. `examples/run_client_e2e.py --suite`: Starts two fresh graphical clients, running `en_us`/GUI scale 2 and `zh_cn`/scale 3 respectively, and actually exercises UI, input, game state, containers, and save workflows.

Do not substitute Python validator unit tests for in-game tests, or count a client case as successful when the GUI has not yet been clicked or the script has only just loaded.

## Environment Requirements

- Java 21.
- The current `mc-1.21.1` branch targets Minecraft 1.21.1, NeoForge 21.1.256, KubeJS `2101.7.2-build.379`, and the Rhino dependency required by that KubeJS build.
- Python 3 and `psutil` (`python -m pip install psutil`).
- A usable graphical desktop and GPU/drivers capable of running the Minecraft client.
- Gradle 8.8. By default, the runner looks for the Gradle 8.8 wrapper cache; supply `--gradle` if it cannot find it.
- Network access for the initial dependency download. With a complete cache, the default offline mode is available.

## Run the Full Acceptance Suite

```bash
python examples/run_client_e2e.py \
  --suite \
  --java-home "/path/to/jdk-21" \
  --gradle "/path/to/gradle-8.8/bin/gradle" \
  --timeout 600
```

Windows PowerShell example (replace these with actual local paths):

```powershell
$env:JAVA_HOME = "C:\Path\To\jdk-21"
python examples/run_client_e2e.py `
  --suite `
  --java-home $env:JAVA_HOME `
  --gradle "C:\Path\To\gradle-8.8\bin\gradle.bat" `
  --timeout 600
```

`--online` allows Gradle to download missing dependencies. For a single configuration, you can supply `--run-id`, `--language en_us|zh_cn`, and `--gui-scale 1..4`. Each E2E run requires a new directory and does not overwrite an existing run ID; the suite's base run-id is limited to 22 characters.

## Without Gradle: Launch an Existing Instance with NTLauncher CLI (Optional)

If the agent's environment has no Gradle development/debugging setup but a fully configured NTLauncher Minecraft instance exists, use [NTLauncher CLI](https://ntlaunch.cn/) to launch that instance. NTLauncher CLI handles launching and process management; this mod provides in-game automation APIs only after the Minecraft client loads. This route does not build the mod, install NeoForge/KubeJS/dependencies, copy test scripts, or automatically determine whether mod tests passed.

Prerequisites: NTLauncher CLI currently provides a Windows executable. The target instance must be fully installed (`install_state == "Installed"`), have working Java and an account configured, and already contain compatible NeoForge, KubeJS dependencies, this mod's JAR, and test scripts under `kubejs/client_scripts/`.

Basic commands:

```powershell
$instances = & .\ntlauncher-cli.exe list-instances --json | ConvertFrom-Json
$instanceId = 1 # Replace with the target instance ID from list-instances output
$instance = $instances.instances | Where-Object { $_.id -eq $instanceId } | Select-Object -First 1
if (-not $instance -or $instance.install_state -ne "Installed") {
  throw "The target instance does not exist or is not fully installed"
}
& .\ntlauncher-cli.exe launch --instance-id $instanceId --json
if ($LASTEXITCODE -ne 0) { throw "The NTLauncher/Minecraft launch workflow failed" }
```

`launch` blocks in the foreground until Minecraft exits. If an agent needs to interact with a running client, launch it in a separate process according to the [official CLI documentation](https://ntlaunch.cn/docs), then call `status`/`stop` as needed. Set a reasonable timeout for each CLI process. If launching fails, inspect errors in CLI stderr and recent Minecraft logs. A successful CLI return only means the launch workflow completed (usually after the game exits), not that Playwright tests passed. Test conclusions must come from `logs/kubejs/client.log`, Minecraft logs, or explicit structured results produced by the script. This is an optional way to launch an existing instance, not a replacement for the isolated Gradle E2E runner above.

## Where Reports Are Stored

Each run is saved under `run/e2e/<run-id>/`, with evidence in `test-results/`:

- `report.json`: Final PASS/FAIL, versions, required stages, and validation errors.
- `events.jsonl`: Per-stage state, run ID, language, GUI scale, and measured evidence.
- `gradle.log`: Output from the Gradle/client process launched by the runner.
- `*.png`: Actual framebuffer screenshots.
- `saves/PW_E2E_<run-id>/`: The isolated test world.

`run/` is ignored by Git; do not commit local saves or reports to the public source repository. Before sharing release acceptance archives, remove local usernames, absolute paths, and unrelated personal data.

## Current Branch Run Evidence (2026-10-07)

Both `en_us` and `zh_cn` clients have passed on Minecraft 1.21.1 / NeoForge 21.1.256 / Java 21 / KubeJS `2101.7.2-build.379`. In the latest single English `diag-knockback-fix` run, all 64 required stages passed with zero validation errors in 107 seconds. This is one measured run, not reliability statistics, and does not justify promising that intermittent failures can never recur.

This fix moved script-generation cleanup from `registerBindings` to `beforeScriptsLoaded` to prevent binding registration from mistakenly clearing newly created tasks. It also adjusted summoned-entity attribute NBT to prevent random knockback caused by source-less `damage` during preparation from contaminating the initial combat position. Historical bilingual Forge 1.20.1 acceptance records remain in the [README](../../README.md); they are not evidence of complete compatibility for the current version.

## Fixtures and Assertions

Test scripts may use commands to prepare fixed terrain/items, but must then assert actual in-game outcomes through the public Playwright API. For example, the chest test checks: a real `ContainerScreen`, 63 menu slots, an emerald on the cursor after taking it from a player slot, changed contents and an empty cursor after clicking a chest slot, and persistent contents after closing/reopening the chest and saving/rejoining. Only state observed on the server/client counts as behavioral evidence.

The public Playwright API aims to preserve method names, parameters, return values, and error semantics across versions, with underlying adapters handling version differences. Native commands, NBT, Java, and KubeJS-specific features may differ. Centralized test fixture adaptation is a future direction; a unified cross-version fixture has not yet been implemented, so existing scripts must not be treated as universally compatible.

The current suite demonstrates fixed vanilla/controlled single-player workflows, not general compatibility with arbitrary modded screens, multiplayer networking, other loaders, or other versions.
