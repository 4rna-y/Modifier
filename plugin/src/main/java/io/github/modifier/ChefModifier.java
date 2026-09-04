package io.github.modifier;

import java.util.List;
import java.util.Random;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * シェフ。
 *
 * <p>食べ物を調理する (作業台で作る、かまど・燻製器で焼く) と「シェフの作った○○」になる。見た目は元のまま。
 * <b>効果は食べたときに決まる</b>: {@link #EFFECT_PERCENT}% で良い効果 14 種か悪い効果 7 種のどれかが付き、
 * 残りは何も起きない。作ったときには何が出るか分からない。
 *
 * <p>料理の印は<b>アイテム側</b> (PDC) に持たせてあるので、誰が食べても同じ扱い。食べたときの処理は選択に
 * 関係なく {@link #serve} で行い、{@link ModifierEffects} が全員分のフックより先に呼ぶ。
 *
 * <p>「調理」の判定:
 * <ul>
 *   <li><b>作業台・手持ちのクラフト</b> — 完成品が食べ物なら対象。プレビューの時点
 *       ({@link PrepareItemCraftEvent}) で印を付けるので、シフトクリックでまとめて作っても全部が料理になる。</li>
 *   <li><b>かまど・燻製器</b> — 完成品スロットから取り出すとき。焚き火は誰が焼いたか
 *       分からないので対象外。ホッパーで抜いた分も対象外。</li>
 * </ul>
 *
 * <p>かまどの完成品スロットに料理が残っていると、印の付いていない焼き上がりと重ならず、
 * 取り出すまで次が焼けない。取り出せば普通に続く。
 */
public final class ChefModifier extends BaseModifier {

    /** 料理の印。値は {@link #DISH}。 */
    public static final NamespacedKey DISH_KEY = new NamespacedKey("modifier", "chef_buff");

    public static final String DISH = "dish";

    /** 食べたときに効果が付く確率 (%)。残りは何も起きない。 */
    public static final int EFFECT_PERCENT = 10;

    /**
     * 付きうる効果。
     *
     * @param debuff true なら悪い効果
     */
    public record Buff(String id, PotionEffectType type, String name, int durationTicks, boolean debuff) {

        /** 良い効果。 */
        public Buff(String id, PotionEffectType type, String name, int durationTicks) {
            this(id, type, name, durationTicks, false);
        }

        /** 表示。例: 「移動速度上昇 (0:30)」 */
        public String label() {
            int seconds = durationTicks / 20;
            return name + " (" + seconds / 60 + ":" + String.format("%02d", seconds % 60) + ")";
        }

        PotionEffect toEffect() {
            return new PotionEffect(type, durationTicks, 0);
        }
    }

    /** 良い効果。どれもレベル I。食べ物なので飲むポーションより短い。 */
    public static final List<Buff> BUFFS = List.of(
            new Buff("speed", PotionEffectType.SPEED, "移動速度上昇", 30 * 20),
            new Buff("haste", PotionEffectType.HASTE, "採掘速度上昇", 30 * 20),
            new Buff("strength", PotionEffectType.STRENGTH, "攻撃力増加", 20 * 20),
            new Buff("jump_boost", PotionEffectType.JUMP_BOOST, "跳躍力上昇", 30 * 20),
            new Buff("regeneration", PotionEffectType.REGENERATION, "再生能力", 8 * 20),
            new Buff("resistance", PotionEffectType.RESISTANCE, "耐性", 20 * 20),
            new Buff("fire_resistance", PotionEffectType.FIRE_RESISTANCE, "火炎耐性", 30 * 20),
            new Buff("water_breathing", PotionEffectType.WATER_BREATHING, "水中呼吸", 30 * 20),
            new Buff("night_vision", PotionEffectType.NIGHT_VISION, "暗視", 30 * 20),
            new Buff("absorption", PotionEffectType.ABSORPTION, "衝撃吸収", 30 * 20),
            new Buff("health_boost", PotionEffectType.HEALTH_BOOST, "体力増強", 30 * 20),
            new Buff("slow_falling", PotionEffectType.SLOW_FALLING, "落下速度低下", 30 * 20),
            new Buff("dolphins_grace", PotionEffectType.DOLPHINS_GRACE, "イルカの好意", 30 * 20),
            new Buff("luck", PotionEffectType.LUCK, "幸運", 45 * 20));

    /** 悪い効果。どれもレベル I で、死ぬほどではない長さ。 */
    public static final List<Buff> DEBUFFS = List.of(
            new Buff("slowness", PotionEffectType.SLOWNESS, "移動速度低下", 20 * 20, true),
            new Buff("mining_fatigue", PotionEffectType.MINING_FATIGUE, "採掘速度低下", 20 * 20, true),
            new Buff("weakness", PotionEffectType.WEAKNESS, "弱体化", 20 * 20, true),
            new Buff("nausea", PotionEffectType.NAUSEA, "吐き気", 12 * 20, true),
            new Buff("hunger", PotionEffectType.HUNGER, "空腹", 30 * 20, true),
            new Buff("poison", PotionEffectType.POISON, "毒", 6 * 20, true),
            new Buff("blindness", PotionEffectType.BLINDNESS, "盲目", 8 * 20, true));

    /** 付きうる効果の全部。良いのが 14、悪いのが 7 なので、効果が付いたうちの 1/3 が悪い。 */
    public static final List<Buff> EFFECTS = java.util.stream.Stream.concat(BUFFS.stream(), DEBUFFS.stream()).toList();

    public ChefModifier() {
        super("chef", "シェフ", Material.BREAD,
                "調理した食べ物が料理になり、食べると " + EFFECT_PERCENT + "% で",
                "ランダムなポーション効果が付く (悪いものも)");
    }

    @Override
    public int weight() {
        // 料理を配れば周りも得をする。誰にも迷惑を掛けない標準枠。
        return 6;
    }

    @Override
    public void onCraftPrepared(Player player, PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        ItemStack result = inventory.getResult();
        if (event.getRecipe() == null || result == null || !isFood(result) || isDish(result)) {
            return;
        }
        inventory.setResult(cook(result));
    }

    @Override
    public void onInventoryClick(Player player, InventoryClickEvent event) {
        // かまど・燻製器・溶鉱炉の完成品スロットだけ。取り出す前に付けるので、
        // シフトクリックでも数字キーでも付いたまま持ち物へ移る
        if (event.getSlotType() != InventoryType.SlotType.RESULT
                || !(event.getClickedInventory() instanceof FurnaceInventory)) {
            return;
        }
        ItemStack item = event.getCurrentItem();
        if (item == null || !isFood(item) || isDish(item)) {
            return;
        }
        event.setCurrentItem(cook(item));
    }

    private static boolean isFood(ItemStack item) {
        return !item.getType().isAir() && item.getType().isEdible();
    }

    /**
     * 料理にする。元のアイテムは変えず、印・名前・説明を付けた複製を返す。
     *
     * <p>名前は翻訳キーで組むので、クライアントの言語で「パン」の部分が出る。
     */
    public static ItemStack cook(ItemStack raw) {
        ItemStack dish = raw.clone();
        ItemMeta meta = dish.getItemMeta();
        if (meta == null) {
            return raw;
        }
        meta.getPersistentDataContainer().set(DISH_KEY, PersistentDataType.STRING, DISH);
        meta.displayName(Component.text("シェフの作った", NamedTextColor.GOLD)
                .append(Component.translatable(raw.getType().translationKey()))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("食べると " + EFFECT_PERCENT + "% で何かが起こる", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("良い効果 " + BUFFS.size() + " 種か、悪い効果 " + DEBUFFS.size() + " 種のどれか",
                        NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        dish.setItemMeta(meta);
        return dish;
    }

    /** シェフの料理か。 */
    public static boolean isDish(ItemStack item) {
        return item != null && item.getPersistentDataContainer().get(DISH_KEY, PersistentDataType.STRING) != null;
    }

    /**
     * 料理を食べたときの処理。選択に関係なく、誰が食べても同じ。料理でなければ何もしない。
     *
     * @return 付いた効果。付かなかった、または料理でなければ null
     */
    public static Buff serve(Player eater, ItemStack item, Random random) {
        if (!isDish(item)) {
            return null;
        }
        if (random.nextInt(100) >= EFFECT_PERCENT) {
            eater.sendActionBar(Component.text("シェフの料理: 何も起きなかった", NamedTextColor.GRAY));
            return null;
        }
        Buff buff = EFFECTS.get(random.nextInt(EFFECTS.size()));
        eater.addPotionEffect(buff.toEffect());
        eater.sendActionBar(Component.text("シェフの料理: " + buff.label(),
                buff.debuff() ? NamedTextColor.RED : NamedTextColor.GREEN));
        return buff;
    }
}
