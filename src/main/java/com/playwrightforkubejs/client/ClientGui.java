package com.playwrightforkubejs.client;

import com.playwrightforkubejs.protocol.ErrorCode;
import com.playwrightforkubejs.protocol.PlaywrightException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.TabButton;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Main-thread, freshly resolved GUI controls. Input is routed through the current Screen. */
public final class ClientGui {
    private ClientGui() {
    }

    public static Map<String, Object> snapshot() {
        Tree tree = tree();
        Map<String, Object> result = screenInfo(tree.screen);
        result.put("widgets", tree.nodes.stream().map(Node::data).toList());
        return result;
    }

    /** Zero matches is a valid query result so a not-yet-open screen can be polled. */
    public static Map<String, Object> lookup(String selector) {
        Tree tree = tree();
        List<Map<String, Object>> matches = GuiSelector.select(selector, tree.nodes.stream().map(Node::data).toList());
        Map<String, Object> result = screenInfo(tree.screen);
        result.put("selector", selector);
        result.put("count", matches.size());
        result.put("matches", matches);
        return result;
    }

    public static Map<String, Object> click(String selector) {
        Tree tree = tree();
        Node target = resolve(tree, selector, false);
        if (!Boolean.TRUE.equals(target.data.get("enabled"))) {
            throw invalid("GUI locator is disabled: " + selector);
        }
        boolean scrolled = scrollWorldIntoView(tree, target);
        if (scrolled) {
            tree = tree();
            target = resolve(tree, selector, true);
        } else {
            GuiSelector.unique(selector, tree.nodes.stream().map(Node::data).toList(), true);
        }
        return clickTarget(tree, target, selector, scrolled);
    }

    /** Focus via Screen input, replace the selected range via EditBox's editing/responder path. */
    public static Map<String, Object> fill(String selector, String text) {
        if (text == null) {
            throw new PlaywrightException(ErrorCode.INVALID_PARAMS, "GUI fill text must not be null");
        }
        Tree tree = tree();
        Node target = resolve(tree, selector, true);
        if (!(target.listener instanceof EditBox editBox)) {
            throw invalid("GUI fill requires an EditBox: " + selector);
        }
        Map<String, Object> click = clickTarget(tree, target, selector, false);
        requireCurrent(tree.screen);
        if (!editBox.isFocused() || !editBox.canConsumeInput()) {
            throw invalid("GUI textbox did not acquire editable focus: " + selector);
        }
        String before = editBox.getValue();
        editBox.moveCursorToEnd(false);
        editBox.setHighlightPos(0);
        editBox.insertText(text);
        requireCurrent(tree.screen);
        if (!text.equals(editBox.getValue())) {
            throw invalid("GUI fill rejected, filtered or truncated input: " + selector
                + " (requested=" + text + ", actual=" + editBox.getValue() + ")");
        }
        Map<String, Object> result = new LinkedHashMap<>(click);
        result.put("before", before);
        result.put("value", editBox.getValue());
        result.put("focused", editBox.isFocused());
        result.put("inputPath", "Screen.mouseClicked/mouseReleased + EditBox.selectAll/insertText");
        return result;
    }

    public static boolean screenMatches(String expected) {
        Screen screen = Minecraft.getInstance().screen;
        return screen != null && (screen.getClass().getSimpleName().equals(expected) || screen.getClass().getName().equals(expected));
    }

