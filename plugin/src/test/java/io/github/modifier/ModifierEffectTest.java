package io.github.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.Material;
import org.bukkit.event.entity.EntityDamageEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 効果の数値を固定する。
 *
 * <p>「10%」「20%」といった決めごとが意図せず変わったらここで落ちる。
 * サーバーを起動しないと確かめられない部分 (attribute の適用や実際の発動) は
 * 対象外で、そこは実機での確認になる。
 */
class ModifierEffectTest {

    @Nested
    @DisplayName("デブ")
    class Fat {
        @Test
        @DisplayName("移動速度 -30% / 満腹度の減り +10% / 受けるダメージ -25%")
        void numbers() {
            assertEquals(-0.30, FatModifier.SPEED_MULTIPLIER, 1e-9);
            assertEquals(1.10f, FatModifier.EXHAUSTION_MULTIPLIER, 1e-6f);
            assertEquals(0.75, FatModifier.DAMAGE_MULTIPLIER, 1e-9);
        }

        @Test
        @DisplayName("10ダメージが7.5になる")
        void reducesDamage() {
            assertEquals(7.5, 10.0 * FatModifier.DAMAGE_MULTIPLIER, 1e-9);
        }

        @Test
        @DisplayName("速度は下がり、ダメージは減る向きになっている")
        void signsAreRight() {
            assertTrue(FatModifier.SPEED_MULTIPLIER < 0, "デブなので速度は下がる");
            assertTrue(FatModifier.DAMAGE_MULTIPLIER < 1.0, "受けるダメージは減る");
            assertTrue(FatModifier.EXHAUSTION_MULTIPLIER > 1.0f, "満腹度の減りは速くなる");
        }
    }

    @Nested
    @DisplayName("スウィフトネスブーツ")
    class SwiftnessBoots {
        @Test
        @DisplayName("歩行速度 +10% / 満腹度の減り +20%")
        void numbers() {
            assertEquals(0.10, SwiftnessBootsModifier.SPEED_MULTIPLIER, 1e-9);
            assertEquals(1.20f, SwiftnessBootsModifier.EXHAUSTION_MULTIPLIER, 1e-6f);
        }

        @Test
        @DisplayName("二段ジャンプは真上にだけ加速する")
        void jumpsStraightUp() {
            assertTrue(SwiftnessBootsModifier.JUMP_UP > 0);
            // 横方向の加速は持たない (今の勢いをそのまま残す)
            assertTrue(java.util.Arrays.stream(SwiftnessBootsModifier.class.getFields())
                    .noneMatch(field -> field.getName().contains("FORWARD")),
                    "前方向の加速が復活している");
        }
    }

    @Nested
    @DisplayName("シールドバッシュ")
    class ShieldBash {
        @Test
        @DisplayName("防いだ分の半分を返す")
        void reflectsHalf() {
            assertEquals(0.5, ShieldBashModifier.REFLECT_RATIO, 1e-9);
            assertEquals(3.0, ShieldBashModifier.reflectedDamage(6.0), 1e-9);
        }

        @Test
        @DisplayName("盾が吸収した量は BLOCKING の絶対値")
        void readsBlockedAmount() {
            EntityDamageEvent event = mock(EntityDamageEvent.class);
            when(event.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING)).thenReturn(true);
            when(event.getDamage(EntityDamageEvent.DamageModifier.BLOCKING)).thenReturn(-6.0);
            assertEquals(6.0, ShieldBashModifier.blockedAmount(event), 1e-9);
        }

