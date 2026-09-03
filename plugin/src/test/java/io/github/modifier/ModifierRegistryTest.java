package io.github.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 選択肢の抽選。 */
class ModifierRegistryTest {

    private final ModifierRegistry registry = ModifierRegistry.withBuiltins(
            mock(Plugin.class), new SelectionStore(mock(Server.class)), new Random(0));

    @Test
    @DisplayName("仕様どおり19種が登録されている")
    void allNineteenAreRegistered() {
        assertEquals(19, registry.all().size());
        Set<String> ids = new HashSet<>(registry.all().stream().map(Modifier::id).toList());
        for (String id : new String[] {
                "fat", "swiftness_boots", "shield_bash",
                "aho", "serf", "dopagaki", "reaper_roulette", "sneer",
                "black_swordsman", "landmine", "healer", "food_poisoning",
                "clown", "insomnia", "leader", "nokya",
                "chef", "regret", "diva"}) {
            assertTrue(ids.contains(id), id + " が登録されていない");
        }
    }

    @Test
    @DisplayName("3択を出せるだけの数が組み込まれている")
    void hasEnoughBuiltinsForThreeChoices() {
        assertTrue(registry.all().size() >= SelectionLayout.MAX_CHOICES,
                "選択肢が3つ揃わないと3択にならない");
    }

    @Test
    @DisplayName("抽選した選択肢は重複しない")
    void picksAreDistinct() {
        for (int seed = 0; seed < 50; seed++) {
            List<Modifier> picked = registry.pick(3, new Random(seed));
            Set<String> ids = new HashSet<>();
            for (Modifier modifier : picked) {
                assertTrue(ids.add(modifier.id()), "同じモディファイアが2回出た: " + modifier.id());
            }
            assertEquals(3, picked.size());
        }
    }

    @Test
    @DisplayName("登録数が足りなければあるだけ返す")
    void picksAtMostWhatIsRegistered() {
        assertEquals(registry.all().size(), registry.pick(99, new Random(0)).size());
    }

    @Test
    @DisplayName("組み合わせは毎回同じにはならない")
    void picksVaryBetweenPlayers() {
        List<String> first = registry.pick(3, new Random(1)).stream().map(Modifier::id).toList();
        List<String> second = registry.pick(3, new Random(7)).stream().map(Modifier::id).toList();
        assertNotEquals(first, second, "抽選が効いていない");
    }

    @Test
    @DisplayName("id が重複する登録は弾く")
    void rejectsDuplicateIds() {
        Modifier existing = registry.all().get(0);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> registry.register(existing));
    }

    @Test
    @DisplayName("重みの合計が 100 に揃っている")
    void weightsAddUpToOneHundred() {
        assertEquals(100, registry.totalWeight(),
                "重みは相対値だが、合計を 100 に保つと表がそのまま割合として読める。"
                + "足したり動かしたりしたら、他をずらして 100 に戻すこと");
    }

    @Test
    @DisplayName("死を打ち消すものは標準より明確に出にくい")
    void deathInterceptorsAreRare() {
        // wiah と組み合わせると、これらは「ワールドがもう一度だけ死を許す」効果になる。
        // 標準の重み未満であることを崩さない。
        for (String id : new String[] {"nokya", "sneer", "reaper_roulette"}) {
            int weight = registry.byId(id).orElseThrow().weight();
            assertTrue(weight < Modifier.DEFAULT_WEIGHT,
                    id + " の重み " + weight + " が標準 " + Modifier.DEFAULT_WEIGHT + " 以上ある");
        }
        // 隠し効果ぶん、無キャは他の2つよりさらに低い
        assertTrue(registry.byId("nokya").orElseThrow().weight()
                        < registry.byId("sneer").orElseThrow().weight(),
                "隠し効果の無キャが冷笑より出やすくなっている");
    }

    @Test
    @DisplayName("なにもしない後悔が一番よく出る")
    void regretIsTheMostCommon() {
        int regret = registry.byId("regret").orElseThrow().weight();
        for (Modifier modifier : registry.all()) {
            if (!modifier.id().equals("regret")) {
                assertTrue(modifier.weight() < regret,
                        modifier.id() + " の重み " + modifier.weight() + " が後悔 " + regret + " 以上ある");
            }
        }
    }

    @Test
    @DisplayName("重みが 0 以下の登録は弾く")
    void rejectsNonPositiveWeights() {
        Modifier never = new BaseModifier("never", "出ない", org.bukkit.Material.STONE, "x") {
            @Override
            public int weight() {
                return 0;
            }
        };
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> registry.register(never));
    }

    @Test
    @DisplayName("重みの大きいものほど実際に多く出る")
    void heavierModifiersAppearMoreOften() {
        Random random = new Random(20260830L);
        Map<String, Integer> hits = new HashMap<>();
        int rounds = 20000;
        for (int i = 0; i < rounds; i++) {
            for (Modifier picked : registry.pick(3, random)) {
                hits.merge(picked.id(), 1, Integer::sum);
            }
        }

        // 一番軽い無キャ (2) と、一番重い後悔 (10) の差は 5 倍あるはず
        int lightest = hits.getOrDefault("nokya", 0);
        int heaviest = hits.getOrDefault("regret", 0);
        assertTrue(heaviest > lightest * 3,
                "重みが抽選に効いていない。無キャ " + lightest + " 回に対して後悔 " + heaviest + " 回");

        // 重みの順序が出現数の順序としておおむね保たれていること
        for (Modifier modifier : registry.all()) {
            assertTrue(hits.getOrDefault(modifier.id(), 0) > 0,
                    modifier.id() + " が " + rounds + " 回引いて一度も出なかった");
        }
    }

    @Test
    @DisplayName("無キャの説明文に隠し効果が書かれていない")
    void nokyaKeepsItsSecret() {
        Modifier nokya = registry.byId("nokya").orElseThrow();
        String description = nokya.description().stream()
                .map(line -> net.kyori.adventure.text.serializer.plain
                        .PlainTextComponentSerializer.plainText().serialize(line))
                .reduce("", (a, b) -> a + b);
        assertEquals("なにも起こらない気がする。", description,
                "隠し効果 (蘇生・テレポート) は説明文に出してはいけない");
    }
}
