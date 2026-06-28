package com.example.everlastingarmor;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingHurtEvent;
import net.neoforged.neoforge.event.entity.player.*;

import java.lang.reflect.Method;

@EventBusSubscriber(modid = EverlastingArmor.MODID)
public class ModEvents {

    // ========== 工具部分（与之前相同） ==========

    @SubscribeEvent
    public static void onDestroyItem(PlayerDestroyItemEvent event) {
        ItemStack stack = event.getOriginal();
        if (isToolOrArmor(stack)) {
            ItemStack brokenStack = stack.copy();
            brokenStack.set(DataComponents.DAMAGE, brokenStack.getMaxDamage());
            brokenStack.set(ModDataComponents.BROKEN.get(), true);
            if (!event.getEntity().getInventory().add(brokenStack)) {
                event.getEntity().drop(brokenStack, false);
            }
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        ItemStack stack = event.getEntity().getMainHandItem();
        if (isBroken(stack) && isTool(stack)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getEntity().getMainHandItem();
        if (isBroken(stack) && isTool(stack)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        ItemStack stack = event.getEntity().getMainHandItem();
        if (isBroken(stack) && isWeapon(stack)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAnvilRepair(AnvilRepairEvent event) {
        ItemStack result = event.getOutput();
        if (isBroken(result)) {
            if (result.getDamageValue() < result.getMaxDamage()) {
                result.remove(ModDataComponents.BROKEN.get());
            }
        }
    }

    @SubscribeEvent
    public static void onXpChange(PlayerXpEvent.XpChange event) {
        Player player = event.getEntity();
        int xp = event.getAmount();
        if (xp <= 0) return;

        int xpUsed = 0;
        ItemStack main = player.getMainHandItem();
        xpUsed += repairWithMending(player, main, xp - xpUsed);
        if (xpUsed < xp) {
            ItemStack off = player.getOffhandItem();
            xpUsed += repairWithMending(player, off, xp - xpUsed);
        }
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack armor = player.getItemBySlot(slot);
            xpUsed += repairWithMending(player, armor, xp - xpUsed);
            if (xpUsed >= xp) break;
        }
        if (xpUsed > 0) {
            event.setAmount(xp - xpUsed);
        }
    }

    private static int repairWithMending(Player player, ItemStack stack, int maxXp) {
        if (stack.isEmpty() || !isBroken(stack)) return 0;

        var mendingHolder = player.registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT)
                .getHolder(Enchantments.MENDING)
                .orElse(null);
        if (mendingHolder == null) return 0;

        int level = stack.getEnchantments().getLevel(mendingHolder);
        if (level <= 0) return 0;

        int damage = stack.getDamageValue();
        if (damage <= 0) return 0;
        int repair = Math.min(damage, maxXp * 2);
        int cost = (repair + 1) / 2;
        if (cost <= maxXp) {
            stack.set(DataComponents.DAMAGE, damage - repair);
            if (stack.getDamageValue() < stack.getMaxDamage()) {
                stack.remove(ModDataComponents.BROKEN.get());
            }
            return cost;
        }
        return 0;
    }

    // ========== 🛡️ 护甲失效使用 LivingHurtEvent（通过反射安全调用） ==========

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Player player)) return;

        try {
            // 通过反射获取 getAmount 和 setAmount 方法
            Method getAmount = event.getClass().getMethod("getAmount");
            Method setAmount = event.getClass().getMethod("setAmount", float.class);

            float damageAfterArmor = (float) getAmount.invoke(event);

            // 计算原始伤害
            float totalArmor = player.getArmorValue();
            float totalToughness = (float) player.getAttribute(Attributes.ARMOR_TOUGHNESS).getValue();
            float reduction = getDamageReduction(totalArmor, totalToughness);
            if (reduction >= 0.8f) reduction = 0.8f;
            float rawDamage = damageAfterArmor / (1.0f - reduction);

            // 计算破损护甲提供的护甲值
            float brokenArmor = 0;
            float brokenToughness = 0;
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack stack = player.getItemBySlot(slot);
                if (isBroken(stack) && stack.getItem() instanceof ArmorItem armorItem) {
                    brokenArmor += armorItem.getDefense();
                    brokenToughness += armorItem.getToughness();
                }
            }

            float effectiveArmor = Math.max(totalArmor - brokenArmor, 0);
            float effectiveToughness = Math.max(totalToughness - brokenToughness, 0);
            float newReduction = getDamageReduction(effectiveArmor, effectiveToughness);
            if (newReduction >= 0.8f) newReduction = 0.8f;

            float newDamage = rawDamage * (1.0f - newReduction);
            setAmount.invoke(event, newDamage);
        } catch (Exception e) {
            // 反射失败，静默忽略
        }
    }

    private static float getDamageReduction(float armor, float toughness) {
        return armor / (armor + 25.0f);
    }

    // ========== 辅助方法 ==========

    private static boolean isBroken(ItemStack stack) {
        return !stack.isEmpty() && stack.getOrDefault(ModDataComponents.BROKEN.get(), false);
    }

    private static boolean isTool(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof DiggerItem || item instanceof HoeItem;
    }

    private static boolean isWeapon(ItemStack stack) {
        return stack.getItem() instanceof SwordItem;
    }

    private static boolean isToolOrArmor(ItemStack stack) {
        Item item = stack.getItem();
        return isTool(stack) || isWeapon(stack) || item instanceof ArmorItem;
    }
}