package com.playwrightforkubejs;

import com.playwrightforkubejs.client.GuiSelector;
import com.playwrightforkubejs.protocol.ErrorCode;
import com.playwrightforkubejs.protocol.PlaywrightException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

final class GuiSelectorTest {
    private static Map<String, Object> node(String id, String role, String text, boolean visible, boolean enabled) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("role", role);
        node.put("text", text);
        node.put("visible", visible);
        node.put("enabled", enabled);
        node.put("bounds", GuiSelector.bounds(40, 25, 100, 20));
        return node;
    }

    @Test
    void legacyChatAndOtherLocatorsRemainOutsideGuiGrammar() {
        for (String selector : List.of("text=Singleplayer", "slot=0", "item=minecraft:stone", "entity=minecraft:pig", "screen=TitleScreen")) {
            assertFalse(GuiSelector.isGui(selector));
            assertThrows(PlaywrightException.class, () -> GuiSelector.parse(selector));
        }
    }

    @Test
    void guiTextUsesExactMatchingNotChatSubstringMatching() {
        Map<String, Object> button = node("root/0", "button", "Create New World", true, true);
        assertEquals(List.of(button), GuiSelector.select("gui-text=Create New World", List.of(button)));
        assertTrue(GuiSelector.select("gui-text=Create", List.of(button)).isEmpty());
    }

    @Test
    void translationSelectorIsIndependentOfRenderedLanguageAndFindsCompositeKeys() {
        Map<String, Object> button = node("root/0", "button", "translated display", true, true);
        button.put("translationKey", "options.generic_value");
        button.put("translationKeys", List.of("options.generic_value", "selectWorld.gameMode"));
        assertEquals(List.of(button), GuiSelector.select("translation=selectWorld.gameMode", List.of(button)));
        button.put("text", "different display language");
        assertEquals(List.of(button), GuiSelector.select("translation=selectWorld.gameMode", List.of(button)));
    }

    @Test
    void roleNameEncodingRoundTripsSpacesUnicodeAndDelimiters() {
        String name = "世界 |name=x + %=value";
        Map<String, Object> tab = node("root/0/1", "tab", name, true, true);
        String selector = GuiSelector.role("tab", name);
        assertEquals(List.of(tab), GuiSelector.select(selector, List.of(tab)));
        assertTrue(GuiSelector.select(GuiSelector.role("button", name), List.of(tab)).isEmpty());
        assertEquals(List.of(tab), GuiSelector.select("role=tab", List.of(tab)));
    }

    @Test
    void malformedRoleIsRejectedInsteadOfSilentlyMatchingNothing() {
        assertThrows(PlaywrightException.class, () -> GuiSelector.parse("role=unsupported"));
        assertThrows(PlaywrightException.class, () -> GuiSelector.parse("role=tab|name=%broken"));
        assertThrows(PlaywrightException.class, () -> GuiSelector.parse("world="));
    }

    @Test
    void missingAndAmbiguousControlsHaveExplicitErrors() {
        Map<String, Object> first = node("root/0", "button", "Done", true, true);
        Map<String, Object> second = node("root/1", "button", "Done", true, true);
        PlaywrightException missing = assertThrows(PlaywrightException.class,
            () -> GuiSelector.unique("gui-text=Missing", List.of(first), true));
        assertEquals(ErrorCode.INVALID_ACTION, missing.getCode());
        assertTrue(missing.getMessage().contains("matched 0"));
        PlaywrightException duplicate = assertThrows(PlaywrightException.class,
            () -> GuiSelector.unique("gui-text=Done", List.of(first, second), true));
        assertTrue(duplicate.getMessage().contains("matched 2"));
        assertSame(second, GuiSelector.unique("widget=root/1", List.of(first, second), true));
    }

    @Test
    void disabledAndHiddenAreQueryableButNotActionable() {
        Map<String, Object> disabled = node("root/0", "button", "Done", true, false);
        Map<String, Object> hidden = node("root/1", "button", "Cancel", false, true);
        assertSame(disabled, GuiSelector.unique("widget=root/0", List.of(disabled), false));
        assertThrows(PlaywrightException.class, () -> GuiSelector.unique("widget=root/0", List.of(disabled), true));
        assertThrows(PlaywrightException.class, () -> GuiSelector.unique("widget=root/1", List.of(hidden), true));
    }

    @Test
    void worldSelectorAcceptsExactSaveIdOrUniqueDisplayName() {
        Map<String, Object> world = node("root/2/0", "world", "run-unique", true, true);
        world.put("worldId", "run-unique-folder");
        world.put("worldName", "run-unique");
        assertSame(world, GuiSelector.unique("world=run-unique-folder", List.of(world), true));
        assertSame(world, GuiSelector.unique("world=run-unique", List.of(world), true));
        assertTrue(GuiSelector.select("world=unique", List.of(world)).isEmpty());
    }

    @Test
    void duplicateWorldNamesRequireAnUnambiguousFolderId() {
        Map<String, Object> first = node("root/2/0", "world", "same", true, true);
        first.put("worldId", "save-a");
        first.put("worldName", "same");
        Map<String, Object> second = node("root/2/1", "world", "same", true, true);
        second.put("worldId", "save-b");
        second.put("worldName", "same");
        assertThrows(PlaywrightException.class, () -> GuiSelector.unique("world=same", List.of(first, second), true));
        assertSame(second, GuiSelector.unique("world=save-b", List.of(first, second), true));
    }

    @Test
    void guiCoordinatesAreUsedWithoutPhysicalPixelConversion() {
        Map<String, Object> button = node("root/0", "button", "Done", true, true);
        for (int scale : List.of(1, 2, 3, 4)) {
            int physicalX = 40 * scale;
            Map<String, Object> bounds = GuiSelector.bounds(physicalX / scale, 25, 100, 20);
            button.put("bounds", bounds);
            assertEquals(40, ((Map<?, ?>) GuiSelector.unique("widget=root/0", List.of(button), true).get("bounds")).get("x"));
            assertTrue(GuiSelector.intersects(40, 25, 100, 20, 0, 0, 320, 180));
        }
        assertFalse(GuiSelector.intersects(40, 200, 100, 20, 0, 0, 320, 180));
        assertFalse(GuiSelector.intersects(40, 25, 0, 20, 0, 0, 320, 180));
    }
}
