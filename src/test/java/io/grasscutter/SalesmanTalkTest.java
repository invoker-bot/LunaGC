package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.activity.salesman.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SalesmanTalkTest {
    private static final long OPEN = Instant.parse("2026-09-30T20:00:00Z").toEpochMilli();
    private static ActivityConfigItem config() {
        var item=new ActivityConfigItem(); item.setActivityId(5003); item.setScheduleId(5003010);
        item.setBeginTime(new Date(OPEN)); item.setEndTime(new Date(OPEN+7*86400000L)); item.onLoad(); return item;
    }
    private static PlayerActivityData data() {
        return PlayerActivityData.of().activityId(5003).scheduleId(5003010).detail("{}").build();
    }
    @Test void currentDaysIntroductionPersistsAndRetransmissionDoesNotSaveAgain() {
        var item=config(); var data=data(); var saves=new AtomicInteger();
        assertEquals(0, SalesmanTalk.complete(data,item,4100101,12,OPEN,saves::incrementAndGet));
        assertTrue(SalesmanSchedule.progress(data).hasTalked(1));
        var restored=PlayerActivityData.of().activityId(5003).scheduleId(5003010).detail(data.getDetail()).build();
        assertEquals(0, SalesmanTalk.complete(restored,item,4100101,12,OPEN,saves::incrementAndGet));
        assertEquals(1,saves.get()); assertEquals(0,SalesmanSchedule.progress(restored).remainingChances());
        assertEquals(Retcode.RET_NOT_CURRENT_TALK_VALUE,SalesmanTalk.complete(restored,item,4100101,12,OPEN+86400000L,saves::incrementAndGet));
        assertEquals(0,SalesmanTalk.complete(restored,item,4100102,12,OPEN+86400000L,saves::incrementAndGet));
        assertTrue(SalesmanSchedule.progress(restored).hasTalked(2));
    }
    @Test void closedStaleLowRankUnknownAndCorruptRequestsNeverChangeProgress() {
        var item=config(); var data=data(); var saves=new AtomicInteger(); String before=data.getDetail();
        assertEquals(Retcode.RET_NOT_CURRENT_TALK_VALUE,SalesmanTalk.complete(data,item,4100107,12,OPEN,saves::incrementAndGet));
        assertEquals(Retcode.RET_NOT_CURRENT_TALK_VALUE,SalesmanTalk.complete(data,item,4100100,12,OPEN,saves::incrementAndGet));
        assertEquals(Retcode.RET_PLAYER_LEVEL_LESS_THAN_VALUE,SalesmanTalk.complete(data,item,4100101,11,OPEN,saves::incrementAndGet));
        item.setDisabled(true); assertEquals(Retcode.RET_ACTIVITY_CLOSE_VALUE,SalesmanTalk.complete(data,item,4100101,12,OPEN,saves::incrementAndGet));
        item.setDisabled(false); data.setScheduleId(5003011); assertEquals(Retcode.RET_ACTIVITY_CLOSE_VALUE,SalesmanTalk.complete(data,item,4100101,12,OPEN,saves::incrementAndGet));
        assertEquals(before,data.getDetail()); assertEquals(0,saves.get());
        data.setScheduleId(5003010); data.setDetailJson("{\"talkedDays\":[8]}"); String corrupt=data.getDetail();
        assertEquals(Retcode.RET_SVR_ERROR_VALUE,SalesmanTalk.complete(data,item,4100101,12,OPEN,saves::incrementAndGet));
        assertEquals(corrupt,data.getDetail());
    }
    @Test void failedPersistenceKeepsTheOriginalDetailAndCanRetry() {
        var item=config(); var data=data(); String before=data.getDetail();
        assertThrows(IllegalStateException.class,()->SalesmanTalk.complete(data,item,4100101,12,OPEN,()->{throw new IllegalStateException("database");}));
        assertEquals(before,data.getDetail());
        assertEquals(0,SalesmanTalk.complete(data,item,4100101,12,OPEN,()->{}));
    }
    @Test void rewardTalkRequiresAnEarnedUnusedChanceAndCannotCreateOne() {
        var item=config(); var data=data(); var progress=new SalesmanProgress(); progress.talk(1); data.setDetail(progress);
        assertEquals(Retcode.RET_NOT_CURRENT_TALK_VALUE,SalesmanTalk.complete(data,item,4100110,12,OPEN,()->fail("must not save")));
        assertEquals(0,SalesmanTalk.complete(data,item,4100114,12,OPEN,()->fail("already introduced")));
        progress.deliver(1); data.setDetail(progress);
        assertEquals(0,SalesmanTalk.complete(data,item,4100110,12,OPEN,()->fail("must not save")));
        assertEquals(Retcode.RET_NOT_CURRENT_TALK_VALUE,SalesmanTalk.complete(data,item,4100114,12,OPEN,()->fail("must not save")));
        assertEquals(1,SalesmanSchedule.progress(data).remainingChances());
    }
}
