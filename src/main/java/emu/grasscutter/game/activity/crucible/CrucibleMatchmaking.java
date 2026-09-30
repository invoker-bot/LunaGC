package emu.grasscutter.game.activity.crucible;

import java.util.*;

/** Party membership stays reserved through confirmation, entry permission and scene loading. */
public final class CrucibleMatchmaking {
    public enum Stage { QUEUED, CONFIRMING, JOINING, TRANSFERRING, LOADING }
    public enum Reply { INVALID, ACCEPTED, ALL_AGREED, DECLINED, TIMED_OUT }
    public record Party(int ownerUid, int scheduleId, Map<Integer, Integer> members) {
        public Party {
            members = Map.copyOf(members);
            if (ownerUid <= 0 || scheduleId <= 0 || members.isEmpty() || !members.containsKey(ownerUid)
                    || members.entrySet().stream().anyMatch(e -> e.getKey() <= 0 || e.getValue() < 0))
                throw new IllegalArgumentException("Invalid matching party");
        }
    }
    public record Run(long id, Stage stage, int hostUid, int scheduleId, List<Party> parties,
                      Map<Integer, Integer> members, long deadline) {
        public Run { parties = List.copyOf(parties); members = Map.copyOf(members); }
    }
    private static final class State {
        long id, created, deadline;
        Stage stage;
        int host;
        List<Party> parties;
        final Set<Integer> agreed = new HashSet<>(), allowed = new HashSet<>();
        Run snapshot() {
            Map<Integer, Integer> members = new LinkedHashMap<>();
            parties.forEach(p -> members.putAll(p.members()));
            return new Run(id, stage, host, parties.get(0).scheduleId(), parties, members, deadline);
        }
    }
    private final int minPlayers, maxPlayers, fillSeconds, queueSeconds, confirmSeconds, loadSeconds;
    private long nextId;
    private final Map<Long, State> runs = new LinkedHashMap<>();
    private final Map<Integer, State> byUid = new HashMap<>();

    public CrucibleMatchmaking(int minPlayers, int maxPlayers, int fillSeconds, int queueSeconds,
                               int confirmSeconds, int loadSeconds) {
        if (minPlayers < 2 || maxPlayers < minPlayers || maxPlayers > 4 || fillSeconds < 0
                || queueSeconds <= 0 || confirmSeconds <= 0 || loadSeconds <= 0)
            throw new IllegalArgumentException("Invalid matching limits");
        this.minPlayers = minPlayers; this.maxPlayers = maxPlayers; this.fillSeconds = fillSeconds;
        this.queueSeconds = queueSeconds; this.confirmSeconds = confirmSeconds; this.loadSeconds = loadSeconds;
    }

    public synchronized boolean enqueue(Party party, long now) {
        if (party.members().size() > maxPlayers || party.members().keySet().stream().anyMatch(byUid::containsKey)) return false;
        State state = new State();
        state.id = ++nextId; state.stage = Stage.QUEUED; state.created = now;
        state.deadline = Math.addExact(now, queueSeconds); state.parties = List.of(party);
        register(state);
        return true;
    }

    /** Lowest eligible host world, FIFO whole parties, bounded by the resource's player count. */
    public synchronized List<Run> findMatches(long now) {
        List<Run> found = new ArrayList<>();
        var queued = runs.values().stream().filter(s -> s.stage == Stage.QUEUED && now < s.deadline)
                .sorted(Comparator.comparingInt((State s) -> s.parties.get(0).members().get(s.parties.get(0).ownerUid()))
                        .thenComparingLong(s -> s.created).thenComparingLong(s -> s.id)).toList();
        for (State host : queued) {
            if (!runs.containsKey(host.id)) continue;
            Party hostParty = host.parties.get(0);
            int level = hostParty.members().get(hostParty.ownerUid());
            if (hostParty.members().values().stream().anyMatch(v -> v < level)) continue;
            List<State> selected = new ArrayList<>(List.of(host));
            int size = hostParty.members().size();
            var candidates = runs.values().stream().filter(s -> s.stage == Stage.QUEUED && s != host && now < s.deadline)
                    .sorted(Comparator.comparingLong((State s) -> s.created).thenComparingLong(s -> s.id)).toList();
            for (State other : candidates) {
                Party party = other.parties.get(0);
                if (party.scheduleId() != hostParty.scheduleId() || size + party.members().size() > maxPlayers
                        || party.members().values().stream().anyMatch(v -> v < level)) continue;
                selected.add(other); size += party.members().size();
            }
            long oldest = selected.stream().mapToLong(s -> s.created).min().orElse(now);
            if (size < minPlayers || size < maxPlayers && now - oldest < fillSeconds) continue;
            State match = new State();
            match.id = ++nextId; match.host = hostParty.ownerUid(); match.stage = Stage.CONFIRMING;
            match.created = now; match.deadline = Math.addExact(now, confirmSeconds);
            match.parties = selected.stream().map(s -> s.parties.get(0)).toList();
            selected.forEach(this::remove); register(match); found.add(match.snapshot());
        }
        return List.copyOf(found);
    }

    public synchronized Run runFor(int uid) { State s = byUid.get(uid); return s == null ? null : s.snapshot(); }
    public synchronized List<Run> runs() { return runs.values().stream().map(State::snapshot).toList(); }

    public synchronized Reply confirm(int uid, boolean agree, long now) {
        State s = byUid.get(uid);
        if (s == null || s.stage != Stage.CONFIRMING || s.agreed.contains(uid)) return Reply.INVALID;
        if (now >= s.deadline) return Reply.TIMED_OUT;
        if (!agree) return Reply.DECLINED;
        s.agreed.add(uid);
        if (s.agreed.size() == s.snapshot().members().size()) {
            s.stage = Stage.JOINING; s.deadline = Math.addExact(now, loadSeconds);
            return Reply.ALL_AGREED;
        }
        return Reply.ACCEPTED;
    }

    public synchronized boolean allowEnter(int uid, int hostUid, long now) {
        State s = byUid.get(uid);
        return s != null && s.stage == Stage.JOINING && s.host == hostUid && now < s.deadline && s.allowed.add(uid);
    }

    public synchronized boolean beginTransfer(long id, long now) {
        State s = runs.get(id);
        if (s == null || s.stage != Stage.JOINING || now >= s.deadline || s.allowed.size() != s.snapshot().members().size()) return false;
        s.stage = Stage.TRANSFERRING;
        return true;
    }

    public synchronized boolean transferred(long id, long now) {
        State s = runs.get(id);
        if (s == null || s.stage != Stage.TRANSFERRING) return false;
        s.stage = Stage.LOADING; s.deadline = Math.addExact(now, loadSeconds);
        return true;
    }

    public synchronized boolean complete(long id) {
        State s = runs.get(id);
        if (s == null || s.stage != Stage.LOADING) return false;
        remove(s); return true;
    }

    public synchronized Run cancel(int uid) {
        State s = byUid.get(uid);
        if (s == null) return null;
        Run result = s.snapshot(); remove(s); return result;
    }

    public synchronized List<Run> expire(long now) {
        var expired = runs.values().stream().filter(s -> now >= s.deadline).toList();
        List<Run> result = expired.stream().map(State::snapshot).toList();
        expired.forEach(this::remove);
        return result;
    }

    private void register(State s) {
        runs.put(s.id, s); s.parties.forEach(p -> p.members().keySet().forEach(uid -> byUid.put(uid, s)));
    }
    private void remove(State s) {
        runs.remove(s.id); s.parties.forEach(p -> p.members().keySet().forEach(uid -> byUid.remove(uid, s)));
    }
}
