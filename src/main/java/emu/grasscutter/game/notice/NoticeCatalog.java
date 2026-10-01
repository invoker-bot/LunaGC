package emu.grasscutter.game.notice;

import com.google.gson.*;
import emu.grasscutter.utils.FileUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;

/** The same persisted publication catalogue backs the GM editor and both client regions. */
public final class NoticeCatalog {
    public record Notice(
            int id,
            String title,
            String content,
            int category,
            boolean enabled,
            long beginTime,
            long endTime) {}

    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static NoticeCatalog instance;
    private final Path path;
    private final Clock clock;
    private List<Notice> notices;
    private int nextId;

    public record State(int nextId, List<Notice> notices) {}

    public static synchronized NoticeCatalog get() throws IOException {
        if (instance == null)
            instance = new NoticeCatalog(FileUtils.getDataPath("Notices.json"), Clock.systemUTC());
        return instance;
    }

    public NoticeCatalog(Path path, Clock clock) throws IOException {
        this.path = path;
        this.clock = clock;
        if (Files.isRegularFile(path)) {
            try {
                var state = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                notices = new ArrayList<>();
                nextId = state.get("nextId").getAsInt();
                for (var entry : state.getAsJsonArray("notices"))
                    notices.add(parse(entry.getAsJsonObject()));
                for (Notice n : notices) validate(n);
                if (notices.stream().map(Notice::id).distinct().count() != notices.size())
                    throw new IllegalArgumentException("Duplicate notice IDs");
            } catch (RuntimeException e) {
                throw new IOException("Cannot read notice catalogue", e);
            }
        } else {
            notices = importLegacy(path.resolveSibling("GameAnnouncement.json"));
            if (notices.isEmpty())
                notices =
                        new ArrayList<>(
                                List.of(
                                        new Notice(
                                                1, "欢迎来到 LunaGC", "欢迎进入本地服务器。公告与邮件可在 GM 页面管理。", 2, true, 0, 2147483647L)));
            nextId = 1;
        }
        nextId = Math.max(nextId, notices.stream().mapToInt(Notice::id).max().orElse(0) + 1);
    }

    public static Notice parse(JsonObject o) {
        if (o == null) throw new IllegalArgumentException("缺少公告内容。");
        return new Notice(
                o.has("id") ? o.get("id").getAsInt() : 0,
                o.has("title") ? o.get("title").getAsString() : null,
                o.has("content") ? o.get("content").getAsString() : null,
                o.has("category") ? o.get("category").getAsInt() : 2,
                o.has("enabled") && o.get("enabled").getAsBoolean(),
                o.has("beginTime") ? o.get("beginTime").getAsLong() : 0,
                o.has("endTime") ? o.get("endTime").getAsLong() : 0);
    }

    private static List<Notice> importLegacy(Path path) throws IOException {
        var result = new ArrayList<Notice>();
        if (!Files.isRegularFile(path)) return result;
        try {
            JsonObject data = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            if (data.has("data")) data = data.getAsJsonObject("data");
            if (!data.has("list")) return result;
            for (JsonElement entry : data.getAsJsonArray("list")) {
                var o = entry.getAsJsonObject();
                if (!o.has("title") || !o.has("content")) continue;
                String title = plain(o.get("title").getAsString()),
                        content = plain(o.get("content").getAsString());
                if (title.isBlank() || content.isBlank()) continue;
                result.add(new Notice(result.size() + 1, title, content, 2, true, 0, 2147483647L));
            }
        } catch (RuntimeException e) {
            throw new IOException("Cannot import GameAnnouncement.json", e);
        }
        return result;
    }

    private static String plain(String s) {
        return s.replaceAll("(?i)<(?:br\\s*/?|/p)>", "\n")
                .replaceAll("<[^>]*>", "")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .trim();
    }

    private static void validate(Notice n) {
        if (n.title() == null || n.title().isBlank() || n.title().length() > 100)
            throw new IllegalArgumentException("公告标题须为 1–100 字。");
        if (n.content() == null || n.content().isBlank() || n.content().length() > 20000)
            throw new IllegalArgumentException("公告正文须为 1–20000 字。");
        if (n.category() != 1 && n.category() != 2) throw new IllegalArgumentException("请选择系统公告或活动公告。");
        if (n.beginTime() < 0 || n.endTime() <= n.beginTime() || n.endTime() > 2147483647L)
            throw new IllegalArgumentException("结束时间必须晚于开始时间，且不晚于 2038 年。");
    }

