package io.github.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Item;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 確率で起きる効果と、収穫まわり。
 *
 * <p>乱数は固定して、当たる場合と外れる場合の両方を通す。
 */
@DisplayName("確率と収穫の効果")
class ChanceEffectTest {

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

    @Nested
    @DisplayName("食中毒")
    class FoodPoisoning {

        private PlayerItemConsumeEvent eating(Material food) {
            return new PlayerItemConsumeEvent(me.player(),
                    Mocks.itemStack(food, 1), EquipmentSlot.HAND);
        }

        @Test
        @DisplayName("当たると空腹と吐き気になる")
        void poisons() {
            Mocks.makeEdible(Material.BREAD);
            new FoodPoisoningModifier(alwaysHits()).onConsume(me.player(), eating(Material.BREAD));

            List<PotionEffectType> types = new ArrayList<>();
            me.state().potionEffects.forEach(effect -> types.add(effect.getType()));
            assertTrue(types.contains(PotionEffectType.HUNGER), "空腹になる");
            assertTrue(types.contains(PotionEffectType.NAUSEA), "吐き気になる");
        }

        @Test
        @DisplayName("外れると何も起きない")
        void sometimesFine() {
            Mocks.makeEdible(Material.BREAD);
            new FoodPoisoningModifier(neverHits()).onConsume(me.player(), eating(Material.BREAD));
            assertTrue(me.state().potionEffects.isEmpty());
        }

        @Test
        @DisplayName("食べ物でなければ起きない")
        void onlyFood() {
            new FoodPoisoningModifier(alwaysHits())
                    .onConsume(me.player(), eating(Material.POTION));
            assertTrue(me.state().potionEffects.isEmpty(), "ポーションでは食中毒にならない");
        }
    }

    @Nested
    @DisplayName("農奴")
    class Serf {

        /** 壊されたブロックとその落とし物。 */
        private BlockDropItemEvent breaking(Material crop, boolean ripe, int amount) {
            Ageable data = mock(Ageable.class);
            when(data.getAge()).thenReturn(ripe ? 7 : 3);
            when(data.getMaximumAge()).thenReturn(7);

            BlockState state = mock(BlockState.class);
            when(state.getType()).thenReturn(crop);
            when(state.getBlockData()).thenReturn(data);

            ItemStack stack = Mocks.itemStack(crop, amount);
            Item entity = mock(Item.class);
            when(entity.getItemStack()).thenReturn(stack);

            List<Item> drops = new ArrayList<>();
            drops.add(entity);
            return new BlockDropItemEvent(mock(Block.class), state, me.player(), drops);
        }

        @Test
        @DisplayName("育ち切った作物が当たると 3倍になる")
        void triplesRipeCrops() {
            BlockDropItemEvent event = breaking(Material.WHEAT, true, 2);
            new SerfModifier(alwaysHits()).onBlockDrops(me.player(), event);

            ItemStack stack = event.getItems().get(0).getItemStack();
            org.mockito.Mockito.verify(stack).setAmount(6);
        }

        @Test
        @DisplayName("外れたら増えない")
        void sometimesNormal() {
            BlockDropItemEvent event = breaking(Material.WHEAT, true, 2);
            new SerfModifier(neverHits()).onBlockDrops(me.player(), event);

            org.mockito.Mockito.verify(event.getItems().get(0).getItemStack(),
                    org.mockito.Mockito.never()).setAmount(org.mockito.ArgumentMatchers.anyInt());
        }

        @Test
        @DisplayName("育ち切っていない作物は増えない")
        void onlyRipeCrops() {
            BlockDropItemEvent event = breaking(Material.WHEAT, false, 1);
            new SerfModifier(alwaysHits()).onBlockDrops(me.player(), event);

            org.mockito.Mockito.verify(event.getItems().get(0).getItemStack(),
                    org.mockito.Mockito.never()).setAmount(org.mockito.ArgumentMatchers.anyInt());
        }

