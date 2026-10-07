# Inventory and Containers

[简体中文](https://github.com/WhiseNT/playwrightforkubejs/wiki/Inventory-and-Containers) | English

[← Wiki Home](https://github.com/WhiseNT/playwrightforkubejs/wiki/Home.en) · [API Reference](https://github.com/WhiseNT/playwrightforkubejs/wiki/API-Reference.en) · [GUI and Locators](https://github.com/WhiseNT/playwrightforkubejs/wiki/GUI-and-Locators.en) · [E2E Testing](https://github.com/WhiseNT/playwrightforkubejs/wiki/E2E-Testing.en)

## Two kinds of slot indices

- `page.inventory().slot(index)` reads a player inventory index (including hotbar/armor/offhand mappings).
- `page.gui().slot(index)` reads a slot index in the **current AbstractContainerScreen menu**.
- `page.gui().snapshot().get("slots")` is the current menu's slot list; each entry contains the slot index, item, count, and screen geometry.
- `page.gui().snapshot().get("carried")` is the ItemStack currently held by the mouse cursor.

Player inventory indices may differ from current menu indices. Crafting tables, the player inventory, chests, and mod containers each have a menu layout defined by their respective container. Read the actual slots on the current screen before clicking slots.

## Vanilla single-chest example

During historical Minecraft 1.20.1 / Forge acceptance testing, the test observed a screen simple name of `ContainerScreen`, the title Chest, and 27 chest slots + 36 player slots, for a total of 63 menu slots in a vanilla single-chest screen. This result describes only that specific container; do not apply it to double chests or custom mod containers.

The following example assumes that the script has already opened a chest in the world, `sourceSlot` is the player's emerald slot identified from the current GUI snapshot, and `targetSlot` is an empty chest slot:

```js
function stack(slot) {
  return {
    item: String(slot.get("item")),
    count: Number(slot.get("count")),
    empty: String(slot.get("empty")) === "true"
  }
}

page.gui().snapshot().then(function (before) {
  if (before.get("type") !== "ContainerScreen") {
    throw new Error("Expected container screen, got " + before.get("type"))
  }
  var slots = before.get("slots")
  var sourceBefore = stack(slots.get(sourceSlot))
  var targetBefore = stack(slots.get(targetSlot))
  if (sourceBefore.item !== "minecraft:emerald" || sourceBefore.count !== 3 || !targetBefore.empty) {
    throw new Error("Container fixture does not match its expected state")
  }
  return page.gui().click(sourceSlot, 0)
}).then(function () {
  return page.gui().snapshot()
}).then(function (picked) {
  var cursor = picked.get("carried")
  if (cursor.get("item") !== "minecraft:emerald" || Number(cursor.get("count")) !== 3) {
    throw new Error("Pickup was not observed on the GUI cursor")
  }
  return page.gui().click(targetSlot, 0)
}).then(function () {
  return page.gui().snapshot()
}).then(function (after) {
  var slots = after.get("slots")
  var target = stack(slots.get(targetSlot))
  var cursor = after.get("carried")
  if (target.item !== "minecraft:emerald" || target.count !== 3 || String(cursor.get("empty")) !== "true") {
    throw new Error("Server-confirmed transfer was not observed")
  }
})
```

Real tests should allow bounded retry waits for client/server synchronization, and read the chest contents again after reopening it, or even after leaving and reentering the saved world. Do not treat acceptance of two click actions as evidence that the items were successfully transferred.

## Reading inventory and equipment

```js
page.inventory().snapshot().then(function (inventory) {
  var items = inventory.get("items")
  var total = 0
  for (var i = 0; i < items.size(); i++) {
    var item = items.get(i)
    if (item.get("item") === "minecraft:emerald") {
      total += Number(item.get("count"))
    }
  }
  if (total < 3) throw new Error("Expected at least 3 emeralds, found " + total)
})
```

Equipment state can be read with `page.inventory().equipment()`. For durability, check damage, maxDamage, remainingDurability, and the actual item state together. Controlled `/give`/`/setblock` commands are suitable for test fixtures, but do not prove normal player gameplay behavior.

## Testing and safety

- Use destructive actions (clicking to move items, dropping items, overwriting slots) only on isolated fixtures.
- Assert slot contents/cursor state first; do not continue operating after a failure.
- After completion, verify whether materials were consumed, chest contents were saved to disk, and the player inventory is correct.
- Reopening a chest and rejoining a world are different persistence boundaries; explicitly test whichever one is required.
- On multiplayer servers, menu state is confirmed by the server. `waitForTimeout` can serve only as a short polling interval, not as a condition on the state being tested.
