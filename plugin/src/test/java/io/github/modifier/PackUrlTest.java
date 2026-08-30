package io.github.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * クライアントへ知らせるパックの URL。
 *
 * <p>ここを間違えるとアイコンが出ないだけで、サーバー側には何も出ない。
 * 気づきにくいので組み立てだけ切り出して見る。
 */
@DisplayName("リソースパックの URL")
class PackUrlTest {

    @Test
    @DisplayName("指定が無ければ待ち受け先から http:// で組み立てる")
    void fallsBackToHostAndPort() {
        assertEquals("http://127.0.0.1:8123",
                ResourcePackHost.publicUrlPrefix("", "127.0.0.1", 8123));
        assertEquals("http://miyako.example.net:8123",
                ResourcePackHost.publicUrlPrefix(null, "miyako.example.net", 8123));
    }

    @Test
    @DisplayName("公開 URL を指定したらホストとポートより優先する")
    void publicUrlWins() {
        assertEquals("https://resourcepack.example.net",
                ResourcePackHost.publicUrlPrefix(
                        "https://resourcepack.example.net", "127.0.0.1", 8123));
    }

    @Test
    @DisplayName("末尾のスラッシュは落とす")
    void trimsTrailingSlash() {
        // パスの先頭にも / が付くので、そのままだと //modifier-....zip になってしまう
        assertEquals("https://resourcepack.example.net",
                ResourcePackHost.publicUrlPrefix(
                        "https://resourcepack.example.net/", "127.0.0.1", 8123));
        assertEquals("https://resourcepack.example.net/pack",
                ResourcePackHost.publicUrlPrefix(
                        "https://resourcepack.example.net/pack///", "127.0.0.1", 8123));
    }

    @Test
    @DisplayName("前後の空白は無視する")
    void ignoresSurroundingSpace() {
        assertEquals("https://resourcepack.example.net",
                ResourcePackHost.publicUrlPrefix(
                        "  https://resourcepack.example.net  ", "127.0.0.1", 8123));
        // 空白だけなら指定していないのと同じ
        assertEquals("http://127.0.0.1:8123",
                ResourcePackHost.publicUrlPrefix("   ", "127.0.0.1", 8123));
    }
}
