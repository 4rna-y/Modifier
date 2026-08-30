package io.github.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** 攻撃と被弾にまつわる効果。 */
@DisplayName("戦闘まわりの効果")
class CombatEffectTest {

    private World world;
    private Server server;
    private Mocks.FakePlayer me;

    @BeforeEach
    void setUp() {
        world = Mocks.world();
        server = Mocks.server(world);
        me = Mocks.player("Me", server, world);
    }

    /** 敵対モブ。{@code Zombie} は {@code Enemy} を継承しているので判定に使える。 */
    private static Zombie zombie() {
        return mock(Zombie.class);
    }

    private EntityDamageByEntityEvent hit(Entity attacker, Entity victim, double amount) {
        return new EntityDamageByEntityEvent(attacker, victim, DamageCause.ENTITY_ATTACK,
                Mocks.damageSource(), amount);
    }

    /**
     * 盾で防いだ攻撃。
     *
     * <p>{@code BLOCKING} の段は、その段を持つ形でイベントを組まないと参照できない。
     * 吸収した分は負の値で入る。
     */
    private EntityDamageByEntityEvent blockedHit(Entity attacker, double base, double blocked) {
        Map<DamageModifier, Double> amounts = new EnumMap<>(DamageModifier.class);
        amounts.put(DamageModifier.BASE, base);
        amounts.put(DamageModifier.BLOCKING, -blocked);

        Map<DamageModifier, com.google.common.base.Function<? super Double, Double>> functions =
                new EnumMap<>(DamageModifier.class);
        functions.put(DamageModifier.BASE, value -> value);
        functions.put(DamageModifier.BLOCKING, value -> value);

        return new EntityDamageByEntityEvent(attacker, me.player(), DamageCause.ENTITY_ATTACK,
                Mocks.damageSource(), amounts, functions);
    }

    @Nested
    @DisplayName("黒の剣士")
    class BlackSwordsman {
        private final BlackSwordsmanModifier swordsman = new BlackSwordsmanModifier();

        @Test
        @DisplayName("敵対モブへ与えたダメージの 10% を吸収する")
        void stealsLifeFromEnemies() {
            me.state().health = 10.0;
            swordsman.onDealtDamage(me.player(), hit(me.player(), zombie(), 8.0));
            assertEquals(List.of(0.8), me.state().healed);
        }

        @Test
        @DisplayName("敵対モブでなければ吸収しない")
        void ignoresNonEnemies() {
            Mocks.FakePlayer other = Mocks.player("Other", server, world);
            swordsman.onDealtDamage(me.player(), hit(me.player(), other.player(), 8.0));
            assertTrue(me.state().healed.isEmpty(), "プレイヤーからは吸わない");
        }

        @Test
        @DisplayName("ダメージが 0 なら何もしない")
        void ignoresZeroDamage() {
            swordsman.onDealtDamage(me.player(), hit(me.player(), zombie(), 0.0));
            assertTrue(me.state().healed.isEmpty());
        }
    }

    @Nested
    @DisplayName("シールドバッシュ")
    class ShieldBash {
        private final ShieldBashModifier bash = new ShieldBashModifier();

        @Test
        @DisplayName("盾で防いだ分の半分を攻撃者へ返す")
        void reflectsHalfOfBlocked() {
            me.state().blocking = true;
            LivingEntity attacker = mock(LivingEntity.class);

            EntityDamageByEntityEvent event = blockedHit(attacker, 6.0, 6.0);
            bash.onDamagedConfirmed(me.player(), event);

            org.mockito.Mockito.verify(attacker).damage(
                    org.mockito.ArgumentMatchers.eq(3.0),
                    org.mockito.ArgumentMatchers.any(Entity.class));
        }

        @Test
        @DisplayName("盾を構えていなければ返さない")
        void needsToBeBlocking() {
            me.state().blocking = false;
            LivingEntity attacker = mock(LivingEntity.class);

            EntityDamageByEntityEvent event = blockedHit(attacker, 6.0, 6.0);
            bash.onDamagedConfirmed(me.player(), event);

            org.mockito.Mockito.verify(attacker, org.mockito.Mockito.never())
                    .damage(org.mockito.ArgumentMatchers.anyDouble(),
                            org.mockito.ArgumentMatchers.any(Entity.class));
        }

