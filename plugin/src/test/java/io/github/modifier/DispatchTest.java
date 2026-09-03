package io.github.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 中央のディスパッチャ。
 *
 * <p>「選んでいる人にだけ効果が届く」「致死判定の順番」「合成ダメージの番人」といった、
 * モディファイア個々ではなく配り方の側の決めごとを見る。
 */
@DisplayName("効果の配り方")
class DispatchTest {

    private World world;
    private Server server;
    private Mocks.FakePlayer me;
    private ModifierPlugin plugin;
    private SelectionStore store;
    private ModifierRegistry registry;
    private NameTagDisplay nameTag;

    @BeforeEach
    void setUp() {
        world = Mocks.world();
        server = Mocks.server(world);
        me = Mocks.player("Me", server, world);

        plugin = mock(ModifierPlugin.class);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getConfig()).thenReturn(new org.bukkit.configuration.file.YamlConfiguration());

        store = mock(SelectionStore.class);
        when(store.chargeAvailable(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        registry = new ModifierRegistry();
        nameTag = mock(NameTagDisplay.class);
    }

    private ModifierEffects effects(Random random) {
        return new ModifierEffects(plugin, registry, store, random, nameTag);
    }

    /** そのプレイヤーが選んでいることにする。 */
    private void selects(Mocks.FakePlayer player, Modifier modifier) {
        when(store.selectedId(player.player())).thenReturn(Optional.of(modifier.id()));
    }

    private void selectsNothing(Mocks.FakePlayer player) {
        when(store.selectedId(player.player())).thenReturn(Optional.empty());
    }

    private void online(Mocks.FakePlayer... players) {
        List<Player> list = new ArrayList<>();
        for (Mocks.FakePlayer player : players) {
            list.add(player.player());
        }
        org.mockito.Mockito.doReturn(list).when(server).getOnlinePlayers();
    }

    private EntityDamageEvent damage(Mocks.FakePlayer target, double amount) {
        return new EntityDamageEvent(target.player(), DamageCause.ENTITY_ATTACK,
                Mocks.damageSource(), amount);
    }

    @Nested
    @DisplayName("振り分け")
    class Routing {

        @Test
        @DisplayName("選んでいる人にだけ効果が届く")
        void onlyTheSelected() {
            registry.register(new FatModifier());
            selects(me, registry.byId("fat").orElseThrow());

            EntityDamageEvent event = damage(me, 10.0);
            effects(new Random(0)).onDamage(event);
            assertEquals(7.5, event.getDamage(), 1e-9);
        }

        @Test
        @DisplayName("何も選んでいなければ素通し")
        void nothingSelected() {
            registry.register(new FatModifier());
            selectsNothing(me);

            EntityDamageEvent event = damage(me, 10.0);
            effects(new Random(0)).onDamage(event);
            assertEquals(10.0, event.getDamage(), 1e-9, "効果は掛からない");
        }

        @Test
        @DisplayName("合成ダメージにはフックを配らない")
        void syntheticDamageIsSkipped() {
            registry.register(new FatModifier());
            selects(me, registry.byId("fat").orElseThrow());

            EntityDamageEvent event = damage(me, 10.0);
            SyntheticDamage.run(() -> effects(new Random(0)).onDamage(event));
            assertEquals(10.0, event.getDamage(), 1e-9,
                    "効果由来のダメージがまた効果を誘発してはいけない");
        }
    }

    @Nested
    @DisplayName("新しいフックの振り分け")
    class NewHooks {

        /** 呼ばれた回数を数える見張り役。 */
        private final class Recording extends BaseModifier {
            final AtomicInteger crafts = new AtomicInteger();
            final AtomicInteger clicks = new AtomicInteger();
            final AtomicInteger interacts = new AtomicInteger();

            Recording() {
                super("recording", "記録", Material.PAPER, "");
            }

            @Override
            public void onCraftPrepared(Player player, org.bukkit.event.inventory.PrepareItemCraftEvent e) {
                crafts.incrementAndGet();
            }

            @Override
            public void onInventoryClick(Player player, org.bukkit.event.inventory.InventoryClickEvent e) {
                clicks.incrementAndGet();
            }

            @Override
            public void onInteract(Player player, org.bukkit.event.player.PlayerInteractEvent e) {
                interacts.incrementAndGet();
            }
        }

        private org.bukkit.inventory.InventoryView viewOf(Mocks.FakePlayer player) {
            org.bukkit.inventory.InventoryView view = mock(org.bukkit.inventory.InventoryView.class);
            when(view.getPlayer()).thenReturn(player.player());
            when(view.getTopInventory()).thenReturn(mock(org.bukkit.inventory.CraftingInventory.class));
            return view;
        }

        @Test
        @DisplayName("クラフト・クリック・操作は選んでいる人にだけ届く")
        void routesToTheSelected() {
            Recording recording = new Recording();
            registry.register(recording);
            selects(me, recording);
            Mocks.FakePlayer other = Mocks.player("Other", server, world);
            selectsNothing(other);
            ModifierEffects effects = effects(new Random(0));

            for (Mocks.FakePlayer player : List.of(me, other)) {
                effects.onCraftPrepared(new org.bukkit.event.inventory.PrepareItemCraftEvent(
                        mock(org.bukkit.inventory.CraftingInventory.class), viewOf(player), false));
                effects.onInventoryClick(new org.bukkit.event.inventory.InventoryClickEvent(
                        viewOf(player), org.bukkit.event.inventory.InventoryType.SlotType.RESULT, 0,
                        org.bukkit.event.inventory.ClickType.LEFT,
                        org.bukkit.event.inventory.InventoryAction.PICKUP_ALL));
                effects.onInteract(new org.bukkit.event.player.PlayerInteractEvent(player.player(),
                        org.bukkit.event.block.Action.RIGHT_CLICK_AIR, null, null, org.bukkit.block.BlockFace.UP));
            }

            assertEquals(1, recording.crafts.get());
            assertEquals(1, recording.clicks.get());
            assertEquals(1, recording.interacts.get());
        }

        @Test
        @DisplayName("シェフの料理は、選んでいない人が食べても効く")
        void chefDishesWorkForEveryone() {
            Mocks.makeEdible(Material.BREAD);
            selectsNothing(me);
            ItemStack dish = ChefModifier.cook(Mocks.stampableItem(Material.BREAD, 1),
                    ChefModifier.BUFFS.get(0));

            effects(new Random(0)).onConsume(new org.bukkit.event.player.PlayerItemConsumeEvent(
                    me.player(), dish, org.bukkit.inventory.EquipmentSlot.HAND));

            assertFalse(me.state().potionEffects.isEmpty(), "料理の効果はアイテムに付いている");
        }

        @Test
        @DisplayName("掛け直すたびに下段の表示も更新する")
        void refreshesTheNameTag() {
            Modifier fat = new FatModifier();
            registry.register(fat);
            when(me.player().getScheduler()).thenReturn(
                    mock(io.papermc.paper.threadedregions.scheduler.EntityScheduler.class));
            ModifierEffects effects = effects(new Random(0));

            selects(me, fat);
            effects.apply(me.player());
            org.mockito.Mockito.verify(nameTag).show(me.player(), fat);

            selectsNothing(me);
            effects.apply(me.player());
            org.mockito.Mockito.verify(nameTag).hide(me.player());
        }
    }

    @Nested
    @DisplayName("致死判定")
    class FatalDamage {

        /** 呼ばれた回数を数える見張り役。 */
        private final class Counting extends BaseModifier {
            private final boolean saves;
            final AtomicInteger ownCalls = new AtomicInteger();
            final AtomicInteger otherCalls = new AtomicInteger();

            Counting(String id, boolean saves) {
                super(id, id, Material.PAPER, "");
                this.saves = saves;
            }

            @Override
            public boolean interceptDeath(Player self, EntityDamageEvent event) {
                ownCalls.incrementAndGet();
                return saves;
            }

            @Override
            public boolean interceptOtherDeath(Player self, Player dying, EntityDamageEvent e) {
                otherCalls.incrementAndGet();
                return saves;
            }
        }

        @Test
        @DisplayName("致死でなければ誰にも聞かない")
        void survivableDamageIsIgnored() {
            Counting own = new Counting("own", true);
            registry.register(own);
            selects(me, own);
            me.state().health = 20.0;
            online(me);

            effects(new Random(0)).onFatalDamage(damage(me, 5.0));
            assertEquals(0, own.ownCalls.get());
        }

        @Test
        @DisplayName("本人が助かるなら、周りには聞かない")
        void ownInterceptWins() {
            Counting own = new Counting("own", true);
            Counting other = new Counting("other", true);
            registry.register(own);
            registry.register(other);

            Mocks.FakePlayer helper = Mocks.player("Helper", server, world);
            selects(me, own);
            selects(helper, other);
            online(me, helper);
            me.state().health = 4.0;

            EntityDamageEvent event = damage(me, 100.0);
            effects(new Random(0)).onFatalDamage(event);

            assertTrue(event.isCancelled(), "死は打ち消される");
            assertEquals(1, own.ownCalls.get());
            assertEquals(0, other.otherCalls.get(), "本人で助かったら周りは呼ばれない");
        }

        @Test
        @DisplayName("本人が助からなければ、周りへ回る")
        void fallsBackToOthers() {
            Counting own = new Counting("own", false);
            Counting other = new Counting("other", true);
            registry.register(own);
            registry.register(other);

            Mocks.FakePlayer helper = Mocks.player("Helper", server, world);
            selects(me, own);
            selects(helper, other);
            online(me, helper);
            me.state().health = 4.0;

            EntityDamageEvent event = damage(me, 100.0);
            effects(new Random(0)).onFatalDamage(event);

            assertTrue(event.isCancelled());
            assertEquals(1, own.ownCalls.get());
            assertEquals(1, other.otherCalls.get(), "周りが救う");
        }

        @Test
        @DisplayName("誰も助けなければ普通に死ぬ")
        void nobodySaves() {
            Counting own = new Counting("own", false);
            registry.register(own);
            selects(me, own);
            online(me);
            me.state().health = 4.0;

            EntityDamageEvent event = damage(me, 100.0);
            effects(new Random(0)).onFatalDamage(event);
            assertFalse(event.isCancelled(), "打ち消さない");
        }

        @Test
        @DisplayName("死亡画面の途中のプレイヤーは救い手に数えない")
        void deadHelpersAreSkipped() {
            Counting own = new Counting("own", false);
            Counting other = new Counting("other", true);
            registry.register(own);
            registry.register(other);

            Mocks.FakePlayer corpse = Mocks.player("Corpse", server, world);
            corpse.state().health = 0.0;
            selects(me, own);
            selects(corpse, other);
            online(me, corpse);
            me.state().health = 4.0;

            EntityDamageEvent event = damage(me, 100.0);
            effects(new Random(0)).onFatalDamage(event);

            assertEquals(0, other.otherCalls.get(), "死体は救えない");
            assertFalse(event.isCancelled());
        }

        @Test
        @DisplayName("不死のトーテムを持っていたらバニラに譲る")
        void yieldsToTotem() {
            Counting own = new Counting("own", true);
            registry.register(own);
            selects(me, own);
            online(me);
            me.state().health = 4.0;
            givesTotem(me);

            EntityDamageEvent event = damage(me, 100.0);
            effects(new Random(0)).onFatalDamage(event);

            assertEquals(0, own.ownCalls.get(),
                    "トーテムで助かるはずの死で、一度きりのチャージを使ってはいけない");
            assertFalse(event.isCancelled());
        }

        @Test
        @DisplayName("トーテムが効かない死因なら、トーテムを持っていても割り込む")
        void totemDoesNotCoverVoid() {
            Counting own = new Counting("own", true);
            registry.register(own);
            selects(me, own);
            online(me);
            me.state().health = 4.0;
            givesTotem(me);

            EntityDamageEvent event = new EntityDamageEvent(me.player(), DamageCause.VOID,
                    Mocks.damageSource(), 100.0);
            effects(new Random(0)).onFatalDamage(event);

            assertEquals(1, own.ownCalls.get(), "奈落はトーテムでも助からない");
            assertTrue(event.isCancelled());
        }

        private void givesTotem(Mocks.FakePlayer player) {
            ItemStack totem = Mocks.itemStack(Material.TOTEM_OF_UNDYING, 1);
            PlayerInventory inventory = mock(PlayerInventory.class);
            when(inventory.getItemInMainHand()).thenReturn(totem);
            ItemStack empty = Mocks.itemStack(Material.AIR, 0);
            when(inventory.getItemInOffHand()).thenReturn(empty);
            when(player.player().getInventory()).thenReturn(inventory);
        }
    }
}
