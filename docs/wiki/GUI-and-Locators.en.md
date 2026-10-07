# GUI and Locators

[简体中文](GUI-and-Locators.md) | English

[← Wiki Home](Home.en.md) · [API Reference](API-Reference.en.md) · [Quick Start](Quick-Start.en.md) · [Troubleshooting](Troubleshooting.en.md)

## Query before providing input

```js
page.gui().snapshot().then(function (gui) {
  console.info("screen=" + gui.get("type") + ", open=" + gui.get("open"))
  var widgets = gui.get("widgets")
  for (var i = 0; i < widgets.size(); i++) {
    var item = widgets.get(i)
    console.info(item.get("id") + " " + item.get("role") +
      " translation=" + item.get("translationKey") + " text=" + item.get("text"))
  }
})
```

Every GUI locator query rereads the current screen tree. Do not reuse widget paths across screens if they were read before the screen changed.

## Selector syntax

| Selector | When to use it | Matching semantics |
| --- | --- | --- |
| `translation=menu.singleplayer` | A vanilla/KubeJS widget has a translation key | Matches by key, independently of the UI language; composite text may contain multiple keys |
| `gui-text=单人游戏` | The text is fixed and the language is known | Matches displayed text exactly, without fuzzy substring searching |
| `role=button` | Widgets need to be filtered by role | Available roles: button, textbox, tab, world, list, group, widget |
| `role=button|name=...` | Narrowing the match when multiple buttons share a screen | name is URL-encoded displayed text; prefer `getByRole(role,name)` for automatic encoding |
| `widget=root/0/2` | A unique path has been observed in a snapshot | Tied to the current screen's widget tree structure, which may change across versions/languages |
| `world=save-folder` | The Select World list | Matches an exact save ID or a uniquely matched displayed world name |

Recommended usage:

```js
var returnButton = page.gui().getByTranslation("menu.returnToGame")
returnButton.waitForEnabled(5000)
  .then(function () { return returnButton.click() })
```

`page.gui().getByText(text)` is an exact-text GUI locator; `page.getByText(text)` retains the legacy `text=` chat-history semantics. For GUI queries, prefer `gui().getByText`, `getByTranslation`, or an explicit selector to avoid treating chat text as widget text.

## Rules for reliable interaction

1. Wait for the target screen first: `page.gui().waitFor("PauseScreen", 10000)`.
2. Locate by translation key or role/name; do not guess menu buttons using unstable array indices.
3. If the match should be unique on the screen, use `.count()` to assert the count before clicking.
4. For a widget that needs text input, first confirm that its role is `textbox`, then use the locator's `.fill(text)`.
5. After click/fill, take another snapshot or wait for the next screen/game state to verify the actual result.

Locator `click()` explicitly fails on zero or multiple matches. Hidden or disabled widgets are not actionable either. `fill()` accepts only actual GUI EditBox widgets. If the input is filtered or truncated, it fails instead of reporting a false success.

## Coordinates and languages

Screen snapshot bounds use the `gui-scaled` coordinate system, not physical window pixels. Prefer locators to absolute coordinates. For multilingual gameplay, translation keys are generally more robust than displayed text. For custom mod screens, first check whether they actually expose a queryable vanilla GUI widget tree.

## Screen class names

`gui().waitFor(screenType, timeoutMs)` waits for the actual screen class's simple name/class name. For example, in this project's historical Minecraft 1.20.1 client acceptance testing, a vanilla chest was exposed as the generic `ContainerScreen` (with the title Chest), not a class named `ChestScreen`. Before writing a test, use a snapshot to verify the actual screen type in the target version; do not infer a Java class name from the screen title.
