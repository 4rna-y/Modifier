package io.github.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 調理と歌。
 *
 * <p>どちらもインベントリやブロックを相手にするので、その周辺を差し替えて確かめる。
 */
@DisplayName("調理と歌")
class ChefAndDivaTest {

    private World world;
    private Server server;
    private Mocks.FakePlayer me;

    @BeforeEach
    void setUp() {
        world = Mocks.world();
        server = Mocks.server(world);
        me = Mocks.player("Me", server, world);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Nested
    @DisplayName("シェフ")
    class Chef {

        /** 常に同じ値を返す乱数。確率の判定と効果の添字の両方に使われる。 */
        private static Random always(int value) {
            return new Random() {
                @Override
                public int nextInt(int bound) {
                    return value;
                }
            };
        }

        /** 呼ばれるたびに次の値を返す乱数 (最後の値を繰り返す)。 */
        private static Random sequence(int... values) {
            return new Random() {
                private int next;

                @Override
                public int nextInt(int bound) {
                    return values[Math.min(next++, values.length - 1)];
                }
            };
        }

        private final ChefModifier chef = new ChefModifier();
        private final ChefModifier.Buff first = ChefModifier.EFFECTS.get(0);

        @BeforeEach
        void foods() {
            Mocks.makeEdible(Material.BREAD);
            Mocks.makeEdible(Material.COOKED_BEEF);
            TestRegistryAccess.override(Material.BREAD.asItemType(), "translationKey",
                    "item.minecraft.bread");
            TestRegistryAccess.override(Material.COOKED_BEEF.asItemType(), "translationKey",
                    "item.minecraft.cooked_beef");
        }

        /** 作業台の完成品が組まれた。 */
        private PrepareItemCraftEvent crafting(ItemStack result, boolean hasRecipe) {
            CraftingInventory inventory = mock(CraftingInventory.class);
            when(inventory.getRecipe()).thenReturn(hasRecipe ? mock(Recipe.class) : null);
            when(inventory.getResult()).thenReturn(result);
            InventoryView view = mock(InventoryView.class);
            when(view.getPlayer()).thenReturn(me.player());
            when(view.getTopInventory()).thenReturn(inventory);
            return new PrepareItemCraftEvent(inventory, view, false);
        }

        /** 上側のインベントリの {@code slot} をクリックした。 */
        private InventoryClickEvent click(Inventory top, InventoryType.SlotType type, int slot,
                ItemStack item) {
            InventoryView view = mock(InventoryView.class);
            when(view.getPlayer()).thenReturn(me.player());
            when(view.getTopInventory()).thenReturn(top);
            when(view.getInventory(anyInt())).thenReturn(top);
            when(view.getItem(slot)).thenReturn(item);
            return new InventoryClickEvent(view, type, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        }

        @Test
        @DisplayName("パンを作ると料理になる (効果はまだ決まらない)")
        void breadBecomesADish() {
            ItemStack bread = Mocks.stampableItem(Material.BREAD, 1);
            PrepareItemCraftEvent event = crafting(bread, true);

            chef.onCraftPrepared(me.player(), event);

            ArgumentCaptor<ItemStack> result = ArgumentCaptor.forClass(ItemStack.class);
            verify(event.getInventory()).setResult(result.capture());
            assertTrue(ChefModifier.isDish(result.getValue()), "印が付いている");
        }

        @Test
        @DisplayName("名前は「シェフの作った○○」、説明欄に確率が載る")
        void namedAndDescribed() {
            ItemStack bread = Mocks.stampableItem(Material.BREAD, 1);
            chef.onCraftPrepared(me.player(), crafting(bread, true));

            ItemMeta meta = bread.getItemMeta();
            ArgumentCaptor<Component> name = ArgumentCaptor.forClass(Component.class);
            verify(meta).displayName(name.capture());
            assertTrue(plain(name.getValue()).startsWith("シェフの作った"), plain(name.getValue()));
            assertTrue(name.getValue().children().stream().anyMatch(child ->
                    child instanceof TranslatableComponent t && t.key().equals("item.minecraft.bread")),
                    "アイテム名はクライアントの言語で出す");

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<Component>> lore = ArgumentCaptor.forClass(List.class);
            verify(meta).lore(lore.capture());
            assertTrue(plain(lore.getValue().get(0)).contains(ChefModifier.EFFECT_PERCENT + "%"),
                    "確率: " + plain(lore.getValue().get(0)));
            verify(bread).setItemMeta(meta);
        }

        @Test
        @DisplayName("食べ物でない完成品には何もしない")
        void onlyFood() {
            PrepareItemCraftEvent event = crafting(Mocks.stampableItem(Material.STICK, 4), true);
            chef.onCraftPrepared(me.player(), event);
            verify(event.getInventory(), never()).setResult(any());
        }

        @Test
        @DisplayName("かまどの完成品を取り出すと料理になる")
        void furnaceOutputBecomesADish() {
            ItemStack beef = Mocks.stampableItem(Material.COOKED_BEEF, 3);
            InventoryClickEvent event = click(mock(FurnaceInventory.class),
                    InventoryType.SlotType.RESULT, 2, beef);

            chef.onInventoryClick(me.player(), event);

            ArgumentCaptor<ItemStack> placed = ArgumentCaptor.forClass(ItemStack.class);
            verify(event.getView()).setItem(eq(2), placed.capture());
            assertTrue(ChefModifier.isDish(placed.getValue()));
        }

        @Test
        @DisplayName("かまどでも完成品スロット以外は触らない")
        void onlyTheResultSlot() {
            ItemStack beef = Mocks.stampableItem(Material.COOKED_BEEF, 1);
            InventoryClickEvent event = click(mock(FurnaceInventory.class),
                    InventoryType.SlotType.CONTAINER, 0, beef);
            chef.onInventoryClick(me.player(), event);
            verify(event.getView(), never()).setItem(anyInt(), any());
            assertFalse(ChefModifier.isDish(beef));
        }

        @Test
        @DisplayName("作業台の完成品スロットのクリックは (プレビューで済むので) 触らない")
        void craftingTableIsHandledByPreview() {
            ItemStack bread = Mocks.stampableItem(Material.BREAD, 1);
            InventoryClickEvent event = click(mock(CraftingInventory.class),
                    InventoryType.SlotType.RESULT, 0, bread);
            chef.onInventoryClick(me.player(), event);
            verify(event.getView(), never()).setItem(anyInt(), any());
        }

        @Test
        @DisplayName("すでに料理なら触らない")
        void doesNotRecookADish() {
            ItemStack beef = ChefModifier.cook(Mocks.stampableItem(Material.COOKED_BEEF, 1));
            InventoryClickEvent event = click(mock(FurnaceInventory.class),
                    InventoryType.SlotType.RESULT, 2, beef);
            chef.onInventoryClick(me.player(), event);
            verify(event.getView(), never()).setItem(anyInt(), any());
            chef.onCraftPrepared(me.player(), crafting(beef, true));
        }

        @Test
        @DisplayName("食べたときに効果が決まり、誰が食べても掛かる")
        void eatingRollsTheBuff() {
            Mocks.FakePlayer guest = Mocks.player("Guest", server, world);
            ItemStack dish = ChefModifier.cook(Mocks.stampableItem(Material.BREAD, 1));

            ChefModifier.Buff buff = ChefModifier.serve(guest.player(), dish, always(0));

            assertSame(first, buff);
            assertEquals(1, guest.state().potionEffects.size());
            PotionEffect effect = guest.state().potionEffects.get(0);
            assertSame(first.type(), effect.getType());
            assertEquals(first.durationTicks(), effect.getDuration());
        }

        @Test
        @DisplayName("外れ (90%) は何も起きない")
        void sometimesNothing() {
            ItemStack dish = ChefModifier.cook(Mocks.stampableItem(Material.BREAD, 1));
            assertNull(ChefModifier.serve(me.player(), dish, always(ChefModifier.EFFECT_PERCENT)));
            assertTrue(me.state().potionEffects.isEmpty());
        }

        @Test
        @DisplayName("悪い効果も出る")
        void debuffs() {
            ItemStack dish = ChefModifier.cook(Mocks.stampableItem(Material.BREAD, 1));
            // 1 回目 (確率の判定) は 0 で当たり、2 回目 (添字) で最初の悪い効果
            ChefModifier.Buff buff = ChefModifier.serve(me.player(), dish, sequence(0, ChefModifier.BUFFS.size()));
            assertNotNull(buff);
            assertTrue(buff.debuff());
            assertSame(ChefModifier.DEBUFFS.get(0), buff);
            assertEquals(1, me.state().potionEffects.size());
        }

        @Test
        @DisplayName("料理でなければ何も掛からない")
        void plainFoodDoesNothing() {
            assertNull(ChefModifier.serve(me.player(), Mocks.itemStack(Material.BREAD, 1), always(0)));
            assertTrue(me.state().potionEffects.isEmpty());
        }

        @Test
        @DisplayName("効能の表記は「名前 (分:秒)」")
        void labelFormat() {
            ChefModifier.Buff buff = new ChefModifier.Buff("x", PotionEffectType.SPEED, "移動速度上昇", 90 * 20);
            assertEquals("移動速度上昇 (1:30)", buff.label());
            assertEquals("移動速度上昇 (0:05)", new ChefModifier.Buff("x", PotionEffectType.SPEED,
                    "移動速度上昇", 5 * 20).label());
        }

        @Test
        @DisplayName("効果の id は重複しない。良い効果と悪い効果の両方を含み、45 秒以内")
        void buffIdsAreUnique() {
            assertEquals(ChefModifier.EFFECTS.size(),
                    ChefModifier.EFFECTS.stream().map(ChefModifier.Buff::id).distinct().count());
            assertEquals(ChefModifier.BUFFS.size() + ChefModifier.DEBUFFS.size(), ChefModifier.EFFECTS.size());
            assertTrue(ChefModifier.BUFFS.stream().noneMatch(ChefModifier.Buff::debuff));
            assertTrue(ChefModifier.DEBUFFS.stream().allMatch(ChefModifier.Buff::debuff));
            assertTrue(ChefModifier.EFFECTS.stream().allMatch(b -> b.durationTicks() <= 45 * 20));
        }
    }

    @Nested
    @DisplayName("歌姫")
    class Diva {

        private final DivaModifier diva = new DivaModifier();

        @BeforeEach
        void air() {
            // 26.x の Material#isAir はレジストリの BlockType に委譲するので、空気を空気にしておく
            TestRegistryAccess.override(Material.AIR.asBlockType(), "isAir", true);
        }

        /** 半径の箱の中に居ることにするプレイヤーたち。{@code AuraAndProjectileTest} と同じ差し替え。 */
        private void nearby(Mocks.FakePlayer... players) {
            List<Player> list = new ArrayList<>();
            for (Mocks.FakePlayer player : players) {
                list.add(player.player());
            }
            org.mockito.Mockito.doReturn(list).when(world).getNearbyEntitiesByType(
                    any(), any(Location.class),
                    org.mockito.ArgumentMatchers.anyDouble(),
                    org.mockito.ArgumentMatchers.anyDouble(),
                    org.mockito.ArgumentMatchers.anyDouble(), any());
        }

        private Mocks.FakePlayer at(String name, double x, double z) {
            Mocks.FakePlayer player = Mocks.player(name, server, world);
            player.state().location = new Location(world, x, 64, z);
            return player;
        }

        private Block noteBlock(Material above) {
            Block block = mock(Block.class);
            when(block.getType()).thenReturn(Material.NOTE_BLOCK);
            Block up = mock(Block.class);
            when(up.getType()).thenReturn(above);
            when(block.getRelative(BlockFace.UP)).thenReturn(up);
            return block;
        }

        private PlayerInteractEvent play(Action action, Block block, EquipmentSlot hand) {
            return new PlayerInteractEvent(me.player(), action, null, block, BlockFace.UP, hand);
        }

        private PlayerInteractEvent play() {
            return play(Action.RIGHT_CLICK_BLOCK, noteBlock(Material.AIR), EquipmentSlot.HAND);
        }

        private static boolean hasRegeneration(Mocks.FakePlayer player) {
            return player.state().potionEffects.stream()
                    .anyMatch(effect -> effect.getType() == PotionEffectType.REGENERATION);
        }

        @Test
        @DisplayName("鳴らすと半径内の全員 (自分含む) に2秒の再生 II")
        void singsToEveryoneNearby() {
            Mocks.FakePlayer friend = at("Friend", 5, 0);
            nearby(me, friend);
            Mocks.installCurrentTick(server, 1000);

            diva.onInteract(me.player(), play());

            for (Mocks.FakePlayer target : List.of(me, friend)) {
                assertTrue(hasRegeneration(target), target.player().getName());
                PotionEffect effect = target.state().potionEffects.get(0);
                assertEquals(DivaModifier.DURATION_TICKS, effect.getDuration());
                assertEquals(DivaModifier.AMPLIFIER, effect.getAmplifier());
            }
        }

        @Test
        @DisplayName("殴っても鳴る")
        void punchingPlaysToo() {
            nearby(me);
            Mocks.installCurrentTick(server, 1000);
            diva.onInteract(me.player(),
                    play(Action.LEFT_CLICK_BLOCK, noteBlock(Material.AIR), EquipmentSlot.HAND));
            assertTrue(hasRegeneration(me));
        }

        @Test
        @DisplayName("オフハンドぶんの右クリックは数えない (二重に鳴らない)")
        void offHandIsIgnored() {
            nearby(me);
            Mocks.installCurrentTick(server, 1000);
            diva.onInteract(me.player(),
                    play(Action.RIGHT_CLICK_BLOCK, noteBlock(Material.AIR), EquipmentSlot.OFF_HAND));
            assertFalse(hasRegeneration(me));
        }

        @Test
        @DisplayName("音符ブロック以外では歌わない")
        void onlyNoteBlocks() {
            nearby(me);
            Mocks.installCurrentTick(server, 1000);
            Block stone = mock(Block.class);
            when(stone.getType()).thenReturn(Material.STONE);
            diva.onInteract(me.player(), play(Action.RIGHT_CLICK_BLOCK, stone, EquipmentSlot.HAND));
            assertFalse(hasRegeneration(me));
        }

        @Test
        @DisplayName("上が塞がっていると鳴らないので歌わない")
        void blockedNoteBlockIsSilent() {
            nearby(me);
            Mocks.installCurrentTick(server, 1000);
            diva.onInteract(me.player(),
                    play(Action.RIGHT_CLICK_BLOCK, noteBlock(Material.STONE), EquipmentSlot.HAND));
            assertFalse(hasRegeneration(me));
        }

        @Test
        @DisplayName("保護などで止められた操作では歌わない")
        void deniedInteractionIsSilent() {
            nearby(me);
            Mocks.installCurrentTick(server, 1000);
            PlayerInteractEvent event = play();
            event.setUseInteractedBlock(Event.Result.DENY);
            diva.onInteract(me.player(), event);
            assertFalse(hasRegeneration(me));
        }

        @Test
        @DisplayName("スニークしながら何か持って右クリックすると鳴らないので歌わない")
        void sneakingWithAnItemPlacesInstead() {
            nearby(me);
            Mocks.installCurrentTick(server, 1000);
            when(me.player().isSneaking()).thenReturn(true);
            // モックを作るのも stubbing なので、when の外で先に作る
            ItemStack stone = Mocks.itemStack(Material.STONE, 1);
            org.bukkit.inventory.PlayerInventory inventory = me.player().getInventory();
            when(inventory.getItemInMainHand()).thenReturn(stone);
            diva.onInteract(me.player(), play());
            assertFalse(hasRegeneration(me));

            // 殴るぶんには関係ない
            diva.onInteract(me.player(),
                    play(Action.LEFT_CLICK_BLOCK, noteBlock(Material.AIR), EquipmentSlot.HAND));
            assertTrue(hasRegeneration(me));
        }

        @Test
        @DisplayName("10秒に一度しか歌えない")
        void cooldown() {
            nearby(me);
            Mocks.installCurrentTick(server, 1000);
            diva.onInteract(me.player(), play());
            assertEquals(1, me.state().potionEffects.size());

            Mocks.installCurrentTick(server, 1000 + (int) DivaModifier.COOLDOWN_TICKS - 1);
            diva.onInteract(me.player(), play());
            assertEquals(1, me.state().potionEffects.size(), "まだ歌えない");

            Mocks.installCurrentTick(server, 1000 + (int) DivaModifier.COOLDOWN_TICKS);
            diva.onInteract(me.player(), play());
            assertEquals(2, me.state().potionEffects.size(), "10秒経てば歌える");
        }

        @Test
        @DisplayName("箱の隅に居る人は対象外 (半径10mの球で判定する)")
        void usesASphereNotABox() {
            // (8, 8) は箱には入るが、中心からの距離は約 11.3m で球の外
            Mocks.FakePlayer corner = at("Corner", 8, 8);
            nearby(me, corner);
            Mocks.installCurrentTick(server, 1000);

            diva.onInteract(me.player(), play());

            assertTrue(hasRegeneration(me));
            assertFalse(hasRegeneration(corner));
        }

        @Test
        @DisplayName("外すとクールタイムの記録も消える")
        void forgetsOnRemoval() {
            nearby(me);
            Mocks.installCurrentTick(server, 1000);
            diva.onInteract(me.player(), play());
            diva.remove(me.player());
            diva.onInteract(me.player(), play());
            assertEquals(2, me.state().potionEffects.size());
        }

        @Test
        @DisplayName("何かしらの効果は必ず入る長さ・強さになっている")
        void actuallyHeals() {
            // 再生 I は 50 tick に 1 回。2秒 (40 tick) だと一度も回復しないことがある
            assertTrue(DivaModifier.AMPLIFIER >= 1, "再生 II 以上でないと 2秒では回復しないことがある");
            assertNotNull(PotionEffectType.REGENERATION);
        }
    }
}
