package io.github.modifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 熱血お肉屋さん。
 *
 * <p>動物を倒すと肉が 1 個多く、しかも焼けた状態で落ちる。腐った肉を食べても空腹にならない。
 * その代わり肉以外を食べるとアレルギーが出て、空腹と吐き気になり、20% で毒 II も付く。
 *
 * <p>「肉」は {@link #MEATS}。生でも焼いてあっても、魚でも腐った肉でも肉。ウサギシチューも肉。
 * ポーションや牛乳は食べ物ではないので、アレルギーの対象外。
 *
 * <p>腐った肉の空腹は、食べ物由来のポーション効果 ({@code Cause.FOOD}) を止めて防ぐ。
 * 効果は食べ終わった直後に同じ tick で付くので、直前に何を食べたかを覚えておけば見分けられる。
 */
public final class ButcherModifier extends BaseModifier {

    /** 生肉 → 焼いた肉。 */
    public static final Map<Material, Material> COOKED = Map.of(
            Material.BEEF, Material.COOKED_BEEF,
            Material.PORKCHOP, Material.COOKED_PORKCHOP,
            Material.CHICKEN, Material.COOKED_CHICKEN,
            Material.MUTTON, Material.COOKED_MUTTON,
            Material.RABBIT, Material.COOKED_RABBIT,
            Material.COD, Material.COOKED_COD,
            Material.SALMON, Material.COOKED_SALMON);

    /** 食べてもアレルギーが出ないもの。 */
    public static final Set<Material> MEATS = meats();

    public static final int EXTRA_MEAT = 1;
    public static final double POISON_CHANCE = 0.20;
    /** 毒 II。 */
    public static final int POISON_AMPLIFIER = 1;
    /** 毒 II は 12 tick ごとに 1 ダメージ。5 秒で 8 ほど減るが、毒では死なない。 */
    public static final int POISON_DURATION_TICKS = 5 * 20;

    private final Random random;
    /** 直前に食べ終わった物。食べ物由来のポーション効果を見分けるのに使う。 */
    private final Map<UUID, Material> lastEaten = new HashMap<>();

    public ButcherModifier(Random random) {
        super("butcher", "熱血お肉屋さん", Material.COOKED_BEEF,
                "動物を倒すと肉が 1 個多く、焼けて落ちる",
                "腐った肉を食べても空腹にならない",
                "肉以外を食べるとアレルギーが出て",
                "空腹と吐き気になり、20% で毒 II も付く");
        this.random = random;
    }

    private static Set<Material> meats() {
        Set<Material> meats = new HashSet<>(COOKED.keySet());
        meats.addAll(COOKED.values());
        meats.add(Material.TROPICAL_FISH);
        meats.add(Material.PUFFERFISH);
        meats.add(Material.ROTTEN_FLESH);
        meats.add(Material.RABBIT_STEW);
        return Set.copyOf(meats);
    }

    @Override
    public int weight() {
        // 肉が増えるのは嬉しいが、パンもリンゴも食べられなくなる。差し引きで標準枠。
        return 5;
    }

    /** 落とし物がなる焼いた肉。肉でなければ null。 */
    static Material cookedOf(Material type) {
        if (COOKED.containsKey(type)) {
            return COOKED.get(type);
        }
        return COOKED.containsValue(type) ? type : null;
    }

    @Override
    public void onKillDrops(Player player, EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Animals)) {
            return;
        }
        List<ItemStack> drops = event.getDrops();
        for (int i = 0; i < drops.size(); i++) {
            ItemStack drop = drops.get(i);
            Material cooked = cookedOf(drop.getType());
            if (cooked == null) {
                continue;
            }
            ItemStack meat = drop.getType() == cooked ? drop : drop.withType(cooked);
            meat.setAmount(meat.getAmount() + EXTRA_MEAT);
            drops.set(i, meat);
        }
    }

    @Override
    public void onConsume(Player player, PlayerItemConsumeEvent event) {
        Material food = event.getItem().getType();
        if (!food.isEdible()) {
            return;
        }
        lastEaten.put(player.getUniqueId(), food);
        if (MEATS.contains(food)) {
            return;
        }
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.HUNGER, FoodPoisoningModifier.HUNGER_DURATION_TICKS, 0));
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.NAUSEA, FoodPoisoningModifier.NAUSEA_DURATION_TICKS, 0));
        boolean poisoned = random.nextDouble() < POISON_CHANCE;
        if (poisoned) {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.POISON, POISON_DURATION_TICKS, POISON_AMPLIFIER));
        }
        player.sendActionBar(Component.text(
                poisoned ? "アレルギーが出た……毒まで回ってきた" : "アレルギーが出た……",
                NamedTextColor.RED));
    }

    @Override
    public void onPotionEffect(Player player, EntityPotionEffectEvent event) {
        if (event.getCause() != EntityPotionEffectEvent.Cause.FOOD) {
            return;
        }
        if (!PotionEffectType.HUNGER.equals(event.getModifiedType())) {
            return;
        }
        if (lastEaten.get(player.getUniqueId()) != Material.ROTTEN_FLESH) {
            return;
        }
        event.setCancelled(true);
    }

    @Override
    public void remove(Player player) {
        lastEaten.remove(player.getUniqueId());
    }
}
