package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.activity.aster.*;
import emu.grasscutter.scripts.ScriptLoader;
import emu.grasscutter.scripts.data.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.HexFormat;
import org.junit.jupiter.api.*;

class AsterScriptsTest {
    @BeforeAll
    static void scripts() throws Exception {
        Grasscutter.getConfig();
        if (ScriptLoader.getEngine() == null) ScriptLoader.init();
    }

    @Test
    void actualMapBlocksLoadAll118OriginalPointsWithSeparateWorldBindings() {
        var meta = SceneMeta.of(3);
        assertNotNull(meta);
        int count = 0;
        for (var area : AsterFragments.areas().values()) {
            var first = AsterSceneController.definition(area, meta.blocks.values()).load(3);
            var second = AsterSceneController.definition(area, meta.blocks.values()).load(3);
            assertNotSame(first, second);
            assertNotSame(first.getBindings(), second.getBindings());
            assertNotNull(first.init_config);
            assertNotNull(first.getSuiteByIndex(1));
            var points = new HashSet<Integer>();
            for (var gadget : first.getSuiteByIndex(1).sceneGadgets) {
                assertEquals(9127, gadget.point_type);
                assertEquals(70500000, gadget.gadget_id);
                points.add(gadget.config_id);
            }
            assertEquals(area.configIds(), points);
            count += points.size();
            assertFalse(
                    meta.blocks.get(first.block_id).groups != null
                            && meta.blocks.get(first.block_id).groups.containsKey(first.id),
                    "Activity mounting cannot change shared map metadata");
        }
        assertEquals(118, count);
        assertThrows(
                IllegalStateException.class,
                () ->
                        AsterSceneController.definition(
                                AsterFragments.areas().values().iterator().next(), List.of()));
    }

    @Test
    void bundledSourcesMatchTheirPinnedRecoveryHashes() throws Exception {
        var root = "/historical-scripts/";
        try (var stream = getClass().getResourceAsStream(root + "Activity/2001/sources.json")) {
            var entries =
                    new com.google.gson.JsonParser()
                            .parse(new java.io.InputStreamReader(stream))
                            .getAsJsonArray();
            assertEquals(6, entries.size());
            for (var entry : entries) {
                var row = entry.getAsJsonObject();
                try (var script = getClass().getResourceAsStream(root + row.get("path").getAsString())) {
                    assertNotNull(script);
                    assertEquals(
                            row.get("sha256").getAsString(),
                            HexFormat.of()
                                    .formatHex(MessageDigest.getInstance("SHA-256").digest(script.readAllBytes())));
                }
            }
        }
    }
}
