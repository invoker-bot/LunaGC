package emu.grasscutter.data.excels.activity;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.*;
import java.util.List;
import lombok.Getter;

/** Per-round titles, separate from persistent NewActivityWatcher missions. */
@ResourceType(name = "MpPlayWatcherConfigData.json")
@Getter
public final class MpPlayWatcherData extends GameResource {
    private int id, progress, priority;
    @SerializedName("MpPlayId") private int mpPlayId;
    private boolean isDisuse;
    private Trigger triggerConfig;
    private long challengeTitleTextMapHash, challengeDescTextMapHash;

    @Override public int getId() { return id; }

    @Getter public static final class Trigger {
        private String triggerType;
        private List<String> paramList = List.of();
    }
}
