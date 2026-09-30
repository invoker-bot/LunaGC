package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.excels.activity.MpPlayGroupData;
import emu.grasscutter.game.activity.crucible.CrucibleInvitation;
import emu.grasscutter.game.activity.crucible.CrucibleInvitation.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CrucibleInvitationTest {
    private static Context team() { return new Context(5001005, 42, 10001, Map.of(10001, 8, 10002, 6)); }

    @Test void matchingConsentCannotTriggerLocalPreparationBeforeWorldsAreMerged() {
        var invitation = new CrucibleInvitation();
        invitation.start(team(), 1000, 30, 20, false);
        assertEquals(Reply.ALL_AGREED, invitation.reply(10002, true, 1001));
        assertEquals(Phase.MATCHING, invitation.phase());
        assertEquals(0, invitation.prepareEndTime());
        assertFalse(invitation.beginBattle(2000));
        assertFalse(invitation.acceptsEvent(invitation.serial(), Event.PREPARE));
        assertFalse(invitation.expire(1300));
        assertTrue(invitation.expire(1301));
        assertTrue(invitation.startPrepared(team(), 2000, 20));
        assertEquals(Phase.PREPARING, invitation.phase());
        assertEquals(2020, invitation.prepareEndTime());
        assertTrue(invitation.beginBattle(2020));
    }

    @Test void guestsMustConsentAndTheRosterCannotChangeMidPreparation() {
        var invitation = new CrucibleInvitation();
        assertTrue(invitation.start(team(), 1000, 30, 20));
        assertEquals(Phase.INVITING, invitation.phase());
        assertEquals(Reply.INVALID, invitation.reply(10003, true, 1001));
        assertEquals(Reply.INVALID, invitation.reply(10001, true, 1001));
        assertFalse(invitation.start(team(), 1001, 30, 20));
        assertEquals(Reply.ALL_AGREED, invitation.reply(10002, true, 1002));
        assertEquals(1022, invitation.prepareEndTime());
        assertEquals(Reply.INVALID, invitation.reply(10002, true, 1003));
        assertFalse(invitation.beginBattle(1021));
        assertTrue(invitation.beginBattle(1022));
        assertFalse(invitation.beginBattle(1023));
        assertEquals(Map.of(10001, 8, 10002, 6), invitation.context().members());
        assertThrows(UnsupportedOperationException.class, () -> invitation.context().members().put(10003, 1));
        assertTrue(invitation.battleStarted());
        invitation.finish();
        assertEquals(Phase.IDLE, invitation.phase());
    }

    @Test void declineTimeoutAndCancellationInvalidateQueuedPreparation() {
        var invitation = new CrucibleInvitation();
        invitation.start(team(), 1000, 30, 20);
        assertEquals(Reply.REJECTED, invitation.reply(10002, false, 1001));
        assertEquals(Phase.IDLE, invitation.phase());
        invitation.start(team(), 2000, 30, 20);
        assertEquals(Reply.TIMED_OUT, invitation.reply(10002, true, 2030));
        assertFalse(invitation.beginBattle(2100));
        invitation.start(team(), 3000, 30, 20);
        assertFalse(invitation.expire(3029));
        assertTrue(invitation.expire(3030));
        assertFalse(invitation.expire(3031));
        invitation.start(team(), 4000, 30, 20);
        invitation.reply(10002, true, 4001);
        long old = invitation.serial();
        assertTrue(invitation.isCurrent(old));
        assertTrue(invitation.cancel());
        assertFalse(invitation.isCurrent(old));
        assertEquals(0, invitation.prepareEndTime());
        assertFalse(invitation.cancel());
        invitation.start(team(), 5000, 30, 20);
        assertFalse(invitation.isCurrent(old));
        assertFalse(invitation.beginBattle(6000));
    }

    @Test void soloPreparationAndValidationUseTheSameLifecycle() {
        var invitation = new CrucibleInvitation();
        assertThrows(IllegalArgumentException.class,
                () -> invitation.start(new Context(1, 1, 10001, Map.of(10002, 8)), 1000, 30, 20));
        assertEquals(Phase.IDLE, invitation.phase());
        assertTrue(invitation.start(new Context(5001005, 42, 10001, Map.of(10001, 8)), 1000, 30, 20));
        assertEquals(Phase.PREPARING, invitation.phase());
        assertEquals(1020, invitation.prepareEndTime());
        assertFalse(invitation.expire(1030), "The invite timeout must not cancel a solo preparation");
        assertTrue(invitation.beginBattle(1020));
        assertTrue(invitation.battleStarted());
        assertFalse(invitation.battleStarted());
    }

    @Test void theResourceCenterRadiusMustNotDeserializeAsZero() {
        var data = new Gson().fromJson("""
                {"playId":1,"centerRadius":250,"prepareTime":20,"centerPosList":[2342.75,283.9,-1730.98]}
                """, MpPlayGroupData.class);
        assertEquals(250, data.getRadius());
        assertEquals(20, data.getPrepareTime());
        assertEquals(2342.75f, data.centerPosition().getX());
    }

    @Test void queuedLuaEventsCannotReenableOrStartAReplacementInvitation() {
        var invitation = new CrucibleInvitation();
        invitation.start(team(), 1000, 30, 20);
        long first = invitation.serial();
        assertFalse(invitation.acceptsEvent(first, Event.PREPARE));
        invitation.reply(10002, true, 1001);
        assertTrue(invitation.acceptsEvent(first, Event.PREPARE));
        assertFalse(invitation.acceptsEvent(first, Event.BATTLE));
        invitation.beginBattle(1021);
        assertTrue(invitation.acceptsEvent(first, Event.BATTLE));
        invitation.cancel();
        long cancelled = invitation.serial();
        assertTrue(invitation.acceptsEvent(cancelled, Event.INTERRUPT));
        assertFalse(invitation.acceptsEvent(first, Event.PREPARE));
        assertFalse(invitation.acceptsEvent(first, Event.BATTLE));
        invitation.start(team(), 2000, 30, 20);
        invitation.reply(10002, true, 2001);
        assertFalse(invitation.acceptsEvent(cancelled, Event.INTERRUPT));
        assertFalse(invitation.acceptsEvent(first, Event.BATTLE));
        long second = invitation.serial();
        assertTrue(invitation.acceptsEvent(second, Event.PREPARE));
        invitation.beginBattle(2021);
        invitation.battleStarted();
        assertFalse(invitation.acceptsEvent(second, Event.PREPARE));
        assertFalse(invitation.acceptsEvent(second, Event.BATTLE));
    }
}
