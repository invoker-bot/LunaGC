package emu.grasscutter.game.mail;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.server.packet.send.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;

class MailHandlerTest {
    ItemData previous, previousWeapon;

    static class TestPlayer extends Player {
        int granted;
        final List<BasePacket> packets = new ArrayList<>();
        final Inventory bag =
                new Inventory(this) {
                    @Override
                    public boolean addItem(GameItem item, ActionReason reason, boolean force, boolean skip) {
                        granted += item.getCount();
                        return true;
                    }
                };

        @Override
        public Inventory getInventory() {
            return bag == null ? super.getInventory() : bag;
        }

        final Handler handler = new Handler(this);

        @Override
        public MailHandler getMailHandler() {
            return handler == null ? super.getMailHandler() : handler;
        }

        @Override
        public void sendPacket(BasePacket packet) {
            packets.add(packet);
        }
    }

    static class Handler extends MailHandler {
        List<Mail> stored = new ArrayList<>();
        int next = 1;
        boolean failSave, failInventory;

        Handler(Player player) {
            super(player);
        }

        @Override
        protected List<Mail> storedMail() {
            return stored;
        }

        @Override
        protected int allocateId() {
            return next++;
        }

        @Override
        protected void persist(Mail mail) {
            if (failSave) throw new IllegalStateException("Simulated write failure");
        }

        @Override
        protected void persistInventory(List<Mail.MailItem> attachments) {
            if (failInventory) throw new IllegalStateException("Simulated inventory write interruption");
        }
    }

    Mail mail(int count) {
        return new Mail(
                new Mail.MailContent("测试", "正文", "LunaGC"),
                count == 0 ? new ArrayList<>() : new ArrayList<>(List.of(new Mail.MailItem(201, count))),
                Instant.now().getEpochSecond() + 86400);
    }

