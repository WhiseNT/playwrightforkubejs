# Playwright For KubeJS Wiki

[简体中文](Home.md) | English

Welcome! This Wiki is for modpack players, KubeJS script authors, mod developers and coding assistants, including first-time users.

> **At a glance:** The current `mc-1.21.1` branch provides a client-side KubeJS automation and testing API for Minecraft 1.21.1 / NeoForge. It covers GUIs, locators, player state, inventory/menu slots, input and bounded asynchronous tasks. It is not browser automation, is not the official Playwright project, and does not provide full A* pathfinding.

## Start with your goal

| What you want to do | Read first |
| --- | --- |
| Install the mod and run your first test | [Installation and compatibility](Installation.en.md) → [Step-by-step tutorial (Chinese)](../tutorial/Getting-Started.md) |
| Have an agent launch an installed Minecraft instance without a Gradle environment | [Optional NTLauncher CLI workflow](E2E-Testing.en.md) |
| Understand task chains in five minutes | [Quick start](Quick-Start.en.md) |
| Find GUI buttons, text fields and widgets | [GUIs and locators](GUI-and-Locators.en.md) |
| Read inventory, chest and crafting-table menu slots | [Inventory and containers](Inventory-and-Containers.en.md) |
| Handle waits, failures, cancellation and reloads | [Asynchronous tasks and errors](Async-Tasks-and-Errors.en.md) |
| Run the real-client acceptance suite | [E2E testing](E2E-Testing.en.md) |
| Have AI help write scripts without inventing APIs | [AI coding guide](AI-Coding-Guide.en.md) |
| Diagnose failed scripts or unmatched locators | [Troubleshooting](Troubleshooting.en.md) |
| Look up a concise index of namespace methods | [API reference](API-Reference.en.md) |

## Applicable versions and evidence boundaries

The current release is `0.1.0+mc1.21.1` (tag `v0.1.0-mc1.21.1`): Java 21, Minecraft 1.21.1, NeoForge 21.1.256 and KubeJS `2101.7.2-build.379`. Install Rhino as required by that KubeJS build; Architectury is not an explicit dependency of this branch. The historical `v0.1.0` release is for Forge 1.20.1. Do not mix these JARs. Use the README at the JAR's release tag as the release-specific reference.

On October 7, 2026, English and Simplified Chinese client runs passed on the current branch. The latest single English run, `diag-knockback-fix`, passed all 64 required stages with zero validator errors in 107 seconds; reliability statistics have not been established. See [E2E testing](E2E-Testing.en.md). Public API method names, parameters, return values and error semantics should remain consistent across versions where practical. Native commands, NBT, Java access and KubeJS features may differ; unified cross-version test fixtures have not yet been implemented.

Current real-client coverage includes English/Chinese UI, menus, inventory and real crafting-table recipes, vanilla chest slot transfers and close/reopen/save/rejoin persistence, equipment, combat, bounded movement, reloads and input release. Tests use a single-player flat world and commands to prepare controlled fixtures. Custom mod screens, other loaders/versions, multiplayer and actual modpack combinations still require separate validation.

## Navigation

- [Installation](Installation.en.md) · [Quick start](Quick-Start.en.md) · [API index](API-Reference.en.md)
- [GUI locators](GUI-and-Locators.en.md) · [Containers](Inventory-and-Containers.en.md) · [Async tasks](Async-Tasks-and-Errors.en.md)
- [Test suite](E2E-Testing.en.md) · [AI guide](AI-Coding-Guide.en.md) · [Troubleshooting](Troubleshooting.en.md)
- [Step-by-step tutorial (Chinese)](../tutorial/Getting-Started.md) · [Repository README](../../README.md)
