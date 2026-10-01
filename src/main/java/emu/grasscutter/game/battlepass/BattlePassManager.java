package emu.grasscutter.game.battlepass;

import dev.morphia.annotations.*;
import emu.grasscutter.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.BattlePassScheduleData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.proto.BattlePassCycleOuterClass.BattlePassCycle;
import emu.grasscutter.net.proto.BattlePassRewardTakeOptionOuterClass.BattlePassRewardTakeOption;
import emu.grasscutter.net.proto.BattlePassScheduleOuterClass.BattlePassSchedule;
import emu.grasscutter.net.proto.BattlePassUnlockStatusOuterClass.BattlePassUnlockStatus;
import emu.grasscutter.server.packet.send.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import lombok.Getter;
import org.bson.types.ObjectId;

@Entity(value = "battlepass", useDiscriminator = false)
public class BattlePassManager extends BasePlayerDataManager {
    @Id @Getter private ObjectId id;

    @Indexed private int ownerUid;
    @Getter private int point;
    @Getter private int cyclePoints; // Weekly maximum cap
    @Getter private int level;

    @Getter private boolean viewed;
    private boolean paid;
    @Getter private boolean extraPaidRewardTaken;
    private int scheduleId;
    private long lastMissionRefreshDay;
    private static final ZoneId RESET_ZONE = ZoneId.of("Asia/Shanghai");

    private Map<Integer, BattlePassMission> missions;
    private Map<Integer, BattlePassReward> takenRewards;

    @Deprecated // Morphia only
    public BattlePassManager() {}

    public BattlePassManager(Player player) {
        super(player);
        this.ownerUid = player.getUid();
    }

    public void setPlayer(Player player) {
        this.player = player;
        this.ownerUid = player.getUid();
    }

    public void updateViewed() {
        this.viewed = true;
    }

    /** Older saves have no schedule ID; keep their progress when first assigning it. */
    public synchronized void synchronizeSchedule() {
        int current = BattlePassScheduleData.currentId();
        if (scheduleId != 0 && scheduleId != current) {
            paid = false;
            extraPaidRewardTaken = false;
            viewed = false;
            level = 0;
            point = 0;
            cyclePoints = 0;
            getMissions().clear();
            getTakenRewards().clear();
            lastMissionRefreshDay = 0;
        }
        scheduleId = current;
    }

    public boolean setLevel(int level) {
        if (level >= 0 && level <= GameConstants.BATTLE_PASS_MAX_LEVEL) {
            this.level = level;
            this.point = 0;
            this.player.sendPacket(new PacketBattlePassCurScheduleUpdateNotify(this.player));
            return true;
        }
        return false;
    }

    public void addPoints(int points) {
        this.addPointsDirectly(points, false);

        this.player.sendPacket(new PacketBattlePassCurScheduleUpdateNotify(player));
        this.save();
    }

    public void addPointsDirectly(int points, boolean isWeekly) {
        int amount = points;

        if (isWeekly) {
            amount = Math.min(amount, GameConstants.BATTLE_PASS_POINT_PER_WEEK - this.cyclePoints);
        }

        if (amount <= 0) {
            return;
        }

        this.point += amount;
        if (isWeekly) this.cyclePoints += amount;

        if (this.point >= GameConstants.BATTLE_PASS_POINT_PER_LEVEL
                && this.getLevel() < GameConstants.BATTLE_PASS_MAX_LEVEL) {
            int levelups = Math.floorDiv(this.point, GameConstants.BATTLE_PASS_POINT_PER_LEVEL);

            // Make sure player cant go above max BP level
            levelups = Math.min(levelups, GameConstants.BATTLE_PASS_MAX_LEVEL - this.level);

            // Set new points after level up
            this.point = this.point - (levelups * GameConstants.BATTLE_PASS_POINT_PER_LEVEL);
            this.level += levelups;
        }
        if (this.level == GameConstants.BATTLE_PASS_MAX_LEVEL) this.point = 0;
    }

    public Map<Integer, BattlePassMission> getMissions() {
        if (this.missions == null) this.missions = new HashMap<>();
        return this.missions;
    }

    // Will return a new empty mission if the mission id is not found
    public BattlePassMission loadMissionById(int id) {
        return getMissions().computeIfAbsent(id, i -> new BattlePassMission(i));
    }

