"""Bounded real Forge graphical-client runner. Requires Python 3 and psutil.

Each invocation uses a fresh directory and refuses to overwrite existing worlds.
Success requires every assertion event, nonempty framebuffer PNGs and exit code 0.
Example: python examples/run_client_e2e.py --suite --java-home <jdk17>
"""
import argparse
import datetime
import json
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import time

import psutil

ROOT = Path(__file__).resolve().parents[1]
REQUIRED = [
    "script-started", "title-ready", "async-wait-propagated", "timeout-propagated",
    "missing-control-propagated", "create-menu-ready", "world-created",
    "movement-verified", "inventory-opened", "inventory-item-verified",
    "block-interaction-verified", "wood-obtained", "planks-crafted",
    "crafting-table-crafted", "crafting-table-placed", "crafting-table-opened",
    "wooden-doors-crafted", "wooden-door-placed",
    "container-chest-opened", "container-item-moved", "container-reopened",
    "equipment-equipped", "equipment-removed-restored", "equipment-hands-swapped",
    "equipment-invalid-slot", "equipment-damage-verified", "combat-target-ready",
    "combat-missing-entity", "combat-distance-filter", "combat-damage-verified", "combat-target-defeated",
    "navigation-flat-arrived", "navigation-flat-arrived-inputs-released",
    "navigation-obstacle-limited", "navigation-obstacle-limited-inputs-released",
    "navigation-waypoint-west", "navigation-waypoint-west-inputs-released",
    "navigation-waypoint-north", "navigation-waypoint-north-inputs-released",
    "navigation-waypoints-arrived", "navigation-waypoints-arrived-inputs-released",
    "navigation-sealed-timeout", "navigation-sealed-timeout-inputs-released",
    "navigation-wall-failed", "navigation-wall-failed-inputs-released",
    "navigation-cancelled-started", "navigation-cancelled", "navigation-cancelled-inputs-released",
    "navigation-wall-clock-timeout-started", "navigation-wall-clock-timeout", "navigation-wall-clock-timeout-inputs-released",
    "world-timeout-propagated", "world-ticks-verified",
    "world-left", "world-rejoined", "persistence-verified", "container-persistence-verified",
    "wooden-door-persistence-verified", "gameplay-equipment-persistence-verified",
    "reload-started", "reload-propagated", "navigation-reload-inputs-released", "reload-new-script-executed",
    "world-left-final",
]
SCREENSHOTS = [
    "title", "create-configured", "joined", "inventory", "block-before-attack",
    "block-after-attack", "world-left-pause", "world-left", "rejoined",
    "world-left-final-pause", "world-left-final",
    "wood-planks-recipe", "wood-table-recipe", "wood-table-placed",
    "wood-door-recipe", "wood-door-placed", "wood-door-rejoined",
    "container-filled", "container-reopened", "container-rejoined",
    "equipment-equipped", "equipment-damaged", "equipment-rejoined", "combat-before", "combat-after",
    "navigation-obstacle", "navigation-limited", "navigation-waypoints",
]


def events_from(path):
    if not path.exists():
        return []
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def validate_events(events, run_id):
    errors = []
    if any(event.get("runId") != run_id for event in events):
        errors.append("Evidence belongs to a different run")
    stages = [event.get("stage") for event in events]
    positions = []
    for stage in REQUIRED:
        matches = [index for index, event in enumerate(events)
                   if event.get("stage") == stage and event.get("status") == "PASS"]
        if len(matches) != 1:
            errors.append(f"Expected exactly one PASS stage: {stage}, got {len(matches)}")
        else:
            positions.append(matches[0])
    if positions != sorted(positions):
        errors.append("Stages completed out of order")
    terminal = [event for event in events if event.get("stage") == "terminal"]
    if len(terminal) != 1 or terminal[0].get("status") != "PASS":
        errors.append("Missing unique terminal PASS (loading scripts is not success)")
        errors.extend(f"Client failure: {event.get('detail', 'unknown error')}"
                      for event in terminal if event.get("status") == "FAIL")
    elif stages[-1] != "terminal":
        errors.append("Evidence arrived after terminal result")
    return errors


def _debug_require(condition, message):
    if not condition:
        raise ValueError(message)


def _debug_number(value, label, integer=False):
    _debug_require(type(value) in (int, float) and float("-inf") < value < float("inf"),
                   f"{label} must be a finite number")
    if integer:
        # Rhino serializes JavaScript Number as e.g. 39.0 even for integer fields.
        _debug_require(value == int(value), f"{label} must be an integer")
    return value


def _debug_detail(event):
    def unique_object(pairs):
        result = {}
        for key, value in pairs:
            _debug_require(key not in result, f"Duplicate evidence field: {key}")
            result[key] = value
        return result

    def check_numbers(value):
        if type(value) in (int, float):
            _debug_number(value, "Evidence value")
        elif isinstance(value, dict):
            for child in value.values():
                check_numbers(child)
        elif isinstance(value, list):
            for child in value:
                check_numbers(child)

    detail = json.loads(event["detail"], object_pairs_hook=unique_object)
    _debug_require(isinstance(detail, dict), "Expected an evidence object")
    check_numbers(detail)
    return detail


