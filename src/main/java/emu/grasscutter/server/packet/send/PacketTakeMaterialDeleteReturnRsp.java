package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;

// No generated proto exists for TakeMaterialDeleteReturnRsp in the 7.0.0 dump the server was built
// from -- the opcode is present in PacketOpcodes, but the .proto never shipped. An empty Rsp is
// still legal (BasePacket#build emits a zero-length body) and it is enough to take the opcode off
// the unhandled list: the client gets an answer instead of timing its request out.
public class PacketTakeMaterialDeleteReturnRsp extends BasePacket {

    public PacketTakeMaterialDeleteReturnRsp() {
        super(PacketOpcodes.TakeMaterialDeleteReturnRsp);
    }
}
