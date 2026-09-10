package io.github.modifier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 復活剤: 一度きりの効果が使用済みのときだけ食べられて、新品に戻る。 */
class RevivalItemTest {

    private final World world = Mocks.world();
    private final Server server = Mocks.server(world);
    private final SelectionStore store = new SelectionStore(server);
    private final Mocks.FakePlayer me = Mocks.player("Me", server, world);

    private ItemStack revival() {
        ItemStack item = Mocks.stampableItem(Material.GOLDEN_APPLE, 1);
        item.getItemMeta().getPersistentDataContainer().set(RevivalItem.ITEM, PersistentDataType.STRING, RevivalItem.REVIVAL);
        return item;
    }

    private PlayerItemConsumeEvent eat(ItemStack item) {
        return new PlayerItemConsumeEvent(me.player(), item, EquipmentSlot.HAND);
    }

    /** 一度きりの効果を持たないモディファイア。 */
    private static Modifier plain() {
        return new Modifier() {
            @Override
            public String id() {
                return "plain";
            }

            @Override
            public Component displayName() {
                return Component.text("素");
            }

            @Override
            public List<Component> description() {
                return List.of();
            }

            @Override
            public Material iconBase() {
                return Material.STONE;
            }
        };
    }

    @Test
    @DisplayName("印の無い金のリンゴは復活剤ではない")
    void plainAppleIsNotRevival() {
        assertFalse(RevivalItem.isRevival(Mocks.stampableItem(Material.GOLDEN_APPLE, 1)));
        assertTrue(RevivalItem.isRevival(revival()));
    }

    @Test
    @DisplayName("使用済みの冷笑なら新品に戻り、食べられる")
    void restoresUsedCharge() {
        store.consumeCharge(me.player());
        assertFalse(store.chargeAvailable(me.player()));
        PlayerItemConsumeEvent event = eat(revival());
        assertTrue(RevivalItem.consume(me.player(), Optional.of(new SneerModifier(store)), store, event));
        assertFalse(event.isCancelled());
        assertTrue(store.chargeAvailable(me.player()));
        assertTrue(me.state().messages.stream().anyMatch(m -> m.contains("新品")));
    }

    @Test
    @DisplayName("まだ使っていなければ食べない")
    void doesNothingWhenChargeUnused() {
        PlayerItemConsumeEvent event = eat(revival());
        assertFalse(RevivalItem.consume(me.player(), Optional.of(new SneerModifier(store)), store, event));
        assertTrue(event.isCancelled());
        assertTrue(me.state().messages.stream().anyMatch(m -> m.contains("まだ使っていない")));
    }

    @Test
    @DisplayName("一度きりの効果が無いモディファイア・未選択なら食べない")
    void doesNothingWithoutCharge() {
        store.consumeCharge(me.player());
        PlayerItemConsumeEvent event = eat(revival());
        assertFalse(RevivalItem.consume(me.player(), Optional.of(plain()), store, event));
        assertTrue(event.isCancelled());
        assertFalse(store.chargeAvailable(me.player()), "戻さない");
        PlayerItemConsumeEvent none = eat(revival());
        assertFalse(RevivalItem.consume(me.player(), Optional.empty(), store, none));
        assertTrue(none.isCancelled());
    }

    @Test
    @DisplayName("よくばりは中身に一度きりの効果があれば対象")
    void bundleUsesCharge() {
        assertTrue(new SneerModifier(store).usesCharge());
        assertTrue(new ReaperRouletteModifier(store, new java.util.Random()).usesCharge());
        assertFalse(plain().usesCharge());
        assertTrue(new ModifierBundle(plain(), List.of(plain(), new SneerModifier(store))).usesCharge());
        assertFalse(new ModifierBundle(plain(), List.of(plain(), plain())).usesCharge());
    }
}
