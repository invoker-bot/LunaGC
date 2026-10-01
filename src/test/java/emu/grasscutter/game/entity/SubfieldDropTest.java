package emu.grasscutter.game.entity;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.server.DropSubfieldMapping;
import emu.grasscutter.data.server.DropTableExcelConfigData;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.proto.SceneEntityInfoOuterClass.SceneEntityInfo;
import it.unimi.dsi.fastutil.ints.Int2FloatMap;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;

class SubfieldDropTest {
  private static final int DROP_ID = -130;
  private static final int TABLE_ID = -21010000;
  private final Gson gson = new Gson();
  private final TestEntity entity = new TestEntity();

  static class TestEntity extends GameEntity {
    final Map<Integer, Integer> drops = new HashMap<>();

    TestEntity() {
      super(null);
    }

    @Override
    protected void spawnSubfieldItem(int id, int count) {
      drops.merge(id, count, Integer::sum);
    }

    @Override
    public void initAbilities() {}

    @Override
    public int getEntityTypeId() {
      return 70220013;
    }

    @Override
    public Int2FloatMap getFightProperties() {
      return null;
    }

    @Override
    public Position getPosition() {
      return new Position();
    }

    @Override
    public Position getRotation() {
      return new Position();
    }

    @Override
    public SceneEntityInfo toProto() {
      return null;
    }
  }

  static class FixedRoll extends Random {
    final int roll;

    FixedRoll(int roll) {
      this.roll = roll;
    }

    @Override
    public int nextInt(int bound) {
      assertTrue(roll < bound);
      return roll;
    }
  }

  @BeforeAll
  static void initialize() throws Exception {
    Class.forName("emu.grasscutter.Grasscutter");
  }

  @BeforeEach
  void mapping() {
    var mapping = new DropSubfieldMapping();
    mapping.setDropId(DROP_ID);
    mapping.setItemId(TABLE_ID);
    GameData.getDropSubfieldMappingMap().put(DROP_ID, mapping);
  }

  @AfterEach
  void cleanup() {
    GameData.getDropSubfieldMappingMap().remove(DROP_ID);
    GameData.getDropTableExcelConfigDataMap().remove(TABLE_ID);
  }

  private void table(String json) {
    GameData.getDropTableExcelConfigDataMap()
        .put(TABLE_ID, gson.fromJson(json, DropTableExcelConfigData.class));
  }

  @Test
  void independentRollUsesWeightOutOfTenThousandIncludingItsBoundary() {
    table("""
{"randomType":1,"dropVec":[{"itemId":100001,"countRange":"1","weight":300}]}""");
    for (int roll = 0; roll < 10000; roll++) entity.dropSubfieldItem(DROP_ID, new FixedRoll(roll));
    assertEquals(
        Map.of(100001, 300),
        entity.drops,
        "A 3% ingredient must drop on exactly 300 of 10000 rolls");
  }

  @Test
  void originalWoodenCrateProducesConfiguredIngredientRates() throws Exception {
    var tables =
        gson.fromJson(
            Files.readString(Path.of("resources/Server/DropTableExcelConfigData.json")),
            DropTableExcelConfigData[].class);
    var crate = Arrays.stream(tables).filter(t -> t.getId() == 21010000).findFirst().orElseThrow();
    GameData.getDropTableExcelConfigDataMap().put(TABLE_ID, crate);
    for (int roll = 0; roll < 10000; roll++) entity.dropSubfieldItem(DROP_ID, new FixedRoll(roll));
    for (var item : crate.getDropVec())
      assertEquals(item.getWeight(), entity.drops.get(item.getItemId()));
    assertEquals(4700, entity.drops.values().stream().mapToInt(Integer::intValue).sum());
  }

  @Test
  void weightedTableChoosesExactlyOneEntryAndAdvancesTheCumulativeWeight() {
    table(
        """
{"randomType":0,"dropVec":[{"itemId":100001,"countRange":"1","weight":1000},{"itemId":100002,"countRange":"1","weight":2000},{"itemId":100013,"countRange":"1","weight":1000}]}""");
    for (int roll = 0; roll < 4000; roll++) {
      entity.drops.clear();
      entity.dropSubfieldItem(DROP_ID, new FixedRoll(roll));
      int selected = roll < 1000 ? 100001 : roll < 3000 ? 100002 : 100013;
      assertEquals(
          Map.of(selected, 1), entity.drops, "Weighted roll " + roll + " must choose one entry");
    }
  }

  @Test
  void guaranteedAndDisabledEntriesKeepTheirActualMeaning() {
    table(
        """
{"randomType":1,"dropVec":[{"itemId":100001,"countRange":"1","weight":10000},{"itemId":100002,"countRange":"1","weight":0}]}""");
    entity.dropSubfieldItem(DROP_ID, new FixedRoll(9999));
    assertEquals(Map.of(100001, 1), entity.drops);
  }

  @Test
  void weightedEmptyAndNoDropEntriesDoNotSpawnItems() {
    table("""
            {"randomType":0,"dropVec":[]}
            """);
    assertTrue(entity.dropSubfieldItem(DROP_ID, new FixedRoll(0)));
    table(
        """
            {"randomType":0,"dropVec":[{"itemId":0,"countRange":"1","weight":1000},{"itemId":100001,"countRange":"1","weight":1000}]}
            """);
    entity.dropSubfieldItem(DROP_ID, new FixedRoll(999));
    assertTrue(entity.drops.isEmpty());
    entity.dropSubfieldItem(DROP_ID, new FixedRoll(1000));
    assertEquals(Map.of(100001, 1), entity.drops);
  }
}
