package io.github.modifier;

import java.util.List;
import java.util.Optional;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.FoodProperties;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * モディファイア復活剤。食べると、一度きりの効果 (冷笑・死神ルーレット) が使用済みなら新品に戻る。
 *
 * <p>土台は金のリンゴ (満腹でも食べられる)。印は PDC {@code modifier:item = revival}。
 * RaidEvent の高レベルのクレートが同じ印で作る ({@code CustomItems})。土台と印を変えるときは両方を直すこと。
 *
 * <p>一度きりの効果が無いモディファイア、まだ使っていない場合は食べない (消費しない)。
 */
public final class RevivalItem {

    static final NamespacedKey ITEM = new NamespacedKey("modifier", "item");
    static final String REVIVAL = "revival";

    private RevivalItem() {
    }

    public static ItemStack create(int amount) {
        ItemStack item = ItemStack.of(Material.GOLDEN_APPLE, amount);
        item.setData(DataComponentTypes.CUSTOM_NAME,
                Component.text("モディファイア復活剤", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        item.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(
                plain("食べると、一度きりの効果 (冷笑・死神ルーレット) が"),
                plain("使用済みなら新品に戻る。満腹でも食べられる"))));
        item.setData(DataComponentTypes.FOOD, FoodProperties.food()
                .nutrition(4).saturation(9.6f).canAlwaysEat(true).build());
        item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        item.editPersistentDataContainer(pdc -> pdc.set(ITEM, PersistentDataType.STRING, REVIVAL));
        return item;
    }

    public static boolean isRevival(ItemStack item) {
        return item != null && REVIVAL.equals(item.getPersistentDataContainer().get(ITEM, PersistentDataType.STRING));
    }

    /**
     * 食べる直前。戻せるなら戻して食べさせ、そうでなければ食べない。
     *
     * @param active 食べた人に効いているモディファイア (解決済み)。未選択なら空
     * @return 戻したなら true
     */
    public static boolean consume(Player player, Optional<Modifier> active, SelectionStore store,
            PlayerItemConsumeEvent event) {
        if (active.isEmpty() || !active.get().usesCharge()) {
            event.setCancelled(true);
            player.sendMessage(Component.text("今のモディファイアには一度きりの効果が無い。", NamedTextColor.GRAY));
            return false;
        }
        if (store.chargeAvailable(player)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("一度きりの効果はまだ使っていない。", NamedTextColor.GRAY));
            return false;
        }
        store.restoreCharge(player);
        player.sendMessage(Component.text("一度きりの効果が新品に戻った。", NamedTextColor.AQUA));
        player.playSound(player, Sound.ITEM_TOTEM_USE, 0.6f, 1.4f);
        return true;
    }

    private static Component plain(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }
}
