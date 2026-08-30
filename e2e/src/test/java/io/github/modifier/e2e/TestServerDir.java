package io.github.modifier.e2e;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

/**
 * E2E 用に使い捨てのサーバーディレクトリを組み立てる。
 *
 * <p>本番の {@code run/} には触らない。ポートも本番とずらしてあるので、開発サーバーを
 * 起動したままテストを回せる。
 */
final class TestServerDir {

    /** 本番の 25565 とぶつからないポート。 */
    static final int SERVER_PORT = 25566;
    /** 本番のパック配信 (8123) とぶつからないポート。 */
    static final int PACK_PORT = 8124;

    /**
     * リセット検証用のポート。
     *
     * <p>リセットのテストはサーバーを落として終わるので、上の一式とは別のサーバーを立てる。
     * 同時には走らないが、前のサーバーの後片付けが遅れても待たされないようずらしておく。
     */
    static final int RESET_SERVER_PORT = 25567;
    static final int RESET_PACK_PORT = 8125;

    /** wiah がリセット予約を書くファイル (ResetManager.MARKER_FILE)。 */
    static final String RESET_MARKER = "plugins/WorldIsAlsoHardcore/pending-reset.txt";

    private final Path root;

    private TestServerDir(Path root) {
        this.root = root;
    }

    Path root() {
        return root;
    }

    Path resolve(String relative) {
        return root.resolve(relative);
    }

    /**
     * サーバーディレクトリを作る。既にあれば作り直す。
     *
     * @param pluginJars 載せるプラグイン。本番と同じ成果物を渡すこと
     */
    static TestServerDir create(Path root, Path paperJar, Path... pluginJars) throws IOException {
        return create(root, paperJar, SERVER_PORT, PACK_PORT, pluginJars);
    }

    /**
     * ポートを指定してサーバーディレクトリを作る。
     *
     * @param serverPort Minecraft の待ち受けポート
     * @param packPort   プラグインがリソースパックを配るポート
     */
    static TestServerDir create(Path root, Path paperJar, int serverPort, int packPort,
            Path... pluginJars) throws IOException {
        deleteRecursively(root);
        Files.createDirectories(root.resolve("plugins/Modifier"));

        Files.copy(paperJar, root.resolve(paperJar.getFileName()),
                StandardCopyOption.REPLACE_EXISTING);
        for (Path jar : pluginJars) {
            Files.copy(jar, root.resolve("plugins").resolve(jar.getFileName()),
                    StandardCopyOption.REPLACE_EXISTING);
        }

        // ローカル検証専用なので EULA は自動同意する。
        Files.writeString(root.resolve("eula.txt"), "eula=true\n");

        // online-mode=false はヘッドレスクライアントが参加するために要る。
        // 使い捨ての閉じたサーバーなので、ここでだけ許す。
        Files.writeString(root.resolve("server.properties"), """
                server-port=%d
                online-mode=false
                hardcore=true
                level-name=world
                level-seed=e2e
                max-players=20
                view-distance=4
                simulation-distance=4
                spawn-protection=0
                sync-chunk-writes=false
                """.formatted(serverPort));

        // パック配信のポートだけ本番からずらす。他は同梱の既定値のまま。
        Files.writeString(root.resolve("plugins/Modifier/config.yml"), """
                enabled: true
                message-prefix: "<gray>[<aqua>Modifier<gray>]</gray> "
                selection:
                  title: "<dark_gray>モディファイアを選択"
                  choice-count: 3
                  force: true
                resource-pack:
                  enabled: true
                  host: "127.0.0.1"
                  bind: "127.0.0.1"
                  port: %d
                  required: true
                  prompt: "<gold>モディファイアのアイコンを表示するために必要です"
                  apply-timeout-ticks: 200
                effects:
                  tick-period-ticks: 2
                """.formatted(packPort));

        return new TestServerDir(root);
    }

    /**
     * 併せて載せる wiah の設定を書く。
     *
     * <p>猶予は既定の10秒から縮めてテストを短くする。{@code randomize-seed} は切っておく
     * (リセット時に server.properties を書き換えられると、動く部分が増えるだけなので)。
     *
     * @param resetDelaySeconds 死亡から全員キックまでの猶予
     */
    void writeWiahConfig(long resetDelaySeconds) throws IOException {
        Files.createDirectories(root.resolve("plugins/WorldIsAlsoHardcore"));
        Files.writeString(root.resolve("plugins/WorldIsAlsoHardcore/config.yml"), """
                worlds: []
                reset-delay-seconds: %d
                title: "<red>サーバーは<seconds>秒後に削除されます"
                subtitle: "<gray><player> が死亡しました"
                broadcast-message: ""
                kick-message: "<red>ハードコア失敗: <player> が死亡しました"
                shutdown-delay-ticks: 20
                shutdown-mode: shutdown
                randomize-seed: false
                """.formatted(resetDelaySeconds));
    }

    static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(entry -> {
                try {
                    Files.delete(entry);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }
}
