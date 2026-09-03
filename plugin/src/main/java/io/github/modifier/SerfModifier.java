package io.github.modifier;

import java.util.Random;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 農奴。
 *
 * <p>収穫した農作物が50%の確率で3倍になる。
 * 成長段階のある作物は、育ち切ったものを収穫したときだけが対象。
 */
public final class SerfModifier extends BaseModifier {

    public static final double CHANCE = 0.5;
    public static final int MULTIPLIER = 3;

    /** 対象の農作物。スイートベリーは右クリック収穫 ({@link #onHarvest}) で拾う。 */
    public static final Set<Material> CROPS = Set.of(
            Material.WHEAT,
            Material.CARROTS,
            Material.POTATOES,
            Material.BEETROOTS,
            Material.NETHER_WART,
            Material.COCOA,
            Material.MELON,
            Material.PUMPKIN,
            Material.SWEET_BERRY_BUSH);

    private final Random random;

    public SerfModifier(Random random) {
        super("serf", "農奴", Material.WHEAT,
                "収穫した農作物が",
                "50% の確率で 3倍になる");
        this.random = random;
    }

    @Override
    public int weight() {
        // 戦闘に影響しない経済効果。
        return 6;
    }

    @Override
    public void onBlockDrops(Player player, BlockDropItemEvent event) {
        if (!CROPS.contains(event.getBlockState().getType())) {
            return;
        }
        // 育ち切っていない作物 (種を回収しただけ) は対象外
        BlockData data = event.getBlockState().getBlockData();
        if (data instanceof Ageable age && age.getAge() < age.getMaximumAge()) {
            return;
        }
        if (random.nextDouble() >= CHANCE) {
            return;
        }
        for (Item item : event.getItems()) {
            ItemStack stack = item.getItemStack();
            stack.setAmount(multiplied(stack.getAmount()));
            item.setItemStack(stack);
        }
    }

    @Override
    public void onHarvest(Player player, PlayerHarvestBlockEvent event) {
        if (random.nextDouble() >= CHANCE) {
            return;
        }
        for (ItemStack stack : event.getItemsHarvested()) {
            stack.setAmount(multiplied(stack.getAmount()));
        }
    }

    static int multiplied(int amount) {
        return amount * MULTIPLIER;
    }
}
