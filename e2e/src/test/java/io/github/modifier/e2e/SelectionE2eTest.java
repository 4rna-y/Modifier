package io.github.modifier.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * ヘッドレスクライアントを使った通し検証。
 *
 * <p>本番と同じプラグイン jar (26.2 でコンパイルしたもの) を、26.1 のサーバーに載せて回す。
 * 26.1 なのはヘッドレスクライアントが 26.2 のプロトコルに未対応なため。使っている API は
 * どちらのバージョンにも存在することを確認済み。
 *
 * <p>1つのサーバーの一生を順番に検証するので、テストは順序付きで状態を共有する。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
@DisplayName("参加からモディファイア確定までの通し検証")
class SelectionE2eTest {

    private static final Duration BOOT = Duration.ofMinutes(5);
    private static final Duration BOT = Duration.ofMinutes(3);

    private TestServerDir server;
    private ProcessConsole console;
    private Path botDir;

    @BeforeAll
    void bootServer() throws Exception {
        Path buildDir = Path.of(System.getProperty("modifier.buildDir", "build")).toAbsolutePath();
        botDir = Path.of(System.getProperty("modifier.botDir", "bot")).toAbsolutePath();
        Path pluginJar = Path.of(System.getProperty("modifier.pluginJar", ""));

        assumeTrue(Files.isRegularFile(pluginJar), "プラグインの jar が無い: " + pluginJar);
        assumeTrue(BotRunner.available(botDir),
                "ヘッドレスクライアントが未導入。" + botDir + " で npm install すること");

        Path paperJar = PaperJar.resolve(buildDir.resolve("paper"));
        server = TestServerDir.create(buildDir.resolve("server"), paperJar, pluginJar);

        console = ProcessConsole.start(server.root(), buildDir.resolve("server-console.log"),
                List.of("java", "-Xms1G", "-Xmx2G", "-jar",
                        paperJar.getFileName().toString(), "nogui"));
        console.await("Done (", BOOT);
    }

