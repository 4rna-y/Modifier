package io.github.modifier.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Modifier と wiah を同じサーバーに載せたときの、死亡とワールドリセットの噛み合わせ。
 *
 * <p>この2つは別のイベントで動く。Modifier は致死ダメージを {@code EntityDamageEvent} で
 * 打ち消し、wiah は {@code PlayerDeathEvent} でリセットを起動する。打ち消された死では
 * 死亡イベントがそもそも生まれないので、リセットも走らないはず — という繋がりを、
 * 実際に両方載せて確かめる。
 *
 * <p>冷笑は一度だけ死を打ち消す。同じプレイヤーが2回死ねば、前半と後半で結果が変わる。
 * 1回のシナリオで両方を観測できるので、サーバーは1つで足りる。
 *
 * <p><b>このテストはサーバーを落として終わる。</b>リセットは全員キック → 停止まで
 * 行くので、{@link SelectionE2eTest} とはサーバーを分けてある (向こうの検証中に
 * 誰かが死ぬと、そこでサーバーが消えてしまうため)。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
@DisplayName("死亡によるワールドリセットとの噛み合わせ")
class WorldResetE2eTest {

    private static final Duration BOOT = Duration.ofMinutes(5);
    private static final Duration BOT = Duration.ofMinutes(3);

    /** 死亡から全員キックまでの猶予。既定の10秒はテストには長いので縮める。 */
    private static final long RESET_DELAY_SECONDS = 3L;

    /** wiah がリセットを始めたときに出すログ。 */
    private static final String RESET_STARTED = "によりワールドリセットを開始します";

    private static final String VICTIM = "E2eDoomed";

    private TestServerDir server;
    private ProcessConsole console;
    private Path marker;
    private BotRunner.Result result;

    /** 1回目の死の直後に、リセットが始まっていたか。 */
    private final AtomicBoolean resetAfterFirstDeath = new AtomicBoolean();
    /** 1回目の死の直後に、リセットの予約ファイルが在ったか。 */
    private final AtomicBoolean markerAfterFirstDeath = new AtomicBoolean();

    @BeforeAll
    void bootAndRun() throws Exception {
        Path buildDir = Path.of(System.getProperty("modifier.buildDir", "build")).toAbsolutePath();
        Path botDir = Path.of(System.getProperty("modifier.botDir", "bot")).toAbsolutePath();
        Path pluginJar = Path.of(System.getProperty("modifier.pluginJar", ""));
        Path wiahJar = Path.of(System.getProperty("modifier.wiahJar", ""));

        assumeTrue(Files.isRegularFile(pluginJar), "プラグインの jar が無い: " + pluginJar);
        assumeTrue(BotRunner.available(botDir),
                "ヘッドレスクライアントが未導入。" + botDir + " で npm install すること");
        // wiah は別リポジトリなので、無ければこのクラスだけ飛ばす。
        assumeTrue(Files.isRegularFile(wiahJar),
                "wiah の jar が無いのでリセットの検証を飛ばす。"
                        + "../world_is_also_hardcore で gradle :plugin:jar すること: " + wiahJar);

        Path paperJar = PaperJar.resolve(buildDir.resolve("paper"));
        server = TestServerDir.create(buildDir.resolve("server-reset"), paperJar,
                TestServerDir.RESET_SERVER_PORT, TestServerDir.RESET_PACK_PORT,
                pluginJar, wiahJar);
        server.writeWiahConfig(RESET_DELAY_SECONDS);
        marker = server.resolve(TestServerDir.RESET_MARKER);

        console = ProcessConsole.start(server.root(),
                buildDir.resolve("server-reset-console.log"),
                List.of("java", "-Xms1G", "-Xmx2G", "-jar",
                        paperJar.getFileName().toString(), "nogui"));
        console.await("Done (", BOOT);

        // 3択は19種からの抽選なので、狙ったものが出るまで引き直させる。要 OP。
        console.send("op " + VICTIM);
        // 蘇生後の HP を見るので、自然回復で埋められると測れない。
        // 満腹度が満タンだと数秒で数 HP 戻ってしまう。
        console.send("gamerule natural_health_regeneration false");
        Thread.sleep(1000);

        result = BotRunner.run(botDir, "death_reset",
                TestServerDir.RESET_SERVER_PORT, BOT, this::onObservation);

        assumeTrue(result.first("choice_missing").isEmpty(),
                "冷笑を引き当てられなかった。抽選なので稀に起きる" + result.describe());
    }

