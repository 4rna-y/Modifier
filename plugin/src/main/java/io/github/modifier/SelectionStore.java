package io.github.modifier;

import java.util.Optional;

import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * 選択済みモディファイアの保存先。
 *
 * <p>プレイヤーの {@link PersistentDataContainer} に置く。これは {@code world/playerdata} に
 * 保存されるので、ワールドフォルダごと作り直す構成なら選択も自然に消える。
 * それとは別に「どのワールドで選んだか」も記録しておき、主ワールドが入れ替わったら
 * 選び直しにする。
 *
 * <p>「一度きり」の効果 (蘇生系) の使用済みフラグもここに置く。フラグは選択に付随するもの
 * なので、選択と一緒に消える。
 */
public final class SelectionStore {

    private static final NamespacedKey SELECTED = new NamespacedKey("modifier", "selected");
    private static final NamespacedKey SELECTED_WORLD = new NamespacedKey("modifier", "selected-world");
    private static final NamespacedKey CHARGE_USED = new NamespacedKey("modifier", "charge-used");

    private final Server server;

    public SelectionStore(Server server) {
        this.server = server;
    }

    /** 選択画面を出すべきか。 */
    public boolean needsSelection(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        return needsSelection(
                pdc.get(SELECTED, PersistentDataType.STRING),
                pdc.get(SELECTED_WORLD, PersistentDataType.STRING),
                currentWorldId());
    }

    /**
     * 保存された選択とワールドから、選択画面が必要かを判定する。
     *
     * <p>未選択か、選んだときとは別のワールドになっていれば選び直し。
     */
    static boolean needsSelection(String storedId, String storedWorldId, String currentWorldId) {
        if (storedId == null || storedId.isBlank()) {
            return true;
        }
        return !currentWorldId.equals(storedWorldId);
    }

    /** 現在のワールドで選択済みの id。未選択、または別ワールドで選んだものなら空。 */
    public Optional<String> selectedId(Player player) {
        if (needsSelection(player)) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                player.getPersistentDataContainer().get(SELECTED, PersistentDataType.STRING));
    }

    public void select(Player player, Modifier modifier) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(SELECTED, PersistentDataType.STRING, modifier.id());
        pdc.set(SELECTED_WORLD, PersistentDataType.STRING, currentWorldId());
        // 選び直したら一度きりの効果も新品に戻す
        pdc.remove(CHARGE_USED);
    }

    public void clear(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.remove(SELECTED);
        pdc.remove(SELECTED_WORLD);
        pdc.remove(CHARGE_USED);
    }

    /** 一度きりの効果がまだ残っているか。 */
    public boolean chargeAvailable(Player player) {
        return !player.getPersistentDataContainer().has(CHARGE_USED, PersistentDataType.BOOLEAN);
    }

    /** 一度きりの効果を使用済みにする。 */
    public void consumeCharge(Player player) {
        player.getPersistentDataContainer().set(CHARGE_USED, PersistentDataType.BOOLEAN, true);
    }

    /**
     * 主ワールドの UID。ワールドを作り直すと変わるので、選び直しの判定に使える。
     *
     * <p>ワールドが1つも読み込まれていない場合は空文字。保存済みの UID と一致しないので、
     * 判断がつかないときは選択画面を出す側に倒れる。
     */
    private String currentWorldId() {
        return server.getWorlds().isEmpty()
                ? ""
                : server.getWorlds().get(0).getUID().toString();
    }
}
