"""Contract tests for graphical client evidence validation (no mocked gameplay claims)."""
import copy
import json
import tempfile
from pathlib import Path
import unittest

import run_client_e2e as runner


class EvidenceValidationTest(unittest.TestCase):
    def stage_events(self):
        return [{"runId": "unit", "stage": name, "status": "PASS", "detail": ""}
                for name in runner.REQUIRED] + [
                    {"runId": "unit", "stage": "terminal", "status": "PASS", "detail": ""}]

    def navigation_event(self, arrived=True, stage=None):
        fixtures = {
            "navigation-flat-arrived": (True, (40.5, -60, -1.5), (40.5, -60, -5.5), 100),
            "navigation-obstacle-limited": (False, (40.5, -60, 4.5), (40.5, -60, 0.5), 40),
            "navigation-waypoint-west": (True, (36.5, -60, 0.5), (40.5, -60, 0.5), 100),
            "navigation-waypoint-north": (True, (36.5, -60, 5.5), (36.5, -60, 0.5), 100),
            "navigation-waypoints-arrived": (True, (40.5, -60, 5.5), (36.5, -60, 5.5), 100),
            "navigation-sealed-timeout": (False, (40.5, -60, 2.5), (40.5, -60, 0.5), 20),
        }
        stage = stage or ("navigation-flat-arrived" if arrived else "navigation-obstacle-limited")
        arrived, coordinates, start, timeout = fixtures[stage]
        target = dict(zip(("x", "y", "z"), coordinates))
        initial = dict(zip(("x", "y", "z"), start))
        final = copy.deepcopy(target if arrived else dict(initial, z=1.5))
        horizontal = ((final["x"] - target["x"]) ** 2 + (final["z"] - target["z"]) ** 2) ** 0.5
        detail = {
            "arrived": arrived, "reason": "ARRIVED" if arrived else "DEADLINE_EXPIRED",
            "strategy": "target-follow-with-local-strafe", "pathPlanned": False,
            "elapsedTicks": 10 if arrived else timeout, "timeoutTicks": timeout,
            "finalPos": final, "target": target, "strafeAttempts": 2, "stalledTicks": 0,
            "distance": horizontal, "bestDistance": horizontal, "horizontalDistance": horizontal,
            "samples": [dict(initial, timeMs=0, keys=["forward"]),
                        dict(final, timeMs=500 if arrived else timeout * 50, keys=[])],
        }
        return {"stage": stage, "detail": json.dumps(detail)}

    def mutate_navigation(self, key, value):
        event = self.navigation_event()
        detail = json.loads(event["detail"])
        detail[key] = value
        event["detail"] = json.dumps(detail)
        return event

    def test_all_ordered_stages_and_terminal_are_required(self):
        events = self.stage_events()
        self.assertEqual([], runner.validate_events(events, "unit"))
        events.pop(10)
        self.assertTrue(runner.validate_events(events, "unit"))

    def test_duplicate_and_reordered_stage_are_rejected(self):
        events = self.stage_events()
        duplicate = copy.deepcopy(events)
        duplicate.insert(1, duplicate[0].copy())
        self.assertTrue(runner.validate_events(duplicate, "unit"))
        events[0], events[1] = events[1], events[0]
        self.assertTrue(runner.validate_events(events, "unit"))

    def test_foreign_run_and_terminal_failure_are_rejected(self):
        events = self.stage_events()
        events[0]["runId"] = "other"
        events[-1]["status"] = "FAIL"
        self.assertTrue(runner.validate_events(events, "unit"))

    def test_arrival_and_bounded_nonarrival_have_valid_evidence(self):
        self.assertEqual([], runner.validate_debug_events([
            self.navigation_event(), self.navigation_event(False)]))

    def test_false_arrival_and_path_planning_claim_are_rejected(self):
        event = self.mutate_navigation("finalPos", {"x": 99, "y": -60, "z": 0.5})
        self.assertTrue(runner.validate_debug_events([event]))
        event = self.mutate_navigation("pathPlanned", True)
        self.assertTrue(runner.validate_debug_events([event]))

    def test_missing_real_trajectory_and_deadline_evidence_are_rejected(self):
        self.assertTrue(runner.validate_debug_events([self.mutate_navigation("samples", [])]))
        self.assertTrue(runner.validate_debug_events([self.mutate_navigation("elapsedTicks", 101)]))
        self.assertTrue(runner.validate_debug_events([{"stage": "navigation-flat-arrived", "detail": "bad"}]))

    def test_residual_keys_or_drift_are_rejected(self):
        detail = {"keys": [], "drift": 0.0, "position": {"x": 40.5, "y": -60, "z": 10.5}}
        event = {"stage": "navigation-cancelled-inputs-released", "detail": json.dumps(detail)}
        self.assertEqual([], runner.validate_debug_events([event]))
        for keys, drift in ((["forward"], 0), ([], 1), ([], -0.01), ([], 0.08),
                            ([], True), ([], float("nan")), ([], float("inf")), ([], "0")):
            with self.subTest(keys=keys, drift=drift):
                event["detail"] = json.dumps(dict(detail, keys=keys, drift=drift))
                self.assertTrue(runner.validate_debug_events([event]))

    def interrupt_events(self, name="navigation-cancelled"):
        code = "CANCELLED" if name == "navigation-cancelled" else "TIMEOUT"
        return [
            {"stage": name + "-started", "status": "PASS", "timeMs": 100,
             "inWorld": True, "gui": {"open": False},
             "detail": json.dumps({"keys": ["forward"], "running": True,
                                    "position": {"x": 40.5, "y": -60, "z": 10.5}})},
            {"stage": name, "status": "PASS", "timeMs": 400, "detail": code + ": expected failure"},
            {"stage": name + "-inputs-released", "status": "PASS", "timeMs": 1200,
             "detail": json.dumps({"keys": [], "drift": 0,
                                    "position": {"x": 40.5, "y": -60, "z": 11.5}})},
        ]

    def test_interruption_requires_real_running_input_and_release(self):
        for name in ("navigation-cancelled", "navigation-wall-clock-timeout"):
            events = self.interrupt_events(name)
            self.assertEqual([], runner.validate_debug_events(events))
            for index in range(3):
                bad = copy.deepcopy(events)
                bad.pop(index)
                self.assertTrue(runner.validate_debug_events(bad))

    def test_interruption_rejects_missing_keys_completed_tasks_or_gui(self):
        for field, value in (("keys", []), ("keys", ["sprint"]), ("running", False), ("running", 1),
                             ("position", {"x": float("nan"), "y": -60, "z": 10.5})):
            events = self.interrupt_events()
            detail = json.loads(events[0]["detail"])
            detail[field] = value
            events[0]["detail"] = json.dumps(detail)
            self.assertTrue(runner.validate_debug_events(events))
        for field, value in (("inWorld", False), ("gui", {"open": True})):
            events = self.interrupt_events()
            events[0][field] = value
            self.assertTrue(runner.validate_debug_events(events))

    def test_interruption_rejects_wrong_error_order_or_time(self):
        events = self.interrupt_events()
        events[1]["detail"] = "TIMEOUT: wrong failure"
        self.assertTrue(runner.validate_debug_events(events))
        events = self.interrupt_events()
        events[0], events[1] = events[1], events[0]
        self.assertTrue(runner.validate_debug_events(events))
        for stamp in (True, -1, float("nan"), 1500):
            events = self.interrupt_events()
            events[0]["timeMs"] = stamp
            self.assertTrue(runner.validate_debug_events(events))

    def test_all_six_fixed_navigation_fixtures_are_accepted(self):
        stages = ("navigation-flat-arrived", "navigation-obstacle-limited", "navigation-waypoint-west",
                  "navigation-waypoint-north", "navigation-waypoints-arrived", "navigation-sealed-timeout")
        for stage in stages:
            event = self.navigation_event(stage=stage)
            with self.subTest(stage=stage):
                self.assertEqual([], runner.validate_debug_events([event]))
                for change in ("target", "timeoutTicks", "elapsedTicks"):
                    bad = json.loads(event["detail"])
                    if change == "target":
                        bad[change]["x"] += 1
                    else:
                        bad[change] += 1 if change == "timeoutTicks" else bad["timeoutTicks"]
                    self.assertTrue(runner.validate_debug_events([dict(event, detail=json.dumps(bad))]))

    def test_navigation_numeric_and_sample_fields_are_strict(self):
        paths = [("elapsedTicks",), ("timeoutTicks",), ("distance",), ("bestDistance",),
                 ("horizontalDistance",), ("stalledTicks",), ("strafeAttempts",)]
        paths += [(owner, axis) for owner in ("target", "finalPos") for axis in ("x", "y", "z")]
        paths += [("samples", index, key) for index in (0, 1) for key in ("x", "y", "z", "timeMs")]
        for path in paths:
            for value in (True, False, "1", None, float("nan"), float("inf"), float("-inf")):
                detail = json.loads(self.navigation_event()["detail"])
                owner = detail
                for key in path[:-1]:
                    owner = owner[key]
                owner[path[-1]] = value
                with self.subTest(path=path, value=value):
                    self.assertTrue(runner.validate_debug_events([
                        dict(self.navigation_event(), detail=json.dumps(detail))]))
        for key in ("elapsedTicks", "timeoutTicks", "stalledTicks", "strafeAttempts"):
            self.assertTrue(runner.validate_debug_events([self.mutate_navigation(key, 1.5)]))
        for samples in ({}, "samples", [None, None], [{"keys": ["forward"]}, {}]):
            self.assertTrue(runner.validate_debug_events([self.mutate_navigation("samples", samples)]))

    def test_missing_coordinates_invalid_keys_and_unordered_samples_are_rejected(self):
        for index in (0, 1):
            for axis in ("x", "y", "z", "timeMs", "keys"):
                detail = json.loads(self.navigation_event()["detail"])
                del detail["samples"][index][axis]
                with self.subTest(index=index, missing=axis):
                    self.assertTrue(runner.validate_debug_events([
                        dict(self.navigation_event(), detail=json.dumps(detail))]))
            for keys in ("forward", {}, None, [True], [1], [""], [" "], ["forward", "forward"]):
                detail = json.loads(self.navigation_event()["detail"])
                detail["samples"][index]["keys"] = keys
                with self.subTest(index=index, keys=keys):
                    self.assertTrue(runner.validate_debug_events([
                        dict(self.navigation_event(), detail=json.dumps(detail))]))
        for first, last in ((500, 0), (-1, 500), (0, 0)):
            detail = json.loads(self.navigation_event()["detail"])
            detail["samples"][0]["timeMs"], detail["samples"][1]["timeMs"] = first, last
            self.assertTrue(runner.validate_debug_events([dict(self.navigation_event(), detail=json.dumps(detail))]))
        detail = json.loads(self.navigation_event()["detail"])
        for sample in detail["samples"]:
            sample["keys"] = []
        self.assertTrue(runner.validate_debug_events([dict(self.navigation_event(), detail=json.dumps(detail))]))

    def test_static_success_and_teleported_final_position_are_rejected(self):
        for static_at_target in (True, False):
            event = self.navigation_event()
            detail = json.loads(event["detail"])
            position = detail["target"] if static_at_target else detail["samples"][0]
            for sample in detail["samples"]:
                for axis in ("x", "y", "z"):
                    sample[axis] = position[axis]
            event["detail"] = json.dumps(detail)
            with self.subTest(static_at_target=static_at_target):
                self.assertTrue(runner.validate_debug_events([event]))
        detail = json.loads(self.navigation_event()["detail"])
        detail["samples"][0]["z"] = detail["target"]["z"] - 1
        self.assertTrue(runner.validate_debug_events([dict(self.navigation_event(), detail=json.dumps(detail))]))

    def test_sample_endpoint_allows_tick_inertia_but_not_unrelated_position(self):
        for offset, accepted in ((0.6, True), (0.8, True), (0.81, False)):
            event = self.navigation_event()
            detail = json.loads(event["detail"])
            detail["samples"][-1]["z"] += offset
            event["detail"] = json.dumps(detail)
            with self.subTest(offset=offset):
                self.assertEqual(accepted, not runner.validate_debug_events([event]))

    def test_release_requires_finite_measured_position(self):
        event = {"stage": "navigation-cancelled-inputs-released"}
        for position in (None, {}, [], {"x": 0, "y": -60}):
            event["detail"] = json.dumps({"keys": [], "drift": 0, "position": position})
            self.assertTrue(runner.validate_debug_events([event]))
        event["detail"] = json.dumps({"keys": [], "drift": 0})
        self.assertTrue(runner.validate_debug_events([event]))
        for axis in ("x", "y", "z"):
            for value in (True, "0", None, float("nan"), float("inf")):
                position = {"x": 40.5, "y": -60, "z": 10.5}
                position[axis] = value
                event["detail"] = json.dumps({"keys": [], "drift": 0, "position": position})
                with self.subTest(axis=axis, value=value):
                    self.assertTrue(runner.validate_debug_events([event]))

    def test_png_missing_and_truncated_are_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "evidence.png"
            self.assertIsNotNone(runner.validate_png(path))
            path.write_bytes(b"\x89PNG\r\n\x1a\n")
            self.assertIsNotNone(runner.validate_png(path))


