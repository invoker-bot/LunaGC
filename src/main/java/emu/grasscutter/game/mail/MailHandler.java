package emu.grasscutter.game.mail;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.proto.EquipParamOuterClass.EquipParam;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.event.player.PlayerReceiveMailEvent;
import emu.grasscutter.server.packet.send.*;
import java.time.Instant;
import java.util.*;

public class MailHandler extends BasePlayerManager {
    private final List<Mail> mail = new ArrayList<>();

    public MailHandler(Player player) {
        super(player);
    }

    protected int allocateId() {
        return DatabaseHelper.nextMailId();
    }

    protected void persist(Mail message) {
        DatabaseHelper.saveGameSync(message);
    }

    protected List<Mail> storedMail() {
        return DatabaseHelper.getAllMail(player);
    }

    protected void persistInventory(List<Mail.MailItem> attachments) {
        var ids = attachments.stream().map(a -> a.itemId).collect(java.util.stream.Collectors.toSet());
        for (var item : player.getInventory().getItems().values())
            if (ids.contains(item.getItemId())) DatabaseHelper.saveGameSync(item);
        DatabaseHelper.saveGameSync(player);
    }

    private boolean active(Mail message) {
        return message.expireTime > Instant.now().getEpochSecond();
    }

    public List<Mail> getMail() {
        synchronized (player) {
            if (!loaded) loadFromDatabase();
            else {
                // A GM delivery can overlap login before the player enters the live-player map.
                var known =
                        mail.stream()
                                .map(Mail::getId)
                                .filter(Objects::nonNull)
                                .collect(java.util.stream.Collectors.toSet());
                for (var stored : storedMail())
                    if (active(stored) && stored.getId() != null && !known.contains(stored.getId())) {
                        if (stored.mailId <= 0)
                            stored.mailId = DatabaseHelper.assignLegacyMailId(stored, allocateId());
                        mail.add(stored);
                        known.add(stored.getId());
                    }
            }
            return mail.stream().filter(this::active).toList();
        }
    }

    public List<Mail> getMail(boolean collected) {
        return getMail().stream().filter(m -> (m.stateValue == 3) == collected).toList();
    }

    public void loadFromDatabase() {
        synchronized (player) {
            if (loaded) return;
            var stored = new ArrayList<>(storedMail());
            stored.removeIf(message -> !active(message));
            stored.sort(
                    Comparator.comparingLong((Mail m) -> m.sendTime)
                            .thenComparing(m -> m.getId() == null ? "" : m.getId().toHexString()));
            var used = new HashSet<Integer>();
            for (Mail message : stored) {
                if (message.mailId <= 0 || !used.add(message.mailId)) {
                    do {
                        message.mailId = allocateId();
                    } while (used.contains(message.mailId));
                    if (message.getId() != null)
                        message.mailId = DatabaseHelper.assignLegacyMailId(message, message.mailId);
                    else persist(message);
                    used.add(message.mailId);
                }
            }
            mail.clear();
            mail.addAll(stored);
            loaded = true;
        }
    }

    public void sendMail(Mail message) {
        deliver(message);
    }

    public boolean deliver(Mail message) {
        synchronized (player) {
            loadFromDatabase();
            message = message.copyForDelivery();
            var event = new PlayerReceiveMailEvent(player, message);
            event.call();
            if (event.isCanceled()) return false;
            var original = message;
            message = event.getMessage();
            if (message == null || !active(message)) throw new IllegalArgumentException("邮件已过期或内容为空。");
            message.deliveryKey = original.deliveryKey;
            message.deliveryDigest = original.deliveryDigest;
            message.setOwnerUid(player.getUid());
            message.mailId = allocateId();
            persist(message);
            mail.add(message);
            if (player.isOnline()) player.sendPacket(new PacketMailChangeNotify(player, message));
            return true;
        }
    }

    public Mail getMailById(int index) {
        synchronized (player) {
            loadFromDatabase();
            return index >= 0 && index < mail.size() && active(mail.get(index)) ? mail.get(index) : null;
        }
    }

    public int toClientMailId(int index) {
        var message = getMailById(index);
        return message == null ? 0 : message.mailId;
    }

    public int toInternalMailIndex(int id) {
        synchronized (player) {
            loadFromDatabase();
            for (int i = 0; i < mail.size(); i++)
                if (mail.get(i).mailId == id && active(mail.get(i))) return i;
            return -1;
        }
    }

    public int getMailIndex(Mail message) {
        synchronized (player) {
            return mail.indexOf(message);
        }
    }

    public boolean replaceMailByIndex(int index, Mail message) {
        synchronized (player) {
            var original = getMailById(index);
            if (original == null) return false;
            message.mailId = original.mailId;
            persist(message);
            mail.set(index, message);
            return true;
        }
    }

    public boolean deleteMail(int index) {
        synchronized (player) {
            var message = getMailById(index);
            if (message == null
                    || message.claimInProgress
                    || (!message.itemList.isEmpty() && !message.isAttachmentGot)) return false;
            long previous = message.expireTime;
            message.expireTime = 0;
            try {
                persist(message);
            } catch (RuntimeException e) {
                message.expireTime = previous;
                throw e;
            }
            mail.remove(message);
            return true;
        }
    }

