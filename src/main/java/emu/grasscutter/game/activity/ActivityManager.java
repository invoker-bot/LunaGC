package emu.grasscutter.game.activity;

import com.esotericsoftware.reflectasm.ConstructorAccess;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.game.activity.condition.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.proto.ActivityInfoOuterClass;
import emu.grasscutter.server.packet.send.*;
import java.util.*;
import java.util.concurrent.*;
import lombok.Getter;

@Getter
public class ActivityManager extends BasePlayerManager {
    public record Configuration(Map<Integer, ActivityConfigItem> activities,
                                Map<Integer, ActivityConfigItem> schedules) {}

    private static volatile Configuration configuration = new Configuration(Map.of(), Map.of());
    private final Map<Integer, PlayerActivityData> playerActivityDataMap = new ConcurrentHashMap<>();
    private ActivityConditionExecutor conditionExecutor;

    public static Map<Integer, ActivityConfigItem> getScheduleActivityConfigMap() {
        return configuration.schedules();
    }

    /** Build all handlers before publishing, so invalid input leaves the live state intact. */
    public static Configuration prepareConfiguration(Collection<ActivityConfigItem> items) {
        var handlers = new HashMap<ActivityType, ConstructorAccess<?>>();
        var watchers = new HashMap<WatcherTriggerType, ConstructorAccess<?>>();
        Grasscutter.reflector.getSubTypesOf(ActivityHandler.class).forEach(type -> {
            var annotation = type.getAnnotation(GameActivity.class);
            if (annotation != null) handlers.put(annotation.value(), ConstructorAccess.get(type));
        });
        Grasscutter.reflector.getSubTypesOf(ActivityWatcher.class).forEach(type -> {
            var annotation = type.getAnnotation(ActivityWatcherType.class);
            if (annotation != null) watchers.put(annotation.value(), ConstructorAccess.get(type));
        });
        var activities = new HashMap<Integer, ActivityConfigItem>();
        var schedules = new HashMap<Integer, ActivityConfigItem>();
        var activityIds = new HashSet<Integer>();
        for (var item : items) {
            item.onLoad();
            if (item.getBeginTime() == null || item.getEndTime() == null
                    || !item.getEndTime().after(item.getBeginTime())
                    || item.getScheduleId() <= 0 || item.getActivityId() <= 0) {
                throw new IllegalArgumentException("活动时间或排期 ID 无效: " + item.getActivityId());
            }
            if (!activityIds.add(item.getActivityId()) || schedules.putIfAbsent(item.getScheduleId(), item) != null) {
                throw new IllegalArgumentException("重复的活动或排期 ID: " + item.getActivityId());
            }
            if (item.isDisabled()) continue;
            var data = GameData.getActivityDataMap().get(item.getActivityId());
            if (data == null) throw new IllegalArgumentException("活动资源不存在: " + item.getActivityId());
            var constructor = handlers.get(ActivityType.getTypeByName(data.getActivityType()));
            var handler = constructor == null ? new DefaultActivityHandler()
                    : (ActivityHandler) constructor.newInstance();
            handler.setActivityConfigItem(item);
            handler.initWatchers(watchers);
            item.setActivityHandler(handler);
            activities.put(item.getActivityId(), item);
        }
        return new Configuration(Map.copyOf(activities), Map.copyOf(schedules));
    }

    public static void installConfiguration(Configuration prepared) { configuration = prepared; }

    public static void loadActivityConfigData() {
        try {
            installConfiguration(prepareConfiguration(DataLoader.loadList("ActivityConfig.json", ActivityConfigItem.class)));
            Grasscutter.getLogger().debug("Loaded {} activities.", configuration.activities().size());
        } catch (Exception e) {
            Grasscutter.getLogger().warn("Unable to load activities config.", e);
        }
    }

    public ActivityManager(Player player) {
        super(player);
        refreshActivities();
    }

    /** Closing preserves progress; a fresh schedule resets the existing database document. */
    public synchronized void refreshActivities() {
        var snapshot = configuration;
        playerActivityDataMap.keySet().removeIf(id -> !snapshot.activities().containsKey(id));
        for (var item : snapshot.activities().values()) {
            var data = playerActivityDataMap.get(item.getActivityId());
            if (data == null) data = PlayerActivityData.getByPlayer(player, item.getActivityId());
            if (data == null || (data.getScheduleId() != 0 && data.getScheduleId() != item.getScheduleId())) {
                var previousId = data == null ? null : data.getId();
                data = item.getActivityHandler().initPlayerActivityData(player);
                data.setId(previousId);
                data.save();
            } else if (data.getScheduleId() == 0) {
                data.setScheduleId(item.getScheduleId());
                data.save();
            }
            data.setPlayer(player);
            data.setActivityHandler(item.getActivityHandler());
            playerActivityDataMap.put(item.getActivityId(), data);
        }
        conditionExecutor = new BasicActivityConditionExecutor(snapshot.activities(),
                GameData.getActivityCondExcelConfigDataMap(),
                PlayerActivityDataMappingBuilder.buildPlayerActivityDataByActivityCondId(playerActivityDataMap),
                AllActivityConditionBuilder.buildActivityConditions());
        player.sendPacket(new PacketActivityScheduleInfoNotify(snapshot.schedules().values()));
        snapshot.activities().values().stream().filter(item -> item.isActiveAt(System.currentTimeMillis()))
                .forEach(item -> player.sendPacket(new PacketActivityInfoNotify(getInfoProtoByActivityId(item.getActivityId()))));
    }

