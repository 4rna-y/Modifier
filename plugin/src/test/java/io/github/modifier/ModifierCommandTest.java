package io.github.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code /m} を引数なしで叩いたときに本人へ返る文面。
 *
 * <p>送信そのもの (Bukkit) ではなく、組み立てた行だけを見る。
 */
class ModifierCommandTest {

    private final ModifierRegistry registry = ModifierRegistry.withBuiltins(
            mock(Plugin.class), new SelectionStore(mock(Server.class)), new Random(0));

    @Test
    @DisplayName("選択中のモディファイアは、名前に続けて効果の説明を並べる")
    void showsNameThenDescription() {
        Modifier fat = registry.byId("fat").orElseThrow();

        List<Component> lines = ModifierCommand.describeSelection(Optional.of("fat"), registry);

        assertEquals(1 + fat.description().size(), lines.size(),
                "説明の行が欠けている、あるいは増えている");
        assertEquals("選択中: " + plain(fat.displayName()), plain(lines.get(0)));
        for (int i = 0; i < fat.description().size(); i++) {
            assertTrue(plain(lines.get(i + 1)).contains(plain(fat.description().get(i))),
                    "説明の " + (i + 1) + " 行目が入っていない: " + plain(lines.get(i + 1)));
        }
    }

    @Test
    @DisplayName("まだ選んでいない人には、その旨だけを返す")
    void tellsWhenNothingIsSelected() {
        List<Component> lines = ModifierCommand.describeSelection(Optional.empty(), registry);

        assertEquals(1, lines.size());
        assertEquals("モディファイアを選んでいません。", plain(lines.get(0)));
    }

    @Test
    @DisplayName("登録から消えた id を持っている人には、その id を添えて知らせる")
    void tellsWhenTheSelectionIsGone() {
        List<Component> lines = ModifierCommand.describeSelection(Optional.of("ghost"), registry);

        assertEquals(1, lines.size());
        assertTrue(plain(lines.get(0)).contains("ghost"),
                "どの id が消えたのか分からない: " + plain(lines.get(0)));
    }

    @Test
    @DisplayName("どのモディファイアを選んでいても説明が出る")
    void everyBuiltinHasSomethingToShow() {
        for (Modifier modifier : registry.all()) {
            List<Component> lines = ModifierCommand.describeSelection(
                    Optional.of(modifier.id()), registry);

            assertTrue(lines.size() >= 2, modifier.id() + " の説明が1行も無い");
            lines.forEach(line -> assertFalse(plain(line).isBlank(),
                    modifier.id() + " に空行が混じっている"));
        }
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component).strip();
    }
}
