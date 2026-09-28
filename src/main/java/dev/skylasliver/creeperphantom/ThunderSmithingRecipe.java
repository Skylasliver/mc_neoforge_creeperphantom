package dev.skylasliver.creeperphantom;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;

/** The vanilla smithing UI, with an explicit two-membrane requirement. */
public final class ThunderSmithingRecipe extends SmithingTransformRecipe {
    public static final int MEMBRANE_COST = 2;
    public static final ResourceKey<Enchantment> INDUCTIVE_RECHARGE=ResourceKey.create(
        Registries.ENCHANTMENT,ResourceLocation.fromNamespaceAndPath(CreeperPhantomMod.MOD_ID,"inductive_recharge"));

    public static final ResourceKey<Enchantment> THUNDER_CONTINUANCE=ResourceKey.create(
        Registries.ENCHANTMENT,ResourceLocation.fromNamespaceAndPath(CreeperPhantomMod.MOD_ID,"thunder_continuance"));

    public ThunderSmithingRecipe() {
        super(Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),Ingredient.of(Items.ELYTRA),
            Ingredient.of(CreeperPhantomMod.THUNDER_MEMBRANE.get()),new ItemStack(CreeperPhantomMod.THUNDER_ELYTRA.get()));
    }

    @Override public boolean matches(SmithingRecipeInput input,Level level) {
        return ThunderConfig.value(ThunderConfig.CRAFTING_ENABLED) && input.addition().getCount()>=MEMBRANE_COST && super.matches(input,level);
    }

    @Override public ItemStack assemble(SmithingRecipeInput input,HolderLookup.Provider registries) {
        if (!ThunderConfig.value(ThunderConfig.CRAFTING_ENABLED)) return ItemStack.EMPTY;
        // Transmute preserves name, damage, repair cost and all original enchantments.
        return enchantResult(super.assemble(input,registries),registries);
    }

    @Override public ItemStack getResultItem(HolderLookup.Provider registries) {
        return enchantResult(new ItemStack(CreeperPhantomMod.THUNDER_ELYTRA.get()),registries);
    }

    public static ItemStack enchantResult(ItemStack result,HolderLookup.Provider registries) {
        result.enchant(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(INDUCTIVE_RECHARGE),1);
        result.enchant(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(THUNDER_CONTINUANCE),1);
        return result;
    }

    @Override public RecipeSerializer<?> getSerializer() { return CreeperPhantomMod.THUNDER_SMITHING.get(); }

    public static final class Serializer implements RecipeSerializer<ThunderSmithingRecipe> {
        private static final MapCodec<ThunderSmithingRecipe> CODEC=MapCodec.unit(ThunderSmithingRecipe::new);
        private static final StreamCodec<RegistryFriendlyByteBuf,ThunderSmithingRecipe> STREAM=
            StreamCodec.of((buf,recipe)->{},buf->new ThunderSmithingRecipe());
        @Override public MapCodec<ThunderSmithingRecipe> codec(){return CODEC;}
        @Override public StreamCodec<RegistryFriendlyByteBuf,ThunderSmithingRecipe> streamCodec(){return STREAM;}
    }
}
