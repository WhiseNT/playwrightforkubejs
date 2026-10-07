# Troubleshooting

[简体中文](https://github.com/WhiseNT/playwrightforkubejs/wiki/Troubleshooting) | English

[← Wiki Home](https://github.com/WhiseNT/playwrightforkubejs/wiki/Home.en) · [Installation](https://github.com/WhiseNT/playwrightforkubejs/wiki/Installation.en) · [Async Tasks and Errors](https://github.com/WhiseNT/playwrightforkubejs/wiki/Async-Tasks-and-Errors.en) · [GUI Locators](https://github.com/WhiseNT/playwrightforkubejs/wiki/GUI-and-Locators.en)

Read the **first** Playwright/KubeJS error and its corresponding stage before looking at the final chain of cascading errors. Logs are usually in the instance's `logs/kubejs/client.log` and `logs/latest.log`.

| Error / Symptom | Common Cause | Troubleshooting Steps |
| --- | --- | --- |
| `Playwright is not defined` | The mod did not load, it was installed in the wrong instance, or the script was placed in server scripts | Check for missing dependencies during the current NeoForge startup; confirm that both the JAR and KubeJS are in the current client instance's `mods/`; place scripts in `kubejs/client_scripts/` |
| `NOT_IN_WORLD` | The action needs a player/world, but the script is still at the title menu/loading screen | First call `page.status().ready(timeoutMs)`, then read world state after `.then()` |
| `TIMEOUT` | The screen/state/locator did not appear within the deadline, or the client stalled | Check the timed-out stage and its `gui().snapshot()`; verify the actual screen type/prerequisites, then set a reasonable finite timeout |
| `GUI_NOT_OPEN` | The container/menu has not opened yet, or the screen has changed | Wait for the actual GUI screen first, then query slots on the current screen |
| `INVALID_ACTION` / locator matched 0 | The selector does not match, or the control is not actionable | Print the actual `translationKey`, role, text, visible, and enabled values from the GUI snapshot; use a precise selector |
| locator matched >1 | The selector is not unique | Narrow the target using translation, role+name, or an actual widget path from the current snapshot |
| `SLOT_OUT_OF_RANGE` | The wrong menu/inventory slot layout was used | Determine indices from the current `gui().snapshot().get("slots")`; do not mix crafting table/chest/player inventory numbering |
| Items appear not to move | Only the action return value was checked without waiting for server state synchronization, or the target slot is wrong | Poll snapshots for the source slot, target slot, and `carried`; then close and reopen the container to confirm the server saved the state |
| `SCRIPT_RELOADED` | The async task belongs to a previous KubeJS client-script generation | This is expected protection after a reload; reacquire the page and start new tasks in the new script scope |
| `CANCELLED` | A bounded task was explicitly cancelled or cancelled by its parent chain | Check whether cancellation is an expected test result or error handling; confirm that movement/input has been released |
| `PATHFINDING_FAILED` | Bounded movement did not reach the target or stopped within its time limit | This mod's `move.to` follows a target with local anti-stuck behavior; it is not path planning. Check the character, obstacles, and target first. Do not misdiagnose this error as an A* path failure |

## Issues Fixed on the Current 1.21.1 Branch

On 2026-10-07, fixes addressed `registerBindings` incorrectly clearing async tasks (old-generation cleanup now happens in `beforeScriptsLoaded`), as well as summoned-entity attribute NBT and random knockback from source-less `damage` during preparation contaminating the initial combat position. If similar symptoms recur, retain the reload sequence, positions before and after combat, and the first failing stage. The latest single English test passing all 64 required stages is not reliability statistics and does not guarantee that intermittent failures can never recur.

## Collect Minimal Diagnostic Information

When reporting to maintainers/AI, include Minecraft/loader (NeoForge on the current branch)/Java/KubeJS/Rhino/mod versions, versions of other installed dependencies, language, the exact error code and complete first exception, current screen type, a minimal reproduction script, and observed state before and after assertions. Remove Microsoft/Mojang login data, your complete personal `.minecraft` directory, access tokens, and private world files.

## Safety Checks before Reporting

- Do not publish `launcher_accounts.json`, `usercache.json`, server addresses/passwords, or access tokens.
- Reports in `run/e2e/` contain local absolute paths; review and redact them before sharing.
- For reproducible vanilla issues, provide steps in a clean world, screenshots, and a minimal script. For custom-mod issues, also provide the exact mod list and versions.
