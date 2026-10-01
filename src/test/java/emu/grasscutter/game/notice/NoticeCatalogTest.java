package emu.grasscutter.game.notice;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NoticeCatalogTest {
    @TempDir Path dir;
    final Clock clock = Clock.fixed(Instant.ofEpochSecond(1800000000), ZoneOffset.UTC);

    NoticeCatalog.Notice notice() {
        return new NoticeCatalog.Notice(
                0, "更新公告", "内容 <script>alert(1)</script>", 2, false, 1799999990, 1800000100);
    }

    @Test
    void draftsPublishWithdrawAndReloadWithStableIds() throws Exception {
        var path = dir.resolve("Notices.json");
        var catalog = new NoticeCatalog(path, clock);
        int id = catalog.edit("save", notice()).id();
        assertTrue(catalog.active().stream().noneMatch(n -> n.id() == id));
        catalog.edit("publish", new NoticeCatalog.Notice(id, null, null, 0, false, 0, 0));
        assertTrue(new NoticeCatalog(path, clock).active().stream().anyMatch(n -> n.id() == id));
        catalog.edit("withdraw", new NoticeCatalog.Notice(id, null, null, 0, false, 0, 0));
        assertTrue(new NoticeCatalog(path, clock).active().stream().noneMatch(n -> n.id() == id));
    }

    @Test
    void scheduledAndExpiredNoticesStayOutOfClientLists() throws Exception {
        var catalog = new NoticeCatalog(dir.resolve("notices.json"), clock);
        catalog.edit("save", new NoticeCatalog.Notice(0, "未来", "内容", 1, true, 1800000001, 1800000100));
        catalog.edit("save", new NoticeCatalog.Notice(0, "已过期", "内容", 1, true, 1799999000, 1800000000));
        assertTrue(catalog.active().stream().noneMatch(n -> n.category() == 1));
    }

    @Test
    void failedWriteDoesNotPublishAndInvalidContentIsRejected() throws Exception {
        var path = dir.resolve("blocked");
        Files.createDirectory(path);
        var catalog = new NoticeCatalog(dir.resolve("notices.json"), clock);
        assertThrows(
                IllegalArgumentException.class,
                () -> catalog.edit("save", new NoticeCatalog.Notice(0, "", "内容", 2, true, 0, 1800000100)));
        int before = catalog.all().size();
        Files.createDirectory(dir.resolve("notices.json"));
        assertThrows(java.io.IOException.class, () -> catalog.edit("save", notice()));
        assertEquals(before, catalog.all().size());
    }
}
