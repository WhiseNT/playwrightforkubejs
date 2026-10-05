// A vanilla chest regression fixture using public Playwright APIs and real container slots.
function playwrightContainerDebug(page, stage, image) {
  var chest = {x: 6, y: -60, z: 2}
  var chestSlot = 13
  var sourceSlot = -1
  var progress = 'container-fixture'

  function require(condition, message) {
    if (!condition) throw new Error(progress + ': ' + message)
  }

  function field(value, key) { return value.get(key) }

  function mark(name, detail) {
    progress = name
    stage(name, JSON.stringify(detail))
  }

  function command(text) {
    return page.chat().command(text).then(function () { return page.waitForTimeout(350) })
  }

  function item(value) {
    return {item: String(field(value, 'item')), count: Number(field(value, 'count')),
      empty: String(field(value, 'empty')) === 'true'}
  }

  function slot(gui, index) {
    var slots = field(gui, 'slots')
    require(index >= 0 && index < slots.size(), 'GUI slot out of range: ' + index)
    var value = slots.get(index)
    return {slot: Number(field(value, 'slot')), item: String(field(value, 'item')),
      count: Number(field(value, 'count')), empty: String(field(value, 'empty')) === 'true'}
  }

  function carried(gui) { return item(field(gui, 'carried')) }

  function containerInfo(gui) {
    return {screenType: String(field(gui, 'type')), containerId: Number(field(gui, 'containerId')),
      slotCount: field(gui, 'slots').size(), chestSlots: 27, playerSlots: 36}
  }

  function waitForGui(predicate, attempts, message) {
    return page.gui().snapshot().then(function (gui) {
      if (predicate(gui)) return gui
      require(attempts > 0, message + ': ' + String(gui))
      return page.waitForTimeout(100).then(function () { return waitForGui(predicate, attempts - 1, message) })
    })
  }

  function waitForChest() {
    return page.gui().waitFor('ContainerScreen', 10000).then(function () {
      return page.gui().snapshot()
    }).then(function (gui) {
      require(String(field(gui, 'type')) === 'ContainerScreen', 'Block interaction did not open ChestScreen: ' + String(gui))
      require(field(gui, 'slots').size() === 63, 'Unexpected chest/player menu slot layout: ' + String(gui))
      return gui
    })
  }

  function openChest() {
    return page.look().at({x: chest.x + 0.5, y: chest.y + 0.5, z: chest.z + 0.5}).then(function () {
      return page.block().interact(chest)
    }).then(function () { return waitForChest() })
  }

  function selectEmptyHandSlot() {
    return page.inventory().snapshot().then(function (inventory) {
      var items = field(inventory, 'items')
      var safeSlot = -1
      for (var i = 0; i < items.size(); i++) {
        var entry = items.get(i)
        var index = Number(field(entry, 'slot'))
        if (index < 9 && String(field(entry, 'empty')) === 'true') {
          safeSlot = index
          break
        }
      }
      require(safeSlot >= 0, 'No empty hotbar slot available for safe chest interaction')
      return page.inventory().selectHotbar(safeSlot)
    })
  }

  return command('/gamemode survival @s').then(function () {
    return command('/tp @s 6.5 -60 0.5 0 0')
  }).then(function () {
    return command('/setblock 6 -60 2 minecraft:chest')
  }).then(function () {
    return page.block().get(chest)
  }).then(function (block) {
    require(String(field(block, 'block')) === 'minecraft:chest', 'Isolated chest fixture was not created: ' + String(block))
    return command('/give @s minecraft:emerald 3')
  }).then(function () {
    return page.inventory().waitFor('minecraft:emerald', 5000)
  }).then(function () {
    return selectEmptyHandSlot()
  }).then(function () {
    return openChest()
  }).then(function (gui) {
    var info = containerInfo(gui)
    require(info.slotCount === info.chestSlots + info.playerSlots && info.containerId >= 0,
      'Chest menu does not expose the expected real slot layout')
    for (var i = info.chestSlots; i < info.slotCount; i++) {
      var candidate = slot(gui, i)
      if (candidate.item === 'minecraft:emerald') {
        require(candidate.count === 3, 'Expected exactly three fixture emeralds: ' + JSON.stringify(candidate))
        sourceSlot = i
        break
      }
    }
    require(sourceSlot >= info.chestSlots, 'Player inventory slot does not contain the fixture emeralds')
    require(slot(gui, chestSlot).empty, 'Target chest slot is not empty before transfer')
    mark('container-chest-opened', {
      screenType: info.screenType, containerId: info.containerId, slotCount: info.slotCount,
      chestSlots: info.chestSlots, playerSlots: info.playerSlots,
      chestBlock: {x: chest.x, y: chest.y, z: chest.z, block: 'minecraft:chest'}
    })
    progress = 'container-item-moved'
    var sourceBefore = slot(gui, sourceSlot)
    var targetBefore = slot(gui, chestSlot)
    return page.gui().click(sourceSlot, 0).then(function () {
      return waitForGui(function (value) {
        return slot(value, sourceSlot).empty && carried(value).item === 'minecraft:emerald' &&
          carried(value).count === 3
      }, 40, 'Item pickup was not reflected by the live chest menu')
    }).then(function (picked) {
      var cursorAfterPickup = carried(picked)
      var sourceAfter = slot(picked, sourceSlot)
      return page.gui().click(chestSlot, 0).then(function () {
        return waitForGui(function (value) {
          return slot(value, chestSlot).item === 'minecraft:emerald' &&
            slot(value, chestSlot).count === 3 && carried(value).empty && slot(value, sourceSlot).empty
        }, 40, 'Item transfer was not reflected by the live chest menu')
      }).then(function (moved) {
        var detail = {
          screenType: String(field(moved, 'type')), containerId: Number(field(moved, 'containerId')),
          slotCount: field(moved, 'slots').size(), chestSlots: 27, playerSlots: 36,
          sourceSlot: sourceSlot, targetSlot: chestSlot,
          clickButton: 0, sourceBefore: sourceBefore, targetBefore: targetBefore,
          cursorAfterPickup: cursorAfterPickup, sourceAfter: slot(moved, sourceSlot),
          targetAfter: slot(moved, chestSlot), carriedAfter: carried(moved)
        }
        mark('container-item-moved', detail)
        return image('container-filled')
      })
    })
  }).then(function () {
    return page.gui().close()
  }).then(function () {
    return waitForGui(function (gui) { return !field(gui, 'open') }, 20, 'Chest did not close')
  }).then(function () {
    return openChest()
  }).then(function (gui) {
    var info = containerInfo(gui)
    var stored = slot(gui, chestSlot)
    var cursor = carried(gui)
    require(stored.item === 'minecraft:emerald' && stored.count === 3 && cursor.empty,
      'Chest contents did not survive closing and reopening: ' + String(gui))
    mark('container-reopened', {
      screenType: info.screenType, containerId: info.containerId, slotCount: info.slotCount,
      chestSlots: info.chestSlots, playerSlots: info.playerSlots,
      targetSlot: chestSlot, target: stored, carried: cursor
    })
    return image('container-reopened')
  }).then(function () {
    return page.gui().close()
  }).then(function () {
    return command('/gamemode creative @s')
  }).catchError(function (error) {
    throw new Error(progress + ': ' + String(error))
  })
}

