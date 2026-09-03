package io.github.modifier;

import java.util.List;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.java.JavaPlugin;

/** Modifier プラグインの入口。 */
public final class ModifierPlugin extends JavaPlugin {

    /** config.yml を消して起動した場合の接頭辞。同梱の config.yml と揃えること。 */
    public static final String DEFAULT_MESSAGE_PREFIX = "<gray>[<aqua>Modifier<gray>]</gray> ";

    private ModifierRegistry registry;
    private SelectionService selection;
    private SelectionStore store;
    private ResourcePackService resourcePack;
    private ModifierEffects effects;
    private NameTagDisplay nameTag;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // キルスイッチ。リスナーもコマンドも配信も登録しない
        if (!getConfig().getBoolean("enabled", true)) {
            getSLF4JLogger().warn("config.yml で enabled: false になっているため、何も行いません。");
            return;
        }

        // 乱数源はサーバー全体で1つ共有する
        java.util.Random random = new java.util.Random();
        this.store = new SelectionStore(getServer());
        this.registry = ModifierRegistry.withBuiltins(this, store, random);
        this.nameTag = new NameTagDisplay(getServer());
        this.effects = new ModifierEffects(this, registry, store, random, nameTag);
        this.selection = new SelectionService(this, registry, store, effects, random);
        this.resourcePack = new ResourcePackService(this);
        resourcePack.start();

        getServer().getPluginManager().registerEvents(
                new SelectionListener(this, selection, store, resourcePack, effects), this);
        getServer().getPluginManager().registerEvents(effects, this);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register("modifier", "自分のモディファイアの確認と管理",
                        List.of("m"), new ModifierCommand(this)));
    }

    public ModifierRegistry registry() {
        return registry;
    }

    public SelectionService selection() {
        return selection;
    }

    public SelectionStore store() {
        return store;
    }

    public ModifierEffects effects() {
        return effects;
    }

    public ResourcePackService resourcePack() {
        return resourcePack;
    }

    @Override
    public void onDisable() {
        if (effects != null) {
            effects.clearAll();
        }
        if (nameTag != null) {
            nameTag.shutdown();
        }
        if (resourcePack != null) {
            resourcePack.stop();
        }
    }

    /** 設定された接頭辞を付けたメッセージを組み立てる。 */
    public Component message(String miniMessage) {
        String prefix = getConfig().getString("message-prefix", DEFAULT_MESSAGE_PREFIX);
        return MiniMessage.miniMessage().deserialize(prefix + miniMessage);
    }

    /** 組み立て済みの本文に接頭辞だけ付ける。MiniMessage として解釈し直さない。 */
    public Component message(Component body) {
        String prefix = getConfig().getString("message-prefix", DEFAULT_MESSAGE_PREFIX);
        return MiniMessage.miniMessage().deserialize(prefix).append(body);
    }
}
