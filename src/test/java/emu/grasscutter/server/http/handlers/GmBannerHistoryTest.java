package emu.grasscutter.server.http.handlers;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GmBannerHistoryTest {
    @TempDir Path directory;

    @BeforeAll
    static void initializeConfiguration() throws Exception {
        // FileUtils and Grasscutter refer to each other during static initialization.
        Class.forName("emu.grasscutter.Grasscutter");
    }

    private Path table(String json) throws Exception {
        Path file = directory.resolve("Banners.json");
        Files.writeString(file, json);
        return file;
    }

    @Test
    void archiveRemainsAvailableWithoutGitCheckout() throws Exception {
        Path file = table("[]");
        var history = GmHandler.loadBannerHistory(file);
        var archives = history.stream().filter(GmHandler.HistoryTable::archive).toList();
        assertTrue(archives.size() >= 52);
        assertTrue(archives.stream().anyMatch(t -> t.subject().endsWith("Version 1.0")));
        assertTrue(archives.stream().anyMatch(t -> t.subject().endsWith("Version 7.0")));
        assertTrue(archives.stream().allMatch(t -> t.commit().equals("archive")));
        assertTrue(archives.stream().mapToInt(t -> t.banners().size()).sum() >= 290);
        assertEquals("[]", Files.readString(file));
    }

    @Test
    void freshRuntimeWithoutJsonStillListsDefaultsAndArchive() throws Exception {
        Path file = directory.resolve("Banners.json");
        var history = GmHandler.loadBannerHistory(file);
        assertFalse(history.get(0).banners().isEmpty());
        assertTrue(history.stream().filter(GmHandler.HistoryTable::archive).count() >= 52);
        assertFalse(Files.exists(file));
    }

    @Test
    void localArchiveTemplateOverridesBundledRowWithoutDuplicates() throws Exception {
        Path file = table("[]");
        var archive = GmHandler.readArchiveBannerTable(file);
        var template = archive.get(0).getAsJsonObject().deepCopy();
        template.addProperty("comment", "Local official archive Version 1.0");
        template.addProperty("costItemId", 224);
        Files.writeString(file, "[" + template + "]");
        var merged = GmHandler.readArchiveBannerTable(file);
        assertEquals(archive.size(), merged.size());
        var selected =
                StreamSupport.stream(merged.spliterator(), false)
                        .map(e -> e.getAsJsonObject())
                        .filter(e -> e.get("scheduleId").equals(template.get("scheduleId")))
                        .toList();
        assertEquals(1, selected.size());
        assertEquals(224, selected.get(0).get("costItemId").getAsInt());
    }

    @Test
    void enabledRerunDoesNotReplaceItsHistoricalTemplate() throws Exception {
        Path file = table("[]");
        var original = GmHandler.readArchiveBannerTable(file).get(0).getAsJsonObject().deepCopy();
        var live = original.deepCopy();
        live.addProperty("disabled", false);
        live.addProperty("beginTime", 123);
        Files.writeString(file, "[" + live + "]");
        var history = GmHandler.loadBannerHistory(file);
        assertEquals(1, history.get(0).banners().size());
        assertFalse(history.get(0).archive());
        var archive = GmHandler.readArchiveBannerTable(file);
        var archived =
                StreamSupport.stream(archive.spliterator(), false)
                        .map(e -> e.getAsJsonObject())
                        .filter(e -> e.get("scheduleId").equals(original.get("scheduleId")))
                        .findFirst()
                        .orElseThrow();
        assertEquals(JsonParser.parseString(original.toString()), archived);
    }

    @Test
    void version71ArchiveIncludesBothNewCharactersAndTheirWeaponWish() throws Exception {
        Path file = table("[]");
        var revision =
                GmHandler.loadBannerHistory(file).stream()
                        .filter(t -> t.archive() && t.subject().endsWith("Version 7.1"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(3, revision.banners().size());
        var vesna =
                revision.banners().stream()
                        .filter(b -> b.getScheduleId() == 160100)
                        .findFirst()
                        .orElseThrow();
        var vodyanitsa =
                revision.banners().stream()
                        .filter(b -> b.getScheduleId() == 160101)
                        .findFirst()
                        .orElseThrow();
        var weapons =
                revision.banners().stream()
                        .filter(b -> b.getScheduleId() == 160102)
                        .findFirst()
                        .orElseThrow();
        assertArrayEquals(new int[] {4143}, vesna.getRateUpItems5());
        assertArrayEquals(new int[] {4140}, vodyanitsa.getRateUpItems5());
        assertEquals("首期", vesna.getPhase());
        assertEquals("首期", vodyanitsa.getPhase());
        assertEquals("首期", weapons.getPhase());
        assertArrayEquals(new int[] {1039, 1076, 1036}, vesna.getRateUpItems4());
        assertArrayEquals(vesna.getRateUpItems4(), vodyanitsa.getRateUpItems4());
        assertArrayEquals(new int[] {11522, 14524}, weapons.getRateUpItems5());
        assertArrayEquals(new int[] {11437, 14437, 15437, 12401, 13401}, weapons.getRateUpItems4());
        assertTrue(revision.banners().stream().allMatch(b -> b.isDisabled() && b.getCostItem() == 223));
        assertEquals("[]", Files.readString(file), "Collecting a new wish must not activate it");
    }
}