def _debug_equipment(value, missing_head=False, reversed_hands=False, slots=True):
    names = ("head", "chest", "legs", "feet", "mainhand", "offhand")
    items = ("iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots", "iron_sword", "shield")
    expected_slots = (39, 38, 37, 36, 4, 40)
    equipment = value["equipment"]
    _debug_require(isinstance(equipment, dict) and set(equipment) == set(names), "Missing equipment slots")
    for index, name in enumerate(names):
        entry = equipment[name]
        _debug_require(isinstance(entry, dict), f"Malformed ItemData: {name}")
        item = "air" if missing_head and name == "head" else items[index]
        if reversed_hands and index >= 4:
            item = items[9 - index]
        _debug_require(entry["item"] == "minecraft:" + item, f"Wrong equipped item: {name}")
        count = _debug_number(entry["count"], "Item count", integer=True)
        damage = _debug_number(entry["damage"], "Item damage", integer=True)
        maximum = _debug_number(entry["maxDamage"], "Item maxDamage", integer=True)
        remaining = _debug_number(entry["remainingDurability"], "Item remainingDurability", integer=True)
        empty = item == "air"
        _debug_require(count == (0 if empty else 1) and entry["damageable"] is (not empty),
                       f"Wrong item count/damageable: {name}")
        _debug_require((damage == maximum == remaining == 0) if empty else
                       (0 <= damage < maximum and remaining == maximum - damage),
                       f"Inconsistent item durability: {name}")
        if slots:
            _debug_require(_debug_number(entry["slot"], "Item slot", integer=True) == expected_slots[index],
                           f"Wrong inventory equipment mapping: {name}")
    armor = _debug_number(value["armorValue"], "Armor value")
    _debug_require(armor == (13 if missing_head else 15), "Wrong measured armor value")


def _debug_player(value, missing_head=False, reversed_hands=False):
    _debug_equipment(value, missing_head, reversed_hands)
    status = value["status"]
    for key in ("health", "maxHealth", "food", "saturation", "armor", "absorption", "attackStrength"):
        _debug_number(status[key], "Player " + key)
    _debug_require(0 < status["health"] <= status["maxHealth"] and 0 <= status["food"] <= 20 and
                   status["saturation"] >= 0 and status["absorption"] >= 0 and
                   0 <= status["attackStrength"] <= 1 and status["armor"] == value["armorValue"],
                   "Inconsistent player health/armor/status")


def _debug_item_equal(first, second):
    return all(first[key] == second[key] for key in
               ("item", "count", "damageable", "damage", "maxDamage", "remainingDurability"))


def _debug_player_equal(first, second):
    # Cooldown and hunger may change between independent synchronized reads.
    return (first["equipment"] == second["equipment"] and first["armorValue"] == second["armorValue"] and
            all(first["status"][key] == second["status"][key] for key in
                ("health", "maxHealth", "armor", "absorption")))


def _debug_target(value, identity=None):
    _debug_require(isinstance(value, dict), "Malformed target")
    _debug_require(_debug_number(value["id"], "Target id", integer=True) >= 0 and
                   isinstance(value["uuid"], str) and
                   re.fullmatch(r"[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}", value["uuid"]) is not None,
                   "Missing target identity")
    _debug_require(value["name"] == "PW_DEBUG_TARGET" and value["type"] == "minecraft:pig",
                   "Wrong combat target")
    _debug_require(type(value["alive"]) is bool and type(value["missing"]) is bool, "Malformed target flags")
    health = _debug_number(value["health"], "Target health")
    _debug_require(0 <= health <= 20 and value["alive"] is (health > 0), "Inconsistent target life/health")
    _debug_require(value["missing"] is False, "Missing target is not observed death evidence")
    _debug_require(_debug_number(value["maxHealth"], "Target maxHealth") == 20, "Wrong target maximum health")
    for axis in ("x", "y", "z"):
        _debug_number(value[axis], "Target " + axis)
    if identity is not None:
        _debug_require(value["id"] == identity["id"] and value["uuid"] == identity["uuid"],
                       "Combat target identity changed")


def _debug_hit(hit, identity, attack, before_health, before_player):
    _debug_require(_debug_number(hit["attack"], "Attack number", integer=True) == attack,
                   "Attack numbers are not continuous")
    _debug_require(hit["id"] == identity["id"] and hit["uuid"] == identity["uuid"], "Hit identity mismatch")
    _debug_number(hit["id"], "Hit id", integer=True)
    target_before, target_after = hit["targetBeforeAttack"], hit["targetAfterAttack"]
    _debug_target(target_before, identity)
    _debug_target(target_after, identity)
    _debug_player(hit["before"])
    _debug_player(hit["after"])
    _debug_require(_debug_player_equal(hit["before"], before_player), "Discontinuous player/equipment evidence")
    for key in ("beforeHealth", "afterHealth", "beforeDamage", "afterDamage"):
        _debug_number(hit[key], "Hit " + key, integer=key.endswith("Damage"))
    _debug_require(hit["beforeHealth"] == before_health == target_before["health"] and
                   target_before["alive"] and hit["afterHealth"] == target_after["health"] < before_health,
                   "Attack did not reduce the same target's health")
    _debug_require(hit["alive"] is target_after["alive"] and hit["missing"] is target_after["missing"],
                   "Hit life flags disagree with target")
    before, after = hit["before"], hit["after"]
    sword_before, sword_after = before["equipment"]["mainhand"], after["equipment"]["mainhand"]
    _debug_require(hit["beforeDamage"] == sword_before["damage"] and
                   hit["afterDamage"] == sword_after["damage"] > sword_before["damage"] and
                   sword_before["maxDamage"] == sword_after["maxDamage"],
                   "Attack did not consume observed sword durability")
    _debug_require(all(_debug_item_equal(before["equipment"][name], after["equipment"][name])
                       for name in ("head", "chest", "legs", "feet", "offhand")) and
                   all(before["status"][key] == after["status"][key]
                       for key in ("health", "maxHealth", "armor", "absorption")),
                   "Unexplained equipment/player change during attack")


