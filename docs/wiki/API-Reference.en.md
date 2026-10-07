# API Reference

[简体中文](https://github.com/WhiseNT/playwrightforkubejs/wiki/API-Reference) | English

[← Wiki Home](https://github.com/WhiseNT/playwrightforkubejs/wiki/Home.en) · [Quick Start](https://github.com/WhiseNT/playwrightforkubejs/wiki/Quick-Start.en) · [GUI and Locators](https://github.com/WhiseNT/playwrightforkubejs/wiki/GUI-and-Locators.en) · [Containers](https://github.com/WhiseNT/playwrightforkubejs/wiki/Inventory-and-Containers.en)

This reference covers the current `mc-1.21.1` branch (release target: `v0.1.0-mc1.21.1`). For details, consult the Java API in the corresponding tag and the actual client behavior. All namespaces are provided by `page`, and all actions/queries return a `PlaywrightTask` that supports asynchronous chaining.

The public Playwright API aims to retain the same method names, parameters, return values, and error semantics across versions, with Minecraft differences handled by the underlying adapters. This does not guarantee consistency for native commands, NBT, Java access, or KubeJS version-specific features, nor does it mean that unified cross-version test fixtures have been implemented. See [Installation](https://github.com/WhiseNT/playwrightforkubejs/wiki/Installation.en) for environment dependencies.

## Root objects

| Call | Purpose |
| --- | --- |
| `Playwright.client()` | Get the client PlaywrightClient |
| `Playwright.client().page()` | Get the client PageApi |
| `Playwright.run(name, function(page) {...})` | Execute a task chain on the client dispatcher |
| `Playwright.expect(value)` | Create an asynchronous expectation |
| `Playwright.reset()` | Explicitly reset the script generation, invalidate old tasks, and release input |

## PageApi and Locator

| Call | Purpose |
| --- | --- |
| `page.locator(selector)` | Create a LocatorApi using a selector |
| `page.getByGuiText(text)` | Match the current GUI's displayed text exactly |
| `page.getByTranslation(key)` | Match a translation key in the current GUI |
| `page.getByRole(role[, name])` | Locate by GUI role and optional displayed name |
| `page.getByWorld(nameOrId)` | Locate an entry in the world list |
| `page.getByWidget(path)` | Locate by widget path in the current screen |
| `page.getBySlot(index)` | Locate a menu slot in the current container menu |
| `page.getByItem(itemId)` / `getByEntity(type)` | Create a read-only item/entity query locator |
| `page.waitUntil(predicate, timeoutMs)` | Poll a synchronous boolean predicate within a bounded time limit |
| `page.waitForTimeout(ms)` | Wait for the specified number of milliseconds; prefer state-based conditions over fixed sleeps |
| `page.waitForChat(match, timeoutMs)` | Wait for a match in chat history |
| `page.waitForGui(timeoutMs)` | Wait for a GUI to open |

Locator supports `snapshot()`, `count()`, `textContent()`, `click()`, `fill(text)`, `waitFor(state, timeoutMs)`, `waitForVisible`, `waitForEnabled`, and `screenshot(output)`. Wait states are `attached`, `visible`, `enabled`, `hidden`, or `detached`. `click()` accepts only GUI widgets/menu slots; `fill()` accepts only editable text boxes.

## GUI and containers

`page.gui()` provides: `waitFor(screenType, timeoutMs)`, `waitForOpen(timeoutMs)`, `waitForUpdate(timeoutMs)`, `snapshot()`, `info()`, `layout()`, `slot(index)`, `click(slot[, button])`, `drag(slots, button)`, and `close()`.

When the current screen is an `AbstractContainerScreen`, its snapshot additionally includes `containerId`, the menu `slots` list, and the `carried` cursor item. Menu slot indices and player inventory slot indices are not the same concept; use the menu slots observed on the current screen.

## Game state and actions

| Namespace | Representative methods |
| --- | --- |
| `page.status()` | `ready(timeoutMs)`, `all()`, `position()`, `rotation()`, `health()`, `world()` |
| `page.inventory()` | `snapshot()`, `slot(index)`, `held()`, `equipment()`, `waitFor(item, timeoutMs)`, `selectHotbar(slot)`, `use()`, `swapHands()` |
| `page.block()` | `get(position)`, `breakBlock(position)`, `place(position, face)`, `interact(position[, face])` |
| `page.entity()` | `list(radius)`, `info(id)`, `attack(target)`, `interact(target)`, `mount(target)`, `dismount()` |
| `page.input()` | `press(key)`, `hold(key, durationMs)`, `combo(keys)`, `down(key)`, `up(key)`, `keysDown()`, `click(button)`, `type(text)` |
| `page.move()` | `direction(direction, blocks)`, `to({x,y,z}[, timeoutMs])`, `jump()`, `sneak(enabled)`, `sprint(enabled)` |
| `page.look()` | `set(yaw,pitch)`, `at(position)`, `entity(filter)`, `current()` |
| `page.chat()` | `send(message)`, `command(command)`, `history(last)`, `waitFor(match, timeoutMs)` |
| `page.screenshot()` | `capture(output)` |
| `page.waitApi()` | `ticks(ticks)`, `timeout(ms)`, `event(name, timeoutMs)`, `guiOpen(timeoutMs)` |

An entity target can be an integer ID or a nonempty filter map (with filters such as `type`, `name`, and `maxDistance`). Verify specific error codes and returned Map fields against the corresponding code and game version.

## Expect

Public expectations: `toBeTruthy()`, `toEqual(expected)`, `toHaveText(expected)`, `toHaveCount(count)`, `toHaveItem(itemId)`, and `toBeVisible()`. They return tasks; return them from the `.then(...)` chain so that assertions are awaited and failures propagate.

## Values and threads

Java `Map` objects exposed through Rhino can be read with `value.get("key")`. Follow a Promise-style pattern for asynchronous operations: return tasks and chain reads in sequence. Do not treat asynchronous return values as synchronous Maps. GUI input runs on the client main thread. Old screen locators may become invalid during a screen change; query again after the transition.
