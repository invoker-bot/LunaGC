package emu.grasscutter.game.mail;

import com.google.gson.Gson;
import emu.grasscutter.game.player.Player;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;

/** Persisted per-recipient receipts make an HTTP retry safe, including offline delivery. */
public final class MailDeliveryService {
    public interface Repository {
        Player player(int uid);

        Mail previous(String key);

        boolean deliver(Player player, Mail mail);
    }

    public record Request(
            List<Integer> targets,
            String title,
            String content,
            String sender,
            int durationDays,
            List<Mail.MailItem> attachments,
            String receipt) {}

    public record Result(int delivered, int alreadyDelivered, List<Integer> failed, String message) {}

    private final Repository repository;

    public MailDeliveryService(Repository repository) {
        this.repository = repository;
    }

    public synchronized Result send(Request request) {
        if (request == null
                || request.targets() == null
                || request.targets().isEmpty()
                || request.targets().size() > 100
                || request.targets().stream().anyMatch(id -> id == null || id <= 0))
            throw new IllegalArgumentException("请填写 1–100 个有效 UID。");
        if (request.title() == null || request.title().isBlank() || request.title().length() > 100)
            throw new IllegalArgumentException("邮件标题须为 1–100 字。");
        if (request.content() == null
                || request.content().isBlank()
                || request.content().length() > 5000)
            throw new IllegalArgumentException("邮件正文须为 1–5000 字。");
        if (request.sender() == null || request.sender().isBlank() || request.sender().length() > 50)
            throw new IllegalArgumentException("发件人须为 1–50 字。");
        if (request.durationDays() < 1 || request.durationDays() > 365)
            throw new IllegalArgumentException("邮件有效期须为 1–365 天。");
        if (request.receipt() == null || !request.receipt().matches("[a-zA-Z0-9-]{16,80}"))
            throw new IllegalArgumentException("发送批次编号无效，请重新打开邮件表单。");
        MailAttachments.validate(request.attachments());
        var targets = request.targets().stream().distinct().sorted().toList();
        var canonical =
                new Request(
                        targets,
                        request.title().trim(),
                        request.content().trim(),
                        request.sender().trim(),
                        request.durationDays(),
                        request.attachments(),
                        request.receipt());
        String digest;
        try {
            digest =
                    HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(new Gson().toJson(canonical).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        var players = new HashMap<Integer, Player>();
        for (int uid : targets) {
            String key = "gm:" + request.receipt() + ":" + uid;
            var old = repository.previous(key);
            if (old != null && !digest.equals(old.deliveryDigest))
                throw new IllegalArgumentException("该发送批次已用于另一份内容，请新建邮件。");
            var player = repository.player(uid);
            if (player == null) throw new IllegalArgumentException("UID " + uid + " 不存在。请先在游戏中创建角色。");
            players.put(uid, player);
        }
        int delivered = 0, already = 0;
        var failed = new ArrayList<Integer>();
        long expires = Instant.now().getEpochSecond() + request.durationDays() * 86400L;
        for (int uid : targets) {
            String key = "gm:" + request.receipt() + ":" + uid;
            if (repository.previous(key) != null) {
                already++;
                continue;
            }
            var attachments =
                    request.attachments().stream()
                            .map(a -> new Mail.MailItem(a.itemId, a.itemCount, a.itemLevel))
                            .toList();
            var message =
                    new Mail(
                            new Mail.MailContent(canonical.title(), canonical.content(), canonical.sender()),
                            new ArrayList<>(attachments),
                            expires);
            message.deliveryKey = key;
            message.deliveryDigest = digest;
            try {
                if (repository.deliver(players.get(uid), message)) delivered++;
                else failed.add(uid);
            } catch (RuntimeException e) {
                emu.grasscutter.Grasscutter.getLogger()
                        .error("GM mail delivery failed: uid={}, receipt={}", uid, request.receipt(), e);
                failed.add(uid);
            }
        }
        return new Result(
                delivered,
                already,
                failed,
                "已投递 "
                        + delivered
                        + " 封，已投递跳过 "
                        + already
                        + " 封，失败 "
                        + failed.size()
                        + " 封。"
                        + (failed.isEmpty() ? "" : "保持内容不变再次发送可重试失败 UID。"));
    }
}
