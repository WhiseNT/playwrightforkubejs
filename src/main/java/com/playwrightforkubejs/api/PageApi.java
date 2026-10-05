package com.playwrightforkubejs.api;

import com.playwrightforkubejs.client.GuiSelector;
import com.playwrightforkubejs.client.ClientWaits;
import com.playwrightforkubejs.task.PlaywrightTask;
import com.playwrightforkubejs.task.RhinoCallbacks;
import com.playwrightforkubejs.protocol.ErrorCode;
import com.playwrightforkubejs.protocol.PlaywrightException;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.util.HideFromJS;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PageApi {
    private final PlaywrightClient client;
    private final ChatApi chat = new ChatApi(this);
    private final GuiApi gui = new GuiApi(this);
    private final InventoryApi inventory = new InventoryApi(this);
    private final BlockApi block = new BlockApi(this);
    private final EntityApi entity = new EntityApi(this);
    private final InputApi input = new InputApi(this);
    private final MoveApi move = new MoveApi(this);
    private final LookApi look = new LookApi(this);
    private final StatusApi status = new StatusApi(this);
    private final ScreenshotApi screenshot = new ScreenshotApi(this);
    private final WaitApi wait = new WaitApi(this);

    PageApi(PlaywrightClient client) {
        this.client = client;
    }

    public ChatApi chat() { return chat; }
    public ChatApi getChat() { return chat; }
    public GuiApi gui() { return gui; }
    public GuiApi getGui() { return gui; }
    public InventoryApi inventory() { return inventory; }
    public InventoryApi getInventory() { return inventory; }
    public BlockApi block() { return block; }
    public BlockApi getBlock() { return block; }
    public EntityApi entity() { return entity; }
    public EntityApi getEntity() { return entity; }
    public InputApi input() { return input; }
    public InputApi getInput() { return input; }
    public MoveApi move() { return move; }
    public MoveApi getMove() { return move; }
    public LookApi look() { return look; }
    public LookApi getLook() { return look; }
    public StatusApi status() { return status; }
    public StatusApi getStatus() { return status; }
    public ScreenshotApi screenshot() { return screenshot; }
    public ScreenshotApi getScreenshot() { return screenshot; }
    public WaitApi waitApi() { return wait; }
    public WaitApi getWait() { return wait; }

    public PlaywrightTask<Map<String, Object>> action(String name, Map<String, Object> params) {
        return client.action(name, params);
    }

    public PlaywrightTask<Map<String, Object>> query(String name, Map<String, Object> params) {
        return client.query(name, params);
    }

    public LocatorApi locator(String selector) {
        return new LocatorApi(this, selector);
    }

    public LocatorApi getByText(String text) { return locator("text=" + text); }
    public LocatorApi getByGuiText(String text) { return locator("gui-text=" + text); }
    public LocatorApi getByTranslation(String key) { return locator("translation=" + key); }
    public LocatorApi getByWorld(String nameOrId) { return locator("world=" + nameOrId); }
    public LocatorApi getByWidget(String path) { return locator("widget=" + path); }
    public LocatorApi getByRole(String role) { return locator("role=" + role); }
    public LocatorApi getByRole(String role, String name) { return locator(GuiSelector.role(role, name)); }
    public LocatorApi getBySlot(int slot) { return locator("slot=" + slot); }
    public LocatorApi getByItem(String item) { return locator("item=" + item); }
    public LocatorApi getByEntity(String type) { return locator("entity=" + type); }

    public PlaywrightTask<Map<String, Object>> waitUntil(Scriptable predicate, long timeoutMs) {
        return waitUntil((Object) predicate, timeoutMs);
    }

    public PlaywrightTask<Map<String, Object>> waitUntil(Object predicate, long timeoutMs) {
        RhinoCallbacks.requireFunction(predicate);
        if (timeoutMs <= 0) {
            throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "waitUntil timeout must be greater than zero");
        }
        return ClientWaits.until("page.waitUntil", () -> {
            Object value = RhinoCallbacks.invoke(predicate);
            if (value instanceof PlaywrightTask<?>) {
                throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "waitUntil predicate must be synchronous, not a task");
            }
            if (!(value instanceof Boolean)) {
                throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "waitUntil predicate must return a boolean");
            }
            return Boolean.TRUE.equals(value);
        }, Math.max(1L, (timeoutMs + 49L) / 50L));
    }

    public PlaywrightTask<Map<String, Object>> waitForTimeout(long milliseconds) {
        return client.waitForTimeout(milliseconds);
    }

    public PlaywrightTask<Map<String, Object>> waitForChat(String match, long timeoutMs) {
        return client.waitForChat(match, timeoutMs);
    }

    public PlaywrightTask<Map<String, Object>> waitForGui(long timeoutMs) {
        return client.waitForGui(timeoutMs);
    }

    public static final class ChatApi {
        private final PageApi page;
        private ChatApi(PageApi page) { this.page = page; }
        public PlaywrightTask<Map<String, Object>> send(String message) { return page.action("chat.send", Map.of("message", message)); }
        public PlaywrightTask<Map<String, Object>> command(String command) { return page.action("chat.command", Map.of("command", command)); }
        public PlaywrightTask<Map<String, Object>> clear() { return page.action("chat.clear", Map.of()); }
        public PlaywrightTask<Map<String, Object>> waitFor(String match, long timeoutMs) { return page.waitForChat(match, timeoutMs); }
        public PlaywrightTask<Map<String, Object>> history(int last) { return page.query("chat.history", Map.of("last", last)); }
        public PlaywrightTask<Map<String, Object>> last() { return page.query("chat.last", Map.of()); }
    }

    public static final class GuiApi {
        private final PageApi page;
        private GuiApi(PageApi page) { this.page = page; }
        public PlaywrightTask<Map<String, Object>> waitForOpen(long timeoutMs) { return page.waitForGui(timeoutMs); }
        public PlaywrightTask<Map<String, Object>> waitFor(String screenType, long timeoutMs) { return ClientWaits.screen(screenType, Math.max(1L, (timeoutMs + 49L) / 50L)); }
        public LocatorApi getByText(String text) { return page.getByGuiText(text); }
        public LocatorApi getByTranslation(String key) { return page.getByTranslation(key); }
        public LocatorApi getByRole(String role) { return page.getByRole(role); }
        public LocatorApi getByRole(String role, String name) { return page.getByRole(role, name); }
        public LocatorApi getByWorld(String nameOrId) { return page.getByWorld(nameOrId); }
        public PlaywrightTask<Map<String, Object>> waitForUpdate(long timeoutMs) { return ClientWaits.screenUpdate(Math.max(1L, Math.round(timeoutMs / 50.0))); }
        public PlaywrightTask<Map<String, Object>> info() { return page.query("gui.info", Map.of()); }
        public PlaywrightTask<Map<String, Object>> layout() { return page.query("gui.layout", Map.of()); }
        public PlaywrightTask<Map<String, Object>> snapshot() { return page.query("gui.snapshot", Map.of()); }
        public PlaywrightTask<Map<String, Object>> slot(int slot) { return page.query("gui.slot", Map.of("slot", slot)); }
        public PlaywrightTask<Map<String, Object>> click(int slot) { return click(slot, 0); }
        public PlaywrightTask<Map<String, Object>> click(int slot, int button) { return page.action("gui.click", Map.of("slot", slot, "button", button)); }
        public PlaywrightTask<Map<String, Object>> drag(List<Integer> slots, int button) { return page.action("gui.drag", Map.of("slots", slots, "button", button)); }
        public PlaywrightTask<Map<String, Object>> close() { return page.action("gui.close", Map.of()); }
    }

    public static final class InventoryApi {
        private final PageApi page;
        private InventoryApi(PageApi page) { this.page = page; }
        public PlaywrightTask<Map<String, Object>> snapshot() { return page.query("inventory.get", Map.of()); }
        public PlaywrightTask<Map<String, Object>> slot(int slot) { return page.query("inventory.slot", Map.of("slot", slot)); }
        public PlaywrightTask<Map<String, Object>> held() { return page.query("inventory.held", Map.of()); }
        public PlaywrightTask<Map<String, Object>> equipment() { return page.query("inventory.equipment", Map.of()); }
        public PlaywrightTask<Map<String, Object>> waitFor(String item, long timeoutMs) { return ClientWaits.inventory(item, Math.max(1L, Math.round(timeoutMs / 50.0))); }
        public PlaywrightTask<Map<String, Object>> selectHotbar(int slot) { return page.action("inventory.hotbar", Map.of("slot", slot)); }
        public PlaywrightTask<Map<String, Object>> drop(boolean all) { return page.action("inventory.drop", Map.of("all", all)); }
        public PlaywrightTask<Map<String, Object>> use() { return page.action("inventory.use", Map.of()); }
        public PlaywrightTask<Map<String, Object>> swapHands() { return page.action("inventory.swap-hands", Map.of()); }
    }

    public static final class BlockApi {
        private final PageApi page;
        private BlockApi(PageApi page) { this.page = page; }
        public PlaywrightTask<Map<String, Object>> get(Map<String, Object> pos) { return page.query("block.get", pos); }
        public PlaywrightTask<Map<String, Object>> breakBlock(Map<String, Object> pos) { return page.action("block.break", pos); }
        public PlaywrightTask<Map<String, Object>> place(Map<String, Object> pos, String face) { return page.action("block.place", withFace(pos, face)); }
        public PlaywrightTask<Map<String, Object>> interact(Map<String, Object> pos) { return interact(pos, "up"); }
        public PlaywrightTask<Map<String, Object>> interact(Map<String, Object> pos, String face) { return page.action("block.interact", withFace(pos, face)); }
        private static Map<String, Object> withFace(Map<String, Object> pos, String face) { Map<String, Object> copy = new LinkedHashMap<>(pos); copy.put("face", face); return copy; }
    }

    public static final class EntityApi {
        private final PageApi page;
        private EntityApi(PageApi page) { this.page = page; }
        public PlaywrightTask<Map<String, Object>> list(double radius) { return page.query("entity.list", Map.of("radius", radius)); }
        public PlaywrightTask<Map<String, Object>> info(int id) { return page.query("entity.info", Map.of("id", id)); }
        // KubeJS may wrap numbers as NativeJavaObject: Rhino otherwise selects the Map overload.
        @HideFromJS
        public PlaywrightTask<Map<String, Object>> attack(int id) { return attack((Object) id); }
        @HideFromJS
        public PlaywrightTask<Map<String, Object>> attack(Map<String, Object> filter) { return attack((Object) filter); }
        public PlaywrightTask<Map<String, Object>> attack(Object target) { return page.action("entity.attack", targetParams(target)); }
        @HideFromJS
        public PlaywrightTask<Map<String, Object>> interact(int id) { return interact((Object) id); }
        @HideFromJS
        public PlaywrightTask<Map<String, Object>> interact(Map<String, Object> filter) { return interact((Object) filter); }
        public PlaywrightTask<Map<String, Object>> interact(Object target) { return page.action("entity.interact", targetParams(target)); }
        @HideFromJS
        public PlaywrightTask<Map<String, Object>> mount(int id) { return mount((Object) id); }
        @HideFromJS
        public PlaywrightTask<Map<String, Object>> mount(Map<String, Object> filter) { return mount((Object) filter); }
        public PlaywrightTask<Map<String, Object>> mount(Object target) { return page.action("entity.mount", targetParams(target)); }
        public PlaywrightTask<Map<String, Object>> dismount() { return page.action("entity.dismount", Map.of()); }

        private static Map<String, Object> targetParams(Object target) {
            Object value = RhinoCallbacks.unwrap(target);
            if (value instanceof Number number) {
                double id = number.doubleValue();
                if (Double.isFinite(id) && id == Math.rint(id) && id >= Integer.MIN_VALUE && id <= Integer.MAX_VALUE) {
                    return Map.of("id", number.intValue());
                }
            } else if (value instanceof Map<?, ?> filter && !filter.isEmpty()) {
                return Map.of("filter", filter);
            }
            throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "Entity target must be an integer id or a non-empty filter");
        }
    }

    public static final class InputApi {
        private final PageApi page;
        private InputApi(PageApi page) { this.page = page; }
        public PlaywrightTask<Map<String, Object>> click(int button) { return page.action("input.click", Map.of("button", button)); }
        public PlaywrightTask<Map<String, Object>> doubleClick(int button) { return page.action("input.double-click", Map.of("button", button)); }
        public PlaywrightTask<Map<String, Object>> moveMouse(double x, double y) { return page.action("input.mouse-move", Map.of("x", x, "y", y)); }
        public PlaywrightTask<Map<String, Object>> scroll(double delta) { return page.action("input.scroll", Map.of("delta", delta)); }
        public PlaywrightTask<Map<String, Object>> type(String text) { return page.action("input.type", Map.of("text", text)); }
        public PlaywrightTask<Map<String, Object>> press(String key) { return page.action("input.key-press", Map.of("key", key)); }
        public PlaywrightTask<Map<String, Object>> hold(String key, long durationMs) { return page.action("input.key-hold", Map.of("key", key, "duration", durationMs)); }
        public PlaywrightTask<Map<String, Object>> combo(List<String> keys) { return page.action("input.key-combo", Map.of("keys", keys)); }
        public PlaywrightTask<Map<String, Object>> down(String key) { return page.action("input.key-down", Map.of("key", key)); }
        public PlaywrightTask<Map<String, Object>> up(String key) { return page.action("input.key-up", Map.of("key", key)); }
        public PlaywrightTask<Map<String, Object>> keysDown() { return page.query("input.keys-down", Map.of()); }
        public PlaywrightTask<Map<String, Object>> mousePosition() { return page.query("input.mouse-pos", Map.of()); }
    }

    public static final class MoveApi {
        private final PageApi page;
        private MoveApi(PageApi page) { this.page = page; }
        public PlaywrightTask<Map<String, Object>> direction(String direction, int blocks) { return page.action("move.direction", Map.of("direction", direction, "blocks", blocks)); }
        public PlaywrightTask<Map<String, Object>> to(Map<String, Object> position) { return to(position, 30000L); }
        public PlaywrightTask<Map<String, Object>> to(Map<String, Object> position, long timeoutMs) {
            Map<String, Object> params = new LinkedHashMap<>(position);
            params.put("timeout", timeoutMs / 1000.0);
            return page.action("move.to", params);
        }
        public PlaywrightTask<Map<String, Object>> jump() { return page.action("move.jump", Map.of()); }
        public PlaywrightTask<Map<String, Object>> sneak(boolean enabled) { return page.action("move.sneak", Map.of("enabled", enabled)); }
        public PlaywrightTask<Map<String, Object>> sprint(boolean enabled) { return page.action("move.sprint", Map.of("enabled", enabled)); }
    }

    public static final class LookApi {
        private final PageApi page;
        private LookApi(PageApi page) { this.page = page; }
        public PlaywrightTask<Map<String, Object>> set(double yaw, double pitch) { return page.action("look.set", Map.of("yaw", yaw, "pitch", pitch)); }
        public PlaywrightTask<Map<String, Object>> at(Map<String, Object> pos) { return page.action("look.at", pos); }
        public PlaywrightTask<Map<String, Object>> entity(Map<String, Object> filter) { return page.action("look.entity", Map.of("filter", filter)); }
        public PlaywrightTask<Map<String, Object>> current() { return page.query("rotation.get", Map.of()); }
    }

    public static final class StatusApi {
        private final PageApi page;
        private StatusApi(PageApi page) { this.page = page; }
        public PlaywrightTask<Map<String, Object>> position() { return page.query("position.get", Map.of()); }
        public PlaywrightTask<Map<String, Object>> rotation() { return page.query("rotation.get", Map.of()); }
        public PlaywrightTask<Map<String, Object>> health() { return page.query("status.health", Map.of()); }
        public PlaywrightTask<Map<String, Object>> world() { return page.query("status.world", Map.of()); }
        public PlaywrightTask<Map<String, Object>> all() { return page.query("status.all", Map.of()); }
        public PlaywrightTask<Map<String, Object>> ready(long timeoutMs) { return ClientWaits.ready(Math.max(1L, Math.round(timeoutMs / 50.0))); }
    }

    public static final class ScreenshotApi {
        private final PageApi page;
        private ScreenshotApi(PageApi page) { this.page = page; }
        public PlaywrightTask<Map<String, Object>> capture(String output) { return page.action("capture.screenshot", Map.of("output", output)); }
    }

    public static final class WaitApi {
        private final PageApi page;
        private WaitApi(PageApi page) { this.page = page; }
        public PlaywrightTask<Map<String, Object>> ticks(long ticks) { return ClientWaits.ticks(ticks); }
        public PlaywrightTask<Map<String, Object>> timeout(long timeoutMs) { return page.waitForTimeout(timeoutMs); }
        public PlaywrightTask<Map<String, Object>> event(String name, long timeoutMs) { return ClientWaits.event(name, Math.max(1L, Math.round(timeoutMs / 50.0))); }
        public PlaywrightTask<Map<String, Object>> chat(String match, long timeoutMs) { return page.waitForChat(match, timeoutMs); }
        public PlaywrightTask<Map<String, Object>> guiOpen(long timeoutMs) { return page.waitForGui(timeoutMs); }
    }
}
