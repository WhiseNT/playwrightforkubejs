# Playwright For KubeJS Wiki

[简体中文](https://github.com/WhiseNT/playwrightforkubejs/wiki/Home) | English

Welcome! This Wiki is for modpack players, KubeJS script authors, mod developers and coding assistants, including first-time users.

> **At a glance:** The current `mc-1.21.1` branch provides a client-side KubeJS automation and testing API for Minecraft 1.21.1 / NeoForge. It covers GUIs, locators, player state, inventory/menu slots, input and bounded asynchronous tasks. It is not browser automation, is not the official Playwright project, and does not provide full A* pathfinding.

## Start with your goal

| What you want to do | Read first |
| --- | --- |
| Install the mod and run your first test | [Installation and compatibility](https://github.com/WhiseNT/playwrightforkubejs/wiki/Installation.en) → [Step-by-step tutorial (Chinese)](../tutorial/Getting-Started.md) |
| Have an agent launch an installed Minecraft instance without a Gradle environment | [Optional NTLauncher CLI workflow](https://github.com/WhiseNT/playwrightforkubejs/wiki/E2E-Testing.en) |
| Understand task chains in five minutes | [Quick start](https://github.com/WhiseNT/playwrightforkubejs/wiki/Quick-Start.en) |
| Find GUI buttons, text fields and widgets | [GUIs and locators](https://github.com/WhiseNT/playwrightforkubejs/wiki/GUI-and-Locators.en) |
| Read inventory, chest and crafting-table menu slots | [Inventory and containers](https://github.com/WhiseNT/playwrightforkubejs/wiki/Inventory-and-Containers.en) |
| Handle waits, failures, cancellation and reloads | [Asynchronous tasks and errors](https://github.com/WhiseNT/playwrightforkubejs/wiki/Async-Tasks-and-Errors.en) |
| Run the real-client acceptance suite | [E2E testing](https://github.com/WhiseNT/playwrightforkubejs/wiki/E2E-Testing.en) |
| Have AI help write scripts without inventing APIs | [AI coding guide](https://github.com/WhiseNT/playwrightforkubejs/wiki/AI-Coding-Guide.en) |
| Diagnose failed scripts or unmatched locators | [Troubleshooting](https://github.com/WhiseNT/playwrightforkubejs/wiki/Troubleshooting.en) |
| Look up a concise index of namespace methods | [API reference](https://github.com/WhiseNT/playwrightforkubejs/wiki/API-Reference.en) |

## Applicable versions and evidence boundaries

The current release is `0.1.0+mc1.21.1` (tag `v0.1.0-mc1.21.1`): Java 21, Minecraft 1.21.1, NeoForge 21.1.256 and KubeJS `2101.7.2-build.379`. Install Rhino as required by that KubeJS build; Architectury is not an explicit dependency of this branch. The historical `v0.1.0` release is for Forge 1.20.1. Do not mix these JARs. Use the README at the JAR's release tag as the release-specific reference.

On October 7, 2026, English and Simplified Chinese client runs passed on the current branch. The latest single English run, `diag-knockback-fix`, passed all 64 required stages with zero validator errors in 107 seconds; reliability statistics have not been established. See [E2E testing](https://github.com/WhiseNT/playwrightforkubejs/wiki/E2E-Testing.en). Public API method names, parameters, return values and error semantics should remain consistent across versions where practical. Native commands, NBT, Java access and KubeJS features may differ; unified cross-version test fixtures have not yet been implemented.

Current real-client coverage includes English/Chinese UI, menus, inventory and real crafting-table recipes, vanilla chest slot transfers and close/reopen/save/rejoin persistence, equipment, combat, bounded movement, reloads and input release. Tests use a single-player flat world and commands to prepare controlled fixtures. Custom mod screens, other loaders/versions, multiplayer and actual modpack combinations still require separate validation.

## Navigation

- [Installation](https://github.com/WhiseNT/playwrightforkubejs/wiki/Installation.en) · [Quick start](https://github.com/WhiseNT/playwrightforkubejs/wiki/Quick-Start.en) · [API index](https://github.com/WhiseNT/playwrightforkubejs/wiki/API-Reference.en)
- [GUI locators](https://github.com/WhiseNT/playwrightforkubejs/wiki/GUI-and-Locators.en) · [Containers](https://github.com/WhiseNT/playwrightforkubejs/wiki/Inventory-and-Containers.en) · [Async tasks](https://github.com/WhiseNT/playwrightforkubejs/wiki/Async-Tasks-and-Errors.en)
- [Test suite](https://github.com/WhiseNT/playwrightforkubejs/wiki/E2E-Testing.en) · [AI guide](https://github.com/WhiseNT/playwrightforkubejs/wiki/AI-Coding-Guide.en) · [Troubleshooting](https://github.com/WhiseNT/playwrightforkubejs/wiki/Troubleshooting.en)
- [Step-by-step tutorial (Chinese)](../tutorial/Getting-Started.md) · [Repository README](../../README.md)
