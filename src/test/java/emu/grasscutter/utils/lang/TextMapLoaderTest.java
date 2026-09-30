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
}