    private static Map<String, Object> clickTarget(Tree tree, Node target, String selector, boolean scrolled) {
        requireCurrent(tree.screen);
        ScreenRectangle rectangle = target.rectangle;
        double x = rectangle.left() + rectangle.width() / 2.0;
        double y = rectangle.top() + rectangle.height() / 2.0;
        if (target.worldList != null) {
            // Click the row text area, not the join-icon region, using the measured row geometry.
            x = target.worldList.getRowRight() - rectangle.width() / 4.0;
            y = (Math.max(rectangle.top(), target.worldList.getY())
                + Math.min(rectangle.bottom(), target.worldList.getBottom())) / 2.0;
        }
        if (!target.listener.isMouseOver(x, y)) {
            // World entries use list-owned hit testing, rather than an entry rectangle.
            if (target.worldList == null || !target.worldList.isMouseOver(x, y)) {
                throw invalid("GUI locator has no clickable point in its bounds: " + selector);
            }
        }
        Map<String, Object> input = ClientInput.clickAt(tree.screen, x, y, 0);
        if (!Boolean.TRUE.equals(input.get("pressedHandled"))) {
            throw invalid("Screen did not handle GUI locator click: " + selector);
        }
        Map<String, Object> result = new LinkedHashMap<>(input);
        result.put("selector", selector);
        result.put("widget", target.data);
        result.put("scrolled", scrolled);
        result.put("inputPath", "Screen.mouseClicked/mouseReleased");
        return result;
    }

    private static boolean scrollWorldIntoView(Tree tree, Node node) {
        if (node.worldList == null) {
            return false;
        }
        requireCurrent(tree.screen);
        WorldSelectionList list = node.worldList;
        if (node.rectangle.top() >= list.getY() && node.rectangle.bottom() <= list.getBottom()) {
            return false;
        }
        // Public list scrolling only changes its viewport; selection/loading still use Screen input.
        double center = node.rectangle.top() + node.rectangle.height() / 2.0;
        list.setScrollAmount(list.getScrollAmount() + center - (list.getY() + list.getBottom()) / 2.0);
        return true;
    }

    private static Node resolve(Tree tree, String selector, boolean actionable) {
        if (tree.screen == null) {
            throw new PlaywrightException(ErrorCode.GUI_NOT_OPEN, "No GUI is open for locator: " + selector);
        }
        Map<String, Object> unique = GuiSelector.unique(selector, tree.nodes.stream().map(Node::data).toList(), actionable);
        return tree.nodes.stream().filter(node -> node.data == unique).findFirst().orElseThrow();
    }

