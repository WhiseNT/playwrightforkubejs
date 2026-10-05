// Invoked by playwright_e2e.js after entering a fresh, isolated test world.
// Only oak logs are given: all planks, the table and doors come from real recipes.
// Survival mode makes ingredient consumption and placement counts observable.
function playwrightWoodDoor(page, stage, image) {
  var table = {x: 2, y: -60, z: 0}
  var door = {x: 2, y: -60, z: 2}
  var doorUpper = {x: 2, y: -59, z: 2}
  var plankSlot = -1
  var progress = 'wood-start'

  function require(condition, message) {
    if (!condition) throw new Error(progress + ': ' + message)
  }

  function field(value, key) { return value.get(key) }

  function mark(name, detail) {
    progress = name
    stage(name, detail)
  }

  function count(inventory, item) {
    var items = field(inventory, 'items')
    var total = 0
    for (var i = 0; i < items.size(); i++) {
      var entry = items.get(i)
      if (String(field(entry, 'item')) === item) total += Number(field(entry, 'count'))
    }
    return total
  }

  function inventoryCounts(logs, planks, tables, doors) {
    return page.inventory().snapshot().then(function (inventory) {
      var expected = [logs, planks, tables, doors]
      var ids = ['minecraft:oak_log', 'minecraft:oak_planks', 'minecraft:crafting_table', 'minecraft:oak_door']
      for (var i = 0; i < ids.length; i++) {
        require(count(inventory, ids[i]) === expected[i], ids[i] + ' expected=' + expected[i] + '; inventory=' + String(inventory))
      }
      return inventory
    })
  }

  function command(text) {
    return page.chat().command(text).then(function () { return page.waitForTimeout(350) })
  }

  function click(slot, button) {
    progress = 'wood-slot-click:' + slot + ':' + (button || 0)
    return page.gui().click(slot, button || 0).then(function () { return page.waitForTimeout(150) })
  }

  function waitSlot(slot, item, amount, attempts) {
    return page.gui().slot(slot).then(function (entry) {
      if (String(field(entry, 'item')) === item && Number(field(entry, 'count')) === amount) return entry
      require(attempts > 0, 'Slot ' + slot + ' expected ' + item + ' x' + amount + '; actual=' + String(entry))
      return page.waitForTimeout(100).then(function () { return waitSlot(slot, item, amount, attempts - 1) })
    })
  }

  function findItemSlot(item, minimumSlot) {
    return page.gui().snapshot().then(function (gui) {
      var slots = field(gui, 'slots')
      for (var i = minimumSlot; i < slots.size(); i++) {
        var entry = slots.get(i)
        if (String(field(entry, 'item')) === item) return Number(field(entry, 'slot'))
      }
      throw new Error(progress + ': Inventory item missing from GUI: ' + item + '; ' + String(gui))
    })
  }

  function placeOneEach(slots, index) {
    if (index === slots.length) return page.gui().snapshot()
    return click(slots[index], 1).then(function () { return placeOneEach(slots, index + 1) })
  }

  function recipe(slots, outputItem, outputCount, screenshot) {
    return findItemSlot('minecraft:oak_planks', slots.length === 4 ? 9 : 10).then(function (slot) {
      plankSlot = slot
      return click(plankSlot)
    }).then(function () {
      return placeOneEach(slots, 0)
    }).then(function () {
      return click(plankSlot)
    }).then(function () {
      return waitSlot(0, outputItem, outputCount, 50)
    }).then(function () {
      return verifyGrid(slots, 'minecraft:oak_planks', 1)
    }).then(function () {
      return image(screenshot)
    })
  }

  function verifyGrid(slots, item, amount) {
    return page.gui().snapshot().then(function (gui) {
      var entries = field(gui, 'slots')
      for (var i = 0; i < slots.length; i++) {
        var entry = entries.get(slots[i])
        require(String(field(entry, 'item')) === item && Number(field(entry, 'count')) === amount,
          'Recipe grid mismatch: slot=' + slots[i] + '; ' + String(gui))
      }
      return gui
    })
  }

  function takeOutput(destination) {
    return waitSlot(destination, 'minecraft:air', 0, 0).then(function () {
      return click(0)
    }).then(function () {
      return click(destination)
    })
  }

  function craftPlanks(remaining) {
    if (remaining === 0) return page.gui().snapshot()
    return waitSlot(0, 'minecraft:oak_planks', 4, 50).then(function () {
      return click(0)
    }).then(function () {
      // InventoryMenu hotbar index 2 is slot 38; merge each four-plank output.
      return click(38)
    }).then(function () {
      return craftPlanks(remaining - 1)
    })
  }

  function waitBlock(pos, item, state, attempts) {
    return page.block().get(pos).then(function (block) {
      if (String(field(block, 'block')) === item && (!state || String(field(block, 'state')).indexOf(state) >= 0)) return block
      require(attempts > 0, 'Block expected ' + item + ' ' + (state || '') + '; actual=' + String(block))
      return page.waitForTimeout(100).then(function () { return waitBlock(pos, item, state, attempts - 1) })
    })
  }

  function verifyDoor() {
    var lower = null
    return waitBlock(door, 'minecraft:oak_door', 'half=lower', 50).then(function (block) {
      lower = block
      return waitBlock(doorUpper, 'minecraft:oak_door', 'half=upper', 50)
    }).then(function (upper) {
      return 'lower=' + String(lower) + '; upper=' + String(upper)
    })
  }

  return inventoryCounts(0, 0, 0, 0).then(function () {
    return command('/tp @s 0.5 -60 0.5 0 0')
  }).then(function () {
    return command('/gamemode survival @s')
  }).then(function () {
    return command('/give @s minecraft:oak_log 3')
  }).then(function () {
    return page.inventory().waitFor('minecraft:oak_log', 10000)
  }).then(function () {
    return inventoryCounts(3, 0, 0, 0)
  }).then(function (inventory) {
    mark('wood-obtained', inventory)
    return page.input().press('inventory')
  }).then(function () {
    return page.gui().waitFor('InventoryScreen', 10000)
  }).then(function () {
    return waitSlot(38, 'minecraft:air', 0, 0)
  }).then(function () {
    return findItemSlot('minecraft:oak_log', 9)
  }).then(function (slot) {
    return click(slot)
  }).then(function () {
    return click(1)
  }).then(function () {
    return waitSlot(1, 'minecraft:oak_log', 3, 50)
  }).then(function () {
    return image('wood-planks-recipe')
  }).then(function () {
    return craftPlanks(3)
  }).then(function () {
    return verifyGrid([1, 2, 3, 4], 'minecraft:air', 0)
  }).then(function () {
    return inventoryCounts(0, 12, 0, 0)
  }).then(function (inventory) {
    mark('planks-crafted', inventory)
    return recipe([1, 2, 3, 4], 'minecraft:crafting_table', 1, 'wood-table-recipe')
  }).then(function () {
    // InventoryMenu hotbar index 1; the original logs have been consumed.
    return takeOutput(37)
  }).then(function () {
    return verifyGrid([1, 2, 3, 4], 'minecraft:air', 0)
  }).then(function () {
    return inventoryCounts(0, 8, 1, 0)
  }).then(function (inventory) {
    mark('crafting-table-crafted', inventory)
    return page.gui().close()
  }).then(function () {
    return page.inventory().selectHotbar(1)
  }).then(function () {
    return page.block().place({x: 2, y: -61, z: 0}, 'up')
  }).then(function () {
    return waitBlock(table, 'minecraft:crafting_table', '', 50)
  }).then(function (block) {
    mark('crafting-table-placed', block)
    return page.look().at({x: 2.5, y: -59.5, z: 0.5})
  }).then(function () {
    return image('wood-table-placed')
  }).then(function () {
    return inventoryCounts(0, 8, 0, 0)
  }).then(function () {
    return page.block().interact(table)
  }).then(function () {
    return page.gui().waitFor('CraftingScreen', 10000)
  }).then(function (gui) {
    mark('crafting-table-opened', gui)
    // CraftingMenu: result 0; 3x3 matrix 1..9. Two columns, three rows.
    return recipe([1, 2, 4, 5, 7, 8], 'minecraft:oak_door', 3, 'wood-door-recipe')
  }).then(function () {
    // CraftingMenu hotbar index 3 is slot 40, unlike InventoryMenu slot 39.
    return takeOutput(40)
  }).then(function () {
    return verifyGrid([1, 2, 3, 4, 5, 6, 7, 8, 9], 'minecraft:air', 0)
  }).then(function () {
    return inventoryCounts(0, 2, 0, 3)
  }).then(function (inventory) {
    mark('wooden-doors-crafted', inventory)
    return page.gui().close()
  }).then(function () {
    return page.inventory().selectHotbar(3)
  }).then(function () {
    return page.look().at({x: 2.5, y: -60, z: 2.5})
  }).then(function () {
    return page.block().place({x: 2, y: -61, z: 2}, 'up')
  }).then(function () {
    return verifyDoor()
  }).then(function (detail) {
    mark('wooden-door-placed', detail)
    return inventoryCounts(0, 2, 0, 2)
  }).then(function () {
    return page.look().at({x: 2.5, y: -59, z: 2.5})
  }).then(function () {
    return image('wood-door-placed')
  }).then(function () {
    // Restore the previous creative fixture without touching its eight stone items.
    return command('/gamemode creative @s')
  }).then(function () {
    return page.inventory().selectHotbar(0)
  }).catchError(function (error) {
    throw new Error(progress + ': ' + String(error))
  })
}

