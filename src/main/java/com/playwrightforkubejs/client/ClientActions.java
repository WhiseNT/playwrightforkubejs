package com.playwrightforkubejs.client;

import com.playwrightforkubejs.protocol.ErrorCode;
import com.playwrightforkubejs.protocol.EventBus;
import com.playwrightforkubejs.protocol.Params;
import com.playwrightforkubejs.protocol.PlaywrightException;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.registries.BuiltInRegistries;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ClientActions {
    private ClientActions() {
    }

    public static Map<String, Object> execute(String action, Map<String, Object> params) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            return switch (action) {
            case "chat.send" -> chatSend(minecraft, Params.requiredString(params, "message"));
            case "chat.command" -> chatCommand(minecraft, Params.requiredString(params, "command"));
            case "chat.clear" -> chatClear();
            case "move.jump" -> pulseKey(minecraft.options.keyJump, "jump");
            case "move.sneak" -> keyAction(minecraft.options.keyShift, Params.bool(params, "enabled", true), "sneak");
            case "move.sprint" -> keyAction(minecraft.options.keySprint, Params.bool(params, "enabled", true), "sprint");
            case "move.direction", "move.to" -> throw new PlaywrightException(ErrorCode.INTERNAL_ERROR, "movement actions must be scheduled through ClientDispatcher");
            case "look.set" -> lookSet(minecraft, Params.number(params, "yaw", 0), Params.number(params, "pitch", 0));
            case "look.at" -> lookAt(minecraft, Params.vec3(params));
            case "look.entity" -> lookAtEntity(minecraft, params);
            case "input.click", "input.double-click", "input.key-press" -> throw new PlaywrightException(ErrorCode.INTERNAL_ERROR, "Input pulses must be scheduled through ClientDispatcher");
            case "input.mouse-move" -> ClientInput.movePointer(Params.number(params, "x", 0), Params.number(params, "y", 0));
            case "input.scroll" -> ClientInput.scroll(Params.number(params, "delta", 0));
            case "input.type" -> ClientInput.type(minecraft.screen, Params.requiredString(params, "text"));
            case "input.key-down" -> ClientInput.keyState(Params.requiredString(params, "key"), true);
            case "input.key-up" -> ClientInput.keyState(Params.requiredString(params, "key"), false);
            case "input.key-combo" -> throw new PlaywrightException(ErrorCode.INTERNAL_ERROR, "Input combos must be scheduled through ClientDispatcher");
            case "input.key-hold" -> throw new PlaywrightException(ErrorCode.INTERNAL_ERROR, "input.key-hold must be scheduled through ClientDispatcher");
            case "block.break" -> blockBreak(minecraft, Params.blockPos(params));
            case "block.place" -> blockPlace(minecraft, Params.blockPos(params), Params.direction(Params.string(params, "face", "up")));
            case "block.interact" -> blockInteract(minecraft, Params.blockPos(params), Params.direction(Params.string(params, "face", "up")));
            case "entity.attack" -> entityAttack(minecraft, params);
            case "entity.interact" -> entityInteract(minecraft, params);
            case "entity.mount" -> entityMount(minecraft, params);
            case "entity.dismount" -> entityDismount(minecraft);
            case "inventory.hotbar" -> hotbar(minecraft, Params.integer(params, "slot", 0));
            case "inventory.drop" -> drop(minecraft, Params.bool(params, "all", false));
            case "inventory.use" -> use(minecraft);
            case "inventory.swap-hands" -> swapHands(minecraft);
            case "gui.click" -> guiClick(minecraft, Params.integer(params, "slot", -1), Params.integer(params, "button", 0));
            case "gui.locator-click" -> ClientGui.click(Params.requiredString(params, "selector"));
            case "gui.locator-fill" -> ClientGui.fill(Params.requiredString(params, "selector"), Params.string(params, "text", null));
            case "gui.close" -> guiClose(minecraft);
            case "gui.drag" -> guiDrag(minecraft, params);
            case "capture.screenshot", "gui.screenshot" -> throw new PlaywrightException(ErrorCode.INTERNAL_ERROR, "Screenshot actions must be scheduled through ClientDispatcher");
            case "client.respawn" -> respawn(minecraft);
            default -> throw new PlaywrightException(ErrorCode.INVALID_ACTION, "Unknown action: " + action);
            };
        } finally {
            KeyState.releaseIfContextChanged();
        }
    }

    private static LocalPlayer player(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            throw new PlaywrightException(ErrorCode.NOT_IN_WORLD, "The client is not in a world");
        }
        return minecraft.player;
    }

    private static Map<String, Object> chatSend(Minecraft minecraft, String message) {
        LocalPlayer player = player(minecraft);
        player.connection.sendChat(message);
        EventBus.getInstance().record("chat.sent", Map.of("message", message));
        return Map.of("message", message);
    }

    private static Map<String, Object> chatCommand(Minecraft minecraft, String command) {
        LocalPlayer player = player(minecraft);
        String normalized = command.startsWith("/") ? command.substring(1) : command;
        player.connection.sendCommand(normalized);
        EventBus.getInstance().record("chat.command", Map.of("command", normalized));
        return Map.of("command", normalized);
    }

    private static Map<String, Object> chatClear() {
        ClientHistory.clear();
        return Map.of("cleared", true);
    }

    private static Map<String, Object> keyAction(KeyMapping mapping, boolean pressed, String name) {
        KeyState.set(mapping, pressed, name);
        return Map.of("key", name, "pressed", pressed);
    }

    private static Map<String, Object> pulseKey(KeyMapping mapping, String name) {
        KeyState.set(mapping, true, name);
        ClientRuntime.schedule(1L, () -> {
            KeyState.set(mapping, false, name);
            return Map.of("released", true);
        });
        return Map.of("key", name, "pressed", true, "releaseScheduled", true);
    }

    public static com.playwrightforkubejs.task.PlaywrightTask<Map<String, Object>> moveDirectionTask(Map<String, Object> params) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = player(minecraft);
        String direction = Params.string(params, "direction", "forward").toLowerCase();
        int blocks = Params.integer(params, "blocks", 1);
        if (blocks < 1) {
            throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "blocks must be at least 1");
        }
        KeyMapping mapping = directionKey(minecraft, direction);
        double yaw = Math.toRadians(player.getYRot());
        double forwardX = -Math.sin(yaw);
        double forwardZ = Math.cos(yaw);
        double axisX = switch (direction) {
            case "forward", "north" -> forwardX;
            case "back", "backward", "south" -> -forwardX;
            case "left", "west" -> Math.cos(yaw);
            case "right", "east" -> -Math.cos(yaw);
            default -> throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "Unknown direction: " + direction);
        };
        double axisZ = switch (direction) {
            case "forward", "north" -> forwardZ;
            case "back", "backward", "south" -> -forwardZ;
            case "left", "west" -> Math.sin(yaw);
            case "right", "east" -> -Math.sin(yaw);
            default -> throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "Unknown direction: " + direction);
        };
        Vec3 start = player.position();
        long generation = ClientRuntime.generation();
        long deadlineTicks = Math.min(1200L, Math.max(40L, (long) blocks * 40L));
        var result = ClientRuntime.track(com.playwrightforkubejs.task.PlaywrightTask.<Map<String, Object>>pending(generation));
        result.onComplete((value, error) -> {
            if (error != null) {
                ClientRuntime.dispatch(() -> {
                    KeyState.set(mapping, false, direction);
                    ClientRuntime.untrackTask(result);
                });
            }
        });
        ClientRuntime.dispatch(() -> {
            if (result.isDone() || result.generation() != ClientRuntime.generation()) {
                return;
            }
            KeyState.set(mapping, true, direction);
            pollMovement(result, mapping, start, axisX, axisZ, blocks, 0L, deadlineTicks, direction);
        });
        return result;
    }

    private static void pollMovement(com.playwrightforkubejs.task.PlaywrightTask<Map<String, Object>> result, KeyMapping mapping,
                                     Vec3 start, double axisX, double axisZ, int blocks, long elapsedTicks,
                                     long deadlineTicks, String direction) {
        if (result.isDone()) {
            KeyState.set(mapping, false, direction);
            ClientRuntime.untrackTask(result);
            return;
        }
        if (result.generation() != ClientRuntime.generation()) {
            KeyState.set(mapping, false, direction);
            result.fail(new PlaywrightException(ErrorCode.SCRIPT_RELOADED, "Movement task belongs to an old script generation"));
            ClientRuntime.untrackTask(result);
            return;
        }
        LocalPlayer player = player(Minecraft.getInstance());
        Vec3 displacement = player.position().subtract(start);
        double progressed = displacement.x * axisX + displacement.z * axisZ;
        if (progressed >= blocks) {
            KeyState.set(mapping, false, direction);
            result.complete(Map.of("direction", direction, "blocksRequested", blocks, "blocksMoved", progressed, "elapsedTicks", elapsedTicks));
            ClientRuntime.untrackTask(result);
            return;
        }
        if (elapsedTicks >= deadlineTicks) {
            KeyState.set(mapping, false, direction);
            result.fail(new PlaywrightException(ErrorCode.PATHFINDING_FAILED, "Movement deadline expired before the requested distance was reached"));
            ClientRuntime.untrackTask(result);
            return;
        }
        var next = ClientRuntime.schedule(1L, () -> {
            pollMovement(result, mapping, start, axisX, axisZ, blocks, elapsedTicks + 1L, deadlineTicks, direction);
            return Map.of("elapsedTicks", elapsedTicks + 1L);
        });
        next.onComplete((value, error) -> {
            if (error != null && !result.isDone()) result.fail(error);
        });
    }

    public static com.playwrightforkubejs.task.PlaywrightTask<Map<String, Object>> holdKeyTask(Map<String, Object> params) {
        String key = Params.requiredString(params, "key").toLowerCase(java.util.Locale.ROOT);
        long durationMs = Params.integer(params, "duration", 250);
        if (durationMs <= 0) {
            throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "duration must be greater than zero");
        }
        long durationTicks = Math.max(1L, Math.min(1200L, Math.round(durationMs / 50.0)));
        var result = ClientRuntime.track(com.playwrightforkubejs.task.PlaywrightTask.<Map<String, Object>>pending(ClientRuntime.generation()));
        result.onComplete((value, error) -> {
            if (error != null) {
                ClientRuntime.dispatch(KeyState::clear);
            }
            ClientRuntime.untrackTask(result);
        });
        ClientRuntime.dispatch(() -> {
            if (result.isDone()) {
                return;
            }
            try {
                ClientInput.keyState(key, true);
                var release = ClientRuntime.schedule(durationTicks, () -> {
                    if (result.isDone() || result.generation() != ClientRuntime.generation()) {
                        return Map.of("ignored", true);
                    }
                    ClientInput.keyState(key, false);
                    result.complete(Map.of("key", key, "durationTicks", durationTicks));
                    ClientRuntime.untrackTask(result);
                    return Map.of("released", true);
                });
                release.onComplete((value, error) -> {
                    if (error != null && !result.isDone()) {
                        result.fail(error);
                    }
                });
            } catch (Throwable error) {
                KeyState.clear();
                result.fail(error);
            }
        });
        return result;
    }

    public static com.playwrightforkubejs.task.PlaywrightTask<Map<String, Object>> moveToTask(Map<String, Object> params) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = player(minecraft);
        Vec3 target = Params.vec3(params);
        double timeoutSeconds = Params.number(params, "timeout", 30.0);
        if (timeoutSeconds <= 0.0) {
            throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "move.to timeout must be greater than zero");
        }
        long timeoutTicks = Math.max(1L, Math.min(1200L, Math.round(timeoutSeconds * 20.0)));
        MoveToState state = new MoveToState(minecraft, target, timeoutTicks, ClientRuntime.generation());
        state.task = ClientRuntime.track(com.playwrightforkubejs.task.PlaywrightTask.<Map<String, Object>>pending(state.generation));
        state.task.onComplete((value, error) -> {
            if (error != null) {
                ClientRuntime.dispatch(state::releaseKey);
            }
        });
        ClientRuntime.dispatch(() -> {
            if (state.task.isDone() || state.generation != ClientRuntime.generation()) {
                return;
            }
            state.bestDistance = player.position().distanceTo(target);
            lookAt(minecraft, target);
            state.press(minecraft.options.keyUp, "forward");
            scheduleMoveTo(state, 0L);
        });
        return state.task;
    }

    private static void scheduleMoveTo(MoveToState state, long elapsedTicks) {
        ClientRuntime.schedule(1L, () -> {
            stepMoveTo(state, elapsedTicks + 1L);
            return Map.of("elapsedTicks", elapsedTicks + 1L);
        });
    }

    private static void stepMoveTo(MoveToState state, long elapsedTicks) {
        if (state.task.isDone()) {
            state.releaseKey();
            ClientRuntime.untrackTask(state.task);
            return;
        }
        if (state.generation != ClientRuntime.generation()) {
            state.releaseKey();
            state.task.fail(new PlaywrightException(ErrorCode.SCRIPT_RELOADED, "Navigation belongs to an old script generation"));
            ClientRuntime.untrackTask(state.task);
            return;
        }
        try {
            LocalPlayer player = player(state.minecraft);
            Vec3 position = player.position();
            Vec3 delta = state.target.subtract(position);
            double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            double distance = position.distanceTo(state.target);
            if (horizontal < 0.75 && Math.abs(delta.y) < 1.25) {
                state.releaseKey();
                state.task.complete(NavigationDiagnostics.result(true, ClientQueries.execute("position.get", Map.of()),
                    distance, elapsedTicks, state.timeoutTicks, state.bestDistance, state.stalledTicks, state.strafeAttempts));
                ClientRuntime.untrackTask(state.task);
                return;
            }
            if (elapsedTicks >= state.timeoutTicks) {
                state.releaseKey();
                state.task.complete(NavigationDiagnostics.result(false, ClientQueries.execute("position.get", Map.of()),
                    distance, elapsedTicks, state.timeoutTicks, state.bestDistance, state.stalledTicks, state.strafeAttempts));
                ClientRuntime.untrackTask(state.task);
                return;
            }
            lookAt(state.minecraft, state.target);
            if (delta.y > 0.6 && player.onGround()) {
                player.setJumping(true);
            }
            if (distance + 0.05 < state.bestDistance) {
                state.bestDistance = distance;
                state.stalledTicks = 0;
            } else {
                state.stalledTicks++;
            }
            if (state.strafeTicks > 0) {
                state.strafeTicks--;
                if (state.strafeTicks == 0) {
                    state.press(state.minecraft.options.keyUp, "forward");
                    state.stalledTicks = 0;
                }
            } else if (state.stalledTicks >= 4 && horizontal < 4.0 && Math.abs(delta.y) < 2.0) {
                KeyMapping strafe = state.strafeLeft ? state.minecraft.options.keyLeft : state.minecraft.options.keyRight;
                state.press(strafe, state.strafeLeft ? "left" : "right");
                state.strafeLeft = !state.strafeLeft;
                state.strafeAttempts++;
                state.strafeTicks = 5;
            } else {
                state.press(state.minecraft.options.keyUp, "forward");
            }
            scheduleMoveTo(state, elapsedTicks);
        } catch (Throwable error) {
            state.releaseKey();
            state.task.fail(error);
            ClientRuntime.untrackTask(state.task);
        }
    }

    private static final class MoveToState {
        private final Minecraft minecraft;
        private final Vec3 target;
        private final long timeoutTicks;
        private final long generation;
        private com.playwrightforkubejs.task.PlaywrightTask<Map<String, Object>> task;
        private KeyMapping currentKey;
        private String currentKeyName;
        private double bestDistance = Double.MAX_VALUE;
        private int stalledTicks;
        private int strafeTicks;
        private int strafeAttempts;
        private boolean strafeLeft = true;

        private MoveToState(Minecraft minecraft, Vec3 target, long timeoutTicks, long generation) {
            this.minecraft = minecraft;
            this.target = target;
            this.timeoutTicks = timeoutTicks;
            this.generation = generation;
        }

        private void press(KeyMapping mapping, String name) {
            if (currentKey != mapping) {
                releaseKey();
                currentKey = mapping;
                currentKeyName = name;
                KeyState.set(mapping, true, name);
            }
        }

        private void releaseKey() {
            if (currentKey != null) {
                KeyState.set(currentKey, false, currentKeyName);
                currentKey = null;
                currentKeyName = null;
            }
        }
    }

    private static KeyMapping directionKey(Minecraft minecraft, String direction) {
        return switch (direction) {
            case "forward", "north" -> minecraft.options.keyUp;
            case "back", "backward", "south" -> minecraft.options.keyDown;
            case "left", "west" -> minecraft.options.keyLeft;
            case "right", "east" -> minecraft.options.keyRight;
            default -> throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "Unknown direction: " + direction);
        };
    }

    private static Map<String, Object> lookSet(Minecraft minecraft, double yaw, double pitch) {
        LocalPlayer player = player(minecraft);
        player.setYRot((float) yaw);
        player.setXRot((float) Math.max(-90, Math.min(90, pitch)));
        player.yRotO = (float) yaw;
        player.xRotO = (float) pitch;
        return Map.of("yaw", yaw, "pitch", pitch);
    }

    private static Map<String, Object> lookAt(Minecraft minecraft, Vec3 target) {
        LocalPlayer player = player(minecraft);
        Vec3 eye = player.getEyePosition();
        Vec3 delta = target.subtract(eye);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horizontal)));
        return lookSet(minecraft, yaw, pitch);
    }

    private static Map<String, Object> blockBreak(Minecraft minecraft, BlockPos pos) {
        LocalPlayer player = player(minecraft);
        if (minecraft.gameMode == null || !minecraft.gameMode.destroyBlock(pos)) {
            throw new PlaywrightException(ErrorCode.BLOCK_OUT_OF_RANGE, "Could not break block at " + pos);
        }
        return Map.of("x", pos.getX(), "y", pos.getY(), "z", pos.getZ(), "broken", true);
    }

    private static Map<String, Object> blockPlace(Minecraft minecraft, BlockPos pos, Direction face) {
        LocalPlayer player = player(minecraft);
        if (minecraft.gameMode == null) {
            throw new PlaywrightException(ErrorCode.NOT_IN_WORLD, "Client game mode is unavailable");
        }
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false);
        var interaction = minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
        return Map.of("x", pos.getX(), "y", pos.getY(), "z", pos.getZ(), "face", face.getName(),
            "interactionResult", interaction.name(), "consumesAction", interaction.consumesAction());
    }

    private static Map<String, Object> blockInteract(Minecraft minecraft, BlockPos pos, Direction face) {
        LocalPlayer player = player(minecraft);
        if (minecraft.gameMode == null) {
            throw new PlaywrightException(ErrorCode.NOT_IN_WORLD, "Client game mode is unavailable");
        }
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false);
        var interaction = minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
        return Map.of("x", pos.getX(), "y", pos.getY(), "z", pos.getZ(), "face", face.getName(),
            "interactionResult", interaction.name(), "consumesAction", interaction.consumesAction());
    }

    private static Entity entity(Minecraft minecraft, int id) {
        player(minecraft);
        Entity entity = minecraft.level.getEntity(id);
        if (entity == null) {
            throw new PlaywrightException(ErrorCode.ENTITY_NOT_FOUND, "Entity id not found: " + id);
        }
        return entity;
    }

    private static Map<String, Object> entityAttack(Minecraft minecraft, Map<String, Object> params) {
        if (minecraft.gameMode == null) {
            throw new PlaywrightException(ErrorCode.NOT_IN_WORLD, "Client game mode is unavailable");
        }
        Entity entity = findEntity(minecraft, params);
        minecraft.gameMode.attack(minecraft.player, entity);
        return Map.of("id", entity.getId(), "attacked", true);
    }

    private static Map<String, Object> entityInteract(Minecraft minecraft, Map<String, Object> params) {
        if (minecraft.gameMode == null) {
            throw new PlaywrightException(ErrorCode.NOT_IN_WORLD, "Client game mode is unavailable");
        }
        Entity entity = findEntity(minecraft, params);
        var interaction = minecraft.gameMode.interact(minecraft.player, entity, InteractionHand.MAIN_HAND);
        return Map.of("id", entity.getId(), "interacted", interaction.consumesAction(),
            "interactionResult", interaction.name(), "consumesAction", interaction.consumesAction());
    }

    private static Map<String, Object> entityMount(Minecraft minecraft, Map<String, Object> params) {
        Entity entity = findEntity(minecraft, params);
        boolean mounted = minecraft.player.startRiding(entity, true);
        if (!mounted) {
            throw new PlaywrightException(ErrorCode.ENTITY_NOT_FOUND, "Could not mount entity " + entity.getId());
        }
        return Map.of("id", entity.getId(), "mounted", true);
    }

    private static Map<String, Object> lookAtEntity(Minecraft minecraft, Map<String, Object> params) {
        Entity entity = findEntity(minecraft, params);
        Map<String, Object> result = new LinkedHashMap<>(lookAt(minecraft, new Vec3(entity.getX(), entity.getEyeY(), entity.getZ())));
        result.put("entityId", entity.getId());
        return result;
    }

    private static Entity findEntity(Minecraft minecraft, Map<String, Object> params) {
        LocalPlayer player = player(minecraft);
        Map<String, Object> filter = entityFilter(params);
        int id = Params.integer(filter, "id", -1);
        String type = Params.string(filter, "type", null);
        String name = Params.string(filter, "name", null);
        boolean maxDistanceSpecified = filter.containsKey("maxDistance");
        double maxDistance = Params.number(filter, "maxDistance", 128.0);
        if (maxDistanceSpecified && maxDistance < 0.0) {
            throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "maxDistance must not be negative");
        }
        double searchRadius = maxDistanceSpecified ? maxDistance : 256.0;
        List<Entity> candidates = new java.util.ArrayList<>(minecraft.level.getEntities(
            player,
            player.getBoundingBox().inflate(searchRadius),
            Entity::isAlive
        ));
        String normalizedType = type == null ? null : (type.contains(":") ? type : "minecraft:" + type).toLowerCase(java.util.Locale.ROOT);
        candidates.removeIf(entity -> {
            if (id >= 0 && entity.getId() != id) {
                return true;
            }
            if (normalizedType != null) {
                var key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
                if (key == null || !key.toString().equalsIgnoreCase(normalizedType)) {
                    return true;
                }
            }
            if (name != null) {
                String entityName = entity.getName().getString();
                try {
                    if (!entityName.matches(name) && !entityName.contains(name)) {
                        return true;
                    }
                } catch (java.util.regex.PatternSyntaxException exception) {
                    if (!entityName.contains(name)) {
                        return true;
                    }
                }
            }
            return maxDistanceSpecified && player.distanceTo(entity) > maxDistance;
        });
        if (candidates.isEmpty()) {
            throw new PlaywrightException(ErrorCode.ENTITY_NOT_FOUND, "No entity matched the supplied filter");
        }
        candidates.sort(java.util.Comparator.comparingDouble(player::distanceToSqr));
        return candidates.get(0);
    }

    private static Map<String, Object> entityFilter(Map<String, Object> params) {
        if (params == null) {
            throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "Entity filter is required");
        }
        Object rawFilter = params.get("filter");
        if (rawFilter instanceof Map<?, ?> map) {
            Map<String, Object> filter = new LinkedHashMap<>();
            map.forEach((key, value) -> filter.put(String.valueOf(key), value));
            return filter;
        }
        if (params.containsKey("id") || params.containsKey("type") || params.containsKey("name") || params.containsKey("maxDistance")) {
            return params;
        }
        throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "Entity filter requires id, type, name, or maxDistance");
    }

    private static Map<String, Object> entityDismount(Minecraft minecraft) {
        LocalPlayer player = player(minecraft);
        player.stopRiding();
        return Map.of("dismounted", true);
    }

    private static Map<String, Object> hotbar(Minecraft minecraft, int slot) {
        LocalPlayer player = player(minecraft);
        if (slot < 0 || slot >= 9) {
            throw new PlaywrightException(ErrorCode.SLOT_OUT_OF_RANGE, "Hotbar slot must be between 0 and 8");
        }
        player.getInventory().selected = slot;
        return Map.of("slot", slot);
    }

    private static Map<String, Object> drop(Minecraft minecraft, boolean all) {
        LocalPlayer player = player(minecraft);
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            return Map.of("dropped", false, "empty", true);
        }
        player.drop(all);
        return Map.of("dropped", true, "all", all);
    }

    private static Map<String, Object> use(Minecraft minecraft) {
        LocalPlayer player = player(minecraft);
        if (minecraft.gameMode == null) {
            throw new PlaywrightException(ErrorCode.NOT_IN_WORLD, "Client game mode is unavailable");
        }
        var interaction = minecraft.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        return Map.of("used", interaction.consumesAction(), "interactionResult", interaction.name(),
            "consumesAction", interaction.consumesAction());
    }

    private static Map<String, Object> swapHands(Minecraft minecraft) {
        LocalPlayer player = player(minecraft);
        player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerActionPacket(
            net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
            player.blockPosition(), Direction.DOWN, 0));
        return Map.of("swapped", true);
    }

    private static Map<String, Object> guiClick(Minecraft minecraft, int slot, int button) {
        LocalPlayer player = player(minecraft);
        if (!(minecraft.screen instanceof AbstractContainerScreen<?> screen)) {
            throw new PlaywrightException(ErrorCode.GUI_NOT_OPEN, "No container GUI is open");
        }
        if (slot < 0 || slot >= screen.getMenu().slots.size()) {
            throw new PlaywrightException(ErrorCode.SLOT_OUT_OF_RANGE, "GUI slot out of range: " + slot);
        }
        if (minecraft.gameMode == null) {
            throw new PlaywrightException(ErrorCode.NOT_IN_WORLD, "Client game mode is unavailable");
        }
        minecraft.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, slot, button, ClickType.PICKUP, player);
        return Map.of("slot", slot, "button", button);
    }

    private static Map<String, Object> guiDrag(Minecraft minecraft, Map<String, Object> params) {
        LocalPlayer player = player(minecraft);
        if (!(minecraft.screen instanceof AbstractContainerScreen<?> screen)) {
            throw new PlaywrightException(ErrorCode.GUI_NOT_OPEN, "No container GUI is open");
        }
        Object rawSlots = params == null ? null : params.get("slots");
        if (!(rawSlots instanceof List<?> slots) || slots.isEmpty()) {
            throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "gui.drag requires a non-empty slots array");
        }
        int button = Params.integer(params, "button", 0);
        if (button != 0 && button != 1) {
            throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "gui.drag button must be 0 (left) or 1 (right)");
        }
        if (minecraft.gameMode == null) {
            throw new PlaywrightException(ErrorCode.NOT_IN_WORLD, "Client game mode is unavailable");
        }
        AbstractContainerMenu menu = screen.getMenu();
        for (Object rawSlot : slots) {
            int slot = rawSlot instanceof Number number ? number.intValue() : Params.integer(Map.of("slot", rawSlot), "slot", -1);
            if (slot < 0 || slot >= menu.slots.size()) {
                throw new PlaywrightException(ErrorCode.SLOT_OUT_OF_RANGE, "GUI drag slot out of range: " + slot);
            }
        }
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, -999, AbstractContainerMenu.getQuickcraftMask(0, button), ClickType.QUICK_CRAFT, player);
        for (Object rawSlot : slots) {
            int slot = rawSlot instanceof Number number ? number.intValue() : Params.integer(Map.of("slot", rawSlot), "slot", -1);
            minecraft.gameMode.handleInventoryMouseClick(menu.containerId, slot, AbstractContainerMenu.getQuickcraftMask(1, button), ClickType.QUICK_CRAFT, player);
        }
        minecraft.gameMode.handleInventoryMouseClick(menu.containerId, -999, AbstractContainerMenu.getQuickcraftMask(2, button), ClickType.QUICK_CRAFT, player);
        return Map.of("dragged", true, "slots", slots, "button", button, "cursorItem", ItemData.of(menu.getCarried(), -1));
    }

    private static Map<String, Object> guiClose(Minecraft minecraft) {
        if (minecraft.screen == null) {
            return Map.of("closed", false);
        }
        Screen screen = minecraft.screen;
        screen.onClose();
        return Map.of("closed", minecraft.screen != screen, "screenChanged", minecraft.screen != screen);
    }

    public static com.playwrightforkubejs.task.PlaywrightTask<Map<String, Object>> screenshotTask(String output) {
        Minecraft minecraft = Minecraft.getInstance();
        Path requested = Path.of(output == null || output.isBlank() ? "screenshots/playwright.png" : output);
        Path target = (requested.isAbsolute() ? requested : minecraft.gameDirectory.toPath().resolve(requested)).toAbsolutePath().normalize();
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
        } catch (IOException exception) {
            return com.playwrightforkubejs.task.PlaywrightTask.failed(
                new PlaywrightException(ErrorCode.INTERNAL_ERROR, "Could not create screenshot directory", exception),
                ClientRuntime.generation()
            );
        }
        long generation = ClientRuntime.generation();
        var task = ClientRuntime.track(com.playwrightforkubejs.task.PlaywrightTask.<Map<String, Object>>pending(generation));
        Runnable capture = () -> {
            if (task.isDone() || generation != ClientRuntime.generation()) {
                task.fail(new PlaywrightException(ErrorCode.SCRIPT_RELOADED, "Screenshot belongs to an old script generation"));
                ClientRuntime.untrackTask(task);
                return;
            }
            final NativeImage image;
            try {
                image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget());
            } catch (Throwable error) {
                task.fail(new PlaywrightException(ErrorCode.INTERNAL_ERROR, "Could not capture Minecraft framebuffer", error));
                ClientRuntime.untrackTask(task);
                return;
            }
            Util.ioPool().execute(() -> {
                try {
                    if (task.isDone()) {
                        return;
                    }
                    image.writeToFile(target.toFile());
                    EventBus.getInstance().record("screenshot.saved", Map.of("path", target.toString()));
                    task.complete(Map.of("path", target.toString(), "width", image.getWidth(), "height", image.getHeight()));
                } catch (IOException exception) {
                    task.fail(new PlaywrightException(ErrorCode.INTERNAL_ERROR, "Could not write screenshot: " + target, exception));
                } catch (Throwable error) {
                    task.fail(new PlaywrightException(ErrorCode.INTERNAL_ERROR, "Screenshot encoding failed", error));
                } finally {
                    image.close();
                    ClientRuntime.untrackTask(task);
                }
            });
        };
        if (RenderSystem.isOnRenderThread()) {
            capture.run();
        } else {
            RenderSystem.recordRenderCall(capture::run);
        }
        return task;
    }

    private static Map<String, Object> respawn(Minecraft minecraft) {
        LocalPlayer player = player(minecraft);
        player.respawn();
        return Map.of("respawned", true);
    }
}
