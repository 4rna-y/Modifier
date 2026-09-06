package io.github.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import java.util.Random;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 選んだときにもらえる道具。 */
@DisplayName("受け取る道具")
class StartingItemsTest {

    private final ModifierRegistry registry = ModifierRegistry.withBuiltins(
            mock(Plugin.class), new SelectionStore(mock(Server.class)), new Random(0));

    @Test
    @DisplayName("道具をもらえるのは仕様の 5 種で、中身もそのとおり")
    void kitsMatchTheSpec() {
        Map<String, List<StartingItems.Item>> expected = Map.of(
                "shield_bash", List.of(StartingItems.Item.of(Material.SHIELD)),
                "serf", List.of(StartingItems.Item.of(Material.IRON_HOE)),
                "landmine", List.of(StartingItems.Item.of(Material.BOW), StartingItems.Item.of(Material.ARROW, 16)),
                "diva", List.of(StartingItems.Item.of(Material.NOTE_BLOCK)),
                "fishers", List.of(StartingItems.Item.of(Material.FISHING_ROD)));

        for (Modifier modifier : registry.all()) {
            assertEquals(expected.getOrDefault(modifier.id(), List.of()), modifier.startingItems(),
                    modifier.id() + " の道具が仕様と違う");
        }
    }

    @Test
    @DisplayName("個数 0 の道具は作れない")
    void rejectsEmptyStacks() {
        assertThrows(IllegalArgumentException.class, () -> StartingItems.Item.of(Material.ARROW, 0));
    }

    @Test
    @DisplayName("説明行は道具を名前で並べ、複数個なら個数を添える")
    void describesTheKit() {
        TestRegistryAccess.override(Material.BOW.asItemType(), "translationKey", "item.minecraft.bow");
        TestRegistryAccess.override(Material.ARROW.asItemType(), "translationKey", "item.minecraft.arrow");

        Component line = StartingItems.describe(List.of(
                StartingItems.Item.of(Material.BOW), StartingItems.Item.of(Material.ARROW, 16))).orElseThrow();
        String text = PlainTextComponentSerializer.plainText().serialize(line);

        assertTrue(text.startsWith("受け取る道具: "), text);
        assertTrue(text.contains("item.minecraft.bow"), "弓の名前が翻訳キーで入る: " + text);
        assertTrue(text.contains("item.minecraft.arrow x16"), "矢は個数付き: " + text);
    }

    @Test
    @DisplayName("道具が無ければ説明行も無い")
    void nothingToDescribe() {
        assertTrue(StartingItems.describe(List.of()).isEmpty());
    }
}
