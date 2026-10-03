package emu.grasscutter.game.beyond;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.mongodb.client.MongoClients;
import dev.morphia.Morphia;
import dev.morphia.annotations.Entity;
import dev.morphia.annotations.Id;
import dev.morphia.mapping.MapperOptions;
import emu.grasscutter.data.excels.BeyondHandbookData;
import emu.grasscutter.data.excels.BeyondHandbookWatcherData;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BeyondPersistenceTest {
    @BeforeAll
    static void initializeLoggerBeforeMongo() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
    }

    @Entity(value = "beyond_snapshot", useDiscriminator = false)
    public static class Snapshot {
        @Id private String id = "player";
        private BeyondCloset beyondCloset;
        private BeyondProgress beyondProgress;

        public Snapshot() {}
    }

    @Test
    void playerEmbeddedStateSurvivesTheProductionMongoCodec() {
        var gson = new Gson();
        var state = new Snapshot();
        state.beyondCloset = new BeyondCloset();
        state.beyondCloset.grant(List.of(500101, 500102));
        state.beyondProgress =
                gson.fromJson(
                        "{\"watcherProgress\":{\"120000\":3},\"claimedGroups\":[1]}", BeyondProgress.class);
        // Only BSON conversion is used. No database command or write is executed.
        try (var client = MongoClients.create("mongodb://127.0.0.1:1/?serverSelectionTimeoutMS=100")) {
            var datastore =
                    Morphia.createDatastore(
                            client,
                            "beyond_codec_test",
                            MapperOptions.builder().storeEmpties(true).storeNulls(false).build());
            var mapper = datastore.getMapper();
            mapper.map(Snapshot.class, BeyondCloset.class, BeyondProgress.class);
            var raw = mapper.toDocument(state);
            assertTrue(raw.containsKey("beyondCloset"));
            assertTrue(raw.containsKey("beyondProgress"));
            var restored = mapper.fromDocument(Snapshot.class, raw);
            assertEquals(state.beyondCloset.toProto(), restored.beyondCloset.toProto());
            assertFalse(restored.beyondCloset.canGrant(List.of(500101)));
            var group =
                    gson.fromJson(
                            "{\"groupId\":1,\"watcherIdList\":[120000],\"logic\":\"LOGIC_AND\"}",
                            BeyondHandbookData.class);
            var watcher =
                    gson.fromJson("{\"id\":120000,\"progress\":3}", BeyondHandbookWatcherData.class);
            var info =
                    restored.beyondProgress.toProto(List.of(group), Map.of(120000, watcher)).getInfoList(0);
            assertEquals(2, info.getRewardState());
            assertEquals(3, info.getWatcherProgressList(0).getProgress());
        }
    }
}