        @Test
        @DisplayName("防いでいなければ 0")
        void nothingBlocked() {
            EntityDamageEvent event = mock(EntityDamageEvent.class);
            when(event.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING)).thenReturn(false);
            assertEquals(0.0, ShieldBashModifier.blockedAmount(event), 1e-9);
        }
    }

    @Nested
    @DisplayName("アホ")
    class Aho {
        @Test
        @DisplayName("落下 -70% / 採掘 -70% / 耐久減少 3倍")
        void numbers() {
            assertEquals(-0.70, AhoModifier.FALL_DAMAGE_MULTIPLIER, 1e-9);
            assertEquals(-0.70, AhoModifier.MINING_SPEED_MULTIPLIER, 1e-9);
            assertEquals(3, AhoModifier.DURABILITY_MULTIPLIER);
        }
    }

    @Nested
    @DisplayName("食中毒")
    class FoodPoisoning {
        @Test
        @DisplayName("75% で発症し、吐き気は演出が見える長さがある")
        void numbers() {
            assertEquals(0.75, FoodPoisoningModifier.CHANCE, 1e-9);
            assertTrue(FoodPoisoningModifier.NAUSEA_DURATION_TICKS >= 10 * 20,
                    "吐き気の演出はかかりが遅いので、短いと何も見えない");
        }
    }

    @Nested
    @DisplayName("不眠症")
    class Insomnia {
        @Test
        @DisplayName("移動速度低下は2分")
        void numbers() {
            assertEquals(2 * 60 * 20, InsomniaModifier.SLOWNESS_DURATION_TICKS);
        }
    }

    @Nested
    @DisplayName("黒の剣士")
    class BlackSwordsman {
        @Test
        @DisplayName("与えたダメージの10%を吸収する")
        void numbers() {
            assertEquals(0.10, BlackSwordsmanModifier.LIFESTEAL_RATIO, 1e-9);
            assertEquals(1.0, 10.0 * BlackSwordsmanModifier.LIFESTEAL_RATIO, 1e-9);
        }
    }

    @Nested
    @DisplayName("ドパガキ")
    class Dopagaki {
        @Test
        @DisplayName("常時 -30% / キル後5秒 +75% / 差し引き +45%")
        void numbers() {
            assertEquals(-0.30, DopagakiModifier.ATTACK_PENALTY, 1e-9);
            assertEquals(0.75, DopagakiModifier.RAGE_BONUS, 1e-9);
            assertEquals(5 * 20, DopagakiModifier.RAGE_DURATION_TICKS);
            // 加算合成なので、バフ中の実効値は足し算になる
            assertEquals(0.45,
                    DopagakiModifier.ATTACK_PENALTY + DopagakiModifier.RAGE_BONUS, 1e-9);
        }
    }

    @Nested
    @DisplayName("農奴")
    class Serf {
        @Test
        @DisplayName("50% で 3倍")
        void numbers() {
            assertEquals(0.5, SerfModifier.CHANCE, 1e-9);
            assertEquals(3, SerfModifier.MULTIPLIER);
            assertEquals(6, SerfModifier.multiplied(2));
        }

        @Test
        @DisplayName("主要な作物が対象に入っている")
        void cropsAreCovered() {
            assertTrue(SerfModifier.CROPS.contains(Material.WHEAT));
            assertTrue(SerfModifier.CROPS.contains(Material.NETHER_WART));
            assertTrue(SerfModifier.CROPS.contains(Material.SWEET_BERRY_BUSH));
            assertFalse(SerfModifier.CROPS.contains(Material.STONE), "作物以外が混ざっている");
            assertFalse(SerfModifier.CROPS.contains(Material.OAK_LOG), "作物以外が混ざっている");
        }
    }

    @Nested
    @DisplayName("地雷系")
    class Landmine {
        @Test
        @DisplayName("威力 2.0 / 満腹度 -1 / ブロックは壊さない")
        void numbers() {
            assertEquals(2.0f, LandmineModifier.EXPLOSION_POWER, 1e-6f);
            assertEquals(1, LandmineModifier.HUNGER_COST);
            assertFalse(LandmineModifier.BREAK_BLOCKS, "ワールドが穴だらけになる");
            assertFalse(LandmineModifier.SET_FIRE, "延焼はさせない");
        }
    }

    @Nested
    @DisplayName("ヒーラー")
    class Healer {
        @Test
        @DisplayName("なかったことにした攻撃の半分を回復と自傷に変換する")
        void numbers() {
            assertEquals(0.5, HealerModifier.CONVERT_RATIO, 1e-9);
            assertEquals(3.0, HealerModifier.converted(6.0), 1e-9);
        }
    }

    @Nested
    @DisplayName("ピエロ")
    class Clown {
        @Test
        @DisplayName("すべての効果が独立に 20%")
        void numbers() {
            assertEquals(0.2, ClownModifier.CHANCE, 1e-9);
            assertEquals(5.0, ClownModifier.TELEPORT_RADIUS, 1e-9);
            assertEquals(5.0, ClownModifier.HEAL_RADIUS, 1e-9);
            assertEquals(1.0, ClownModifier.HEAL_AMOUNT, 1e-9);
        }
    }

    @Nested
    @DisplayName("死神ルーレット")
    class ReaperRoulette {
        @Test
        @DisplayName("巻き込まれた側の HP は半減するが 0 にはならない")
        void victimNeverDies() {
            assertEquals(10.0, ReaperRouletteModifier.halvedHealth(20.0), 1e-9);
            assertEquals(1.0, ReaperRouletteModifier.halvedHealth(1.5), 1e-9);
            assertEquals(1.0, ReaperRouletteModifier.halvedHealth(0.5), 1e-9);
            assertTrue(ReaperRouletteModifier.halvedHealth(0.1) > 0);
        }
    }

    @Nested
    @DisplayName("リーダー")
    class Leader {
        @Test
        @DisplayName("半径5m / 効果は掛け直し間隔より長く途切れない")
        void numbers() {
            assertEquals(5.0, LeaderModifier.RADIUS, 1e-9);
            assertTrue(LeaderModifier.EFFECT_DURATION_TICKS > LeaderModifier.APPLY_INTERVAL_TICKS,
                    "効果が間隔より短いとオーラが点滅する");
        }
    }

    @Nested
    @DisplayName("蘇生")
    class RevivalRule {
        @Test
        @DisplayName("戻す HP は最大値の半分")
        void healthRatioIsHalf() {
            assertEquals(0.5, Revival.HEALTH_RATIO, 1e-9);
        }
    }
}
