package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.UseWidgetCreateGadgetReqOuterClass.UseWidgetCreateGadgetReq;
import emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketUseWidgetCreateGadgetRsp;
import java.util.*;

@Opcodes(PacketOpcodes.UseWidgetCreateGadgetReq)
public class HandlerUseWidgetCreateGadgetReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        UseWidgetCreateGadgetReq req = UseWidgetCreateGadgetReq.parseFrom(payload);
        var player = session.getPlayer();

        int materialId = req.getMaterialId();
        var scene = player.getScene();

        // The widget only exists as a gadget if the item actually maps to one.
        ItemData itemData = GameData.getItemDataMap().get(materialId);
        if (itemData == null || itemData.getGadgetId() == 0 || scene == null) {
            // TODO 7.0 has no CmdId for UseWidgetCreateGadgetRsp in the proto dump yet, so the
            // reply cannot be sent; the gadget simply does not appear. Add the opcode here and at
            // PacketOpcodes.UseWidgetCreateGadgetRsp once the dump names it.
            Grasscutter.getLogger()
                    .debug("UseWidgetCreateGadgetReq rejected: material {} is not a gadget", materialId);
            return;
        }

        // Only one widget gadget is out at a time; placing a new one recalls the previous one, the
        // same way the official client behaves for compasses and similar gadgets.
        var gadgets = player.getTeamManager().getGadgets();
        if (!gadgets.isEmpty()) {
            for (var old : new ArrayList<>(gadgets)) {
                scene.removeEntity(old, VisionType.VisionType_VISION_REMOVE);
            }
            gadgets.clear();
        }

        var pos = new Position(req.getPos());
        var rot = req.hasRot() ? new Position(req.getRot()) : new Position();
        var gadget = new EntityGadget(scene, itemData.getGadgetId(), pos, rot);
        // EntityGadget.owner is a GameEntity: the avatar is what the client sees as the gadget's
        // master, and gadgetInfo.setOwnerEntityId writes its id into the scene broadcast.
        gadget.setOwner(player.getTeamManager().getCurrentAvatarEntity());
        gadgets.add(gadget);
        scene.addEntity(gadget);

        // Scene.onPlayerLeave removes the gadget from the scene again, so it does not survive into
        // the next scene on its own.
        // TODO reply with PacketUseWidgetCreateGadgetRsp once 7.0 names its CmdId (see above). The
        // gadget is already in the scene, so without the ack the client shows it but may spin briefly.
        Grasscutter.getLogger()
                .debug("UseWidgetCreateGadgetReq placed gadget {} for {}", itemData.getGadgetId(), player.getUid());
    }
}
