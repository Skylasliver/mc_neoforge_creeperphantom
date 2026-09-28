package dev.skylasliver.creeperphantom.client;

import dev.skylasliver.creeperphantom.CreeperPhantomMod;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

@EventBusSubscriber(modid=CreeperPhantomMod.MOD_ID, value=Dist.CLIENT)
public final class EnchantmentTooltipEvents {
    @SubscribeEvent
    public static void addDescriptions(ItemTooltipEvent event) {
        var lines = event.getToolTip();
        // Skip the item name; insert only after an enchantment that is actually visible.
        for (int i = 1; i < lines.size(); i++) {
            if (!(lines.get(i).getContents() instanceof TranslatableContents contents)) continue;
            String key = contents.getKey();
            if (key.equals("enchantment.skylasliver_creeperphantom.inductive_recharge")
                || key.equals("enchantment.skylasliver_creeperphantom.thunder_continuance")) {
                lines.add(++i, Component.translatable(key + ".desc").withStyle(ChatFormatting.GRAY));
            }
        }
    }
}
