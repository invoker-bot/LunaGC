package emu.grasscutter.game.mail;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.player.Player;
import java.util.*;
import org.junit.jupiter.api.*;

class MailDeliveryServiceTest {
    ItemData previous;

    @BeforeEach
    void resources() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        previous =
                GameData.getItemDataMap()
                        .put(
                                201,
                                new Gson().fromJson("{\"id\":201,\"itemType\":\"ITEM_VIRTUAL\"}", ItemData.class));
    }

    @AfterEach
    void restore() {
        if (previous == null) GameData.getItemDataMap().remove(201);
        else GameData.getItemDataMap().put(201, previous);
    }

    static class Repository implements MailDeliveryService.Repository {
        Map<String, Mail> messages = new HashMap<>();
        Map<Integer, Player> players = Map.of(1, new Player(), 2, new Player());
        boolean rejectSecond;

        public Player player(int uid) {
            return players.get(uid);
        }

        public Mail previous(String key) {
            return messages.get(key);
        }

        public boolean deliver(Player player, Mail mail) {
            if (rejectSecond && player == players.get(2)) return false;
            messages.put(mail.deliveryKey, mail);
            return true;
        }
    }

    MailDeliveryService.Request request(List<Integer> targets, String title) {
        return new MailDeliveryService.Request(
                targets,
                title,
                "正文",
                "LunaGC",
                7,
                List.of(new Mail.MailItem(201, 60)),
                "abc-def-1234567890");
    }

    @Test
    void partialRetryResumesOnlyUnsentRecipientsAndCopiesEachAttachment() {
        var repo = new Repository();
        var service = new MailDeliveryService(repo);
        repo.rejectSecond = true;
        assertEquals(List.of(2), service.send(request(List.of(1, 2), "标题")).failed());
        repo.rejectSecond = false;
        var retry = service.send(request(List.of(1, 2), "标题"));
        assertEquals(1, retry.delivered());
        assertEquals(1, retry.alreadyDelivered());
        assertEquals(2, repo.messages.size());
        var a = repo.messages.get("gm:abc-def-1234567890:1");
        var b = repo.messages.get("gm:abc-def-1234567890:2");
        assertNotSame(a, b);
        assertNotSame(a.itemList.get(0), b.itemList.get(0));
        assertThrows(
                IllegalArgumentException.class, () -> service.send(request(List.of(1, 2), "changed")));
    }

    @Test
    void nonexistentRecipientsAndInvalidAttachmentsFailBeforeAnyDelivery() {
        var repo = new Repository();
        var service = new MailDeliveryService(repo);
        assertThrows(
                IllegalArgumentException.class, () -> service.send(request(List.of(1, 999), "标题")));
        assertTrue(repo.messages.isEmpty());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.send(
                                new MailDeliveryService.Request(
                                        List.of(1),
                                        "标题",
                                        "正文",
                                        "LunaGC",
                                        7,
                                        List.of(new Mail.MailItem(999999, 1)),
                                        "abc-def-1234567890")));
        assertTrue(repo.messages.isEmpty());
    }
}