    private static Tree tree() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            throw new PlaywrightException(ErrorCode.INTERNAL_ERROR, "GUI operations must run on the Minecraft client thread");
        }
        Screen screen = minecraft.screen;
        List<Node> nodes = new ArrayList<>();
        if (screen != null) {
            Set<GuiEventListener> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            visit(screen.children(), "root", screen, null, true, true, seen, nodes);
        }
        return new Tree(screen, nodes);
    }

    private static void visit(List<? extends GuiEventListener> children, String parentId, Screen screen,
                              WorldSelectionList worldList, boolean parentVisible, boolean parentEnabled,
                              Set<GuiEventListener> seen, List<Node> nodes) {
        for (int index = 0; index < children.size(); index++) {
            GuiEventListener listener = children.get(index);
            if (!seen.add(listener)) {
                continue;
            }
            String id = parentId + "/" + index;
            ScreenRectangle rectangle = listener.getRectangle();
            Component message = null;
            String role = listener instanceof ContainerEventHandler ? "group" : "widget";
            boolean visible = parentVisible;
            boolean enabled = parentEnabled;
            String value = "";
            if (listener instanceof AbstractWidget widget) {
                message = widget.getMessage();
                visible &= widget.visible;
                enabled &= widget.active;
                role = widget instanceof TabButton ? "tab" : widget instanceof EditBox ? "textbox"
                    : widget instanceof AbstractButton ? "button" : "widget";
                if (widget instanceof EditBox box) {
                    value = box.getValue();
                    enabled &= box.isEditable();
                }
            }
            Map<String, Object> data = new LinkedHashMap<>();
            if (worldList != null && listener instanceof WorldSelectionList.WorldListEntry entry) {
                int top = worldList.getRowTop(index);
                int bottom = worldList.getRowBottom(index);
                rectangle = new ScreenRectangle(worldList.getRowLeft(), top, worldList.getRowWidth(), bottom - top);
                role = "world";
                message = Component.literal(entry.summary.getLevelName());
                enabled &= entry.canJoin();
                visible &= GuiSelector.intersects(rectangle.left(), rectangle.top(), rectangle.width(), rectangle.height(),
                    worldList.getX(), worldList.getY(), worldList.getRight(), worldList.getBottom());
                data.put("worldId", entry.summary.getLevelId());
                data.put("worldName", entry.summary.getLevelName());
                data.put("selected", worldList.getSelected() == entry);
                data.put("rowIndex", index);
            }
            if (listener instanceof WorldSelectionList) {
                role = "list";
            }
            // Container handlers sometimes report empty bounds; their children still have valid geometry.
            if (rectangle.width() > 0 && rectangle.height() > 0) {
                visible &= GuiSelector.intersects(rectangle.left(), rectangle.top(), rectangle.width(), rectangle.height(),
                    0, 0, screen.width, screen.height);
            } else if (!(listener instanceof ContainerEventHandler)) {
                visible = false;
            }
            List<String> keys = translationKeys(message);
            data.put("id", id);
            data.put("parentId", parentId);
            data.put("type", listener.getClass().getName());
            data.put("role", role);
            data.put("text", message == null ? "" : message.getString());
            data.put("translationKey", keys.isEmpty() ? "" : keys.get(0));
            data.put("translationKeys", keys);
            data.put("bounds", GuiSelector.bounds(rectangle.left(), rectangle.top(), rectangle.width(), rectangle.height()));
            data.put("visible", visible);
            data.put("enabled", enabled);
            data.put("active", listener instanceof AbstractWidget widget ? widget.active : enabled);
            if (listener instanceof EditBox box) {
                data.put("editable", box.isEditable());
            }
            data.put("focused", listener.isFocused());
            data.put("focus", listener.isFocused());
            data.put("value", value);
            data.put("screenId", screenId(screen));
            nodes.add(new Node(listener, rectangle, worldList, data));
            if (listener instanceof ContainerEventHandler container) {
                visit(container.children(), id, screen, listener instanceof WorldSelectionList list ? list : null,
                    visible, enabled, seen, nodes);
            }
        }
    }

    private static List<String> translationKeys(Component component) {
        Set<String> keys = new LinkedHashSet<>();
        collectKeys(component, keys, Collections.newSetFromMap(new IdentityHashMap<>()));
        return List.copyOf(keys);
    }

    private static void collectKeys(Component component, Set<String> keys, Set<Component> seen) {
        if (component == null || !seen.add(component)) {
            return;
        }
        if (component.getContents() instanceof TranslatableContents contents) {
            keys.add(contents.getKey());
            for (Object argument : contents.getArgs()) {
                if (argument instanceof Component child) {
                    collectKeys(child, keys, seen);
                }
            }
        }
        for (Component sibling : component.getSiblings()) {
            collectKeys(sibling, keys, seen);
        }
    }

    private static Map<String, Object> screenInfo(Screen screen) {
        Minecraft minecraft = Minecraft.getInstance();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("open", screen != null);
        result.put("type", screen == null ? "" : screen.getClass().getSimpleName());
        result.put("className", screen == null ? "" : screen.getClass().getName());
        result.put("screenId", screen == null ? "" : screenId(screen));
        result.put("title", screen == null ? "" : screen.getTitle().getString());
        result.put("width", minecraft.getWindow().getGuiScaledWidth());
        result.put("height", minecraft.getWindow().getGuiScaledHeight());
        result.put("guiScale", minecraft.getWindow().getGuiScale());
        result.put("coordinateSpace", "gui-scaled");
        return result;
    }

    private static String screenId(Screen screen) {
        return screen.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(screen));
    }

    private static void requireCurrent(Screen expected) {
        if (expected == null || Minecraft.getInstance().screen != expected) {
            throw invalid("GUI locator's screen changed before input was dispatched");
        }
    }

    private static PlaywrightException invalid(String message) {
        return new PlaywrightException(ErrorCode.INVALID_ACTION, message);
    }

    private record Node(GuiEventListener listener, ScreenRectangle rectangle, WorldSelectionList worldList,
                        Map<String, Object> data) {
    }

    private record Tree(Screen screen, List<Node> nodes) {
    }
}
