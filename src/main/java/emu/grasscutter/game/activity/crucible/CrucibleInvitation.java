package emu.grasscutter.game.activity.crucible;

import java.util.*;

/** Consent and preparation for one world's Crucible, with immutable round membership. */
public final class CrucibleInvitation {
    public enum Phase { IDLE, INVITING, MATCHING, PREPARING, STARTING, BATTLE }
    public enum Reply { INVALID, ACCEPTED, ALL_AGREED, REJECTED, TIMED_OUT }
    public enum Event { PREPARE, BATTLE, INTERRUPT }
    public record Context(int scheduleId, long sceneTicket, int ownerUid, Map<Integer, Integer> members) {
        public Context { members = Map.copyOf(members); }
    }

    private Phase phase = Phase.IDLE;
    private Context context;
    private long serial;
    private final Set<Integer> agreed = new HashSet<>();
    private long inviteDeadline;
    private long prepareDeadline;
    private int prepareSeconds;
    private boolean matching;

    public synchronized Phase phase() { return phase; }
    public synchronized Context context() { return context; }
    public synchronized long serial() { return serial; }
    public synchronized boolean isCurrent(long value) { return value == serial; }
    public synchronized boolean acceptsEvent(long value, Event event) {
        if (value != serial) return false;
        return switch (event) {
            case PREPARE -> phase == Phase.PREPARING || phase == Phase.STARTING;
            case BATTLE -> phase == Phase.STARTING;
            case INTERRUPT -> phase == Phase.IDLE;
        };
    }
    public synchronized int prepareEndTime() {
        return phase == Phase.PREPARING || phase == Phase.STARTING ? (int) prepareDeadline : 0;
    }

    public synchronized boolean start(Context next, long now, int inviteSeconds, int prepareSeconds) {
        return start(next, now, inviteSeconds, prepareSeconds, true);
    }

    public synchronized boolean start(Context next, long now, int inviteSeconds, int prepareSeconds, boolean skipMatch) {
        if (phase != Phase.IDLE) return false;
        Objects.requireNonNull(next);
        if (next.scheduleId() <= 0 || next.ownerUid() <= 0 || next.members().isEmpty()
                || next.members().size() > 4 || !next.members().containsKey(next.ownerUid())
                || next.members().keySet().stream().anyMatch(uid -> uid <= 0)
                || inviteSeconds <= 0 || prepareSeconds < 0)
            throw new IllegalArgumentException("Invalid Crucible invitation");
        long deadline = Math.addExact(now, inviteSeconds);
        Math.addExact(deadline, prepareSeconds);
        context = next;
        serial++;
        agreed.clear();
        agreed.add(next.ownerUid());
        inviteDeadline = deadline;
        this.prepareSeconds = prepareSeconds;
        matching = !skipMatch;
        phase = Phase.INVITING;
        if (next.members().size() == 1) prepare(now);
        return true;
    }

    public synchronized Reply reply(int uid, boolean agree, long now) {
        if (phase != Phase.INVITING || uid == context.ownerUid()
                || !context.members().containsKey(uid) || agreed.contains(uid)) return Reply.INVALID;
        if (expire(now)) return Reply.TIMED_OUT;
        if (!agree) { cancel(); return Reply.REJECTED; }
        agreed.add(uid);
        if (agreed.size() == context.members().size()) {
            prepare(now);
            return Reply.ALL_AGREED;
        }
        return Reply.ACCEPTED;
    }

    private void prepare(long now) {
        if (matching) {
            inviteDeadline = Math.addExact(now, 300);
            prepareDeadline = 0;
            phase = Phase.MATCHING;
            return;
        }
        prepareDeadline = Math.addExact(now, prepareSeconds);
        phase = Phase.PREPARING;
    }

    public synchronized boolean expire(long now) {
        if (phase != Phase.INVITING && phase != Phase.MATCHING || now < inviteDeadline) return false;
        cancel();
        return true;
    }

    /** Matchmaking already obtained consent from every participant before merging worlds. */
    public synchronized boolean startPrepared(Context next, long now, int prepareSeconds) {
        if (!start(next, now, 1, prepareSeconds, true)) return false;
        agreed.addAll(next.members().keySet());
        prepare(now);
        return true;
    }

    public synchronized boolean beginBattle(long now) {
        if (phase != Phase.PREPARING || now < prepareDeadline) return false;
        phase = Phase.STARTING;
        return true;
    }

    public synchronized boolean battleStarted() {
        if (phase != Phase.STARTING) return false;
        phase = Phase.BATTLE;
        return true;
    }

    public synchronized void finish() { if (phase == Phase.BATTLE) { phase = Phase.IDLE; serial++; } }
    public synchronized boolean cancel() {
        if (phase == Phase.IDLE) return false;
        phase = Phase.IDLE;
        serial++;
        return true;
    }
}