    public synchronized List<Notice> all() {
        return List.copyOf(notices);
    }

    public synchronized List<Notice> active() {
        long now = clock.instant().getEpochSecond();
        return notices.stream()
                .filter(n -> n.enabled() && n.beginTime() <= now && n.endTime() > now)
                .sorted(Comparator.comparingInt(Notice::id).reversed())
                .toList();
    }

    public synchronized Notice edit(String action, Notice input) throws IOException {
        if (input == null) throw new IllegalArgumentException("缺少公告内容。");
        int id = input.id() == 0 ? nextId : input.id();
        Notice previous = notices.stream().filter(n -> n.id() == id).findFirst().orElse(null), updated;
        if (input.id() != 0 && previous == null) throw new IllegalArgumentException("公告不存在。");
        switch (action) {
            case "save" -> {
                validate(input);
                updated =
                        new Notice(
                                id,
                                input.title().trim(),
                                input.content().trim(),
                                input.category(),
                                input.enabled(),
                                input.beginTime(),
                                input.endTime());
            }
            case "publish", "withdraw" -> {
                if (previous == null) throw new IllegalArgumentException("请先保存公告。");
                if (action.equals("publish") && previous.endTime() <= clock.instant().getEpochSecond())
                    throw new IllegalArgumentException("公告已过期，请先修改结束时间。");
                updated =
                        new Notice(
                                id,
                                previous.title(),
                                previous.content(),
                                previous.category(),
                                action.equals("publish"),
                                previous.beginTime(),
                                previous.endTime());
            }
            case "delete" -> {
                if (previous == null) throw new IllegalArgumentException("公告不存在。");
                updated = previous;
            }
            default -> throw new IllegalArgumentException("未知公告操作。");
        }
        var candidate = new ArrayList<>(notices);
        candidate.removeIf(n -> n.id() == id);
        if (!action.equals("delete")) candidate.add(updated);
        int next = Math.max(nextId, id + 1);
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(path.toAbsolutePath().getParent(), "notices-", ".tmp");
        try {
            Files.writeString(temporary, JSON.toJson(new State(next, candidate)), StandardCharsets.UTF_8);
            try {
                Files.move(
                        temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        notices = candidate;
        nextId = next;
        return updated;
    }

    private static String html(String text) {
        return "<p>"
                + text.replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("\"", "&quot;")
                        .replace("\n", "<br>")
                + "</p>";
    }

    public Map<String, Object> clientContent() {
        var rows =
                active().stream()
                        .map(
                                n ->
                                        Map.of(
                                                "ann_id",
                                                n.id(),
                                                "title",
                                                n.title(),
                                                "subtitle",
                                                "",
                                                "banner",
                                                "",
                                                "content",
                                                html(n.content()),
                                                "lang",
                                                "zh-cn"))
                        .toList();
        return Map.of("list", rows, "total", rows.size(), "t", String.valueOf(clock.millis()));
    }

    public Map<String, Object> clientList() {
        var active = active();
        var groups = new ArrayList<Map<String, Object>>();
        for (int category : List.of(2, 1)) {
            var rows =
                    active.stream()
                            .filter(n -> n.category() == category)
                            .map(
                                    n ->
                                            Map.of(
                                                    "ann_id",
                                                    n.id(),
                                                    "title",
                                                    n.title(),
                                                    "subtitle",
                                                    "",
                                                    "banner",
                                                    "",
                                                    "content",
                                                    html(n.content()),
                                                    "start_time",
                                                    String.valueOf(n.beginTime()),
                                                    "end_time",
                                                    String.valueOf(n.endTime()),
                                                    "type",
                                                    category,
                                                    "lang",
                                                    "zh-cn"))
                            .toList();
            groups.add(
                    Map.of("type_id", category, "type_label", category == 2 ? "系统公告" : "活动公告", "list", rows));
        }
        return Map.of(
                "list",
                groups,
                "total",
                active.size(),
                "type_list",
                List.of(Map.of("id", 2, "name", "系统公告"), Map.of("id", 1, "name", "活动公告")),
                "timezone",
                8,
                "t",
                String.valueOf(clock.millis()),
                "alert",
                !active.isEmpty(),
                "alert_id",
                active.isEmpty() ? 0 : active.get(0).id());
    }
}
