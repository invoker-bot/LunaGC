package emu.grasscutter.server.http.handlers;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.gacha.GachaBanner;
import emu.grasscutter.utils.lang.Language;
import it.unimi.dsi.fastutil.ints.*;
import java.util.*;
import org.junit.jupiter.api.*;

class GmBannerContentsTest {
  @BeforeAll
  static void initializeConfiguration() throws Exception {
    Class.forName("emu.grasscutter.Grasscutter");
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> row(String json, Int2ObjectMap<Language.TextStrings> strings)
      throws Exception {
    var banner = new Gson().fromJson(json, GachaBanner.class);
    var method =
        GmHandler.class.getDeclaredMethod(
            "bannerRowOf", Int2ObjectMap.class, GachaBanner.class, Int2ObjectMap.class, long.class);
    method.setAccessible(true);
    var before = new Gson().toJson(banner);
    var result =
        (Map<String, Object>)
            method.invoke(null, strings, banner, new Int2ObjectOpenHashMap<GachaBanner>(), 0L);
    assertEquals(
        before, new Gson().toJson(banner), "Reading candidates must not modify the banner");
    return result;
  }

  private Map<String, Object> row(String json) throws Exception {
    return row(json, new Int2ObjectOpenHashMap<>());
  }

  private List<Integer> ids(Map<String, Object> row, int rarity) {
    var ids = (int[]) row.get("poolItems" + rarity);
    assertNotNull(ids, "GM must expose candidates even when the banner has no UP items");
    return Arrays.stream(ids).boxed().toList();
  }

  @Test
  void standardWithoutUpListsItsDefaultCharactersAndWeaponsAtEveryRarity() throws Exception {
    var standard = row("{\"scheduleId\":893,\"bannerType\":\"STANDARD\"}");
    assertTrue(ids(standard, 5).containsAll(List.of(1003, 1016, 1042, 11501, 15502)));
    assertEquals(16, ids(standard, 5).size());
    assertTrue(ids(standard, 4).containsAll(List.of(1014, 11401)));
    assertTrue(ids(standard, 3).contains(11301));
    assertEquals(0, ((int[]) standard.get("rateUpItems5")).length);
  }

  @Test
  void characterBannerIncludesFeaturedAndFallbackCharactersWithoutFiveStarWeapons()
      throws Exception {
    var character = row("{\"bannerType\":\"CHARACTER\",\"rateUpItems5\":[1058]}");
    assertTrue(ids(character, 5).containsAll(List.of(1058, 1003)));
    assertFalse(ids(character, 5).contains(11501));
  }

  @Test
  void customPoolsOverrideDefaultsAndDoNotRepeatUpItemsOrZeroIds() throws Exception {
    var custom =
        row(
            """
            {"bannerType":"STANDARD","rateUpItems5":[1003],
             "fallbackItems5Pool1":[1003,1003],"fallbackItems5Pool2":[11501,11501,0],
             "fallbackItems4Pool1":[1014],"fallbackItems4Pool2":[],"fallbackItems3":[11302,11302,0]}
            """);
    assertEquals(List.of(1003, 11501), ids(custom, 5));
    assertEquals(List.of(1014), ids(custom, 4));
    assertEquals(List.of(11302), ids(custom, 3));
  }

  @Test
  void emptiedFallbackUsesTheSameDefaultWeaponsAsThePullSystem() throws Exception {
    var custom =
        row(
            """
            {"bannerType":"STANDARD","rateUpItems5":[1003],
             "fallbackItems5Pool1":[1003],"fallbackItems5Pool2":[]}
            """);
    assertEquals(11, ids(custom, 5).size());
    assertTrue(ids(custom, 5).containsAll(List.of(1003, 11501, 15502)));
  }

  @Test
  void guaranteedFeaturedPoolDoesNotListUnreachableFallbackItems() throws Exception {
    var custom = row("{\"bannerType\":\"CHARACTER\",\"rateUpItems5\":[1058],\"eventChance5\":100}");
    assertEquals(List.of(1058), ids(custom, 5));
  }

  @Test
  @SuppressWarnings("unchecked")
  void candidatesResolveNamesAndKindsAndKeepUnknownIdsVisible() throws Exception {
    var gson = new Gson();
    var previousWeapon =
        GameData.getItemDataMap()
            .put(
                11501,
                gson.fromJson(
                    "{\"id\":11501,\"itemType\":\"ITEM_WEAPON\",\"nameTextMapHash\":101}",
                    ItemData.class));
    var previousAvatar =
        GameData.getItemDataMap()
            .put(
                1003,
                gson.fromJson(
                    "{\"id\":1003,\"itemType\":\"ITEM_MATERIAL\",\"materialType\":\"MATERIAL_AVATAR\",\"nameTextMapHash\":102}",
                    ItemData.class));
    try {
      var strings = new Int2ObjectOpenHashMap<Language.TextStrings>();
      strings.put(101, new Language.TextStrings("测试武器"));
      strings.put(102, new Language.TextStrings("测试角色"));
      var custom =
          row(
              """
                {"fallbackItems5Pool1":[1003],"fallbackItems5Pool2":[11501,999999]}
                """,
              strings);
      var names = (List<Map<String, Object>>) custom.get("poolItems5Names");
      assertNotNull(names);
      assertEquals("测试角色", names.get(0).get("name"));
      assertEquals("character", names.get(0).get("kind"));
      assertEquals("测试武器", names.get(1).get("name"));
      assertEquals("weapon", names.get(1).get("kind"));
      assertEquals(999999, names.get(2).get("id"));
      assertNull(names.get(2).get("name"));
      assertEquals("unknown", names.get(2).get("kind"));
    } finally {
      if (previousWeapon == null) GameData.getItemDataMap().remove(11501);
      else GameData.getItemDataMap().put(11501, previousWeapon);
      if (previousAvatar == null) GameData.getItemDataMap().remove(1003);
      else GameData.getItemDataMap().put(1003, previousAvatar);
    }
  }
}
