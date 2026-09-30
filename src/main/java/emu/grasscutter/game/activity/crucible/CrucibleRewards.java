package emu.grasscutter.game.activity.crucible;

import java.util.*;
import java.util.function.Function;

/** One successful round's personal rewards. The scene owns its lifetime. */
public final class CrucibleRewards {
    public record Ticket(int scheduleId, long sceneTicket, long roundSerial) {
        public Ticket {
            if (scheduleId <= 0 || sceneTicket <= 0 || roundSerial <= 0)
                throw new IllegalArgumentException("Invalid Crucible reward ticket");
        }
    }
    public record Reward(int worldLevel, int dropId) {
        public Reward {
            if (worldLevel < 0 || dropId <= 0) throw new IllegalArgumentException("Invalid personal reward");
        }
    }
    public enum Result { OK, STALE, NO_QUALIFICATION, ALREADY_TAKEN, PREVIEW_REQUIRED, BUSY,
        NOT_ENOUGH_RESIN, INVENTORY_FULL, INVALID_REWARD }
    public record Snapshot(int resin, List<Integer> remaining, List<Integer> qualified) {}

    private static final class Round {
        final Ticket ticket;
        final int resin;
        final Map<Integer, Reward> rewards;
        final Set<Integer> remaining, forfeited = new HashSet<>(), previewed = new HashSet<>(), pending = new HashSet<>();
        Round(Ticket ticket, int resin, Map<Integer, Reward> rewards) {
            this.ticket = ticket; this.resin = resin; this.rewards = Map.copyOf(rewards);
            remaining = new HashSet<>(rewards.keySet());
        }
    }
    private Round round;

    public synchronized boolean open(Ticket ticket, int resin, Map<Integer, Reward> rewards) {
        Objects.requireNonNull(ticket);
        if (resin <= 0 || rewards.isEmpty() || rewards.size() > 4
                || rewards.keySet().stream().anyMatch(uid -> uid <= 0))
            throw new IllegalArgumentException("Invalid Crucible reward offer");
        if (round != null && round.ticket.equals(ticket)) return false;
        round = new Round(ticket, resin, rewards);
        return true;
    }
    public synchronized Ticket ticket() { return round == null ? null : round.ticket; }
    public synchronized void clear() { round = null; }
    public synchronized boolean hasRemaining() { return round != null && !round.remaining.isEmpty(); }
    public synchronized boolean hasRemaining(int uid) { return round != null && round.remaining.contains(uid); }
    public synchronized Snapshot snapshot() {
        if (round == null) return new Snapshot(0, List.of(), List.of());
        return new Snapshot(round.resin, round.remaining.stream().sorted().toList(),
                round.rewards.keySet().stream().filter(uid -> !round.forfeited.contains(uid)).sorted().toList());
    }
    private Result check(Ticket ticket, int uid) {
        if (round == null || !round.ticket.equals(ticket)) return Result.STALE;
        if (!round.rewards.containsKey(uid) || round.forfeited.contains(uid)) return Result.NO_QUALIFICATION;
        if (round.pending.contains(uid)) return Result.BUSY;
        return round.remaining.contains(uid) ? Result.OK : Result.ALREADY_TAKEN;
    }
    public synchronized Result preview(Ticket ticket, int uid) {
        var result = check(ticket, uid);
        if (result == Result.OK) round.previewed.add(uid);
        return result;
    }
    /** Reject duplicates before calling inventory/resin code, including reentrant plugin callbacks. */
    public synchronized Result claim(Ticket ticket, int uid, Function<Reward, Result> settle) {
        var result = check(ticket, uid);
        if (result != Result.OK) return result;
        if (!round.previewed.contains(uid)) return Result.PREVIEW_REQUIRED;
        var current = round;
        current.pending.add(uid);
        try {
            result = Objects.requireNonNull(settle.apply(current.rewards.get(uid)));
            if (result == Result.OK) current.remaining.remove(uid);
            return result;
        } catch (RuntimeException exception) {
            // An inventory exception can follow a debit or partial delivery. Never permit a second grant.
            current.remaining.remove(uid);
            throw exception;
        } finally {
            current.pending.remove(uid);
        }
    }
    public synchronized void forfeit(int uid) {
        if (round == null || !round.rewards.containsKey(uid)) return;
        round.forfeited.add(uid); round.remaining.remove(uid); round.previewed.remove(uid);
    }
}
