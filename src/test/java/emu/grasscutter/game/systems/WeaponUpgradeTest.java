package emu.grasscutter.game.systems;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.weapon.*;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import emu.grasscutter.net.proto.WeaponUpgradeRspOuterClass.WeaponUpgradeRsp;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;

class WeaponUpgradeTest {
  private final Map<Integer, ItemData> itemsBefore = new HashMap<>();
  private final Map<Integer, WeaponPromoteData> promotesBefore = new HashMap<>();
  private final Map<Integer, WeaponLevelData> levelsBefore = new HashMap<>();
  private TestPlayer player;
  private InventorySystem system;
  private GameItem weapon;

  private static class TestPlayer extends Player {
    final List<BasePacket> packets = new ArrayList<>();

    @Override
    public void sendPacket(BasePacket packet) {
      if (packets != null) packets.add(packet);
    }

    @Override
    public void save() {}
  }

  private <T> T[] resources(String file, Class<T[]> type) throws Exception {
    return new Gson()
        .fromJson(Files.readString(Path.of("resources", "ExcelBinOutput", file)), type);
  }

  @BeforeEach
  void setup() throws Exception {
    Class.forName("emu.grasscutter.Grasscutter");
    for (var data : resources("WeaponExcelConfigData.json", ItemData[].class)) {
      if (data.getId() != 11101) continue;
      data.onLoad();
      itemsBefore.put(data.getId(), GameData.getItemDataMap().put(data.getId(), data));
    }
    for (var data : resources("MaterialExcelConfigData.json", ItemData[].class)) {
      if (data.getId() < 104011 || data.getId() > 104013) continue;
      data.onLoad();
      itemsBefore.put(data.getId(), GameData.getItemDataMap().put(data.getId(), data));
    }
    for (var data : resources("WeaponPromoteExcelConfigData.json", WeaponPromoteData[].class)) {
      if (data.getWeaponPromoteId() != 11101) continue;
      data.onLoad();
      promotesBefore.put(data.getId(), GameData.getWeaponPromoteDataMap().put(data.getId(), data));
    }
    for (var data : resources("WeaponLevelExcelConfigData.json", WeaponLevelData[].class)) {
      if (data.getLevel() > 20) continue;
      levelsBefore.put(data.getId(), GameData.getWeaponLevelDataMap().put(data.getId(), data));
    }
    player = new TestPlayer();
    player.setMora(10000);
    weapon = stock(11101, 1);
    stock(104013, 5);
    system = new InventorySystem(null);
    player.packets.clear();
  }

  @AfterEach
  void restore() {
    itemsBefore.forEach(
        (id, data) -> {
          if (data == null) GameData.getItemDataMap().remove(id);
          else GameData.getItemDataMap().put(id, data);
        });
    promotesBefore.forEach(
        (id, data) -> {
          if (data == null) GameData.getWeaponPromoteDataMap().remove(id);
          else GameData.getWeaponPromoteDataMap().put(id, data);
        });
    levelsBefore.forEach(
        (id, data) -> {
          if (data == null) GameData.getWeaponLevelDataMap().remove(id);
          else GameData.getWeaponLevelDataMap().put(id, data);
        });
  }

  private GameItem stock(int id, int count) {
    var item = new GameItem(id, count);
    // UID 0 keeps save() away from the database while exercising the real inventory.
    item.setOwner(player);
    player.getInventory().getItems().put(item.getGuid(), item);
    player.getInventory().getInventoryTab(item.getItemType()).onAddItem(item);
    return item;
  }

  private List<ItemParam> ores(int count) {
    return List.of(ItemParam.newBuilder().setItemId(104013).setCount(count).build());
  }

  private WeaponUpgradeRsp response() throws Exception {
    return WeaponUpgradeRsp.parseFrom(
        player.packets.stream()
            .filter(p -> p.getOpcode() == PacketOpcodes.WeaponUpgradeRsp)
            .findFirst()
            .orElseThrow()
            .getData());
  }

  @Test
  void oreUpgradeConsumesOreAndMoraAndReturnsTheNewLevel() throws Exception {
    system.upgradeWeapon(player, weapon.getGuid(), List.of(), ores(1));
    assertEquals(13, weapon.getLevel());
    assertEquals(1525, weapon.getExp());
    assertEquals(10000, weapon.getTotalExp());
    assertEquals(4, player.getInventory().getItemById(104013).getCount());
    assertEquals(9000, player.getMora());
    var rsp = response();
    assertEquals(0, rsp.getRetcode());
    assertEquals(1, rsp.getOldLevel());
    assertEquals(weapon.getLevel(), rsp.getCurLevel());
    assertEquals(weapon.getGuid(), rsp.getTargetWeaponGuid());
  }

  @Test
  void feedingWeaponConsumesItAndReusesItsExperience() throws Exception {
    var food = stock(11101, 1);
    food.setTotalExp(1000);
    system.upgradeWeapon(player, weapon.getGuid(), List.of(food.getGuid()), List.of());
    assertEquals(1400, weapon.getTotalExp());
    assertEquals(5, weapon.getLevel());
    assertEquals(450, weapon.getExp());
    assertNull(player.getInventory().getItemByGuid(food.getGuid()));
    assertEquals(9940, player.getMora());
    assertEquals(0, response().getRetcode());
  }

  @Test
  void previewMatchesActualRefundWithoutConsumingAnything() throws Exception {
    weapon.setLevel(19);
    var preview = system.calcWeaponUpgradeReturnItems(player, weapon.getGuid(), List.of(), ores(2));
    assertNotNull(preview);
    assertFalse(preview.isEmpty());
    assertEquals(19, weapon.getLevel());
    assertEquals(5, player.getInventory().getItemById(104013).getCount());
    assertEquals(10000, player.getMora());
    assertTrue(player.packets.isEmpty());
    system.upgradeWeapon(player, weapon.getGuid(), List.of(), ores(2));
    assertEquals(20, weapon.getLevel());
    assertEquals(preview, response().getItemParamListList());
    for (var ore : preview) {
      int originalAfterPayment = ore.getItemId() == 104013 ? 3 : 0;
      assertEquals(
          originalAfterPayment + ore.getCount(),
          player.getInventory().getItemById(ore.getItemId()).getCount());
    }
  }

  @Test
  void insufficientMaterialsOrMoraCannotConsumeItemsOrExperience() {
    system.upgradeWeapon(player, weapon.getGuid(), List.of(), ores(6));
    assertEquals(1, weapon.getLevel());
    assertEquals(5, player.getInventory().getItemById(104013).getCount());
    assertEquals(10000, player.getMora());
    player.setMora(999);
    system.upgradeWeapon(player, weapon.getGuid(), List.of(), ores(1));
    assertEquals(1, weapon.getLevel());
    assertEquals(0, weapon.getTotalExp());
    assertEquals(5, player.getInventory().getItemById(104013).getCount());
    assertEquals(999, player.getMora());
  }
}