        @Test
        @DisplayName("吸収が 0 なら返さない")
        void needsSomethingBlocked() {
            me.state().blocking = true;
            LivingEntity attacker = mock(LivingEntity.class);

            EntityDamageByEntityEvent event = blockedHit(attacker, 6.0, 0.0);
            bash.onDamagedConfirmed(me.player(), event);

            org.mockito.Mockito.verify(attacker, org.mockito.Mockito.never())
                    .damage(org.mockito.ArgumentMatchers.anyDouble(),
                            org.mockito.ArgumentMatchers.any(Entity.class));
        }
    }

    @Nested
    @DisplayName("ヒーラー")
    class Healer {
        private final HealerModifier healer = new HealerModifier();

        @Test
        @DisplayName("プレイヤーへの攻撃を消し、半分を回復に、半分を自傷に変える")
        void convertsDamageIntoHealing() {
            Mocks.FakePlayer friend = Mocks.player("Friend", server, world);
            friend.state().health = 10.0;
            me.state().health = 20.0;

            EntityDamageByEntityEvent event = hit(me.player(), friend.player(), 6.0);
            healer.onDealtDamage(me.player(), event);

            assertTrue(event.isCancelled(), "攻撃はなかったことになる");
            assertEquals(List.of(3.0), friend.state().healed, "半分を相手に回復");
            assertEquals(List.of(3.0), me.state().damaged, "同じ量を自分が受ける");
        }

        @Test
        @DisplayName("モブへの攻撃には効かない")
        void onlyAppliesToPlayers() {
            EntityDamageByEntityEvent event = hit(me.player(), zombie(), 6.0);
            healer.onDealtDamage(me.player(), event);
            assertFalse(event.isCancelled(), "モブは普通に殴れる");
            assertTrue(me.state().damaged.isEmpty());
        }

        @Test
        @DisplayName("自傷が連鎖しないよう合成ダメージとして通す")
        void selfDamageIsMarkedSynthetic() {
            Mocks.FakePlayer friend = Mocks.player("Friend", server, world);
            // 自傷の最中は合成ダメージの印が立っていること
            org.mockito.Mockito.doAnswer(invocation -> {
                assertTrue(SyntheticDamage.active(),
                        "自傷が合成ダメージとして通っていない。別の効果を誘発してしまう");
                return null;
            }).when(me.player()).damage(org.mockito.ArgumentMatchers.anyDouble());

            healer.onDealtDamage(me.player(), hit(me.player(), friend.player(), 6.0));
            assertFalse(SyntheticDamage.active(), "処理が終わったら印は下りる");
        }
    }

    @Nested
    @DisplayName("ドパガキ")
    class Dopagaki {

        @Test
        @DisplayName("敵対モブ以外を倒しても激昂しない")
        void onlyRagesOnEnemies() {
            DopagakiModifier dopagaki =
                    new DopagakiModifier(mock(org.bukkit.plugin.Plugin.class));
            Mocks.FakePlayer other = Mocks.player("Other", server, world);

            dopagaki.apply(me.player());
            dopagaki.onKill(me.player(), other.player());

            assertEquals(-0.30, me.scalarOn(org.bukkit.attribute.Attribute.ATTACK_DAMAGE), 1e-9,
                    "プレイヤーを倒しても激昂はしない");
        }

        @Test
        @DisplayName("敵対モブを倒すと激昂が乗り、差し引き +45% になる")
        void ragesOnEnemyKill() {
            org.bukkit.plugin.Plugin plugin = mock(org.bukkit.plugin.Plugin.class);
            // スケジューラは「予約されたが、まだ発火していない」状態にしておく
            io.papermc.paper.threadedregions.scheduler.EntityScheduler scheduler =
                    mock(io.papermc.paper.threadedregions.scheduler.EntityScheduler.class);
            when(me.player().getScheduler()).thenReturn(scheduler);

            DopagakiModifier dopagaki = new DopagakiModifier(plugin);
            dopagaki.apply(me.player());
            dopagaki.onKill(me.player(), zombie());

            assertEquals(0.45, me.scalarOn(org.bukkit.attribute.Attribute.ATTACK_DAMAGE), 1e-9,
                    "-30% と +75% が加算で合成される");
        }
    }
}
