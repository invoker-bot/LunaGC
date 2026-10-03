package emu.grasscutter.game.beyond;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.net.proto.*;
import java.util.List;
import org.junit.jupiter.api.*;

class BeyondClosetTest {
    private final Gson gson = new Gson();

    @BeforeAll
    static void initialize() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
    }

    @Test
    void permanentPurchaseNotificationUsesTheNativeOwnershipList() throws Exception {
        // AddCosmetic field 4; nested costume ID field 13. Login list is field 7.
        var add =
                BeyondAddCosmeticNotifyOuterClass.BeyondAddCosmeticNotify.parseFrom(
                        new byte[] {0x22, 2, 0x68, 9});
        assertEquals(9, add.getOwnedCostumeList(0).getCostumeId());
        assertEquals(0, add.getOwnedCostumeList(0).getExpireTime());
        var login =
                BeyondCosmeticDataNotifyOuterClass.BeyondCosmeticDataNotify.parseFrom(
                        new byte[] {0x3a, 2, 0x68, 9});
        assertEquals(9, login.getOwnedCostumeList(0).getCostumeId());
    }

    @Test
    void purchasePersistsAndDoesNotUnlockOrChargeTwice() {
        var closet = new BeyondCloset();
        assertTrue(closet.canGrant(List.of(264269)));
        assertEquals(264269, closet.grant(List.of(264269)).get(0).getCostumeId());
        var restored = gson.fromJson(gson.toJson(closet), BeyondCloset.class);
        assertFalse(restored.canGrant(List.of(264269)));
        assertTrue(restored.grant(List.of(264269)).isEmpty());
        assertEquals(List.of(264269), restored.toProto().stream().map(p -> p.getCostumeId()).toList());
        assertEquals(
                List.of(500101),
                restored.grant(List.of(264269, 500101)).stream().map(p -> p.getCostumeId()).toList());
    }

    @Test
    void resourceUseActionsExpandOnlyTheRequestedSuit() {
        int material = 987654;
        var old = GameData.getBydMaterialDataMap().get(material);
        var first = GameData.getBeyondCostumeDataMap().get(987650);
        var second = GameData.getBeyondCostumeDataMap().get(987651);
        var other = GameData.getBeyondCostumeDataMap().get(987652);
        try {
            GameData.getBydMaterialDataMap()
                    .put(
                            material,
                            gson.fromJson(
                                    "{\"id\":987654,\"itemUse\":[{\"useOp\":\"BYD_MATERIAL_USE_GAIN_COSTUME_SUIT\",\"useParam\":[\"987653\",\"\"]}]}",
                                    BydMaterialData.class));
            GameData.getBeyondCostumeDataMap()
                    .put(
                            987650,
                            gson.fromJson("{\"costumeId\":987650,\"suitId\":987653}", BeyondCostumeData.class));
            GameData.getBeyondCostumeDataMap()
                    .put(
                            987651,
                            gson.fromJson("{\"costumeId\":987651,\"suitId\":987653}", BeyondCostumeData.class));
            GameData.getBeyondCostumeDataMap()
                    .put(
                            987652,
                            gson.fromJson("{\"costumeId\":987652,\"suitId\":123}", BeyondCostumeData.class));
            assertEquals(List.of(987650, 987651), BeyondCloset.resolveCostumes(material));
            assertTrue(BeyondCloset.resolveCostumes(123).isEmpty());
        } finally {
            if (old == null) GameData.getBydMaterialDataMap().remove(material);
            else GameData.getBydMaterialDataMap().put(material, old);
            if (first == null) GameData.getBeyondCostumeDataMap().remove(987650);
            else GameData.getBeyondCostumeDataMap().put(987650, first);
            if (second == null) GameData.getBeyondCostumeDataMap().remove(987651);
            else GameData.getBeyondCostumeDataMap().put(987651, second);
            if (other == null) GameData.getBeyondCostumeDataMap().remove(987652);
            else GameData.getBeyondCostumeDataMap().put(987652, other);
        }
    }
}
