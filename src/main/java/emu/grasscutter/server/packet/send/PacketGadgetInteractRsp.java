package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.entity.EntityBaseGadget;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GadgetInteractRspOuterClass.GadgetInteractRsp;
import emu.grasscutter.net.proto.InterOpTypeOuterClass.InterOpType;
import emu.grasscutter.net.proto.InteractTypeOuterClass.InteractType;
import emu.grasscutter.net.proto.RetcodeOuterClass;

public class PacketGadgetInteractRsp extends BasePacket {
    public PacketGadgetInteractRsp(EntityBaseGadget gadget, InteractType interact) {
        this(gadget, interact, null);
    }

    public PacketGadgetInteractRsp(
            EntityBaseGadget gadget, InteractType interact, InterOpType opType) {
        this(gadget, interact, opType, 0);
    }

    public PacketGadgetInteractRsp(EntityBaseGadget gadget, InteractType interact, InterOpType opType, int retcode) {
        this(gadget.getId(), gadget.getGadgetId(), interact, opType, retcode);
    }

    public PacketGadgetInteractRsp(int entityId, int gadgetId, InteractType interact, InterOpType opType, int retcode) {
        super(PacketOpcodes.GadgetInteractRsp);

        var proto =
                GadgetInteractRsp.newBuilder()
                        .setGadgetEntityId(entityId)
                        .setInteractType(interact)
                        .setGadgetId(gadgetId)
                        .setRetcode(retcode);

        if (opType != null) {
            proto.setOpType(opType);
        }

        this.setData(proto.build());
    }

    public PacketGadgetInteractRsp() {
        super(PacketOpcodes.GadgetInteractRsp);

        GadgetInteractRsp proto =
                GadgetInteractRsp.newBuilder()
                        .setRetcode(RetcodeOuterClass.Retcode.RET_SVR_ERROR_VALUE)
                        .build();

        this.setData(proto);
    }
}
