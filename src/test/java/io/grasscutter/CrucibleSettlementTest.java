package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.protobuf.UnknownFieldSet;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.activity.MpPlayWatcherData;
import emu.grasscutter.game.activity.crucible.*;
import emu.grasscutter.net.proto.GadgetPlayUidInfoOuterClass.GadgetPlayUidInfo;
import emu.grasscutter.net.proto.GadgetPlayStopNotifyOuterClass.GadgetPlayStopNotify;
import emu.grasscutter.scripts.data.SceneGadgetCrucibleConfig;
import emu.grasscutter.server.packet.send.PacketGadgetPlayStopNotify;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class CrucibleSettlementTest {
    private static List<MpPlayWatcherData> rules() throws Exception {
        return Arrays.stream(new Gson().fromJson(Files.readString(
                Path.of("resources/ExcelBinOutput/MpPlayWatcherConfigData.json")), MpPlayWatcherData[].class))
                .filter(row -> row.getMpPlayId() == 1).toList();
    }

    private static GadgetPlayState round() {
        var config = new SceneGadgetCrucibleConfig();
        config.duration = 900; config.start_cd = 3; config.mp_play_id = 1;
        config.progress_stage = List.of(0, 5000, 20000, 35000);
        var state = new GadgetPlayState();
        state.start(config, 1000, new GadgetPlayState.Round(5001005, 42, 8,
                Map.of(10001, 8, 10002, 8, 10003, 8)));
        return state;
    }

    private static void submit(GadgetPlayState state, int uid, String element, int balls, int score) {
        assertTrue(state.setRoundUidValue(uid, element + "_ball", balls, 1003));
        assertTrue(state.setRoundUidValue(uid, element, score, 1003));
    }

    private static GadgetPlayState.Change finish(GadgetPlayState state) {
        return state.addProgress(35000, 1004).stream()
                .filter(change -> change.type() == GadgetPlayState.ChangeType.SUCCEEDED).findFirst().orElseThrow();
    }

    @Test void loadsOriginalTitlesWithoutConfusingThemWithPersistentActivityMissions() throws Exception {
        var rules = rules();
        assertEquals(19, rules.size());
        assertEquals(18, rules.stream().filter(row -> !row.isDisuse()).count());
        var transport = rules.stream().filter(row -> row.getId() == 2010102).findFirst().orElseThrow();
        assertEquals(99, transport.getPriority());
        assertEquals(1982233114L, transport.getChallengeTitleTextMapHash());
        assertEquals("TRIGGER_CRUCIBLE_MAX_BALL", transport.getTriggerConfig().getTriggerType());
        assertSame(GameData.getMpPlayWatcherDataMap(), GameData.getMapByResourceDef(MpPlayWatcherData.class));
    }

    @Test void resourcePriorityChoosesTransportLeaderAndThirtyThousandScoreTitle() throws Exception {
        var state = round();
        submit(state, 10001, "Fire", 45, 16000);
        submit(state, 10002, "Water", 5, 31000);
        var change = finish(state);
        assertEquals(2010102, CrucibleSettlement.watcherFor(10001, change, rules()));
        assertEquals(2010112, CrucibleSettlement.watcherFor(10002, change, rules()), "Priority100 outranks the leader's priority99");
        assertEquals(0, CrucibleSettlement.watcherFor(10003, change, rules()), "Zero contribution must not satisfy a leader watcher");
        assertEquals(0, CrucibleSettlement.watcherFor(99999, change, rules()));
    }

    @Test void perElementTitlesUseTheLuaElementEnumAndExactTenClotBoundary() throws Exception {
        var elements = Map.of("Fire", 2010113, "Water", 2010114, "Grass", 2010115,
                "Electric", 2010116, "Ice", 2010117, "Wind", 2010118, "Rock", 2010119);
        for (var element : elements.entrySet()) {
            var state = round();
            submit(state, 10001, element.getKey(), 10, 3000);
            submit(state, 10002, "Fire", 100, 32000);
            assertEquals(element.getValue(), CrucibleSettlement.watcherFor(10001, finish(state), rules()), element.getKey());
            state = round();
            submit(state, 10001, element.getKey(), 9, 2700);
            submit(state, 10002, "Fire", 100, 32300);
            assertEquals(0, CrucibleSettlement.watcherFor(10001, finish(state), rules()), "Nine clots do not complete the ten-clot title");
        }
    }

    @Test void killsBelongToOneMemberAndOnlyConfiguredGroupsCount() throws Exception {
        var state = round();
        submit(state, 10001, "Fire", 10, 3000);
        submit(state, 10002, "Water", 100, 32000);
        for (int i = 0; i < 14; i++) assertTrue(state.recordMonsterKill(200 + i, 10001, 305001001, 1003));
        assertFalse(state.recordMonsterKill(200, 10002, 305001001, 1003));
        assertFalse(state.recordMonsterKill(999, 99999, 305001001, 1003));
        assertTrue(state.recordMonsterKill(300, 10001, 99999, 1003));
        assertEquals(2010113, CrucibleSettlement.watcherFor(10001, finish(state), rules()), "Fourteen real group kills do not meet the fifteen-kill priority40 title");
        state = round();
        submit(state, 10001, "Fire", 10, 3000);
        submit(state, 10002, "Water", 100, 32000);
        for (int i = 0; i < 15; i++) state.recordMonsterKill(200 + i, 10001, 133003554, 1003);
        assertEquals(2010106, CrucibleSettlement.watcherFor(10001, finish(state), rules()));
    }

    @Test void settlementCarriesBattleWatcherAtNativeTagNineAndKeepsOldRoundStats() throws Exception {
        var state = round();
        submit(state, 10001, "Fire", 10, 3000);
        submit(state, 10002, "Water", 100, 32000);
        state.recordMonsterKill(200, 10001, 305001001, 1003);
        var change = finish(state);
        var profiles = Map.of(10001, GadgetPlayUidInfo.newBuilder().setUid(10001).setNickname("Traveller").build(),
                99999, GadgetPlayUidInfo.newBuilder().setUid(99999).build());
        assertThrows(UnsupportedOperationException.class, () -> change.participantStats().get(10001).balls().put("Fire", 100));
        assertThrows(UnsupportedOperationException.class, () -> change.participantStats().get(10001).groupKills().put(305001001, 100));
        assertFalse(state.setRoundUidValue(10001, "Fire_ball", 1000, 1005));
        var config = new SceneGadgetCrucibleConfig();
        config.duration = 900; config.mp_play_id = 1; config.progress_stage = List.of(0, 35000);
        state.start(config, 2000, change.round());
        var reset = state.stop(2001).get(0).participantStats().get(10001);
        assertEquals(0, reset.balls().get("Fire"));
        assertEquals(0, reset.score());
        assertTrue(reset.groupKills().isEmpty(), "Kills must not carry into a new round with the same members");
        assertEquals(10, change.participantStats().get(10001).balls().get("Fire"));
        var members = CrucibleSettlement.members(change, profiles, rules());
        assertEquals(List.of(10001, 10002, 10003), members.stream().map(GadgetPlayUidInfo::getUid).toList());
        var member = members.get(0);
        assertEquals("Traveller", member.getNickname());
        assertEquals(3000, member.getScore());
        assertEquals(List.of(2010113L), UnknownFieldSet.parseFrom(member.toByteArray()).getField(9).getVarintList());
        var descriptor = member.getDescriptorForType().findFieldByName("battle_watcher_id");
        assertNotNull(descriptor, "The old op name hides the settlement watcher identity");
        assertEquals(9, descriptor.getNumber());
        var packet = new PacketGadgetPlayStopNotify(1, change, members);
        assertEquals(members, GadgetPlayStopNotify.parseFrom(packet.getData()).getUidInfoListList());
    }

    @Test void timeoutUsesOnlyThisRoundAndResourceTiesStayDeterministic() throws Exception {
        var state = round();
        submit(state, 10001, "Fire", 20, 12000);
        submit(state, 10002, "Water", 20, 12000);
        state.addProgress(24000, 1003);
        var timeout = state.tick(1903).get(0);
        assertEquals(GadgetPlayState.ChangeType.TIMED_OUT, timeout.type());
        var shuffled = new ArrayList<>(rules());
        Collections.reverse(shuffled);
        assertEquals(2010102, CrucibleSettlement.watcherFor(10001, timeout, shuffled));
        assertEquals(2010102, CrucibleSettlement.watcherFor(10002, timeout, shuffled));
        assertEquals(0, CrucibleSettlement.watcherFor(10003, timeout, shuffled));
    }
}
