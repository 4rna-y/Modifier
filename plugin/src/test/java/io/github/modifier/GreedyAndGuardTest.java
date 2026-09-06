package io.github.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Random;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** よくばりの抽選と束ね方、選択中の守り。 */
@DisplayName("よくばりと選択中の守り")
class GreedyAndGuardTest {

    private World world;
    private Server server;
    private Mocks.FakePlayer me;
    private SelectionStore store;
    private ModifierRegistry registry;

    @BeforeEach
    void setUp() {
        world = Mocks.world();
        server = Mocks.server(world);
        me = Mocks.player("Me", server, world);
        store = new SelectionStore(server);
        registry = ModifierRegistry.withBuiltins(mock(Plugin.class), store, new Random(0));
    }

    private static String plain(net.kyori.adventure.text.Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Nested
    @DisplayName("よくばり")
    class Greedy {

        @Test
        @DisplayName("選ぶと自分以外から 2 つ引いて保存し、その 2 つを束ねたものが効く")
        void picksTwoOthers() {
            Modifier greedy = registry.byId("greedy").orElseThrow();
            greedy.onChosen(me.player());

            List<String> picked = store.bundle(me.player());
            assertEquals(2, picked.size());
            assertNotEquals(picked.get(0), picked.get(1), "同じものを 2 回引かない");
            assertFalse(picked.contains("greedy"), "よくばりがよくばりを引いてはいけない");

            Modifier resolved = greedy.resolveFor(me.player());
            ModifierBundle bundle = assertInstanceOf(ModifierBundle.class, resolved);
            assertEquals(picked, bundle.parts().stream().map(Modifier::id).toList());
            assertEquals("greedy", bundle.id(), "保存上の id はよくばりのまま");
            assertEquals(plain(greedy.displayName()), plain(bundle.displayName()));
        }

        @Test
        @DisplayName("何度引いてもよくばり自身は出ない")
        void neverPicksItself() {
            for (int seed = 0; seed < 200; seed++) {
                ModifierRegistry fresh = ModifierRegistry.withBuiltins(
                        mock(Plugin.class), store, new Random(seed));
                fresh.byId("greedy").orElseThrow().onChosen(me.player());
                assertFalse(store.bundle(me.player()).contains("greedy"), "seed " + seed);
            }
        }

        @Test
        @DisplayName("中身が保存されていなければ、なにもしない自分自身のまま")
        void withoutABundleItIsInert() {
            Modifier greedy = registry.byId("greedy").orElseThrow();
            assertSame(greedy, greedy.resolveFor(me.player()));
        }

        @Test
        @DisplayName("説明には中身の名前と説明が並ぶ")
        void describesItsParts() {
            Modifier greedy = registry.byId("greedy").orElseThrow();
            greedy.onChosen(me.player());
            ModifierBundle bundle = (ModifierBundle) greedy.resolveFor(me.player());

            String text = String.join("\n", bundle.description().stream().map(GreedyAndGuardTest::plain).toList());
            for (Modifier part : bundle.parts()) {
                assertTrue(text.contains(plain(part.displayName())), part.id() + " の名前が説明に無い");
                assertTrue(text.contains(plain(part.description().get(0))), part.id() + " の説明が無い");
            }
        }

        @Test
        @DisplayName("束ねたものは常時効果もフックも中身の全部に配る")
        void bundleDelegatesToEveryPart() {
            ModifierBundle bundle = new ModifierBundle(registry.byId("greedy").orElseThrow(),
                    List.of(new FatModifier(), new AhoModifier()));

            bundle.apply(me.player());
            assertEquals(FatModifier.SPEED_MULTIPLIER, me.scalarOn(Attribute.MOVEMENT_SPEED), 1e-9);
            assertEquals(AhoModifier.FALL_DAMAGE_MULTIPLIER, me.scalarOn(Attribute.FALL_DAMAGE_MULTIPLIER), 1e-9);

            EntityDamageEvent hit = new EntityDamageEvent(me.player(), DamageCause.ENTITY_ATTACK,
                    Mocks.damageSource(), 10.0);
            bundle.onDamaged(me.player(), hit);
            assertEquals(7.5, hit.getDamage(), 1e-9, "デブの軽減が効く");

            bundle.remove(me.player());
            assertFalse(me.hasAttribute(Attribute.MOVEMENT_SPEED));
            assertFalse(me.hasAttribute(Attribute.FALL_DAMAGE_MULTIPLIER));
        }

        @Test
        @DisplayName("道具は中身のぶんを全部もらえる")
        void startingItemsAreCombined() {
            ModifierBundle bundle = new ModifierBundle(registry.byId("greedy").orElseThrow(),
                    List.of(new ShieldBashModifier(), new LandmineModifier()));
            assertEquals(List.of(
                    StartingItems.Item.of(Material.SHIELD),
                    StartingItems.Item.of(Material.BOW),
                    StartingItems.Item.of(Material.ARROW, 16)), bundle.startingItems());
        }

        @Test
        @DisplayName("死を打ち消すものは、どれか 1 つが打ち消した時点で止まる")
        void deathInterceptStopsAtTheFirstSaver() {
            int[] asked = {0};
            Modifier saver = new BaseModifier("saver", "救う", Material.PAPER, "") {
                @Override
                public boolean interceptDeath(Player self, EntityDamageEvent event) {
                    asked[0]++;
                    return true;
                }
            };
            Modifier another = new BaseModifier("another", "もう1つ", Material.PAPER, "") {
                @Override
                public boolean interceptDeath(Player self, EntityDamageEvent event) {
                    asked[0] += 10;
                    return true;
                }
            };
            ModifierBundle bundle = new ModifierBundle(registry.byId("greedy").orElseThrow(),
                    List.of(saver, another));

            assertTrue(bundle.interceptDeath(me.player(), new EntityDamageEvent(me.player(),
                    DamageCause.VOID, Mocks.damageSource(), 100.0)));
            assertEquals(1, asked[0], "2 つ目には聞かない");
        }
    }

    @Nested
    @DisplayName("選択中の守り")
    class Guard {

        @Test
        @DisplayName("守ると無敵になり、やめると元に戻る")
        void protectsAndReleases() {
            SelectionGuard.protect(me.player());
            assertTrue(me.state().invulnerable);

            SelectionGuard.release(me.player());
            assertFalse(me.state().invulnerable);
        }

        @Test
        @DisplayName("守る前の状態は覚えず、やめれば必ず無敵を外す")
        void alwaysClearsOnRelease() {
            // 参加直後の isInvulnerable() はクライアント読み込み待ちで true を返すので、覚えて戻すと永久に無敵になる
            me.state().invulnerable = true;
            SelectionGuard.protect(me.player());
            SelectionGuard.release(me.player());
            assertFalse(me.state().invulnerable, "覚えた true を戻してはいけない");
        }

        @Test
        @DisplayName("二重に守っても、やめれば一度で元に戻る")
        void protectIsIdempotent() {
            SelectionGuard.protect(me.player());
            SelectionGuard.protect(me.player());
            SelectionGuard.release(me.player());
            assertFalse(me.state().invulnerable);
        }

        @Test
        @DisplayName("守っていない人の無敵には触らない")
        void releaseWithoutProtectDoesNothing() {
            me.state().invulnerable = true;
            SelectionGuard.release(me.player());
            assertTrue(me.state().invulnerable, "管理者が付けた無敵を勝手に外さない");
        }
    }
}