class ReloadEvidenceValidationTest(unittest.TestCase):
    def reload_events(self):
        started = {"generation": 7, "keys": ["sprint", "forward"],
                   "tasks": ["wait", "chain", "navigation", "hold"], "active": True}
        propagated = {"oldGeneration": 7, "currentGeneration": 8,
                      "taskErrors": {name: "SCRIPT_RELOADED" for name in started["tasks"]},
                      "keys": [], "forward": False, "sprint": False, "staleCallbackRan": False}
        released = {"keys": [], "drift": 0.01, "position": {"x": 0.5, "y": -60, "z": 0.9}}
        return [{"stage": stage, "status": "PASS", "generation": generation, "timeMs": stamp,
                 "detail": json.dumps(detail)}
                for stage, detail, generation, stamp in (
                    ("script-started", {}, 7, 1000),
                    ("reload-started", started, 7, 2000),
                    ("reload-propagated", propagated, 8, 2100),
                    ("navigation-reload-inputs-released", released, 8, 3000),
                    ("reload-new-script-executed", {}, 8, 3100))]

    def change(self, stage, path, value=None, delete=False):
        events = self.reload_events()
        event = next(event for event in events if event["stage"] == stage)
        detail = json.loads(event["detail"])
        owner = detail
        for key in path[:-1]:
            owner = owner[key]
        if delete:
            del owner[path[-1]]
        else:
            owner[path[-1]] = value
        event["detail"] = json.dumps(detail)
        return events

    def test_structured_four_task_reload_is_accepted(self):
        self.assertEqual([], runner.validate_debug_events(self.reload_events()))
        events = self.reload_events()
        for event in events:
            event["generation"] = float(event["generation"])
            detail = json.loads(event["detail"])
            for key in ("generation", "oldGeneration", "currentGeneration"):
                if key in detail:
                    detail[key] = float(detail[key])
            event["detail"] = json.dumps(detail)
        self.assertEqual([], runner.validate_debug_events(events))
        self.assertEqual([], runner.validate_debug_events(self.reload_events()[1:3]))

    def test_text_claims_or_two_task_disguises_are_rejected(self):
        for stage in ("reload-started", "reload-propagated"):
            for detail in ("PASS", "generation=7; SCRIPT_RELOADED tasks=4", "SCRIPT_RELOADED tasks=2", "{}"):
                events = self.reload_events()
                next(event for event in events if event["stage"] == stage)["detail"] = detail
                with self.subTest(stage=stage, detail=detail):
                    self.assertTrue(runner.validate_debug_events(events))
        for tasks in (["wait", "chain"], ["wait", "chain", "wait", "chain"],
                      ["wait", "chain", "navigation", "unknown"], "wait chain navigation hold", [None] * 4):
            self.assertTrue(runner.validate_debug_events(self.change("reload-started", ("tasks",), tasks)))
        self.assertTrue(runner.validate_debug_events(self.change(
            "reload-propagated", ("taskErrors",), {"wait": "SCRIPT_RELOADED", "chain": "SCRIPT_RELOADED"})))

    def test_every_unique_task_error_is_required(self):
        for task in ("wait", "chain", "navigation", "hold"):
            self.assertTrue(runner.validate_debug_events(self.change(
                "reload-propagated", ("taskErrors", task), delete=True)))
            for code in ("CANCELLED", "TIMEOUT", "ENTITY_NOT_FOUND", None, True):
                with self.subTest(task=task, code=code):
                    self.assertTrue(runner.validate_debug_events(self.change(
                        "reload-propagated", ("taskErrors", task), code)))
        events = self.reload_events()
        event = events[2]
        event["detail"] = event["detail"].replace('"hold": "SCRIPT_RELOADED"',
                                                    '"hold": "CANCELLED", "hold": "SCRIPT_RELOADED"')
        self.assertTrue(runner.validate_debug_events(events))
        self.assertTrue(runner.validate_debug_events(self.change(
            "reload-propagated", ("taskErrors", "extra"), "SCRIPT_RELOADED")))

    def test_active_raw_keys_and_post_reload_release_are_required(self):
        for keys in ([], ["forward"], ["sprint"], ["key.keyboard.w", "key.keyboard.left.control"],
                     "forward sprint", ["forward", "sprint", ""], ["forward", "sprint", True]):
            with self.subTest(keys=keys):
                self.assertTrue(runner.validate_debug_events(self.change("reload-started", ("keys",), keys)))
        for value in (False, 1, "true", None):
            self.assertTrue(runner.validate_debug_events(self.change("reload-started", ("active",), value)))
        for key, value in (("keys", ["forward"]), ("keys", ["sprint"]), ("keys", None),
                           ("forward", True), ("sprint", True), ("staleCallbackRan", True),
                           ("forward", 0), ("sprint", "false"), ("staleCallbackRan", 0)):
            with self.subTest(key=key, value=value):
                self.assertTrue(runner.validate_debug_events(self.change("reload-propagated", (key,), value)))

    def test_missing_detail_fields_and_inconsistent_generations_are_rejected(self):
        for stage, keys in (("reload-started", ("generation", "keys", "tasks", "active")),
                            ("reload-propagated", ("oldGeneration", "currentGeneration", "taskErrors",
                                                   "keys", "forward", "sprint", "staleCallbackRan"))):
            for key in keys:
                self.assertTrue(runner.validate_debug_events(self.change(stage, (key,), delete=True)))
        for stage, key, value in (("reload-started", "generation", 6),
                                  ("reload-propagated", "oldGeneration", 6),
                                  ("reload-propagated", "currentGeneration", 9),
                                  ("reload-propagated", "currentGeneration", 7)):
            self.assertTrue(runner.validate_debug_events(self.change(stage, (key,), value)))
        for stage, key in (("reload-started", "generation"), ("reload-propagated", "oldGeneration"),
                           ("reload-propagated", "currentGeneration")):
            for value in (True, 7.5, "7", float("nan"), float("inf")):
                self.assertTrue(runner.validate_debug_events(self.change(stage, (key,), value)))
        for index in range(5):
            for generation in (None, True, 7.5, "7", float("nan"), 9):
                events = self.reload_events()
                events[index]["generation"] = generation
                self.assertTrue(runner.validate_debug_events(events))

    def test_reload_order_timestamps_and_unique_stages_are_required(self):
        events = self.reload_events()
        for index in (1, 2):
            bad = copy.deepcopy(events)
            bad.pop(index)
            self.assertTrue(runner.validate_debug_events(bad))
        for index in range(1, 5):
            bad = copy.deepcopy(events)
            bad.insert(index, copy.deepcopy(bad[index]))
            self.assertTrue(runner.validate_debug_events(bad))
            for stamp in (-1, 0, True, "2000", float("nan"), float("inf")):
                bad = copy.deepcopy(events)
                bad[index]["timeMs"] = stamp
                self.assertTrue(runner.validate_debug_events(bad))
        for first, second in ((1, 2), (2, 3), (3, 4)):
            bad = copy.deepcopy(events)
            bad[first], bad[second] = bad[second], bad[first]
            self.assertTrue(runner.validate_debug_events(bad))
        events[2]["status"] = "FAIL"
        self.assertTrue(runner.validate_debug_events(events))

    def container_events(self):
        chest_block = {"x": 6, "y": -60, "z": 2, "block": "minecraft:chest"}
        def detail(**values):
            return json.dumps(values)
        def event(stage, stamp, payload, container_id=7):
            payload.setdefault("screenType", "ContainerScreen")
            payload.setdefault("containerId", container_id)
            payload.setdefault("slotCount", 63)
            payload.setdefault("chestSlots", 27)
            payload.setdefault("playerSlots", 36)
            return {"stage": stage, "status": "PASS", "timeMs": stamp, "inWorld": True,
                    "gui": {"open": True, "type": "ContainerScreen"}, "detail": detail(**payload)}
        emerald = {"item": "minecraft:emerald", "count": 3, "empty": False}
        empty = {"item": "minecraft:air", "count": 0, "empty": True}
        return [
            event("container-chest-opened", 100, {"chestBlock": chest_block}),
            event("container-item-moved", 200, {
                "sourceSlot": 38, "targetSlot": 13, "clickButton": 0,
                "sourceBefore": dict(emerald, slot=38), "targetBefore": dict(empty, slot=13),
                "cursorAfterPickup": emerald, "sourceAfter": dict(empty, slot=38),
                "targetAfter": dict(emerald, slot=13), "carriedAfter": empty,
            }),
            event("container-reopened", 300, {
                "targetSlot": 13, "target": dict(emerald, slot=13), "carried": empty,
            }, container_id=8),
            {"stage": "world-rejoined", "status": "PASS", "timeMs": 400},
            event("container-persistence-verified", 500, {
                "targetSlot": 13, "target": dict(emerald, slot=13), "carried": empty,
                "worldRejoined": True, "chestBlock": chest_block,
            }, container_id=9),
        ]

    def test_measured_vanilla_container_move_close_reopen_and_rejoin_are_accepted(self):
        self.assertEqual([], runner.validate_debug_events(self.container_events()))

    def test_container_validation_rejects_fake_items_slots_screen_and_rejoin(self):
        mutations = (
            ("container-item-moved", "targetAfter", "item", "minecraft:stone"),
            ("container-item-moved", "sourceSlot", None, 13),
            ("container-item-moved", "carriedAfter", "empty", False),
            ("container-reopened", "target", "count", 64),
            ("container-persistence-verified", "worldRejoined", None, False),
        )
        for stage, key, child, value in mutations:
            events = self.container_events()
            target = next(entry for entry in events if entry["stage"] == stage)
            payload = json.loads(target["detail"])
            if child is None:
                payload[key] = value
            else:
                payload[key][child] = value
            target["detail"] = json.dumps(payload)
            with self.subTest(stage=stage, key=key, child=child, value=value):
                self.assertTrue(runner.validate_debug_events(events))
        events = self.container_events()
        next(event for event in events if event["stage"] == "container-reopened")["gui"]["type"] = "CraftingScreen"
        self.assertTrue(runner.validate_debug_events(events))
        events = self.container_events()
        next(event for event in events if event["stage"] == "container-persistence-verified")["gui"]["open"] = False
        self.assertTrue(runner.validate_debug_events(events))

    def test_container_validation_rejects_missing_duplicate_reordered_and_unmeasured_events(self):
        events = self.container_events()
        for name in ("container-chest-opened", "container-item-moved", "container-reopened",
                     "container-persistence-verified"):
            bad = copy.deepcopy(events)
            bad.pop(next(index for index, event in enumerate(bad) if event["stage"] == name))
            self.assertTrue(runner.validate_debug_events(bad))
            bad = copy.deepcopy(events)
            index = next(index for index, event in enumerate(bad) if event["stage"] == name)
            bad.insert(index + 1, copy.deepcopy(bad[index]))
            self.assertTrue(runner.validate_debug_events(bad))
        events = self.container_events()
        events[0], events[1] = events[1], events[0]
        self.assertTrue(runner.validate_debug_events(events))
        events = self.container_events()
        events[4]["timeMs"] = 50
        self.assertTrue(runner.validate_debug_events(events))


