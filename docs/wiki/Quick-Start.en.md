# Quick Start

[简体中文](Quick-Start.md) | English

[← Wiki Home](Home.en.md) · [Installation](Installation.en.md) · [Full API](API-Reference.en.md) · [Step-by-step beginner tutorial (Chinese)](../tutorial/Getting-Started.md)

## Your first status check

Place the following in `kubejs/client_scripts/pw_status.js`:

```js
Playwright.run("status-check", function (page) {
  return page.status().ready(120000)
    .then(function () {
      return page.status().all()
    })
    .then(function (status) {
      console.info("[PW] Minecraft client ready: " + String(status))
      return status
    })
    .catchError(function (error) {
      console.error("[PW] status-check failed: " + String(error))
      throw error
    })
})
```

After entering a world, check `logs/kubejs/client.log`. The check is successful only when the log explicitly shows that it passed; having a script file does not mean the test has run.

## Click the current pause menu using a translation key

```js
Playwright.run("close-pause-menu", function (page) {
  return page.input().press("escape")
    .then(function () {
      return page.gui().waitFor("PauseScreen", 10000)
    })
    .then(function () {
      return page.gui().getByTranslation("menu.returnToGame").waitForEnabled(5000)
    })
    .then(function () {
      return page.gui().getByTranslation("menu.returnToGame").click()
    })
    .then(function () {
      return page.gui().snapshot()
    })
    .then(function (gui) {
      if (String(gui.get("open")) === "true") {
        throw new Error("Expected the pause screen to close; got " + String(gui.get("type")))
      }
      console.info("[PW] menu.returnToGame click closed the current screen")
    })
    .catchError(function (error) {
      console.error("[PW] close-pause-menu failed: " + String(error))
      throw error
    })
})
```

Run this test while in a game world. `escape` opens the actual PauseScreen; `menu.returnToGame` is a language-independent translation key. Finally, the test reads the new GUI state and asserts that the menu has closed. If the widget is missing or the match is not unique, the locator reports an error instead of silently clicking a different widget.

## Script-writing sequence

1. Wait for the client/world to be ready.
2. Query the current state or screen snapshot to establish the preconditions.
3. Locate a specific widget or target.
4. Perform one action and wait for its task to complete.
5. Read the actual game state again for your assertion.
6. Add diagnostic error handling to the entire task chain.

**Do not** synchronously assume that the GUI/world is ready when the script loads, or assume that the game state changed merely because an action task succeeded. See the [API reference](API-Reference.en.md) for more details and [Inventory and Containers](Inventory-and-Containers.en.md) for a chest example.
