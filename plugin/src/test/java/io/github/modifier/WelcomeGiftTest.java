package io.github.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** どれを選んでももらえるウェルカムギフト。 */
@DisplayName("ウェルカムギフト")
class WelcomeGiftTest {

    private static Random returning(double value) {
        return new Random() {
            @Override
            public double nextDouble() {
                return value;
            }
        };
    }

    @Test
    @DisplayName("ベッド・パン 4・木材 16・石炭 16 は必ず入る")
    void alwaysHasTheBasics() {
        assertEquals(List.of(
                StartingItems.Item.of(Material.RED_BED),
                StartingItems.Item.of(Material.BREAD, 4),
                StartingItems.Item.of(Material.OAK_PLANKS, 16),
                StartingItems.Item.of(Material.COAL, 16)), WelcomeGift.roll(returning(0.999)));
    }

    @Test
    @DisplayName("5% でエンチャントされた金のリンゴが足される")
    void goldenAppleAtFivePercent() {
        assertEquals(0.05, WelcomeGift.GOLDEN_APPLE_CHANCE, 1e-9);
        List<StartingItems.Item> lucky = WelcomeGift.roll(returning(0.0));
        assertTrue(lucky.contains(StartingItems.Item.of(Material.ENCHANTED_GOLDEN_APPLE)));
        assertEquals(WelcomeGift.ALWAYS.size() + 1, lucky.size(), "基本の品はそのまま");

        assertFalse(WelcomeGift.roll(returning(0.05)).contains(WelcomeGift.GOLDEN_APPLE), "5% ちょうどは外れ");
    }
    @Test
    @DisplayName("受け取り済みの印はワールドごとで、選び直し (clear) では消えない")
    void welcomedOncePerWorld() {
        org.bukkit.World world = Mocks.world();
        org.bukkit.Server server = Mocks.server(world);
        Mocks.FakePlayer me = Mocks.player("Me", server, world);
        SelectionStore store = new SelectionStore(server);

        assertFalse(store.welcomedHere(me.player()), "まだ受け取っていない");
        store.markWelcomed(me.player());
        assertTrue(store.welcomedHere(me.player()));
        store.clear(me.player());
        assertTrue(store.welcomedHere(me.player()), "選び直しても二度目は無い");

        org.bukkit.World rebuilt = Mocks.world();
        org.mockito.Mockito.when(rebuilt.getUID()).thenReturn(java.util.UUID.randomUUID());
        org.mockito.Mockito.when(server.getWorlds()).thenReturn(List.of(rebuilt));
        assertFalse(store.welcomedHere(me.player()), "ワールドを作り直したらまた受け取れる");
    }


    @Test
    @DisplayName("実際の乱数でもおおよそ 5%")
    void roughlyFivePercent() {
        Random random = new Random(20260906L);
        int hits = 0;
        int rounds = 20000;
        for (int i = 0; i < rounds; i++) {
            if (WelcomeGift.roll(random).contains(WelcomeGift.GOLDEN_APPLE)) {
                hits++;
            }
        }
        assertTrue(hits > rounds * 0.04 && hits < rounds * 0.06, "当たりが " + hits + " / " + rounds);
    }
}
