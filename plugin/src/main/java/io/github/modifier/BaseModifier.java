package io.github.modifier;

import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

/** メタ情報の受け皿。効果は継承先が実装する。 */
public abstract class BaseModifier implements Modifier {

    private final String id;
    private final Component displayName;
    private final List<Component> description;
    private final Material iconBase;

    protected BaseModifier(String id, String name, Material iconBase, String... lines) {
        this.id = id;
        this.displayName = Component.text(name, NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false);
        this.description = List.of(lines).stream()
                .map(line -> (Component) Component.text(line, NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false))
                .toList();
        this.iconBase = iconBase;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Component displayName() {
        return displayName;
    }

    @Override
    public List<Component> description() {
        return description;
    }

    @Override
    public Material iconBase() {
        return iconBase;
    }

    /** このモディファイア専用の attribute キー。 */
    protected NamespacedKey key(String suffix) {
        return new NamespacedKey("modifier", id + "_" + suffix);
    }

    /**
     * 飛行フラグを触ってよい状態か。
     *
     * <p>クリエイティブやスペクテイターの飛行を奪わないためのガード。
     */
    protected static boolean isSurvivalLike(Player player) {
        GameMode mode = player.getGameMode();
        return mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE;
    }
}
