package io.github.modifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /modifier} (別名 {@code /m})。
 *
 * <p>引数なしは「自分が選んだモディファイアの効果」を本人へ返すだけなので誰でも実行できる。
 * サーバーの状態を見たり触ったりする {@code status|select|reload} は
 * {@code modifier.admin} を持っている人だけ。
 */
public final class ModifierCommand implements BasicCommand {

    private static final List<String> ADMIN_SUB_COMMANDS = List.of("status", "select", "reload");

    private final ModifierPlugin plugin;

    public ModifierCommand(ModifierPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String permission() {
        return "modifier.use";
    }

    @Override
    public void execute(@NotNull CommandSourceStack source, String @NotNull [] args) {
        CommandSender sender = source.getSender();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "" -> showSelection(sender);
            case "status" -> {
                if (requireAdmin(sender)) {
                    showStatus(sender);
                }
            }
            case "select" -> {
                if (!requireAdmin(sender)) {
                    return;
                }
                if (sender instanceof Player player) {
                    // 選択済みでも選び直せるよう、保存と効果を捨ててから開く
                    plugin.selection().reselect(player);
                    sender.sendMessage(plugin.message("<gray>選択をやり直します。"));
                } else {
                    sender.sendMessage(plugin.message("<red>プレイヤーが実行してください。"));
                }
            }
            case "reload" -> {
                if (requireAdmin(sender)) {
                    plugin.reloadConfig();
                    sender.sendMessage(plugin.message("<green>config.yml を再読み込みしました。"));
                }
            }
            default -> sender.sendMessage(plugin.message(sender.hasPermission("modifier.admin")
                    ? "<red>使い方: /m [status|select|reload]"
                    : "<red>使い方: /m (自分のモディファイアを表示)"));
        }
    }

    // ------------------------------------------------------------------ 引数なし

    /** 自分が選んだモディファイアの効果を本人へ送る。 */
    private void showSelection(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.message("<red>プレイヤーが実行してください。"));
            return;
        }
        List<Component> lines = describeSelection(plugin.store().selectedId(player), plugin.registry());
        sender.sendMessage(plugin.message(lines.get(0)));
        // 2行目以降 (効果の説明) は接頭辞を付けず、字下げしてぶら下げる。
        lines.stream().skip(1).forEach(sender::sendMessage);
    }

    /**
     * 選択中のモディファイアを伝える行を組む。1行目は接頭辞を付けて送る前提。
     *
     * <p>選んでいない場合と、選んだ id が登録から消えている場合はその案内だけを返す。
     * 後者は config やビルドを差し替えたときに起こりうる。
     */
    static List<Component> describeSelection(Optional<String> selectedId, ModifierRegistry registry) {
        if (selectedId.isEmpty()) {
            return List.of(Component.text("モディファイアを選んでいません。", NamedTextColor.GRAY));
        }
        Optional<Modifier> selected = registry.byId(selectedId.get());
        if (selected.isEmpty()) {
            return List.of(Component.text(
                    "選択中のモディファイア (" + selectedId.get() + ") が見つかりません。",
                    NamedTextColor.RED));
        }

        Modifier modifier = selected.get();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text("選択中: ", NamedTextColor.GRAY).append(modifier.displayName()));
        modifier.description().forEach(line ->
                lines.add(Component.text("  ", NamedTextColor.GRAY).append(line)));
        return List.copyOf(lines);
    }

    // ------------------------------------------------------------------ 管理用

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission("modifier.admin")) {
            return true;
        }
        sender.sendMessage(plugin.message("<red>権限がありません。"));
        return false;
    }

    private void showStatus(CommandSender sender) {
        sender.sendMessage(plugin.message(
                "<white>v" + plugin.getPluginMeta().getVersion()
                        + " / enabled: " + plugin.getConfig().getBoolean("enabled", true)
                        + " / 登録数: " + plugin.registry().all().size()));
        if (sender instanceof Player player) {
            sender.sendMessage(plugin.message("<gray>選択中: "
                    + plugin.store().selectedId(player).orElse("(未選択)")));
        }
        sender.sendMessage(plugin.message("<gray>パック: " + plugin.resourcePack().host()
                .map(h -> h.uri() + " (sha1 " + h.sha1().substring(0, 12) + "…, "
                        + h.sizeBytes() + " bytes)")
                .orElse("(配信していない)")));
        // アイコンが出ない切り分け用に、実際に組んだアイテムの見た目を出す。
        // 併せて抽選の重みも出す。
        int total = plugin.registry().totalWeight();
        for (Modifier modifier : plugin.registry().all()) {
            ItemStack icon = SelectionMenu.icon(modifier);
            sender.sendMessage(plugin.message("<dark_gray>- <gray>" + modifier.id()
                    + " <dark_gray>重み <yellow>" + modifier.weight() + "<gray>/" + total
                    + " <dark_gray>→ <aqua>" + icon.getData(DataComponentTypes.ITEM_MODEL)
                    + " <dark_gray>(土台 " + icon.getType().getKey() + ")"));
        }
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack source, String @NotNull [] args) {
        if (args.length > 1 || !source.getSender().hasPermission("modifier.admin")) {
            // 引数なしで使う人には補完するものが無い。
            return List.of();
        }
        String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        return ADMIN_SUB_COMMANDS.stream().filter(s -> s.startsWith(prefix)).toList();
    }
}