    public boolean hasMission(int id) {
        return getMissions().containsKey(id);
    }

    public boolean isPaid() {
        // Preserve earned premium access on saves produced by the old always-paid implementation.
        return paid || getTakenRewards().values().stream().anyMatch(BattlePassReward::isPaid);
    }

    public synchronized boolean unlockPaid(boolean extra) {
        if (extra ? extraPaidRewardTaken : isPaid()) return false;
        var schedule = GameData.getBattlePassScheduleDataMap().get(BattlePassScheduleData.currentId());
        if (extra
                && (schedule == null
                        || GameData.getRewardDataMap().get(schedule.getExtraPaidRewardId()) == null))
            throw new IllegalArgumentException("当前纪行的珍珠之歌奖励资源不完整。");
        if (extra) {
            var reward = GameData.getRewardDataMap().get(schedule.getExtraPaidRewardId());
            var items = new ArrayList<GameItem>();
            for (var item : reward.getRewardItemList()) {
                if (!GameData.getItemDataMap().containsKey(item.getItemId()))
                    throw new IllegalArgumentException("纪行奖励物品资源缺失：" + item.getItemId());
                items.add(new GameItem(item.getItemId(), item.getItemCount()));
            }
            getPlayer().getInventory().addItems(items, ActionReason.BattlePassPaidReward);
            addPointsDirectly(schedule.getExtraPaidAddPoint(), false);
            extraPaidRewardTaken = true;
        }
        this.paid = true;
        save();
        getPlayer().sendPacket(new PacketBattlePassCurScheduleUpdateNotify(getPlayer()));
        return true;
    }

    public Map<Integer, BattlePassReward> getTakenRewards() {
        if (this.takenRewards == null) this.takenRewards = new HashMap<>();
        return this.takenRewards;
    }

    // Mission triggers
    public void triggerMission(WatcherTriggerType triggerType) {
        getPlayer().getServer().getBattlePassSystem().triggerMission(getPlayer(), triggerType);
    }

    public void triggerMission(WatcherTriggerType triggerType, int param, int progress) {
        getPlayer()
                .getServer()
                .getBattlePassSystem()
                .triggerMission(getPlayer(), triggerType, param, progress);
    }

    public void triggerMission(String triggerName, int param, int progress) {
        getPlayer()
                .getServer()
                .getBattlePassSystem()
                .triggerMission(getPlayer(), triggerName, param, progress);
    }

    // Handlers
    public synchronized List<Integer> takeMissionPoint(List<Integer> missionIdList) {
        refreshMissions();
        // Obvious exploit check
        if (missionIdList.size() > GameData.getBattlePassMissionDataMap().size()) {
            return List.of();
        }

        List<BattlePassMission> updatedMissions = new ArrayList<>(missionIdList.size());

        for (int id : missionIdList) {
            // Skip if we dont have this mission
            if (!this.hasMission(id)) {
                continue;
            }

            BattlePassMission mission = this.loadMissionById(id);

            if (mission.getData() == null || !mission.getData().isValidRefreshType()) {
                this.getMissions().remove(mission.getId());
                continue;
            }

            // Take reward
            if (mission.getStatus() == BattlePassMissionStatus.MISSION_STATUS_FINISHED) {
                this.addPointsDirectly(mission.getData().getAddPoint(), mission.getData().isCycleRefresh());
                mission.setStatus(BattlePassMissionStatus.MISSION_STATUS_POINT_TAKEN);

                updatedMissions.add(mission);
            }
        }

        if (!updatedMissions.isEmpty()) {
            // Save to db
            this.save();

            // Packet
            getPlayer().sendPacket(new PacketBattlePassMissionUpdateNotify(updatedMissions));
            getPlayer().sendPacket(new PacketBattlePassCurScheduleUpdateNotify(getPlayer()));
        }
        return updatedMissions.stream().map(BattlePassMission::getId).toList();
    }

