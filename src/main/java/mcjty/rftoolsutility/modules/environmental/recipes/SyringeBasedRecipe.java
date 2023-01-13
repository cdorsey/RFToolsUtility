package mcjty.rftoolsutility.modules.environmental.recipes;

import mcjty.rftoolsutility.modules.environmental.EnvironmentalModule;
import mcjty.rftoolsutility.modules.spawner.items.SyringeItem;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;

public class SyringeBasedRecipe extends ShapedRecipe {

    private final ResourceLocation mobId;
    private final int syringeIndex;

    public SyringeBasedRecipe(ResourceLocation id, String group, int width, int height, NonNullList<Ingredient> ingredients, ItemStack result, ResourceLocation mobId, int syringeIndex) {
        // @todo 1.19.3 (CraftingBookCategory.MISC)
        super(id, group, width, height, addMob(ingredients, mobId, syringeIndex), result);
        this.mobId = mobId;
        this.syringeIndex = syringeIndex;
    }

    public SyringeBasedRecipe(ShapedRecipe other, ResourceLocation mobId, int syringeIndex) {
        // @todo 1.19.3 category
        super(other.getId(), other.getGroup(), /*other.category(), */other.getWidth(), other.getHeight(), addMob(other.getIngredients(), mobId, syringeIndex), other.getResultItem());
        this.mobId = mobId;
        this.syringeIndex = syringeIndex;
    }

    private static NonNullList<Ingredient> addMob(NonNullList<Ingredient> input, ResourceLocation mobId, int syringeIndex) {
        NonNullList<Ingredient> output = NonNullList.withSize(input.size(), Ingredient.EMPTY);
        for (int i = 0 ; i < input.size() ; i++) {
            Ingredient ingredient = input.get(i);
            if (syringeIndex == i) {
                if (!ingredient.isEmpty() && ingredient.getItems().length > 0 && ingredient.getItems()[0].getItem() instanceof SyringeItem) {
                    ItemStack syringe = SyringeItem.createMobSyringe(mobId);
                    ingredient = Ingredient.of(syringe);
                } else {
                    throw new RuntimeException("Bad recipe. Index " + syringeIndex + " does not point to syringe!");
                }
            }
            output.set(i, ingredient);
        }
        return output;
    }

    @Override
    public boolean matches(@Nonnull CraftingContainer inv, @Nonnull Level level) {
        boolean matches = super.matches(inv, level);
        if (matches) {
            for (int i = 0 ; i < inv.getWidth() * inv.getHeight() ; i++) {
                ItemStack stack = inv.getItem(i);
                if (stack.getItem() instanceof SyringeItem) {
                    String mob = SyringeItem.getMobId(stack);
                    if (mob == null || !mob.equals(mobId.toString())) {
                        return false;
                    }
                    int amount = SyringeItem.getLevel(stack);
                    if (amount < 100) {
                        return false;
                    }
                }
            }
        }
        return matches;
    }

    public ResourceLocation getMobId() {
        return mobId;
    }

    @Nonnull
    @Override
    public RecipeSerializer<?> getSerializer() {
        return EnvironmentalModule.SYRINGE_SERIALIZER.get();
    }

    public int getSyringeIndex() {
        return syringeIndex;
    }
}
