// Real InventoryScreen clicks and real player attacks; commands only prepare fixtures.
// Function-body helpers and var deliberately avoid Rhino block-function bindings.
var playwrightGameplayDebugExpected = null

function playwrightGameplayDebug(page, stage, image, expectedFailure) {
  var progress = 'gameplay-fixture'
  var armorNames = ['head', 'chest', 'legs', 'feet']
  var armorItems = ['minecraft:iron_helmet', 'minecraft:iron_chestplate', 'minecraft:iron_leggings', 'minecraft:iron_boots']
  var armorGuiSlots = [5, 6, 7, 8]
  var equipmentNames = ['head', 'chest', 'legs', 'feet', 'mainhand', 'offhand']
  var suppliedItems = armorItems.concat(['minecraft:iron_sword', 'minecraft:shield'])
  var helmetHome = -1
  var equipped = null
  var removed = null
  var swapped = null
  var beforeDamage = null
  var afterDamage = null
  var target = null
  var targetId = -1
  var targetUuid = ''
  var initialTarget = null
  var attacks = 0
  var hits = []
  var initialSwordDamage = 0
  var fixtureBlocks = [
    {x: 19, y: -60, z: 2}, {x: 19, y: -59, z: 2},
    {x: 21, y: -60, z: 2}, {x: 21, y: -59, z: 2},
    {x: 19, y: -60, z: 3}, {x: 19, y: -59, z: 3},
    {x: 20, y: -60, z: 3}, {x: 20, y: -59, z: 3},
    {x: 21, y: -60, z: 3}, {x: 21, y: -59, z: 3}
  ]

  function require(condition, message) {
    if (!condition) throw new Error(progress + ': ' + message)
  }

  function field(value, key) { return value.get(key) }

  function mark(name, detail) {
    progress = name
    stage(name, JSON.stringify(detail))
  }

  function itemData(value) {
    require(value !== null && typeof value !== 'undefined', 'Missing ItemData')
    var data = {
      slot: Number(field(value, 'slot')),
      item: String(field(value, 'item')),
      count: Number(field(value, 'count')),
      damageable: String(field(value, 'damageable')) === 'true',
      damage: Number(field(value, 'damage')),
      maxDamage: Number(field(value, 'maxDamage')),
      remainingDurability: Number(field(value, 'remainingDurability'))
    }
    require(isFinite(data.damage) && isFinite(data.maxDamage) && isFinite(data.remainingDurability),
      'Missing durability contract for ' + data.item)
    require(data.remainingDurability === data.maxDamage - data.damage,
      'Inconsistent durability for ' + JSON.stringify(data))
    return data
  }

  function equipmentData(value) {
    var slots = field(value, 'equipment')
    var data = {equipment: {}, armorValue: Number(field(value, 'armorValue'))}
    for (var i = 0; i < equipmentNames.length; i++) {
      data.equipment[equipmentNames[i]] = itemData(field(slots, equipmentNames[i]))
    }
    require(isFinite(data.armorValue), 'Missing armorValue')
    return data
  }

  function healthData(value) {
    var data = {
      health: Number(field(value, 'health')), maxHealth: Number(field(value, 'maxHealth')),
      food: Number(field(value, 'food')), saturation: Number(field(value, 'saturation')),
      armor: Number(field(value, 'armor')), absorption: Number(field(value, 'absorption')),
      attackStrength: Number(field(value, 'attackStrength'))
    }
    require(isFinite(data.health) && isFinite(data.armor) && isFinite(data.absorption) && isFinite(data.attackStrength),
      'Missing health/equipment/combat contract')
    return data
  }

  function entityData(value) {
    return {
      id: Number(field(value, 'id')), uuid: String(field(value, 'uuid')),
      type: String(field(value, 'type')), name: String(field(value, 'name')),
      x: Number(field(value, 'x')), y: Number(field(value, 'y')), z: Number(field(value, 'z')),
      alive: String(field(value, 'alive')) === 'true',
      health: Number(field(value, 'health')), maxHealth: Number(field(value, 'maxHealth')),
      missing: false
    }
  }

  function command(text) {
    return page.chat().command(text).then(function () { return page.waitForTimeout(350) })
  }

  function poll(query, accepted, remaining, label) {
    return query().then(function (value) {
      if (accepted(value)) return value
      require(remaining > 0, label + '; last=' + JSON.stringify(value))
      return page.waitForTimeout(100).then(function () { return poll(query, accepted, remaining - 1, label) })
    })
  }

  function readEquipment() { return page.inventory().equipment().then(function (value) { return equipmentData(value) }) }
  function readHealth() { return page.status().health().then(function (value) { return healthData(value) }) }

  function readState() {
    var equipment = null
    return readEquipment().then(function (value) {
      equipment = value
      return readHealth()
    }).then(function (health) {
      return {equipment: equipment.equipment, armorValue: equipment.armorValue, status: health}
    })
  }

  function checkArmor(value, missingHead) {
    for (var i = 0; i < armorNames.length; i++) {
      var entry = value.equipment[armorNames[i]]
      var id = missingHead && i === 0 ? 'minecraft:air' : armorItems[i]
      if (entry.item !== id || entry.count !== (id === 'minecraft:air' ? 0 : 1)) return false
    }
    return value.armorValue === (missingHead ? 13 : 15)
  }

  function checkHands(value, reversed) {
    return value.equipment.mainhand.item === (reversed ? 'minecraft:shield' : 'minecraft:iron_sword') &&
      value.equipment.offhand.item === (reversed ? 'minecraft:iron_sword' : 'minecraft:shield')
  }

  function waitEquipment(missingHead, reversed) {
    return poll(readState, function (value) {
      return checkArmor(value, missingHead) && checkHands(value, reversed) && value.status.armor === value.armorValue
    }, 50, 'Equipment state did not synchronize')
  }

  function ruleFalse(name) {
    return command('/gamerule ' + name + ' false').then(function () {
      return page.chat().clear()
    }).then(function () {
      return page.chat().command('/gamerule ' + name)
    }).then(function () {
      // The rule name and literal false are invariant across en_us and zh_cn.
      return page.waitForChat(name, 5000)
    }).then(function () {
      return poll(function () {
        return page.chat().history(10).then(function (value) {
          var messages = field(value, 'messages')
          var result = []
          for (var i = 0; i < messages.size(); i++) result.push(String(messages.get(i)))
          return result
        })
      }, function (messages) {
        for (var i = 0; i < messages.length; i++) {
          if (messages[i].indexOf(name) >= 0 && messages[i].indexOf('false') >= 0) return true
        }
        return false
      }, 40, 'Gamerule query did not confirm ' + name + '=false')
    })
  }

  function waitMode(name) {
    return poll(function () {
      return page.query('status.gamemode', {}).then(function (value) { return String(field(value, 'gamemode')) })
    }, function (value) { return value === name }, 50, 'Gamemode did not become ' + name)
  }

  function waitPosition(x, y, z) {
    return poll(function () {
      return page.status().position().then(function (value) {
        return {x: Number(field(value, 'x')), y: Number(field(value, 'y')), z: Number(field(value, 'z'))}
      })
    }, function (value) {
      return Math.abs(value.x - x) < 0.05 && Math.abs(value.y - y) < 0.05 && Math.abs(value.z - z) < 0.05
    }, 50, 'Position did not synchronize')
  }

  function preservedInventory() {
    return page.inventory().snapshot().then(function (inventory) {
      var entries = field(inventory, 'items')
      var actual = {stone: 0, oak_planks: 0, oak_door: 0}
      for (var i = 0; i < entries.size(); i++) {
        var entry = entries.get(i)
        var id = String(field(entry, 'item'))
        if (id === 'minecraft:stone') actual.stone += Number(field(entry, 'count'))
        if (id === 'minecraft:oak_planks') actual.oak_planks += Number(field(entry, 'count'))
        if (id === 'minecraft:oak_door') actual.oak_door += Number(field(entry, 'count'))
      }
      require(actual.stone === 8 && actual.oak_planks === 2 && actual.oak_door === 2,
        'Existing fixtures were changed: ' + JSON.stringify(actual))
      return actual
    })
  }

  function provide(index) {
    if (index === suppliedItems.length) return preservedInventory()
    return command('/give @s ' + suppliedItems[index] + ' 1').then(function () {
      return page.inventory().waitFor(suppliedItems[index], 5000)
    }).then(function () { return provide(index + 1) })
  }

  function cursorEmpty() {
    return page.gui().snapshot().then(function (gui) {
      require(String(field(gui, 'type')) === 'InventoryScreen', 'Expected real InventoryScreen')
      var carried = field(gui, 'carried')
      require(carried !== null && typeof carried !== 'undefined', 'gui.snapshot.carried contract missing')
      var entry = itemData(carried)
      require(entry.item === 'minecraft:air' && entry.count === 0, 'GUI cursor not empty: ' + JSON.stringify(entry))
      return gui
    })
  }

  function click(slot) {
    return page.gui().click(slot, 0).then(function () { return page.waitForTimeout(150) })
  }

  function waitSlot(slot, item) {
    return poll(function () { return page.gui().slot(slot).then(function (value) { return itemData(value) }) },
      function (value) { return value.item === item && value.count === (item === 'minecraft:air' ? 0 : 1) },
      50, 'GUI slot ' + slot + ' did not contain ' + item)
  }

  function findItem(item) {
    return cursorEmpty().then(function (gui) {
      var slots = field(gui, 'slots')
      for (var i = 0; i < slots.size(); i++) {
        var entry = slots.get(i)
        var slot = Number(field(entry, 'slot'))
        if (slot >= 9 && slot <= 44 && String(field(entry, 'item')) === item) return slot
      }
      throw new Error(progress + ': Missing GUI inventory item ' + item)
    })
  }

  function transfer(source, destination, item) {
    if (source === destination) return waitSlot(destination, item).then(function () { return cursorEmpty() })
    return cursorEmpty().then(function () {
      return waitSlot(destination, 'minecraft:air')
    }).then(function () {
      return waitSlot(source, item)
    }).then(function () {
      return click(source)
    }).then(function () {
      return click(destination)
    }).then(function () {
      return waitSlot(destination, item)
    }).then(function () {
      return waitSlot(source, 'minecraft:air')
    }).then(function () {
      return cursorEmpty()
    })
  }

  function equip(index) {
    if (index === armorItems.length) return cursorEmpty()
    return findItem(armorItems[index]).then(function (slot) {
      return transfer(slot, armorGuiSlots[index], armorItems[index])
    }).then(function () { return equip(index + 1) })
  }

  function emptyInventorySlot() {
    return cursorEmpty().then(function (gui) {
      var slots = field(gui, 'slots')
      for (var i = 0; i < slots.size(); i++) {
        var entry = slots.get(i)
        var slot = Number(field(entry, 'slot'))
        if (slot >= 9 && slot <= 44 && String(field(entry, 'item')) === 'minecraft:air') return slot
      }
      throw new Error(progress + ': No empty InventoryScreen destination for helmet removal')
    })
  }

  function verifyInventoryEquipmentSlots(index) {
    var slots = [39, 38, 37, 36, 4, 40]
    var ids = armorItems.concat(['minecraft:iron_sword', 'minecraft:shield'])
    if (index === slots.length) return page.waitForTimeout(0)
    return page.inventory().slot(slots[index]).then(function (value) {
      var entry = itemData(value)
      require(entry.item === ids[index] && entry.count === 1,
        'Inventory equipment mapping mismatch: ' + JSON.stringify(entry))
      return verifyInventoryEquipmentSlots(index + 1)
    })
  }

  function damagedState() {
    return poll(readState, function (value) {
      if (value.status.health >= beforeDamage.status.health || !checkArmor(value, false) || !checkHands(value, false)) return false
      for (var i = 0; i < armorNames.length; i++) {
        var name = armorNames[i]
        if (value.equipment[name].damage > beforeDamage.equipment[name].damage) return true
      }
      return false
    }, 60, 'Controlled mob_attack did not reduce health and damage worn armor')
  }

  function prepareCage(index) {
    if (index === fixtureBlocks.length) return verifyCage(0)
    var pos = fixtureBlocks[index]
    return command('/setblock ' + pos.x + ' ' + pos.y + ' ' + pos.z + ' minecraft:glass').then(function () {
      return prepareCage(index + 1)
    })
  }

  function verifyCage(index) {
    if (index === fixtureBlocks.length) return page.waitForTimeout(0)
    return poll(function () {
      return page.block().get(fixtureBlocks[index]).then(function (value) { return String(field(value, 'block')) })
    }, function (value) { return value === 'minecraft:glass' }, 30, 'Combat fixture wall missing').then(function () {
      return verifyCage(index + 1)
    })
  }

  function findTarget() {
    return poll(function () {
      return page.entity().list(12).then(function (value) {
        var entries = field(value, 'entities')
        var result = []
        for (var i = 0; i < entries.size(); i++) {
          var entry = entries.get(i)
          if (String(field(entry, 'name')) === 'PW_DEBUG_TARGET') result.push(entityData(entry))
        }
        return result
      })
    }, function (value) {
      return value.length === 1 && value[0].alive && value[0].type === 'minecraft:pig' &&
        value[0].health === 20 && value[0].maxHealth === 20 &&
        Math.abs(value[0].x - 20.5) < 0.1 && Math.abs(value[0].y + 60) < 0.1 && Math.abs(value[0].z - 2.5) < 0.1
    }, 60, 'Named combat fixture was not ready').then(function (value) { return value[0] })
  }

  function readTarget() {
    return page.entity().info(targetId).then(function (value) {
      var entry = entityData(value)
      require(entry.uuid === targetUuid && entry.name === 'PW_DEBUG_TARGET', 'Combat target identity changed')
      // Disappearance is not a death measurement. Require the real entity's zero-health state.
      return entry
    })
  }

  function readCombat() {
    var entity = null
    return readTarget().then(function (value) {
      entity = value
      return readState()
    }).then(function (state) { return {target: entity, player: state} })
  }

  function fight(remaining) {
    var before = null
    var attackState = null
    return readCombat().then(function (value) {
      if (!value.target.alive) return value
      require(remaining > 0, 'Target survived 8 real attacks: ' + JSON.stringify(value))
      before = value
      require(checkArmor(value.player, false) && checkHands(value.player, false), 'Equipment changed during combat')
      return page.status().position().then(function (position) {
        var dx = value.target.x - Number(field(position, 'x'))
        var dy = value.target.y - Number(field(position, 'y'))
        var dz = value.target.z - Number(field(position, 'z'))
        require(dx * dx + dy * dy + dz * dz <= 9, 'Target moved outside real melee reach: ' + JSON.stringify(value.target))
        return poll(readHealth, function (health) { return health.attackStrength >= 0.99 }, 50, 'Attack cooldown never recovered')
      }).then(function () {
        return page.look().entity({id: targetId})
      }).then(function () {
        // Re-read actual target coordinates every round; never teleport a player to fake a hit.
        return readTarget()
      }).then(function (value) {
        attackState = value
        require(value.alive && value.health > 0, 'Target died before this player attack')
        attacks++
        return page.entity().attack(targetId)
      }).then(function () {
        return poll(readCombat, function (value) {
          return value.target.health < before.target.health &&
            value.player.equipment.mainhand.damage > before.player.equipment.mainhand.damage
        }, 60, 'Attack sent but target health/sword durability did not change')
      }).then(function (after) {
        var hit = {
          attack: attacks, id: targetId, uuid: targetUuid,
          beforeHealth: before.target.health, afterHealth: after.target.health,
          beforeDamage: before.player.equipment.mainhand.damage, afterDamage: after.player.equipment.mainhand.damage,
          alive: after.target.alive, missing: after.target.missing,
          targetBeforeAttack: attackState, targetAfterAttack: after.target,
          before: before.player, after: after.player
        }
        hits.push(hit)
        if (attacks === 1) mark('combat-damage-verified', hit)
        progress = 'combat-target-defeated'
        if (!after.target.alive) return after
        return fight(remaining - 1)
      })
    })
  }

  return preservedInventory().then(function () {
    return ruleFalse('naturalRegeneration')
  }).then(function () {
    return command('/gamemode survival @s')
  }).then(function () {
    return waitMode('survival')
  }).then(function () {
    return command('/difficulty normal')
  }).then(function () {
    return poll(function () {
      return page.status().world().then(function (value) { return String(field(value, 'difficulty')) })
    }, function (value) { return value === 'normal' }, 50, 'Difficulty did not become normal')
  }).then(function () {
    return command('/tp @s 20.5 -60 0.5 0 0')
  }).then(function () {
    return waitPosition(20.5, -60, 0.5)
  }).then(function () {
    return ruleFalse('doMobSpawning')
  }).then(function () {
    progress = 'equipment-equipped'
    return provide(0)
  }).then(function () {
    return page.input().press('inventory')
  }).then(function () {
    return page.gui().waitFor('InventoryScreen', 10000)
  }).then(function () {
    return cursorEmpty()
  }).then(function () {
    return equip(0)
  }).then(function () {
    return findItem('minecraft:iron_sword')
  }).then(function (slot) {
    // InventoryMenu hotbar index 4 is GUI slot 40; inventory.slot(4) is the same stack.
    return transfer(slot, 40, 'minecraft:iron_sword')
  }).then(function () {
    return findItem('minecraft:shield')
  }).then(function (slot) {
    return transfer(slot, 45, 'minecraft:shield')
  }).then(function () {
    return page.inventory().selectHotbar(4)
  }).then(function () {
    return verifyInventoryEquipmentSlots(0)
  }).then(function () {
    return waitEquipment(false, false)
  }).then(function (value) {
    equipped = value
    for (var i = 0; i < equipmentNames.length; i++) {
      var entry = value.equipment[equipmentNames[i]]
      require(entry.damageable && entry.damage === 0 && entry.maxDamage > 0, 'Fresh equipment is not pristine: ' + JSON.stringify(entry))
    }
    mark('equipment-equipped', value)
    return image('equipment-equipped')
  }).then(function () {
    progress = 'equipment-removed-restored'
    return emptyInventorySlot()
  }).then(function (slot) {
    helmetHome = slot
    return transfer(5, helmetHome, 'minecraft:iron_helmet')
  }).then(function () {
    return waitEquipment(true, false)
  }).then(function (value) {
    removed = value
    require(value.armorValue === equipped.armorValue - 2, 'Removing helmet did not reduce armor by 2')
    return transfer(helmetHome, 5, 'minecraft:iron_helmet')
  }).then(function () {
    return waitEquipment(false, false)
  }).then(function (value) {
    require(value.armorValue === equipped.armorValue, 'Restored helmet did not restore armor')
    mark('equipment-removed-restored', {emptyDestination: helmetHome, before: equipped, removed: removed, restored: value})
    return cursorEmpty()
  }).then(function () {
    return page.gui().close()
  }).then(function () {
    progress = 'equipment-hands-swapped'
    return page.inventory().selectHotbar(4)
  }).then(function () {
    return page.inventory().swapHands()
  }).then(function () {
    return waitEquipment(false, true)
  }).then(function (value) {
    swapped = value
    return page.inventory().swapHands()
  }).then(function () {
    return waitEquipment(false, false)
  }).then(function (value) {
    mark('equipment-hands-swapped', {before: equipped, swapped: swapped, restored: value})
    progress = 'equipment-invalid-slot'
    return expectedFailure(page.inventory().slot(41), 'SLOT_OUT_OF_RANGE', 'equipment-invalid-slot')
  }).then(function () {
    progress = 'equipment-damage-verified'
    return readState()
  }).then(function (value) {
    beforeDamage = value
    require(value.status.health > 4, 'Controlled damage fixture needs sufficient player health')
    // Controlled damage fixture: this command is NOT a replacement for the real attacks below.
    return command('/damage @s 4 minecraft:mob_attack')
  }).then(function () {
    return damagedState()
  }).then(function (value) {
    afterDamage = value
    var damagedArmor = ''
    for (var i = 0; i < armorNames.length; i++) {
      var name = armorNames[i]
      if (value.equipment[name].damage > beforeDamage.equipment[name].damage) {
        damagedArmor = name
        break
      }
    }
    require(damagedArmor !== '', 'No worn armor recorded controlled damage')
    mark('equipment-damage-verified', {
      fixture: 'controlled damage fixture', command: '/damage @s 4 minecraft:mob_attack',
      beforeHealth: beforeDamage.status.health, afterHealth: value.status.health,
      damagedArmor: damagedArmor,
      beforeDamage: beforeDamage.equipment[damagedArmor].damage, afterDamage: value.equipment[damagedArmor].damage,
      before: beforeDamage, after: value
    })
    return page.input().press('inventory')
  }).then(function () {
    return page.gui().waitFor('InventoryScreen', 10000)
  }).then(function () {
    return cursorEmpty()
  }).then(function () {
    return image('equipment-damaged')
  }).then(function () {
    return page.gui().close()
  }).then(function () {
    progress = 'combat-target-ready'
    // Only our own tag is cleaned; no mass kill of unrelated world entities.
    return command('/kill @e[tag=pw_gameplay_debug_target]')
  }).then(function () {
    // Open-front glass fixture keeps knockback behind the target, without blocking melee.
    return prepareCage(0)
  }).then(function () {
    return command('/summon minecraft:pig 20.5 -60 2.5 {CustomName:\'{"text":"PW_DEBUG_TARGET"}\',CustomNameVisible:1b,Tags:["pw_gameplay_debug_target"],NoAI:1b,PersistenceRequired:1b,Attributes:[{Name:"minecraft:generic.max_health",Base:20.0d}],Health:20.0f}')
  }).then(function () {
    return findTarget()
  }).then(function (value) {
    target = value
    targetId = value.id
    targetUuid = value.uuid
    initialTarget = value
    initialSwordDamage = afterDamage.equipment.mainhand.damage
    return readState()
  }).then(function (value) {
    mark('combat-target-ready', {target: target, player: value, fixture: 'open-front glass knockback boundary'})
    return page.look().entity({id: targetId})
  }).then(function () {
    return image('combat-before')
  }).then(function () {
    progress = 'combat-missing-entity'
    return expectedFailure(page.entity().info(-98765), 'ENTITY_NOT_FOUND', 'combat-missing-entity')
  }).then(function () {
    progress = 'combat-distance-filter'
    return expectedFailure(page.entity().attack({id: targetId, maxDistance: 0.1}), 'ENTITY_NOT_FOUND', 'combat-distance-filter')
  }).then(function () {
    progress = 'combat-damage-verified'
    return fight(8)
  }).then(function (value) {
    progress = 'combat-target-defeated'
    require(!value.target.alive && value.target.health === 0 && !value.target.missing, 'Real zero-health death was not observed')
    require(attacks > 0 && attacks <= 8 && hits.length === attacks, 'Real attack count missing')
    require(value.player.equipment.mainhand.damage > initialSwordDamage, 'Sword durability did not record combat')
    mark('combat-target-defeated', {
      id: targetId, uuid: targetUuid, alive: value.target.alive, missing: value.target.missing,
      beforeHealth: initialTarget.health, afterHealth: value.target.health,
      beforeDamage: initialSwordDamage, afterDamage: value.player.equipment.mainhand.damage,
      attacks: attacks, hits: hits, target: value.target, player: value.player
    })
    return image('combat-after')
  }).then(function () {
    progress = 'gameplay-restore'
    return preservedInventory()
  }).then(function () {
    return readState()
  }).then(function (value) {
    require(checkArmor(value, false) && checkHands(value, false), 'Final equipped items were not preserved')
    // Pure JS only: no Java Map survives in the save/rejoin expectation.
    playwrightGameplayDebugExpected = {
      equipment: value.equipment, armorValue: value.armorValue, health: value.status.health,
      target: {id: targetId, uuid: targetUuid, name: 'PW_DEBUG_TARGET', alive: false}
    }
    return command('/gamemode creative @s')
  }).then(function () {
    return waitMode('creative')
  }).then(function () {
    return page.inventory().selectHotbar(0)
  }).then(function () {
    return command('/tp @s 0.5 -60 0.5 0 0')
  }).then(function () {
    return waitPosition(0.5, -60, 0.5)
  }).catchError(function (error) {
    throw new Error(progress + ': ' + String(error))
  })
}

