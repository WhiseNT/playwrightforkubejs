// Explicit opt-in: run with -PplaywrightE2E=true -PplaywrightRunId=<unique-id>.
// No direct world-loading APIs: every menu transition uses a real GUI control.
// Declare helpers at function-body scope to avoid Rhino's block-function binding conflict.
;(function () {
  if (typeof PlaywrightTest === 'undefined') return
  var pwE2eStarted = false
  var pwE2eStage = 'startup'
  var pwE2ePage = null
  var pwE2eWorld = String(PlaywrightTest.worldName())

  function pwAssert(condition, message) {
    if (!condition) throw new Error(message)
  }

  function pwField(value, key) {
    return value.get(key)
  }

  function pwStage(name, detail) {
    pwE2eStage = name
    PlaywrightTest.record(name, 'PASS', String(detail || ''))
    console.info('[PW-E2E] ' + name + ' PASS ' + String(detail || ''))
  }

  function pwScreen(type, timeout) {
    return pwE2ePage.gui().waitFor(type, timeout || 30000)
  }

  function pwClick(key) {
    pwE2eStage = 'click:' + key
    return pwE2ePage.locator('translation=' + key).click()
  }

  function pwFill(key, value) {
    // Labels and their textboxes can share a translation key; retain strict locators.
    return pwE2ePage.locator('translation=' + key).snapshot().then(function (result) {
      var matches = pwField(result, 'matches')
      var textbox = null
      for (var i = 0; i < matches.size(); i++) {
        var node = matches.get(i)
        if (String(pwField(node, 'role')) === 'textbox') {
          pwAssert(textbox === null, 'Multiple textboxes for ' + key)
          textbox = node
        }
      }
      pwAssert(textbox !== null, 'Missing textbox for ' + key)
      return pwE2ePage.locator('widget=' + String(pwField(textbox, 'id'))).fill(value)
    })
  }

  function pwImage(name) {
    // Actions can finish within the same tick, before the new screen has rendered.
    return pwE2ePage.waitForTimeout(150).then(function () {
      return pwE2ePage.screenshot().capture('test-results/' + name + '.png')
    }).then(function (value) {
      pwStage('screenshot:' + name, value)
      return value
    })
  }

  function pwCycle(selectorKey, valueKey, remaining) {
    return pwE2ePage.gui().snapshot().then(function (gui) {
      if (String(gui).indexOf(valueKey) >= 0) return gui
      pwAssert(remaining > 0, 'Cycle did not reach ' + valueKey + ': ' + String(gui))
      return pwClick(selectorKey).then(function () {
        return pwE2ePage.waitForTimeout(150)
      }).then(function () {
        return pwCycle(selectorKey, valueKey, remaining - 1)
      })
    })
  }

  function pwExpectedFailure(task, code, name) {
    var unexpectedSuccess = false
    return task.then(function () {
      unexpectedSuccess = true
      throw new Error('Expected ' + code + ' but task succeeded: ' + name)
    }).catchError(function (error) {
      pwAssert(!unexpectedSuccess, 'Negative test unexpectedly succeeded: ' + name)
      var actualCode = typeof error.getCodeName === 'function' ? String(error.getCodeName()) : ''
      pwAssert(actualCode === code, 'Unexpected failure: code=' + actualCode + ', ' + String(error))
      pwStage(name, actualCode + ': ' + String(error))
      return true
    })
  }

  function pwCommand(command) {
    return pwE2ePage.chat().command(command).then(function () {
      return pwE2ePage.waitForTimeout(350)
    })
  }

  function pwOpenWorld() {
    return pwClick('menu.singleplayer').then(function () {
      return pwScreen('SelectWorldScreen')
    }).then(function () {
      // Saved-world discovery is asynchronous even after the selection screen opens.
      return pwE2ePage.locator('world=' + pwE2eWorld).waitForVisible(30000)
    }).then(function () {
      return pwE2ePage.locator('world=' + pwE2eWorld).click()
    }).then(function () {
      return pwClick('selectWorld.select')
    }).then(function () {
      return pwE2ePage.status().ready(120000)
    }).then(function () {
      return pwE2ePage.waitForTimeout(1200)
    }).then(function () {
      return pwE2ePage.status().world()
    }).then(function (world) {
      pwAssert(String(pwField(world, 'levelName')) === pwE2eWorld, 'Rejoined wrong world: ' + String(world))
      pwStage('world-rejoined', world)
      return pwImage('rejoined')
    })
  }

  function pwLeaveWorld(name) {
    return pwE2ePage.input().press('escape').then(function () {
      return pwScreen('PauseScreen')
    }).then(function () {
      return pwImage(name + '-pause')
    }).then(function () {
      return pwClick('menu.returnToMenu')
    }).then(function () {
      return pwScreen('TitleScreen', 60000)
    }).then(function () {
      pwStage(name, 'Returned to title using Save and Quit control')
      return pwImage(name)
    })
  }

  function pwMain() {
    var startPosition = null
    var beforeTick = null
    var waitStarted = 0
    var measuredDelay = 0
    return pwScreen('TitleScreen', 120000).then(function () {
      // The title exists underneath the startup overlay before its fade-out finishes.
      return pwE2ePage.waitForTimeout(2500)
    }).then(function () {
      pwStage('title-ready', pwE2eWorld)
      return pwImage('title')
    }).then(function () {
      waitStarted = Date.now()
      return pwE2ePage.waitForTimeout(600).then(function () {
        measuredDelay = Date.now() - waitStarted
        pwAssert(measuredDelay >= 600, 'Nested task completed before wait elapsed: ' + measuredDelay)
        return 'nested-wait-finished'
      })
    }).then(function (value) {
      pwAssert(String(value) === 'nested-wait-finished', 'Nested task was not flattened: ' + String(value))
      pwStage('async-wait-propagated', measuredDelay + 'ms')
      return pwExpectedFailure(pwE2ePage.gui().waitFor('PW_SCREEN_THAT_DOES_NOT_EXIST', 450), 'TIMEOUT', 'timeout-propagated')
    }).then(function () {
      return pwExpectedFailure(pwE2ePage.locator('gui-text=PW_CONTROL_THAT_DOES_NOT_EXIST').click(), 'INVALID_ACTION', 'missing-control-propagated')
    }).then(function () {
      return pwClick('menu.singleplayer')
    }).then(function () {
      // Empty directories may open CreateWorldScreen directly; otherwise use Create New World.
      return pwE2ePage.waitForTimeout(800)
    }).then(function () {
      return pwE2ePage.gui().snapshot()
    }).then(function (gui) {
      if (String(pwField(gui, 'type')) === 'CreateWorldScreen') return gui
      return pwScreen('SelectWorldScreen').then(function () { return pwClick('selectWorld.create') })
    }).then(function () {
      return pwScreen('CreateWorldScreen', 60000)
    }).then(function () {
      pwStage('create-menu-ready', pwE2eWorld)
      return pwFill('selectWorld.enterName', pwE2eWorld)
    }).then(function () {
      return pwCycle('selectWorld.gameMode', 'selectWorld.gameMode.creative', 4)
    }).then(function () {
      return pwClick('createWorld.tab.world.title')
    }).then(function () {
      return pwCycle('selectWorld.mapType', 'generator.minecraft.flat', 8)
    }).then(function () {
      return pwFill('selectWorld.enterSeed', '246813579')
    }).then(function () {
      return pwClick('createWorld.tab.game.title')
    }).then(function () {
      return pwImage('create-configured')
    }).then(function () {
      return pwClick('selectWorld.create')
    }).then(function () {
      return pwE2ePage.status().ready(180000)
    }).then(function () {
      return pwE2ePage.waitForTimeout(1200)
    }).then(function () {
      return pwE2ePage.status().world()
    }).then(function (world) {
      pwAssert(String(pwField(world, 'levelName')) === pwE2eWorld, 'Created wrong world: ' + String(world))
      pwStage('world-created', world)
      return pwImage('joined')
    }).then(function () {
      return pwCommand('/tp @s 0.5 -60 0.5 0 0')
    }).then(function () {
      return pwE2ePage.status().position()
    }).then(function (position) {
      startPosition = position
      return pwE2ePage.move().direction('forward', 2)
    }).then(function () {
      return pwE2ePage.status().position()
    }).then(function (position) {
      var dx = Number(pwField(position, 'x')) - Number(pwField(startPosition, 'x'))
      var dz = Number(pwField(position, 'z')) - Number(pwField(startPosition, 'z'))
      pwAssert(Math.sqrt(dx * dx + dz * dz) >= 1.8, 'Movement had no real effect: ' + String(position))
      pwStage('movement-verified', 'before=' + String(startPosition) + '; after=' + String(position))
      return pwE2ePage.input().press('inventory')
    }).then(function () {
      return pwScreen('CreativeModeInventoryScreen')
    }).then(function () {
      pwStage('inventory-opened', 'Inventory key produced real screen')
      return pwImage('inventory')
    }).then(function () {
      return pwE2ePage.input().press('escape')
    }).then(function () {
      return pwE2ePage.waitForTimeout(250)
    }).then(function () {
      return pwE2ePage.gui().snapshot()
    }).then(function (gui) {
      pwAssert(!pwField(gui, 'open'), 'Escape did not close inventory: ' + String(gui))
      return pwCommand('/give @s minecraft:stone 8')
    }).then(function () {
      return pwE2ePage.inventory().snapshot()
    }).then(function (inventory) {
      pwAssert(String(inventory).indexOf('minecraft:stone') >= 0, 'Given stone absent: ' + String(inventory))
      pwStage('inventory-item-verified', inventory)
      return pwCommand('/tp @s 0.5 -60 0.5 0 30')
    }).then(function () {
      return pwCommand('/setblock 0 -60 3 minecraft:gold_block')
    }).then(function () {
      return pwE2ePage.block().get({x: 0, y: -60, z: 3})
    }).then(function (block) {
      pwAssert(String(pwField(block, 'block')) === 'minecraft:gold_block', 'Interaction fixture absent: ' + String(block))
      return pwE2ePage.look().at({x: 0.5, y: -59.5, z: 3.5})
    }).then(function () {
      return pwE2ePage.waitForTimeout(250)
    }).then(function () {
      return pwImage('block-before-attack')
    }).then(function () {
      return pwE2ePage.input().click(0)
    }).then(function () {
      return pwE2ePage.waitForTimeout(500)
    }).then(function () {
      return pwE2ePage.block().get({x: 0, y: -60, z: 3})
    }).then(function (block) {
      pwAssert(String(pwField(block, 'block')) === 'minecraft:air', 'Real left click did not break fixture: ' + String(block))
      pwStage('block-interaction-verified', block)
      return pwImage('block-after-attack')
    }).then(function () {
      pwE2eStage = 'wood-crafting'
      return playwrightWoodDoor(pwE2ePage, pwStage, pwImage)
    }).then(function () {
      pwE2eStage = 'container-regression'
      return playwrightContainerDebug(pwE2ePage, pwStage, pwImage)
    }).then(function () {
      return playwrightGameplayDebug(pwE2ePage, pwStage, pwImage, pwExpectedFailure)
    }).then(function () {
      return playwrightNavigationDebug(pwE2ePage, pwStage, pwImage, pwExpectedFailure)
    }).then(function () {
      return pwExpectedFailure(pwE2ePage.gui().waitFor('PW_SCREEN_THAT_DOES_NOT_EXIST', 450), 'TIMEOUT', 'world-timeout-propagated')
    }).then(function () {
      return pwE2ePage.status().world()
    }).then(function (time) {
      beforeTick = Number(pwField(time, 'gameTime'))
      return pwE2ePage.waitForTimeout(1000)
    }).then(function () {
      return pwE2ePage.status().world()
    }).then(function (time) {
      pwAssert(Number(pwField(time, 'gameTime')) > beforeTick, 'World ticks stalled')
      pwStage('world-ticks-verified', time)
      return pwLeaveWorld('world-left')
    }).then(function () {
      return pwOpenWorld()
    }).then(function () {
      return pwE2ePage.block().get({x: 0, y: -60, z: 3})
    }).then(function (block) {
      pwAssert(String(pwField(block, 'block')) === 'minecraft:air', 'Interaction did not persist across reload: ' + String(block))
      return pwE2ePage.inventory().snapshot()
    }).then(function (inventory) {
      pwAssert(String(inventory).indexOf('minecraft:stone') >= 0, 'Inventory did not persist across rejoin')
      pwStage('persistence-verified', inventory)
      return playwrightContainerDebugPersistence(pwE2ePage, pwStage, pwImage)
    }).then(function () {
      return playwrightWoodDoorPersistence(pwE2ePage, pwStage, pwImage)
    }).then(function () {
      return playwrightGameplayDebugPersistence(pwE2ePage, pwStage, pwImage)
    }).then(function () {
      PlaywrightTest.reloadAndProbe()
    })
  }

  // Forge's client scheduler runs at the title screen too; KubeJS ClientEvents.tick does not.
  Playwright.client().page().waitForTimeout(50).then(function () {
    if (pwE2eStarted) return
    pwE2eStarted = true
    try {
      pwE2ePage = Playwright.client().page()
      if (PlaywrightTest.wasReloaded()) {
        var reloadStopped = null
        pwE2ePage.waitForTimeout(500).then(function () {
          pwAssert(PlaywrightTest.reloadProbePassed(), 'Old tasks or movement inputs survived the real KubeJS reload')
          return pwE2ePage.status().position()
        }).then(function (value) {
          reloadStopped = {x: Number(value.get('x')), y: Number(value.get('y')), z: Number(value.get('z'))}
          return pwE2ePage.waitForTimeout(400)
        }).then(function () {
          return pwE2ePage.status().position()
        }).then(function (value) {
          var now = {x: Number(value.get('x')), y: Number(value.get('y')), z: Number(value.get('z'))}
          var dx = now.x - reloadStopped.x
          var dz = now.z - reloadStopped.z
          var drift = Math.sqrt(dx * dx + dz * dz)
          pwAssert(drift < 0.08, 'Movement continued after script reload: ' + drift)
          return pwE2ePage.input().keysDown().then(function (keys) {
            pwAssert(keys.get('keys').isEmpty(), 'Reload left keys pressed: ' + String(keys))
            pwStage('navigation-reload-inputs-released', JSON.stringify({keys: [], drift: drift, position: now}))
          })
        }).then(function () {
          pwStage('reload-new-script-executed', 'New KubeJS scope verified input release and stable real player position')
          return pwLeaveWorld('world-left-final')
        }).then(function () {
          PlaywrightTest.finish('PASS', 'All GUI, async, equipment, combat, navigation, rejoin and real script reload assertions completed')
        }).catchError(function (error) {
          PlaywrightTest.finish('FAIL', 'post-reload: ' + String(error))
        })
        return
      }
      PlaywrightTest.record('script-started', 'PASS', pwE2eWorld)
      pwMain().catchError(function (error) {
        var detail = pwE2eStage + ': ' + String(error)
        console.error('[PW-E2E] FAILED ' + detail)
        pwE2ePage.screenshot().capture('test-results/failure.png').then(function () {
          PlaywrightTest.finish('FAIL', detail)
        }).catchError(function (captureError) {
          PlaywrightTest.finish('FAIL', detail + '; screenshot=' + String(captureError))
        })
      })
    } catch (error) {
      PlaywrightTest.finish('FAIL', pwE2eStage + ': ' + String(error))
    }
  }).catchError(function (error) {
    PlaywrightTest.finish('FAIL', 'startup-dispatch: ' + String(error))
  })
})()