def _debug_container_stack(value, label, expected_item, expected_count, has_slot=False):
    _debug_require(isinstance(value, dict), label + " must be a structured item snapshot")
    expected_keys = {"slot", "item", "count", "empty"} if has_slot else {"item", "count", "empty"}
    _debug_require(set(value) == expected_keys, label + " has missing or unexpected fields")
    if has_slot:
        _debug_require(_debug_number(value["slot"], label + " slot", integer=True) >= 0,
                       label + " has an invalid slot index")
    count = _debug_number(value["count"], label + " count", integer=True)
    _debug_require(value["item"] == expected_item and count == expected_count and
                   type(value["empty"]) is bool and value["empty"] is (expected_item == "minecraft:air"),
                   label + " contradicts the observed item/count")


def _validate_container_debug_events(events):
    names = ("container-chest-opened", "container-item-moved", "container-reopened",
             "container-persistence-verified")
    if not any(event["stage"] in names for event in events):
        return []
    try:
        selected = {}
        for name in names:
            matches = [(index, event) for index, event in enumerate(events) if event["stage"] == name]
            _debug_require(len(matches) == 1, "Missing or duplicate container stage: " + name)
            selected[name] = matches[0]
        indices = [selected[name][0] for name in names]
        _debug_require(indices == sorted(indices), "Container stages completed out of order")
        timestamps = []
        for name in names:
            index, event = selected[name]
            _debug_require(event["status"] == "PASS" and event["inWorld"] is True,
                           "Container stage did not pass in a real world: " + name)
            gui = event["gui"]
            _debug_require(isinstance(gui, dict) and gui["open"] is True and gui["type"] == "ContainerScreen",
                           "Container stage was not observed on the real ChestScreen: " + name)
            detail = _debug_detail(event)
            _debug_require(detail["screenType"] == "ContainerScreen", "Wrong measured container screen: " + name)
            _debug_require(_debug_number(detail["containerId"], "Container id", integer=True) >= 0 and
                           _debug_number(detail["slotCount"], "Container slot count", integer=True) == 63 and
                           _debug_number(detail["chestSlots"], "Chest slot count", integer=True) == 27 and
                           _debug_number(detail["playerSlots"], "Player inventory slot count", integer=True) == 36,
                           "Unexpected vanilla chest/player slot layout: " + name)
            stamp = _debug_number(event["timeMs"], "Container event timeMs")
            _debug_require(stamp >= 0, "Negative container event timeMs")
            timestamps.append(stamp)
            selected[name] = (index, event, detail)
        _debug_require(timestamps == sorted(timestamps), "Container event timestamps are out of order")
        opened = selected[names[0]][2]
        moved = selected[names[1]][2]
        reopened = selected[names[2]][2]
        persisted = selected[names[3]][2]
        block = opened["chestBlock"]
        _debug_require(isinstance(block, dict) and block["block"] == "minecraft:chest" and
                       tuple(_debug_number(block[axis], "Fixture chest " + axis, integer=True)
                             for axis in ("x", "y", "z")) == (6, -60, 2),
                       "Chest screen has no matching isolated world fixture")
        source = _debug_number(moved["sourceSlot"], "Source inventory slot", integer=True)
        target = _debug_number(moved["targetSlot"], "Target chest slot", integer=True)
        _debug_require(27 <= source < 63 and target == 13 and
                       _debug_number(moved["clickButton"], "Container click button", integer=True) == 0 and
                       moved["containerId"] == opened["containerId"] and moved["slotCount"] == opened["slotCount"],
                       "Transfer did not use a player slot and a chest slot in the open menu")
        _debug_container_stack(moved["sourceBefore"], "Source before pickup", "minecraft:emerald", 3, True)
        _debug_container_stack(moved["targetBefore"], "Chest target before transfer", "minecraft:air", 0, True)
        _debug_require(moved["sourceBefore"]["slot"] == source and moved["targetBefore"]["slot"] == target,
                       "Item snapshots do not match their declared menu slots")
        _debug_container_stack(moved["cursorAfterPickup"], "Cursor after real pickup", "minecraft:emerald", 3)
        _debug_container_stack(moved["sourceAfter"], "Source after transfer", "minecraft:air", 0, True)
        _debug_container_stack(moved["targetAfter"], "Chest after transfer", "minecraft:emerald", 3, True)
        _debug_container_stack(moved["carriedAfter"], "Cursor after transfer", "minecraft:air", 0)
        _debug_require(_debug_number(moved["targetAfter"]["slot"], "Transferred chest slot", integer=True) == target,
                       "Transferred item was observed in a different chest slot")
        for name, detail in ((names[2], reopened), (names[3], persisted)):
            _debug_require(_debug_number(detail["targetSlot"], "Reopened chest slot", integer=True) == target,
                           "Reopened chest was checked at a different slot: " + name)
            _debug_container_stack(detail["target"], "Reopened chest item: " + name,
                                   "minecraft:emerald", 3, True)
            _debug_container_stack(detail["carried"], "Reopened cursor: " + name,
                                   "minecraft:air", 0)
        persistence_index, persistence_event, persistence_detail = selected[names[3]]
        _debug_require(persisted["worldRejoined"] is True and
                       persisted["containerId"] >= 0 and persisted["chestBlock"] == block,
                       "Container contents were not verified after a world rejoin")
        rejoined = [(index, event) for index, event in enumerate(events) if event["stage"] == "world-rejoined"]
        _debug_require(len(rejoined) == 1 and rejoined[0][1]["status"] == "PASS" and
                       rejoined[0][0] < persistence_index,
                       "Container persistence evidence did not follow a real world rejoin")
    except (ValueError, KeyError, TypeError, OverflowError, RecursionError) as error:
        return [f"Invalid measured container evidence: {error}"]
    return []