    @BeforeEach
    void resources() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        previous =
                GameData.getItemDataMap()
                        .put(
                                201,
                                new Gson()
                                        .fromJson(
                                                "{\"id\":201,\"itemType\":\"ITEM_VIRTUAL\",\"stackLimit\":2147483647}",
                                                ItemData.class));
        previousWeapon =
                GameData.getItemDataMap()
                        .put(
                                11101,
                                new Gson()
                                        .fromJson(
                                                "{\"id\":11101,\"itemType\":\"ITEM_WEAPON\",\"rankLevel\":3,\"stackLimit\":1}",
                                                ItemData.class));
    }

    @AfterEach
    void restore() {
        if (previous == null) GameData.getItemDataMap().remove(201);
        else GameData.getItemDataMap().put(201, previous);
        if (previousWeapon == null) GameData.getItemDataMap().remove(11101);
        else GameData.getItemDataMap().put(11101, previousWeapon);
    }

    @Test
    void deletionDoesNotShiftClientIdsAndRepeatedLoadDoesNotDuplicateMail() {
        var player = new TestPlayer();
        var a = mail(0);
        var b = mail(0);
        player.handler.stored.addAll(List.of(a, b));
        player.handler.loadFromDatabase();
        player.handler.loadFromDatabase();
        assertEquals(2, player.handler.getMail().size());
        int id = b.mailId;
        assertTrue(player.handler.deleteMail(0));
        assertEquals(id, player.handler.toClientMailId(0));
        assertEquals(0, player.handler.toInternalMailIndex(id));
        assertEquals(-1, player.handler.toInternalMailIndex(a.mailId));
        var reloaded = new Handler(player);
        reloaded.stored = List.of(b);
        reloaded.loadFromDatabase();
        assertEquals(id, reloaded.toClientMailId(0));
    }

    @Test
    void sendingTheSameTemplateCreatesIndependentDocumentsAndStableClientIds() {
        var player = new TestPlayer();
        var template = mail(2);
        assertTrue(player.handler.deliver(template));
        assertTrue(player.handler.deliver(template));
        var first = player.handler.getMailById(0);
        var second = player.handler.getMailById(1);
        assertNotSame(first, second);
        assertNotSame(first.itemList.get(0), second.itemList.get(0));
        assertNotEquals(first.mailId, second.mailId);
        assertEquals(0, template.mailId);
    }

    @Test
    void normalCollectedAndExpiredMailHaveConsistentPayloads() throws Exception {
        var player = new TestPlayer();
        var normal = mail(2);
        var collected = mail(0);
        collected.stateValue = 3;
        var expired = mail(0);
        expired.expireTime = 0;
        player.handler.stored.addAll(List.of(normal, collected, expired));
        var rsp =
                GetAllMailRspOuterClass.GetAllMailRsp.parseFrom(
                        new PacketGetAllMailRsp(player, false).getData());
        assertEquals(
                List.of(normal.mailId),
                rsp.getMailListList().stream().map(MailDataOuterClass.MailData::getMailId).toList());
        var notify =
                GetAllMailResultNotifyOuterClass.GetAllMailResultNotify.parseFrom(
                        new PacketGetAllMailResultNotify(player, false).getData());
        assertEquals(rsp.getMailListList(), notify.getMailListList());
        assertEquals(1, notify.getTotalPageCount());
        assertEquals(
                1,
                GetAllMailRspOuterClass.GetAllMailRsp.parseFrom(
                                new PacketGetAllMailRsp(player, true).getData())
                        .getMailListCount());
        var change =
                MailChangeNotifyOuterClass.MailChangeNotify.parseFrom(
                        new PacketMailChangeNotify(player, List.of(normal)).getData());
        assertEquals(0, change.getMailListCount());
        assertEquals(1, change.getChangeMailListCount());
    }

    @Test
    void retryCannotGrantAttachmentsTwiceAndUnclaimedMailCannotBeDeleted() {
        var player = new TestPlayer();
        var message = mail(60);
        player.handler.stored.add(message);
        player.handler.loadFromDatabase();
        assertFalse(player.handler.deleteMail(0));
        var first = player.handler.claim(List.of(message.mailId, message.mailId));
        assertEquals(0, first.retcode());
        assertEquals(1, first.ids().size());
        assertEquals(60, player.granted);
        player.handler.claim(List.of(message.mailId));
        assertEquals(60, player.granted);
        assertTrue(message.isAttachmentGot);
        assertTrue(player.handler.deleteMail(0));
    }

    @Test
    void expiredInvalidAndFullInventoryClaimsDoNotLoseAttachments() {
        var player = new TestPlayer();
        var message = mail(0);
        message.itemList.add(new Mail.MailItem(11101, 1));
        player.handler.stored.add(message);
        player.handler.loadFromDatabase();
        player.bag.createInventoryTab(ItemType.ITEM_WEAPON, new EquipInventoryTab(0));
        assertNotEquals(0, player.handler.claim(List.of(message.mailId)).retcode());
        assertFalse(message.isAttachmentGot);
        assertFalse(message.claimInProgress);
        assertEquals(0, player.granted);
        message.expireTime = 0;
        assertNotEquals(0, player.handler.claim(List.of(message.mailId)).retcode());
        assertEquals(0, player.granted);
        assertNotEquals(0, player.handler.claim(List.of(999)).retcode());
    }

    @Test
    void writeFailureBlocksGrantAndInterruptedGrantBlocksAnotherClaim() {
        var player = new TestPlayer();
        var message = mail(60);
        player.handler.stored.add(message);
        player.handler.loadFromDatabase();
        player.handler.failSave = true;
        assertNotEquals(0, player.handler.claim(List.of(message.mailId)).retcode());
        assertEquals(0, player.granted);
        player.handler.failSave = false;
        player.handler.failInventory = true;
        assertNotEquals(0, player.handler.claim(List.of(message.mailId)).retcode());
        assertEquals(60, player.granted);
        assertTrue(message.claimInProgress);
        player.handler.claim(List.of(message.mailId));
        assertEquals(60, player.granted);
    }
}
