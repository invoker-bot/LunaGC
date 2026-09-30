package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.activity.crucible.CrucibleMatchmaking;
import emu.grasscutter.game.activity.crucible.CrucibleMatchmaking.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CrucibleMatchmakingTest {
    private static Party solo(int uid, int level) { return new Party(uid, 5001005, Map.of(uid, level)); }

    @Test void matchmakingRequiresTwoPlayersAndEveryConsentBeforeTransfer() {
        var queue = new CrucibleMatchmaking(2, 4, 5, 300, 30, 60);
        assertTrue(queue.enqueue(solo(10001, 8), 1000));
        assertTrue(queue.findMatches(1006).isEmpty());
        assertTrue(queue.enqueue(solo(10002, 6), 1006));
        var match = queue.findMatches(1006).get(0);
        assertEquals(10002, match.hostUid(), "Both players must be able to enter the chosen world");
        assertEquals(Stage.CONFIRMING, match.stage());
        assertFalse(queue.beginTransfer(match.id(), 1006));
        assertEquals(Reply.INVALID, queue.confirm(10003, true, 1006));
        assertEquals(Reply.ACCEPTED, queue.confirm(10001, true, 1007));
        assertEquals(Reply.INVALID, queue.confirm(10001, true, 1008));
        assertEquals(Reply.ALL_AGREED, queue.confirm(10002, true, 1008));
        assertFalse(queue.allowEnter(10001, 10001, 1008));
        assertTrue(queue.allowEnter(10001, 10002, 1008));
        assertFalse(queue.beginTransfer(match.id(), 1008));
        assertTrue(queue.allowEnter(10002, 10002, 1008));
        assertTrue(queue.beginTransfer(match.id(), 1008));
        assertFalse(queue.beginTransfer(match.id(), 1008));
        assertTrue(queue.transferred(match.id(), 1008));
        assertEquals(Stage.LOADING, queue.runFor(10001).stage());
        assertFalse(queue.complete(match.id() - 1));
        assertTrue(queue.complete(match.id()));
        assertNull(queue.runFor(10001));
        assertNull(queue.runFor(10002));
    }

    @Test void partiesAreAtomicCapacityIsBoundedAndSchedulesNeverMix() {
        var queue = new CrucibleMatchmaking(2, 4, 5, 300, 30, 60);
        var pair = new Party(10001, 5001005, Map.of(10001, 8, 10002, 8));
        assertTrue(queue.enqueue(pair, 1000));
        assertFalse(queue.enqueue(solo(10002, 8), 1000));
        assertTrue(queue.enqueue(new Party(10003, 5001006, Map.of(10003, 8)), 1000));
        assertTrue(queue.enqueue(new Party(10004, 5001005, Map.of(10004, 6, 10005, 6)), 1000));
        var match = queue.findMatches(1000).get(0);
        assertEquals(4, match.members().size());
        assertEquals(10004, match.hostUid());
        assertEquals(Stage.QUEUED, queue.runFor(10003).stage());
        assertEquals(match.id(), queue.runFor(10002).id());
        assertEquals(match.id(), queue.runFor(10005).id());
        assertThrows(UnsupportedOperationException.class, () -> match.members().put(1, 1));
    }

    @Test void cancelAndTimeoutReleaseEveryMemberAndOldRunIdsCannotTransferANewMatch() {
        var queue = new CrucibleMatchmaking(2, 4, 5, 300, 30, 60);
        queue.enqueue(solo(10001, 8), 1000);
        queue.enqueue(solo(10002, 8), 1000);
        var old = queue.findMatches(1005).get(0);
        assertEquals(Reply.DECLINED, queue.confirm(10002, false, 1006));
        assertEquals(old.id(), queue.cancel(10002).id());
        assertNull(queue.runFor(10001));
        queue.enqueue(solo(10001, 8), 2000);
        assertTrue(queue.expire(2299).isEmpty());
        assertEquals(Stage.QUEUED, queue.expire(2300).get(0).stage());
        queue.enqueue(solo(10001, 8), 3000);
        queue.enqueue(solo(10002, 8), 3000);
        var next = queue.findMatches(3005).get(0);
        assertNotEquals(old.id(), next.id());
        assertEquals(Reply.TIMED_OUT, queue.confirm(10001, true, 3035));
        assertFalse(queue.beginTransfer(old.id(), 3006));
        assertEquals(Stage.CONFIRMING, queue.expire(3035).get(0).stage());
        assertNull(queue.runFor(10002));
    }

    @Test void entryPermissionAndLoadingHaveTheirOwnDeadlines() {
        var queue = new CrucibleMatchmaking(2, 4, 5, 300, 30, 60);
        queue.enqueue(solo(10001, 8), 1000);
        queue.enqueue(solo(10002, 8), 1000);
        queue.findMatches(1005);
        queue.confirm(10001, true, 1006);
        queue.confirm(10002, true, 1007);
        assertTrue(queue.expire(1066).isEmpty());
        assertFalse(queue.allowEnter(10001, 10001, 1067));
        assertEquals(Stage.JOINING, queue.expire(1067).get(0).stage());
        queue.enqueue(solo(10001, 8), 2000);
        queue.enqueue(solo(10002, 8), 2000);
        var match = queue.findMatches(2005).get(0);
        queue.confirm(10001, true, 2006);
        queue.confirm(10002, true, 2007);
        queue.allowEnter(10001, match.hostUid(), 2008);
        queue.allowEnter(10002, match.hostUid(), 2008);
        assertTrue(queue.beginTransfer(match.id(), 2008));
        assertFalse(queue.complete(match.id()), "A match cannot complete before the transfer is recorded");
        assertTrue(queue.transferred(match.id(), 2009));
        assertEquals(Stage.LOADING, queue.expire(2069).get(0).stage());
        assertFalse(queue.complete(match.id()));
    }
}
