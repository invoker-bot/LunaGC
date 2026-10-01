package emu.grasscutter.game.gacha;

import com.google.gson.*;
import emu.grasscutter.data.DataLoader;
import emu.grasscutter.utils.JsonUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Stable local IDs are distinct from the official archive's IDs (90000 and above). */
public final class BannerConfig {
    private BannerConfig() {}

    public static JsonArray read(Path file) throws IOException {
        if (Files.exists(file)) {
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                return JsonUtils.loadToClass(reader, JsonArray.class);
            }
        }
        var defaults = DataLoader.loadTableToList("Banners", GachaBanner.class);
        return JsonUtils.toJson(defaults == null ? List.of() : defaults).getAsJsonArray();
    }

    public static JsonArray normalize(JsonArray original) {
        var rows = original.deepCopy();
        var used = new HashSet<Integer>();
        for (var el : rows) {
            if (!el.isJsonObject()) throw new IllegalArgumentException("卡池配置必须是对象列表。");
            int id = id(el.getAsJsonObject());
            if (id >= 0 && !used.add(id)) throw new IllegalArgumentException("卡池 ID 重复：" + id);
        }
        int liveId = 1000, templateId = 50000;
        for (var el : rows) {
            var row = el.getAsJsonObject();
            if (id(row) >= 0) continue;
            boolean disabled = row.has("disabled") && row.get("disabled").getAsBoolean();
            int next = disabled ? templateId : liveId;
            while (used.contains(next)) next++;
            if (next >= (disabled ? 90000 : 50000)) throw new IllegalArgumentException("本地卡池 ID 已用尽。");
            row.addProperty("scheduleId", next);
            used.add(next);
            if (disabled) templateId = next + 1;
            else liveId = next + 1;
        }
        return rows;
    }

    private static int id(JsonObject row) {
        return !row.has("scheduleId") || row.get("scheduleId").isJsonNull()
                ? -1
                : row.get("scheduleId").getAsBigDecimal().intValueExact();
    }

    /** Persist before exposing IDs; keep the original table for migration recovery. */
    public static synchronized JsonArray load(Path file) throws IOException {
        var original = read(file);
        var normalized = normalize(original);
        if (Files.exists(file) && normalized.equals(original)) return normalized;
        Files.createDirectories(file.toAbsolutePath().getParent());
        var backup = Files.createTempFile(file.getParent(), "Banners.before-id-migration-", ".json");
        if (Files.exists(file)) Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
        else Files.writeString(backup, JsonUtils.encode(original), StandardCharsets.UTF_8);
        var temp = Files.createTempFile(file.getParent(), "Banners-", ".tmp");
        try {
            Files.writeString(temp, JsonUtils.encode(normalized), StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
        return normalized;
    }

    public static long rerunEnd(JsonObject request, long now) {
        int days = 30;
        if (request.has("durationDays")) {
            try {
                days = request.get("durationDays").getAsBigDecimal().intValueExact();
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("复刻时长须为 1–365 的整数天数。");
            }
        }
        if (days < 1 || days > 365) throw new IllegalArgumentException("复刻时长须为 1–365 天。");
        long end = now + days * 86400L;
        if (end > Integer.MAX_VALUE) throw new IllegalArgumentException("到期时间超出协议支持范围。");
        return end;
    }

    public static long requestedEnd(JsonObject request, long now, long begin) {
        long end;
        try {
            end = request.get("endTime").getAsBigDecimal().longValueExact();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("请填写有效的到期时间。");
        }
        if (end <= now || end <= begin || end > Integer.MAX_VALUE)
            throw new IllegalArgumentException("到期时间须晚于当前时间和开始时间，且不超过 2038 年协议上限。");
        return end;
    }
}
