package io.grasscutter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.CookRecipeData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.utils.JsonUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The 7.0 client dump writes CookRecipeData's two repeated ItemParam lists under swapped keys for
 * most rows. {@link CookRecipeData#onLoad()} undoes that, and this checks it against the real
 * resource dump when one is present.
 */
public final class CookRecipeSwapTest {

    private static final String EXCEL =
            "resources/ExcelBinOutput/CookRecipeExcelConfigData.json";

    private static List<CookRecipeData> loadRecipes() throws Exception {
        Path file = Path.of(EXCEL);
        assumeTrue(Files.exists(file), "the resource dump is not checked in");
        List<CookRecipeData> recipes = JsonUtils.loadToList(file, CookRecipeData.class);
        recipes.forEach(CookRecipeData::onLoad);
        return recipes;
    }

    private static Set<Integer> loadItemIds() throws Exception {
        // ItemData is loaded from four excels; only the ones present here contribute ids.
        Set<Integer> ids = new HashSet<>();
        for (String name :
                new String[] {
                    "MaterialExcelConfigData.json",
                    "WeaponExcelConfigData.json",
                    "ReliquaryExcelConfigData.json",
                    "HomeWorldFurnitureExcelConfigData.json"
                }) {
            Path file = Path.of("resources/ExcelBinOutput").resolve(name);
            if (!Files.exists(file)) {
                continue;
            }
            JsonUtils.loadToList(file, ItemData.class).forEach(item -> ids.add(item.getId()));
        }
        return ids;
    }

    @Test
    @DisplayName("every recipe's ingredients and outputs land on the right field")
    public void swapIsUndoneForEveryRow() throws Exception {
        List<CookRecipeData> recipes = loadRecipes();
        assertTrue(recipes.size() > 100, "expected the full recipe table");

        for (CookRecipeData recipe : recipes) {
            assertNotNull(recipe.getInputVec(), "ingredient list missing for " + recipe.getId());
            assertNotNull(recipe.getQualityOutputVec(), "output list missing for " + recipe.getId());

            // The output list is exactly one slot per QTE quality tier, all filled.
            assertEquals(3, recipe.getQualityOutputVec().size(), "output list size for " + recipe.getId());
            for (ItemParamData output : recipe.getQualityOutputVec()) {
                assertTrue(output.getItemId() > 0, "empty output slot for " + recipe.getId());
                assertTrue(output.getCount() > 0, "empty output count for " + recipe.getId());
            }
            // Ingredients occupy a wider list, and at least one is required.
            assertTrue(recipe.getInputVec().size() > 3, "ingredient list size for " + recipe.getId());
            assertTrue(
                    recipe.getInputVec().stream().anyMatch(i -> i.getItemId() > 0),
                    "no ingredients for " + recipe.getId());
        }
    }

    @Test
    @DisplayName("野菇鸡肉串 takes 蘑菇 and 禽肉 and yields the three skewer tiers")
    public void knownRecipeIsRightWayRound() throws Exception {
        List<CookRecipeData> recipes = loadRecipes();
        CookRecipeData recipe =
                recipes.stream().filter(r -> r.getId() == 1001).findFirst().orElseThrow();

        assertEquals(
                List.of(100011, 100064),
                recipe.getInputVec().stream()
                        .map(ItemParamData::getItemId)
                        .filter(id -> id > 0)
                        .toList());
        assertEquals(
                List.of(108011, 108012, 108013),
                recipe.getQualityOutputVec().stream().map(ItemParamData::getItemId).toList());
    }

    @Test
    @DisplayName("the handler's quality->output index always resolves to a real dish")
    public void qualityIndexResolvesToADish() throws Exception {
        // Mirrors CookingManager: int qualityIndex = quality == 0 ? 2 : quality - 1;
        List<CookRecipeData> recipes = loadRecipes();

        for (CookRecipeData recipe : recipes) {
            for (int quality : new int[] {0, 1, 2, 3}) {
                int qualityIndex = quality == 0 ? 2 : quality - 1;
                ItemParamData output = recipe.getQualityOutputVec().get(qualityIndex);
                assertTrue(
                        output.getItemId() > 0,
                        "recipe " + recipe.getId() + " quality " + quality + " has no dish");
            }
            // payItems consumes inputVec, so every entry it reads must be a real item too.
            assertTrue(
                    recipe.getInputVec().stream()
                            .mapToInt(ItemParamData::getItemId)
                            .filter(id -> id > 0)
                            .count()
                            >= 1,
                    "recipe " + recipe.getId() + " has no ingredients to pay");
        }
    }

    @Test
    @DisplayName("no recipe pays or produces an id the item table does not know")
    public void everyReferencedItemExists() throws Exception {
        Set<Integer> itemIds = loadItemIds();
        assumeTrue(!itemIds.isEmpty(), "the item excels are not checked in");

        List<String> missing = new ArrayList<>();
        for (CookRecipeData recipe : loadRecipes()) {
            for (ItemParamData ingredient : recipe.getInputVec()) {
                if (ingredient.getItemId() > 0 && !itemIds.contains(ingredient.getItemId())) {
                    missing.add(
                            "recipe " + recipe.getId() + " pays unknown item " + ingredient.getItemId());
                }
            }
            for (ItemParamData output : recipe.getQualityOutputVec()) {
                if (output.getItemId() > 0 && !itemIds.contains(output.getItemId())) {
                    missing.add(
                            "recipe "
                                    + recipe.getId()
                                    + " produces unknown item "
                                    + output.getItemId());
                }
            }
        }

        assertTrue(missing.isEmpty(), missing.size() + " dangling item refs: " + missing);
    }
}
