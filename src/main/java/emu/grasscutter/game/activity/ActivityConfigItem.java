package emu.grasscutter.game.activity;

import java.util.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ActivityConfigItem {
    int activityId;
    int activityType;
    int scheduleId;
    List<Integer> meetCondList;
    Date beginTime;
    Date openTime;
    Date closeTime;
    Date endTime;
    boolean disabled;
    String historyKey;

    transient ActivityHandler activityHandler;

    public void onLoad() {
        if (openTime == null) {
            this.openTime = beginTime;
        }

        if (closeTime == null) {
            this.closeTime = endTime;
        }
    }

    public boolean isActiveAt(long time) {
        return !disabled && beginTime != null && endTime != null
                && time >= beginTime.getTime() && time < endTime.getTime();
    }

    public boolean isOpenAt(long time) {
        return isActiveAt(time) && openTime != null && closeTime != null
                && time >= openTime.getTime() && time < closeTime.getTime();
    }
}
