package emu.grasscutter.server.http.handlers;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.mail.*;
import emu.grasscutter.game.notice.NoticeCatalog;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.utils.JsonUtils;
import io.javalin.http.Context;
import java.util.*;

/** Every route is wrapped by GmHandler's token/loopback authorization. */
public final class GmMessages {
    private static final MailDeliveryService DELIVERY =
            new MailDeliveryService(
                    new MailDeliveryService.Repository() {
                        public Player player(int uid) {
                            return serverPlayer(uid);
                        }

                        public Mail previous(String key) {
                            return DatabaseHelper.getMailByDeliveryKey(key);
                        }

                        public boolean deliver(Player player, Mail mail) {
                            // Re-check the live session after validation so an offline snapshot cannot replace
                            // it.
                            var current = serverPlayer(player.getUid());
                            return current != null && current.getMailHandler().deliver(mail);
                        }
                    });

    private static Player serverPlayer(int uid) {
        if (Grasscutter.getGameServer() == null) throw new IllegalArgumentException("游戏服务未启动。");
        return Grasscutter.getGameServer().getPlayerByUid(uid, true);
    }

    private static class MailRequest {
        List<Integer> targets;
        String title;
        String content;
        String sender;
        int durationDays;
        List<Mail.MailItem> attachments = new ArrayList<>();
        String receipt;
    }

    static void notices(Context ctx) throws Exception {
        var catalog = NoticeCatalog.get();
        ctx.json(
                Map.of(
                        "retcode",
                        0,
                        "notices",
                        catalog.all(),
                        "activeIds",
                        catalog.active().stream().map(NoticeCatalog.Notice::id).toList()));
    }

    static void editNotice(Context ctx) throws Exception {
        var request = JsonUtils.decode(ctx.body(), com.google.gson.JsonObject.class);
        if (request == null || !request.has("action")) throw new IllegalArgumentException("公告参数不完整。");
        var result =
                NoticeCatalog.get()
                        .edit(
                                request.get("action").getAsString(),
                                NoticeCatalog.parse(request.getAsJsonObject("notice")));
        ctx.json(Map.of("retcode", 0, "notice", result, "message", "公告已保存，重新打开游戏公告即可查看。"));
    }

    static void sendMail(Context ctx) {
        var r = JsonUtils.decode(ctx.body(), MailRequest.class);
        if (r == null) throw new IllegalArgumentException("邮件参数不完整。");
        var result =
                DELIVERY.send(
                        new MailDeliveryService.Request(
                                r.targets, r.title, r.content, r.sender, r.durationDays, r.attachments, r.receipt));
        ctx.json(
                Map.of(
                        "retcode",
                        result.failed().isEmpty() ? 0 : 1,
                        "result",
                        result,
                        "message",
                        result.message()));
    }

    static void inbox(Context ctx) {
        int uid;
        try {
            uid = Integer.parseInt(Objects.requireNonNullElse(ctx.queryParam("target"), "0"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("请填写有效的 UID。");
        }
        var player = serverPlayer(uid);
        if (player == null) throw new IllegalArgumentException("该 UID 不存在。");
        var messages = player.getMailHandler().getMail();
        int offset =
                Math.max(0, Integer.parseInt(Objects.requireNonNullElse(ctx.queryParam("offset"), "0")));
        var rows =
                messages.stream()
                        .sorted(Comparator.comparingLong((Mail m) -> m.sendTime).reversed())
                        .skip(offset)
                        .limit(50)
                        .map(
                                m -> {
                                    var row = new LinkedHashMap<String, Object>();
                                    row.put("id", m.mailId);
                                    row.put("title", m.mailContent.title);
                                    row.put("sender", m.mailContent.sender);
                                    row.put("content", m.mailContent.content);
                                    row.put("attachments", m.itemList);
                                    row.put("sendTime", m.sendTime);
                                    row.put("expireTime", m.expireTime);
                                    row.put("read", m.isRead);
                                    row.put("claimed", m.isAttachmentGot);
                                    row.put("claimInProgress", m.claimInProgress);
                                    row.put("gmDelivery", m.deliveryKey != null);
                                    return row;
                                })
                        .toList();
        ctx.json(
                Map.of(
                        "retcode",
                        0,
                        "target",
                        uid,
                        "mail",
                        rows,
                        "total",
                        messages.size(),
                        "offset",
                        offset,
                        "limit",
                        50));
    }
}
