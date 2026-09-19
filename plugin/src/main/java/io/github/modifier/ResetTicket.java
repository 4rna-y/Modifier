package io.github.modifier;

import java.util.List;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * モディファイアリセットチケット。長押しでチャージしきると、モディファイアを選び直せる。
 *
 * <p>土台は紙。印は PDC {@code modifier:item = reset_ticket} で、キーは
 * {@link RevivalItem#ITEM} と共有する。RaidEvent のレイド報酬が同じ印で作る
 * ({@code CustomItems})。土台と印を変えるときは両方を直すこと。
 *
 * <p>長押しにしてあるのは、選び直しが<b>取り返しのつかない</b>操作だから。
 * {@link SelectionService#reselect} は画面を開く時点で今の選択を捨てるので、
 * 右クリック1回で発動すると誤爆が事故になる。
 *
 * <p>選び直しでは開始アイテムを配らない。配ると、チケットを使うたびに
 * モディファイアごとの装備 (地雷系の弓など) を増やせてしまう。
 */
public final class ResetTicket {

    static final String RESET_TICKET = "reset_ticket";

    /** チャージの長さ (秒)。Manchor のリコールスクロールと同じ操作感に揃えてある。 */
    static final float CHARGE_SECONDS = 3f;

    private ResetTicket() {
    }

    public static ItemStack create(int amount) {
        ItemStack item = ItemStack.of(Material.PAPER, amount);
        item.setData(DataComponentTypes.CUSTOM_NAME,
                Component.text("モディファイアリセットチケット", NamedTextColor.LIGHT_PURPLE)
                        .decoration(TextDecoration.ITALIC, false));
        item.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(
                plain("長押しで " + (int) CHARGE_SECONDS + " 秒チャージするとモディファイアを選び直す"),
                plain("今の効果は失われる。開始アイテムは配られない"),
                plain("使うと 1 枚消える"))));
        item.setData(DataComponentTypes.CONSUMABLE, Consumable.consumable()
                .consumeSeconds(CHARGE_SECONDS)
                .animation(ItemUseAnimation.BOW)
                .sound(Key.key("block.beacon.ambient"))
                .hasConsumeParticles(false)
                .build());
        item.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        item.editPersistentDataContainer(pdc ->
                pdc.set(RevivalItem.ITEM, PersistentDataType.STRING, RESET_TICKET));
        return item;
    }

    public static boolean isResetTicket(ItemStack item) {
        return item != null && RESET_TICKET.equals(
                item.getPersistentDataContainer().get(RevivalItem.ITEM, PersistentDataType.STRING));
    }

    /**
     * チャージを完走した。1 枚消して選び直しを開く。
     *
     * <p>イベントは常に打ち切って自前で消費する。素通しすると、選び直せなかったときにも
     * 1 枚消える (Manchor の {@code ScrollListener} と同じ理由)。
     *
     * @return 選び直しを始めたなら true
     */
    public static boolean consume(Player player, ModifierPlugin plugin, PlayerItemConsumeEvent event) {
        event.setCancelled(true);
        if (plugin.store().needsSelection(player)) {
            player.sendMessage(Component.text("まだモディファイアを選んでいない。", NamedTextColor.GRAY));
            return false;
        }
        consumeOne(player);
        player.sendMessage(Component.text("モディファイアを選び直す。", NamedTextColor.LIGHT_PURPLE));
        player.playSound(player, Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
        // 食べている最中に画面を開くとクライアントが取りこぼすので 1 tick 置く
        player.getScheduler().runDelayed(plugin, task -> {
            if (player.isOnline()) {
                plugin.selection().reselect(player, false);
            }
        }, null, 1L);
        return true;
    }

    /** 持っているチケットを 1 枚減らす。利き手を先に見る。 */
    private static void consumeOne(Player player) {
        var inventory = player.getInventory();
        if (take(inventory.getItemInMainHand())) {
            return;
        }
        take(inventory.getItemInOffHand());
    }

    private static boolean take(ItemStack item) {
        if (!isResetTicket(item)) {
            return false;
        }
        item.setAmount(item.getAmount() - 1);
        return true;
    }

    private static Component plain(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }
}
