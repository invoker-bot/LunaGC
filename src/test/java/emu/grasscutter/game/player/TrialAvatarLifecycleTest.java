package emu.grasscutter.game.player;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadPoolExecutor;
import java.lang.reflect.Proxy;
import com.google.gson.Gson;
import dev.morphia.Datastore;
import dev.morphia.query.Query;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.RewardData;
import emu.grasscutter.data.excels.avatar.AvatarData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.database.DatabaseManager;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.avatar.AvatarStorage;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.quest.GameMainQuest;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.AvatarDelNotifyOuterClass.AvatarDelNotify;
import emu.grasscutter.net.proto.AvatarTeamUpdateNotifyOuterClass.AvatarTeamUpdateNotify;
import emu.grasscutter.server.packet.send.PacketAvatarDelNotify;
import emu.grasscutter.server.packet.send.PacketAvatarTeamUpdateNotify;
import org.bson.types.ObjectId;
import dev.morphia.Morphia;
import dev.morphia.mapping.MapperOptions;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import com.mongodb.client.MongoClients;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TrialAvatarLifecycleTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
    }

    private static void drain() throws Exception {
        var executor = (ThreadPoolExecutor) DatabaseHelper.getEventExecutor();
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (executor.getActiveCount() != 0 || !executor.getQueue().isEmpty()) {
            if (System.nanoTime() > until) fail("Database jobs did not finish");
            Thread.sleep(5);
        }
    }

    private static GameMainQuest finishedQuest() throws Exception {
        var quest = new GameMainQuest();
        var field = GameMainQuest.class.getDeclaredField("isFinished");
        field.setAccessible(true);
        field.setBoolean(quest, true);
        return quest;
    }

    @Test
    void questTrialDoesNotModifyTheSavedParty() {
        var manager = new TeamManager(new Player());
        var original = List.of(10000007, 10000096, 10000150, 10000035);
        manager.getCurrentSinglePlayerTeamInfo().getAvatars().addAll(original);

        manager.setupTrialAvatars(true);
        manager.getCurrentTeamInfo().getAvatars().add(10000021);

        assertEquals(original, manager.getCurrentSinglePlayerTeamInfo().getAvatars());
        assertEquals(5, manager.getCurrentTeamInfo().size());
        assertNotSame(manager.getCurrentSinglePlayerTeamInfo(), manager.getCurrentTeamInfo());
    }

    @Test
    void addingAnotherTrialKeepsTheOriginalSelectedSlot() {
        var manager = new TeamManager(new Player());
        manager.getCurrentSinglePlayerTeamInfo().getAvatars().addAll(List.of(10000007, 10000096));
        manager.setCurrentCharacterIndex(1);
        manager.setupTrialAvatars(true);
        manager.setCurrentCharacterIndex(4);

        manager.setupTrialAvatars(true);

        assertEquals(1, manager.getPreviousIndex());
    }

    private static class FakeAvatar extends Avatar {
        private final int id;
        private final ObjectId objectId = new ObjectId();
        FakeAvatar(int id, int trial, int parent) {
            this.id = id;
            setTrialAvatarId(trial);
            setFromParentQuestId(parent);
            setGrantReason(trial == 0 ? 0 : 1);
        }
        @Override public int getAvatarId() { return id; }
        @Override public long getGuid() { return 100000000000L + id + getTrialAvatarId() * 1000000000L; }
        @Override public ObjectId getObjectId() { return objectId; }
        @Override public AvatarData getAvatarData() { return new AvatarData(); }
        @Override public void recalcStats(boolean forceSendAbilityChange) {}
        @Override public void save() {}
    }

    private static class RecordingPlayer extends Player {
        private final AvatarStorage storage = new AvatarStorage(this) {
            @Override protected Avatar createRecoveredAvatar(int id) { return new FakeAvatar(id, 0, 0); }
            @Override public void addStartingWeapon(Avatar avatar) { starterWeapons.add(avatar.getAvatarId()); }
        };
        private final TeamManager manager = new TeamManager(this);
        private final List<BasePacket> packets = new ArrayList<>();
        private final List<Integer> starterWeapons = new ArrayList<>();
        @Override public AvatarStorage getAvatars() { return storage == null ? super.getAvatars() : storage; }
        @Override public TeamManager getTeamManager() { return manager == null ? super.getTeamManager() : manager; }
        @Override public void sendPacket(BasePacket packet) { packets.add(packet); }
        @Override public void save() {}
        @Override public int getUid() { return 12345; }
        @Override public void addAvatar(Avatar avatar, boolean addToTeam) {
            fail("Legacy recovery must precede inventory loading, without adding a starter weapon");
        }
    }

    private static RecordingPlayer party(Integer... ids) {
        var player = new RecordingPlayer();
        for (int id : ids) {
            player.getAvatars().addAvatar(new FakeAvatar(id, 0, 0));
            player.getTeamManager().getCurrentSinglePlayerTeamInfo().getAvatars().add(id);
        }
        return player;
    }

    @Test
    void removingOneTrialKeepsAnotherAndTheOwnedCopy() throws Exception {
        var player = party(10000007, 10000021);
        var manager = player.getTeamManager();
        var ownedAmber = player.getAvatars().getAvatarById(10000021);
        var trialAmber = new FakeAvatar(10000021, 1, 306);
        var trialKaeya = new FakeAvatar(10000015, 2, 307);
        manager.setupTrialAvatars(true);
        manager.addAvatarToTrialTeam(trialAmber);
        manager.addAvatarToTrialTeam(trialKaeya);

        manager.removeTrialAvatar(1);

        assertTrue(manager.isUsingTrialTeam());
        assertEquals(List.of(10000007, 10000021, 10000015), manager.getCurrentTeamInfo().getAvatars());
        assertEquals(List.of(10000007, 10000021), manager.getCurrentSinglePlayerTeamInfo().getAvatars());
        assertSame(ownedAmber, player.getAvatars().getAvatarById(10000021));
        assertEquals(trialKaeya.getGuid(), manager.getTrialAvatarGuid(2));
        var deletion = player.packets.stream().filter(p -> p instanceof PacketAvatarDelNotify).findFirst().orElseThrow();
        assertEquals(List.of(trialAmber.getGuid()), AvatarDelNotify.parseFrom(deletion.getData()).getAvatarGuidListList());
    }

    @Test
    void removingAllTrialsRestoresThePartyAndSendsItsRealTeamData() throws Exception {
        var player = party(10000007, 10000096, 10000150, 10000035);
        var manager = player.getTeamManager();
        manager.setCurrentCharacterIndex(2);
        manager.setupTrialAvatars(false);
        manager.addAvatarToTrialTeam(new FakeAvatar(10000021, 1, 0));
        manager.addAvatarToTrialTeam(new FakeAvatar(10000015, 2, 0));
        manager.setCurrentCharacterIndex(0);

        manager.removeTrialAvatar();

        assertFalse(manager.isUsingTrialTeam());
        assertTrue(manager.getTrialAvatars().isEmpty());
        assertEquals(2, manager.getCurrentCharacterIndex());
        assertEquals(List.of(10000007, 10000096, 10000150, 10000035), manager.getCurrentTeamInfo().getAvatars());
        var update = player.packets.stream().filter(p -> p instanceof PacketAvatarTeamUpdateNotify).findFirst().orElseThrow();
        var proto = AvatarTeamUpdateNotify.parseFrom(update.getData());
        assertEquals(4, proto.getAvatarTeamMapOrThrow(1).getAvatarGuidListCount());
        assertEquals(0, proto.getTempAvatarGuidListCount());
    }

    @Test
    void borrowedAvatarCannotEnterOwnedStorage() {
        var player = party(10000007);
        assertFalse(player.getAvatars().addAvatar(new FakeAvatar(10000021, 1, 306)));
        assertFalse(player.getAvatars().hasAvatar(10000021));
    }

    @Test
    void oldSixSlotPartyIsRepairedWithoutDeletingOwnedAmber() {
        var player = party(10000007, 10000096, 10000150, 10000035);
        var amber = new FakeAvatar(10000021, 0, 0);
        player.getAvatars().addAvatar(amber);
        var manager = player.getTeamManager();
        manager.getCurrentSinglePlayerTeamInfo().getAvatars().addAll(List.of(10000021, 10000015));
        manager.setCurrentCharacterIndex(2);

        manager.repairPersistentTeams();

        assertEquals(List.of(10000007, 10000096, 10000150, 10000035), manager.getCurrentSinglePlayerTeamInfo().getAvatars());
        assertEquals(2, manager.getCurrentCharacterIndex());
        assertSame(amber, player.getAvatars().getAvatarById(10000021));
    }

    @Test
    void normalAvatarSavesButBorrowedAvatarDoesNot() throws Exception {
        var field = DatabaseManager.class.getDeclaredField("gameDatastore");
        field.setAccessible(true);
        var original = field.get(null);
        var saved = new ArrayList<Object>();
        field.set(null, Proxy.newProxyInstance(Datastore.class.getClassLoader(), new Class<?>[] {Datastore.class},
            (proxy, method, args) -> { if (method.getName().equals("save")) saved.add(args[0]); return args == null ? null : args[0]; }));
        try {
            var borrowed = new Avatar();
            borrowed.setTrialAvatarId(1);
            borrowed.save();
            var owned = new Avatar();
            owned.save();
            drain();
            assertEquals(List.of(owned), saved);
            assertThrows(IllegalArgumentException.class, () -> DatabaseHelper.deleteTrialAvatar(owned));
        } finally {
            drain();
            field.set(null, original);
        }
    }

    @Test
    void completedQuestRecoversKaeyaOncePreservingSavedEquipmentAndOwnedAmber() throws Exception {
        var player = party(10000007, 10000021);
        var ownedAmber = player.getAvatars().getAvatarById(10000021);
        var amber = new FakeAvatar(10000021, 1, 306);
        var kaeya = new FakeAvatar(10000015, 2, 307);
        var legacyField = AvatarStorage.class.getDeclaredField("legacyTrialAvatars");
        legacyField.setAccessible(true);
        @SuppressWarnings("unchecked") var legacy = (List<Avatar>) legacyField.get(player.getAvatars());
        legacy.addAll(List.of(amber, kaeya));
        var gson = new Gson();
        player.getQuestManager().getMainQuests().put(306, finishedQuest());
        player.getQuestManager().getMainQuests().put(307, finishedQuest());
        var priorMain = GameData.getMainQuestDataMap().put(307, gson.fromJson("{\"id\":307,\"rewardIdList\":[100307]}", MainQuestData.class));
        var priorReward = GameData.getRewardDataMap().put(100307, gson.fromJson("{\"rewardId\":100307,\"rewardItemList\":[{\"itemId\":1015,\"itemCount\":1}]}", RewardData.class));
        var priorItem = GameData.getItemDataMap().put(1015, gson.fromJson("{\"id\":1015,\"materialType\":\"MATERIAL_AVATAR\"}", ItemData.class));
        var weaponData = gson.fromJson("{\"id\":11101,\"itemType\":\"ITEM_WEAPON\",\"equipType\":\"EQUIP_WEAPON\"}", ItemData.class);
        var priorWeapon = GameData.getItemDataMap().put(11101, weaponData);
        var weapon = new GameItem(weaponData);
        var itemId = GameItem.class.getDeclaredField("id");
        itemId.setAccessible(true);
        itemId.set(weapon, new ObjectId());
        weapon.setEquipCharacter(10000015);
        var query = Proxy.newProxyInstance(Query.class.getClassLoader(), new Class<?>[] {Query.class},
            (proxy, method, args) -> method.getName().equals("stream") ? List.of(weapon).stream() : proxy);
        var datastoreField = DatabaseManager.class.getDeclaredField("gameDatastore");
        datastoreField.setAccessible(true);
        var original = datastoreField.get(null);
        var removed = new ArrayList<Object>();
        var saved = new ArrayList<Object>();
        datastoreField.set(null, Proxy.newProxyInstance(Datastore.class.getClassLoader(), new Class<?>[] {Datastore.class},
            (proxy, method, args) -> {
                if (method.getName().equals("find")) return query;
                if (method.getName().equals("delete")) { removed.add(args[0]); return null; }
                if (method.getName().equals("save")) saved.add(args[0]);
                return args == null ? null : args[0];
            }));
        try {
            player.getAvatars().recoverLegacyTrialAvatars();
            player.getAvatars().recoverLegacyTrialAvatars();

            assertEquals(3, player.getAvatars().getAvatarCount());
            assertEquals(0, player.getAvatars().getAvatarById(10000015).getTrialAvatarId());
            assertEquals(List.of(amber, kaeya), removed);
            assertEquals(1, saved.size());
            assertSame(ownedAmber, player.getAvatars().getAvatarById(10000021));
            assertEquals(List.of(10000007, 10000021), player.getTeamManager().getCurrentSinglePlayerTeamInfo().getAvatars());

            var loaded = BasePlayerManager.class.getDeclaredField("loaded");
            loaded.setAccessible(true);
            loaded.setBoolean(player.getAvatars(), true);
            player.getInventory().loadFromDatabase();
            drain();
            assertSame(weapon, player.getAvatars().getAvatarById(10000015).getWeapon());
            assertEquals(10000015, weapon.getEquipCharacter());
            assertEquals(1, player.getInventory().getItems().size());
            assertFalse(player.starterWeapons.contains(10000015));
        } finally {
            drain();
            datastoreField.set(null, original);
            if (priorMain == null) GameData.getMainQuestDataMap().remove(307); else GameData.getMainQuestDataMap().put(307, priorMain);
            if (priorReward == null) GameData.getRewardDataMap().remove(100307); else GameData.getRewardDataMap().put(100307, priorReward);
            if (priorItem == null) GameData.getItemDataMap().remove(1015); else GameData.getItemDataMap().put(1015, priorItem);
            if (priorWeapon == null) GameData.getItemDataMap().remove(11101); else GameData.getItemDataMap().put(11101, priorWeapon);
        }
    }

    @Test
    void finishingOneQuestDoesNotRemoveAnotherQuestsTrial() {
        var player = party(10000007);
        var manager = player.getTeamManager();
        manager.setupTrialAvatars(true);
        manager.addAvatarToTrialTeam(new FakeAvatar(10000021, 1, 306));
        manager.addAvatarToTrialTeam(new FakeAvatar(10000015, 2, 307));
        manager.getQuestTrialAvatarIds().put(1, 306);
        manager.getQuestTrialAvatarIds().put(2, 307);

        manager.removeQuestTrialAvatars(306);

        assertEquals(0, manager.getTrialAvatarGuid(1));
        assertNotEquals(0, manager.getTrialAvatarGuid(2));
        assertEquals(307, manager.getQuestTrialAvatarIds().get(2));
        assertFalse(manager.getQuestTrialAvatarIds().containsKey(1));
    }

    @Test
    void reconnectDropsFinishedQuestGrantsWithoutRegrantingThem() throws Exception {
        var player = party(10000007);
        player.getQuestManager().getMainQuests().put(306, finishedQuest());
        player.getTeamManager().getQuestTrialAvatarIds().put(1, 306);

        player.getTeamManager().restoreQuestTrialAvatars();

        assertTrue(player.getTeamManager().getQuestTrialAvatarIds().isEmpty());
        assertFalse(player.getTeamManager().isUsingTrialTeam());
        assertEquals(1, player.getAvatars().getAvatarCount());
    }

    @Test
    void fixedDungeonTeamStillTakesPriorityOverAQuestTrial() throws Exception {
        var player = party(10000007, 10000021);
        var manager = player.getTeamManager();
        manager.setupTrialAvatars(true);
        manager.addAvatarToTrialTeam(new FakeAvatar(10000015, 2, 307));
        var temporary = TeamManager.class.getDeclaredField("temporaryTeam");
        temporary.setAccessible(true);
        temporary.set(manager, List.of(new TeamInfo(new ArrayList<>(List.of(10000007)))));
        var index = TeamManager.class.getDeclaredField("useTemporarilyTeamIndex");
        index.setAccessible(true);
        index.setInt(manager, 0);

        assertEquals(List.of(10000007), manager.getCurrentTeamInfo().getAvatars());
        assertEquals(List.of(10000007, 10000021), manager.getCurrentSinglePlayerTeamInfo().getAvatars());
    }

    @Entity(value = "snapshots")
    public static class Snapshot {
        @Id private String id = "party";
        private TeamManager team;
        public Snapshot() {}
        Snapshot(TeamManager team) { this.team = team; }
    }

    @Test
    void mongoRoundTripStoresOnlyTheOriginalPartyAndGrantIds() throws Exception {
        var uri = System.getenv("LUNAGC_TEST_MONGO_URI");
        Assumptions.assumeTrue(uri != null && !uri.isBlank());
        String databaseName = "lunagc_trial_verify_" + UUID.randomUUID().toString().replace("-", "");
        try (var client = MongoClients.create(uri)) {
            try {
                var datastore = Morphia.createDatastore(client, databaseName,
                    MapperOptions.builder().storeEmpties(true).storeNulls(false).build());
                datastore.getMapper().map(Snapshot.class, TeamManager.class, TeamInfo.class);
                var manager = new TeamManager(new Player());
                var original = List.of(10000007, 10000096, 10000150, 10000035);
                manager.getCurrentSinglePlayerTeamInfo().getAvatars().addAll(original);
                manager.setupTrialAvatars(true);
                manager.getCurrentTeamInfo().getAvatars().add(10000021);
                manager.getQuestTrialAvatarIds().put(1, 306);
                datastore.save(new Snapshot(manager));

                var raw = client.getDatabase(databaseName).getCollection("snapshots").find().first().get("team", org.bson.Document.class);
                assertFalse(raw.containsKey("trialAvatars"));
                assertFalse(raw.containsKey("trialAvatarTeam"));
                assertEquals(306, raw.get("questTrialAvatarIds", org.bson.Document.class).getInteger("1"));
                var loaded = datastore.find(Snapshot.class).first().team;
                loaded.setPlayer(new Player());
                assertEquals(original, loaded.getCurrentSinglePlayerTeamInfo().getAvatars());
                assertFalse(loaded.isUsingTrialTeam());
                assertEquals(306, loaded.getQuestTrialAvatarIds().get(1));
            } finally {
                client.getDatabase(databaseName).drop();
            }
        }
    }
}
