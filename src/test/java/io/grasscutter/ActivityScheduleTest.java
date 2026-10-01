package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.net.proto.ActivityScheduleInfoNotifyOuterClass.ActivityScheduleInfoNotify;
import emu.grasscutter.server.packet.send.PacketActivityScheduleInfoNotify;
import emu.grasscutter.utils.JsonUtils;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ActivityScheduleTest {
    @TempDir Path directory;

    private HistoricalActivity crucible() {
        var event = new HistoricalActivity();
        event.setKey("5001-2020-10-12");
        event.setActivityId(5001);
        event.setActivityType(2);
        event.setScheduleId(5001001);
        event.setOfficialBeginTime("2020-10-12T10:00:00+08:00");
        event.setOfficialEndTime("2020-10-19T03:59:00+08:00");
        return event;
    }

    @Test
    void disableAndResumePreserveScheduleButRerunGetsFreshProgressIdentity() {
        long now = System.currentTimeMillis();
        var first = ActivityScheduleStore.plan(List.of(), crucible(), "enable", 7, now);
        var closed = ActivityScheduleStore.plan(first, crucible(), "disable", 0, now + 1000);
        assertFalse(first.get(0).isDisabled(), "Planning must not modify the current config");
        assertFalse(closed.get(0).isActiveAt(now + 1000));
        var resumed = ActivityScheduleStore.plan(closed, crucible(), "enable", 30, now + 2000);
        assertEquals(first.get(0).getScheduleId(), resumed.get(0).getScheduleId());
        assertEquals(first.get(0).getEndTime(), resumed.get(0).getEndTime());
        var rerun = ActivityScheduleStore.plan(resumed, crucible(), "rerun", 14, now + 3000);
        assertNotEquals(first.get(0).getScheduleId(), rerun.get(0).getScheduleId());
        assertEquals(now + 3000, rerun.get(0).getBeginTime().getTime());
    }

    @Test
    void activityWindowIncludesStartAndExcludesEndAndPacketClosesDisabledEntry() throws Exception {
        long now = System.currentTimeMillis();
        var item = ActivityScheduleStore.plan(List.of(), crucible(), "enable", 1, now).get(0);
        assertTrue(item.isActiveAt(now));
        assertFalse(item.isActiveAt(now - 1));
        assertFalse(item.isActiveAt(item.getEndTime().getTime()));
        item.setDisabled(true);
        var packet = new PacketActivityScheduleInfoNotify(List.of(item));
        var proto = ActivityScheduleInfoNotify.parseFrom(packet.getData());
        assertFalse(proto.getActivityScheduleList(0).getIsOpen());
    }

    @Test
    void unknownTypeAndInvalidDurationCannotReplaceExistingConfiguration() {
        var event = crucible();
        var current = ActivityScheduleStore.plan(List.of(), event, "enable", 7, 1000);
        assertThrows(
                IllegalArgumentException.class,
                () -> ActivityScheduleStore.plan(current, event, "enable", 366, 2000));
        event.setActivityType(0);
        assertThrows(
                IllegalArgumentException.class,
                () -> ActivityScheduleStore.plan(current, event, "rerun", 7, 2000));
        assertEquals(1, current.size());
    }

    @Test
    void unrelatedActivityDoesNotChangeCrucibleScheduleNamespace() {
        var other = new ActivityConfigItem();
        other.setActivityId(5072);
        other.setScheduleId(5072001);
        other.setBeginTime(new Date(1000));
        other.setEndTime(new Date(100000));
        var result = ActivityScheduleStore.plan(List.of(other), crucible(), "enable", 7, 1000);
        assertEquals(5001001, result.get(0).getScheduleId());
        assertEquals(5072001, result.get(1).getScheduleId());
    }

    @Test
    void verifiedGeneralTypeZeroIsDifferentFromAnUnknownType() {
        var event = crucible();
        event.setActivityType(0);
        event.setTypeName("NEW_ACTIVITY_GENERAL");
        assertDoesNotThrow(() -> ActivityScheduleStore.plan(List.of(), event, "enable", 7, 1000));
    }

    @Test
    void schedulePersistsAsUtf8AndSurvivesRestartWithoutTransientHandler() throws Exception {
        var items = ActivityScheduleStore.plan(List.of(), crucible(), "enable", 7, 1000);
        var path = directory.resolve("ActivityConfig.json");
        ActivityScheduleStore.write(path, items);
        var loaded = JsonUtils.loadToList(path, ActivityConfigItem.class);
        assertEquals(items.get(0).getScheduleId(), loaded.get(0).getScheduleId());
        assertEquals(items.get(0).getHistoryKey(), loaded.get(0).getHistoryKey());
        assertNull(loaded.get(0).getActivityHandler());
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void officialTimesKeepChinaTimezoneAndVersionOpeningDatePrecision() {
        assertEquals(1602468000L, HistoricalActivity.epoch("2020-10-12T10:00:00+08:00"));
        assertEquals(1608652800L, HistoricalActivity.epoch("2020-12-23"));
    }

    @Test
    void extendingExpiryPreservesProgressIdentityAndMovesGameplayDeadline() throws Exception {
        long now = 1_790_000_000_000L;
        var current = ActivityScheduleStore.plan(List.of(), crucible(), "enable", 7, now);
        var original = current.get(0);
        long end = now + Duration.ofDays(30).toMillis();
        var next = ActivityScheduleStore.reschedule(current, crucible(), end, now + 1000);
        var adjusted = next.get(0);
        assertEquals(original.getScheduleId(), adjusted.getScheduleId());
        assertEquals(original.getHistoryKey(), adjusted.getHistoryKey());
        assertEquals(original.getBeginTime(), adjusted.getBeginTime());
        assertEquals(original.getOpenTime(), adjusted.getOpenTime());
        assertEquals(end, adjusted.getEndTime().getTime());
        assertEquals(end, adjusted.getCloseTime().getTime());
        assertTrue(adjusted.isOpenAt(end - 1));
        assertFalse(adjusted.isOpenAt(end));
        assertNotEquals(end, original.getEndTime().getTime(), "Validation must not mutate live config");
        var path = directory.resolve("ActivityConfig.json");
        ActivityScheduleStore.write(path, next);
        var loaded = JsonUtils.loadToList(path, ActivityConfigItem.class).get(0);
        assertEquals(adjusted.getScheduleId(), loaded.getScheduleId());
        assertEquals(adjusted.getEndTime(), loaded.getEndTime());
        assertEquals(adjusted.getCloseTime(), loaded.getCloseTime());
    }

    @Test
    void shorteningExpiryPreservesRewardClaimWindowAndDisabledState() {
        long now = 1_790_000_000_000L;
        var current = ActivityScheduleStore.plan(List.of(), crucible(), "enable", 30, now);
        var original = current.get(0);
        original.setCloseTime(
                new Date(original.getEndTime().getTime() - Duration.ofDays(3).toMillis()));
        original.setDisabled(true);
        long end = now + Duration.ofDays(14).toMillis();
        var adjusted = ActivityScheduleStore.reschedule(current, crucible(), end, now + 1000).get(0);
        assertEquals(end, adjusted.getEndTime().getTime());
        assertEquals(end - Duration.ofDays(3).toMillis(), adjusted.getCloseTime().getTime());
        assertTrue(adjusted.isDisabled());
        assertEquals(original.getScheduleId(), adjusted.getScheduleId());
        assertEquals(original.getBeginTime(), adjusted.getBeginTime());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ActivityScheduleStore.reschedule(
                                current, crucible(), now + Duration.ofDays(2).toMillis(), now + 1000));
    }

    @Test
    void invalidExpiryOrDifferentHistoryCannotReplaceCurrentSchedule() {
        long now = 1_790_000_000_000L;
        var current = ActivityScheduleStore.plan(List.of(), crucible(), "enable", 7, now);
        for (long end : new long[] {now, now - 1000, Long.MAX_VALUE, (Integer.MAX_VALUE + 1L) * 1000}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ActivityScheduleStore.reschedule(current, crucible(), end, now));
        }
        var different = crucible();
        different.setKey("different-release");
        long future = now + Duration.ofDays(30).toMillis();
        assertThrows(
                IllegalArgumentException.class,
                () -> ActivityScheduleStore.reschedule(current, different, future, now));
        assertThrows(
                IllegalArgumentException.class,
                () -> ActivityScheduleStore.reschedule(List.of(), crucible(), future, now));
        assertEquals(now + Duration.ofDays(7).toMillis(), current.get(0).getEndTime().getTime());
    }

    @Test
    void expiryUpdateLeavesOtherActivityWindowsUntouched() {
        long now = 1_790_000_000_000L;
        var current =
                new ArrayList<>(ActivityScheduleStore.plan(List.of(), crucible(), "enable", 7, now));
        var other = new ActivityConfigItem();
        other.setActivityId(5003);
        other.setScheduleId(5003001);
        other.setBeginTime(new Date(now));
        other.setEndTime(new Date(now + Duration.ofDays(14).toMillis()));
        other.onLoad();
        current.add(other);
        var adjusted =
                ActivityScheduleStore.reschedule(
                        current, crucible(), now + Duration.ofDays(30).toMillis(), now);
        assertEquals(
                other,
                adjusted.stream().filter(item -> item.getActivityId() == 5003).findFirst().orElseThrow());
    }

    @Test
    void requestRejectsFractionalDaysAndTimestampsInsteadOfTruncatingThem() {
        var request = new JsonObject();
        assertEquals(0, ActivityScheduleStore.requestedDays(request));
        for (int days : new int[] {0, 1, 365}) {
            request.addProperty("durationDays", days);
            assertEquals(days, ActivityScheduleStore.requestedDays(request));
        }
        for (double days : new double[] {-1, 0.5, 366}) {
            request.addProperty("durationDays", days);
            assertThrows(
                    IllegalArgumentException.class, () -> ActivityScheduleStore.requestedDays(request));
        }
        assertThrows(IllegalArgumentException.class, () -> ActivityScheduleStore.requestedEnd(request));
        request.addProperty("endTime", 1_790_000_000L);
        assertEquals(1_790_000_000_000L, ActivityScheduleStore.requestedEnd(request));
        request.addProperty("endTime", 1_790_000_000.5);
        assertThrows(IllegalArgumentException.class, () -> ActivityScheduleStore.requestedEnd(request));
        request.addProperty("endTime", Long.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> ActivityScheduleStore.requestedEnd(request));
    }
}
