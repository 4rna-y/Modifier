package io.github.modifier;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.FoodProperties;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 採掘師。
 *
 * <ul>
 *   <li>鉱石を掘ると、道具に幸運 II が付いているものとして落とし物が決まる。
 *       道具に幸運 II 以上が付いていればそのまま。シルクタッチなら鉱石そのものが落ちるので触らない。</li>
 *   <li>食べ物を食べると、その食べ物の満腹度回復量に応じて採掘速度上昇が付く。
 *       3 以下なら I を 10 秒、4〜7 なら I を 30 秒、8 以上なら II を 20 秒。</li>
 *   <li>原木を壊す速さが半分になる。</li>
 *   <li>作物を収穫しても収穫物は落ちず、種だけ落ちる。ジャガイモのように収穫物が種を兼ねるものは 1 個だけ。</li>
 * </ul>
 *
 * <p>鉱石は、道具そのものを書き換えず、幸運 II を付けた複製で落とし物を引き直して個数を差し替える。
 * 鉱石の落とし物は種類が変わらず個数だけ増えるので、同じ種類の個数を置き換えれば済む。
 *
 * <p>原木の遅さは attribute ({@code BLOCK_BREAK_SPEED}) で付けるが、これはブロックごとに効かせられない。
 * そこで {@link #tick} で見ているブロックを調べ、原木を向いている間だけ付ける。
 */
public final class MinerModifier extends BaseModifier {

    public static final int FORTUNE_LEVEL = 2;

    /** 対象の鉱石。古代の残骸は幸運が効かないので入れない。 */
    public static final Set<Material> ORES = Set.of(
            Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
            Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.NETHER_QUARTZ_ORE, Material.NETHER_GOLD_ORE);

    /** 原木。バニラの {@code #logs} タグと同じ顔ぶれ (原木・木・皮を剥いだもの・ネザーの幹・竹ブロック)。 */
    public static final Set<Material> LOGS = logs();

    public static final double LOG_SPEED_MULTIPLIER = -0.50;
    /** 見ているブロックを調べる距離。バニラの手が届く距離より少し長め。 */
    public static final int LOG_REACH = 6;

    /** 満腹度回復量がこれ以下なら軽食。 */
    public static final int LIGHT_MEAL_MAX = 3;
    /** 満腹度回復量がこれ以上ならごちそう。 */
    public static final int FEAST_MIN = 8;
    public static final int LIGHT_MEAL_HASTE_TICKS = 10 * 20;
    public static final int MEAL_HASTE_TICKS = 30 * 20;
    public static final int FEAST_HASTE_TICKS = 20 * 20;

    /** 作物ブロック → その収穫物。 */
    public static final Map<Material, Material> CROP_YIELDS = Map.of(
            Material.WHEAT, Material.WHEAT,
            Material.BEETROOTS, Material.BEETROOT,
            Material.CARROTS, Material.CARROT,
            Material.POTATOES, Material.POTATO,
            Material.NETHER_WART, Material.NETHER_WART,
            Material.COCOA, Material.COCOA_BEANS);

    /** 収穫物が種を兼ねる作物。収穫物を 1 個だけ残す。 */
    public static final Set<Material> SELF_SEEDING = Set.of(
            Material.CARROTS, Material.POTATOES, Material.NETHER_WART, Material.COCOA);

    private final ToIntFunction<ItemStack> nutrition;

    public MinerModifier() {
        this(MinerModifier::nutritionOf);
    }

    /** @param nutrition 食べ物の満腹度回復量。食べ物でなければ負の値 */
    MinerModifier(ToIntFunction<ItemStack> nutrition) {
        super("miner", "採掘師", Material.IRON_PICKAXE,
                "鉱石を掘ると 幸運 II が付いているものとして落ちる",
                "食べると採掘速度上昇 (満腹度 3 以下: I 10秒 /",
                "4〜7: I 30秒 / 8 以上: II 20秒)",
                "原木を壊す速さ -50%",
                "作物を収穫しても種しか落ちない");
        this.nutrition = nutrition;
    }

    private static Set<Material> logs() {
        Set<Material> logs = EnumSet.noneOf(Material.class);
        for (Material material : Material.values()) {
            if (material.isLegacy()) {
                continue;
            }
            String name = material.name();
            boolean netherStem = name.endsWith("_STEM")
                    && (name.contains("CRIMSON") || name.contains("WARPED"));
            if (name.endsWith("_LOG") || name.endsWith("_WOOD") || name.endsWith("_HYPHAE")
                    || netherStem || name.endsWith("BAMBOO_BLOCK")) {
                logs.add(material);
            }
        }
        return Set.copyOf(logs);
    }

    @Override
    public int weight() {
        // 鉱石が増えるのは強い。木と畑の下方修正はあるが、頻繁に出さない。
        return 2;
    }

    // ---- 鉱石 ---------------------------------------------------------------

    @Override
    public void onBlockDrops(Player player, BlockDropItemEvent event) {
        BlockState state = event.getBlockState();
        Material type = state.getType();
        if (ORES.contains(type)) {
            redrawOre(player, state, event);
        } else if (CROP_YIELDS.containsKey(type)) {
            keepSeedsOnly(type, event);
        }
    }

    private static void redrawOre(Player player, BlockState state, BlockDropItemEvent event) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.getType().isAir()) {
            return;
        }
        if (tool.containsEnchantment(Enchantment.SILK_TOUCH)) {
            return;
        }
        if (tool.getEnchantmentLevel(Enchantment.FORTUNE) >= FORTUNE_LEVEL) {
            return;
        }
        ItemStack lucky = tool.clone();
        lucky.addUnsafeEnchantment(Enchantment.FORTUNE, FORTUNE_LEVEL);
        Map<Material, Integer> wanted = new EnumMap<>(Material.class);
        for (ItemStack drop : state.getDrops(lucky, player)) {
            wanted.merge(drop.getType(), drop.getAmount(), Integer::sum);
        }
        for (Item item : event.getItems()) {
            ItemStack stack = item.getItemStack();
            // 同じ種類が 2 つに分かれて落ちることは無いが、あっても 1 つ目だけを差し替える
            Integer amount = wanted.remove(stack.getType());
            if (amount == null) {
                continue;
            }
            stack.setAmount(amount);
            item.setItemStack(stack);
        }
    }

    // ---- 作物 ---------------------------------------------------------------

    /** 収穫物を落とさない。種を兼ねる作物は 1 個だけ残す。 */
    private static void keepSeedsOnly(Material crop, BlockDropItemEvent event) {
        Material yield = CROP_YIELDS.get(crop);
        boolean keepOne = SELF_SEEDING.contains(crop);
        for (Iterator<Item> items = event.getItems().iterator(); items.hasNext(); ) {
            Item item = items.next();
            ItemStack stack = item.getItemStack();
            Material type = stack.getType();
            if (type == yield && keepOne) {
                stack.setAmount(1);
                item.setItemStack(stack);
                keepOne = false;
                continue;
            }
            // 収穫物と、種にならない副産物 (青くなったジャガイモ)。種はそのまま
            if (type == yield || type == Material.POISONOUS_POTATO) {
                items.remove();
            }
        }
    }

    @Override
    public void onHarvest(Player player, PlayerHarvestBlockEvent event) {
        // スイートベリーは実がそのまま種なので 1 個だけ
        for (ItemStack stack : event.getItemsHarvested()) {
            if (stack.getType() == Material.SWEET_BERRIES) {
                stack.setAmount(1);
            }
        }
    }

    // ---- 食事 ---------------------------------------------------------------

    /** 食べ物の満腹度回復量。食べ物でなければ -1。 */
    static int nutritionOf(ItemStack item) {
        FoodProperties food = item.getData(DataComponentTypes.FOOD);
        return food == null ? -1 : food.nutrition();
    }

    /** 満腹度回復量に応じた採掘速度上昇。 */
    static PotionEffect hasteFor(int nutrition) {
        if (nutrition <= LIGHT_MEAL_MAX) {
            return new PotionEffect(PotionEffectType.HASTE, LIGHT_MEAL_HASTE_TICKS, 0);
        }
        if (nutrition < FEAST_MIN) {
            return new PotionEffect(PotionEffectType.HASTE, MEAL_HASTE_TICKS, 0);
        }
        return new PotionEffect(PotionEffectType.HASTE, FEAST_HASTE_TICKS, 1);
    }

    @Override
    public void onConsume(Player player, PlayerItemConsumeEvent event) {
        int amount = nutrition.applyAsInt(event.getItem());
        if (amount < 0) {
            return;
        }
        player.addPotionEffect(hasteFor(amount));
    }

    // ---- 原木 ---------------------------------------------------------------

    @Override
    public void tick(Player player) {
        Block target = player.getTargetBlockExact(LOG_REACH);
        boolean facingLog = target != null && LOGS.contains(target.getType());
        if (facingLog) {
            if (!Attributes.has(player, Attribute.BLOCK_BREAK_SPEED, key("logs"))) {
                Attributes.setScalar(player, Attribute.BLOCK_BREAK_SPEED, key("logs"), LOG_SPEED_MULTIPLIER);
            }
        } else {
            Attributes.clear(player, Attribute.BLOCK_BREAK_SPEED, key("logs"));
        }
    }

    @Override
    public void remove(Player player) {
        Attributes.clear(player, Attribute.BLOCK_BREAK_SPEED, key("logs"));
    }
}
