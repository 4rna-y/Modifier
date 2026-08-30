package io.github.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 「死がなかったことになる」系。
 *
 * <p>致死ダメージを打ち消す仕組みなので、蘇生の後始末とチャージの消費を確かめる。
 */
@DisplayName("死亡まわりの効果")
class DeathEffectTest {

    private World world;
    private Server server;
    private Mocks.FakePlayer me;
    private SelectionStore store;

    @BeforeEach
    void setUp() {
        world = Mocks.world();
        server = Mocks.server(world);
        me = Mocks.player("Me", server, world);
        store = mock(SelectionStore.class);
        when(store.chargeAvailable(org.mockito.ArgumentMatchers.any())).thenReturn(true);
    }

    /** オンラインの顔ぶれを差し替える。 */
    private void online(Mocks.FakePlayer... players) {
        List<Player> list = new ArrayList<>();
        for (Mocks.FakePlayer player : players) {
            list.add(player.player());
        }
        org.mockito.Mockito.doReturn(list).when(server).getOnlinePlayers();
    }

    private EntityDamageEvent lethal() {
        return new EntityDamageEvent(me.player(), DamageCause.ENTITY_ATTACK,
                Mocks.damageSource(), 100.0);
    }

    @Nested
    @DisplayName("冷笑")
    class Sneer {

        @Test
        @DisplayName("死を打ち消し、HP を半分に戻してリスポーン地点へ飛ばす")
        void revivesAndTeleports() {
            Location respawn = new Location(world, 100, 70, 100);
            when(me.player().getRespawnLocation()).thenReturn(respawn);
            me.state().health = 0.5;

            SneerModifier sneer = new SneerModifier(store);
            assertTrue(sneer.interceptDeath(me.player(), lethal()), "死を打ち消すはず");

            assertEquals(10.0, me.state().health, 1e-9, "最大 HP の半分に戻る");
            assertEquals(List.of(respawn), me.state().teleports);
            org.mockito.Mockito.verify(store).consumeCharge(me.player());
        }

        @Test
        @DisplayName("リスポーン地点が無ければワールドのスポーンへ飛ばす")
        void fallsBackToWorldSpawn() {
            Location spawn = new Location(world, 0, 70, 0);
            when(me.player().getRespawnLocation()).thenReturn(null);
            when(world.getSpawnLocation()).thenReturn(spawn);

            SneerModifier sneer = new SneerModifier(store);
            assertTrue(sneer.interceptDeath(me.player(), lethal()));
            assertEquals(List.of(spawn), me.state().teleports);
        }

        @Test
        @DisplayName("チャージを使い切ったら二度と発動しない")
        void onlyOnce() {
            when(store.chargeAvailable(me.player())).thenReturn(false);
            SneerModifier sneer = new SneerModifier(store);
            assertFalse(sneer.interceptDeath(me.player(), lethal()), "2回目は助からない");
            org.mockito.Mockito.verify(store, org.mockito.Mockito.never())
                    .consumeCharge(org.mockito.ArgumentMatchers.any());
        }
    }

    @Nested
    @DisplayName("死神ルーレット")
    class ReaperRoulette {

        @Test
        @DisplayName("他プレイヤーを死亡地点へ引きずり込み、HP と満腹度を半分にする")
        void dragsSomeoneElseIn() {
            Mocks.FakePlayer victim = Mocks.player("Victim", server, world);
            victim.state().health = 16.0;
            victim.state().foodLevel = 18;
            me.state().location = new Location(world, 50, 12, 50);
            online(me, victim);

            ReaperRouletteModifier reaper = new ReaperRouletteModifier(store, new Random(0));
            assertTrue(reaper.interceptDeath(me.player(), lethal()));

            assertEquals(8.0, victim.state().health, 1e-9, "HP は半分");
            assertEquals(9, victim.state().foodLevel, "満腹度も半分");
            assertEquals(1, victim.state().teleports.size(), "死亡地点へ引きずり込まれる");
            assertEquals(50, victim.state().teleports.get(0).getX(), 1e-9);
            assertEquals(10.0, me.state().health, 1e-9, "自分は最大 HP の半分で蘇る");
        }

