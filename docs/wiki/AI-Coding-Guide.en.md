# Guide for AI / Automated Coding Assistants

[简体中文](https://github.com/WhiseNT/playwrightforkubejs/wiki/AI-Coding-Guide) | English

[← Wiki Home](https://github.com/WhiseNT/playwrightforkubejs/wiki/Home.en) · [API Reference](https://github.com/WhiseNT/playwrightforkubejs/wiki/API-Reference.en) · [Async Tasks](https://github.com/WhiseNT/playwrightforkubejs/wiki/Async-Tasks-and-Errors.en) · [E2E Acceptance Testing](https://github.com/WhiseNT/playwrightforkubejs/wiki/E2E-Testing.en)

This page helps AI assistants and humans work together to write runnable, auditable KubeJS tests. Although the project name includes Playwright, its runtime is not a Playwright browser/page and does not use a browser DOM.

## Project Conventions for Code Generators

Before generating code for the current release:

1. Confirm the target Minecraft/NeoForge/KubeJS versions and current tag.
2. Open this Wiki's API pages and example scripts. If versions conflict, use the source signatures in the current checkout as the authority.
3. Use only implemented `Playwright` / `PlaywrightTask` / PageApi / LocatorApi methods. Do not guess browser APIs or invent `page.goto`, DOM support, ARIA auto-waiting, or browser network contexts.
4. Return every dependent action/wait/assertion in the `.then(...)` chain. Do not treat tasks as synchronous return values.
5. Give each test a finite timeout, a unique run ID, stage-specific error messages, and assertions against actual post-action state.
6. Prefer translation keys when locating vanilla GUI controls. For custom screens, first check the type, control tree, and menu slots in an actual `gui().snapshot()`.
7. Collect actual state evidence supporting every claimed result. An action returning `{clicked:true}` does not prove a server-side item transfer; a missing entity does not prove it was killed; reaching a point with `move.to` does not prove path planning.
8. Preserve failure and cancellation semantics. Never catch an exception and then output PASS. Do not skip validators or negative tests.
9. Run the relevant Python/JUnit/JS syntax checks after writing the code. For world-related behavior, run the opt-in real-client E2E tests.
10. Clearly separate “what was implemented,” “which versions/modes were verified,” and “which boundaries remain untested” in reports.
11. If no Gradle development environment is available but a configured NTLauncher instance exists, use [NTLauncher CLI](https://ntlaunch.cn/docs) as an optional external launcher. First check that the instance's `install_state`, Java, account, mods, and scripts are ready.
12. Do not equate a successful CLI launch with passing tests. Confirm that assertions completed using KubeJS/client logs or structured test results, and check the CLI exit code and error output. Foreground `launch` waits until the game exits.

## Current Branch and Cross-Version Strategy

The current `mc-1.21.1` release targets `v0.1.0-mc1.21.1` / `0.1.0+mc1.21.1`, using MC 1.21.1, NeoForge 21.1.256, Java 21, and KubeJS `2101.7.2-build.379`. Install Rhino according to this KubeJS build's dependency requirements. Do not invent a version or list Architectury as an explicitly declared dependency of the current branch. Select the historical Forge 1.20.1 `v0.1.0` release separately.

The public Playwright API aims to preserve method names, parameters, return values, and error semantics across versions, with underlying code handling version differences. Native commands, NBT, Java access, and KubeJS-specific features may differ. Centralized test fixture adaptation is a planned direction; a unified cross-version fixture has not yet been implemented. Passing clients in two languages or a single 64-stage PASS does not justify claiming universal compatibility, a completed compatibility layer, or that intermittent failures can never recur.

## Launch Workflow without a Development Environment (Optional)

NTLauncher CLI can help an agent launch an **already fully configured** Windows Minecraft instance when no Gradle debugging environment is available. It does not build this project, install NeoForge/KubeJS/this mod, copy scripts, or provide this mod's in-game automation API. Those capabilities still come from Playwright For KubeJS preinstalled in the target instance.

Recommended steps:

1. List instances with `ntlauncher-cli.exe list-instances --json`, select an ID, and confirm its `install_state` is `Installed`.
2. Confirm that the instance's Java, account, Minecraft/NeoForge/KubeJS dependencies, this mod, and client scripts are configured.
3. Launch the instance according to the [NTLauncher CLI documentation](https://ntlaunch.cn/docs). `launch --instance-id <ID> --json` is a blocking foreground command that returns only after the game exits. For background operation, launch a separate CLI process and manage it with `status`/`stop`.
4. Confirm script execution, passing assertions, and error status in game logs or an explicit test result file. A successful CLI response only proves that its launch workflow completed, not that client test cases passed.
5. Set bounded timeouts for launching and testing. On failure, retain CLI error output and relevant Minecraft/KubeJS logs. Do not unconditionally terminate an instance the player already has running.

This is an optional launch channel for an existing instance, not equivalent to the repository's isolated Gradle E2E tests, and it does not establish verification on non-Windows platforms.

## Task Template to Copy into an AI Assistant

```text
Write a KubeJS client_scripts test for the currently checked-out version of Playwright For KubeJS.
First check docs/wiki/API-Reference.en.md, the corresponding PageApi/LocatorApi source, and existing examples. Do not guess interfaces.
Use Playwright.run and .then chains that return PlaywrightTask; all waits must be finite.
Verify preconditions, perform a real action, then query the final client/menu/world state for assertions.
Failures must propagate and include the stage and actual snapshot. Never treat command results or accepted actions as evidence of in-game success.
Finally, explain runtime prerequisites, world state that may be affected, tests to run, and compatibility boundaries that remain unverified.
```

## Evidence Design Checklist

- **Stable identity:** Match the same entity ID + UUID before and after combat, not just its name.
- **Accurate quantities:** Distinguish player inventory indices from current GUI menu indices; record `carried` before and after a click.
- **Actual screens:** Confirm the actual screen class, title, container/menu slot count, and whether the GUI is open.
- **Physical effects:** Prove action results by reading final world/inventory/health/position/durability state.
- **Boundary errors:** Check stable error codes and assert that input is released after failure/cancellation.
- **Timing and generations:** For multiple tasks, record ordering, timeMs, and script generation. Old callbacks must not execute after a reload.
- **Negative tests:** Add fabricated success, missing fields, wrong targets, out-of-order events, duplicate events, and similar cases to evidence validator tests.

## Unverified Claims You Must Not Make

Do not call this project official Playwright; do not call `move().to(...)` A*; do not generalize single-player flat-world vanilla E2E results into cross-mod, multiplayer, or cross-version compatibility; do not describe fixture preparation using commands as a purely natural survival workflow. See [compatibility boundaries](https://github.com/WhiseNT/playwrightforkubejs/wiki/Home.en#applicable-versions-and-evidence-boundaries) for further constraints.
