package emu.grasscutter.game.beyond;

import dev.morphia.annotations.Entity;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.net.proto.WorldWatcherAllDataNotifyOuterClass.WorldWatcherAllDataNotify;
import emu.grasscutter.net.proto.WorldWatcherInfoOuterClass.WorldWatcherInfo;
import emu.grasscutter.net.proto.WorldWatcherProgressOuterClass.WorldWatcherProgress;
import java.util.*;

/** Persisted handbook state. Progress must come from validated server gameplay events. */
@Entity(useDiscriminator = false)
public final class BeyondProgress {
    private Map<Integer, Integer> watcherProgress = new HashMap<>();
    private Set<Integer> claimedGroups = new HashSet<>();

    public synchronized WorldWatcherAllDataNotify toProto() {
        return toProto(
                GameData.getBeyondHandbookDataMap().values(), GameData.getBeyondHandbookWatcherDataMap());
    }

    public synchronized WorldWatcherAllDataNotify toProto(
            Collection<BeyondHandbookData> groups, Map<Integer, BeyondHandbookWatcherData> watchers) {
        var result = WorldWatcherAllDataNotify.newBuilder();
        groups.stream()
                .sorted(Comparator.comparingInt(BeyondHandbookData::getId))
                .forEach(
                        group -> {
                            var info = WorldWatcherInfo.newBuilder().setGroupId(group.getId());
                            int finished = 0;
                            for (int id : group.getWatcherIdList()) {
                                var config = watchers.get(id);
                                // Missing requirements can never turn into completed tasks.
                                int value = Math.max(0, watcherProgress.getOrDefault(id, 0));
                                int cap = config == null ? 0 : config.getProgress();
                                if (cap > 0) value = Math.min(value, cap);
                                info.addWatcherProgressList(
                                        WorldWatcherProgress.newBuilder().setWatcherId(id).setProgress(value));
                                if (cap > 0 && value >= cap) {
                                    finished++;
                                    info.addFinishedWatcherList(id);
                                }
                            }
                            boolean complete =
                                    !group.getWatcherIdList().isEmpty()
                                            && ("LOGIC_OR".equals(group.getLogic())
                                                    ? finished > 0
                                                    : finished == group.getWatcherIdList().size());
                            info.setRewardState(claimedGroups.contains(group.getId()) ? 2 : complete ? 1 : 0);
                            result.addInfoList(info);
                        });
        return result.build();
    }
}
