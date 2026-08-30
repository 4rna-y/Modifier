package io.github.modifier;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * モディファイア選択画面。
 *
 * <p>{@link InventoryHolder} を自前で持つことで、イベント側は
 * {@code getHolder() instanceof SelectionMenu} だけで自分の画面か判別できる。
 */
public final class SelectionMenu implements InventoryHolder {

    private final List<Modifier> choices;
    private final Inventory inventory;

    public SelectionMenu(List<Modifier> choices, Component title) {
        this.choices = List.copyOf(choices);
        this.inventory = Bukkit.createInventory(this, SelectionLayout.SIZE, title);

        int[] slots = SelectionLayout.slotsFor(this.choices.size());
        for (int i = 0; i < slots.length; i++) {
            inventory.setItem(slots[i], icon(this.choices.get(i)));
        }
    }

    /**
     * 選択肢のアイコン。
     *
     * <p>見た目は {@code item_model} で差し替える。リソースパックを適用していない
     * クライアントには {@link Modifier#iconBase()} の見た目で表示される。
     */
    public static ItemStack icon(Modifier modifier) {
        ItemStack item = ItemStack.of(modifier.iconBase());
        item.setData(DataComponentTypes.ITEM_MODEL, modifier.iconModel());
        item.setData(DataComponentTypes.ITEM_NAME, modifier.displayName());
        item.setData(DataComponentTypes.LORE, ItemLore.lore()
                .addLines(modifier.description())
                .build());
        // 土台アイテム由来の表示 (攻撃力など) が説明文に混ざらないようにする。
        item.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay()
                .addHiddenComponents(DataComponentTypes.ATTRIBUTE_MODIFIERS)
                .build());
        return item;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public List<Modifier> choices() {
        return choices;
    }

    /** クリックされたスロットに対応する選択肢。選択肢以外のスロットなら空。 */
    public Optional<Modifier> choiceAt(int slot) {
        OptionalInt index = SelectionLayout.indexOf(slot, choices.size());
        return index.isEmpty() ? Optional.empty() : Optional.of(choices.get(index.getAsInt()));
    }
}