def _validate_gameplay_debug_events(events):
    stages = ("equipment-equipped", "equipment-removed-restored", "equipment-hands-swapped",
              "equipment-damage-verified", "combat-target-ready", "combat-damage-verified",
              "combat-target-defeated", "gameplay-equipment-persistence-verified")
    negative = {"equipment-invalid-slot": "SLOT_OUT_OF_RANGE", "combat-missing-entity": "ENTITY_NOT_FOUND",
                "combat-distance-filter": "ENTITY_NOT_FOUND"}
    errors, measured, seen = [], {}, []
    for event in events:
        name = event.get("stage")
        if name not in stages and name not in negative:
            continue
        try:
            _debug_require(name not in seen, "Duplicate measured stage")
            seen.append(name)
            if name in negative:
                _debug_require(isinstance(event["detail"], str) and
                               event["detail"].startswith(negative[name] + ": "), "Missing observed failure code")
                continue
            detail = _debug_detail(event)
            index = stages.index(name)
            _debug_require(index == 0 or stages[index - 1] in measured, "Missing or out-of-order preceding evidence")
            if name == "equipment-equipped":
                _debug_player(detail)
                _debug_require(all(entry["damage"] == 0 for entry in detail["equipment"].values()),
                               "Newly equipped items are not pristine")
            elif name == "equipment-removed-restored":
                before, removed, restored = detail["before"], detail["removed"], detail["restored"]
                _debug_player(before)
                _debug_player(removed, missing_head=True)
                _debug_player(restored)
                destination = _debug_number(detail["emptyDestination"], "Empty GUI destination", integer=True)
                _debug_require(9 <= destination <= 44 and destination != 40, "Invalid empty helmet destination")
                _debug_require(_debug_player_equal(before, measured[stages[0]]) and
                               _debug_player_equal(before, restored) and
                               all(_debug_item_equal(before["equipment"][slot], removed["equipment"][slot])
                                   for slot in ("chest", "legs", "feet", "mainhand", "offhand")) and
                               all(before["status"][key] == removed["status"][key]
                                   for key in ("health", "maxHealth", "absorption")),
                               "Helmet removal/restoration evidence is inconsistent")
            elif name == "equipment-hands-swapped":
                before, swapped, restored = detail["before"], detail["swapped"], detail["restored"]
                _debug_player(before)
                _debug_player(swapped, reversed_hands=True)
                _debug_player(restored)
                _debug_require(_debug_player_equal(before, measured[stages[1]]["restored"]) and
                               _debug_player_equal(before, restored) and
                               all(_debug_item_equal(before["equipment"][slot], swapped["equipment"][slot])
                                   for slot in ("head", "chest", "legs", "feet")) and
                               _debug_item_equal(before["equipment"]["mainhand"], swapped["equipment"]["offhand"]) and
                               _debug_item_equal(before["equipment"]["offhand"], swapped["equipment"]["mainhand"]) and
                               all(before["status"][key] == swapped["status"][key]
                                   for key in ("health", "maxHealth", "absorption")),
                               "Hand swap/restoration evidence is inconsistent")
            elif name == "equipment-damage-verified":
                before, after, armor = detail["before"], detail["after"], detail["damagedArmor"]
                _debug_player(before)
                _debug_player(after)
                _debug_require(detail["fixture"] == "controlled damage fixture" and
                               detail["command"] == "/damage @s 4 minecraft:mob_attack", "Wrong controlled damage fixture")
                _debug_require(armor in ("head", "chest", "legs", "feet"), "Missing damaged worn armor")
                for key in ("beforeHealth", "afterHealth", "beforeDamage", "afterDamage"):
                    _debug_number(detail[key], "Controlled damage " + key, integer=key.endswith("Damage"))
                _debug_require(_debug_player_equal(before, measured[stages[2]]["restored"]) and
                               detail["beforeHealth"] == before["status"]["health"] > 4 and
                               detail["afterHealth"] == after["status"]["health"] < detail["beforeHealth"] and
                               detail["beforeDamage"] == before["equipment"][armor]["damage"] and
                               detail["afterDamage"] == after["equipment"][armor]["damage"] > detail["beforeDamage"] and
                               all(before["status"][key] == after["status"][key]
                                   for key in ("maxHealth", "absorption")),
                               "Controlled damage lacks real health/armor loss")
                for slot in before["equipment"]:
                    first, last = before["equipment"][slot], after["equipment"][slot]
                    _debug_require(first["maxDamage"] == last["maxDamage"] and last["damage"] >= first["damage"],
                                   "Armor durability reversed or maximum changed")
                    if slot in ("mainhand", "offhand"):
                        _debug_require(_debug_item_equal(first, last), "Controlled damage changed held items")
            elif name == "combat-target-ready":
                _debug_target(detail["target"])
                _debug_player(detail["player"])
                target = detail["target"]
                _debug_require(detail["fixture"] == "open-front glass knockback boundary" and
                               target["alive"] and target["health"] == 20 and
                               all(abs(target[axis] - coordinate) < 0.1
                                   for axis, coordinate in (("x", 20.5), ("y", -60), ("z", 2.5))) and
                               _debug_player_equal(detail["player"], measured[stages[3]]["after"]),
                               "Initial target/player fixture does not match measured state")
            elif name == "combat-damage-verified":
                ready = measured[stages[4]]
                _debug_hit(detail, ready["target"], 1, ready["target"]["health"], ready["player"])
            elif name == "combat-target-defeated":
                ready = measured[stages[4]]
                _debug_target(detail["target"], ready["target"])
                _debug_player(detail["player"])
                attacks = _debug_number(detail["attacks"], "Total attacks", integer=True)
                hits = detail["hits"]
                _debug_require(isinstance(hits, list) and 1 <= attacks <= 8 and len(hits) == attacks,
                               "Missing bounded real attack history")
                _debug_require(hits[0] == measured[stages[5]], "First hit disagrees with damage stage")
                health, player = ready["target"]["health"], ready["player"]
                for attack, hit in enumerate(hits, 1):
                    _debug_hit(hit, ready["target"], attack, health, player)
                    health, player = hit["afterHealth"], hit["after"]
                for key in ("beforeHealth", "afterHealth", "beforeDamage", "afterDamage"):
                    _debug_number(detail[key], "Defeat " + key, integer=key.endswith("Damage"))
                _debug_number(detail["id"], "Defeated id", integer=True)
                _debug_require(detail["id"] == ready["target"]["id"] and detail["uuid"] == ready["target"]["uuid"] and
                               detail["alive"] is False and detail["target"]["alive"] is False and
                               detail["missing"] is detail["target"]["missing"] and
                               detail["beforeHealth"] == ready["target"]["health"] and
                               detail["afterHealth"] == detail["target"]["health"] == health == 0 and
                               hits[-1]["alive"] is False and
                               detail["beforeDamage"] == ready["player"]["equipment"]["mainhand"]["damage"] and
                               detail["afterDamage"] == player["equipment"]["mainhand"]["damage"] and
                               _debug_player_equal(detail["player"], player),
                               "Defeat summary contradicts observed attack/death history")
            else:
                expected, actual = detail["expected"], detail["actual"]
                final = measured[stages[6]]
                _debug_equipment(expected)
                _debug_equipment(actual, slots=False)
                _debug_require(expected["equipment"] == final["player"]["equipment"] and
                               expected["armorValue"] == final["player"]["armorValue"], "Forged persistence expectation")
                for slot in expected["equipment"]:
                    _debug_require(_debug_item_equal(expected["equipment"][slot], actual["equipment"][slot]),
                                   "Equipment/durability did not persist: " + slot)
                for value, keys in ((expected, ("health",)),
                                    (actual, ("health", "armor", "absorption", "attackStrength"))):
                    for key in keys:
                        _debug_number(value[key], "Persistence " + key)
                _debug_require(expected["health"] == actual["health"] == final["player"]["status"]["health"] and
                               actual["armor"] == actual["armorValue"] == expected["armorValue"] and
                               actual["absorption"] >= 0 and 0 <= actual["attackStrength"] <= 1 and
                               actual["targetAbsent"] is True, "Rejoined health/armor/target contradicts final state")
                for target in (expected["target"], actual["target"]):
                    _debug_number(target["id"], "Persisted target id", integer=True)
                    _debug_require(target["id"] == final["id"] and target["uuid"] == final["uuid"] and
                                   target["name"] == "PW_DEBUG_TARGET" and target["alive"] is False,
                                   "Persistence target identity/death mismatch")
            measured[name] = detail
        except (ValueError, KeyError, TypeError, OverflowError, RecursionError) as error:
            errors.append(f"Invalid measured gameplay evidence: {name}: {error}")
    return errors


