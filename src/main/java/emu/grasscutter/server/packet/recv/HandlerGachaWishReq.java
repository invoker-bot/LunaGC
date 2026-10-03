package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.gacha.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GachaWishReqOuterClass.GachaWishReq;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketGachaWishRsp;

@Opcodes(PacketOpcodes.GachaWishReq)
public class HandlerGachaWishReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        GachaWishReq req = GachaWishReq.parseFrom(payload);

        GachaBanner banner =
                session.getServer().getGachaSystem().getGachaBanners().get(req.getGachaScheduleId());
        session.send(
                applyWish(session.getPlayer(), banner, req, System.currentTimeMillis() / 1000L));
    }

    static PacketGachaWishRsp applyWish(
            Player player, GachaBanner banner, GachaWishReq req, long now) {
        if (banner == null || !banner.hasEpitomized() || !banner.isActive(now)
                || banner.getScheduleId() != req.getGachaScheduleId()
                || banner.getGachaType() != req.getGachaType()) {
            return new PacketGachaWishRsp(Retcode.RET_GACHA_SCHEDULE_NOT_MATCH);
        }
        // Zero cancels the path. Every nonzero choice must belong to this banner's featured pool.
        if (req.getItemId() != 0 && !banner.isWishItemAllowed(req.getItemId())) {
            return new PacketGachaWishRsp(Retcode.RET_GACHA_WISH_INVALID_ITEM);
        }
        var wish = player.getGachaInfo().getWishInfo(banner);
        wish.selectItem(req.getItemId());
        player.save();
        return new PacketGachaWishRsp(
                banner.getGachaType(), banner.getScheduleId(), wish.getWishItemId(),
                wish.getFatePoints(), banner.getWishMaxProgress());
    }
}
