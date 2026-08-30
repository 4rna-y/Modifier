package io.github.modifier;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.kyori.adventure.resource.ResourcePackStatus;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** 参加時のリソースパック配布・選択画面の表示・選択の確定を受け持つ。 */
public final class SelectionListener implements Listener {

    private final ModifierPlugin plugin;
    private final SelectionService selection;
    private final SelectionStore store;
    private final ResourcePackService resourcePack;
    private final ModifierEffects effects;

    /**
     * 選択画面をまだ開いていないプレイヤー。
     *
     * <p>「パックの適用が決まったら開く」と「待ちすぎたら開く」の2経路があるので、
     * 先に来た方だけが開けるようにしておく。パックのコールバックはサーバースレッド外から
     * 来る場合があるため並行に扱えるものを使う。
     */
    private final Set<UUID> awaitingOpen = ConcurrentHashMap.newKeySet();

    public SelectionListener(ModifierPlugin plugin, SelectionService selection,
            SelectionStore store, ResourcePackService resourcePack, ModifierEffects effects) {
        this.plugin = plugin;
        this.selection = selection;
        this.store = store;
        this.resourcePack = resourcePack;
        this.effects = effects;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean needsSelection = store.needsSelection(player);
        if (needsSelection) {
            awaitingOpen.add(player.getUniqueId());
        } else {
            // 選択済みなら、その効果を掛け直す
            effects.apply(player);
        }

        // アイコンが出るかどうかに関わらず配る。
        resourcePack.send(player, status -> {
            if (status != ResourcePackStatus.SUCCESSFULLY_LOADED) {
                plugin.getSLF4JLogger().warn(
                        "{} はリソースパックを適用しませんでした ({})。"
                                + "アイコンは土台アイテムの見た目になります。",
                        player.getName(), status);
            }
            openIfPending(player);
        });

        if (!needsSelection) {
            return;
        }
        // パックが降ってこないまま待たされ続けないよう、上限を切って必ず開く。
        long timeout = plugin.getConfig().getLong("resource-pack.apply-timeout-ticks", 200L);
        player.getScheduler().runDelayed(plugin,
                task -> openIfPending(player), null, Math.max(1L, timeout));
    }

    /** 先着1回だけ選択画面を開く。 */
    private void openIfPending(Player player) {
        if (!awaitingOpen.remove(player.getUniqueId())) {
            return;
        }
        // 参加した瞬間はまだ画面を開ける状態にないので、必ず1tick後に回す。
        player.getScheduler().runDelayed(plugin, task -> {
            if (player.isOnline()) {
                selection.open(player);
            }
        }, null, 1L);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SelectionMenu menu)) {
            return;
        }
        // 選択肢の持ち出しも入れ替えもさせない。
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        // 下段 (プレイヤーの持ち物) のクリックは選択にしない
        if (event.getClickedInventory() != event.getInventory()) {
            return;
        }
        menu.choiceAt(event.getRawSlot()).ifPresent(modifier -> choose(player, modifier));
    }

    /** 選択を確定して効果を掛ける。 */
    private void choose(Player player, Modifier modifier) {
        // 連打で二重に走らせない
        if (!store.needsSelection(player)) {
            return;
        }
        store.select(player, modifier);
        selection.forget(player.getUniqueId());
        effects.apply(player);

        player.sendMessage(plugin.message("<green>"
                + PlainTextComponentSerializer.plainText().serialize(modifier.displayName())
                + " <gray>を選びました。"));
        player.playSound(player, Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);

        // クリック処理の最中に閉じられないので1tick待つ
        player.getScheduler().runDelayed(plugin,
                task -> player.closeInventory(), null, 1L);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof SelectionMenu) {
            event.setCancelled(true);
        }
    }

    /** 未選択のまま閉じられたら開き直す。 */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SelectionMenu)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!plugin.getConfig().getBoolean("selection.force", true)) {
            return;
        }
        if (!store.needsSelection(player)) {
            return;
        }
        // 閉じた直後に開き直すとクライアントが取りこぼすことがあるので少し置く
        player.getScheduler().runDelayed(plugin, task -> {
            if (player.isOnline() && store.needsSelection(player) && !hasMenuOpen(player)) {
                selection.open(player);
            }
        }, null, 2L);
    }

    /**
     * すでに選択画面が開いているか。
     *
     * <p>開いている画面を別の画面で置き換えると、置き換えられた側の
     * {@link InventoryCloseEvent} が飛んでくる。それをそのまま再展開に繋ぐと
     * 開き直しが延々と続くので、ここで止める。
     */
    private static boolean hasMenuOpen(Player player) {
        return player.getOpenInventory().getTopInventory().getHolder() instanceof SelectionMenu;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        awaitingOpen.remove(event.getPlayer().getUniqueId());
        selection.forget(event.getPlayer().getUniqueId());
    }
}
