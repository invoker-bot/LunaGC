package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetShopRspNewOuterClass.GetShopRspNew;
import java.util.*;

public class PacketGetShopRspNew extends BasePacket {

    public PacketGetShopRspNew(int param) {
        super(PacketOpcodes.GetShopRspNew);

        GetShopRspNew.Builder rsp = GetShopRspNew.newBuilder()
                .setParam(param)
                .setRetcode(0);

        rsp.addAllDGINCLDAKFI(new ArrayList<>());

        this.setData(rsp.build());
    }
    
    /**
     * Alt builder
     * @param param Shop type being queried
     * @param availableShops Custom list of available shop id
     */
    public PacketGetShopRspNew(int param, List<Integer> availableShops) {
        super(PacketOpcodes.GetShopRspNew);

        GetShopRspNew.Builder rsp = GetShopRspNew.newBuilder()
                .setParam(param)
                .setRetcode(0);

        rsp.addAllDGINCLDAKFI(availableShops);

        this.setData(rsp.build());
    }
}