package emu.grasscutter.game.shop;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.server.packet.send.PacketGetShopRsp;
import emu.grasscutter.utils.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;

class ShopPresentationTest {
  final Gson gson = new Gson();
  final Map<Integer, ShopRotateData> oldRotations = new HashMap<>();

  @BeforeEach
  void resources() throws Exception {
    Class.forName("emu.grasscutter.Grasscutter");
    oldRotations.putAll(GameData.getShopRotateDataMap());
    GameData.getShopRotateDataMap().clear();
    for (var row :
        JsonUtils.loadToList(
            FileUtils.getExcelPath("ShopRotateExcelConfigData.json"), ShopRotateData.class)) {
      GameData.getShopRotateDataMap().put(row.getId(), row);
    }
  }

  @AfterEach
  void restore() {
    GameData.getShopRotateDataMap().clear();
    GameData.getShopRotateDataMap().putAll(oldRotations);
  }

  ShopInfo rotating(int group) {
    var row =
        gson.fromJson(
            "{\"goodsId\":102005,\"shopType\":1001,\"itemCount\":1,\"rotateId\":"
                + group
                + ",\"beginTime\":\"2020-10-01 04:00:01\",\"endTime\":\"2035-01-01 00:00:00\"}",
            ShopGoodsData.class);
    row.onLoad();
    return new ShopInfo(row);
  }

  int at(String date) {
    return Math.toIntExact(
        LocalDateTime.parse(date).atZone(ZoneId.of("Asia/Shanghai")).toEpochSecond());
  }

  @Test
  void monthlyCharactersFollowResourcesAndChangeAtFourAm() {
    var first = rotating(10201);
    var second = rotating(10202);
    assertEquals(1020, first.resolveGoodsItem(at("2026-10-01T04:00:00")).getId());
    assertEquals(1025, second.resolveGoodsItem(at("2026-10-01T03:59:59")).getId());
    assertEquals(1021, second.resolveGoodsItem(at("2026-10-01T04:00:00")).getId());
    assertEquals(1031, first.resolveGoodsItem(at("2027-01-01T04:00:00")).getId());
    assertEquals(0, first.getGoodsItem().getId(), "A query must not freeze the shared monthly row");
  }

  @Test
  void monthlyWeaponsRotateBetweenTheResourceSets() {
    var sword = rotating(10203);
    assertEquals(11404, sword.resolveGoodsItem(at("2026-10-01T04:00:00")).getId());
    assertEquals(11408, sword.resolveGoodsItem(at("2026-11-01T04:00:00")).getId());
    sword.setRotateId(-1);
    assertNull(sword.resolveGoodsItem(at("2026-10-01T04:00:00")));
  }

  @Test
  void nativeGoodsKeepPurchaseLimitsAndDoNotRepeatScalarCurrencyCosts() {
    var manager = new ShopSystem(null);
    var good = new ShopInfo();
    good.setGoodsId(102003);
    good.setGoodsItem(new ItemParamData(223, 1));
    good.setCostItemList(List.of(new ItemParamData(221, 5)));
    good.setScoin(100);
    good.setHcoin(160);
    good.setMcoin(75);
    good.setMinLevel(4);
    good.setBuyLimit(5);
    var expired = ShopCatalog.copy(good);
    expired.setGoodsId(102027);
    expired.setEndTime(1);
    var invalid = ShopCatalog.copy(good);
    invalid.setGoodsId(102028);
    invalid.setGoodsItem(new ItemParamData(0, 1));
    var source = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<List<ShopInfo>>();
    source.put(1001, List.of(good, expired, invalid));
    manager.getCatalog().replaceBase(source);
    var shop = PacketGetShopRsp.buildShop(new Player(), manager, 1001);
    assertEquals(1, shop.getGoodsListCount());
    var actual = shop.getGoodsList(0);
    assertEquals(5, actual.getSingleLimit());
    assertEquals(4, actual.getMinLevel());
    assertEquals(100, actual.getScoin());
    assertEquals(160, actual.getHcoin());
    assertEquals(75, actual.getMcoin());
    assertEquals(1, actual.getCostItemListCount());
    assertEquals(221, actual.getCostItemList(0).getItemId());
  }

  @Test
  void resourcePaimonRowsReplaceTheForksUnrelatedPresetItems() throws Exception {
    var groups = GameData.getShopGoodsDataEntries();
    var previous = groups.get(1001);
    try {
      var rows =
          JsonUtils.loadToList(
                  FileUtils.getExcelPath("ShopGoodsExcelConfigData.json"), ShopGoodsData.class)
              .stream()
              .filter(r -> r.getShopType() == 1001)
              .toList();
      rows.forEach(ShopGoodsData::onLoad);
      groups.put(1001, rows);
      var manager = new ShopSystem(null);
      var active = PacketGetShopRsp.buildShop(new Player(), manager, 1001);
      var first =
          active.getGoodsListList().stream()
              .filter(r -> r.getGoodsId() == 102005)
              .findFirst()
              .orElseThrow();
      assertEquals(
          rotating(10201).resolveGoodsItem(Utils.getCurrentSeconds()).getId(),
          first.getGoodsItem().getItemId());
      assertFalse(active.getGoodsListList().stream().anyMatch(r -> r.getGoodsId() == 102027));
      assertTrue(
          active.getGoodsListList().stream().allMatch(r -> r.getGoodsItem().getItemId() > 0));
      assertEquals(
          rows.size(),
          manager.getCatalog().entries().stream().filter(e -> e.shopType() == 1001).count());
    } finally {
      if (previous == null) groups.remove(1001);
      else groups.put(1001, previous);
    }
  }
}