    @AfterAll
    void stopServer() throws Exception {
        if (console != null) {
            console.stopAndWait(Duration.ofMinutes(2));
            console.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("本番の jar が 26.1 のサーバーでも読み込まれ、19種が登録される")
    void pluginLoads() throws Exception {
        console.send("modifier status");
        console.await("登録数: 19", Duration.ofSeconds(30));
        assertTrue(console.sawLine("リソースパックを配信します"),
                "パックの配信が始まっていない" + console.tail());
    }

    @Test
    @Order(2)
    @DisplayName("参加すると選択画面が開き、閉じても開き直し、クリックで確定する")
    void selectionRoundTrip() throws Exception {
        BotRunner.Result result =
                BotRunner.run(botDir, "selection", TestServerDir.SERVER_PORT, BOT);
        assertEquals(0, result.exitCode(), "ボットが失敗した" + result.describe());

        assertTrue(result.first("resource_pack_offered").isPresent(),
                "リソースパックが配られていない" + result.describe());

        var opened = result.first("gui_opened").orElseThrow(
                () -> new AssertionError("参加時に選択画面が開かない" + result.describe()));
        assertEquals("モディファイアを選択", opened.field("title").orElse(""),
                "画面のタイトルが違う" + result.describe());

        // 中央の行に3つだけ並ぶ (SelectionLayout.slotsFor(3))
        String slots = opened.field("slots").orElse("");
        Map<String, Integer> perItem = new HashMap<>();
        Matcher entries = Pattern.compile("\"(\\d+)\":\"([^\"]+)\"").matcher(slots);
        while (entries.find()) {
            assertTrue(Set.of("11", "13", "15").contains(entries.group(1)),
                    "選択肢のスロットは 11/13/15 だけのはず: " + slots);
            perItem.merge(entries.group(2), 1, Integer::sum);
        }
        assertEquals(3, perItem.size(), "3択になっていない: " + perItem);
        for (var entry : perItem.entrySet()) {
            assertEquals(1, entry.getValue(),
                    entry.getKey() + " が 1枠に収まっていない: " + perItem);
        }

        assertTrue(result.first("gui_reopened").isPresent(),
                "未選択のまま閉じたのに開き直さない" + result.describe());

        var clicked = result.first("choice_clicked").orElseThrow(
                () -> new AssertionError("選択できていない" + result.describe()));
        assertTrue(clicked.has("windowClosed", "true"),
                "確定しても画面が閉じない" + result.describe());
        assertTrue(clicked.field("confirmMessage").orElse("").contains("選びました"),
                "確定のメッセージが出ていない" + result.describe());

        var after = result.first("after_choice").orElseThrow();
        assertTrue(after.has("windowOpen", "false"),
                "確定したのに開き直してしまう" + result.describe());
    }

    @Test
    @Order(3)
    @DisplayName("複数人が同時に参加して、それぞれ選択できる")
    void multiplePlayers() throws Exception {
        BotRunner.Result result =
                BotRunner.run(botDir, "multiplayer", TestServerDir.SERVER_PORT, BOT);
        assertEquals(0, result.exitCode(), "ボットが失敗した" + result.describe());

        assertEquals(3, result.of("gui_opened").size(),
                "3人とも選択画面が開くはず" + result.describe());
        assertEquals(3, result.of("choice_clicked").size(),
                "3人とも確定できるはず" + result.describe());
        for (var clicked : result.of("choice_clicked")) {
            assertTrue(clicked.field("confirmMessage").orElse("").contains("選びました"),
                    clicked.field("bot").orElse("?") + " が確定できていない" + result.describe());
        }
        assertTrue(result.first("all_joined").isPresent(),
                "3人が同時に居る状態を作れていない" + result.describe());
    }

    @Test
    @Order(4)
    @DisplayName("選んだ効果が実際に乗る (デブは受けるダメージが 25% 減る)")
    void chosenEffectApplies() throws Exception {
        // 3択は抽選なので、ボットが狙ったものを引き当てるまで選び直せるようにする
        console.send("op E2eFatty");
        // 自然回復があるとダメージの測定が濁るので止める
        console.send("gamerule natural_health_regeneration false");
        Thread.sleep(1000);

        BotRunner.Result result = BotRunner.run(botDir, "effect_fat",
                TestServerDir.SERVER_PORT, BOT, observation -> {
                    // ボットが構えたらコンソールから固定量のダメージを与える
                    if (observation.event().equals("ready_for_damage")) {
                        try {
                            console.send("damage E2eFatty 8");
                        } catch (Exception e) {
                            throw new AssertionError("ダメージを与えられない", e);
                        }
                    }
                });
        assertEquals(0, result.exitCode(), "ボットが失敗した" + result.describe());
        assertTrue(result.first("choice_missing").isEmpty(),
                "デブが3択に出なかった。抽選なので稀に起きる" + result.describe());

        var health = result.first("health_after_damage").orElseThrow(
                () -> new AssertionError("HP の変化を観測できていない" + result.describe()));
        double lost = Double.parseDouble(health.field("lost").orElse("0"));
        // 8 ダメージが 25% 減って 6 になるはず
        assertEquals(6.0, lost, 0.51,
                "デブの軽減が効いていない。実際に減った量: " + lost + result.describe());
    }

    @Test
    @Order(5)
    @DisplayName("選択済みなら入り直しても画面は出ず、/modifier select で選び直せる")
    void rejoinAndReselect() throws Exception {
        console.send("op E2eRejoin");
        Thread.sleep(1000);

        BotRunner.Result result =
                BotRunner.run(botDir, "rejoin", TestServerDir.SERVER_PORT, BOT);
        assertEquals(0, result.exitCode(), "ボットが失敗した" + result.describe());

        var rejoined = result.first("rejoined").orElseThrow(
                () -> new AssertionError("入り直せていない" + result.describe()));
        assertTrue(rejoined.has("guiOpened", "false"),
                "選択済みなのに参加時の画面が出てしまう" + result.describe());

        var reselect = result.first("after_reselect_command").orElseThrow();
        assertTrue(reselect.has("guiOpen", "true"),
                "/modifier select で選び直せない" + result.describe());
    }

    @Test
    @Order(6)
    @DisplayName("シールドバッシュが実ゲームで反射する")
    void shieldBashReflects() throws Exception {
        for (String bot : new String[] {"E2eDefender", "E2eAttacker"}) {
            console.send("op " + bot);
        }
        console.send("gamerule natural_health_regeneration false");
        Thread.sleep(1000);

        BotRunner.Result result = BotRunner.run(botDir, "effect_shield_bash",
                TestServerDir.SERVER_PORT, BOT, observation -> {
                    try {
                        if (observation.event().equals("both_ready")) {
                            // 防御側に盾を持たせて構えさせ、攻撃側には剣を渡す
                            console.send("give E2eDefender shield");
                            console.send("give E2eAttacker iron_sword");
                            Thread.sleep(500);
                            console.send("item replace entity E2eDefender weapon.offhand"
                                    + " with shield");
                            // 構えは attacker からは見えないので、防御側を常に構えさせる
                            console.send("execute at E2eDefender run tp E2eAttacker ~ ~ ~");
                        }
                    } catch (Exception e) {
                        throw new AssertionError("準備に失敗", e);
                    }
                });
        assertEquals(0, result.exitCode(), "ボットが失敗した" + result.describe());
        assumeTrue(result.first("choice_missing").isEmpty(),
                "シールドバッシュを引き当てられなかった" + result.describe());

        var health = result.first("attacker_health").orElseThrow(
                () -> new AssertionError("攻撃側の HP を観測できていない" + result.describe()));
        double lost = Double.parseDouble(health.field("lost").orElse("0"));
        assertTrue(lost > 0,
                "盾で防いだのに攻撃側へ反射が返っていない。"
                        + "DamageModifier.BLOCKING が 26.x で機能していない可能性がある。"
                        + "攻撃側が失った HP: " + lost + result.describe());
    }

    @Test
    @Order(7)
    @DisplayName("二段ジャンプが実ゲームで上向きの速度を生む")
    void doubleJumpLaunches() throws Exception {
        console.send("op E2eJumper");
        Thread.sleep(1000);

        BotRunner.Result result =
                BotRunner.run(botDir, "effect_double_jump", TestServerDir.SERVER_PORT, BOT);
        assertEquals(0, result.exitCode(), "ボットが失敗した" + result.describe());
        assumeTrue(result.first("choice_missing").isEmpty(),
                "スウィフトネスブーツを引き当てられなかった" + result.describe());

        var jump = result.first("double_jump").orElseThrow(
                () -> new AssertionError("ジャンプを観測できていない" + result.describe()));
        // 着地してから送っていたら発動条件から外れる。観測そのものが無意味なので、
        // 速度より先にここで落とす。
        assertTrue(jump.has("airborne", "true"),
                "滞空中に飛行トグルを送れていない。この観測では二段ジャンプを判定できない"
                        + result.describe());
        // ボットの位置ではなく、サーバーが送ってきた速度で見る。mineflayer は自分の
        // 位置をクライアント側で予測していて、自分あての entity_velocity を取り込まない。
        // 速度を与えられてもボットの座標は落下したままになるので、座標では判定できない。
        double launched = Double.parseDouble(jump.field("launchedY").orElse("0"));
        assertTrue(launched > 0.5,
                "空中で飛行を切り替えてもサーバーが上向きの速度を与えていない。"
                        + "PlayerToggleFlightEvent が飛んでいない可能性がある。"
                        + "与えられた上向きの速度: " + launched + result.describe());
    }

    @Test
    @Order(8)
    @DisplayName("スウィフトネスブーツでも落下ダメージは消えない")
    void swiftnessBootsStillTakesFallDamage() throws Exception {
        console.send("op E2eFaller");
        console.send("gamerule natural_health_regeneration false");
        Thread.sleep(1000);

        BotRunner.Result result = BotRunner.run(botDir, "effect_fall_damage",
                TestServerDir.SERVER_PORT, BOT, observation -> {
                    if (observation.event().equals("ready_to_fall")) {
                        try {
                            // 真上へ 15 ブロック運んで落とす。素の落下ダメージなら
                            // ceil(15 - 3) = 12 ほど減るはずで、死にはしない。
                            console.send("execute at E2eFaller run tp E2eFaller ~ ~15 ~");
                        } catch (Exception e) {
                            throw new AssertionError("持ち上げられない", e);
                        }
                    }
                });
        assertEquals(0, result.exitCode(), "ボットが失敗した" + result.describe());
        assumeTrue(result.first("choice_missing").isEmpty(),
                "スウィフトネスブーツを引き当てられなかった" + result.describe());

        var fall = result.first("fall_damage").orElseThrow(
                () -> new AssertionError("落下を観測できていない" + result.describe()));
        double lost = Double.parseDouble(fall.field("lost").orElse("0"));
        assertTrue(lost > 0,
                "二段ジャンプのために飛行を許しているせいで、落下ダメージが丸ごと消えている。"
                        + "バニラは飛行を許されたプレイヤーの落下ダメージを無効にするため、"
                        + "setFlyingFallDamage で明示的に戻す必要がある。"
                        + "失った HP: " + lost + result.describe());
        // 15 ブロックなら 12 前後。armor 無しなのでそのまま入る
        assertEquals(12.0, lost, 3.0,
                "落下ダメージの量がバニラと食い違う。失った HP: " + lost + result.describe());
    }

    @Test
    @Order(9)
    @DisplayName("不眠症が夜明けで移動速度低下を付ける")
    void insomniaTriggersOnWaking() throws Exception {
        console.send("op E2eSleeper");
        Thread.sleep(1000);

        BotRunner.Result result = BotRunner.run(botDir, "effect_insomnia",
                TestServerDir.SERVER_PORT, BOT, observation -> {
                    try {
                        if (observation.event().equals("ready_to_sleep")) {
                            // 足元を平らにしてベッドを置き、夜にする
                            console.send("execute at E2eSleeper run fill ~-3 ~-1 ~-3"
                                    + " ~3 ~-1 ~3 stone");
                            console.send("execute at E2eSleeper run fill ~-3 ~ ~-3"
                                    + " ~3 ~2 ~3 air");
                            // ベッドは2ブロック。頭と足を明示して置く
                            console.send("execute at E2eSleeper run setblock ~2 ~ ~"
                                    + " red_bed[facing=east,part=foot]");
                            console.send("execute at E2eSleeper run setblock ~3 ~ ~"
                                    + " red_bed[facing=east,part=head]");
                            // 1人でも寝れば夜が明けるようにする
                            console.send("gamerule players_sleeping_percentage 0");
                            // 近くに敵対 Mob が居ると「モンスターが近くにいる」で寝られない。
                            // 湧いているかどうかで結果が変わるので、ここで消しておく。
                            console.send("difficulty peaceful");
                            console.send("time set night");
                        }
                    } catch (Exception e) {
                        throw new AssertionError("寝床の準備に失敗", e);
                    }
                });
        assertEquals(0, result.exitCode(), "ボットが失敗した" + result.describe());
        assumeTrue(result.first("choice_missing").isEmpty(),
                "不眠症を引き当てられなかった" + result.describe());
        assumeTrue(result.first("no_bed").isEmpty() && result.first("sleep_failed").isEmpty(),
                "ボットを寝かせられなかった (テスト環境の都合)" + result.describe());

        var effects = result.first("effects_after_waking").orElseThrow(
                () -> new AssertionError("起床後の効果を観測できていない" + result.describe()));
        assertTrue(Integer.parseInt(effects.field("count").orElse("0")) > 0,
                "夜を明かしても移動速度低下が付かない。"
                        + "TimeSkipEvent の時点で寝ている判定になっていない可能性がある。"
                        + result.describe());
    }

    @Test
    @Order(10)
    @DisplayName("一連の検証でサーバー側に例外が出ていない")
    void noServerExceptions() {
        assertFalse(console.sawLine("Caused by:"),
                "サーバーで例外が起きている" + console.tail());
        assertTrue(console.isAlive(), "サーバーが落ちている" + console.tail());
    }

    @Test
    @Order(11)
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