    /**
     * ボットの観測に合わせて、コンソールから手を出す。
     *
     * <p>「1回目の死の時点でリセットが走っていないこと」は後から遡れないので、
     * その瞬間にここで控えておく。
     */
    private void onObservation(BotRunner.Observation observation) {
        try {
            switch (observation.event()) {
                case "ready_for_first_death", "ready_for_second_death" ->
                        console.send("damage " + VICTIM + " 100");
                case "after_first_death" -> {
                    resetAfterFirstDeath.set(console.sawLine(RESET_STARTED));
                    markerAfterFirstDeath.set(Files.exists(marker));
                }
                default -> {
                    // 見るだけの観測
                }
            }
        } catch (IOException e) {
            throw new AssertionError("コンソールへ送れない", e);
        }
    }

    @AfterAll
    void stopServer() throws Exception {
        if (console != null) {
            // リセットまで行っていれば既に落ちている。stopAndWait はそれを見て何もしない。
            console.stopAndWait(Duration.ofMinutes(2));
            console.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("ボットは冷笑を引き当てて2回死んだ")
    void scenarioRan() {
        assertEquals(0, result.exitCode(), "ボットが失敗した" + result.describe());
        assertTrue(result.first("after_first_death").isPresent(),
                "1回目の死を観測できていない" + result.describe());
        assertTrue(result.first("after_second_death").isPresent(),
                "2回目の死を観測できていない" + result.describe());
    }

    @Test
    @Order(2)
    @DisplayName("冷笑が打ち消した死ではワールドリセットが走らない")
    void interceptedDeathDoesNotReset() {
        var first = result.first("after_first_death").orElseThrow();

        assertTrue(first.has("died", "false"),
                "冷笑が死を打ち消せていない" + result.describe());
        assertTrue(first.field("message").orElse("").contains("死ぬわけがない"),
                "冷笑の発動メッセージが出ていない" + result.describe());

        // ここがこのクラスの本題。打ち消した死では PlayerDeathEvent が生まれないので、
        // それを待っている wiah 側は動かないはず。
        assertFalse(resetAfterFirstDeath.get(),
                "死を打ち消したのにワールドリセットが走ってしまった。"
                        + "PlayerDeathEvent が飛んでいる可能性がある" + console.tail());
        assertFalse(markerAfterFirstDeath.get(),
                "死を打ち消したのにリセットの予約ファイルが書かれた" + console.tail());
    }

    @Test
    @Order(3)
    @DisplayName("打ち消された側は HP が最大値の半分で立ち上がる")
    void interceptedPlayerRevivesAtHalf() {
        var first = result.first("after_first_death").orElseThrow();
        // HP が一度も動かなければ null で来る。数値として読む前に弾いておく。
        String health = first.field("health").orElse("null");
        assertFalse("null".equals(health),
                "1回目のダメージで HP が動かなかった" + result.describe());
        assertEquals(10.0, Double.parseDouble(health), 0.51,
                "蘇生後の HP が最大値の半分になっていない。"
                        + "自然回復が効いていると測れないので gamerule を確認すること"
                        + result.describe());
    }

    @Test
    @Order(4)
    @DisplayName("チャージを使い切った2回目の死ではワールドリセットが走る")
    void realDeathTriggersReset() throws Exception {
        console.await(VICTIM + " の死亡 " + RESET_STARTED, Duration.ofSeconds(30));

        assertTrue(Files.isRegularFile(marker),
                "リセットの予約ファイルが書かれていない: " + marker + console.tail());
        assertTrue(Files.readString(marker).contains("world"),
                "予約ファイルに削除対象のワールドが載っていない: " + Files.readString(marker));
    }

    @Test
    @Order(5)
    @DisplayName("リセットは全員をキックしてサーバーを停止するところまで進む")
    void resetKicksAndStops() throws Exception {
        console.await("参加中の全プレイヤーをキックしました", Duration.ofSeconds(30));
        console.await("サーバーを停止します", Duration.ofSeconds(30));

        var second = result.first("after_second_death").orElseThrow();
        assertTrue(second.has("kicked", "true"),
                "ボットがキックされていない" + result.describe());
    }

    @Test
    @Order(6)
    @DisplayName("2つのプラグインを同時に載せてもサーバー側に例外が出ない")
    void noServerExceptions() {
        assertFalse(console.sawLine("Caused by:"),
                "サーバーで例外が起きている" + console.tail());
    }

    @Test
    @Order(7)
    @DisplayName("下準備のコンソールコマンドが黙って失敗していない")
    void setupCommandsSucceeded() {
        // 26.x で gamerule 名が snake_case へ変わっており、旧名を送っても
        // コンソールに出るだけで誰も気付かなかった。同じ取りこぼしを繰り返さないための番人。
        assertFalse(console.sawLine("Incorrect argument for command"),
                "下準備のコマンドが引数エラーで通っていない" + console.tail());
        assertFalse(console.sawLine("Unknown or incomplete command"),
                "下準備のコマンドが認識されていない" + console.tail());
    }
}