def _debug_position(value, label):
    _debug_require(isinstance(value, dict), f"{label} must be a measured position")
    for axis in ("x", "y", "z"):
        _debug_number(value[axis], label + " " + axis)
    return value


def _debug_keys(value, label):
    # A released/end-of-task snapshot may be empty; every recorded key must be a name.
    _debug_require(isinstance(value, list) and
                   all(isinstance(key, str) and key.strip() for key in value),
                   f"{label} must be a list of nonempty key names")
    _debug_require(len(value) == len(set(value)), f"{label} contains duplicate keys")
    return value


def _debug_distance(first, last, axes=("x", "y", "z")):
    return sum((last[axis] - first[axis]) ** 2 for axis in axes) ** 0.5


def _validate_reload_debug_events(events):
    names = ("reload-started", "reload-propagated", "navigation-reload-inputs-released",
             "reload-new-script-executed")
    if not any(event["stage"] in names for event in events):
        return []
    try:
        selected = {}
        for name in names:
            matches = [(index, event) for index, event in enumerate(events) if event["stage"] == name]
            _debug_require(len(matches) == 1 if name in names[:2] else len(matches) <= 1,
                           "Missing or duplicate reload stage: " + name)
            if matches:
                selected[name] = matches[0]
        ordered = [selected[name][0] for name in names if name in selected]
        _debug_require(ordered == sorted(ordered), "Reload stages completed out of order")
        timestamps = []
        for name in names:
            if name in selected:
                event = selected[name][1]
                _debug_require(event["status"] == "PASS", "Reload stage is not PASS: " + name)
                stamp = _debug_number(event["timeMs"], "Reload event timeMs")
                _debug_require(stamp >= 0, "Negative reload event timeMs")
                timestamps.append(stamp)
        _debug_require(timestamps == sorted(timestamps), "Reload timestamps are out of order")
        started = _debug_detail(selected[names[0]][1])
        propagated = _debug_detail(selected[names[1]][1])
        old = _debug_number(started["generation"], "Old generation", integer=True)
        new = _debug_number(propagated["currentGeneration"], "Current generation", integer=True)
        previous = _debug_number(propagated["oldGeneration"], "Propagated old generation", integer=True)
        _debug_require(old >= 0 and previous == old and new > old, "Reload generation did not advance consistently")
        tasks = started["tasks"]
        expected_tasks = {"wait", "chain", "navigation", "hold"}
        _debug_require(isinstance(tasks, list) and len(tasks) == 4 and
                       all(isinstance(task, str) for task in tasks) and set(tasks) == expected_tasks,
                       "Reload must probe four unique task kinds")
        before_keys = _debug_keys(started["keys"], "Pre-reload keys")
        # KeyState records the action names supplied by moveToTask and holdKeyTask,
        # not translated labels or physical key.keyboard.* bindings.
        _debug_require({"forward", "sprint"}.issubset(before_keys) and started["active"] is True,
                       "Reload did not observe active forward and sprint inputs")
        task_errors = propagated["taskErrors"]
        _debug_require(isinstance(task_errors, dict) and set(task_errors) == expected_tasks and
                       all(error == "SCRIPT_RELOADED" for error in task_errors.values()),
                       "Missing unique SCRIPT_RELOADED result for each of four tasks")
        _debug_require(_debug_keys(propagated["keys"], "Post-reload keys") == [] and
                       propagated["forward"] is False and propagated["sprint"] is False and
                       propagated["staleCallbackRan"] is False,
                       "Reload retained keys or executed an old callback")
        boundary, last_stamp = selected[names[1]][0], -1
        for index, event in enumerate(events):
            generation = _debug_number(event["generation"], "Event generation", integer=True)
            _debug_require(generation == (old if index < boundary else new),
                           "Event generation disagrees with measured reload detail: " + event["stage"])
            stamp = _debug_number(event["timeMs"], "Event timeMs")
            _debug_require(stamp >= 0 and stamp >= last_stamp,
                           "Event timestamp disagrees with reload ordering: " + event["stage"])
            last_stamp = stamp
    except (ValueError, KeyError, TypeError, OverflowError, RecursionError) as error:
        return [f"Invalid measured reload evidence: {error}"]
    return []


