package com.playwrightforkubejs.client;

import com.playwrightforkubejs.protocol.ErrorCode;
import com.playwrightforkubejs.protocol.Params;
import com.playwrightforkubejs.protocol.PlaywrightException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ClientQueries {
    private ClientQueries() {
    }

    public static Map<String, Object> execute(String query, Map<String, Object> params) {
        Minecraft minecraft = Minecraft.getInstance();
        return switch (query) {
            case "position.get" -> position(minecraft);
            case "rotation.get" -> rotation(minecraft);
            case "status.health" -> health(minecraft);
            case "status.effects" -> effects(minecraft);
            case "status.experience" -> experience(minecraft);
            case "status.gamemode" -> gamemode(minecraft);
            case "status.world" -> world(minecraft);
            case "status.all" -> all(minecraft);
            case "block.get" -> block(minecraft, Params.blockPos(params));
            case "entity.list" -> entities(minecraft, Params.number(params, "radius", 16));
            case "entity.info" -> entityInfo(minecraft, Params.integer(params, "id", -1));
            case "inventory.get" -> inventory(minecraft);
            case "inventory.slot", "gui.slot" -> inventorySlot(minecraft, Params.integer(params, "slot", -1), query.startsWith("gui."));
            case "inventory.held" -> held(minecraft);
            case "inventory.equipment" -> equipment(minecraft);
            case "gui.info", "gui.layout", "gui.snapshot" -> gui(minecraft, query);
            case "gui.locator" -> ClientGui.lookup(Params.requiredString(params, "selector"));
            case "screen.size" -> screenSize(minecraft);
            case "chat.history" -> chatHistory(params);
            case "chat.last" -> chatLast();
            case "input.keys-down" -> Map.of("keys", KeyState.keysDown());
            case "input.mouse-pos" -> ClientInput.mousePosition();
            case "hud.scoreboard" -> scoreboard(minecraft);
            case "hud.tab" -> tabList(minecraft);
            case "hud.nametag" -> nametag(minecraft, Params.requiredString(params, "player"));
            default -> throw new PlaywrightException(ErrorCode.INVALID_ACTION, "Unknown query: " + query);
        };
    }

    private static LocalPlayer player(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null) {
            throw new PlaywrightException(ErrorCode.NOT_IN_WORLD, "The client is not in a world");
        }
        return minecraft.player;
    }

    private static Map<String, Object> position(Minecraft minecraft) {
        LocalPlayer player = player(minecraft);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("x", player.getX());
        result.put("y", player.getY());
        result.put("z", player.getZ());
        result.put("blockX", player.blockPosition().getX());
        result.put("blockY", player.blockPosition().getY());
        result.put("blockZ", player.blockPosition().getZ());
        result.put("dimension", minecraft.level.dimension().location().toString());
        result.put("onGround", player.onGround());
        return result;
    }

    private static Map<String, Object> rotation(Minecraft minecraft) {
        LocalPlayer player = player(minecraft);
        return Map.of("yaw", player.getYRot(), "pitch", player.getXRot());
    }

    private static Map<String, Object> health(Minecraft minecraft) {
        LocalPlayer player = player(minecraft);
        return Map.of(
            "health", player.getHealth(),
            "maxHealth", player.getMaxHealth(),
            "food", player.getFoodData().getFoodLevel(),
            "saturation", player.getFoodData().getSaturationLevel(),
            "armor", player.getArmorValue(),
            "absorption", player.getAbsorptionAmount(),
            "attackStrength", player.getAttackStrengthScale(0f)
        );
    }

    private static Map<String, Object> effects(Minecraft minecraft) {
        LocalPlayer player = player(minecraft);
        List<Map<String, Object>> values = new ArrayList<>();
        player.getActiveEffects().forEach(effect -> values.add(Map.of(
            "effect", BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()).toString(),
            "amplifier", effect.getAmplifier(),
            "duration", effect.getDuration(),
            "ambient", effect.isAmbient(),
            "visible", effect.isVisible()
        )));
        return Map.of("effects", values);
    }

    private static Map<String, Object> experience(Minecraft minecraft) {
        LocalPlayer player = player(minecraft);
        return Map.of("level", player.experienceLevel, "progress", player.experienceProgress, "total", player.totalExperience);
    }

    private static Map<String, Object> gamemode(Minecraft minecraft) {
        LocalPlayer player = player(minecraft);
        return Map.of("gamemode", minecraft.gameMode == null ? "unknown" : minecraft.gameMode.getPlayerMode().getName());
    }

    private static Map<String, Object> world(Minecraft minecraft) {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean inWorld = minecraft.player != null && minecraft.level != null;
        result.put("inWorld", inWorld);
        result.put("levelName", "");
        result.put("levelId", "");
        result.put("integratedServer", minecraft.getSingleplayerServer() != null);
        if (!inWorld) {
            return result;
        }
        result.put("dimension", minecraft.level.dimension().location().toString());
        result.put("gameTime", minecraft.level.getGameTime());
        result.put("dayTime", minecraft.level.getDayTime());
        result.put("difficulty", minecraft.level.getDifficulty().getKey());
        var server = minecraft.getSingleplayerServer();
        if (server != null) {
            result.put("levelName", server.getWorldData().getLevelName());
            java.nio.file.Path directory = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize();
            if (directory.getFileName() != null) {
                result.put("levelId", directory.getFileName().toString());
            }
        }
        return result;
    }

    private static Map<String, Object> all(Minecraft minecraft) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("position", position(minecraft));
        result.put("rotation", rotation(minecraft));
        result.put("health", health(minecraft));
        result.put("world", world(minecraft));
        result.put("screen", minecraft.screen == null ? null : minecraft.screen.getClass().getSimpleName());
        result.put("chat", chatHistory(Map.of("last", 10)));
        return result;
    }

    private static Map<String, Object> scoreboard(Minecraft minecraft) {
        player(minecraft);
        Scoreboard scoreboard = minecraft.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) {
            return Map.of("visible", false, "scores", List.of());
        }
        List<Map<String, Object>> scores = scoreboard.listPlayerScores(objective).stream()
            .sorted(Comparator.comparingInt(PlayerScoreEntry::value).reversed())
            .map(score -> Map.<String, Object>of("owner", score.owner(), "score", score.value()))
            .toList();
        return Map.of("visible", true, "objective", objective.getName(), "title", objective.getDisplayName().getString(), "scores", scores);
    }

    private static Map<String, Object> tabList(Minecraft minecraft) {
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null) {
            return Map.of("visible", false, "players", List.of());
        }
        List<Map<String, Object>> players = connection.getOnlinePlayers().stream().map(info -> playerInfo(minecraft, info)).toList();
        return Map.of("visible", true, "players", players, "count", players.size());
    }

    private static Map<String, Object> nametag(Minecraft minecraft, String name) {
        ClientPacketListener connection = minecraft.getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(name);
        if (info == null) {
            throw new PlaywrightException(ErrorCode.ENTITY_NOT_FOUND, "Player not found in tab list: " + name);
        }
        Map<String, Object> result = new LinkedHashMap<>(playerInfo(minecraft, info));
        result.put("displayName", minecraft.gui.getTabList().getNameForDisplay(info).getString());
        return result;
    }

    private static Map<String, Object> playerInfo(Minecraft minecraft, PlayerInfo info) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", info.getProfile().getName());
        result.put("uuid", info.getProfile().getId().toString());
        result.put("latency", info.getLatency());
        result.put("gameMode", info.getGameMode() == null ? "unknown" : info.getGameMode().getName());
        result.put("displayName", info.getTabListDisplayName() == null
            ? minecraft.gui.getTabList().getNameForDisplay(info).getString()
            : info.getTabListDisplayName().getString());
        return result;
    }

    private static Map<String, Object> block(Minecraft minecraft, BlockPos pos) {
        player(minecraft);
        BlockState state = minecraft.level.getBlockState(pos);
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        return Map.of(
            "x", pos.getX(), "y", pos.getY(), "z", pos.getZ(),
            "block", id,
            "state", state.toString(),
            "solid", state.isSolid(),
            "replaceable", state.canBeReplaced()
        );
    }

    private static Map<String, Object> entities(Minecraft minecraft, double radius) {
        LocalPlayer player = player(minecraft);
        List<Map<String, Object>> result = new ArrayList<>();
        minecraft.level.getEntities(player, player.getBoundingBox().inflate(radius), entity -> true)
            .forEach(entity -> result.add(entity(entity)));
        return Map.of("entities", result, "count", result.size());
    }

    private static Map<String, Object> entityInfo(Minecraft minecraft, int id) {
        player(minecraft);
        Entity entity = minecraft.level.getEntity(id);
        if (entity == null) {
            throw new PlaywrightException(ErrorCode.ENTITY_NOT_FOUND, "Entity id not found: " + id);
        }
        return entity(entity);
    }

    private static Map<String, Object> entity(Entity entity) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", entity.getId());
        result.put("uuid", entity.getUUID().toString());
        result.put("type", BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
        result.put("name", entity.getName().getString());
        result.put("x", entity.getX());
        result.put("y", entity.getY());
        result.put("z", entity.getZ());
        result.put("yaw", entity.getYRot());
        result.put("pitch", entity.getXRot());
        result.put("alive", entity.isAlive());
        if (entity instanceof LivingEntity living) {
            result.put("health", living.getHealth());
            result.put("maxHealth", living.getMaxHealth());
        }
        return result;
    }

    private static Map<String, Object> inventory(Minecraft minecraft) {
        LocalPlayer player = player(minecraft);
        List<Map<String, Object>> values = new ArrayList<>();
        for (int slot = 0; slot < player.getInventory().items.size(); slot++) {
            values.add(ItemData.of(player.getInventory().items.get(slot), slot));
        }
        for (int slot = 0; slot < player.getInventory().armor.size(); slot++) {
            values.add(ItemData.of(player.getInventory().armor.get(slot), 36 + slot));
        }
        values.add(ItemData.of(player.getInventory().offhand.get(0), 40));
        return Map.of("items", values, "size", values.size());
    }

    private static Map<String, Object> inventorySlot(Minecraft minecraft, int index, boolean gui) {
        if (index < 0) {
            throw new PlaywrightException(ErrorCode.SLOT_OUT_OF_RANGE, "Slot must be non-negative");
        }
        if (gui) {
            Screen screen = minecraft.screen;
            if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
                throw new PlaywrightException(ErrorCode.GUI_NOT_OPEN, "No container GUI is open");
            }
            AbstractContainerMenu menu = containerScreen.getMenu();
            if (index >= menu.slots.size()) {
                throw new PlaywrightException(ErrorCode.SLOT_OUT_OF_RANGE, "GUI slot out of range: " + index);
            }
            Slot slot = menu.slots.get(index);
            return ItemData.of(slot.getItem(), index);
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> values = (List<Map<String, Object>>) inventory(minecraft).get("items");
        if (index >= values.size()) {
            throw new PlaywrightException(ErrorCode.SLOT_OUT_OF_RANGE, "Inventory slot out of range: " + index);
        }
        return values.get(index);
    }

    private static Map<String, Object> held(Minecraft minecraft) {
        LocalPlayer player = player(minecraft);
        return ItemData.of(player.getMainHandItem(), -1);
    }

    private static Map<String, Object> equipment(Minecraft minecraft) {
        LocalPlayer player = player(minecraft);
        Map<String, Map<String, Object>> equipment = new LinkedHashMap<>();
        equipment.put("head", ItemData.of(player.getInventory().getItem(39), 39));
        equipment.put("chest", ItemData.of(player.getInventory().getItem(38), 38));
        equipment.put("legs", ItemData.of(player.getInventory().getItem(37), 37));
        equipment.put("feet", ItemData.of(player.getInventory().getItem(36), 36));
        equipment.put("mainhand", ItemData.of(player.getMainHandItem(), player.getInventory().selected));
        equipment.put("offhand", ItemData.of(player.getInventory().getItem(40), 40));
        return Map.of("equipment", equipment, "armorValue", player.getArmorValue());
    }

    private static Map<String, Object> gui(Minecraft minecraft, String query) {
        Screen screen = minecraft.screen;
        Map<String, Object> result = new LinkedHashMap<>(ClientGui.snapshot());
        if (screen == null) {
            return result;
        }
        if (screen instanceof AbstractContainerScreen<?> containerScreen) {
            AbstractContainerMenu menu = containerScreen.getMenu();
            List<Map<String, Object>> slots = new ArrayList<>();
            for (int index = 0; index < menu.slots.size(); index++) {
                Slot slot = menu.slots.get(index);
                Map<String, Object> data = new LinkedHashMap<>(ItemData.of(slot.getItem(), index));
                data.put("x", slot.x);
                data.put("y", slot.y);
                data.put("guiX", containerScreen.getGuiLeft() + slot.x);
                data.put("guiY", containerScreen.getGuiTop() + slot.y);
                data.put("containerId", menu.containerId);
                slots.add(data);
            }
            result.put("containerId", menu.containerId);
            result.put("slots", slots);
            result.put("carried", ItemData.of(menu.getCarried(), -1));
        }
        return result;
    }

    private static Map<String, Object> screenSize(Minecraft minecraft) {
        return Map.of("width", minecraft.getWindow().getGuiScaledWidth(), "height", minecraft.getWindow().getGuiScaledHeight());
    }

    private static Map<String, Object> chatHistory(Map<String, Object> params) {
        int last = Params.integer(params, "last", 50);
        List<String> messages = ClientHistory.chat(last);
        String match = Params.string(params, "match", null);
        if (match != null) {
            messages = messages.stream().filter(message -> message.contains(match)).toList();
        }
        return Map.of("messages", messages);
    }

    private static Map<String, Object> chatLast() {
        return Map.of("message", ClientHistory.last() == null ? "" : ClientHistory.last());
    }
}