    private void resolveRewardItems(
            RewardData reward, int index, int multiplier, List<GameItem> items) {
        if (reward == null || reward.getRewardItemList() == null) {
            throw new IllegalArgumentException("纪行奖励资源缺失");
        }
        for (var entry : reward.getRewardItemList()) {
            var data = GameData.getItemDataMap().get(entry.getItemId());
            if (data == null) throw new IllegalArgumentException("纪行奖励物品缺失: " + entry.getItemId());
            int count = Math.multiplyExact(entry.getItemCount(), multiplier);
            if (data.getMaterialType() != MaterialType.MATERIAL_SELECTABLE_CHEST) {
                items.add(new GameItem(data, count));
                continue;
            }
            if (data.getItemUse() == null
                    || data.getItemUse().isEmpty()
                    || data.getItemUse().get(0).getUseParam().length == 0) {
                throw new IllegalArgumentException("纪行自选奖励资源缺失");
            }
            var use = data.getItemUse().get(0);
            var choices = use.getUseParam()[0].split(",");
            if (index < 1 || index > choices.length) throw new IllegalArgumentException("纪行自选奖励选项无效");
            int chosen = Integer.parseInt(choices[index - 1]);
            if (use.getUseOp() == ItemUseOp.ITEM_USE_ADD_SELECT_ITEM) {
                if (!GameData.getItemDataMap().containsKey(chosen))
                    throw new IllegalArgumentException("自选物品缺失");
                items.add(new GameItem(chosen, count));
            } else if (use.getUseOp() == ItemUseOp.ITEM_USE_GRANT_SELECT_REWARD) {
                var selected = GameData.getRewardDataMap().get(chosen);
                if (selected == null || selected.getRewardItemList() == null)
                    throw new IllegalArgumentException("自选奖励缺失");
                for (var choice : selected.getRewardItemList()) {
                    if (!GameData.getItemDataMap().containsKey(choice.getItemId()))
                        throw new IllegalArgumentException("自选物品缺失");
                    items.add(
                            new GameItem(choice.getItemId(), Math.multiplyExact(choice.getItemCount(), count)));
                }
            } else throw new IllegalArgumentException("纪行自选奖励类型无效");
        }
    }

    public synchronized List<BattlePassRewardTakeOption> takeReward(
            List<BattlePassRewardTakeOption> options) {
        synchronizeSchedule();
        var claimed = new ArrayList<BattlePassRewardTakeOption>();
        var items = new ArrayList<GameItem>();
        var schedule = GameData.getBattlePassScheduleDataMap().get(BattlePassScheduleData.currentId());
        int index =
                schedule != null && schedule.getLevelRewardIndexId() > 0
                        ? schedule.getLevelRewardIndexId()
                        : GameConstants.BATTLE_PASS_CURRENT_INDEX;
        for (var option : options) {
            var tag = option.getTag();
            int id = tag.getRewardId();
            if (id == 0
                    || getTakenRewards().containsKey(id)
                    || tag.getLevel() < 1
                    || tag.getLevel() > level) continue;
            var data = GameData.getBattlePassRewardDataMap().get(index * 100 + tag.getLevel());
            if (data == null) continue;
            boolean premium =
                    tag.getUnlockStatus()
                            == BattlePassUnlockStatus.BattlePassUnlockSTATUS_BATTLE_PASS_UNLOCK_PAID;
            boolean free =
                    tag.getUnlockStatus()
                            == BattlePassUnlockStatus.BattlePassUnlockSTATUS_BATTLE_PASS_UNLOCK_FREE;
            if (!(free && data.getFreeRewardIdList().contains(id))
                    && !(premium && isPaid() && data.getPaidRewardIdList().contains(id))) continue;
            var resolved = new ArrayList<GameItem>();
            try {
                resolveRewardItems(GameData.getRewardDataMap().get(id), option.getOptionIdx(), 1, resolved);
            } catch (IllegalArgumentException | ArithmeticException e) {
                Grasscutter.getLogger().warn("Battle pass reward {} rejected: {}", id, e.getMessage());
                continue;
            }
            getPlayer().getInventory().addItems(resolved, ActionReason.BattlePassLevelReward);
            getTakenRewards().put(id, new BattlePassReward(tag.getLevel(), id, premium));
            claimed.add(option);
            items.addAll(resolved);
        }
        if (!claimed.isEmpty()) {
            save();
            getPlayer().sendPacket(new PacketBattlePassCurScheduleUpdateNotify(getPlayer()));
        }
        getPlayer().sendPacket(new PacketTakeBattlePassRewardRsp(claimed, items));
        return List.copyOf(claimed);
    }

