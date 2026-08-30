package io.github.modifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;
import org.mockito.stubbing.Answer;

/**
 * 効果のテストで使うモック一式。
 *
 * <p>本物のイベントを組んで本物のハンドラへ流すのがこのテストの狙いなので、
 * モックにするのはプレイヤーとワールドだけに留める。
 */
final class Mocks {

    private Mocks() {
    }

    /** テスト中に何が起きたかを覚えておくための、プレイヤーに紐づく記録。 */
    static final class PlayerState {
        double health = 20.0;
        double maxHealth = 20.0;
        int foodLevel = 20;
        boolean allowFlight;
        boolean flying;
        /** 飛行を許した状態で落下ダメージを受けるか。NOT_SET はバニラ任せ (= 受けない)。 */
        net.kyori.adventure.util.TriState flyingFallDamage =
                net.kyori.adventure.util.TriState.NOT_SET;
        boolean onGround = true;
        boolean blocking;
        GameMode gameMode = GameMode.SURVIVAL;
        Vector velocity = new Vector(0, 0, 0);
        Location location;
        /** 付けられた attribute の修正。attribute 名 → キー → 量 */
        final Map<String, Map<NamespacedKey, Double>> attributes = new HashMap<>();
        final List<PotionEffect> potionEffects = new ArrayList<>();
        final List<Double> healed = new ArrayList<>();
        final List<Double> damaged = new ArrayList<>();
        final List<Location> teleports = new ArrayList<>();
        final List<String> messages = new ArrayList<>();
    }

    /** モックしたプレイヤーと、その記録。 */
    record FakePlayer(Player player, PlayerState state) {

        /** 指定した attribute に今かかっている割合の合計。 */
        double scalarOn(Attribute attribute) {
            return state.attributes.getOrDefault(nameOf(attribute), Map.of())
                    .values().stream().mapToDouble(Double::doubleValue).sum();
        }

        boolean hasAttribute(Attribute attribute) {
            return !state.attributes.getOrDefault(nameOf(attribute), Map.of()).isEmpty();
        }
    }

    static String nameOf(Attribute attribute) {
        // テスト用の偽レジストリはキーごとに一意なモックを返すので、キーで識別できる
        return attribute.getKey().toString();
    }

    /**
     * ダメージの出どころ。
     *
     * <p>本物を組むには DamageType のレジストリが要るので、テストではモックで代用する。
     * 効果側はこの中身を見ないので足りる。
     */
    static org.bukkit.damage.DamageSource damageSource() {
        return mock(org.bukkit.damage.DamageSource.class);
    }

