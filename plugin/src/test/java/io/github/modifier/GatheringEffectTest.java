package io.github.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Silverfish;
import org.bukkit.entity.Zombie;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 釣り・採掘・肉。
 *
 * <p>どれも持ち物や落とし物を相手にするので、アイテムを差し替えて確かめる。
 */
@DisplayName("釣りと採掘と肉")
class GatheringEffectTest {

    private World world;
    private Server server;
    private Mocks.FakePlayer me;

    @BeforeEach
    void setUp() {
        world = Mocks.world();
        server = Mocks.server(world);
        me = Mocks.player("Me", server, world);
    }

    /** 必ず当たる乱数。 */
    private static Random alwaysHits() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        };
    }

    /** 絶対に当たらない乱数。 */
    private static Random neverHits() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 0.999999;
            }
        };
    }

    private static Integer lentLevel(ItemStack rod) {
        return rod.getPersistentDataContainer().get(FishersModifier.LENT_KEY, PersistentDataType.INTEGER);
    }

    @Nested
    @DisplayName("フィッシャーズ")
    class Fishers {

        private final FishersModifier fishers = new FishersModifier(alwaysHits());
        private final ItemStack[] storage = new ItemStack[36];

        @BeforeEach
        void lureGoesToThree() {
            Mocks.maxLevel(Enchantment.LURE, 3);
        }

        @Test
        @DisplayName("手に持った釣り竿に入れ食い III が付き、元のレベル 0 が印に残る")
        void lendsToTheHeldRod() {
            ItemStack rod = Mocks.richItem(Material.FISHING_ROD, 1);
            storage[0] = rod;
            Mocks.backpack(me.player(), storage, 0, null);

            fishers.tick(me.player());

            assertEquals(3, rod.getEnchantmentLevel(Enchantment.LURE));
            assertEquals(0, lentLevel(rod), "貸す前のレベルを覚えておく");
        }

        @Test
        @DisplayName("手に無い釣り竿には付かず、手から離すと自前の入れ食い I に戻る")
        void reclaimsWhenNotHeld() {
            ItemStack rod = Mocks.richItem(Material.FISHING_ROD, 1);
            rod.addUnsafeEnchantment(Enchantment.LURE, 1);
            storage[3] = rod;
            Mocks.backpack(me.player(), storage, 3, null);
            fishers.tick(me.player());
            assertEquals(3, rod.getEnchantmentLevel(Enchantment.LURE));
            assertEquals(1, lentLevel(rod));

            // 別のスロットを持つ
            Mocks.backpack(me.player(), storage, 0, null);
            fishers.tick(me.player());

            assertEquals(1, rod.getEnchantmentLevel(Enchantment.LURE), "自前の I に戻る");
            assertNull(lentLevel(rod), "印も消える");
        }

        @Test
        @DisplayName("自前で III が付いていれば触らない")
        void leavesMaxedRodsAlone() {
            ItemStack rod = Mocks.richItem(Material.FISHING_ROD, 1);
            rod.addUnsafeEnchantment(Enchantment.LURE, 3);
            storage[0] = rod;
            Mocks.backpack(me.player(), storage, 0, null);

            fishers.tick(me.player());

            assertNull(lentLevel(rod), "貸していないので印も無い");
            assertEquals(3, rod.getEnchantmentLevel(Enchantment.LURE));
        }

        @Test
        @DisplayName("オフハンドも手のうち")
        void offHandCounts() {
            ItemStack rod = Mocks.richItem(Material.FISHING_ROD, 1);
            Mocks.backpack(me.player(), storage, 0, rod);

            fishers.tick(me.player());

            assertEquals(3, rod.getEnchantmentLevel(Enchantment.LURE));
        }

        @Test
        @DisplayName("釣り竿以外には何もしない")
        void onlyRods() {
            ItemStack sword = Mocks.richItem(Material.IRON_SWORD, 1);
            storage[0] = sword;
            Mocks.backpack(me.player(), storage, 0, null);

            fishers.tick(me.player());

            assertEquals(0, sword.getEnchantmentLevel(Enchantment.LURE));
            assertNull(lentLevel(sword));
        }

        /** v0.3.2 以前の版が宝釣りを貸した釣り竿。 */
        private ItemStack oldLentRod(int ownLuck) {
            ItemStack rod = Mocks.richItem(Material.FISHING_ROD, 1);
            rod.editPersistentDataContainer(pdc ->
                    pdc.set(FishersModifier.OLD_LENT_KEY, PersistentDataType.INTEGER, ownLuck));
            rod.addUnsafeEnchantment(Enchantment.LUCK_OF_THE_SEA, 3);
            return rod;
        }

        @Test
        @DisplayName("宝釣りを貸したままの釣り竿は、手にあれば入れ食いに貸し替わる")
        void migratesHeldRod() {
            ItemStack rod = oldLentRod(0);
            storage[0] = rod;
            Mocks.backpack(me.player(), storage, 0, null);

            fishers.tick(me.player());

            assertEquals(0, rod.getEnchantmentLevel(Enchantment.LUCK_OF_THE_SEA), "古い貸し出しは返す");
            assertNull(rod.getPersistentDataContainer()
                    .get(FishersModifier.OLD_LENT_KEY, PersistentDataType.INTEGER), "古い印も消える");
            assertEquals(3, rod.getEnchantmentLevel(Enchantment.LURE));
            assertEquals(0, lentLevel(rod));
        }

        @Test
        @DisplayName("宝釣りを貸したままの釣り竿は、手に無ければ自前のレベルへ戻る")
        void migratesStowedRod() {
            ItemStack rod = oldLentRod(1);
            storage[3] = rod;
            Mocks.backpack(me.player(), storage, 0, null);

            fishers.tick(me.player());

            assertEquals(1, rod.getEnchantmentLevel(Enchantment.LUCK_OF_THE_SEA), "自前の宝釣り I に戻る");
            assertEquals(0, rod.getEnchantmentLevel(Enchantment.LURE), "手に無いので入れ食いは付かない");
            assertNull(rod.getPersistentDataContainer()
                    .get(FishersModifier.OLD_LENT_KEY, PersistentDataType.INTEGER));
            assertNull(lentLevel(rod));
        }

        private ItemStack lentRod() {
            ItemStack rod = Mocks.richItem(Material.FISHING_ROD, 1);
            assertTrue(FishersModifier.lend(rod));
            return rod;
        }

        @Test
        @DisplayName("捨てると外れる")
        void reclaimsOnDrop() {
            ItemStack rod = lentRod();
            Item drop = mock(Item.class);
            when(drop.getItemStack()).thenReturn(rod);

            fishers.onItemDrop(me.player(), new PlayerDropItemEvent(me.player(), drop));

            assertEquals(0, rod.getEnchantmentLevel(Enchantment.LURE));
            verify(drop).setItemStack(rod);
        }

        @Test
        @DisplayName("インベントリでクリックした時点で外れる (別の箱へ移る前)")
        void reclaimsOnClick() {
            ItemStack rod = lentRod();
            InventoryView view = mock(InventoryView.class);
            when(view.getPlayer()).thenReturn(me.player());
            when(view.getTopInventory()).thenReturn(mock(org.bukkit.inventory.Inventory.class));
            when(view.getItem(5)).thenReturn(rod);
            InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER,
                    5, ClickType.LEFT, InventoryAction.PICKUP_ALL);

            fishers.onInventoryClick(me.player(), event);

            assertEquals(0, rod.getEnchantmentLevel(Enchantment.LURE));
            assertNull(lentLevel(rod));
        }

        @Test
        @DisplayName("死んで落とす釣り竿からも外れる")
        void reclaimsOnDeath() {
            ItemStack rod = lentRod();
            PlayerDeathEvent death = mock(PlayerDeathEvent.class);
            when(death.getDrops()).thenReturn(List.of(rod));

            fishers.onDeath(me.player(), death);

            assertEquals(0, rod.getEnchantmentLevel(Enchantment.LURE));
        }

        @Test
        @DisplayName("効果を外すと、手にある釣り竿からも返してもらう")
        void removeReclaimsEverything() {
            ItemStack held = lentRod();
            ItemStack off = lentRod();
            storage[0] = held;
            Mocks.backpack(me.player(), storage, 0, off);

            fishers.remove(me.player());

            assertEquals(0, held.getEnchantmentLevel(Enchantment.LURE));
            assertEquals(0, off.getEnchantmentLevel(Enchantment.LURE));
        }

        @Test
        @DisplayName("釣り竿の耐久値は減らない")
        void rodNeverBreaks() {
            ItemStack rod = Mocks.itemStack(Material.FISHING_ROD, 1);
            PlayerItemDamageEvent event = new PlayerItemDamageEvent(me.player(), rod, 1, 1);
            fishers.onItemDamage(me.player(), event);
            assertTrue(event.isCancelled());

            PlayerItemDamageEvent sword = new PlayerItemDamageEvent(me.player(),
                    Mocks.itemStack(Material.IRON_SWORD, 1), 1, 1);
            fishers.onItemDamage(me.player(), sword);
            assertFalse(sword.isCancelled(), "釣り竿だけ");
        }

        /** 魚が釣れた。釣れた物はアイテムとして水面に居る。 */
        private PlayerFishEvent caught(Item fish, PlayerFishEvent.State state) {
            return new PlayerFishEvent(me.player(), fish, mock(FishHook.class), state);
        }

        private Item fishAt(double x, double z) {
            Item fish = mock(Item.class);
            when(fish.getLocation()).thenReturn(new Location(world, x, 63, z));
            return fish;
        }

        @Test
        @DisplayName("当たると釣れた物が消え、代わりにシルバーフィッシュが釣り人へ飛んでくる")
        void hooksASilverfish() {
            Item fish = fishAt(10, 0);
            Silverfish silverfish = mock(Silverfish.class);
            when(world.spawn(any(Location.class), eq(Silverfish.class), any(Consumer.class)))
                    .thenAnswer(i -> {
                        @SuppressWarnings("unchecked")
                        Consumer<Silverfish> setup = i.getArgument(2);
                        setup.accept(silverfish);
                        return silverfish;
                    });

            new FishersModifier(alwaysHits()).onFish(me.player(),
                    caught(fish, PlayerFishEvent.State.CAUGHT_FISH));

            verify(fish).remove();
            ArgumentCaptor<Vector> velocity = ArgumentCaptor.forClass(Vector.class);
            verify(silverfish).setVelocity(velocity.capture());
            assertTrue(velocity.getValue().getX() < 0, "釣り人 (原点) の方向へ飛ぶ");
            assertTrue(velocity.getValue().getY() > 0, "少し上へ放る");
        }

        @Test
        @DisplayName("外れれば普通に釣れる")
        void usuallyJustFish() {
            Item fish = fishAt(10, 0);
            new FishersModifier(neverHits()).onFish(me.player(),
                    caught(fish, PlayerFishEvent.State.CAUGHT_FISH));

            verify(fish, never()).remove();
            verify(world, never()).spawn(any(Location.class), eq(Silverfish.class), any(Consumer.class));
        }

        @Test
        @DisplayName("投げた・掛かった段階では何も起きない")
        void onlyWhenCaught() {
            Item fish = fishAt(10, 0);
            for (PlayerFishEvent.State state : new PlayerFishEvent.State[] {
                    PlayerFishEvent.State.FISHING, PlayerFishEvent.State.BITE,
                    PlayerFishEvent.State.REEL_IN, PlayerFishEvent.State.FAILED_ATTEMPT}) {
                new FishersModifier(alwaysHits()).onFish(me.player(), caught(fish, state));
            }
            verify(fish, never()).remove();
        }

        @Test
        @DisplayName("釣り人へ飛ぶ勢いはバニラの魚と同じ式")
        void pullFollowsVanilla() {
            Vector pull = FishersModifier.pullTowards(
                    new Location(world, 0, 64, 0), new Location(world, 10, 64, 0));
            assertEquals(1.0, pull.getX(), 1e-9);
            assertEquals(Math.sqrt(10) * 0.08, pull.getY(), 1e-9);
            assertEquals(0.0, pull.getZ(), 1e-9);
        }

        @Test
        @DisplayName("釣り竿を 1 本もらえる")
        void givesARod() {
            assertEquals(List.of(StartingItems.Item.of(Material.FISHING_ROD)), fishers.startingItems());
        }
    }

    @Nested
    @DisplayName("採掘師")
    class Miner {

        private final MinerModifier miner = new MinerModifier();
        private final ItemStack[] storage = new ItemStack[36];

        /** 鉱石を壊した。幸運付きで引き直すと {@code luckyDrops} が落ちる。 */
        private BlockDropItemEvent breaking(Material ore, ItemStack dropped, List<ItemStack> luckyDrops) {
            BlockState state = mock(BlockState.class);
            when(state.getType()).thenReturn(ore);
            when(state.getDrops(any(ItemStack.class), any(Entity.class))).thenReturn(luckyDrops);

            Item entity = mock(Item.class);
            when(entity.getItemStack()).thenReturn(dropped);
            List<Item> drops = new ArrayList<>();
            drops.add(entity);
            return new BlockDropItemEvent(mock(Block.class), state, me.player(), drops);
        }

        private ItemStack holding(Material tool) {
            ItemStack stack = Mocks.richItem(tool, 1);
            storage[0] = stack;
            Mocks.backpack(me.player(), storage, 0, null);
            return stack;
        }

        @Test
        @DisplayName("鉱石の落とし物が、幸運 II を付けた道具で引き直した個数になる")
        void redrawsWithFortuneTwo() {
            ItemStack pickaxe = holding(Material.IRON_PICKAXE);
            ItemStack diamond = Mocks.richItem(Material.DIAMOND, 1);
            BlockDropItemEvent event = breaking(Material.DIAMOND_ORE, diamond,
                    List.of(Mocks.richItem(Material.DIAMOND, 3)));

            miner.onBlockDrops(me.player(), event);

            assertEquals(3, diamond.getAmount());
            verify(event.getItems().get(0)).setItemStack(diamond);

            ArgumentCaptor<ItemStack> used = ArgumentCaptor.forClass(ItemStack.class);
            verify(event.getBlockState()).getDrops(used.capture(), eq(me.player()));
            assertEquals(2, used.getValue().getEnchantmentLevel(Enchantment.FORTUNE),
                    "幸運 II を付けた道具で引く");
            assertEquals(0, pickaxe.getEnchantmentLevel(Enchantment.FORTUNE),
                    "手にある道具そのものは変えない");
        }

        @Test
        @DisplayName("幸運 I の道具は II に上がるが、III の道具はそのまま")
        void onlyRaisesToTwo() {
            ItemStack one = holding(Material.IRON_PICKAXE);
            one.addUnsafeEnchantment(Enchantment.FORTUNE, 1);
            BlockDropItemEvent lower = breaking(Material.IRON_ORE, Mocks.richItem(Material.RAW_IRON, 1),
                    List.of(Mocks.richItem(Material.RAW_IRON, 2)));
            miner.onBlockDrops(me.player(), lower);
            verify(lower.getBlockState()).getDrops(any(ItemStack.class), any(Entity.class));

            ItemStack three = holding(Material.DIAMOND_PICKAXE);
            three.addUnsafeEnchantment(Enchantment.FORTUNE, 3);
            BlockDropItemEvent higher = breaking(Material.IRON_ORE, Mocks.richItem(Material.RAW_IRON, 1),
                    List.of(Mocks.richItem(Material.RAW_IRON, 2)));
            miner.onBlockDrops(me.player(), higher);
            verify(higher.getBlockState(), never()).getDrops(any(ItemStack.class), any(Entity.class));
        }

        @Test
        @DisplayName("シルクタッチの道具には触らない")
        void leavesSilkTouchAlone() {
            holding(Material.IRON_PICKAXE).addUnsafeEnchantment(Enchantment.SILK_TOUCH, 1);
            BlockDropItemEvent event = breaking(Material.DIAMOND_ORE,
                    Mocks.richItem(Material.DIAMOND_ORE, 1), List.of());

            miner.onBlockDrops(me.player(), event);

            verify(event.getBlockState(), never()).getDrops(any(ItemStack.class), any(Entity.class));
        }

        @Test
        @DisplayName("鉱石以外と素手は対象外")
        void onlyOresWithATool() {
            holding(Material.IRON_PICKAXE);
            BlockDropItemEvent stone = breaking(Material.STONE, Mocks.richItem(Material.COBBLESTONE, 1),
                    List.of());
            miner.onBlockDrops(me.player(), stone);
            verify(stone.getBlockState(), never()).getDrops(any(ItemStack.class), any(Entity.class));

            storage[0] = null;
            Mocks.backpack(me.player(), storage, 0, null);
            BlockDropItemEvent bareHanded = breaking(Material.COAL_ORE, Mocks.richItem(Material.COAL, 1),
                    List.of());
            miner.onBlockDrops(me.player(), bareHanded);
            verify(bareHanded.getBlockState(), never()).getDrops(any(ItemStack.class), any(Entity.class));
        }

        /** 作物を壊した。落とし物は複数のアイテムエンティティ。 */
        private BlockDropItemEvent cropBreak(Material crop, ItemStack... dropped) {
            BlockState state = mock(BlockState.class);
            when(state.getType()).thenReturn(crop);
            List<Item> drops = new ArrayList<>();
            for (ItemStack stack : dropped) {
                Item entity = mock(Item.class);
                when(entity.getItemStack()).thenReturn(stack);
                drops.add(entity);
            }
            return new BlockDropItemEvent(mock(Block.class), state, me.player(), drops);
        }

        private List<Material> typesOf(BlockDropItemEvent event) {
            return event.getItems().stream().map(item -> item.getItemStack().getType()).toList();
        }

        @Test
        @DisplayName("小麦とビートルートは収穫物が消えて種だけ残る")
        void wheatLeavesOnlySeeds() {
            BlockDropItemEvent wheat = cropBreak(Material.WHEAT,
                    Mocks.richItem(Material.WHEAT, 2), Mocks.richItem(Material.WHEAT_SEEDS, 3));
            miner.onBlockDrops(me.player(), wheat);
            assertEquals(List.of(Material.WHEAT_SEEDS), typesOf(wheat));

            BlockDropItemEvent beetroot = cropBreak(Material.BEETROOTS,
                    Mocks.richItem(Material.BEETROOT, 1), Mocks.richItem(Material.BEETROOT_SEEDS, 2));
            miner.onBlockDrops(me.player(), beetroot);
            assertEquals(List.of(Material.BEETROOT_SEEDS), typesOf(beetroot));
        }

        @Test
        @DisplayName("ジャガイモ・ニンジン・ネザーウォート・カカオは収穫物が 1 個だけ残る")
        void selfSeedingCropsKeepOne() {
            ItemStack potatoes = Mocks.richItem(Material.POTATO, 4);
            BlockDropItemEvent potato = cropBreak(Material.POTATOES,
                    potatoes, Mocks.richItem(Material.POISONOUS_POTATO, 1));
            miner.onBlockDrops(me.player(), potato);
            assertEquals(List.of(Material.POTATO), typesOf(potato), "青くなったジャガイモは種にならない");
            assertEquals(1, potatoes.getAmount());

            ItemStack wart = Mocks.richItem(Material.NETHER_WART, 4);
            miner.onBlockDrops(me.player(), cropBreak(Material.NETHER_WART, wart));
            assertEquals(1, wart.getAmount());

            ItemStack beans = Mocks.richItem(Material.COCOA_BEANS, 3);
            miner.onBlockDrops(me.player(), cropBreak(Material.COCOA, beans));
            assertEquals(1, beans.getAmount());
        }

        @Test
        @DisplayName("スイートベリーは右クリック収穫でも 1 個だけ")
        void sweetBerriesKeepOne() {
            ItemStack berries = Mocks.richItem(Material.SWEET_BERRIES, 3);
            List<ItemStack> harvested = new ArrayList<>(List.of(berries));
            miner.onHarvest(me.player(), new org.bukkit.event.player.PlayerHarvestBlockEvent(
                    me.player(), mock(Block.class), harvested));
            assertEquals(1, berries.getAmount());
        }

        private org.bukkit.potion.PotionEffect hasteAfterEating(int nutrition) {
            MinerModifier fed = new MinerModifier(item -> nutrition);
            fed.onConsume(me.player(), new PlayerItemConsumeEvent(me.player(),
                    Mocks.itemStack(Material.BREAD, 1), EquipmentSlot.HAND));
            assertEquals(1, me.state().potionEffects.size(), "採掘速度上昇が 1 つ付く");
            org.bukkit.potion.PotionEffect effect = me.state().potionEffects.remove(0);
            assertEquals(PotionEffectType.HASTE, effect.getType());
            return effect;
        }

        @Test
        @DisplayName("食べ物の満腹度回復量で採掘速度上昇の強さと長さが決まる")
        void hasteDependsOnTheMeal() {
            org.bukkit.potion.PotionEffect light = hasteAfterEating(3);
            assertEquals(0, light.getAmplifier(), "3 以下は I");
            assertEquals(MinerModifier.LIGHT_MEAL_HASTE_TICKS, light.getDuration());

            org.bukkit.potion.PotionEffect meal = hasteAfterEating(4);
            assertEquals(0, meal.getAmplifier(), "4〜7 は I");
            assertEquals(MinerModifier.MEAL_HASTE_TICKS, meal.getDuration());
            assertEquals(MinerModifier.MEAL_HASTE_TICKS, hasteAfterEating(7).getDuration());

            org.bukkit.potion.PotionEffect feast = hasteAfterEating(8);
            assertEquals(1, feast.getAmplifier(), "8 以上は II");
            assertEquals(MinerModifier.FEAST_HASTE_TICKS, feast.getDuration());
        }

        @Test
        @DisplayName("食べ物でないものを飲んでも何も付かない")
        void drinksGiveNothing() {
            new MinerModifier(item -> -1).onConsume(me.player(), new PlayerItemConsumeEvent(
                    me.player(), Mocks.itemStack(Material.POTION, 1), EquipmentSlot.HAND));
            assertTrue(me.state().potionEffects.isEmpty());
        }

        private void lookingAt(Material type) {
            Block block = mock(Block.class);
            when(block.getType()).thenReturn(type);
            when(me.player().getTargetBlockExact(MinerModifier.LOG_REACH)).thenReturn(block);
        }

        @Test
        @DisplayName("原木を向いている間だけ採掘速度が半分になる")
        void slowsOnlyWhileFacingLogs() {
            lookingAt(Material.OAK_LOG);
            miner.tick(me.player());
            assertEquals(MinerModifier.LOG_SPEED_MULTIPLIER,
                    me.scalarOn(org.bukkit.attribute.Attribute.BLOCK_BREAK_SPEED), 1e-9);

            lookingAt(Material.STONE);
            miner.tick(me.player());
            assertFalse(me.hasAttribute(org.bukkit.attribute.Attribute.BLOCK_BREAK_SPEED), "石を向けば元どおり");

            lookingAt(Material.STRIPPED_SPRUCE_LOG);
            miner.tick(me.player());
            miner.remove(me.player());
            assertFalse(me.hasAttribute(org.bukkit.attribute.Attribute.BLOCK_BREAK_SPEED), "外せば残らない");
        }

        @Test
        @DisplayName("原木の顔ぶれはバニラの #logs と同じ")
        void logsAreCovered() {
            for (Material log : new Material[] {Material.OAK_LOG, Material.STRIPPED_OAK_LOG, Material.OAK_WOOD,
                    Material.STRIPPED_DARK_OAK_WOOD, Material.CRIMSON_STEM, Material.STRIPPED_WARPED_STEM,
                    Material.WARPED_HYPHAE, Material.BAMBOO_BLOCK, Material.STRIPPED_BAMBOO_BLOCK,
                    Material.PALE_OAK_LOG}) {
                assertTrue(MinerModifier.LOGS.contains(log), log + " が原木になっていない");
            }
            for (Material other : new Material[] {Material.MELON_STEM, Material.PUMPKIN_STEM,
                    Material.ATTACHED_MELON_STEM, Material.MUSHROOM_STEM, Material.BIG_DRIPLEAF_STEM,
                    Material.OAK_PLANKS, Material.OAK_LEAVES}) {
                assertFalse(MinerModifier.LOGS.contains(other), other + " は原木ではない");
            }
        }

        @Test
        @DisplayName("主要な鉱石が対象に入っている")
        void oresAreCovered() {
            assertTrue(MinerModifier.ORES.contains(Material.DIAMOND_ORE));
            assertTrue(MinerModifier.ORES.contains(Material.DEEPSLATE_DIAMOND_ORE));
            assertTrue(MinerModifier.ORES.contains(Material.NETHER_QUARTZ_ORE));
            assertFalse(MinerModifier.ORES.contains(Material.ANCIENT_DEBRIS), "古代の残骸に幸運は効かない");
            assertFalse(MinerModifier.ORES.contains(Material.STONE));
        }
    }

    @Nested
    @DisplayName("熱血お肉屋さん")
    class Butcher {

        private final ButcherModifier butcher = new ButcherModifier(alwaysHits());

        @BeforeEach
        void foods() {
            Mocks.makeEdible(Material.BREAD);
            Mocks.makeEdible(Material.COOKED_BEEF);
            Mocks.makeEdible(Material.CHICKEN);
            Mocks.makeEdible(Material.ROTTEN_FLESH);
        }

        private EntityDeathEvent killed(Class<? extends org.bukkit.entity.LivingEntity> type,
                ItemStack... drops) {
            List<ItemStack> list = new ArrayList<>(List.of(drops));
            return new EntityDeathEvent(mock(type), Mocks.damageSource(), list);
        }

        @Test
        @DisplayName("動物の肉は焼けた状態で 1 個多く落ち、肉以外はそのまま")
        void cooksAndAddsOne() {
            ItemStack beef = Mocks.richItem(Material.BEEF, 1);
            ItemStack leather = Mocks.richItem(Material.LEATHER, 1);
            EntityDeathEvent event = killed(Cow.class, beef, leather);

            butcher.onKillDrops(me.player(), event);

            assertEquals(Material.COOKED_BEEF, event.getDrops().get(0).getType());
            assertEquals(2, event.getDrops().get(0).getAmount());
            assertSame(leather, event.getDrops().get(1));
            assertEquals(1, leather.getAmount());
        }

        @Test
        @DisplayName("燃えて焼けていた肉も 1 個増える")
        void alreadyCookedStillGetsOne() {
            ItemStack steak = Mocks.richItem(Material.COOKED_BEEF, 1);
            EntityDeathEvent event = killed(Cow.class, steak);

            butcher.onKillDrops(me.player(), event);

            assertSame(steak, event.getDrops().get(0));
            assertEquals(2, steak.getAmount());
        }

        @Test
        @DisplayName("動物でなければ増えない")
        void onlyAnimals() {
            ItemStack flesh = Mocks.richItem(Material.ROTTEN_FLESH, 1);
            EntityDeathEvent event = killed(Zombie.class, flesh);

            butcher.onKillDrops(me.player(), event);

            assertEquals(1, flesh.getAmount());
        }

        private PlayerItemConsumeEvent eating(Material food) {
            return new PlayerItemConsumeEvent(me.player(), Mocks.itemStack(food, 1), EquipmentSlot.HAND);
        }

        private List<PotionEffectType> effects() {
            List<PotionEffectType> types = new ArrayList<>();
            me.state().potionEffects.forEach(effect -> types.add(effect.getType()));
            return types;
        }

        @Test
        @DisplayName("肉以外を食べると空腹と吐き気になり、当たれば毒 II も付く")
        void allergyWithPoison() {
            butcher.onConsume(me.player(), eating(Material.BREAD));

            assertTrue(effects().contains(PotionEffectType.HUNGER));
            assertTrue(effects().contains(PotionEffectType.NAUSEA));
            PotionEffect poison = me.state().potionEffects.stream()
                    .filter(effect -> effect.getType().equals(PotionEffectType.POISON))
                    .findFirst().orElseThrow(() -> new AssertionError("毒が付いていない"));
            assertEquals(ButcherModifier.POISON_AMPLIFIER, poison.getAmplifier(), "毒 II");
        }

        @Test
        @DisplayName("外れれば毒は付かないが、空腹と吐き気は必ず")
        void allergyWithoutPoison() {
            new ButcherModifier(neverHits()).onConsume(me.player(), eating(Material.BREAD));

            assertTrue(effects().contains(PotionEffectType.HUNGER));
            assertTrue(effects().contains(PotionEffectType.NAUSEA));
            assertFalse(effects().contains(PotionEffectType.POISON));
        }

        @Test
        @DisplayName("肉なら何も起きない。食べ物でないものも")
        void meatIsFine() {
            butcher.onConsume(me.player(), eating(Material.COOKED_BEEF));
            butcher.onConsume(me.player(), eating(Material.CHICKEN));
            butcher.onConsume(me.player(), eating(Material.ROTTEN_FLESH));
            butcher.onConsume(me.player(), eating(Material.POTION));
            assertTrue(me.state().potionEffects.isEmpty());
        }

        private EntityPotionEffectEvent hungerFrom(EntityPotionEffectEvent.Cause cause) {
            return new EntityPotionEffectEvent(me.player(), null,
                    new PotionEffect(PotionEffectType.HUNGER, 30 * 20, 0), null, cause,
                    EntityPotionEffectEvent.Action.ADDED, false);
        }

        @Test
        @DisplayName("腐った肉を食べた直後の食べ物由来の空腹は止める")
        void rottenFleshDoesNotStarve() {
            butcher.onConsume(me.player(), eating(Material.ROTTEN_FLESH));
            EntityPotionEffectEvent event = hungerFrom(EntityPotionEffectEvent.Cause.FOOD);

            butcher.onPotionEffect(me.player(), event);

            assertTrue(event.isCancelled());
        }

        @Test
        @DisplayName("他の物を食べた後の空腹や、食べ物由来でない空腹は止めない")
        void otherHungerStays() {
            butcher.onConsume(me.player(), eating(Material.CHICKEN));
            EntityPotionEffectEvent fromChicken = hungerFrom(EntityPotionEffectEvent.Cause.FOOD);
            butcher.onPotionEffect(me.player(), fromChicken);
            assertFalse(fromChicken.isCancelled(), "生の鶏肉の空腹はそのまま");

            butcher.onConsume(me.player(), eating(Material.ROTTEN_FLESH));
            EntityPotionEffectEvent fromPlugin = hungerFrom(EntityPotionEffectEvent.Cause.PLUGIN);
            butcher.onPotionEffect(me.player(), fromPlugin);
            assertFalse(fromPlugin.isCancelled(), "食べ物由来でなければ触らない");
        }

        @Test
        @DisplayName("肉の一覧に生・焼き・魚・腐った肉が入っている")
        void meatsAreCovered() {
            for (Material meat : new Material[] {Material.BEEF, Material.COOKED_BEEF, Material.PORKCHOP,
                    Material.COOKED_CHICKEN, Material.MUTTON, Material.RABBIT, Material.COD,
                    Material.COOKED_SALMON, Material.TROPICAL_FISH, Material.PUFFERFISH,
                    Material.ROTTEN_FLESH, Material.RABBIT_STEW}) {
                assertTrue(ButcherModifier.MEATS.contains(meat), meat + " が肉になっていない");
            }
            assertFalse(ButcherModifier.MEATS.contains(Material.BREAD));
            assertFalse(ButcherModifier.MEATS.contains(Material.APPLE));
        }
    }
}
