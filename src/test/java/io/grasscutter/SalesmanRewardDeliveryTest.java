package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.game.activity.salesman.SalesmanRewardDelivery;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class SalesmanRewardDeliveryTest {
    private static ItemData item(int id, String type, int limit) {
        return new Gson().fromJson("{\"id\":" + id + ",\"itemType\":\"ITEM_MATERIAL\",\"materialType\":\""
                + type + "\",\"stackLimit\":" + limit + "}", ItemData.class);
    }
    @Test void allSevenOriginalRewardRowsPassPreflightAndHaveThirtyPrimogems() throws Exception {
        var gson = new Gson(); var definitions = new HashMap<Integer, ItemData>();
        for (var row : gson.fromJson(Files.readString(Path.of("resources/ExcelBinOutput/MaterialExcelConfigData.json")), ItemData[].class))
            definitions.put(row.getId(), row);
        var tab = new MaterialInventoryTab(100);
        int count = 0;
        for (var row : gson.fromJson(Files.readString(Path.of("resources/ExcelBinOutput/RewardExcelConfigData.json")), RewardData[].class)) {
            if (row.getId() < 470001 || row.getId() > 470007) continue;
            row.onLoad(); count++;
            assertEquals(30, row.getRewardItemList().stream().filter(p -> p.getId() == 201).mapToInt(ItemParamData::getCount).sum());
            assertEquals(0, SalesmanRewardDelivery.validate(row.getRewardItemList(), definitions::get, ignored -> tab, ignored -> 0));
        }
        assertEquals(7, count);
    }
    @Test void newStacksCombinedCountsAndExistingStacksRespectCapacityAndLimits() {
        var definitions = Map.of(104002, item(104002, "MATERIAL_EXP_FRUIT", 100), 104003, item(104003, "MATERIAL_EXP_FRUIT", 100));
        var tab = new MaterialInventoryTab(1);
        assertEquals(Retcode.RET_ITEM_EXCEED_LIMIT_VALUE, SalesmanRewardDelivery.validate(
                List.of(new ItemParamData(104002, 3), new ItemParamData(104003, 3)), definitions::get, ignored -> tab, ignored -> 0));
        tab.onAddItem(new GameItem(definitions.get(104002), 90));
        assertEquals(0, SalesmanRewardDelivery.validate(List.of(new ItemParamData(104002, 5), new ItemParamData(104002, 5)),
                definitions::get, ignored -> tab, ignored -> 0));
        assertEquals(Retcode.RET_ITEM_EXCEED_LIMIT_VALUE, SalesmanRewardDelivery.validate(
                List.of(new ItemParamData(104002, 6), new ItemParamData(104002, 5)), definitions::get, ignored -> tab, ignored -> 0));
    }
    @Test void invalidCountsUnknownItemsUnsupportedTypesAndCurrencyOverflowFailBeforeGrant() {
        var gson = new Gson();
        var definitions = Map.of(104002, item(104002, "MATERIAL_EXP_FRUIT", 100),
                201, gson.fromJson("{\"id\":201,\"itemType\":\"ITEM_VIRTUAL\"}", ItemData.class),
                202, gson.fromJson("{\"id\":202,\"itemType\":\"ITEM_VIRTUAL\"}", ItemData.class),
                203, gson.fromJson("{\"id\":203,\"itemType\":\"ITEM_VIRTUAL\"}", ItemData.class),
                9001, gson.fromJson("{\"id\":9001,\"itemType\":\"ITEM_WEAPON\"}", ItemData.class));
        var tab = new MaterialInventoryTab(10);
        for (var rows : List.of(List.<ItemParamData>of(), List.of(new ItemParamData(104002, 0)),
                List.of(new ItemParamData(104002, -1)), List.of(new ItemParamData(999999, 1)),
                List.of(new ItemParamData(203, 1)), List.of(new ItemParamData(9001, 1))))
            assertEquals(Retcode.RET_SVR_ERROR_VALUE, SalesmanRewardDelivery.validate(rows, definitions::get, ignored -> tab, ignored -> 0));
        assertEquals(Retcode.RET_ITEM_EXCEED_LIMIT_VALUE, SalesmanRewardDelivery.validate(
                List.of(new ItemParamData(104002, 101)), definitions::get, ignored -> tab, ignored -> 0), "Do not let GameItem truncate the reward");
        for (int id : List.of(201, 202)) assertEquals(Retcode.RET_ITEM_EXCEED_LIMIT_VALUE,
                SalesmanRewardDelivery.validate(List.of(new ItemParamData(id, 30)), definitions::get, ignored -> tab, ignored -> Integer.MAX_VALUE - 29));
        assertEquals(Retcode.RET_SVR_ERROR_VALUE, SalesmanRewardDelivery.validate(List.of(new ItemParamData(104002, 1)), definitions::get, ignored -> null, ignored -> 0));
    }
}
