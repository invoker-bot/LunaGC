package emu.grasscutter.server.packet.send;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.CookRecipeData;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PlayerCookArgsRspOuterClass.PlayerCookArgsRsp;

public class PacketPlayerCookArgsRsp extends BasePacket {

    public PacketPlayerCookArgsRsp() {
        super(PacketOpcodes.PlayerCookArgsRsp);

        PlayerCookArgsRsp proto = PlayerCookArgsRsp.newBuilder().build();

        this.setData(proto);
    }

    /**
     * Answers a {@code PlayerCookArgsReq} for one recipe.
     *
     * <p>The client sizes its cooking QTE target zone from {@code qte_range_ratio}. The recipe's
     * {@code qteParam} is stored as "ratio,offset" in the excel, so the first component is what
     * the client would otherwise have to fall back to guessing.
     *
     * @param recipeId the recipe the player opened, from the request
     */
    public PacketPlayerCookArgsRsp(int recipeId) {
        super(PacketOpcodes.PlayerCookArgsRsp);

        PlayerCookArgsRsp.Builder proto = PlayerCookArgsRsp.newBuilder();

        var recipeData = GameData.getCookRecipeDataMap().get(recipeId);
        if (recipeData != null) {
            float ratio = parseQteRangeRatio(recipeData);
            if (ratio > 0f) {
                proto.setQteRangeRatio(ratio);
            }
        }

        this.setData(proto.build());
    }

    private static float parseQteRangeRatio(CookRecipeData recipeData) {
        // qteParam looks like "0.63,0.4" -- (range ratio, offset). Anything unparseable leaves
        // the field at its default and the client keeps whatever zone it computed locally.
        String param = recipeData.getQteParam();
        if (param == null || param.isBlank()) {
            return 0f;
        }
        try {
            return Float.parseFloat(param.split(",")[0].trim());
        } catch (NumberFormatException ignored) {
            return 0f;
        }
    }
}