// Used after save-and-quit/rejoin: the crafted table and both door halves must persist.
function playwrightWoodDoorPersistence(page, stage, image) {
  return page.block().get({x: 2, y: -60, z: 0}).then(function (block) {
    if (String(block.get('block')) !== 'minecraft:crafting_table') throw new Error('Crafted table did not persist: ' + String(block))
    return page.block().get({x: 2, y: -60, z: 2})
  }).then(function (block) {
    if (String(block.get('block')) !== 'minecraft:oak_door' || String(block.get('state')).indexOf('half=lower') < 0) {
      throw new Error('Door lower half did not persist: ' + String(block))
    }
    return page.block().get({x: 2, y: -59, z: 2})
  }).then(function (block) {
    if (String(block.get('block')) !== 'minecraft:oak_door' || String(block.get('state')).indexOf('half=upper') < 0) {
      throw new Error('Door upper half did not persist: ' + String(block))
    }
    return page.inventory().snapshot()
  }).then(function (inventory) {
    var items = inventory.get('items')
    var doors = 0
    var planks = 0
    for (var i = 0; i < items.size(); i++) {
      var entry = items.get(i)
      if (String(entry.get('item')) === 'minecraft:oak_door') doors += Number(entry.get('count'))
      if (String(entry.get('item')) === 'minecraft:oak_planks') planks += Number(entry.get('count'))
    }
    if (doors !== 2 || planks !== 2) throw new Error('Crafted inventory did not persist: ' + String(inventory))
    stage('wooden-door-persistence-verified', inventory)
    return image('wood-door-rejoined')
  })
}
