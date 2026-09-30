package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.game.activity.crucible.CrucibleRewards;
import emu.grasscutter.game.activity.crucible.CrucibleRewards.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CrucibleRewardsTest {
    private static final Ticket FIRST = new Ticket(5001005, 42, 1);
    private static CrucibleRewards rewards() {
        var ledger = new CrucibleRewards();
        var members = new HashMap<>(Map.of(10001, new Reward(8, 209001500), 10002, new Reward(1, 209000800)));
        ledger.open(FIRST, 40, members);
        members.put(10003, new Reward(8, 209001500));
        return ledger;
    }
    @Test void previewDoesNotChargeAndClaimUsesTheFrozenPersonalLevel() {
        var ledger = rewards(); var debits = new AtomicInteger();
        assertEquals(Result.PREVIEW_REQUIRED, ledger.claim(FIRST, 10002, reward -> { debits.incrementAndGet(); return Result.OK; }));
        assertEquals(Result.OK, ledger.preview(FIRST, 10002));
        assertTrue(ledger.hasRemaining(10002));
        assertEquals(0, debits.get());
        assertEquals(Result.OK, ledger.claim(FIRST, 10002, reward -> {
            assertEquals(new Reward(1, 209000800), reward); debits.incrementAndGet(); return Result.OK;
        }));
        assertEquals(Result.ALREADY_TAKEN, ledger.claim(FIRST, 10002, reward -> fail("Duplicate debit")));
        assertEquals(1, debits.get());
        assertEquals(List.of(10001), ledger.snapshot().remaining());
        assertEquals(List.of(10001, 10002), ledger.snapshot().qualified());
        assertEquals(40, ledger.snapshot().resin());
    }
    @Test void insufficientResinOrFullInventoryCanBeRetriedWithoutLosingEligibility() {
        var ledger = rewards(); ledger.preview(FIRST, 10001);
        for (var refusal : List.of(Result.NOT_ENOUGH_RESIN, Result.INVENTORY_FULL, Result.INVALID_REWARD)) {
            assertEquals(refusal, ledger.claim(FIRST, 10001, reward -> refusal));
            assertTrue(ledger.hasRemaining(10001));
        }
        assertEquals(Result.OK, ledger.claim(FIRST, 10001, reward -> Result.OK));
    }
    @Test void lateJoinersLeavingPlayersAndStaleSchedulesCannotClaim() {
        var ledger = rewards();
        assertEquals(Result.NO_QUALIFICATION, ledger.preview(FIRST, 10003));
        ledger.preview(FIRST, 10002); ledger.forfeit(10002);
        assertEquals(Result.NO_QUALIFICATION, ledger.claim(FIRST, 10002, reward -> fail("Left scene")));
        assertEquals(List.of(10001), ledger.snapshot().qualified());
        for (var stale : List.of(new Ticket(5001006, 42, 1), new Ticket(5001005, 43, 1), new Ticket(5001005, 42, 2)))
            assertEquals(Result.STALE, ledger.claim(stale, 10001, reward -> fail("Old ticket")));
        ledger.clear();
        assertEquals(Result.STALE, ledger.preview(FIRST, 10001));
        assertFalse(ledger.hasRemaining());
    }
    @Test void openingTheSameSuccessTwiceCannotRestoreConsumedRewards() {
        var ledger = rewards(); ledger.preview(FIRST, 10001);
        ledger.claim(FIRST, 10001, reward -> Result.OK);
        assertFalse(ledger.open(FIRST, 40, Map.of(10001, new Reward(8, 209001500))));
        assertEquals(Result.ALREADY_TAKEN, ledger.preview(FIRST, 10001));
        var next = new Ticket(5001005, 42, 2);
        assertTrue(ledger.open(next, 40, Map.of(10001, new Reward(8, 209001500))));
        assertEquals(Result.PREVIEW_REQUIRED, ledger.claim(next, 10001, reward -> fail("New preview required")));
    }
    @Test void simultaneousAndReentrantRequestsCannotDoubleCharge() throws Exception {
        var ledger = rewards(); ledger.preview(FIRST, 10001);
        var debits = new AtomicInteger(); var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Result> claim = () -> { start.await(); return ledger.claim(FIRST, 10001, reward -> {
                debits.incrementAndGet();
                assertEquals(Result.BUSY, ledger.claim(FIRST, 10001, nested -> fail("Reentrant charge")));
                return Result.OK;
            }); };
            var one = pool.submit(claim); var two = pool.submit(claim); start.countDown();
            assertEquals(Set.of(Result.OK, Result.ALREADY_TAKEN), Set.of(one.get(5, TimeUnit.SECONDS), two.get(5, TimeUnit.SECONDS)));
            assertEquals(1, debits.get());
        } finally { pool.shutdownNow(); }
    }
    @Test void deliveryFailureCannotReplayAPotentiallyPartialGrant() {
        var ledger = rewards(); ledger.preview(FIRST, 10001);
        assertThrows(IllegalStateException.class, () -> ledger.claim(FIRST, 10001, reward -> { throw new IllegalStateException("Database failed after debit"); }));
        assertEquals(Result.ALREADY_TAKEN, ledger.claim(FIRST, 10001, reward -> fail("Partial delivery replay")));
        assertTrue(ledger.hasRemaining(10002));
    }
    @Test void clearingInsideACallbackCannotConsumeTheNextRound() {
        var ledger = rewards(); ledger.preview(FIRST, 10001);
        var next = new Ticket(5001005, 43, 1);
        assertEquals(Result.OK, ledger.claim(FIRST, 10001, reward -> {
            ledger.clear(); ledger.open(next, 40, Map.of(10001, new Reward(0, 209000600))); return Result.OK;
        }));
        assertTrue(ledger.hasRemaining(10001));
        assertEquals(Result.OK, ledger.preview(next, 10001));
    }
}
