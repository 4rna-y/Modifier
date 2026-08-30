package io.github.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.kyori.adventure.util.TriState;
import org.bukkit.GameMode;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 常時効果と、その場で完結する状況発動。
 *
 * <p>本物のイベントを組んでモディファイアへ渡し、イベントやプレイヤーが実際に
 * どう書き換わるかを見る。
 */
@DisplayName("常時効果")
class PassiveEffectTest {

    private World world;
    private Server server;
    private Mocks.FakePlayer me;

    @BeforeEach
    void setUp() {
        world = Mocks.world();
        server = Mocks.server(world);
        me = Mocks.player("Me", server, world);
    }

    private EntityDamageEvent damage(double amount) {
        return new EntityDamageEvent(me.player(), DamageCause.ENTITY_ATTACK,
                Mocks.damageSource(), amount);
    }

    private EntityExhaustionEvent exhaustion(float amount) {
        return new EntityExhaustionEvent(me.player(), ExhaustionReason.WALK, amount);
    }

    @Nested
    @DisplayName("デブ")
    class Fat {
        private final FatModifier fat = new FatModifier();

        @Test
        @DisplayName("移動速度を 30% 下げる")
        void slowsDown() {
            fat.apply(me.player());
            assertEquals(-0.30, me.scalarOn(Attribute.MOVEMENT_SPEED), 1e-9);
        }

        @Test
        @DisplayName("外すと移動速度が元に戻る")
        void removesCleanly() {
            fat.apply(me.player());
            fat.remove(me.player());
            assertFalse(me.hasAttribute(Attribute.MOVEMENT_SPEED));
        }

        @Test
        @DisplayName("何度掛け直しても二重にならない")
        void isIdempotent() {
            fat.apply(me.player());
            fat.apply(me.player());
            fat.apply(me.player());
            assertEquals(-0.30, me.scalarOn(Attribute.MOVEMENT_SPEED), 1e-9,
                    "掛け直しで積み上がってはいけない");
        }

        @Test
        @DisplayName("受けるダメージを 25% 減らす")
        void reducesDamage() {
            EntityDamageEvent event = damage(10.0);
            fat.onDamaged(me.player(), event);
            assertEquals(7.5, event.getDamage(), 1e-9);
        }

        @Test
        @DisplayName("満腹度の減りを 10% 速める")
        void burnsFoodFaster() {
            EntityExhaustionEvent event = exhaustion(1.0f);
            fat.onExhaustion(me.player(), event);
            assertEquals(1.10f, event.getExhaustion(), 1e-6f);
        }
    }

    @Nested
    @DisplayName("アホ")
    class Aho {
        private final AhoModifier aho = new AhoModifier();

        @Test
        @DisplayName("落下ダメージと採掘速度を 70% ずつ下げる")
        void appliesBothPenalties() {
            aho.apply(me.player());
            assertEquals(-0.70, me.scalarOn(Attribute.FALL_DAMAGE_MULTIPLIER), 1e-9);
            assertEquals(-0.70, me.scalarOn(Attribute.BLOCK_BREAK_SPEED), 1e-9);
        }

        @Test
        @DisplayName("外すと両方とも消える")
        void removesBoth() {
            aho.apply(me.player());
            aho.remove(me.player());
            assertFalse(me.hasAttribute(Attribute.FALL_DAMAGE_MULTIPLIER));
            assertFalse(me.hasAttribute(Attribute.BLOCK_BREAK_SPEED));
        }

        @Test
        @DisplayName("耐久値の減りが 3倍になる")
        void wearsGearOut() {
            ItemStack tool = org.mockito.Mockito.mock(ItemStack.class);
            PlayerItemDamageEvent event = new PlayerItemDamageEvent(me.player(), tool, 2, 2);
            aho.onItemDamage(me.player(), event);
            assertEquals(6, event.getDamage());
        }
    }

    @Nested
    @DisplayName("スウィフトネスブーツ")
    class SwiftnessBoots {
        private final SwiftnessBootsModifier boots = new SwiftnessBootsModifier();

        @Test
        @DisplayName("歩行速度を 10% 上げ、飛行フラグを立てる")
        void speedsUpAndArmsTheJump() {
            boots.apply(me.player());
            assertEquals(0.10, me.scalarOn(Attribute.MOVEMENT_SPEED), 1e-9);
            assertTrue(me.state().allowFlight, "二段ジャンプのために飛行フラグが要る");
        }

        @Test
        @DisplayName("飛行を許しても落下ダメージは消さない")
        void keepsFallDamageDespiteFlight() {
            // バニラは「飛行を許されたプレイヤー」の落下ダメージを丸ごと無効にする。
            // 二段ジャンプはその飛行フラグを借りているので、明示的に戻さないと
            // 二段ジャンプを使わない限り無傷で落ちられてしまう。
            boots.apply(me.player());
            assertEquals(TriState.TRUE, me.state().flyingFallDamage,
                    "飛行を許したなら、落下ダメージは明示的に戻すこと");
        }

