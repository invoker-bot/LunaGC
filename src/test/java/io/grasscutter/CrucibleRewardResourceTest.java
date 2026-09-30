package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import emu.grasscutter.data.excels.activity.MpPlayGroupData;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.activity.crucible.CrucibleSceneController;
import emu.grasscutter.game.activity.crucible.CrucibleRewardDelivery;
import emu.grasscutter.game.activity.crucible.CrucibleRewards.Result;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.DropTableData;
import emu.grasscutter.scripts.ScriptLoader;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.game.world.Position;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class CrucibleRewardResourceTest {
    private static MpPlayGroupData crucible() throws Exception {
        var rows = new Gson().fromJson(Files.readString(Path.of("resources/ExcelBinOutput/MpPlayGroupExcelConfigData.json")), MpPlayGroupData[].class);
        return Arrays.stream(rows).filter(row -> row.getPlayId() == 1).findFirst().orElseThrow();
    }
    @Test void resourceLoaderMustRetainThePersonalRewardTableAndResinPrice() throws Exception {
        var gson = new Gson();
        var rows = JsonParser.parseString(Files.readString(
                Path.of("resources/ExcelBinOutput/MpPlayGroupExcelConfigData.json"))).getAsJsonArray();
        var source = java.util.stream.StreamSupport.stream(rows.spliterator(), false)
                .filter(row -> row.getAsJsonObject().get("playId").getAsInt() == 1)
                .findFirst().orElseThrow();
        var loaded = gson.toJsonTree(gson.fromJson(source, MpPlayGroupData.class)).getAsJsonObject();
        assertNotNull(loaded.get("rewardVec"), "The resource loader currently discards all nine reward levels");
        assertEquals(9, loaded.getAsJsonArray("rewardVec").size());
        assertEquals(305001001, loaded.get("rewardGroupId").getAsInt());
        assertEquals(3, loaded.get("rewardConfigId").getAsInt());
        assertEquals(40, loaded.get("resinCost").getAsInt());
    }
    @Test void personalWorldLevelsSelectAllNineOriginalTiersAndNewLevelsUseTheHistoricalCap() throws Exception {
        var data = crucible();
        int[] drops = {209000600, 209000800, 209000900, 209001000, 209001100, 209001200, 209001300, 209001400, 209001500};
        var tables = JsonParser.parseString(Files.readString(Path.of("resources/Server/DropTableExcelConfigData.json"))).getAsJsonArray();
        var tableIds = new HashSet<Integer>();
        for (var table : tables) tableIds.add(table.getAsJsonObject().get("id").getAsInt());
        for (int level = 0; level < 9; level++) {
            assertEquals(drops[level], data.rewardForWorldLevel(level).getDropId());
            assertEquals(4201 + level, data.rewardForWorldLevel(level).getRewardPreview());
            assertTrue(tableIds.contains(drops[level]), "Actual resource drop must exist");
        }
        assertEquals(drops[8], data.rewardForWorldLevel(9).getDropId());
        assertThrows(IllegalStateException.class, () -> data.rewardForWorldLevel(-1));
        assertThrows(IllegalStateException.class, () -> new MpPlayGroupData().rewardForWorldLevel(0));
    }
    @Test void originalLuaRewardPositionUsesTheGroundHeightAndDoesNotChangeSharedGroupData() throws Exception {
        Grasscutter.getConfig(); if (ScriptLoader.getEngine() == null) ScriptLoader.init();
        var group = SceneGroup.of(305001001).load(3);
        var point = CrucibleSceneController.rewardPosition(group, crucible().getRewardConfigId());
        assertEquals(2346.308f, point.pos.getX(), 0.001f);
        assertEquals(283.784f, point.pos.getY(), 0.001f);
        assertEquals(-1735.868f, point.pos.getZ(), 0.001f);
        assertTrue(CrucibleSceneController.withinRewardDistance(point.pos, point.pos));
        assertFalse(CrucibleSceneController.withinRewardDistance(new Position(2346.308f, 238.784f, -1735.868f), point.pos));
        assertFalse(CrucibleSceneController.withinRewardDistance(new Position(Float.NaN, 0, 0), point.pos));
        assertThrows(IllegalStateException.class, () -> CrucibleSceneController.rewardPosition(group, 99999));
        assertFalse(group.gadgets.containsKey(3), "Reward marker is a Lua point, not a permanently mounted gadget");
    }
    @Test void everyReachableResourceDropCanBeDeliveredBeforeResinIsSpent() throws Exception {
        var gson = new Gson();
        var tables = new HashMap<Integer, DropTableData>();
        for (var file : List.of("DropTableExcelConfigData.json", "DropSubTableExcelConfigData.json"))
            for (var table : gson.fromJson(Files.readString(Path.of("resources/Server", file)), DropTableData[].class))
                tables.put(table.getId(), table);
        var materials = new HashMap<Integer, ItemData>();
        for (var item : gson.fromJson(Files.readString(Path.of("resources/ExcelBinOutput/MaterialExcelConfigData.json")), ItemData[].class))
            materials.put(item.getId(), item);
        for (int level = 0; level < 9; level++) {
            var maximum = new HashMap<Integer, Integer>();
            collectDrops(crucible().rewardForWorldLevel(level).getDropId(), 1, tables, new HashSet<>(), maximum);
            assertFalse(maximum.isEmpty());
            var items = new ArrayList<GameItem>();
            for (var drop : maximum.entrySet()) {
                assertNotNull(materials.get(drop.getKey()), "Missing real item " + drop.getKey());
                items.add(new GameItem(materials.get(drop.getKey()), drop.getValue()));
            }
            var tab = new MaterialInventoryTab(20);
            assertEquals(Result.OK, CrucibleRewardDelivery.validate(items, ignored -> tab), "Resource tier " + level);
        }
        var gadgetRows = JsonParser.parseString(Files.readString(Path.of("resources/ExcelBinOutput/GadgetExcelConfigData.json"))).getAsJsonArray();
        JsonObject rewardGadget = null;
        for (var row : gadgetRows) if (row.getAsJsonObject().get("id").getAsInt() == CrucibleSceneController.REWARD_GADGET)
            rewardGadget = row.getAsJsonObject();
        assertNotNull(rewardGadget);
        assertEquals("MpPlayRewardPoint", rewardGadget.get("type").getAsString());
        assertTrue(rewardGadget.get("isInteractive").getAsBoolean());
    }
    private static void collectDrops(int id, int count, Map<Integer, DropTableData> tables,
                                     Set<Integer> path, Map<Integer, Integer> items) {
        var table = tables.get(id);
        if (table == null) { items.merge(id, count, Math::addExact); return; }
        assertTrue(path.add(id), "Drop graph cycle " + id);
        for (var child : table.getDropVec()) {
            if (child.getId() == 0 || child.getWeight() <= 0) continue;
            var range = child.getCountRange().split(";");
            int upper = (int) Math.ceil(Double.parseDouble(range[range.length - 1]));
            if (upper > 0) collectDrops(child.getId(), Math.multiplyExact(count, upper), tables, path, items);
        }
        path.remove(id);
    }
}
