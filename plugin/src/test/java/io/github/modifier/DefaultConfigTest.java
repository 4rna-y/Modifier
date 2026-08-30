package io.github.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 同梱する config.yml の既定値を固定する。 */
class DefaultConfigTest {

    private final YamlConfiguration config = loadBundledConfig();

    @Test
    @DisplayName("既定で有効")
    void enabledByDefault() {
        assertTrue(config.getBoolean("enabled"));
    }

    @Test
    @DisplayName("接頭辞は同梱 config.yml とコード側の既定値で一致する")
    void prefixMatchesCodeDefault() {
        String raw = config.getString("message-prefix");
        assertNotNull(raw, "message-prefix が config.yml に無い");
        assertEquals(ModifierPlugin.DEFAULT_MESSAGE_PREFIX, raw,
                "config.yml を消して起動したときに見た目が変わってしまう");
    }

    @Test
    @DisplayName("接頭辞は MiniMessage として解釈できる")
    void prefixIsValidMiniMessage() {
        String rendered = PlainTextComponentSerializer.plainText().serialize(
                MiniMessage.miniMessage().deserialize(config.getString("message-prefix", "")));
        assertEquals("[Modifier] ", rendered);
    }

    @Test
    @DisplayName("選択肢の数は3")
    void offersThreeChoices() {
        assertEquals(3, config.getInt("selection.choice-count"));
        assertEquals(SelectionService.DEFAULT_CHOICE_COUNT,
                config.getInt("selection.choice-count"),
                "同梱の config.yml とコード側の既定値がずれている");
    }

    @Test
    @DisplayName("選択画面のタイトルもコード側の既定値と一致する")
    void selectionTitleMatchesCodeDefault() {
        assertEquals(SelectionService.DEFAULT_TITLE, config.getString("selection.title"));
    }

    @Test
    @DisplayName("リソースパックは既定で配る")
    void resourcePackIsSentByDefault() {
        assertTrue(config.getBoolean("resource-pack.enabled"));
        assertTrue(config.getBoolean("resource-pack.required"),
                "拒否されるとアイコンが出ないので、既定では必須にしておく");
    }

    @Test
    @DisplayName("パックの確認文言もコード側の既定値と一致する")
    void resourcePackPromptMatchesCodeDefault() {
        assertEquals(ResourcePackService.DEFAULT_PROMPT, config.getString("resource-pack.prompt"));
    }

    @Test
    @DisplayName("未選択のまま閉じられたら開き直す")
    void selectionIsForcedByDefault() {
        assertTrue(config.getBoolean("selection.force"),
                "閉じたままにできると効果無しで遊べてしまう");
    }

    private static YamlConfiguration loadBundledConfig() {
        try (InputStream in = DefaultConfigTest.class.getResourceAsStream("/config.yml")) {
            assertNotNull(in, "config.yml がテストのクラスパスに無い");
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AssertionError("config.yml を読めません", e);
        }
    }
}