def _validate_interrupt_debug_events(events):
    errors = []
    for name, code in (("navigation-cancelled", "CANCELLED"), ("navigation-wall-clock-timeout", "TIMEOUT")):
        names = (name + "-started", name, name + "-inputs-released")
        if not any(event["stage"] in names[:2] for event in events):
            continue
        try:
            selected = []
            for stage in names:
                matches = [(index, event) for index, event in enumerate(events) if event["stage"] == stage]
                _debug_require(len(matches) == 1, "Missing or duplicate interrupt stage: " + stage)
                selected.append(matches[0])
            indices = [index for index, event in selected]
            _debug_require(indices == sorted(indices), "Interrupt stages completed out of order")
            stamps = [_debug_number(event["timeMs"], "Interrupt timeMs") for index, event in selected]
            _debug_require(stamps == sorted(stamps) and stamps[0] >= 0, "Invalid interrupt timestamps")
            _debug_require(all(event["status"] == "PASS" for index, event in selected), "Interrupt stage is not PASS")
            started, completed = selected[0][1], selected[1][1]
            detail = _debug_detail(started)
            _debug_require("forward" in _debug_keys(detail["keys"], "Interrupt held keys") and
                           detail["running"] is True and started["inWorld"] is True and
                           started["gui"]["open"] is False, "Interrupt never acquired a running in-world forward input")
            _debug_position(detail["position"], "Interrupt start position")
            _debug_require(isinstance(completed["detail"], str) and completed["detail"].startswith(code + ": "),
                           "Wrong interruption error code")
        except (ValueError, KeyError, TypeError, OverflowError, RecursionError) as error:
            errors.append(f"Invalid measured interrupt evidence: {name}: {error}")
    return errors


