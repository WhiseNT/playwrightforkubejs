# 背包与容器

简体中文 | [English](Inventory-and-Containers.en.md)

[← Wiki 首页](Home.md) · [API 参考](API-Reference.md) · [GUI 与 Locator](GUI-and-Locators.md) · [E2E 测试](E2E-Testing.md)

## 两种 slot 编号

- `page.inventory().slot(index)` 读取玩家库存 index（包括 hotbar/armor/offhand 映射）。
- `page.gui().slot(index)` 读取**当前 AbstractContainerScreen 菜单**中的 slot index。
- `page.gui().snapshot().get("slots")` 是当前菜单 slot 列表；每项含 slot index、物品、数量与屏幕几何。
- `page.gui().snapshot().get("carried")` 是鼠标游标正拿着的 ItemStack。

玩家 inventory index 与当前菜单 index 可能不同。工作台、玩家 inventory、箱子和模组容器的 menu layout 由各自的 container 定义；先读本屏幕实际 slots，再进行槽位点击。

## Vanilla 单箱子例子

历史 Minecraft 1.20.1 / Forge 验收中，在 vanilla 单箱子界面里，测试观测到 screen 简名 `ContainerScreen`、标题 Chest、27 箱子格 + 36 玩家格，共 63 个菜单槽位。此结果仅描述这一特定容器，不要套用到双箱子或模组自定义容器。

下面的例子假定脚本已在世界里打开箱子，且 `sourceSlot` 是从本次 GUI snapshot 中找到的玩家绿宝石槽，`targetSlot` 是空箱子格：

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

实际测试要对 client/server 同步留出有限的重试等待，并在重新打开、甚至存档重进后再读箱子内容。不要把两次 click action 被接受当作物品已成功转移的证据。

## 背包与装备读数

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

装备状态可用 `page.inventory().equipment()` 读取；耐久应同时检查 damage、maxDamage、remainingDurability 和真实物品状态。受控 `/give`/`/setblock` 命令适合测试夹具，但不是对用户正常游戏行为的证明。

## 测试与安全

- 仅对隔离 fixture 使用破坏性动作（点击移动、丢弃、覆盖槽位）。
- 先断言槽位内容/游标，失败时不要继续操作。
- 完成后验证原料是否消耗、箱子内容是否落盘、玩家库存是否正确。
- 箱子 reopen 和世界 rejoin 是不同持久性边界；需要哪一项就显式测试哪一项。
- 玩家联机服务器中菜单状态由服务端确认，`waitForTimeout` 只能作为短轮询间隔，不等同于业务状态条件。