function playwrightContainerDebugPersistence(page, stage, image) {
  var chest = {x: 6, y: -60, z: 2}
  var chestSlot = 13
  var progress = 'container-persistence'

  function require(condition, message) {
    if (!condition) throw new Error(progress + ': ' + message)
  }

  function command(text) {
    return page.chat().command(text).then(function () { return page.waitForTimeout(350) })
  }

  function openChest() {
    return page.look().at({x: chest.x + 0.5, y: chest.y + 0.5, z: chest.z + 0.5}).then(function () {
      return page.block().interact(chest)
    }).then(function () {
      return page.gui().waitFor('ContainerScreen', 10000)
    }).then(function () { return page.gui().snapshot() })
  }

  return command('/gamemode survival @s').then(function () {
    return command('/tp @s 6.5 -60 0.5 0 0')
  }).then(function () {
    return page.block().get(chest)
  }).then(function (block) {
    require(String(block.get('block')) === 'minecraft:chest', 'Chest block did not persist across world rejoin')
    return openChest()
  }).then(function (gui) {
    var slots = gui.get('slots')
    require(String(gui.get('type')) === 'ContainerScreen' && slots.size() === 63,
      'Rejoined world did not expose the vanilla chest menu')
    var stored = slots.get(chestSlot)
    var carried = gui.get('carried')
    var actual = {screenType: String(gui.get('type')), containerId: Number(gui.get('containerId')),
      slotCount: slots.size(), chestSlots: 27, playerSlots: 36, targetSlot: chestSlot, worldRejoined: true,
      target: {slot: Number(stored.get('slot')), item: String(stored.get('item')),
        count: Number(stored.get('count')), empty: String(stored.get('empty')) === 'true'},
      carried: {item: String(carried.get('item')), count: Number(carried.get('count')),
        empty: String(carried.get('empty')) === 'true'}, chestBlock: {x: chest.x, y: chest.y, z: chest.z,
        block: 'minecraft:chest'}}
    require(actual.target.item === 'minecraft:emerald' && actual.target.count === 3 && actual.carried.empty,
      'Actual chest contents did not persist across world rejoin: ' + JSON.stringify(actual))
    stage('container-persistence-verified', JSON.stringify(actual))
    return image('container-rejoined')
  }).then(function () {
    return page.gui().close()
  }).then(function () {
    return command('/gamemode creative @s')
  }).then(function () {
    return command('/tp @s 0.5 -60 0.5 0 0')
  }).catchError(function (error) {
    throw new Error(progress + ': ' + String(error))
  })
}
