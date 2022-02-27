/*******************************************************************************
 * Copyright 2014-2017, the Biomes O' Plenty Team
 *
 * This work is licensed under a Creative Commons Attribution-NonCommercial-NoDerivatives 4.0 International Public License.
 *
 * To view a copy of this license, visit http://creativecommons.org/licenses/by-nc-nd/4.0/.
 *
 * Original: https://github.com/Glitchfiend/BiomesOPlenty/blob/0f8be0526e01d918cf8f22d4904a3b74981dee6f/src/main/java/biomesoplenty/common/util/inventory/CraftingUtil.java
 * (edited to work with multiple mods)
 ******************************************************************************/
package vazkii.potionfingers;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.oredict.OreIngredient;

import javax.annotation.Nonnull;
import java.util.*;

@Mod.EventBusSubscriber
public final class RecipeHandler {

    private static final List<ResourceLocation> usedNames = new ArrayList<>();

    private static final List<IRecipe> recipes = new ArrayList<>();

    // Many bridge methods for backwards compatibility

    public static void addOreDictRecipe(ItemStack output, Object... inputs) {
        addShapedRecipe(output, inputs);
    }

    public static void addShapedRecipe(ItemStack output, Object... inputs) {
        String namespace = getNamespace();
        ArrayList<String> pattern = Lists.newArrayList();
        Map<String, Ingredient> key = Maps.newHashMap();
        Iterator<Object> itr = Arrays.asList(inputs).iterator();

        while (itr.hasNext()) {
            Object obj = itr.next();

            if (obj instanceof String) {
                String str = (String) obj;

                if (str.length() > 3)
                    throw new IllegalArgumentException("Invalid string length for recipe " + str.length());

                if (pattern.size() <= 2)
                    pattern.add(str);
                else
                    throw new IllegalArgumentException("Recipe has too many crafting rows!");
            } else if (obj instanceof Character)
                key.put(((Character) obj).toString(), asIngredient(itr.next()));
            else
                throw new IllegalArgumentException("Unexpected argument of type " + obj.getClass().toString());
        }

        int width = pattern.get(0).length();
        int height = pattern.size();

        try {
            key.put(" ", Ingredient.EMPTY);
            NonNullList<Ingredient> ingredients = prepareMaterials(pattern.toArray(new String[0]), key, width, height);
            ShapedRecipes recipe = new ShapedRecipes(outputGroup(output), width, height, ingredients, output);
            addRecipe(unusedLocForOutput(namespace, output), recipe);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    // copy from vanilla
    @Nonnull
    private static NonNullList<Ingredient> prepareMaterials(@Nonnull String[] ingredientKeys, Map<String, Ingredient> ingredients, int width, int height) {
        NonNullList<Ingredient> ingredientList = NonNullList.withSize(width * height, Ingredient.EMPTY);

        for (int i = 0; i < ingredientKeys.length; ++i)
            for (int j = 0; j < ingredientKeys[i].length(); ++j) {
                String s = ingredientKeys[i].substring(j, j + 1);
                Ingredient ingredient = ingredients.get(s);

                ingredientList.set(j + width * i, ingredient);
            }

        return ingredientList;
    }

    public static void addRecipe(@Nonnull ResourceLocation res, @Nonnull IRecipe recipe) {
        if (recipe.getRecipeOutput().isEmpty())
            throw new IllegalArgumentException("recipe output was empty");

        recipe.setRegistryName(res);
        usedNames.add(res);
        recipes.add(recipe);
    }

    public static Ingredient asIngredient(Object object) {
        if (object instanceof Ingredient)
            return (Ingredient) object;

        else if (object instanceof Item)
            return Ingredient.fromItem((Item) object);

        else if (object instanceof ItemStack)
            return Ingredient.fromStacks((ItemStack) object);

        else if (object instanceof String)
            return new OreIngredient((String) object);


        throw new IllegalArgumentException("Cannot convert object of type " + object.getClass().toString() + " to an Ingredient!");
    }

    @Nonnull
    private static ResourceLocation unusedLocForOutput(String namespace, @Nonnull ItemStack output) {
        ResourceLocation baseLoc = new ResourceLocation(namespace, Objects.requireNonNull(output.getItem().getRegistryName()).getPath());
        ResourceLocation recipeLoc = baseLoc;
        int index = 0;

        // find unused recipe name
        while (usedNames.contains(recipeLoc)) {
            index++;
            recipeLoc = new ResourceLocation(namespace, baseLoc.getPath() + "_" + index);
        }

        return recipeLoc;
    }

    @Nonnull
    private static String outputGroup(@Nonnull ItemStack output) {
        return Objects.requireNonNull(output.getItem().getRegistryName()).toString();
    }

    @Nonnull
    private static String getNamespace() {
        return Objects.requireNonNull(Loader.instance().activeModContainer()).getModId();
    }

    @SubscribeEvent
    public static void register(RegistryEvent.Register<IRecipe> event) {
        for (IRecipe recipe : recipes)
            event.getRegistry().register(recipe);
    }
}
