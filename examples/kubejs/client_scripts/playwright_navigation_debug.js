// Existing move.to is a target follower, not an autonomous route planner.
// Commands build isolated obstacles; every tested journey uses normal movement inputs.
function playwrightNavigationDebug(page, stage, image, expectedFailure) {
  var progress = 'navigation-start'
  var active = null

  function require(condition, detail) {
    if (!condition) throw new Error(progress + ': ' + detail)
  }

  function mark(name, detail) {
    progress = name
    stage(name, JSON.stringify(detail))
  }

  function position(value) {
    return {x: Number(value.get('x')), y: Number(value.get('y')), z: Number(value.get('z'))}
  }

  function keyNames(value) {
    var keys = value.get('keys')
    var names = []
    for (var i = 0; i < keys.size(); i++) names.push(String(keys.get(i)))
    return names
  }

  function command(text) {
    return page.chat().command(text).then(function () { return page.waitForTimeout(350) })
  }

  function checkBlock(x, y, z, item) {
    return page.block().get({x: x, y: y, z: z}).then(function (block) {
      require(String(block.get('block')) === item, 'Fixture block mismatch: ' + String(block))
    })
  }

  function assertReleased(name) {
    var stopped = null
    return page.waitForTimeout(400).then(function () {
      return page.input().keysDown()
    }).then(function (keys) {
      require(keys.get('keys').isEmpty(), name + ' left keys down: ' + String(keys))
      return page.status().position()
    }).then(function (value) {
      stopped = position(value)
      return page.waitForTimeout(400)
    }).then(function () {
      return page.status().position()
    }).then(function (value) {
      var now = position(value)
      var dx = now.x - stopped.x
      var dz = now.z - stopped.z
      var drift = Math.sqrt(dx * dx + dz * dz)
      require(drift < 0.08, name + ' still moving after inputs released: ' + drift)
      stage(name + '-inputs-released', JSON.stringify({keys: [], drift: drift, position: now}))
    })
  }

  function follow(name, target, timeout, arrived) {
    var samples = []
    var began = Date.now()
    progress = name
    active = page.move().to(target, timeout)

    function poll(remaining) {
      var sample = null
      return page.status().position().then(function (value) {
        sample = position(value)
        sample.timeMs = Date.now() - began
        return page.input().keysDown()
      }).then(function (keys) {
        sample.keys = keyNames(keys)
        samples.push(sample)
        if (active.isDone()) return active
        if (remaining <= 0) {
          active.cancel()
          throw new Error(name + ' exceeded bounded trajectory sampling')
        }
        return page.waitForTimeout(100).then(function () { return poll(remaining - 1) })
      })
    }

    return poll(Math.ceil(timeout / 100) + 40).then(function (value) {
      active = null
      var finalPos = position(value.get('finalPos'))
      var dx = finalPos.x - target.x
      var dz = finalPos.z - target.z
      var horizontal = Math.sqrt(dx * dx + dz * dz)
      var result = {
        arrived: String(value.get('arrived')) === 'true', reason: String(value.get('reason')),
        strategy: String(value.get('strategy')), pathPlanned: String(value.get('pathPlanned')) === 'true',
        distance: Number(value.get('distance')), bestDistance: Number(value.get('bestDistance')),
        elapsedTicks: Number(value.get('elapsedTicks')), timeoutTicks: Number(value.get('timeoutTicks')),
        stalledTicks: Number(value.get('stalledTicks')), strafeAttempts: Number(value.get('strafeAttempts')),
        target: target, finalPos: finalPos, horizontalDistance: horizontal, samples: samples
      }
      require(result.arrived === arrived, 'Unexpected arrival: ' + JSON.stringify(result))
      require(result.pathPlanned === false, 'Target follower falsely claims a planned path')
      require(result.elapsedTicks <= result.timeoutTicks, 'Tick deadline exceeded')
      require(result.reason === (arrived ? 'ARRIVED' : 'DEADLINE_EXPIRED'), 'Wrong completion reason')
      if (arrived) require(horizontal < 0.75 && Math.abs(finalPos.y - target.y) < 1.25, 'Arrival position incorrect')
      else require(result.elapsedTicks === result.timeoutTicks, 'Non-arrival finished before its deadline')
      if (name === 'navigation-obstacle-limited') require(result.strafeAttempts > 0, 'No local anti-stall attempts were observed')
      mark(name, result)
      return assertReleased(name)
    })
  }

  function interrupt(name, timeout) {
    progress = name
    var held = []
    return page.gui().info().then(function (gui) {
      if (!gui.get('open')) return
      require(String(gui.get('type')) === 'PauseScreen', 'Unexpected GUI before input probe: ' + String(gui))
      return page.gui().getByTranslation('menu.returnToGame').click()
    }).then(function () {
      return page.gui().info()
    }).then(function (gui) {
      require(!gui.get('open'), 'Input probe requires the real game screen')
      active = page.move().to({x: 40.5, y: -60, z: 35.5}, 10000)
      // Read acquired input immediately; a later GUI transition correctly releases it.
      return page.input().keysDown()
    }).then(function (keys) {
      held = keyNames(keys)
      require(held.indexOf('forward') >= 0 && !active.isDone(), 'Input probe did not acquire a running forward movement')
      return page.status().position()
    }).then(function (value) {
      require(!active.isDone(), 'Navigation completed before interruption')
      stage(name + '-started', JSON.stringify({keys: held, running: true, position: position(value)}))
      if (timeout) active.timeout(300)
      else require(active.cancel(), 'Running navigation could not be cancelled')
      return expectedFailure(active, timeout ? 'TIMEOUT' : 'CANCELLED', name)
    }).then(function () {
      active = null
      return assertReleased(name)
    })
  }

  return command('/tp @s 40.5 -60 -5.5 0 0').then(function () {
    return follow('navigation-flat-arrived', {x: 40.5, y: -60, z: -1.5}, 5000, true)
  }).then(function () {
    return command('/fill 38 -60 2 42 -58 2 minecraft:stone')
  }).then(function () {
    return checkBlock(40, -60, 2, 'minecraft:stone')
  }).then(function () {
    return checkBlock(40, -58, 2, 'minecraft:stone')
  }).then(function () {
    return command('/tp @s 40.5 -60 0.5 0 0')
  }).then(function () {
    return page.look().at({x: 40.5, y: -59, z: 2.5})
  }).then(function () {
    return image('navigation-obstacle')
  }).then(function () {
    // Cannot plan around this wall: finite failure is expected, not a success.
    return follow('navigation-obstacle-limited', {x: 40.5, y: -60, z: 4.5}, 2000, false)
  }).then(function () {
    return image('navigation-limited')
  }).then(function () {
    return command('/tp @s 40.5 -60 0.5 0 0')
  }).then(function () {
    // Explicit user-provided waypoints; this proves route execution, not route planning.
    return follow('navigation-waypoint-west', {x: 36.5, y: -60, z: 0.5}, 5000, true)
  }).then(function () {
    return follow('navigation-waypoint-north', {x: 36.5, y: -60, z: 5.5}, 5000, true)
  }).then(function () {
    return follow('navigation-waypoints-arrived', {x: 40.5, y: -60, z: 5.5}, 5000, true)
  }).then(function () {
    return page.look().at({x: 40.5, y: -59, z: 2.5})
  }).then(function () {
    return image('navigation-waypoints')
  }).then(function () {
    return command('/tp @s 40.5 -60 0.5 0 0')
  }).then(function () {
    return follow('navigation-sealed-timeout', {x: 40.5, y: -60, z: 2.5}, 1000, false)
  }).then(function () {
    return command('/tp @s 40.5 -60 0.5 0 0')
  }).then(function () {
    return expectedFailure(page.move().direction('forward', 3), 'PATHFINDING_FAILED', 'navigation-wall-failed')
  }).then(function () {
    return assertReleased('navigation-wall-failed')
  }).then(function () {
    return command('/tp @s 40.5 -60 10.5 0 0')
  }).then(function () {
    return interrupt('navigation-cancelled', false)
  }).then(function () {
    return command('/tp @s 40.5 -60 10.5 0 0')
  }).then(function () {
    return interrupt('navigation-wall-clock-timeout', true)
  }).then(function () {
    return command('/tp @s 0.5 -60 0.5 0 0')
  }).catchError(function (error) {
    if (active !== null && !active.isDone()) active.cancel()
    throw new Error(progress + ': ' + String(error))
  })
}
