package dev.skylasliver.creeperphantom.client.jei;

import dev.skylasliver.creeperphantom.CreeperPhantomMod;
import dev.skylasliver.creeperphantom.ThunderSmithingRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.recipe.category.extensions.vanilla.smithing.ISmithingCategoryExtension;
import mezz.jei.api.registration.IVanillaCategoryExtensionRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Loaded by JEI's plugin discovery only; common code never references JEI classes. */
@JeiPlugin
public final class ThunderJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(CreeperPhantomMod.MOD_ID, "thunder_smithing");
    }

    @Override
    public void registerVanillaCategoryExtensions(IVanillaCategoryExtensionRegistration registration) {
        registration.getSmithingCategory().addExtension(ThunderSmithingRecipe.class, new SmithingExtension());
    }

    public static final class SmithingExtension implements ISmithingCategoryExtension<ThunderSmithingRecipe> {
        @Override
        public <T extends IIngredientAcceptor<T>> void setTemplate(ThunderSmithingRecipe recipe, T slot) {
            slot.addItemStack(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
        }

        @Override
        public <T extends IIngredientAcceptor<T>> void setBase(ThunderSmithingRecipe recipe, T slot) {
            slot.addItemStack(new ItemStack(Items.ELYTRA));
        }

        @Override
        public <T extends IIngredientAcceptor<T>> void setAddition(ThunderSmithingRecipe recipe, T slot) {
            slot.addItemStack(new ItemStack(CreeperPhantomMod.THUNDER_MEMBRANE.get(), ThunderSmithingRecipe.MEMBRANE_COST));
        }

        @Override
        public <T extends IIngredientAcceptor<T>> void setOutput(ThunderSmithingRecipe recipe, T slot) {
            var level = Minecraft.getInstance().level;
            if (level != null) {
                slot.addItemStack(recipe.getResultItem(level.registryAccess()));
            }
        }
    }
}
