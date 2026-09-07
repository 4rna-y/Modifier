package io.github.modifier;

import java.util.List;
import java.util.Random;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Silverfish;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/**
 * フィッシャーズ。
 *
 * <p>手に持った釣り竿に、常に最大レベルの入れ食いが付く。釣り竿の耐久値は減らない。
 * ただし釣り上げたとき 10% でシルバーフィッシュが掛かる。選ぶと釣り竿を1本もらえる。
 *
 * <p>入れ食いは<b>貸している</b>だけで、手から離れると外れる。付けるときに元のレベルを印
 * ({@link #LENT_KEY}) として持たせ、外すときにそのレベルへ戻すので、自前で付けてあった
 * 入れ食いは消えない。自前で最大レベルなら何もしない。
 *
 * <p>v0.3.2 以前は宝釣りを貸していた。当時の印が残る釣り竿は {@link #returnOldLoan} が
 * 片付ける。
 *
 * <p>「手」は利き手とオフハンドの両方。付け外しは {@link #tick} が持ち物を見て行い、
 * 持ち物の外へ出る経路 (捨てる・別のインベントリへ移す・死んで落とす) ではその場で外す。
 */
public final class FishersModifier extends BaseModifier {

    /** 貸した入れ食いの印。値は貸す前に付いていた入れ食いのレベル (無ければ 0)。 */
    public static final NamespacedKey LENT_KEY = new NamespacedKey("modifier", "fishers_lent_lure");

    /** 入れ食いにする前 (v0.3.2 以前) に貸していた宝釣りの印。値は宝釣りのレベル。 */
    public static final NamespacedKey OLD_LENT_KEY = new NamespacedKey("modifier", "fishers_lent");

    public static final double SILVERFISH_CHANCE = 0.10;

    private final Random random;

    public FishersModifier(Random random) {
        super("fishers", "フィッシャーズ", Material.FISHING_ROD,
                "手に持った釣り竿に 入れ食い III が付く",
                "釣り竿の耐久値が減らない",
                "10% でシルバーフィッシュが釣れる");
        this.random = random;
    }

    @Override
    public int weight() {
        // 釣りをする人には嬉しく、誰にも迷惑を掛けない。シルバーフィッシュは本人の問題。
        return 5;
    }

    @Override
    public List<StartingItems.Item> startingItems() {
        return List.of(StartingItems.Item.of(Material.FISHING_ROD));
    }

    // ---- 入れ食いの貸し借り ----------------------------------------------------

    /**
     * 手にある釣り竿に入れ食いを貸す。
     *
     * @return 変えたか。貸してあるか、自前で最大なら false
     */
    static boolean lend(ItemStack rod) {
        boolean returned = returnOldLoan(rod);
        if (rod.getPersistentDataContainer().has(LENT_KEY, PersistentDataType.INTEGER)) {
            return returned;
        }
        int max = Enchantment.LURE.getMaxLevel();
        int own = rod.getEnchantmentLevel(Enchantment.LURE);
        if (own >= max) {
            return returned;
        }
        rod.editPersistentDataContainer(pdc -> pdc.set(LENT_KEY, PersistentDataType.INTEGER, own));
        rod.addUnsafeEnchantment(Enchantment.LURE, max);
        return true;
    }

    /**
     * 貸した入れ食いを返してもらう。元のレベルがあればそれに戻す。
     *
     * @return 変えたか。貸していなければ false
     */
    static boolean reclaim(ItemStack rod) {
        boolean returned = returnOldLoan(rod);
        Integer own = rod.getPersistentDataContainer().get(LENT_KEY, PersistentDataType.INTEGER);
        if (own == null) {
            return returned;
        }
        rod.removeEnchantment(Enchantment.LURE);
        if (own > 0) {
            rod.addUnsafeEnchantment(Enchantment.LURE, own);
        }
        rod.editPersistentDataContainer(pdc -> pdc.remove(LENT_KEY));
        return true;
    }

