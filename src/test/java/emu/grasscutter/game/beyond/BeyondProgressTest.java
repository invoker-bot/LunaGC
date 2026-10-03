package emu.grasscutter.game.beyond;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.game.inventory.Inventory;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.shop.BeyondCurrency;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class BeyondProgressTest {
    @org.junit.jupiter.api.BeforeAll
    static void initialize() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
    }

    private final Gson gson = new Gson();

    private BeyondHandbookData group(int id, String logic) {
        return gson.fromJson(
                "{\"KGEHGPFLIBH\":"
                        + id
                        + ",\"ILBBEFKCLGD\":\""
                        + logic
                        + "\",\"watcherIdList\":[120000,120001]}",
                BeyondHandbookData.class);
    }

    private Map<Integer, BeyondHandbookWatcherData> watchers() {
        return Map.of(
                120000,
                gson.fromJson("{\"id\":120000,\"progress\":3}", BeyondHandbookWatcherData.class),
                120001,
                gson.fromJson("{\"id\":120001,\"progress\":1}", BeyondHandbookWatcherData.class));
    }

    @Test
    void handbookContainsUnfinishedTasksForNewPlayers() {
        var info =
                new BeyondProgress().toProto(List.of(group(1, "LOGIC_OR")), watchers()).getInfoList(0);
        assertEquals(1, info.getGroupId());
        assertEquals(0, info.getRewardState());
        assertEquals(2, info.getWatcherProgressListCount());
        assertEquals(0, info.getFinishedWatcherListCount());
    }

    @Test
    void savedProgressRespectsGroupLogicCapsAndClaimedRewards() {
        var state =
                gson.fromJson(
                        "{\"watcherProgress\":{\"120000\":8},\"claimedGroups\":[3]}", BeyondProgress.class);
        var restored = gson.fromJson(gson.toJson(state), BeyondProgress.class);
        var proto =
                restored.toProto(
                        List.of(group(3, "LOGIC_OR"), group(2, "LOGIC_AND"), group(1, "LOGIC_OR")), watchers());
        assertEquals(1, proto.getInfoList(0).getRewardState());
        assertEquals(3, proto.getInfoList(0).getWatcherProgressList(0).getProgress());
        assertEquals(0, proto.getInfoList(1).getRewardState());
        assertEquals(2, proto.getInfoList(2).getRewardState());
        assertEquals(
                0,
                restored.toProto(List.of(group(1, "LOGIC_OR")), Map.of()).getInfoList(0).getRewardState());
    }

    @Test
    void handbookUsesNativeNestedTagsAndOpcode() throws Exception {
        // Notify field 7; group 1, reward state 2, progress 3 (ID 1, amount 2), completed IDs 4.
        var proto =
                WorldWatcherAllDataNotifyOuterClass.WorldWatcherAllDataNotify.parseFrom(
                        new byte[] {0x3a, 12, 0x08, 1, 0x10, 1, 0x1a, 4, 0x08, 7, 0x10, 2, 0x20, 7});
        assertEquals(22528, PacketOpcodes.WorldWatcherAllDataNotify);
        assertEquals(7, proto.getInfoList(0).getWatcherProgressList(0).getWatcherId());
        assertEquals(2, proto.getInfoList(0).getWatcherProgressList(0).getProgress());
        assertEquals(List.of(7), proto.getInfoList(0).getFinishedWatcherListList());
    }

    @Test
    void currencyPaymentDoesNotUseTeyvatBalanceOrOverdraw() {
        var player =
                new Player() {
                    @Override
                    public void save() {}

                    @Override
                    public void sendPacket(emu.grasscutter.net.packet.BasePacket packet) {}
                };
        var inventory = new Inventory(player);
        player.setPrimogems(1000);
        player.setProperty(BeyondCurrency.propertyForItem(231), 100);
        assertTrue(inventory.payItem(231, 40));
        assertEquals(60, player.getProperty(BeyondCurrency.propertyForItem(231)));
        assertFalse(inventory.payItem(231, 61));
        assertEquals(60, player.getProperty(BeyondCurrency.propertyForItem(231)));
        assertEquals(1000, player.getPrimogems());
    }
}
