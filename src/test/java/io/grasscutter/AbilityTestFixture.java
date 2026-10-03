package io.grasscutter;

import com.google.gson.Gson;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerRunMode;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.config.ConfigGlobalCombat;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamManager;
import emu.grasscutter.game.managers.SatiationManager;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.SceneEntityInfoOuterClass.SceneEntityInfo;
import emu.grasscutter.server.game.GameServer;
import it.unimi.dsi.fastutil.ints.*;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** An isolated ability scene, with no network listener or database writes. */
final class AbilityTestFixture implements AutoCloseable {
    final TestPlayer player;
    final TestScene scene;
    final TestEntity entity;
    private final ServerRunMode previousMode;
    private final ConfigGlobalCombat previousCombat;
    private final boolean previousScripts;

    AbilityTestFixture() throws Exception {
        previousMode = Grasscutter.getRunMode();
        previousCombat = GameData.getConfigGlobalCombat();
        previousScripts = Grasscutter.getConfig().server.game.enableScriptInBigWorld;
        Grasscutter.setRunModeOverride(ServerRunMode.DISPATCH_ONLY);
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = false;
        GameData.setConfigGlobalCombat(new Gson().fromJson(
                "{\"defaultAbilities\":{\"defaultMPLevelAbilities\":[],\"levelElementAbilities\":[]}}",
                ConfigGlobalCombat.class));
        var server = new GameServer();
        var worlds = GameServer.class.getDeclaredField("worlds");
        worlds.setAccessible(true);
        worlds.set(server, new HashSet<World>());
        player = new TestPlayer();
        scene = new TestScene(new TestWorld(server, player), new Gson().fromJson(
                "{\"id\":3,\"type\":\"SCENE_WORLD\"}", SceneData.class));
        player.setScene(scene);
        entity = new TestEntity(scene);
        entity.setId(42);
        scene.getEntities().put(42, entity);
    }

    @Override public void close() {
        Grasscutter.setRunModeOverride(previousMode);
        GameData.setConfigGlobalCombat(previousCombat);
        Grasscutter.getConfig().server.game.enableScriptInBigWorld = previousScripts;
    }

    static class TestPlayer extends Player {
        private final TeamManager team = new TeamManager(this);
        final List<BasePacket> packets = new CopyOnWriteArrayList<>();
        private final SatiationManager satiation = new SatiationManager(this) {
            @Override public void updateSingleAvatar(emu.grasscutter.game.avatar.Avatar avatar, float time) {}
        };
        @Override public TeamManager getTeamManager() { return team; }
        @Override public SatiationManager getSatiationManager() { return satiation; }
        @Override public void sendPacket(BasePacket packet) { packets.add(packet); }
    }

    static class TestScene extends Scene {
        final List<BasePacket> packets;
        TestScene(TestWorld world, SceneData data) {
            super(world, data);
            packets = world.packets;
        }
        @Override public void broadcastPacket(BasePacket packet) { packets.add(packet); }
    }

    static class TestWorld extends World {
        final List<BasePacket> packets = new CopyOnWriteArrayList<>();
        TestWorld(GameServer server, Player player) { super(server, player); }
        @Override public void broadcastPacket(BasePacket packet) { packets.add(packet); }
    }

    static class TestEntity extends GameEntity {
        private final Int2FloatMap properties = new Int2FloatOpenHashMap();
        TestEntity(Scene scene) { super(scene); }
        @Override public void initAbilities() {}
        @Override public int getEntityTypeId() { return 10000073; }
        @Override public Int2FloatMap getFightProperties() { return properties; }
        @Override public Position getPosition() { return new Position(); }
        @Override public Position getRotation() { return new Position(); }
        @Override public SceneEntityInfo toProto() { return SceneEntityInfo.getDefaultInstance(); }
    }
}
