package emu.grasscutter.game.gacha;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import emu.grasscutter.utils.JsonUtils;
import java.nio.file.*;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class BannerConfigTest {
    @TempDir Path directory;

    @BeforeAll
    static void configuration() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
    }

    @Test
    void migrationPreservesRowsStatesAndReservesExplicitIds() throws Exception {
        var raw =
                JsonParser.parseString(
                                "[{\"scheduleId\":1000,\"disabled\":false},"
                                        + "{\"disabled\":false,\"rateUpItems5\":[1022]},"
                                        + "{\"disabled\":true,\"prefabPath\":\"old\"},{\"scheduleId\":50000,\"disabled\":true}]")
                        .getAsJsonArray();
        var file = directory.resolve("Banners.json");
        Files.writeString(file, raw.toString());
        var rows = BannerConfig.load(file);
        assertEquals(1000, rows.get(0).getAsJsonObject().get("scheduleId").getAsInt());
        assertEquals(1001, rows.get(1).getAsJsonObject().get("scheduleId").getAsInt());
        assertEquals(50001, rows.get(2).getAsJsonObject().get("scheduleId").getAsInt());
        assertTrue(rows.get(2).getAsJsonObject().get("disabled").getAsBoolean());
        assertEquals(
                raw.get(1).getAsJsonObject().get("rateUpItems5"),
                rows.get(1).getAsJsonObject().get("rateUpItems5"));
        assertEquals(rows, BannerConfig.load(file));
        try (var backups = Files.list(directory)) {
            var backup =
                    backups
                            .filter(p -> p.getFileName().toString().startsWith("Banners.before-id-migration-"))
                            .toList();
            assertEquals(1, backup.size());
            assertEquals(raw, JsonParser.parseString(Files.readString(backup.get(0))));
        }
    }

    @Test
    void bundledDefaultsGainDistinctPersistedIdsWithoutEnablingTemplates() throws Exception {
        var rows = BannerConfig.load(directory.resolve("Banners.json"));
        var ids =
                StreamSupport.stream(rows.spliterator(), false)
                        .map(e -> e.getAsJsonObject().get("scheduleId").getAsInt())
                        .toList();
        assertEquals(ids.size(), ids.stream().distinct().count());
        assertTrue(ids.stream().allMatch(id -> id >= 0));
        assertTrue(rows.size() > 80);
        assertEquals(rows, BannerConfig.load(directory.resolve("Banners.json")));
    }

    @Test
    void duplicateIdsAreRejectedWithoutChangingTheFile() throws Exception {
        var file = directory.resolve("Banners.json");
        String original = "[{\"scheduleId\":803},{\"scheduleId\":803}]";
        Files.writeString(file, original);
        assertThrows(IllegalArgumentException.class, () -> BannerConfig.load(file));
        assertEquals(original, Files.readString(file));
    }

    @Test
    void rerunDurationIsBoundedIntegralAndDefaultsToThirtyDays() {
        long now = 1790810000;
        assertEquals(now + 30 * 86400L, BannerConfig.rerunEnd(new JsonObject(), now));
        var r = new JsonObject();
        r.addProperty("durationDays", 7);
        assertEquals(now + 7 * 86400L, BannerConfig.rerunEnd(r, now));
        for (double days : new double[] {0, -1, 366, 1.5}) {
            r.addProperty("durationDays", days);
            assertThrows(IllegalArgumentException.class, () -> BannerConfig.rerunEnd(r, now));
        }
        r.addProperty("durationDays", 365);
        assertThrows(
                IllegalArgumentException.class, () -> BannerConfig.rerunEnd(r, Integer.MAX_VALUE - 1));
    }

    @Test
    void expiryMustBeFutureAndAfterScheduledStart() {
        var r = new JsonObject();
        r.addProperty("endTime", 2000);
        assertEquals(2000, BannerConfig.requestedEnd(r, 1000, 1500));
        assertThrows(IllegalArgumentException.class, () -> BannerConfig.requestedEnd(r, 2000, 0));
        assertThrows(IllegalArgumentException.class, () -> BannerConfig.requestedEnd(r, 1000, 2000));
        r.addProperty("endTime", 2147483648L);
        assertThrows(IllegalArgumentException.class, () -> BannerConfig.requestedEnd(r, 1000, 0));
    }

    @Test
    void everyBannerIncludingStandardExpiresAtItsDeadline() {
        for (String type : new String[] {"STANDARD", "CHARACTER", "WEAPON"}) {
            var banner =
                    JsonUtils.decode(
                            "{\"bannerType\":\"" + type + "\",\"beginTime\":1000,\"endTime\":2000}",
                            GachaBanner.class);
            assertFalse(banner.isActive(999));
            assertTrue(banner.isActive(1000));
            assertTrue(banner.isActive(1999));
            assertFalse(banner.isActive(2000));
        }
    }
}
