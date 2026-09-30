package emu.grasscutter.game.entity.gadget;

import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq;
import emu.grasscutter.net.proto.SceneGadgetInfoOuterClass.SceneGadgetInfo;

public final class GadgetMpPlayReward extends GadgetContent {
    public GadgetMpPlayReward(EntityGadget gadget) { super(gadget); }
    @Override public boolean onInteract(Player player, GadgetInteractReq request) {
        getGadget().getScene().getCrucibleSceneController().claimReward(player, getGadget(), request);
        // One player's claim must not remove the other participants' reward point.
        return false;
    }
    @Override public void onBuildProto(SceneGadgetInfo.Builder info) {
        info.setMpPlayReward(getGadget().getScene().getCrucibleSceneController().rewardInfo(getGadget()));
    }
}
