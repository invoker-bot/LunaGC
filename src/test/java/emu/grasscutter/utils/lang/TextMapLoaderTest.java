package emu.grasscutter.utils.lang;

import static org.junit.jupiter.api.Assertions.*;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TextMapLoaderTest {
    @TempDir Path directory;

    @Test
    void combinesMediumWithBaseAndDecodesJson() throws Exception {
        Files.writeString(
                directory.resolve("TextMapCHS.json"),
                "{\"1000\":\"旧名称\",\"1512\":\"错误偏移\",\"3000\":\"引号\\\"和\\n换行\",\"9999\":\"无关\"}");
        Files.writeString(
                directory.resolve("TextMap_MediumCHS.json"),
                "{\"1000\":\"新名称\",\"1488\":\"资金\",\"4294967295\":\"无符号\"}");
        var strings =
                TextMapLoader.load(directory, "CHS", new IntOpenHashSet(new int[] {1000, 2000, 3000, -1}));
        assertEquals("新名称", TextMapLoader.resolve(strings, 1000));
        assertEquals("资金", TextMapLoader.resolve(strings, 2000));
        assertEquals("引号\"和\n换行", TextMapLoader.resolve(strings, 3000));
        assertEquals("无符号", TextMapLoader.resolve(strings, -1));
        assertFalse(strings.containsKey(9999));
        assertNull(TextMapLoader.resolve(strings, 9000));
    }

    @Test
    void supportsSplitDumpAndDoesNotReplaceNameWithPlaceholder() throws Exception {
        Files.writeString(directory.resolve("TextMapCHS_0.json"), "{\"100\":\"名称\"}");
        Files.writeString(directory.resolve("TextMapCHS_1.json"), "{\"200\":\"名称二\"}");
        Files.writeString(
                directory.resolve("TextMap_MediumCHS.json"), "{\"100\":\"[N/A] 100\",\"200\":\"\"}");
        var strings = TextMapLoader.load(directory, "CHS", new IntOpenHashSet(new int[] {100, 200}));
        assertEquals("名称", TextMapLoader.resolve(strings, 100));
        assertEquals("名称二", TextMapLoader.resolve(strings, 200));
    }

    @Test
    void missingOptionalMediumIsAccepted() throws Exception {
        Files.writeString(directory.resolve("TextMapEN.json"), "{\"100\":\"Name\"}");
        assertEquals(
                "Name",
                TextMapLoader.resolve(
                        TextMapLoader.load(directory, "EN", new IntOpenHashSet(new int[] {100})), 100));
    }

    @Test
    void resolvesReportedCurrenciesFromActualResources() throws Exception {
        int[] hashes = {
            2108702780, 1592729508, 1241621348, 771415300, 979907428, 1447233620, 805934140
        };
        String[] names = {"欢乐零食", "资金", "行动点", "推想记录", "绘夏热度", "军团士气", "完备进度"};
        var strings =
                TextMapLoader.load(Path.of("resources/TextMap"), "CHS", new IntOpenHashSet(hashes));
        for (int i = 0; i < hashes.length; i++)
            assertEquals(names[i], TextMapLoader.resolve(strings, hashes[i]));
    }

    @Test
    void resolvesVersion71CharactersCardsAndFeaturedWeaponsFromActualResources() throws Exception {
        long[] hashes = {
            3992521194L,
            3775698260L,
            1692132714L,
            4172378124L,
            4086029419L,
            2677592067L,
            1359826123L,
            2998374891L,
            3763051755L
        };
        String[] names = {"薇斯纳", "薇斯纳", "沃雅妮莎", "沃雅妮莎", "蝶变", "漩流颂歌", "新枝", "凝雪沉心", "柔风游弦"};
        var wanted = new IntOpenHashSet();
        for (long hash : hashes) wanted.add((int) hash);
        var strings = TextMapLoader.load(Path.of("resources/TextMap"), "CHS", wanted);
        for (int i = 0; i < hashes.length; i++)
            assertEquals(names[i], TextMapLoader.resolve(strings, (int) hashes[i]));
        var english = TextMapLoader.load(Path.of("resources/TextMap"), "EN", wanted);
        assertEquals("Vesna", TextMapLoader.resolve(english, (int) hashes[0]));
        assertEquals("Vodyanitsa", TextMapLoader.resolve(english, (int) hashes[2]));
        assertEquals("Beyond the Chrysalis", TextMapLoader.resolve(english, (int) hashes[4]));
    }

    @Test
    void supplementalNamesYieldToExactAndDriftedResourceNames() throws Exception {
        Files.writeString(
                directory.resolve("TextMapCHS.json"),
                "{\"3992521194\":\"资源中的薇斯纳\",\"1692132202\":\"新版角色名称\",\"4086029419\":\"新版武器名称\",\"921623202\":\"旧称\"}");
        var hashes = new IntOpenHashSet(new int[] {(int) 3992521194L, 1692132714, (int) 4086029419L});
        var strings = TextMapLoader.load(directory, "CHS", hashes);
        assertEquals("资源中的薇斯纳", TextMapLoader.resolve(strings, (int) 3992521194L));
        assertEquals("新版角色名称", TextMapLoader.resolve(strings, 1692132714));
        assertEquals("新版武器名称", TextMapLoader.resolve(strings, (int) 4086029419L));
        assertNull(
                TextMapLoader.resolve(strings, (int) 2677592067L),
                "Only requested hashes should be supplemented");
    }
}