    public int buyLevels(int buyLevel) {
        int boughtLevels = Math.min(buyLevel, GameConstants.BATTLE_PASS_MAX_LEVEL - this.level);

        if (boughtLevels > 0) {
            int price = GameConstants.BATTLE_PASS_LEVEL_PRICE * boughtLevels;

            if (getPlayer().getPrimogems() < price) {
                return 0;
            }

            getPlayer().setPrimogems(getPlayer().getPrimogems() - price);
            this.level += boughtLevels;
            this.save();

            getPlayer().sendPacket(new PacketBattlePassCurScheduleUpdateNotify(getPlayer()));
        }

        return boughtLevels;
    }

    protected Instant missionNow() {
        return Instant.now();
    }

    private LocalDate missionDay() {
        return missionNow().atZone(RESET_ZONE).minusHours(4).toLocalDate();
    }

    private static LocalDate weekStart(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** First assignment preserves legacy progress; subsequent refreshes also catch missed Mondays. */
    public synchronized boolean refreshMissions() {
        synchronizeSchedule();
        var day = missionDay();
        if (lastMissionRefreshDay >= day.toEpochDay()) return false;
        boolean dailyReset = lastMissionRefreshDay != 0;
        boolean weeklyReset =
                dailyReset
                        && weekStart(day).isAfter(weekStart(LocalDate.ofEpochDay(lastMissionRefreshDay)));
        lastMissionRefreshDay = day.toEpochDay();
        getMissions().values().removeIf(m -> m.getData() == null || !m.getData().isValidRefreshType());
        if (dailyReset) resetDailyMissions();
        if (weeklyReset) resetWeeklyMissions();
        save();
        return dailyReset;
    }

    private void resetMissions(boolean daily) {
        var changed = new ArrayList<BattlePassMission>();
        for (var mission : getMissions().values()) {
            var data = mission.getData();
            if (data == null) continue;
            var type = data.getRefreshType();
            boolean matches =
                    daily
                            ? type == null
                                    || type == BattlePassMissionRefreshType.BATTLE_PASS_MISSION_REFRESH_DAILY
                            : type
                                            == BattlePassMissionRefreshType
                                                    .BATTLE_PASS_MISSION_REFRESH_CYCLE_CROSS_SCHEDULE
                                    || type == BattlePassMissionRefreshType.BATTLE_PASS_MISSION_REFRESH_CYCLE;
            if (!matches) continue;
            mission.setProgress(0);
            mission.setStatus(BattlePassMissionStatus.MISSION_STATUS_UNFINISHED);
            changed.add(mission);
        }
        if (!daily) cyclePoints = 0;
        save();
        getPlayer().sendPacket(new PacketBattlePassMissionUpdateNotify(changed));
        getPlayer().sendPacket(new PacketBattlePassCurScheduleUpdateNotify(getPlayer()));
    }

    public synchronized void resetDailyMissions() {
        resetMissions(true);
    }

    public synchronized void resetWeeklyMissions() {
        resetMissions(false);
    }

    //
    public BattlePassSchedule getScheduleProto() {
        var monday = weekStart(missionDay());
        int begin = (int) monday.atTime(4, 0).atZone(RESET_ZONE).toEpochSecond();
        int end = (int) monday.plusWeeks(1).atTime(4, 0).atZone(RESET_ZONE).toEpochSecond();

        BattlePassSchedule.Builder schedule =
                BattlePassSchedule.newBuilder()
                        .setScheduleId(BattlePassScheduleData.currentId())
                        .setLevel(this.getLevel())
                        .setPoint(this.getPoint())
                        .setCurCyclePoints(this.cyclePoints)
                        .setIsViewed(this.viewed)
                        .setIsExtraPaidRewardTaken(this.extraPaidRewardTaken)
                        .setBeginTime(0)
                        .setEndTime(2059483200)
                        .setUnlockStatus(
                                this.isPaid()
                                        ? BattlePassUnlockStatus.BattlePassUnlockSTATUS_BATTLE_PASS_UNLOCK_PAID
                                        : BattlePassUnlockStatus.BattlePassUnlockSTATUS_BATTLE_PASS_UNLOCK_FREE)
                        .setCurCycle(
                                BattlePassCycle.newBuilder().setBeginTime(begin).setEndTime(end).setCycleIdx(3));

        for (BattlePassReward reward : getTakenRewards().values()) {
            schedule.addRewardTakenList(reward.toProto());
        }

        return schedule.build();
    }

    public void save() {
        DatabaseHelper.saveBattlePass(this);
    }
}
