package emu.grasscutter.game.entity;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.config.ConfigGlobalCombat;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.server.game.GameServer;
import emu.grasscutter.server.packet.send.PacketLifeStateChangeNotify;
import it.unimi.dsi.fastutil.ints.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;

class EntityDeathTest {
  private TestScene scene;

  static class TestScene extends Scene {
    Runnable onDeathNotify;

    TestScene(World world, SceneData data) {
      super(world, data);
    }

    @Override
    public void broadcastPacket(BasePacket packet) {
      if (packet instanceof PacketLifeStateChangeNotify && onDeathNotify != null)
        onDeathNotify.run();
      super.broadcastPacket(packet);
    }
  }

  static class TestEntity extends SubfieldDropTest.TestEntity {
    final Scene scene;
    final Int2FloatMap properties = new Int2FloatOpenHashMap();
    final AtomicInteger deaths = new AtomicInteger();
    Runnable callback;

    TestEntity(Scene scene) {
      this.scene = scene;
      setId(1);
    }

    @Override
    public Scene getScene() {
      return scene;
    }

    @Override
    public Int2FloatMap getFightProperties() {
      return properties;
    }

    @Override
    public void onDeath(int killerId) {
      deaths.incrementAndGet();
      if (callback != null) callback.run();
      super.onDeath(killerId);
    }

    void revive() {
      setDead(false);
    }
  }

  @BeforeEach
  void scene() throws Exception {
    var previousMode = Grasscutter.getRunMode();
    var previousCombat = GameData.getConfigGlobalCombat();
    boolean previousScripts = Grasscutter.getConfig().server.game.enableScriptInBigWorld;
    try {
      Grasscutter.setRunModeOverride(ServerRunMode.DISPATCH_ONLY);
      Grasscutter.getConfig().server.game.enableScriptInBigWorld = false;
      GameData.setConfigGlobalCombat(
          new Gson()
              .fromJson(
                  """
                {"defaultAbilities":{"defaultMPLevelAbilities":[],"levelElementAbilities":[]}}
                """,
                  ConfigGlobalCombat.class));
      var server = new GameServer();
      var worlds = GameServer.class.getDeclaredField("worlds");
      worlds.setAccessible(true);
      worlds.set(server, new java.util.HashSet<World>());
      var world = new World(server, null);
      scene =
          new TestScene(
              world, new Gson().fromJson("{\"id\":3,\"type\":\"SCENE_WORLD\"}", SceneData.class));
    } finally {
      Grasscutter.setRunModeOverride(previousMode);
      GameData.setConfigGlobalCombat(previousCombat);
      Grasscutter.getConfig().server.game.enableScriptInBigWorld = previousScripts;
    }
  }

  private TestEntity entity() {
    var entity = new TestEntity(scene);
    scene.getEntities().put(entity.getId(), entity);
    return entity;
  }

  @Test
  void repeatedAndReentrantDeathCallsDeliverOnlyOneDeathCallback() {
    var entity = entity();
    entity.callback =
        () -> {
          if (entity.deaths.get() == 1) scene.killEntity(entity);
        };
    scene.killEntity(entity);
    scene.killEntity(entity);
    assertEquals(1, entity.deaths.get());
    assertEquals(1, scene.getKilledMonsterCount());
    assertNull(scene.getEntities().get(entity.getId()));
  }

  @Test
  void lethalHpFlagStillAllowsTheFirstDeathAndRevivalAllowsANewDeath() {
    var entity = entity();
    entity.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0);
    entity.checkIfDead();
    assertTrue(entity.isDead());
    scene.killEntity(entity);
    assertEquals(1, entity.deaths.get());
    entity.revive();
    scene.getEntities().put(entity.getId(), entity);
    scene.killEntity(entity);
    assertEquals(2, entity.deaths.get());
  }

  @Test
  void concurrentDeathsCannotRepeatTheCallback() throws Exception {
    var entity = entity();
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var firstNotify = new AtomicBoolean();
    scene.onDeathNotify =
        () -> {
          if (!firstNotify.compareAndSet(false, true)) return;
          entered.countDown();
          try {
            assertTrue(release.await(3, TimeUnit.SECONDS));
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
        };
    var executor = Executors.newSingleThreadExecutor();
    try {
      var first = executor.submit(() -> scene.killEntity(entity));
      assertTrue(entered.await(3, TimeUnit.SECONDS));
      assertSame(
          entity,
          scene.getEntities().get(entity.getId()),
          "The first death has not removed the entity yet");
      scene.killEntity(entity);
      release.countDown();
      first.get(3, TimeUnit.SECONDS);
      assertEquals(1, entity.deaths.get());
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }
}
