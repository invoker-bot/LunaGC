package emu.grasscutter.game.activity;

import java.time.*;
import lombok.Data;

/** Dated public facts, tied to a specific activity record in the selected client resources. */
@Data
public class HistoricalActivity {
    private String key;
    private int activityId;
    private int activityType;
    private int scheduleId;
    private String typeName;
    private String name;
    private String version;
    private String officialBeginTime;
    private String officialEndTime;
    private String officialBeginDescription;
    private String officialEndDescription;
    private String sourceUrl;

    public boolean hasKnownType() {
        return activityType > 0 || "NEW_ACTIVITY_GENERAL".equals(typeName);
    }

    public static long epoch(String value) {
        if (value == null || value.isBlank()) return 0;
        return value.length() == 10
                ? LocalDate.parse(value).atStartOfDay(ZoneOffset.ofHours(8)).toEpochSecond()
                : OffsetDateTime.parse(value).toEpochSecond();
    }

    public long durationMillis() {
        var duration = (epoch(officialEndTime) - epoch(officialBeginTime)) * 1000;
        return duration > 0 ? duration : Duration.ofDays(7).toMillis();
    }
}
