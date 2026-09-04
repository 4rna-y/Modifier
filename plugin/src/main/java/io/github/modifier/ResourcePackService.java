package io.github.modifier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.resource.ResourcePackStatus;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/** 参加者へリソースパックを配る。 */
public final class ResourcePackService {

    public static final String DEFAULT_PROMPT =
            "<gold>アイコンを表示するために必要です";

    private final ModifierPlugin plugin;
    private ResourcePackHost host;

    public ResourcePackService(ModifierPlugin plugin) {
        this.plugin = plugin;
    }

    /** 設定に従って配信を始める。無効なら何もしない。 */
    public void start() {
        if (!plugin.getConfig().getBoolean("resource-pack.enabled", true)) {
            plugin.getSLF4JLogger().info(
                    "resource-pack.enabled が false のため、パックを配りません。");
            return;
        }
        String publicBaseUrl = plugin.getConfig().getString("resource-pack.public-base-url", "");
        String advertised = plugin.getConfig().getString("resource-pack.host", "127.0.0.1");
        String bind = plugin.getConfig().getString("resource-pack.bind", "0.0.0.0");
        int port = plugin.getConfig().getInt("resource-pack.port", 8123);
        try {
            this.host = ResourcePackHost.start(plugin, publicBaseUrl, advertised, bind, port);
            plugin.getSLF4JLogger().info("リソースパックを配信します: {} ({} bytes, sha1 {})",
                    host.uri(), host.sizeBytes(), host.sha1());
        } catch (IOException e) {
            plugin.getSLF4JLogger().error(
                    "リソースパックを配信できません。アイコンは土台アイテムの見た目になります。", e);
        }
    }

    public void stop() {
        if (host != null) {
            host.close();
            host = null;
        }
    }

    public Optional<ResourcePackHost> host() {
        return Optional.ofNullable(host);
    }

    /**
     * パックを送る。
     *
     * @param onResolved 適用の可否が決まった時点で1度だけ呼ばれる。配信していない場合は即座に呼ぶ
     */
    public void send(Player player, Consumer<ResourcePackStatus> onResolved) {
        if (host == null) {
            onResolved.accept(ResourcePackStatus.DECLINED);
            return;
        }
        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(
                host.id(), host.uri(), host.sha1());

        // 他のプラグイン (Mamble) のパックも 1 回の要求にまとめて送る。別々に送ると、確認画面が出ている間に
        // 次の要求が届いた方が勝ち、先のパックは DISCARDED になって適用されない。
        // 相手は ServicesManager に ResourcePackInfo を登録しておくだけでよい。
        List<ResourcePackInfo> packs = new ArrayList<>();
        packs.add(info);
        for (RegisteredServiceProvider<ResourcePackInfo> registration
                : plugin.getServer().getServicesManager().getRegistrations(ResourcePackInfo.class)) {
            if (registration.getPlugin() != plugin) {
                packs.add(registration.getProvider());
            }
        }

        player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(packs)
                .required(plugin.getConfig().getBoolean("resource-pack.required", true))
                .replace(false)
                .prompt(MiniMessage.miniMessage().deserialize(
                        plugin.getConfig().getString("resource-pack.prompt", DEFAULT_PROMPT)))
                // ACCEPTED / DOWNLOADED は途中経過なので、自分のパックが決着した時だけ通す
                .callback((uuid, status, audience) -> {
                    if (uuid.equals(host.id()) && !status.intermediate()) {
                        onResolved.accept(status);
                    }
                })
                .build());
    }
}