        @Test
        @DisplayName("作物以外は増えない")
        void onlyCrops() {
            BlockDropItemEvent event = breaking(Material.STONE, true, 1);
            new SerfModifier(alwaysHits()).onBlockDrops(me.player(), event);

            org.mockito.Mockito.verify(event.getItems().get(0).getItemStack(),
                    org.mockito.Mockito.never()).setAmount(org.mockito.ArgumentMatchers.anyInt());
        }
    }

    @Nested
    @DisplayName("ピエロ")
    class Clown {

        @Test
        @DisplayName("当たると食事の満腹度回復が消える")
        void cancelsFoodRecovery() {
            me.state().foodLevel = 10;
            FoodLevelChangeEvent event =
                    new FoodLevelChangeEvent(me.player(), 16, Mocks.itemStack(Material.BREAD, 1));
            new ClownModifier(alwaysHits()).onFoodChange(me.player(), event);
            assertTrue(event.isCancelled(), "食べても回復しない");
        }

        @Test
        @DisplayName("自然減少には手を出さない")
        void leavesNaturalDrainAlone() {
            me.state().foodLevel = 10;
            // 食事由来でない (item が null) 減少
            FoodLevelChangeEvent event = new FoodLevelChangeEvent(me.player(), 9, null);
            new ClownModifier(alwaysHits()).onFoodChange(me.player(), event);
            assertFalse(event.isCancelled(), "腹が減るのは止めない");
        }

        @Test
        @DisplayName("当たると採掘したアイテムが消える")
        void losesDrops() {
            BlockState state = mock(BlockState.class);
            when(state.getType()).thenReturn(Material.STONE);
            List<Item> drops = new ArrayList<>();
            drops.add(mock(Item.class));
            BlockDropItemEvent event =
                    new BlockDropItemEvent(mock(Block.class), state, me.player(), drops);

            new ClownModifier(alwaysHits()).onBlockDrops(me.player(), event);
            assertTrue(event.getItems().isEmpty(), "ドロップが消える");
        }

        @Test
        @DisplayName("外れれば採掘したアイテムは残る")
        void keepsDropsWhenUnlucky() {
            BlockState state = mock(BlockState.class);
            when(state.getType()).thenReturn(Material.STONE);
            List<Item> drops = new ArrayList<>();
            drops.add(mock(Item.class));
            BlockDropItemEvent event =
                    new BlockDropItemEvent(mock(Block.class), state, me.player(), drops);

            new ClownModifier(neverHits()).onBlockDrops(me.player(), event);
            assertEquals(1, event.getItems().size());
        }

        @Test
        @DisplayName("当たると受ける攻撃が消える")
        void dodgesAttacks() {
            org.bukkit.event.entity.EntityDamageByEntityEvent event =
                    new org.bukkit.event.entity.EntityDamageByEntityEvent(
                            mock(org.bukkit.entity.Zombie.class), me.player(),
                            org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                            Mocks.damageSource(), 5.0);
            new ClownModifier(alwaysHits()).onDamaged(me.player(), event);
            assertTrue(event.isCancelled());
        }

        @Test
        @DisplayName("落下などの環境ダメージは消えない")
        void onlyDodgesAttacks() {
            org.bukkit.event.entity.EntityDamageEvent event =
                    new org.bukkit.event.entity.EntityDamageEvent(me.player(),
                            org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL,
                            Mocks.damageSource(), 5.0);
            new ClownModifier(alwaysHits()).onDamaged(me.player(), event);
            assertFalse(event.isCancelled(), "攻撃だけが対象");
        }
    }

    @Nested
    @DisplayName("不眠症")
    class Insomnia {

        @Test
        @DisplayName("夜を明かすと移動速度低下が付く")
        void slowsAfterSleeping() {
            new InsomniaModifier().onNightSkipped(me.player());
            assertEquals(1, me.state().potionEffects.size());
            assertEquals(PotionEffectType.SLOWNESS, me.state().potionEffects.get(0).getType());
            assertEquals(InsomniaModifier.SLOWNESS_DURATION_TICKS,
                    me.state().potionEffects.get(0).getDuration());
        }
    }
}