        @Test
        @DisplayName("着地の見回りでも落下ダメージの設定を保つ")
        void tickKeepsFallDamageOn() {
            boots.apply(me.player());
            me.state().flyingFallDamage = TriState.NOT_SET;   // 何かに戻されたことにする
            boots.tick(me.player());
            assertEquals(TriState.TRUE, me.state().flyingFallDamage);
        }

        @Test
        @DisplayName("外すと落下ダメージの扱いがバニラへ戻る")
        void removeRestoresVanillaFallDamage() {
            boots.apply(me.player());
            boots.remove(me.player());
            assertEquals(TriState.NOT_SET, me.state().flyingFallDamage);
        }

        @Test
        @DisplayName("クリエイティブへ移ったら落下ダメージの上書きを外す")
        void creativeGetsItsFlightBack() {
            boots.apply(me.player());
            me.state().gameMode = GameMode.CREATIVE;
            boots.tick(me.player());
            assertEquals(TriState.NOT_SET, me.state().flyingFallDamage,
                    "クリエイティブで飛んでいる最中に落下ダメージが入ってはいけない");
        }

        @Test
        @DisplayName("二段ジャンプは真上へ加速し、横の勢いは保つ")
        void jumpsStraightUp() {
            me.state().velocity = new Vector(0.4, -0.6, -0.2);
            me.state().allowFlight = true;

            PlayerToggleFlightEvent event = new PlayerToggleFlightEvent(me.player(), true);
            boots.onToggleFlight(me.player(), event);

            assertTrue(event.isCancelled(), "飛行への切り替えは打ち消す");
            assertEquals(SwiftnessBootsModifier.JUMP_UP, me.state().velocity.getY(), 1e-9);
            assertEquals(0.4, me.state().velocity.getX(), 1e-9, "横の勢いは維持する");
            assertEquals(-0.2, me.state().velocity.getZ(), 1e-9);
        }

        @Test
        @DisplayName("跳んだ後は着地するまで跳べない")
        void onlyOnceUntilLanding() {
            me.state().allowFlight = true;
            boots.onToggleFlight(me.player(), new PlayerToggleFlightEvent(me.player(), true));
            assertFalse(me.state().allowFlight, "跳んだ直後は空中で跳べない");

            me.state().onGround = false;
            boots.tick(me.player());
            assertFalse(me.state().allowFlight, "空中では戻らない");

            me.state().onGround = true;
            boots.tick(me.player());
            assertTrue(me.state().allowFlight, "着地したら戻る");
        }

        @Test
        @DisplayName("クリエイティブでは飛行を奪わない")
        void leavesCreativeAlone() {
            me.state().gameMode = GameMode.CREATIVE;
            me.state().allowFlight = true;

            PlayerToggleFlightEvent event = new PlayerToggleFlightEvent(me.player(), true);
            boots.onToggleFlight(me.player(), event);
            assertFalse(event.isCancelled(), "クリエイティブの飛行は邪魔しない");
            assertTrue(me.state().allowFlight);

            boots.remove(me.player());
            assertTrue(me.state().allowFlight, "外すときもクリエイティブの飛行は残す");
        }

        @Test
        @DisplayName("すでに飛んでいるなら何もしない")
        void ignoresWhenAlreadyFlying() {
            me.state().flying = true;
            PlayerToggleFlightEvent event = new PlayerToggleFlightEvent(me.player(), false);
            boots.onToggleFlight(me.player(), event);
            assertFalse(event.isCancelled());
        }

        @Test
        @DisplayName("満腹度の減りを 20% 速める")
        void burnsFoodFastest() {
            EntityExhaustionEvent event = exhaustion(1.0f);
            boots.onExhaustion(me.player(), event);
            assertEquals(1.20f, event.getExhaustion(), 1e-6f);
        }
    }

    @Nested
    @DisplayName("ドパガキ")
    class Dopagaki {

        @Test
        @DisplayName("常時 30% の攻撃力低下がかかる")
        void appliesPenalty() {
            DopagakiModifier dopagaki =
                    new DopagakiModifier(org.mockito.Mockito.mock(org.bukkit.plugin.Plugin.class));
            dopagaki.apply(me.player());
            assertEquals(-0.30, me.scalarOn(Attribute.ATTACK_DAMAGE), 1e-9);
        }

        @Test
        @DisplayName("外すと激昂の分も一緒に消える")
        void removesBothModifiers() {
            DopagakiModifier dopagaki =
                    new DopagakiModifier(org.mockito.Mockito.mock(org.bukkit.plugin.Plugin.class));
            dopagaki.apply(me.player());
            dopagaki.remove(me.player());
            assertFalse(me.hasAttribute(Attribute.ATTACK_DAMAGE));
        }
    }
}
