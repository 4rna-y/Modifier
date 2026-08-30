package io.github.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import javax.imageio.ImageIO;

import net.kyori.adventure.key.Key;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * 登録済みモディファイアと、同梱リソースパックの中身が食い違っていないことを見る。
 *
 * <p>アイコンが出ない原因のほとんどは「モディファイアを足したのにパックへ置き忘れた」
 * なので、クライアントを立ち上げる前にここで気づけるようにしておく。
 */
class PackAssetsTest {

    /**
     * アイコンをまだ用意していないモディファイア。
     *
     * <p>アートが出来たらパックに置き、ここから消すこと。置いたのに消し忘れていると
     * このテストが落ちる。
     */
    private static final Set<String> PENDING_ICONS = Set.of();

    private final ModifierRegistry registry = ModifierRegistry.withBuiltins(
            mock(Plugin.class), new SelectionStore(mock(Server.class)), new Random(0));

    @TestFactory
    @DisplayName("登録済みモディファイアのアイコン一式がパックに揃っている")
    List<DynamicTest> everyModifierHasItsAssets() {
        File packDir = packDir();
        List<DynamicTest> tests = new ArrayList<>();

        for (Modifier modifier : registry.all()) {
            tests.add(DynamicTest.dynamicTest(modifier.id(), () -> {
                Key model = modifier.iconModel();
                File definition = new File(packDir,
                        "assets/" + model.namespace() + "/items/" + model.value() + ".json");
                File modelFile = new File(packDir,
                        "assets/modifier/models/item/" + modifier.id() + ".json");
                File texture = new File(packDir,
                        "assets/modifier/textures/item/" + modifier.id() + ".png");

                if (PENDING_ICONS.contains(modifier.id())) {
                    assertFalse(definition.isFile() && modelFile.isFile() && texture.isFile(),
                            "アイコンが揃ったので PENDING_ICONS から " + modifier.id()
                                    + " を消すこと");
                    return;
                }

                assertTrue(definition.isFile(), "アイテムモデル定義が無い: " + definition);
                assertTrue(modelFile.isFile(), "モデルが無い: " + modelFile);
                assertTrue(texture.isFile(), "テクスチャが無い: " + texture);

                BufferedImage image = ImageIO.read(texture);
                assertEquals(image.getWidth(), image.getHeight(),
                        "アイテムのテクスチャは正方形でなければならない: " + texture);
                assertTrue(isPowerOfTwo(image.getWidth()),
                        "2の冪でないとミップマップが落ちる: " + texture + " が "
                                + image.getWidth() + "px");
            }));
        }
        return tests;
    }

    @org.junit.jupiter.api.Test
    @DisplayName("pack.mcmeta が 26.2 の pack_format を宣言している")
    void packFormatMatchesTheServer() throws Exception {
        File mcmeta = new File(packDir(), "pack.mcmeta");
        assertTrue(mcmeta.isFile(), "pack.mcmeta が無い");
        String text = java.nio.file.Files.readString(mcmeta.toPath());
        // 26.2 のサーバー jar の version.json が resource_major: 88 を持つ
        assertTrue(text.contains("\"pack_format\": 88"),
                "pack_format がサーバーの想定と違う: " + text);
    }

    private static File packDir() {
        String configured = System.getProperty("modifier.packDir");
        assumeTrue(configured != null, "modifier.packDir が未設定 (gradle 経由で実行すること)");
        File dir = new File(configured);
        assumeTrue(dir.isDirectory(), "リソースパックが見つからない: " + dir);
        return dir;
    }

    private static boolean isPowerOfTwo(int value) {
        return value > 0 && (value & (value - 1)) == 0;
    }
}
