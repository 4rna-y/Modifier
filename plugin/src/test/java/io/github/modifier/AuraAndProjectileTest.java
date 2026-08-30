package io.github.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * オーラと飛び道具。
 *
 * <p>どちらも周囲や命中先を相手にするので、ワールド側の問い合わせを差し替えて確かめる。
 */
@DisplayName("オーラと飛び道具")
class AuraAndProjectileTest {

    private World world;
    private Server server;
    private Mocks.FakePlayer me;

    @BeforeEach
    void setUp() {
        world = Mocks.world();
        server = Mocks.server(world);
        me = Mocks.player("Me", server, world);
    }

    @Nested
    @DisplayName("地雷系")
    class Landmine {

        private final LandmineModifier landmine = new LandmineModifier();

        /** 命中した相手の居るワールド。爆発の呼び出しを見るために別に持つ。 */
        private Zombie targetAt(World targetWorld, double x, double z) {
            Zombie target = mock(Zombie.class);
            when(target.getWorld()).thenReturn(targetWorld);
            when(target.getLocation()).thenReturn(new Location(targetWorld, x, 64, z));
            return target;
        }

        @Test
        @DisplayName("自分の矢が敵対モブに当たると命中地点で爆発する")
        void explodesOnEnemyHit() {
            Zombie target = targetAt(world, 10, 20);
            AbstractArrow arrow = mock(AbstractArrow.class);
            landmine.onProjectileHit(me.player(), new ProjectileHitEvent(arrow, target));

            verify(world).createExplosion(me.player(), target.getLocation(),
                    LandmineModifier.EXPLOSION_POWER,
                    LandmineModifier.SET_FIRE, LandmineModifier.BREAK_BLOCKS);
        }

        @Test
        @DisplayName("爆発のたびに満腹度が 1 減る")
        void costsHunger() {
            me.state().foodLevel = 12;
            landmine.onProjectileHit(me.player(),
                    new ProjectileHitEvent(mock(AbstractArrow.class), targetAt(world, 0, 0)));
            assertEquals(11, me.state().foodLevel);
        }

        @Test
        @DisplayName("満腹度は 0 より下がらない")
        void hungerNeverGoesNegative() {
            me.state().foodLevel = 0;
            landmine.onProjectileHit(me.player(),
                    new ProjectileHitEvent(mock(AbstractArrow.class), targetAt(world, 0, 0)));
            assertEquals(0, me.state().foodLevel);
        }

        @Test
        @DisplayName("トライデントでは爆発しない")
        void tridentIsNotAnArrow() {
            // Trident は AbstractArrow を継承しているので、明示的に除外できているかを見る
            Trident trident = mock(Trident.class);
            landmine.onProjectileHit(me.player(),
                    new ProjectileHitEvent(trident, targetAt(world, 0, 0)));

            verify(world, never()).createExplosion(org.mockito.ArgumentMatchers.any(Player.class),
                    org.mockito.ArgumentMatchers.any(Location.class),
                    org.mockito.ArgumentMatchers.anyFloat(),
                    org.mockito.ArgumentMatchers.anyBoolean(),
                    org.mockito.ArgumentMatchers.anyBoolean());
            assertEquals(20, me.state().foodLevel, "満腹度も減らない");
        }

        @Test
        @DisplayName("敵対モブ以外に当たっても爆発しない")
        void onlyEnemies() {
            Mocks.FakePlayer other = Mocks.player("Other", server, world);
            landmine.onProjectileHit(me.player(),
                    new ProjectileHitEvent(mock(AbstractArrow.class), other.player()));

            verify(world, never()).createExplosion(org.mockito.ArgumentMatchers.any(Player.class),
                    org.mockito.ArgumentMatchers.any(Location.class),
                    org.mockito.ArgumentMatchers.anyFloat(),
                    org.mockito.ArgumentMatchers.anyBoolean(),
                    org.mockito.ArgumentMatchers.anyBoolean());
        }

        @Test
        @DisplayName("何にも当たらなければ何もしない")
        void needsATarget() {
            landmine.onProjectileHit(me.player(),
                    new ProjectileHitEvent(mock(AbstractArrow.class)));
            assertEquals(20, me.state().foodLevel);
        }
    }

    @Nested
    @DisplayName("リーダー")
    class Leader {

        private final LeaderModifier leader = new LeaderModifier();

