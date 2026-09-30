package emu.grasscutter.game.activity.crucible;

import emu.grasscutter.data.GameData;
import emu.grasscutter.game.activity.*;
import emu.grasscutter.game.props.ActivityType;
import emu.grasscutter.net.proto.ActivityInfoOuterClass.ActivityInfo;
import emu.grasscutter.net.proto.CrucibleActivityDetailInfoOuterClass.CrucibleActivityDetailInfo;

@GameActivity(ActivityType.NEW_ACTIVITY_CRUCIBLE)
public final class CrucibleActivityHandler extends ActivityHandler {
    @Override public void onInitPlayerActivityData(PlayerActivityData data) {}

    @Override public void onProtoBuild(PlayerActivityData data, ActivityInfo.Builder info) {
        var play = GameData.getMpPlayGroupDataMap().get(1);
        if (play == null) throw new IllegalStateException("原素烘炉 MP play #1 资源缺失");
        var detail = CrucibleActivityDetailInfo.newBuilder().setPos(play.centerPosition().toProto());
        if (data != null && data.getPlayer() != null) detail.setBattleWorldLevel(data.getPlayer().getWorldLevel());
        info.setCrucibleInfo(detail);
    }
}
