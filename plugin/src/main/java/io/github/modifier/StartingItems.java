package io.github.modifier;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * モディファイアを選んだときに受け取る道具。
 *
 * <p>選択の確定時 ({@link SelectionListener}) に一度だけ配る。参加やリスポーンでは配り直さない。
 * 持ち物に入り切らなければ足元に落とす。
 */
public final class StartingItems {

    private StartingItems() {
    }

    /**
     * 道具1口。
     *
     * <p>実際の {@link ItemStack} はサーバーが無いと作れないので、渡す直前まで素材と個数で持つ。
     * これで単体テストからも「何を何個もらえるか」を見られる。
     */
    public record Item(Material type, int amount) {

        public Item {
            if (amount < 1) {
                throw new IllegalArgumentException("個数は 1 以上: " + type + " が " + amount);
            }
        }

        public static Item of(Material type) {
            return new Item(type, 1);
        }

        public static Item of(Material type, int amount) {
            return new Item(type, amount);
        }

        ItemStack toItemStack() {
            return ItemStack.of(type, amount);
        }

        /** 表示。名前は翻訳キーで組むので、クライアントの言語で「盾」「矢 x16」のように出る。 */
        public Component label() {
            Component name = Component.translatable(type.translationKey());
            return amount == 1 ? name : name.append(Component.text(" x" + amount));
        }
    }

    /** 持ち物へ入れる。入り切らない分は足元に落とす。 */
    public static void give(Player player, List<Item> items) {
        if (items.isEmpty()) {
            return;
        }
        ItemStack[] stacks = items.stream().map(Item::toItemStack).toArray(ItemStack[]::new);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(stacks);
        for (ItemStack rest : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
    }

    /** 説明文に添える行。受け取る物が無ければ空。 */
    public static Optional<Component> describe(List<Item> items) {
        if (items.isEmpty()) {
            return Optional.empty();
        }
        Component line = Component.text("受け取る道具: ", NamedTextColor.DARK_AQUA);
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                line = line.append(Component.text(", ", NamedTextColor.DARK_AQUA));
            }
            line = line.append(items.get(i).label().color(NamedTextColor.AQUA));
        }
        return Optional.of(line.decoration(TextDecoration.ITALIC, false));
    }
}