        @Test
        @DisplayName("他に誰も居なければ発動せず、チャージも減らない")
        void needsSomeoneToDrag() {
            online(me);
            ReaperRouletteModifier reaper = new ReaperRouletteModifier(store, new Random(0));

            assertFalse(reaper.interceptDeath(me.player(), lethal()), "巻き込む相手が居ない");
            org.mockito.Mockito.verify(store, org.mockito.Mockito.never())
                    .consumeCharge(org.mockito.ArgumentMatchers.any());
        }

        @Test
        @DisplayName("死亡画面の途中の相手と観戦者は巻き込まない")
        void skipsCorpsesAndSpectators() {
            Mocks.FakePlayer corpse = Mocks.player("Corpse", server, world);
            corpse.state().health = 0.0;
            Mocks.FakePlayer ghost = Mocks.player("Ghost", server, world);
            ghost.state().gameMode = GameMode.SPECTATOR;
            online(me, corpse, ghost);

            ReaperRouletteModifier reaper = new ReaperRouletteModifier(store, new Random(0));
            assertFalse(reaper.interceptDeath(me.player(), lethal()),
                    "引きずり込める相手が居ないので発動しない");
            assertTrue(corpse.state().teleports.isEmpty());
            assertTrue(ghost.state().teleports.isEmpty());
        }

        @Test
        @DisplayName("巻き込まれた相手の HP は 0 にならない")
        void victimNeverDies() {
            Mocks.FakePlayer victim = Mocks.player("Victim", server, world);
            victim.state().health = 0.5;
            online(me, victim);

            new ReaperRouletteModifier(store, new Random(0))
                    .interceptDeath(me.player(), lethal());
            assertTrue(victim.state().health > 0, "半減で死なせてはいけない");
        }
    }

    @Nested
    @DisplayName("無キャ")
    class Nokya {

        @Test
        @DisplayName("他プレイヤーの死を打ち消し、自分の位置へ引き寄せる")
        void savesSomeoneElse() {
            Mocks.FakePlayer dying = Mocks.player("Dying", server, world);
            dying.state().health = 0.5;
            me.state().location = new Location(world, 7, 65, 7);

            NokyaModifier nokya = new NokyaModifier(store);
            assertTrue(nokya.interceptOtherDeath(me.player(), dying.player(), lethal()));

            assertEquals(10.0, dying.state().health, 1e-9, "助かった側が最大 HP の半分に戻る");
            assertEquals(1, dying.state().teleports.size());
            assertEquals(7, dying.state().teleports.get(0).getX(), 1e-9,
                    "無キャの居る場所へ引き寄せる");
        }

        @Test
        @DisplayName("自分の死では発動しない")
        void doesNotSaveItself() {
            NokyaModifier nokya = new NokyaModifier(store);
            assertFalse(nokya.interceptDeath(me.player(), lethal()),
                    "隠し効果は他人のためのもの");
        }

        @Test
        @DisplayName("一度使ったら二度と発動しない")
        void onlyOnce() {
            when(store.chargeAvailable(me.player())).thenReturn(false);
            Mocks.FakePlayer dying = Mocks.player("Dying", server, world);
            assertFalse(new NokyaModifier(store)
                    .interceptOtherDeath(me.player(), dying.player(), lethal()));
        }

        @Test
        @DisplayName("説明文に隠し効果を書かない")
        void keepsItsSecret() {
            NokyaModifier nokya = new NokyaModifier(store);
            String description = nokya.description().stream()
                    .map(line -> net.kyori.adventure.text.serializer.plain
                            .PlainTextComponentSerializer.plainText().serialize(line))
                    .reduce("", String::concat);
            assertEquals("なにも起こらない気がする。", description);
            assertNotNull(nokya.displayName());
        }
    }
}