function playwrightGameplayDebugPersistence(page, stage, image) {
  var progress = 'gameplay-equipment-persistence-verified'
  var expected = playwrightGameplayDebugExpected
  var armorNames = ['head', 'chest', 'legs', 'feet']
  var actual = {equipment: {}, targetAbsent: false}

  function require(condition, message) {
    if (!condition) throw new Error(progress + ': ' + message)
  }

  function field(value, key) { return value.get(key) }

  function itemData(value) {
    return {
      item: String(field(value, 'item')), count: Number(field(value, 'count')),
      damageable: String(field(value, 'damageable')) === 'true',
      damage: Number(field(value, 'damage')), maxDamage: Number(field(value, 'maxDamage')),
      remainingDurability: Number(field(value, 'remainingDurability'))
    }
  }

  function compare(name, value) {
    var want = expected.equipment[name]
    require(value.item === want.item && value.count === want.count && value.damage === want.damage &&
      value.damageable === want.damageable && value.maxDamage === want.maxDamage &&
      value.remainingDurability === want.remainingDurability,
      name + ' did not persist: ' + JSON.stringify({expected: want, actual: value}))
    actual.equipment[name] = value
  }

  function waitSurvival(remaining) {
    return page.query('status.gamemode', {}).then(function (value) {
      if (String(field(value, 'gamemode')) === 'survival') return true
      require(remaining > 0, 'Rejoined player did not enter survival inventory mode')
      return page.waitForTimeout(100).then(function () { return waitSurvival(remaining - 1) })
    })
  }

  // The earlier fixture restores creative; verify armor in the real survival inventory GUI.
  return page.chat().command('/gamemode survival @s').then(function () {
    return waitSurvival(50)
  }).then(function () {
    return page.inventory().equipment()
  }).then(function (value) {
    require(expected !== null, 'Missing pure-JS gameplay expectation from initial run')
    var entries = field(value, 'equipment')
    for (var i = 0; i < armorNames.length; i++) compare(armorNames[i], itemData(field(entries, armorNames[i])))
    compare('offhand', itemData(field(entries, 'offhand')))
    actual.armorValue = Number(field(value, 'armorValue'))
    require(actual.armorValue === expected.armorValue, 'Armor value did not persist')
    // Rejoin intentionally selects slot 0, so do not read the sword from mainhand.
    return page.inventory().slot(4)
  }).then(function (value) {
    compare('mainhand', itemData(value))
    return page.status().health()
  }).then(function (value) {
    actual.health = Number(field(value, 'health'))
    actual.armor = Number(field(value, 'armor'))
    actual.absorption = Number(field(value, 'absorption'))
    actual.attackStrength = Number(field(value, 'attackStrength'))
    require(actual.armor === expected.armorValue, 'Health armor query disagrees after rejoin')
    require(actual.health === expected.health, 'Health changed despite naturalRegeneration=false')
    return page.entity().list(64)
  }).then(function (value) {
    var entries = field(value, 'entities')
    for (var i = 0; i < entries.size(); i++) {
      var entry = entries.get(i)
      require(String(field(entry, 'name')) !== expected.target.name && String(field(entry, 'uuid')) !== expected.target.uuid,
        'Defeated combat target exists after rejoin')
    }
    actual.targetAbsent = true
    actual.target = expected.target
    stage('gameplay-equipment-persistence-verified', JSON.stringify({expected: expected, actual: actual}))
    return page.input().press('inventory')
  }).then(function () {
    return page.gui().waitFor('InventoryScreen', 10000)
  }).then(function () {
    return image('equipment-rejoined')
  }).then(function () {
    return page.gui().close()
  }).catchError(function (error) {
    throw new Error(progress + ': ' + String(error))
  })
}
