package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.game.activity.ActivityConfigItem;
import emu.grasscutter.game.activity.salesman.SalesmanNpcScene;
import emu.grasscutter.game.activity.salesman.SalesmanNpcScene.Visit;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.proto.GroupSuiteNotifyOuterClass.GroupSuiteNotify;
import emu.grasscutter.net.proto.GroupUnloadNotifyOuterClass.GroupUnloadNotify;
import emu.grasscutter.net.proto.NpcTalkRspOuterClass.NpcTalkRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.packet.send.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class SalesmanNpcSceneTest {
    private static final long OPEN = Instant.parse("2026-09-30T20:00:00Z").toEpochMilli();
    private static ActivityConfigItem config(int schedule) {
        var item = new ActivityConfigItem(); item.setActivityId(5003); item.setScheduleId(schedule);
        item.setBeginTime(new Date(OPEN)); item.setEndTime(new Date(OPEN + 9 * 86400000L)); item.onLoad(); return item;
    }
    private static SceneNpcBornData born() throws Exception {
        return new Gson().fromJson(Files.readString(Path.of("resources/BinOutput/Scene/SceneNpcBorn/scene3_npcborn.json")), SceneNpcBornData.class);
    }
    @Test void originalBirthTableContainsAllSevenRealSuitesAndNpcRotations() throws Exception {
        var slots = SalesmanNpcScene.locations(born());
        assertEquals(Set.of(1,2,3,4,5,6,7), slots.keySet());
        assertEquals(2255.541f, slots.get(1).getPos().getX(), 0.01);
        assertEquals(-495.3023f, slots.get(7).getPos().getX(), 0.01);
        assertEquals(345.8279f, slots.get(1).getRot().getY(), 0.01);
        assertEquals(135.2079f, slots.get(7).getRot().getY(), 0.01);
        slots.forEach((day, entry) -> {
            assertEquals(305003001, entry.getGroupId()); assertEquals(30001, entry.getConfigId());
            assertEquals(List.of(day), entry.getSuiteIdList());
        });
    }
    @Test void missingDuplicateOrInvalidSuitesAreRejectedBeforeSendingAnything() throws Exception {
        var source = born();
        var seven = new ArrayList<>(source.getBornPosList().stream().filter(x -> x.getGroupId() == 305003001).toList());
        source.setBornPosList(seven);
        seven.remove(6); assertThrows(IllegalArgumentException.class, () -> SalesmanNpcScene.locations(source));
        seven.add(seven.get(0)); assertThrows(IllegalArgumentException.class, () -> SalesmanNpcScene.locations(source));
        var invalid = born();
        invalid.getBornPosList().stream().filter(x -> x.getGroupId() == 305003001).findFirst().orElseThrow().getPos().setX(Float.NaN);
        assertThrows(IllegalArgumentException.class, () -> SalesmanNpcScene.locations(invalid));
    }
    @Test void originalDaySwitchUsesChinaFourAmAndExtendedScheduleKeepsLastNpc() {
        var item = config(5003010);
        assertEquals(new Visit(5003010,1), SalesmanNpcScene.visit(item,12,OPEN));
        assertEquals(new Visit(5003010,1), SalesmanNpcScene.visit(item,12,OPEN+86400000L-1));
        assertEquals(new Visit(5003010,2), SalesmanNpcScene.visit(item,12,OPEN+86400000L));
        assertEquals(new Visit(5003010,7), SalesmanNpcScene.visit(item,12,OPEN+8*86400000L));
        assertNull(SalesmanNpcScene.visit(item,11,OPEN));
        assertNull(SalesmanNpcScene.visit(item,12,OPEN-1));
        assertNull(SalesmanNpcScene.visit(item,12,item.getEndTime().getTime()));
        item.setDisabled(true); assertNull(SalesmanNpcScene.visit(item,12,OPEN));
    }
    @Test void perClientDayScheduleDistanceAndSceneExitTransitionsDoNotLeaveOldNpc() {
        var sent = new ArrayList<String>();
        var visibility = new SalesmanNpcScene.Visibility(new SalesmanNpcScene.Visibility.Client() {
            public void show(Visit view) { sent.add("show:"+view.scheduleId()+":"+view.day()); }
            public void hide() { sent.add("hide"); }
        });
        var first = new Visit(5003010,1);
        visibility.update(first); visibility.update(first);
        visibility.update(new Visit(5003010,2));
        visibility.update(new Visit(5003011,2));
        visibility.update(null); visibility.update(null);
        assertEquals(List.of("show:5003010:1","hide","show:5003010:2","hide","show:5003011:2","hide"), sent);
        assertNull(visibility.shown());
        assertTrue(SalesmanNpcScene.nearby(new Position(3,4,0),new Position(),5));
        assertFalse(SalesmanNpcScene.nearby(new Position(3,4,0),new Position(),4.99));
        assertFalse(SalesmanNpcScene.nearby(new Position(Float.NaN,0,0),new Position(),10));
    }
    @Test void failedHideRetriesBeforeAReplacementCanBeShown() {
        var sent = new ArrayList<String>(); var fail = new boolean[]{false};
        var visibility = new SalesmanNpcScene.Visibility(new SalesmanNpcScene.Visibility.Client() {
            public void show(Visit view) { sent.add("show:"+view.day()); }
            public void hide() { sent.add("hide"); if(fail[0]) throw new IllegalStateException("offline"); }
        });
        visibility.update(new Visit(5003010,1)); fail[0] = true;
        assertThrows(IllegalStateException.class, () -> visibility.update(new Visit(5003010,2)));
        fail[0] = false; visibility.update(new Visit(5003010,2));
        assertEquals(List.of("show:1","hide","hide","show:2"), sent);
    }
    @Test void packetsCarryOriginalGroupSuiteAndRejectedTalkDoesNotReturnSuccess() throws Exception {
        for (int day=1;day<=7;day++) {
            var packet=new PacketGroupSuiteNotify(SalesmanNpcScene.GROUP_ID,day);
            assertEquals(Map.of(305003001,day),GroupSuiteNotify.parseFrom(packet.getData()).getGroupMapMap());
        }
        var unload=new PacketGroupUnloadNotify(List.of(SalesmanNpcScene.GROUP_ID));
        assertEquals(List.of(305003001),GroupUnloadNotify.parseFrom(unload.getData()).getGroupListList());
        var denied=new PacketNpcTalkRsp(936,4100101,77,Retcode.RET_NOT_CURRENT_TALK_VALUE);
        var response=NpcTalkRsp.parseFrom(denied.getData());
        assertEquals(303,response.getRetcode()); assertEquals(4100101,response.getCurTalkId());
        assertEquals(936,response.getNpcEntityId()); assertEquals(77,response.getEntityId());
    }
}
