package io.github.modifier;

import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExhaustionEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;

/**
 * 複数のモディファイアを束ねて 1 つとして扱う。よくばりが使う。
 *
 * <p>メタ情報 (id・名前・アイコン・重み) は頭 ({@code head}) のもの。効果のフックは中身へ
 * 順番に配る。死を打ち消すものは、どれか 1 つが打ち消した時点で止める。
 */
public final class ModifierBundle implements Modifier {

    private final Modifier head;
    private final List<Modifier> parts;

    public ModifierBundle(Modifier head, List<Modifier> parts) {
        this.head = head;
        this.parts = List.copyOf(parts);
    }

    /** 束ねている中身。 */
    public List<Modifier> parts() {
        return parts;
    }

    @Override
    public String id() {
        return head.id();
    }

    @Override
    public Component displayName() {
        return head.displayName();
    }

    /** 頭の説明に続けて、中身それぞれの名前と説明を並べる。 */
    @Override
    public List<Component> description() {
        List<Component> lines = new ArrayList<>(head.description());
        for (Modifier part : parts) {
            lines.add(Component.text("◆ ", NamedTextColor.YELLOW).append(part.displayName())
                    .decoration(TextDecoration.ITALIC, false));
            lines.addAll(part.description());
        }
        return List.copyOf(lines);
    }

    @Override
    public Material iconBase() {
        return head.iconBase();
    }

    @Override
    public Key iconModel() {
        return head.iconModel();
    }

    @Override
    public int weight() {
        return head.weight();
    }

    @Override
    public List<StartingItems.Item> startingItems() {
        List<StartingItems.Item> items = new ArrayList<>(head.startingItems());
        parts.forEach(part -> items.addAll(part.startingItems()));
        return List.copyOf(items);
    }

    @Override
    public Modifier resolveFor(Player player) {
        return this;
    }

    // ---- 効果はすべて中身へ ----------------------------------------------------

    @Override
    public void apply(Player player) {
        parts.forEach(part -> part.apply(player));
    }

    @Override
    public void remove(Player player) {
        parts.forEach(part -> part.remove(player));
    }

    @Override
    public void onDamaged(Player player, EntityDamageEvent event) {
        parts.forEach(part -> part.onDamaged(player, event));
    }

    @Override
    public void onDealtDamage(Player player, EntityDamageByEntityEvent event) {
        parts.forEach(part -> part.onDealtDamage(player, event));
    }

    @Override
    public void onDamagedConfirmed(Player player, EntityDamageEvent event) {
        parts.forEach(part -> part.onDamagedConfirmed(player, event));
    }

    @Override
    public void onKill(Player player, LivingEntity victim) {
        parts.forEach(part -> part.onKill(player, victim));
    }

    @Override
    public void onKillDrops(Player player, EntityDeathEvent event) {
        parts.forEach(part -> part.onKillDrops(player, event));
    }

    @Override
    public void onExhaustion(Player player, EntityExhaustionEvent event) {
        parts.forEach(part -> part.onExhaustion(player, event));
    }

    @Override
    public void onBlockDrops(Player player, BlockDropItemEvent event) {
        parts.forEach(part -> part.onBlockDrops(player, event));
    }

    @Override
    public void onHarvest(Player player, PlayerHarvestBlockEvent event) {
        parts.forEach(part -> part.onHarvest(player, event));
    }

    @Override
    public void onItemDamage(Player player, PlayerItemDamageEvent event) {
        parts.forEach(part -> part.onItemDamage(player, event));
    }

    @Override
    public void onConsume(Player player, PlayerItemConsumeEvent event) {
        parts.forEach(part -> part.onConsume(player, event));
    }

    @Override
    public void onFoodChange(Player player, FoodLevelChangeEvent event) {
        parts.forEach(part -> part.onFoodChange(player, event));
    }

    @Override
    public void onPotionEffect(Player player, EntityPotionEffectEvent event) {
        parts.forEach(part -> part.onPotionEffect(player, event));
    }

    @Override
    public void onProjectileHit(Player player, ProjectileHitEvent event) {
        parts.forEach(part -> part.onProjectileHit(player, event));
    }

    @Override
    public void onFish(Player player, PlayerFishEvent event) {
        parts.forEach(part -> part.onFish(player, event));
    }

    @Override
    public void onItemDrop(Player player, PlayerDropItemEvent event) {
        parts.forEach(part -> part.onItemDrop(player, event));
    }

    @Override
    public void onNightSkipped(Player player) {
        parts.forEach(part -> part.onNightSkipped(player));
    }

    @Override
    public void onToggleFlight(Player player, PlayerToggleFlightEvent event) {
        parts.forEach(part -> part.onToggleFlight(player, event));
    }

    @Override
    public void onCraftPrepared(Player player, PrepareItemCraftEvent event) {
        parts.forEach(part -> part.onCraftPrepared(player, event));
    }

    @Override
    public void onInventoryClick(Player player, InventoryClickEvent event) {
        parts.forEach(part -> part.onInventoryClick(player, event));
    }

    @Override
    public void onInteract(Player player, PlayerInteractEvent event) {
        parts.forEach(part -> part.onInteract(player, event));
    }

    @Override
    public void onDeath(Player player, PlayerDeathEvent event) {
        parts.forEach(part -> part.onDeath(player, event));
    }

    @Override
    public void tick(Player player) {
        parts.forEach(part -> part.tick(player));
    }

    @Override
    public boolean interceptDeath(Player self, EntityDamageEvent event) {
        for (Modifier part : parts) {
            if (part.interceptDeath(self, event)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean interceptOtherDeath(Player self, Player dying, EntityDamageEvent event) {
        for (Modifier part : parts) {
            if (part.interceptOtherDeath(self, dying, event)) {
                return true;
            }
        }
        return false;
    }
}