    /**
     * 宝釣りを貸していた頃 (v0.3.2 以前) の印が残っていれば、宝釣りを元のレベルへ戻して
     * 印を消す。
     *
     * <p>更新した時点で手にあった釣り竿には、古い印と貸した宝釣り III が残る。古い印は
     * {@link #LENT_KEY} とは別の名前なので放っておくと誰も外さず、宝釣りが永久に残って
     * しまう。釣り竿に触るたび ({@link #lend} と {@link #reclaim} の頭) ここで片付ける。
     *
     * @return 変えたか。古い印が無ければ false
     */
    static boolean returnOldLoan(ItemStack rod) {
        Integer own = rod.getPersistentDataContainer().get(OLD_LENT_KEY, PersistentDataType.INTEGER);
        if (own == null) {
            return false;
        }
        rod.removeEnchantment(Enchantment.LUCK_OF_THE_SEA);
        if (own > 0) {
            rod.addUnsafeEnchantment(Enchantment.LUCK_OF_THE_SEA, own);
        }
        rod.editPersistentDataContainer(pdc -> pdc.remove(OLD_LENT_KEY));
        return true;
    }

    private static boolean isRod(ItemStack item) {
        return item != null && item.getType() == Material.FISHING_ROD;
    }

    /** 持ち物の中の釣り竿を、手にあるものは貸し、それ以外は返してもらう。 */
    private static void sortOut(Player player, boolean lendHeld) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] storage = inventory.getStorageContents();
        int held = inventory.getHeldItemSlot();
        for (int slot = 0; slot < storage.length; slot++) {
            ItemStack item = storage[slot];
            if (!isRod(item)) {
                continue;
            }
            boolean changed = (lendHeld && slot == held) ? lend(item) : reclaim(item);
            if (changed) {
                inventory.setItem(slot, item);
            }
        }
        ItemStack offHand = inventory.getItemInOffHand();
        if (isRod(offHand) && (lendHeld ? lend(offHand) : reclaim(offHand))) {
            inventory.setItemInOffHand(offHand);
        }
    }

    @Override
    public void tick(Player player) {
        sortOut(player, true);
    }

    @Override
    public void remove(Player player) {
        sortOut(player, false);
    }

    @Override
    public void onItemDrop(Player player, PlayerDropItemEvent event) {
        ItemStack dropped = event.getItemDrop().getItemStack();
        if (isRod(dropped) && reclaim(dropped)) {
            event.getItemDrop().setItemStack(dropped);
        }
    }

    @Override
    public void onInventoryClick(Player player, InventoryClickEvent event) {
        // 別のインベントリへ移る前に返してもらう。まだ手にあるなら次の tick で貸し直す
        ItemStack current = event.getCurrentItem();
        if (isRod(current) && reclaim(current)) {
            event.setCurrentItem(current);
        }
        if (event.getClick() == ClickType.NUMBER_KEY) {
            int button = event.getHotbarButton();
            ItemStack hotbar = player.getInventory().getItem(button);
            if (isRod(hotbar) && reclaim(hotbar)) {
                player.getInventory().setItem(button, hotbar);
            }
        }
    }

    @Override
    public void onDeath(Player player, PlayerDeathEvent event) {
        for (ItemStack drop : event.getDrops()) {
            if (isRod(drop)) {
                reclaim(drop);
            }
        }
    }

    // ---- 釣り ----------------------------------------------------------------

    @Override
    public void onItemDamage(Player player, PlayerItemDamageEvent event) {
        if (event.getItem().getType() == Material.FISHING_ROD) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onFish(Player player, PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        if (!(event.getCaught() instanceof Item caught)) {
            return;
        }
        if (random.nextDouble() >= SILVERFISH_CHANCE) {
            return;
        }
        // 釣れた物の代わりにシルバーフィッシュが、同じ勢いで釣り人へ飛んでくる
        Location where = caught.getLocation();
        Vector pull = pullTowards(where, player.getLocation());
        caught.remove();
        where.getWorld().spawn(where, Silverfish.class, fish -> fish.setVelocity(pull));
        player.sendActionBar(Component.text("シルバーフィッシュが釣れた！", NamedTextColor.RED));
    }

    /** 釣り上げた物が釣り人へ飛ぶ勢い。バニラが魚に与えるものと同じ式。 */
    static Vector pullTowards(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return new Vector(dx * 0.1, dy * 0.1 + Math.sqrt(distance) * 0.08, dz * 0.1);
    }
}
