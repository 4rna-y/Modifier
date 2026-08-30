package io.github.modifier;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/** {@code /modifier <status|select|reload>} */
public final class ModifierCommand implements BasicCommand {

    private static final List<String> SUB_COMMANDS = List.of("status", "select", "reload");

    private final ModifierPlugin plugin;

    public ModifierCommand(ModifierPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String permission() {
        return "modifier.admin";
    }

    @Override
    public void execute(@NotNull CommandSourceStack source, String @NotNull [] args) {
        CommandSender sender = source.getSender();
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> {
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
            case "select" -> {
                if (sender instanceof Player player) {
                    // 選択済みでも選び直せるよう、保存と効果を捨ててから開く
                    plugin.selection().reselect(player);
                    sender.sendMessage(plugin.message("<gray>選択をやり直します。"));
                } else {
                    sender.sendMessage(plugin.message("<red>プレイヤーが実行してください。"));
                }
            }
            case "reload" -> {
                plugin.reloadConfig();
                sender.sendMessage(plugin.message("<green>config.yml を再読み込みしました。"));
            }
            default -> sender.sendMessage(plugin.message("<red>使い方: /modifier <status|select|reload>"));
        }
    }

    @Override
    public @NotNull Collection<String> suggest(@NotNull CommandSourceStack source, String @NotNull [] args) {
        if (args.length > 1) {
            return List.of();
        }
        String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        return SUB_COMMANDS.stream().filter(s -> s.startsWith(prefix)).toList();
    }
}