class GameplayEvidenceValidationTest(unittest.TestCase):
    def gameplay_events(self, missing=False):
        names = ("head", "chest", "legs", "feet", "mainhand", "offhand")
        items = ("iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots", "iron_sword", "shield")
        maximums, slots = (165, 240, 225, 195, 250, 336), (39, 38, 37, 36, 4, 40)
        equipped = {
            "equipment": {name: {"slot": slot, "item": "minecraft:" + item, "count": 1,
                                  "damageable": True, "damage": 0, "maxDamage": maximum,
                                  "remainingDurability": maximum}
                          for name, item, maximum, slot in zip(names, items, maximums, slots)},
            "armorValue": 15,
            "status": {"health": 20, "maxHealth": 20, "food": 20, "saturation": 5,
                       "armor": 15, "absorption": 0, "attackStrength": 1},
        }
        removed = copy.deepcopy(equipped)
        removed["equipment"]["head"] = {"slot": 39, "item": "minecraft:air", "count": 0,
                                           "damageable": False, "damage": 0, "maxDamage": 0,
                                           "remainingDurability": 0}
        removed["armorValue"] = removed["status"]["armor"] = 13
        swapped = copy.deepcopy(equipped)
        swapped["equipment"]["mainhand"] = dict(equipped["equipment"]["offhand"], slot=4)
        swapped["equipment"]["offhand"] = dict(equipped["equipment"]["mainhand"], slot=40)
        damaged = copy.deepcopy(equipped)
        damaged["status"]["health"] = 18.4
        for name in names[:4]:
            damaged["equipment"][name]["damage"] = 1
            damaged["equipment"][name]["remainingDurability"] -= 1
        target = {"id": 42, "uuid": "12345678-1234-1234-1234-123456789abc", "type": "minecraft:pig",
                  "name": "PW_DEBUG_TARGET", "x": 20.5, "y": -60, "z": 2.5, "alive": True,
                  "health": 20, "maxHealth": 20, "missing": False}
        hits, player, entity = [], copy.deepcopy(damaged), copy.deepcopy(target)
        for attack, health in enumerate((14, 8, 2, 0), 1):
            after = copy.deepcopy(player)
            after["equipment"]["mainhand"]["damage"] += 1
            after["equipment"]["mainhand"]["remainingDurability"] -= 1
            after["status"]["attackStrength"] = 0.1
            target_after = dict(entity, health=health, alive=health > 0, missing=health == 0 and missing)
            if target_after["missing"]:
                for key in ("maxHealth", "x", "y", "z"):
                    del target_after[key]
            hits.append({"attack": attack, "id": 42, "uuid": target["uuid"], "beforeHealth": entity["health"],
                         "afterHealth": health, "beforeDamage": player["equipment"]["mainhand"]["damage"],
                         "afterDamage": after["equipment"]["mainhand"]["damage"], "alive": health > 0,
                         "missing": target_after["missing"], "targetBeforeAttack": copy.deepcopy(entity),
                         "targetAfterAttack": target_after, "before": player, "after": after})
            player, entity = copy.deepcopy(after), copy.deepcopy(target_after)
        expected = {"equipment": copy.deepcopy(player["equipment"]), "armorValue": 15,
                    "health": player["status"]["health"],
                    "target": {"id": 42, "uuid": target["uuid"], "name": "PW_DEBUG_TARGET", "alive": False}}
        actual = copy.deepcopy(expected)
        for item in actual["equipment"].values():
            del item["slot"]  # Persistence itemData intentionally omits slot.
        actual.update(armor=15, absorption=0, attackStrength=1, targetAbsent=True)
        details = [
            ("equipment-equipped", equipped),
            ("equipment-removed-restored", {"emptyDestination": 9, "before": equipped,
                                             "removed": removed, "restored": equipped}),
            ("equipment-hands-swapped", {"before": equipped, "swapped": swapped, "restored": equipped}),
            ("equipment-damage-verified", {"fixture": "controlled damage fixture",
                                           "command": "/damage @s 4 minecraft:mob_attack", "beforeHealth": 20,
                                           "afterHealth": 18.4, "damagedArmor": "head", "beforeDamage": 0,
                                           "afterDamage": 1, "before": equipped, "after": damaged}),
            ("combat-target-ready", {"target": target, "player": damaged,
                                      "fixture": "open-front glass knockback boundary"}),
            ("combat-damage-verified", hits[0]),
            ("combat-target-defeated", {"id": 42, "uuid": target["uuid"], "alive": False, "missing": missing,
                                         "beforeHealth": 20, "afterHealth": 0, "beforeDamage": 0,
                                         "afterDamage": 4, "attacks": 4, "hits": hits, "target": entity,
                                         "player": player}),
            ("gameplay-equipment-persistence-verified", {"expected": expected, "actual": actual}),
        ]
        events = [{"stage": stage, "status": "PASS", "detail": json.dumps(detail)} for stage, detail in details]
        for name, code in (("equipment-invalid-slot", "SLOT_OUT_OF_RANGE"),
                           ("combat-missing-entity", "ENTITY_NOT_FOUND"),
                           ("combat-distance-filter", "ENTITY_NOT_FOUND")):
            events.append({"stage": name, "status": "PASS", "detail": code + ": observed failure"})
        events.sort(key=lambda event: runner.REQUIRED.index(event["stage"]))
        return events

    def change(self, stage, path, value=None, delete=False, events=None):
        events = copy.deepcopy(self.gameplay_events() if events is None else events)
        event = next(event for event in events if event["stage"] == stage)
        detail = json.loads(event["detail"])
        owner = detail
        for key in path[:-1]:
            owner = owner[key]
        if delete:
            del owner[path[-1]]
        else:
            owner[path[-1]] = value
        event["detail"] = json.dumps(detail)
        return events

    def assert_rejected(self, events):
        self.assertTrue(runner.validate_debug_events(events), "Forged evidence was accepted")

    def test_only_observed_dead_target_is_accepted(self):
        self.assertEqual([], runner.validate_debug_events(self.gameplay_events()))
        self.assert_rejected(self.gameplay_events(missing=True))

    def test_missing_target_cannot_be_used_as_health_zero_or_kill_evidence(self):
        for path in (("missing",), ("target", "missing"), ("hits", 3, "missing"),
                     ("hits", 3, "targetAfterAttack", "missing")):
            with self.subTest(path=path):
                self.assert_rejected(self.change("combat-target-defeated", path, True))
        # Coherent missing=true flags and zero health still do not observe a death.
        events = self.gameplay_events()
        for path in (("missing",), ("target", "missing"), ("hits", 3, "missing"),
                     ("hits", 3, "targetAfterAttack", "missing")):
            events = self.change("combat-target-defeated", path, True, events=events)
        self.assert_rejected(events)
        for axis in ("x", "y", "z", "maxHealth"):
            self.assert_rejected(self.change("combat-target-defeated", ("target", axis), delete=True))
            self.assert_rejected(self.change(
                "combat-target-defeated", ("hits", 3, "targetAfterAttack", axis), delete=True))

    def test_rhino_float_integer_fields_are_accepted_without_converting_booleans(self):
        def rhino_numbers(value):
            if type(value) is int:
                return float(value)
            if isinstance(value, dict):
                return {key: rhino_numbers(child) for key, child in value.items()}
            if isinstance(value, list):
                return [rhino_numbers(child) for child in value]
            return value

        events = self.gameplay_events()
        for event in events:
            if event["detail"].startswith("{"):
                detail = rhino_numbers(json.loads(event["detail"]))
                event["detail"] = json.dumps(detail)
        self.assertEqual([], runner.validate_debug_events(events))
        equipped = json.loads(events[0]["detail"])
        self.assertIs(type(equipped["equipment"]["head"]["slot"]), float)
        self.assertIs(equipped["equipment"]["head"]["damageable"], True)

    def test_fractional_integer_fields_are_rejected(self):
        cases = (("equipment-equipped", ("equipment", "head", "slot"), 39.5),
                 ("equipment-equipped", ("equipment", "head", "count"), 1.5),
                 ("equipment-equipped", ("equipment", "head", "damage"), 0.5),
                 ("equipment-equipped", ("equipment", "head", "maxDamage"), 165.5),
                 ("equipment-equipped", ("equipment", "head", "remainingDurability"), 164.5),
                 ("equipment-removed-restored", ("emptyDestination",), 9.5),
                 ("equipment-damage-verified", ("afterDamage",), 1.5),
                 ("combat-target-ready", ("target", "id"), 42.5),
                 ("combat-damage-verified", ("attack",), 1.5),
                 ("combat-target-defeated", ("attacks",), 4.5),
                 ("combat-target-defeated", ("hits", 1, "afterDamage"), 2.5),
                 ("gameplay-equipment-persistence-verified", ("actual", "target", "id"), 42.5))
        for stage, path, value in cases:
            with self.subTest(stage=stage, path=path):
                self.assert_rejected(self.change(stage, path, value))
        for value in (True, False, 0.5, "1", None, float("nan"), float("inf"), float("-inf")):
            with self.subTest(integer_value=value):
                with self.assertRaises(ValueError):
                    runner._debug_number(value, "Integer contract", integer=True)

    def test_pristine_equipment_stage_can_be_validated_without_future_stages(self):
        self.assertEqual([], runner.validate_debug_events(self.gameplay_events()[:1]))

    def test_pass_and_attack_sent_labels_are_not_measurements(self):
        for stage in ("equipment-equipped", "combat-damage-verified", "combat-target-defeated",
                      "gameplay-equipment-persistence-verified"):
            for detail in ("PASS", '{"attacked":true}', "{}", "[]", "null"):
                with self.subTest(stage=stage, detail=detail):
                    events = self.gameplay_events()
                    next(event for event in events if event["stage"] == stage)["detail"] = detail
                    self.assert_rejected(events)

    def test_missing_nested_measurements_are_rejected(self):
        cases = (
            ("equipment-equipped", ("equipment", "head", "damageable")),
            ("equipment-equipped", ("equipment", "mainhand", "remainingDurability")),
            ("equipment-equipped", ("status", "armor")),
            ("equipment-removed-restored", ("removed",)),
            ("equipment-hands-swapped", ("restored",)),
            ("equipment-damage-verified", ("damagedArmor",)),
            ("combat-target-ready", ("target", "uuid")),
            ("combat-target-ready", ("fixture",)),
            ("combat-damage-verified", ("targetBeforeAttack",)),
            ("combat-damage-verified", ("after", "equipment", "mainhand", "damage")),
            ("combat-target-defeated", ("hits",)),
            ("combat-target-defeated", ("hits", 1, "targetAfterAttack")),
            ("gameplay-equipment-persistence-verified", ("actual", "targetAbsent")),
            ("gameplay-equipment-persistence-verified", ("actual", "equipment", "mainhand")),
        )
        for stage, path in cases:
            with self.subTest(stage=stage, path=path):
                self.assert_rejected(self.change(stage, path, delete=True))

    def test_nonfinite_bool_string_and_duplicate_numeric_fields_are_rejected(self):
        cases = (("equipment-equipped", ("equipment", "head", "damage")),
                 ("equipment-damage-verified", ("afterHealth",)),
                 ("combat-target-ready", ("target", "health")),
                 ("combat-damage-verified", ("targetAfterAttack", "x")),
                 ("combat-target-defeated", ("hits", 2, "afterDamage")),
                 ("gameplay-equipment-persistence-verified", ("actual", "health")))
        for stage, path in cases:
            for value in (float("nan"), float("inf"), float("-inf"), True, "20", None):
                with self.subTest(stage=stage, path=path, value=value):
                    self.assert_rejected(self.change(stage, path, value))
        events = self.gameplay_events()
        events[0]["detail"] = events[0]["detail"].replace('"armorValue": 15', '"armorValue": 13, "armorValue": 15')
        self.assert_rejected(events)
        events[0]["detail"] = '{"armorValue": 1e999}'
        self.assert_rejected(events)

    def test_armor_durability_removal_swap_and_damage_are_cross_checked(self):
        cases = (
            ("equipment-equipped", ("armorValue",), 13),
            ("equipment-equipped", ("equipment", "head", "damage"), 1),
            ("equipment-equipped", ("equipment", "head", "slot"), 5),
            ("equipment-equipped", ("equipment", "head", "remainingDurability"), 164),
            ("equipment-removed-restored", ("removed", "armorValue"), 15),
            ("equipment-removed-restored", ("restored", "equipment", "head", "item"), "minecraft:air"),
            ("equipment-hands-swapped", ("swapped", "equipment", "mainhand", "item"), "minecraft:iron_sword"),
            ("equipment-hands-swapped", ("restored", "equipment", "offhand", "count"), 0),
            ("equipment-damage-verified", ("afterHealth",), 20),
            ("equipment-damage-verified", ("afterDamage",), 0),
            ("equipment-damage-verified", ("damagedArmor",), "mainhand"),
            ("equipment-damage-verified", ("after", "status", "armor"), 13),
        )
        for stage, path, value in cases:
            with self.subTest(stage=stage, path=path):
                self.assert_rejected(self.change(stage, path, value))
        # Forge coherent summary and nested state: unchanged health or armor is still not a measurement.
        for loss in ("health", "armor"):
            events = self.gameplay_events()
            event = next(event for event in events if event["stage"] == "equipment-damage-verified")
            detail = json.loads(event["detail"])
            if loss == "health":
                detail["afterHealth"] = detail["after"]["status"]["health"] = 20
            else:
                detail["after"]["equipment"] = copy.deepcopy(detail["before"]["equipment"])
                detail["afterDamage"] = 0
            event["detail"] = json.dumps(detail)
            self.assert_rejected(events)

    def test_target_identity_health_and_real_attack_are_required(self):
        cases = (
            ("combat-target-ready", ("target", "health"), 19),
            ("combat-target-ready", ("target", "maxHealth"), 10),
            ("combat-target-ready", ("target", "uuid"), ""),
            ("combat-target-ready", ("target", "type"), "minecraft:cow"),
            ("combat-damage-verified", ("id",), 43),
            ("combat-damage-verified", ("targetBeforeAttack", "uuid"), "87654321-1234-1234-1234-123456789abc"),
            ("combat-damage-verified", ("targetAfterAttack", "id"), 43),
            ("combat-damage-verified", ("afterHealth",), 20),
            ("combat-damage-verified", ("afterDamage",), 0),
        )
        for stage, path, value in cases:
            with self.subTest(stage=stage, path=path):
                self.assert_rejected(self.change(stage, path, value))
        for loss in ("health", "sword"):
            events = self.gameplay_events()
            event = next(event for event in events if event["stage"] == "combat-damage-verified")
            detail = json.loads(event["detail"])
            if loss == "health":
                detail["afterHealth"] = detail["targetAfterAttack"]["health"] = detail["beforeHealth"]
            else:
                detail["afterDamage"] = detail["beforeDamage"]
                detail["after"]["equipment"]["mainhand"] = copy.deepcopy(detail["before"]["equipment"]["mainhand"])
            event["detail"] = json.dumps(detail)
            self.assert_rejected(events)

    def test_cumulative_history_rejects_gaps_reordering_and_fake_death(self):
        cases = (
            (("attacks",), 0), (("attacks",), 9), (("attacks",), 3),
            (("hits", 1, "attack"), 3), (("hits", 1, "beforeHealth"), 20),
            (("hits", 1, "beforeDamage"), 0), (("hits", 1, "afterHealth"), 14),
            (("hits", 1, "afterDamage"), 1), (("hits", 1, "targetAfterAttack", "uuid"),
             "87654321-1234-1234-1234-123456789abc"),
            (("hits", 0, "after", "status", "attackStrength"), 0.2),
            (("afterHealth",), 2), (("afterDamage",), 3), (("alive",), True),
            (("target", "alive"), True), (("target", "health"), 2), (("uuid",), "other"),
            (("hits", 3, "alive"), True), (("hits", 3, "missing"), True),
        )
        for path, value in cases:
            with self.subTest(path=path):
                self.assert_rejected(self.change("combat-target-defeated", path, value))
        events = self.gameplay_events()
        event = next(event for event in events if event["stage"] == "combat-target-defeated")
        detail = json.loads(event["detail"])
        detail["hits"][1], detail["hits"][2] = detail["hits"][2], detail["hits"][1]
        event["detail"] = json.dumps(detail)
        self.assert_rejected(events)

    def test_coherently_forged_later_hit_still_requires_continuous_actual_loss(self):
        for forgery in ("health-gap", "no-health-loss", "sword-gap", "no-sword-loss"):
            events = self.gameplay_events()
            event = next(event for event in events if event["stage"] == "combat-target-defeated")
            detail = json.loads(event["detail"])
            hit = detail["hits"][1]
            if forgery == "health-gap":
                hit["beforeHealth"] = hit["targetBeforeAttack"]["health"] = 12
            elif forgery == "no-health-loss":
                hit["afterHealth"] = hit["targetAfterAttack"]["health"] = hit["beforeHealth"]
            elif forgery == "sword-gap":
                hit["beforeDamage"] = 0
                hit["afterDamage"] = 1
                for state, damage in (("before", 0), ("after", 1)):
                    sword = hit[state]["equipment"]["mainhand"]
                    sword["damage"], sword["remainingDurability"] = damage, sword["maxDamage"] - damage
            else:
                hit["afterDamage"] = hit["beforeDamage"]
                hit["after"]["equipment"]["mainhand"] = copy.deepcopy(hit["before"]["equipment"]["mainhand"])
            event["detail"] = json.dumps(detail)
            with self.subTest(forgery=forgery):
                self.assert_rejected(events)

    def test_malformed_nested_collections_and_boolean_flags_are_rejected(self):
        cases = (("equipment-equipped", ("equipment",), []),
                 ("equipment-equipped", ("status",), []),
                 ("equipment-equipped", ("equipment", "head", "damageable"), 1),
                 ("combat-target-ready", ("target", "alive"), "true"),
                 ("combat-damage-verified", ("targetBeforeAttack", "missing"), True),
                 ("combat-target-defeated", ("hits",), {}),
                 ("combat-target-defeated", ("hits", 1, "attack"), 2.5),
                 ("gameplay-equipment-persistence-verified", ("actual", "target", "alive"), 0))
        for stage, path, value in cases:
            with self.subTest(stage=stage, path=path):
                self.assert_rejected(self.change(stage, path, value))

    def test_missing_reordered_and_duplicate_gameplay_stage_are_rejected(self):
        for index in range(8):
            events = self.gameplay_events()
            measured = [event for event in events if event["stage"] not in
                        ("equipment-invalid-slot", "combat-missing-entity", "combat-distance-filter")]
            event = measured[index]
            duplicate = copy.deepcopy(events)
            duplicate.insert(0, copy.deepcopy(event))
            self.assert_rejected(duplicate)
            if index < 7:
                events.remove(event)
                self.assert_rejected(events)
        events = self.gameplay_events()
        events[0], events[1] = events[1], events[0]
        self.assert_rejected(events)

    def test_rejoin_must_match_final_measured_state_not_its_own_forged_expectation(self):
        stage = "gameplay-equipment-persistence-verified"
        cases = (
            (("actual", "health"), 20), (("actual", "armor"), 13), (("actual", "targetAbsent"), False),
            (("actual", "targetAbsent"), 1), (("actual", "equipment", "mainhand", "damage"), 0),
            (("actual", "equipment", "head", "remainingDurability"), 165),
            (("actual", "target", "id"), 43), (("expected", "target", "alive"), True),
        )
        for path, value in cases:
            with self.subTest(path=path):
                self.assert_rejected(self.change(stage, path, value))
        events = self.gameplay_events()
        events = self.change(stage, ("expected", "health"), 20, events=events)
        events = self.change(stage, ("actual", "health"), 20, events=events)
        self.assert_rejected(events)
        for owner in ("expected", "actual"):
            events = self.change(stage, (owner, "equipment", "mainhand", "damage"), 0, events=events)
            events = self.change(stage, (owner, "equipment", "mainhand", "remainingDurability"), 250, events=events)
        self.assert_rejected(events)

    def test_expected_failure_detail_is_not_a_pass_label(self):
        for stage in ("equipment-invalid-slot", "combat-missing-entity", "combat-distance-filter"):
            events = self.gameplay_events()
            next(event for event in events if event["stage"] == stage)["detail"] = "PASS"
            with self.subTest(stage=stage):
                self.assert_rejected(events)

    def test_malformed_events_and_nonfinite_navigation_are_rejected(self):
        for events in ([None], [{"stage": []}], [{"stage": True}]):
            self.assert_rejected(events)
        for value in (float("nan"), float("inf"), float("-inf")):
            navigation = EvidenceValidationTest().mutate_navigation("elapsedTicks", value)
            self.assert_rejected([navigation])


if __name__ == "__main__":
    unittest.main()