    /**
     * アイテム。
     *
     * <p>イベントによっては受け取った {@code ItemStack} を {@code clone()} して持つので、
     * それを返さないと {@code getItem()} が null になる。
     */
    static ItemStack itemStack(Material type, int amount) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(type);
        when(stack.getAmount()).thenReturn(amount);
        when(stack.clone()).thenReturn(stack);
        return stack;
    }

    /**
     * その素材を「食べ物」として扱わせる。
     *
     * <p>26.x の {@code Material#isEdible()} はレジストリの {@code ItemType} へ委譲する。
     * テスト用の偽レジストリはキーごとに同じモックを返すので、ここで一度立てておけば
     * 以後ずっと食べ物として扱われる。
     */
    static void makeEdible(Material material) {
        TestRegistryAccess.override(material.asItemType(), "isEdible", true);
    }

    /**
     * {@code Bukkit.getCurrentTick()} が答える値を決める。
     *
     * <p>静的な入口なので、最初に呼ばれたときだけ Bukkit へサーバーを差し込む。
     * 以後は差し込み済みのサーバーの答えだけを差し替える。
     */
    static synchronized void installCurrentTick(Server server, int tick) {
        if (org.bukkit.Bukkit.getServer() == null) {
            // Bukkit.setServer は起動ログを組み立てようとしてバージョン情報を要求する。
            // ここで欲しいのは器だけなので、検証を通さずフィールドへ直接入れる。
            try {
                java.lang.reflect.Field field = org.bukkit.Bukkit.class.getDeclaredField("server");
                field.setAccessible(true);
                field.set(null, server);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Bukkit へサーバーを差し込めない", e);
            }
        }
        when(org.bukkit.Bukkit.getServer().getCurrentTick()).thenReturn(tick);
    }

    static World world() {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.getUID()).thenReturn(UUID.nameUUIDFromBytes("world".getBytes()));
        return world;
    }

    static Server server(World world) {
        Server server = mock(Server.class);
        when(server.getWorlds()).thenReturn(List.of(world));
        return server;
    }

    /** 素振り用のプレイヤー。位置は原点、HP 満タン、サバイバル。 */
    static FakePlayer player(String name, Server server, World world) {
        PlayerState state = new PlayerState();
        state.location = new Location(world, 0, 64, 0);

        Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        when(player.getUniqueId()).thenReturn(UUID.nameUUIDFromBytes(name.getBytes()));
        when(player.getServer()).thenReturn(server);
        when(player.getWorld()).thenReturn(world);
        when(player.isOnline()).thenReturn(true);

        when(player.getLocation()).thenAnswer(i -> state.location.clone());
        when(player.getHealth()).thenAnswer(i -> state.health);
        when(player.getFoodLevel()).thenAnswer(i -> state.foodLevel);
        when(player.getGameMode()).thenAnswer(i -> state.gameMode);
        when(player.getAllowFlight()).thenAnswer(i -> state.allowFlight);
        when(player.isFlying()).thenAnswer(i -> state.flying);
        when(player.isOnGround()).thenAnswer(i -> state.onGround);
        when(player.isBlocking()).thenAnswer(i -> state.blocking);
        when(player.isDead()).thenAnswer(i -> state.health <= 0);
        when(player.getVelocity()).thenAnswer(i -> state.velocity.clone());

        record$(player, state);
        attributes(player, state);
        inventory(player);
        return new FakePlayer(player, state);
    }

    /** 状態を書き換える呼び出しを記録に反映させる。 */
    private static void record$(Player player, PlayerState state) {
        org.mockito.Mockito.doAnswer(i -> {
            state.health = Math.min(state.maxHealth, state.health + (double) i.getArgument(0));
            state.healed.add(i.getArgument(0));
            return null;
        }).when(player).heal(org.mockito.ArgumentMatchers.anyDouble(), any());
        org.mockito.Mockito.doAnswer(i -> {
            state.health = Math.min(state.maxHealth, state.health + (double) i.getArgument(0));
            state.healed.add(i.getArgument(0));
            return null;
        }).when(player).heal(org.mockito.ArgumentMatchers.anyDouble());
        org.mockito.Mockito.doAnswer(i -> {
            state.damaged.add(i.getArgument(0));
            state.health -= (double) i.getArgument(0);
            return null;
        }).when(player).damage(org.mockito.ArgumentMatchers.anyDouble());
        org.mockito.Mockito.doAnswer(i -> {
            state.damaged.add(i.getArgument(0));
            state.health -= (double) i.getArgument(0);
            return null;
        }).when(player).damage(org.mockito.ArgumentMatchers.anyDouble(), any(
                org.bukkit.entity.Entity.class));
        org.mockito.Mockito.doAnswer(i -> {
            state.health = i.getArgument(0);
            return null;
        }).when(player).setHealth(org.mockito.ArgumentMatchers.anyDouble());
        org.mockito.Mockito.doAnswer(i -> {
            state.foodLevel = i.getArgument(0);
            return null;
        }).when(player).setFoodLevel(org.mockito.ArgumentMatchers.anyInt());
        org.mockito.Mockito.doAnswer(i -> {
            state.allowFlight = i.getArgument(0);
            return null;
        }).when(player).setAllowFlight(org.mockito.ArgumentMatchers.anyBoolean());
        org.mockito.Mockito.doAnswer(i -> {
            state.flying = i.getArgument(0);
            return null;
        }).when(player).setFlying(org.mockito.ArgumentMatchers.anyBoolean());
        org.mockito.Mockito.doAnswer(i -> {
            state.flyingFallDamage = i.getArgument(0);
            return null;
        }).when(player).setFlyingFallDamage(any(net.kyori.adventure.util.TriState.class));
        when(player.hasFlyingFallDamage()).thenAnswer(i -> state.flyingFallDamage);
        org.mockito.Mockito.doAnswer(i -> {
            state.velocity = i.getArgument(0);
            return null;
        }).when(player).setVelocity(any(Vector.class));
        org.mockito.Mockito.doAnswer(i -> {
            state.potionEffects.add(i.getArgument(0));
            return true;
        }).when(player).addPotionEffect(any(PotionEffect.class));
        when(player.teleportAsync(any())).thenAnswer(i -> {
            state.teleports.add(i.getArgument(0));
            return CompletableFuture.completedFuture(true);
        });
        org.mockito.Mockito.doAnswer(i -> {
            // Object へ受けてから文字列化する。直接 String.valueOf に渡すと
            // char[] のオーバーロードが選ばれてしまう
            Object message = i.getArgument(0);
            state.messages.add(String.valueOf(message));
            return null;
        }).when(player).sendMessage(any(net.kyori.adventure.text.Component.class));
    }

    /** attribute の付け外しを記録に反映させる。 */
    private static void attributes(Player player, PlayerState state) {
        Answer<AttributeInstance> instances = invocation -> {
            Attribute attribute = invocation.getArgument(0);
            Map<NamespacedKey, Double> onIt = state.attributes
                    .computeIfAbsent(nameOf(attribute), key -> new HashMap<>());

            AttributeInstance instance = mock(AttributeInstance.class);
            when(instance.getBaseValue()).thenReturn(20.0);
            when(instance.getValue()).thenReturn(20.0);
            org.mockito.Mockito.doAnswer(i -> {
                AttributeModifier modifier = i.getArgument(0);
                onIt.put(modifier.getKey(), modifier.getAmount());
                return null;
            }).when(instance).addTransientModifier(any());
            org.mockito.Mockito.doAnswer(i -> {
                onIt.remove((NamespacedKey) i.getArgument(0));
                return null;
            }).when(instance).removeModifier(any(NamespacedKey.class));
            return instance;
        };
        when(player.getAttribute(any())).thenAnswer(instances);
    }

    /** 手ぶらの持ち物。 */
    private static void inventory(Player player) {
        // 実 ItemStack の生成にはサーバーが要るのでモックで代用する
        ItemStack empty = mock(ItemStack.class);
        when(empty.getType()).thenReturn(Material.AIR);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(inventory.getItemInMainHand()).thenReturn(empty);
        when(inventory.getItemInOffHand()).thenReturn(empty);
        when(player.getInventory()).thenReturn(inventory);
    }
}