    public void deleteMail(List<Integer> indexes) {
        synchronized (player) {
            deleteClientMail(indexes.stream().map(this::toClientMailId).toList());
        }
    }

    public void deleteClientMail(List<Integer> ids) {
        synchronized (player) {
            var deleted = new ArrayList<Integer>();
            boolean denied = false;
            for (int id : new LinkedHashSet<>(ids)) {
                int index = toInternalMailIndex(id);
                if (index >= 0 && deleteMail(index)) deleted.add(id);
                else denied = true;
            }
            player.sendPacket(
                    new PacketDelMailRsp(
                            player, deleted, denied ? Retcode.RET_MAIL_ITEM_NOT_GET.getNumber() : 0));
            if (!deleted.isEmpty()) player.sendPacket(new PacketMailChangeNotify(player, null, deleted));
        }
    }

    public void updateClientMail(List<Integer> ids, Boolean star) {
        synchronized (player) {
            var changed = new ArrayList<Mail>();
            for (int id : new LinkedHashSet<>(ids)) {
                var message = getMailById(toInternalMailIndex(id));
                if (message == null) continue;
                boolean read = message.isRead;
                int importance = message.importance;
                if (star == null) message.isRead = true;
                else message.importance = star ? 1 : 0;
                try {
                    persist(message);
                } catch (RuntimeException e) {
                    message.isRead = read;
                    message.importance = importance;
                    throw e;
                }
                changed.add(message);
            }
            if (!changed.isEmpty()) player.sendPacket(new PacketMailChangeNotify(player, changed));
        }
    }

    public record ClaimResult(List<Integer> ids, List<EquipParam> items, int retcode) {}

    public ClaimResult claim(List<Integer> ids) {
        synchronized (player) {
            synchronized (player.getInventory()) {
                var claimedIds = new ArrayList<Integer>();
                var items = new ArrayList<EquipParam>();
                var changed = new ArrayList<Mail>();
                int retcode = 0;
                for (int id : new LinkedHashSet<>(ids)) {
                    var message = getMailById(toInternalMailIndex(id));
                    if (message == null) {
                        retcode = Retcode.RET_MAIL_EXPIRED.getNumber();
                        continue;
                    }
                    if (message.isAttachmentGot) {
                        claimedIds.add(id);
                        continue;
                    }
                    if (message.claimInProgress) {
                        retcode = Retcode.RET_SVR_ERROR.getNumber();
                        continue;
                    }
                    var inventory = player.getInventory();
                    int granted = 0;
                    boolean attempted = false;
                    try {
                        MailAttachments.validate(message.itemList);
                        MailAttachments.checkCapacity(inventory, message.itemList);
                        message.claimInProgress = true;
                        try {
                            persist(message);
                        } catch (RuntimeException e) {
                            message.claimInProgress = false;
                            throw e;
                        }
                        for (var attachment : message.itemList) {
                            var data = GameData.getItemDataMap().get(attachment.itemId);
                            int count =
                                    (data.getItemType() == ItemType.ITEM_WEAPON
                                                    || data.getItemType() == ItemType.ITEM_RELIQUARY)
                                            ? attachment.itemCount
                                            : 1;
                            for (int i = 0; i < count; i++) {
                                var item = new GameItem(data, count == 1 ? attachment.itemCount : 1);
                                item.setLevel(attachment.itemLevel);
                                item.setPromoteLevel(GameItem.getMinPromoteLevel(attachment.itemLevel));
                                attempted = true;
                                if (!inventory.addItem(item, ActionReason.MailAttachment, false, true)) {
                                    attempted = false;
                                    throw new IllegalStateException("Inventory rejected mail attachment");
                                }
                                granted++;
                            }
                        }
                        persistInventory(message.itemList);
                        message.isAttachmentGot = true;
                        message.isRead = true;
                        message.claimInProgress = false;
                        try {
                            persist(message);
                        } catch (RuntimeException e) {
                            message.claimInProgress = true;
                            throw e;
                        }
                        claimedIds.add(id);
                        changed.add(message);
                        for (var a : message.itemList)
                            items.add(
                                    EquipParam.newBuilder()
                                            .setItemId(a.itemId)
                                            .setItemNum(a.itemCount)
                                            .setItemLevel(a.itemLevel)
                                            .setPromoteLevel(GameItem.getMinPromoteLevel(a.itemLevel))
                                            .build());
                    } catch (IllegalArgumentException e) {
                        retcode = Retcode.RET_ITEM_EXCEED_LIMIT.getNumber();
                    } catch (RuntimeException e) {
                        // Once any grant may have succeeded, retain the durable reservation for operator
                        // reconciliation.
                        if (granted == 0 && !attempted && message.claimInProgress) {
                            message.claimInProgress = false;
                            try {
                                persist(message);
                            } catch (RuntimeException ignored) {
                                message.claimInProgress = true;
                            }
                        }
                        Grasscutter.getLogger()
                                .error("Mail attachment claim failed: uid={}, mailId={}", player.getUid(), id, e);
                        retcode = Retcode.RET_SVR_ERROR.getNumber();
                    }
                }
                if (!changed.isEmpty()) player.sendPacket(new PacketMailChangeNotify(player, changed));
                return new ClaimResult(claimedIds, items, retcode);
            }
        }
    }
}
