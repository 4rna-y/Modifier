package io.github.modifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

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
 * <p>食べ物を調理する (作業台で作る、かまど・燻製器で焼く) と、ランダムなポーション効果の
 * 付いた料理になる。名前は「シェフの作ったおいしい○○」、見た目は元のまま、説明欄に効能が載る。
 *
 * <p>効果は<b>アイテム側</b>に持たせてあるので、誰が食べても効く。食べたときの処理は選択に
 * 関係なく {@link #serve} で行い、{@link ModifierEffects} が全員分のフックより先に呼ぶ。
 *
 * <p>「調理」の判定:
 * <ul>
 *   <li><b>作業台・手持ちのクラフト</b> — 完成品が食べ物なら対象。プレビューの時点
 *       ({@link PrepareItemCraftEvent}) で付けるので、作る前に何が付くか見える。
 *       シフトクリックでまとめて作ると1個ごとにプレビューが走り直すので、同じ完成品を
 *       作り続けている間は同じ効果を使い回す (効果が違うと重ならず、枠を食い潰すため)。
 *       グリッドを空にするか別の物を作ると引き直す。</li>
 *   <li><b>かまど・燻製器</b> — 完成品スロットから取り出すとき。焚き火は誰が焼いたか
 *       分からないので対象外。ホッパーで抜いた分も対象外。</li>
 * </ul>
 *
 * <p>かまどの完成品スロットに料理が残っていると、効果の付いていない焼き上がりと重ならず、
 * 取り出すまで次が焼けない。取り出せば普通に続く。
 */
public final class ChefModifier extends BaseModifier {

    /** 料理に付ける印。値は {@link Buff#id()}。 */
    public static final NamespacedKey BUFF_KEY = new NamespacedKey("modifier", "chef_buff");

    /** 料理に付きうる効果。 */
    public record Buff(String id, PotionEffectType type, String name, int durationTicks) {

        /** 説明欄に出す文言。例: 「移動速度上昇 (1:30)」 */
        public String label() {
            int seconds = durationTicks / 20;
            return name + " (" + seconds / 60 + ":" + String.format("%02d", seconds % 60) + ")";
        }

        PotionEffect toEffect() {
            return new PotionEffect(type, durationTicks, 0);
        }
    }

    /** どれもレベル I。飲むポーションと同程度の長さにしてある。 */
    public static final List<Buff> BUFFS = List.of(
            new Buff("speed", PotionEffectType.SPEED, "移動速度上昇", 90 * 20),
            new Buff("haste", PotionEffectType.HASTE, "採掘速度上昇", 90 * 20),
            new Buff("strength", PotionEffectType.STRENGTH, "攻撃力増加", 60 * 20),
            new Buff("jump_boost", PotionEffectType.JUMP_BOOST, "跳躍力上昇", 90 * 20),
            new Buff("regeneration", PotionEffectType.REGENERATION, "再生能力", 15 * 20),
            new Buff("resistance", PotionEffectType.RESISTANCE, "耐性", 60 * 20),
            new Buff("fire_resistance", PotionEffectType.FIRE_RESISTANCE, "火炎耐性", 120 * 20),
            new Buff("water_breathing", PotionEffectType.WATER_BREATHING, "水中呼吸", 120 * 20),
            new Buff("night_vision", PotionEffectType.NIGHT_VISION, "暗視", 120 * 20),
            new Buff("absorption", PotionEffectType.ABSORPTION, "衝撃吸収", 120 * 20),
            new Buff("health_boost", PotionEffectType.HEALTH_BOOST, "体力増強", 120 * 20),
            new Buff("slow_falling", PotionEffectType.SLOW_FALLING, "落下速度低下", 90 * 20),
            new Buff("dolphins_grace", PotionEffectType.DOLPHINS_GRACE, "イルカの好意", 90 * 20),
            new Buff("luck", PotionEffectType.LUCK, "幸運", 180 * 20));

    /** 同じ完成品を作り続けている間に使い回す効果。 */
    private record Batch(Material dish, Buff buff) {
    }

    private final Map<UUID, Batch> batches = new HashMap<>();
    private final Random random;

    public ChefModifier(Random random) {
        super("chef", "シェフ", Material.BREAD,
                "調理した食べ物に",
                "ランダムなポーション効果が付く");
        this.random = random;
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
        if (event.getRecipe() == null || result == null || !isFood(result)) {
            // 作り終えた (グリッドが空になった) か、食べ物以外を作っている。次の料理は引き直す
            batches.remove(player.getUniqueId());
            return;
        }
        Batch batch = batches.get(player.getUniqueId());
        if (batch == null || batch.dish() != result.getType()) {
            batch = new Batch(result.getType(), roll());
            batches.put(player.getUniqueId(), batch);
        }
        inventory.setResult(cook(result, batch.buff()));
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
        if (item == null || !isFood(item) || buffOn(item) != null) {
            return;
        }
        event.setCurrentItem(cook(item, roll()));
    }

    @Override
    public void remove(Player player) {
        batches.remove(player.getUniqueId());
    }

    private Buff roll() {
        return BUFFS.get(random.nextInt(BUFFS.size()));
    }

    private static boolean isFood(ItemStack item) {
        return !item.getType().isAir() && item.getType().isEdible();
    }

    /**
     * 料理にする。元のアイテムは変えず、印・名前・説明を付けた複製を返す。
     *
     * <p>名前は翻訳キーで組むので、クライアントの言語で「パン」の部分が出る。
     */
    public static ItemStack cook(ItemStack raw, Buff buff) {
        ItemStack dish = raw.clone();
        ItemMeta meta = dish.getItemMeta();
        if (meta == null) {
            return raw;
        }
        meta.getPersistentDataContainer().set(BUFF_KEY, PersistentDataType.STRING, buff.id());
        meta.displayName(Component.text("シェフの作ったおいしい", NamedTextColor.GOLD)
                .append(Component.translatable(raw.getType().translationKey()))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("食べると " + buff.label(), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        dish.setItemMeta(meta);
        return dish;
    }

    /** その料理に付いている効果。料理でなければ null。 */
    public static Buff buffOn(ItemStack item) {
        if (item == null) {
            return null;
        }
        String id = item.getPersistentDataContainer().get(BUFF_KEY, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }
        return BUFFS.stream().filter(buff -> buff.id().equals(id)).findFirst().orElse(null);
    }

    /** 料理を食べたときの処理。選択に関係なく、誰が食べても効く。料理でなければ何もしない。 */
    public static void serve(Player eater, ItemStack item) {
        Buff buff = buffOn(item);
        if (buff != null) {
            eater.addPotionEffect(buff.toEffect());
        }
    }
}
