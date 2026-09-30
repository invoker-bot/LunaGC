package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.activity.salesman.*;
import emu.grasscutter.game.props.ActivityType;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.SalesmanTakeRewardReqOuterClass.SalesmanTakeRewardReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.*;
import java.util.concurrent.ThreadLocalRandom;

@Opcodes(PacketOpcodes.SalesmanTakeRewardReq)
public final class HandlerSalesmanTakeRewardReq extends PacketHandler {
    @Override public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var request = SalesmanTakeRewardReq.parseFrom(payload);
        var player = session.getPlayer(); var manager = player.getActivityManager();
        var result = new SalesmanRewards.Result(Retcode.RET_ACTIVITY_CLOSE_VALUE, 0);
        synchronized (manager) {
            var handler = manager.getActivityHandlerAs(ActivityType.NEW_ACTIVITY_SALESMAN, SalesmanActivityHandler.class).orElse(null);
            if (handler != null && handler.getActivityConfigItem().getScheduleId() == request.getScheduleId()) {
                var data = manager.getPlayerActivityDataMap().get(SalesmanSchedule.ACTIVITY_ID);
                if (data != null) try {
                    long now = System.currentTimeMillis(); int day = SalesmanSchedule.dayIndex(handler.getActivityConfigItem(), now);
                    if (day < 1) result = new SalesmanRewards.Result(Retcode.RET_ACTIVITY_CLOSE_VALUE, 0);
                    else if (player.getLevel() < 12) result = new SalesmanRewards.Result(Retcode.RET_PLAYER_LEVEL_LESS_THAN_VALUE, 0);
                    else {
                        var scene = player.getScene();
                        if (scene != null) scene.getSalesmanSceneController().updatePlayer(player, now);
                        if (scene == null || !scene.getSalesmanSceneController().canInteract(player, now)
                                || !SalesmanSchedule.progress(data).hasTalked(Math.min(day, 7)))
                            result = new SalesmanRewards.Result(Retcode.RET_NOT_CURRENT_TALK_VALUE, 0);
                        else synchronized (player.getInventory()) {
                            result = SalesmanRewards.take(data, handler.getActivityConfigItem(), player.getLevel(), now,
                                    request.getPosition(), () -> ThreadLocalRandom.current().nextDouble(),
                                    bound -> ThreadLocalRandom.current().nextInt(bound), reward -> SalesmanRewardDelivery.validate(player, reward),
                                    reward -> SalesmanRewardDelivery.grant(player, reward), data::saveSync);
                        }
                    }
                } catch (RuntimeException failed) {
                    Grasscutter.getLogger().error("Salesman reward failed for UID {} schedule {} position {}; reconcile pending progress and inventory before retrying",
                            player.getUid(), request.getScheduleId(), request.getPosition(), failed);
                    result = new SalesmanRewards.Result(Retcode.RET_SVR_ERROR_VALUE, 0);
                }
            }
            session.send(new PacketSalesmanTakeRewardRsp(request.getScheduleId(), request.getPosition(), result.rewardId(), result.retcode()));
            if (result.retcode() == 0) {
                player.sendPacket(new PacketActivityInfoNotify(manager.getInfoProtoByActivityId(SalesmanSchedule.ACTIVITY_ID)));
                manager.triggerActivityConditions();
            }
        }
    }
}