def validate_debug_events(events):
    """Validate measured evidence, not merely success labels from client scripts."""
    if any(not isinstance(event, dict) or not isinstance(event.get("stage"), str) for event in events):
        return ["Malformed measured debug event/stage"]
    errors = (_validate_gameplay_debug_events(events) + _validate_reload_debug_events(events) +
              _validate_interrupt_debug_events(events) + _validate_container_debug_events(events))
    navigation = {
        "navigation-flat-arrived": (True, (40.5, -60, -1.5), 100),
        "navigation-obstacle-limited": (False, (40.5, -60, 4.5), 40),
        "navigation-waypoint-west": (True, (36.5, -60, 0.5), 100),
        "navigation-waypoint-north": (True, (36.5, -60, 5.5), 100),
        "navigation-waypoints-arrived": (True, (40.5, -60, 5.5), 100),
        "navigation-sealed-timeout": (False, (40.5, -60, 2.5), 20),
    }
    for event in events:
        name = event.get("stage", "")
        if name not in navigation and not name.endswith("-inputs-released"):
            continue
        try:
            detail = _debug_detail(event)
            if name.endswith("-inputs-released"):
                drift = _debug_number(detail["drift"], "Input release drift")
                _debug_position(detail["position"], "Input release position")
                _debug_require(_debug_keys(detail["keys"], "Released keys") == [] and 0 <= drift < 0.08,
                               "Residual input or movement")
                continue
            expected, coordinates, deadline = navigation[name]
            _debug_require(detail["arrived"] is expected, "Wrong measured navigation outcome")
            _debug_require(detail["reason"] == ("ARRIVED" if expected else "DEADLINE_EXPIRED"),
                           "Wrong navigation reason")
            _debug_require(detail["pathPlanned"] is False and detail["strategy"] == "target-follow-with-local-strafe",
                           "Unsupported path-planning claim")
            elapsed = _debug_number(detail["elapsedTicks"], "Navigation elapsedTicks", integer=True)
            timeout = _debug_number(detail["timeoutTicks"], "Navigation timeoutTicks", integer=True)
            _debug_require(timeout == deadline and 0 < elapsed <= timeout, "Invalid fixed navigation deadline")
            _debug_require(expected or elapsed == timeout, "Navigation falsely finished early")
            final_pos = _debug_position(detail["finalPos"], "Navigation finalPos")
            target = _debug_position(detail["target"], "Navigation target")
            _debug_require(tuple(target[axis] for axis in ("x", "y", "z")) == coordinates,
                           "Navigation target differs from the fixed fixture")
            horizontal = _debug_distance(final_pos, target, ("x", "z"))
            for key in ("distance", "bestDistance", "horizontalDistance", "stalledTicks", "strafeAttempts"):
                number = _debug_number(detail[key], "Navigation " + key, integer=key.endswith("Ticks") or key == "strafeAttempts")
                _debug_require(number >= 0, "Negative navigation diagnostic: " + key)
            _debug_require(abs(detail["horizontalDistance"] - horizontal) < 0.000001,
                           "Navigation distance contradicts measured coordinates")
            _debug_require(not expected or horizontal < 0.75 and abs(final_pos["y"] - target["y"]) < 1.25,
                           "Arrival label contradicts coordinates")
            samples = detail["samples"]
            _debug_require(isinstance(samples, list) and len(samples) >= 2, "Missing real movement trajectory")
            times, held = [], False
            for sample in samples:
                _debug_position(sample, "Trajectory sample")
                stamp = _debug_number(sample["timeMs"], "Trajectory timeMs")
                _debug_require(stamp >= 0, "Negative trajectory timeMs")
                times.append(stamp)
                held = bool(_debug_keys(sample["keys"], "Trajectory keys")) or held
            _debug_require(held, "Trajectory never acquired movement inputs")
            _debug_require(times == sorted(times) and times[-1] > times[0], "Unordered or static trajectory timeMs")
            # Position and key polling are asynchronous; allow a tick/inertia offset, not an unrelated endpoint.
            _debug_require(_debug_distance(samples[-1], final_pos) <= 0.8,
                           "Trajectory endpoint contradicts finalPos")
            _debug_require(not expected or
                           _debug_distance(samples[0], samples[-1], ("x", "z")) > 1 and
                           _debug_distance(samples[0], final_pos, ("x", "z")) > 1,
                           "Successful navigation has no measured displacement greater than one block")
            if name == "navigation-obstacle-limited":
                _debug_require(detail["strafeAttempts"] >= 1, "Missing obstacle anti-stall diagnostics")
        except (ValueError, KeyError, TypeError, OverflowError, RecursionError) as error:
            errors.append(f"Invalid measured debug evidence: {name}: {error}")
    return errors


def validate_png(path):
    if not path.exists():
        return f"Missing screenshot: {path.name}"
    raw = path.read_bytes()
    if len(raw) < 4096 or raw[:8] != b"\x89PNG\r\n\x1a\n":
        return f"Invalid or suspiciously empty PNG: {path.name}"
    width, height = struct.unpack(">II", raw[16:24])
    if width < 640 or height < 480:
        return f"Unexpected framebuffer dimensions: {path.name} {width}x{height}"
    return None


def dump_owned_java_threads(process, java_home, results):
    """Capture only Java descendants of this run before terminating a stalled client."""
    jcmd = java_home / "bin" / ("jcmd.exe" if os.name == "nt" else "jcmd")
    if not jcmd.is_file():
        return
    try:
        children = psutil.Process(process.pid).children(recursive=True)
    except psutil.NoSuchProcess:
        return
    for child in children:
        try:
            if child.name().lower() not in ("java", "java.exe", "javaw.exe"):
                continue
            with (results / f"threads-{child.pid}.txt").open("w", encoding="utf-8") as output:
                subprocess.run([str(jcmd), str(child.pid), "Thread.print"], stdout=output,
                               stderr=subprocess.STDOUT, timeout=20, check=False)
        except (psutil.Error, OSError, subprocess.TimeoutExpired):
            continue


def stop_owned_processes(process):
    """Only terminate descendants of the Gradle process launched by this runner."""
    try:
        parent = psutil.Process(process.pid)
        children = parent.children(recursive=True)
    except psutil.NoSuchProcess:
        children = []
        parent = None
    targets = list(reversed(children)) + ([parent] if parent is not None else [])
    for target in targets:
        try:
            target.terminate()
        except psutil.NoSuchProcess:
            pass
    _, alive = psutil.wait_procs(targets, timeout=5)
    for target in alive:
        try:
            target.kill()
        except psutil.NoSuchProcess:
            pass


def find_gradle(explicit):
    if explicit:
        return Path(explicit).resolve()
    candidates = list((Path.home() / ".gradle/wrapper/dists/gradle-8.8-bin").glob("*/gradle-8.8/bin/gradle"))
    if not candidates:
        raise RuntimeError("Gradle 8.8 not found; provide --gradle")
    return sorted(candidates)[0]


