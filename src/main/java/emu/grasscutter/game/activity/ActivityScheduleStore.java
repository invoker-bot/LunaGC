package emu.grasscutter.game.activity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

public final class ActivityScheduleStore {
    private static final Gson SCHEDULE_JSON = new GsonBuilder().setPrettyPrinting()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").disableHtmlEscaping().create();
    private ActivityScheduleStore() {}

    /** Pure planning: no live config is mutated before validation and a successful disk write. */
    public static List<ActivityConfigItem> plan(List<ActivityConfigItem> current,
            HistoricalActivity event, String action, int durationDays, long now) {
        if (!Set.of("enable", "disable", "rerun").contains(action))
            throw new IllegalArgumentException("action 必须为 enable、disable 或 rerun");
        if (durationDays < 0 || durationDays > 365)
            throw new IllegalArgumentException("开启天数必须在 1 到 365 之间，0 表示历史时长");
        var result = new ArrayList<ActivityConfigItem>();
        ActivityConfigItem selected = null;
        for (var item : current) {
            var copy = SCHEDULE_JSON.fromJson(SCHEDULE_JSON.toJson(item), ActivityConfigItem.class);
            copy.onLoad();
            if (copy.getActivityId() == event.getActivityId()) selected = copy;
            else result.add(copy);
        }
        if ("disable".equals(action)) {
            if (selected != null) { selected.setDisabled(true); result.add(selected); }
            return result;
        }
        if (!event.hasKnownType()) throw new IllegalArgumentException("活动类型尚未核实，暂不能开启");
        boolean resume = "enable".equals(action) && selected != null
                && Objects.equals(selected.getHistoryKey(), event.getKey())
                && selected.getEndTime().getTime() > now;
        if (!resume) {
            int nextSchedule = selected == null ? event.getScheduleId()
                    : Math.addExact(Math.max(event.getScheduleId(), selected.getScheduleId()), 1);
            var occupied = new HashSet<Integer>();
            current.forEach(item -> occupied.add(item.getScheduleId()));
            while (occupied.contains(nextSchedule)) nextSchedule = Math.addExact(nextSchedule, 1);
            selected = new ActivityConfigItem();
            selected.setActivityId(event.getActivityId());
            selected.setActivityType(event.getActivityType());
            selected.setScheduleId(nextSchedule);
            selected.setHistoryKey(event.getKey());
            selected.setMeetCondList(List.of());
            selected.setBeginTime(new Date(now));
            long duration = durationDays == 0 ? event.durationMillis() : Duration.ofDays(durationDays).toMillis();
            selected.setEndTime(new Date(Math.addExact(now, duration)));
            selected.onLoad();
        }
        selected.setDisabled(false);
        result.add(selected);
        result.sort(Comparator.comparingInt(ActivityConfigItem::getActivityId));
        return result;
    }

    public static void write(Path path, List<ActivityConfigItem> items) throws IOException {
        var target = path.toAbsolutePath();
        Files.createDirectories(target.getParent());
        var temp = Files.createTempFile(target.getParent(), "activities-", ".json.tmp");
        try {
            Files.writeString(temp, SCHEDULE_JSON.toJson(items) + "\n", StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temp); }
    }
}