        /**
         * 半径 {@code RADIUS} の箱の中に居ることにするプレイヤーたち。
         *
         * <p>{@code Location#getNearbyPlayers(double)} は最終的にワールドの
         * 「Predicate 付き6引数版」へ落ちるので、そこを差し替える。
         */
        private void nearby(Mocks.FakePlayer... players) {
            List<Player> list = new ArrayList<>();
            for (Mocks.FakePlayer player : players) {
                list.add(player.player());
            }
            org.mockito.Mockito.doReturn(list).when(world).getNearbyEntitiesByType(
                    org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(Location.class),
                    org.mockito.ArgumentMatchers.anyDouble(),
                    org.mockito.ArgumentMatchers.anyDouble(),
                    org.mockito.ArgumentMatchers.anyDouble(),
                    org.mockito.ArgumentMatchers.any());
        }

        private Mocks.FakePlayer at(String name, double x, double z) {
            Mocks.FakePlayer player = Mocks.player(name, server, world);
            player.state().location = new Location(world, x, 64, z);
            return player;
        }

        private List<PotionEffectType> typesOn(Mocks.FakePlayer player) {
            List<PotionEffectType> types = new ArrayList<>();
            player.state().potionEffects.forEach(effect -> types.add(effect.getType()));
            return types;
        }

        @Test
        @DisplayName("半径内の全員にバフと空腹を配る")
        void handsOutTheAura() {
            Mocks.FakePlayer friend = at("Friend", 3, 0);
            nearby(me, friend);
            Mocks.installCurrentTick(server, 1000);

            leader.tick(me.player());

            for (Mocks.FakePlayer target : List.of(me, friend)) {
                List<PotionEffectType> types = typesOn(target);
                assertTrue(types.contains(PotionEffectType.HASTE), "採掘速度上昇");
                assertTrue(types.contains(PotionEffectType.STRENGTH), "攻撃力増加");
                assertTrue(types.contains(PotionEffectType.RESISTANCE), "耐性");
                assertTrue(types.contains(PotionEffectType.HUNGER), "空腹も配ってしまう");
            }
        }

        @Test
        @DisplayName("自分にも掛かる")
        void includesItself() {
            nearby(me);
            Mocks.installCurrentTick(server, 1000);
            leader.tick(me.player());
            assertFalse(me.state().potionEffects.isEmpty(), "本人も対象");
        }

        @Test
        @DisplayName("箱の隅に居る人は対象外 (半径5mの球で判定する)")
        void usesASphereNotABox() {
            // (4, 4) は箱には入るが、中心からの距離は約 5.66m で球の外
            Mocks.FakePlayer corner = at("Corner", 4, 4);
            nearby(me, corner);
            Mocks.installCurrentTick(server, 1000);

            leader.tick(me.player());

            assertFalse(me.state().potionEffects.isEmpty(), "中心の自分には掛かる");
            assertTrue(corner.state().potionEffects.isEmpty(),
                    "対角の遠い相手にまで届いてはいけない");
        }

        @Test
        @DisplayName("短い周期で呼ばれても間引かれる")
        void throttles() {
            nearby(me);
            Mocks.installCurrentTick(server, 1000);
            leader.tick(me.player());
            int afterFirst = me.state().potionEffects.size();

            // 同じ tick のまま呼び直しても増えない
            leader.tick(me.player());
            assertEquals(afterFirst, me.state().potionEffects.size(), "間引きが効いていない");

            // 間隔を空ければまた配られる
            Mocks.installCurrentTick(server,
                    1000 + (int) LeaderModifier.APPLY_INTERVAL_TICKS);
            leader.tick(me.player());
            assertTrue(me.state().potionEffects.size() > afterFirst, "間隔を空ければ配り直す");
        }

        @Test
        @DisplayName("効果の長さは掛け直しの間隔より長い")
        void aurasDoNotFlicker() {
            nearby(me);
            Mocks.installCurrentTick(server, 1000);
            leader.tick(me.player());

            me.state().potionEffects.forEach(effect ->
                    assertTrue(effect.getDuration() > LeaderModifier.APPLY_INTERVAL_TICKS,
                            "間隔より短いとオーラが点滅する: " + effect));
        }

        @Test
        @DisplayName("外すと間引きの記録も消える")
        void forgetsOnRemoval() {
            nearby(me);
            Mocks.installCurrentTick(server, 1000);
            leader.tick(me.player());
            int afterFirst = me.state().potionEffects.size();

            leader.remove(me.player());
            // 記録が消えているので、同じ tick でもまた配られる
            leader.tick(me.player());
            assertTrue(me.state().potionEffects.size() > afterFirst);
        }
    }
}