def run_one(args, run_id, language, scale):
    # The real Create World EditBox allows 32 characters, including PW_E2E_.
    if not re.fullmatch(r"[A-Za-z0-9_-]{1,25}", run_id):
        raise ValueError("Run id must be 1-25 letters, numbers, _ or - (suite prefix: at most 22)")
    directory = ROOT / "run/e2e" / run_id
    if directory.exists():
        raise RuntimeError(f"Refusing to overwrite existing E2E directory: {directory}")
    results = directory / "test-results"
    results.mkdir(parents=True)
    scripts = directory / "kubejs/client_scripts"
    scripts.mkdir(parents=True)
    for name in ("playwright_e2e.js", "playwright_wood_door.js", "playwright_container_debug.js",
                 "playwright_gameplay_debug.js", "playwright_navigation_debug.js"):
        shutil.copy2(ROOT / "examples/kubejs/client_scripts" / name, scripts)
    (directory / "options.txt").write_text(
        f"lang:{language}\nguiScale:{scale}\npauseOnLostFocus:false\nonboardAccessibility:true\n"
        "tutorialStep:none\nrenderDistance:4\nsimulationDistance:5\nmaxFps:60\n",
        encoding="utf-8",
    )
    java_home = Path(args.java_home).resolve()
    java = java_home / "bin" / ("java.exe" if os.name == "nt" else "java")
    version = subprocess.run([str(java), "-version"], capture_output=True, text=True, check=True)
    java_version = version.stdout + version.stderr
    if not re.search(r'version "17[.\"]', java_version):
        raise RuntimeError("A Java 17 runtime is required")
    environment = os.environ.copy()
    environment["JAVA_HOME"] = str(java_home)
    environment["PATH"] = str(java_home / "bin") + os.pathsep + environment.get("PATH", "")
    # Resolve Git Bash explicitly: Windows System32/bash.exe may be a broken WSL launcher.
    bash = os.environ.get("SHELL") or shutil.which("bash")
    if os.name == "nt":
        git_bash = Path(os.environ.get("ProgramFiles", "C:/Program Files")) / "Git/bin/bash.exe"
        if git_bash.is_file():
            bash = str(git_bash)
    if not bash:
        raise RuntimeError("Git Bash not found")
    command = [bash, find_gradle(args.gradle).as_posix(), "--no-daemon", "--max-workers=2", "runClient",
               "-PplaywrightE2E=true", f"-PplaywrightRunId={run_id}"]
    if not args.online:
        command.insert(2, "--offline")
    started = time.monotonic()
    errors = []
    with (results / "gradle.log").open("w", encoding="utf-8") as log:
        process = subprocess.Popen(command, cwd=ROOT, env=environment, stdout=log, stderr=subprocess.STDOUT)
        try:
            exit_code = process.wait(timeout=args.timeout)
        except subprocess.TimeoutExpired:
            errors.append(f"Graphical client exceeded wall-clock deadline ({args.timeout}s)")
            dump_owned_java_threads(process, java_home, results)
            stop_owned_processes(process)
            exit_code = process.wait(timeout=15)
    try:
        events = events_from(results / "events.jsonl")
        errors.extend(validate_events(events, run_id))
        errors.extend(validate_debug_events(events))
        if any(event.get("language") != language or event.get("guiScale") != scale for event in events):
            errors.append("Actual client language/GUI scale differs from requested configuration")
    except (OSError, ValueError) as error:
        events = []
        errors.append(f"Unreadable evidence: {error}")
    if exit_code != 0:
        errors.append(f"Gradle/client exited with code {exit_code}")
    for name in SCREENSHOTS:
        error = validate_png(results / f"{name}.png")
        if error:
            errors.append(error)
    world = directory / "saves" / f"PW_E2E_{run_id}" / "level.dat"
    if not world.is_file():
        errors.append("Test world was not saved to its isolated directory")
    report = {
        "runId": run_id, "status": "FAIL" if errors else "PASS", "language": language,
        "guiScale": scale, "elapsedSeconds": round(time.monotonic() - started, 3),
        "exitCode": exit_code, "javaVersion": java_version.strip(), "command": command,
        "requiredStages": REQUIRED, "observedStages": [event.get("stage") for event in events],
        "errors": errors, "evidenceDirectory": str(results),
    }
    (results / "report.json").write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False), flush=True)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home", required=True)
    parser.add_argument("--gradle")
    parser.add_argument("--run-id")
    parser.add_argument("--language", default="en_us", choices=["en_us", "zh_cn"])
    parser.add_argument("--gui-scale", type=int, default=2, choices=[1, 2, 3, 4])
    parser.add_argument("--timeout", type=int, default=600)
    parser.add_argument("--suite", action="store_true", help="Run two fresh clients: English/scale2 and Chinese/scale3")
    parser.add_argument("--online", action="store_true", help="Allow Gradle to download missing dependencies")
    args = parser.parse_args()
    if args.timeout < 10:
        parser.error("--timeout must be at least 10 seconds")
    prefix = args.run_id or datetime.datetime.now().strftime("%Y%m%d_%H%M%S_%f")
    cases = [(prefix + "_en", "en_us", 2), (prefix + "_zh", "zh_cn", 3)] if args.suite else [(prefix, args.language, args.gui_scale)]
    reports = [run_one(args, run_id, language, scale) for run_id, language, scale in cases]
    return 0 if all(report["status"] == "PASS" for report in reports) else 1


if __name__ == "__main__":
    raise SystemExit(main())
