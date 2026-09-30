package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.activity.crucible.CrucibleRewardDelivery;
import emu.grasscutter.game.activity.crucible.CrucibleRewards.Result;
import emu.grasscutter.game.inventory.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class CrucibleRewardDeliveryTest {
    private static GameItem book(int id, int count, int limit) {
        var data = new Gson().fromJson("{\"id\":" + id + ",\"itemType\":\"ITEM_MATERIAL\","
                + "\"materialType\":\"MATERIAL_EXP_FRUIT\",\"stackLimit\":" + limit + "}", ItemData.class);
        return new GameItem(data, count);
    }
    private static GameItem exp(int id) {
        return new GameItem(new Gson().fromJson("{\"id\":" + id + ",\"itemType\":\"ITEM_VIRTUAL\"}", ItemData.class), 200);
    }
    @Test void countsDistinctNewStacksAndAllowsAnExistingStackInAFullBag() {
        var tab = new MaterialInventoryTab(1);
        assertEquals(Result.INVENTORY_FULL, CrucibleRewardDelivery.validate(
                List.of(book(104002, 10, 99999), book(104003, 2, 99999)), ignored -> tab));
        tab.onAddItem(book(104002, 10, 99999));
        assertEquals(Result.OK, CrucibleRewardDelivery.validate(List.of(exp(102), exp(105), book(104002, 10, 99999)), ignored -> tab));
        assertEquals(Result.INVENTORY_FULL, CrucibleRewardDelivery.validate(List.of(book(104003, 2, 99999)), ignored -> tab));
    }
    @Test void duplicatedDropEntriesMustFitTheirCombinedStackLimit() {
        var tab = new MaterialInventoryTab(1); tab.onAddItem(book(104002, 90, 100));
        assertEquals(Result.INVENTORY_FULL, CrucibleRewardDelivery.validate(
                List.of(book(104002, 6, 100), book(104002, 5, 100)), ignored -> tab));
        assertEquals(Result.OK, CrucibleRewardDelivery.validate(
                List.of(book(104002, 6, 100), book(104002, 4, 100)), ignored -> tab));
        var empty = new MaterialInventoryTab(1);
        assertEquals(Result.OK, CrucibleRewardDelivery.validate(
                List.of(book(104003, 2, 100), book(104003, 2, 100)), ignored -> empty));
    }
    @Test void invalidOrUnsupportedDropsFailBeforeCharging() {
        var tab = new MaterialInventoryTab(5);
        for (var items : List.of(List.<GameItem>of(), List.of(book(104002, 0, 99999)), List.of(exp(999999)), List.of(new GameItem())))
            assertEquals(Result.INVALID_REWARD, CrucibleRewardDelivery.validate(items, ignored -> tab));
        assertEquals(Result.INVALID_REWARD, CrucibleRewardDelivery.validate(List.of(book(104002, 1, 99999)), ignored -> null));
    }
}
