package emu.grasscutter.game.battlepass;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.BattlePassMissionData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.*;
import emu.grasscutter.server.event.player.PlayerFinishBattlePassMission;
import emu.grasscutter.server.game.*;
import emu.grasscutter.server.packet.send.PacketBattlePassMissionUpdateNotify;
import java.util.*;

public class BattlePassSystem extends BaseGameSystem {
    private volatile Map<String, List<BattlePassMissionData>> cachedTriggers = Map.of();

    // BP Mission manager for the server, contains cached triggers so we dont have to load it for each
    // player
    public BattlePassSystem(GameServer server) {
        super(server);

        reload();
    }

    public void reload() {
        var triggers = new HashMap<String, List<BattlePassMissionData>>();

        for (BattlePassMissionData missionData : GameData.getBattlePassMissionDataMap().values()) {
            if (missionData.isValidRefreshType()) {
                List<BattlePassMissionData> triggerList =
                        triggers.computeIfAbsent(missionData.getTriggerName(), e -> new ArrayList<>());
                triggerList.add(missionData);
            }
        }
        triggers.replaceAll((k, v) -> List.copyOf(v));
        cachedTriggers = Map.copyOf(triggers);
    }

    public GameServer getServer() {
        return server;
    }

    private Map<String, List<BattlePassMissionData>> getTriggers() {
        return cachedTriggers;
    }

    public void triggerMission(Player player, WatcherTriggerType triggerType) {
        triggerMission(player, triggerType, 0, 1);
    }

    public void triggerMission(
            Player player, WatcherTriggerType triggerType, int param, int progress) {
        triggerMission(player, triggerType.name(), param, progress);
    }

    public void triggerMission(Player player, String triggerType, int param, int progress) {
        if (progress <= 0) return;
        var pass = player.getBattlePassManager();
        synchronized (pass) {
            pass.refreshMissions();
            List<BattlePassMissionData> triggerList = getTriggers().get(triggerType);

            if (triggerList == null || triggerList.isEmpty()) return;

            for (BattlePassMissionData data : triggerList) {
                // Skip params check if param == 0
                if (!data.getMainParams().isEmpty()) {
                    if (!data.getMainParams().contains(param)) {
                        continue;
                    }
                }

                // Get mission from player, if it doesnt exist, then we make one
                BattlePassMission mission = player.getBattlePassManager().loadMissionById(data.getId());

                if (mission.isFinshed()) continue;

                // Add progress
                mission.addProgress(progress, data.getProgress());

                if (mission.getProgress() >= data.getProgress()) {
                    mission.setStatus(BattlePassMissionStatus.MISSION_STATUS_FINISHED);

                    new PlayerFinishBattlePassMission(player, mission).call();
                }

                // Save to db
                player.getBattlePassManager().save();

                // Packet
                player.sendPacket(new PacketBattlePassMissionUpdateNotify(mission));
            }
        }
    }
}
