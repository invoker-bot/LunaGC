package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.activity.salesman.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.SalesmanDeliverItemReqOuterClass.SalesmanDeliverItemReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.*;

@Opcodes(PacketOpcodes.SalesmanDeliverItemReq)
public final class HandlerSalesmanDeliverItemReq extends PacketHandler {
    @Override public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var request = SalesmanDeliverItemReq.parseFrom(payload);
        var player = session.getPlayer();
        var manager = player.getActivityManager();
        int result = Retcode.RET_ACTIVITY_CLOSE_VALUE;
        // refreshActivities shares this lock, so an old delivery cannot overwrite a new replay's progress.
        synchronized (manager) {
            var handler = manager.getActivityHandlerAs(ActivityType.NEW_ACTIVITY_SALESMAN, SalesmanActivityHandler.class).orElse(null);
            if (handler != null && handler.getActivityConfigItem().getScheduleId() == request.getScheduleId()) {
                var data = manager.getPlayerActivityDataMap().get(SalesmanSchedule.ACTIVITY_ID);
                if (data != null) try {
                    result = SalesmanDelivery.deliver(data, handler.getActivityConfigItem(), player.getLevel(),
                            System.currentTimeMillis(), costs -> player.getInventory().payItems(costs, 1, ActionReason.SalesmanDeliverItem), data::save);
                } catch (RuntimeException failed) {
                    Grasscutter.getLogger().error("Salesman delivery failed for UID {} schedule {}; check pending activity progress before retrying",
                            player.getUid(), request.getScheduleId(), failed);
                    result = Retcode.RET_SVR_ERROR_VALUE;
                }
            }
            session.send(new PacketSalesmanDeliverItemRsp(request.getScheduleId(), result));
            if (result == 0) {
                player.sendPacket(new PacketActivityInfoNotify(manager.getInfoProtoByActivityId(SalesmanSchedule.ACTIVITY_ID)));
                manager.triggerActivityConditions();
            }
        }
    }
}
