# Installation and Compatible Versions

[简体中文](https://github.com/WhiseNT/playwrightforkubejs/wiki/Installation) | English

[← Wiki Home](https://github.com/WhiseNT/playwrightforkubejs/wiki/Home.en) · [Hands-on Tutorial (Chinese)](../tutorial/Getting-Started.md) · [Quick Start](https://github.com/WhiseNT/playwrightforkubejs/wiki/Quick-Start.en)

## Download a Release

The current branch corresponds to the `v0.1.0-mc1.21.1` tag. Download `playwrightforkubejs-0.1.0+mc1.21.1.jar` from the [matching GitHub Release](https://github.com/WhiseNT/playwrightforkubejs/releases/tag/v0.1.0-mc1.21.1); do not download a source ZIP and change its extension.

Place the mod JAR in the target Minecraft instance's `mods/` directory. That instance also needs NeoForge, KubeJS for NeoForge, and its dependencies. This mod JAR does not bundle or replace Minecraft/NeoForge/KubeJS/Rhino. Architectury is not an explicitly declared dependency of the current branch.

The historical Forge 1.20.1 release is still available from the [v0.1.0 download](https://github.com/WhiseNT/playwrightforkubejs/releases/download/v0.1.0/playwrightforkubejs-0.1.0.jar). Its asset is `playwrightforkubejs-0.1.0.jar`; do not mix it with the current NeoForge 1.21.1 asset.

| Component | Current Release Configuration |
| --- | --- |
| Java | 21 |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.256 |
| KubeJS for NeoForge | 2101.7.2-build.379 |
| Rhino | Install according to the dependency requirements of this KubeJS build |

Install KubeJS dependencies as required by the relevant modpack or download page. All dependencies belong in the same Minecraft instance. If NeoForge reports missing mods, resolve dependencies and versions first; do not try substituting JARs intended for another loader.

### Optional: Launch an Installed Instance with NTLauncher CLI

If an agent has no Gradle development environment but you already have a preconfigured Windows NTLauncher instance, you can launch it with [NTLauncher CLI](https://ntlaunch.cn/). First install the compatible environment listed above, this mod, and the client test scripts in that instance, and configure Java and an account. The CLI does not install dependencies or deploy the mod for you. See the [NTLauncher documentation](https://ntlaunch.cn/docs) for launch commands and JSON output. The launcher only starts Minecraft; you must still check subsequent KubeJS/client logs or script results to determine whether the tests passed.

## Install Your Own Test Scripts

Place scripts under the instance directory:

```text
kubejs/client_scripts/my_test.js
```

Ordinary usage only needs the public `Playwright` binding. `PlaywrightTest` is a test fixture API exclusively for this repository's E2E tests. It is not registered unless the test-run property is enabled, and it is not a general-purpose scripting API.

For an initial check, copy the [minimal smoke script](../../examples/kubejs/client_scripts/playwright_smoke.js), enter a world, and inspect `logs/kubejs/client.log`. A step-by-step installation and execution tutorial is available [here (Chinese)](../tutorial/Getting-Started.md).

## Build from Source

You need Java 21 and network access to download Gradle/Maven dependencies. Run from the repository root:

```bash
./gradlew clean build
```

Windows:

```powershell
.\gradlew.bat clean build
```

The output JAR is `build/libs/playwrightforkubejs-0.1.0+mc1.21.1.jar`. Build verification does not automatically place the JAR in a player's instance; copy it manually into that instance's `mods/` directory when installing.

## Support Boundaries

Verified versions are determined by the contents of the corresponding release tag. The current tested scope is a Minecraft 1.21.1 / NeoForge 21.1.256 single-player client; the historical `v0.1.0` release targets Forge 1.20.1. Fabric, unverified Minecraft/KubeJS combinations, dedicated servers, multiplayer networking, and arbitrary modded screens are outside the compatibility guarantee. When updating Minecraft or the loader, run real GUI tests against your own combination of mods.

The public Playwright API aims to preserve method names, parameters, return values, and error semantics across versions, with underlying adapters handling version differences. Native commands, NBT, Java access, and KubeJS-specific features may differ. Centralized test fixture adaptation is a planned direction; a unified cross-version fixture has not yet been implemented, and this does not mean all scripts are already compatible.