    public void triggerWatcher(WatcherTriggerType type, String... params) {
        configuration.activities().values().stream()
                .filter(item -> item.isActiveAt(System.currentTimeMillis()))
                .map(ActivityConfigItem::getActivityHandler)
                .map(ActivityHandler::getWatchersMap).map(map -> map.get(type))
                .filter(Objects::nonNull).flatMap(Collection::stream)
                .forEach(watcher -> {
                    var data = playerActivityDataMap.get(watcher.getActivityHandler().getActivityConfigItem().getActivityId());
                    if (data != null) watcher.trigger(data, params);
                });
    }

    /** A queued round must not contribute to a newer activity schedule. */
    public synchronized void triggerWatcher(int activityId, int scheduleId, WatcherTriggerType type, String... params) {
        var item = configuration.activities().get(activityId);
        var data = playerActivityDataMap.get(activityId);
        if (item == null || data == null || !item.isActiveAt(System.currentTimeMillis())
                || item.getScheduleId() != scheduleId || data.getScheduleId() != scheduleId) return;
        item.getActivityHandler().getWatchersMap().getOrDefault(type, List.of())
                .forEach(watcher -> watcher.trigger(data, params));
    }

    public boolean isActivityActive(int id) {
        var item = configuration.activities().get(id);
        return item != null && item.isActiveAt(System.currentTimeMillis());
    }

    public boolean hasActivityEnded(int id) {
        var item = configuration.activities().get(id);
        return item == null || System.currentTimeMillis() >= item.getEndTime().getTime();
    }

    public boolean isActivityOpen(int id) {
        var item = configuration.activities().get(id);
        return item != null && item.isOpenAt(System.currentTimeMillis());
    }

    public int getOpenDay(int id) {
        var item = configuration.activities().get(id);
        if (item == null || System.currentTimeMillis() < item.getOpenTime().getTime()) return 0;
        return item == null ? 0 : Math.max(0, (int) TimeUnit.MILLISECONDS.toDays(
                System.currentTimeMillis() - item.getOpenTime().getTime()) + 1);
    }

    public boolean isActivityClosed(int id) {
        var item = configuration.activities().get(id);
        return item == null || System.currentTimeMillis() >= item.getCloseTime().getTime();
    }

    public boolean meetsCondition(int id) { return conditionExecutor.meetsCondition(id); }

    public void triggerActivityConditions() {
        configuration.activities().values().stream().filter(item -> item.isActiveAt(System.currentTimeMillis()))
                .forEach(item -> item.getActivityHandler().triggerCondEvents(player));
    }

    public ActivityInfoOuterClass.ActivityInfo getInfoProtoByActivityId(int id) {
        var item = configuration.activities().get(id);
        if (item == null || !item.isActiveAt(System.currentTimeMillis())) {
            return ActivityInfoOuterClass.ActivityInfo.newBuilder().setActivityId(id).build();
        }
        return item.getActivityHandler().toProto(playerActivityDataMap.get(id), conditionExecutor);
    }

    public Optional<ActivityHandler> getActivityHandler(ActivityType type) {
        return configuration.activities().values().stream()
                .filter(item -> item.getActivityType() == type.getValue() && item.isActiveAt(System.currentTimeMillis()))
                .map(ActivityConfigItem::getActivityHandler).findFirst();
    }

    public <T extends ActivityHandler> Optional<T> getActivityHandlerAs(ActivityType type, Class<T> clazz) {
        return getActivityHandler(type).filter(clazz::isInstance).map(clazz::cast);
    }

    public Optional<Integer> getActivityIdByActivityType(ActivityType type) {
        return getActivityHandler(type).map(ActivityHandler::getActivityConfigItem).map(ActivityConfigItem::getActivityId);
    }

    public Optional<PlayerActivityData> getPlayerActivityDataByActivityType(ActivityType type) {
        return getActivityIdByActivityType(type).map(playerActivityDataMap::get);
    }

    public Optional<ActivityInfoOuterClass.ActivityInfo> getInfoProtoByActivityType(ActivityType type) {
        return getActivityIdByActivityType(type).map(this::getInfoProtoByActivityId);
    }
}
