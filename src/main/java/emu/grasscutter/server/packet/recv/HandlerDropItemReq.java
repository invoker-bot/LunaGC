package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.DropItemReqOuterClass.DropItemReq;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketDropItemRsp;

@Opcodes(PacketOpcodes.DropItemReq)
public class HandlerDropItemReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        DropItemReq req = DropItemReq.parseFrom(payload);
        var player = session.getPlayer();

        long guid = req.getGuid();
        int count = req.getCount();
        int storeTypeValue = req.getStoreTypeValue();

        // The client never sends a stack of size 0, but removeItem() would treat a non-positive
        // count as a no-op and still report success, so reject it here instead.
        if (count <= 0) {
            player.sendPacket(
                    new PacketDropItemRsp(
                            Retcode.RET_ITEM_INVALID_DROP_COUNT_VALUE, guid, storeTypeValue));
            return;
        }

        GameItem item = player.getInventory().getItemByGuid(guid);
        if (item == null || item.getItemData() == null) {
            player.sendPacket(
                    new PacketDropItemRsp(Retcode.RET_ITEM_NOT_EXIST_VALUE, guid, storeTypeValue));
            return;
        }

        // Equipped gear cannot be dropped; the avatar would be left with an empty slot client-side.
        if (item.isEquipped()) {
            player.sendPacket(
                    new PacketDropItemRsp(
                            Retcode.RET_EQUIP_WEARED_CANNOT_DROP_VALUE, guid, storeTypeValue));
            return;
        }

        // Virtual items (Mora, primogems, resin, ...) have no gadget form, so dropping them into
        // the scene would only delete them. The official client blocks these in the UI.
        if (item.getItemType() == ItemType.ITEM_VIRTUAL || item.getItemData().getGadgetId() == 0) {
            player.sendPacket(
                    new PacketDropItemRsp(Retcode.RET_ITEM_NOT_DROPABLE_VALUE, guid, storeTypeValue));
            return;
        }

        // removeItem() underflows the stack instead of failing when asked for more than it holds.
        if (count > item.getCount()) {
            player.sendPacket(
                    new PacketDropItemRsp(
                            Retcode.RET_ITEM_COUNT_NOT_ENOUGH_VALUE, guid, storeTypeValue));
            return;
        }

        int dropCount = item.getCount() <= count ? item.getCount() : count;

        // Take the item out of the inventory first; the entity then carries what is actually
        // removed, so a failed removal never conjures items into the scene.
        if (!player.getInventory().removeItem(guid, count)) {
            player.sendPacket(
                    new PacketDropItemRsp(Retcode.RET_ITEM_NOT_EXIST_VALUE, guid, storeTypeValue));
            return;
        }

        // Spawn the dropped stack as a pickable gadget next to the player's avatar. share=true so
        // co-op partners see and can pick up the same drop, matching the official behaviour.
        var scene = player.getScene();
        var avatar = player.getTeamManager().getCurrentAvatarEntity();
        if (scene != null && avatar != null) {
            scene.addDropEntity(new GameItem(item.getItemData(), dropCount), avatar, player, true);
        }

        player.sendPacket(new PacketDropItemRsp(guid, storeTypeValue));
    }
}
