package emu.grasscutter.game.shop;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.*;
import it.unimi.dsi.fastutil.ints.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class ShopCatalogTest {
    @TempDir Path dir;
    ItemData previous;

    @BeforeEach
    void before() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        previous =
                GameData.getItemDataMap()
                        .put(
                                201,
                                new Gson().fromJson("{\"id\":201,\"itemType\":\"ITEM_VIRTUAL\"}", ItemData.class));
    }

    @AfterEach
    void after() {
        if (previous == null) GameData.getItemDataMap().remove(201);
        else GameData.getItemDataMap().put(201, previous);
    }

    ShopInfo good() {
        var good = new ShopInfo();
        good.setGoodsId(5);
        good.setGoodsItem(new ItemParamData(201, 60));
        good.setCostItemList(new ArrayList<>());
        good.setBuyLimit(1);
        return good;
    }

    Int2ObjectMap<List<ShopInfo>> base(ShopInfo good) {
        var base = new Int2ObjectOpenHashMap<List<ShopInfo>>();
        base.put(902, List.of(good));
        return base;
    }

    @Test
    void disableAndZeroPriceOverridesSurviveReloadAndResetRestoresResources() throws Exception {
        var path = dir.resolve("ShopOverrides.json");
        var original = good();
        original.setScoin(100);
        var catalog = new ShopCatalog(path);
        catalog.replaceBase(base(original));
        catalog.edit(902, 5, "disable", null);
        assertFalse(catalog.active().containsKey(902));
        var override = good();
        catalog.edit(902, 5, "save", override);
        catalog.edit(902, 5, "enable", null);
        var reloaded = new ShopCatalog(path);
        reloaded.replaceBase(base(original));
        assertEquals(0, reloaded.active().get(902).get(0).getScoin());
        reloaded.edit(902, 5, "reset", null);
        assertEquals(100, reloaded.active().get(902).get(0).getScoin());
    }

    @Test
    void customGoodsHaveStableUniqueIdsAndCanBeRemoved() throws Exception {
        var catalog = new ShopCatalog(dir.resolve("shop.json"));
        catalog.replaceBase(base(good()));
        int a = catalog.edit(902, 0, "save", good()), b = catalog.edit(1001, 0, "save", good());
        assertTrue(a >= ShopCatalog.CUSTOM_ID_BASE);
        assertEquals(a + 1, b);
        assertThrows(IllegalArgumentException.class, () -> catalog.edit(1001, a, "save", good()));
        catalog.edit(902, a, "reset", null);
        assertEquals(2, catalog.entries().size());
    }

    @Test
    void invalidPriceCannotChangeThePublishedCatalogue() throws Exception {
        var path = dir.resolve("shop.json");
        var catalog = new ShopCatalog(path);
        catalog.replaceBase(base(good()));
        var invalid = good();
        invalid.setScoin(-1);
        assertThrows(IllegalArgumentException.class, () -> catalog.edit(902, 5, "save", invalid));
        assertFalse(Files.exists(path));
        assertEquals(0, catalog.active().get(902).get(0).getScoin());
    }

    @Test
    void failedWriteLeavesLiveGoodsAndProductSwitchesUnchanged() throws Exception {
        var path = dir.resolve("shop.json");
        var catalog = new ShopCatalog(path);
        catalog.replaceBase(base(good()));
        Files.createDirectory(path);
        assertThrows(Exception.class, () -> catalog.edit(902, 5, "disable", null));
        assertTrue(catalog.active().containsKey(902));
        assertThrows(Exception.class, () -> catalog.setProductEnabled("card:101", false));
        assertTrue(catalog.productEnabled("card:101"));
    }

    @Test
    void ordinaryResourceUpperLevelCanBePreservedWhenLoweringTheMinimum() throws Exception {
        var path = dir.resolve("paimon-shop.json");
        var original = good();
        original.setMinLevel(10);
        original.setMaxLevel(99);
        var source = new Int2ObjectOpenHashMap<List<ShopInfo>>();
        source.put(1001, List.of(original));
        var catalog = new ShopCatalog(path);
        catalog.replaceBase(source);
        var edited = ShopCatalog.copy(original);
        edited.setMinLevel(4);
        catalog.edit(1001, original.getGoodsId(), "save", edited);
        var reloaded = new ShopCatalog(path);
        reloaded.replaceBase(source);
        assertEquals(4, reloaded.active().get(1001).get(0).getMinLevel());
        assertEquals(99, reloaded.active().get(1001).get(0).getMaxLevel());
        assertEquals(10, original.getMinLevel());
    }

    @Test
    void beyondLevelAndCurrencyPriceOverridesSurviveReload() throws Exception {
        var path = dir.resolve("beyond-shop.json");
        var catalog = new ShopCatalog(path);
        var override = good();
        override.setMinLevel(1);
        override.setMaxLevel(99);
        override.setBeyondMcoin(1200);
        int id = catalog.edit(101000, 0, "save", override);
        var reloaded = new ShopCatalog(path);
        reloaded.replaceBase(new Int2ObjectOpenHashMap<>());
        var saved = reloaded.active().get(101000).get(0);
        assertEquals(id, saved.getGoodsId());
        assertEquals(99, saved.getMaxLevel());
        assertEquals(1200, saved.getBeyondMcoin());
        assertThrows(IllegalArgumentException.class, () -> catalog.edit(902, 0, "save", override));
        override.setMaxLevel(100);
        assertThrows(IllegalArgumentException.class, () -> catalog.edit(101000, id, "save", override));
        assertEquals(99, catalog.active().get(101000).get(0).getMaxLevel());
    }

    @Test
    void resourceRefreshTypeSurvivesJsonPersistence() {
        var resource =
                new Gson()
                        .fromJson(
                                "{\"goodsId\":5,\"itemId\":201,\"itemCount\":1,\"refreshType\":\"SHOP_REFRESH_DAILY\",\"refreshParam\":1}",
                                ShopGoodsData.class);
        resource.onLoad();
        var good = new ShopInfo(resource);
        assertEquals(
                ShopInfo.ShopRefreshType.SHOP_REFRESH_DAILY, ShopCatalog.copy(good).getShopRefreshType());
        assertTrue(ShopSystem.getShopNextRefreshTime(good) > 0);
    }
}
