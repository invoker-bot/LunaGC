package io.grasscutter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonParser;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.utils.JsonUtils;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class MainQuestDataParsingTest {
    @Test
    void acceptsUnsigned64BitPreloadLuaHash() {
        var json = JsonParser.parseString(
                "{\"id\":1008,\"preloadLuaList\":[14457059026087496718]}");

        var quest = JsonUtils.decode(json, MainQuestData.class);

        assertEquals(1008, quest.getId());
        assertEquals(new BigInteger("14457059026087496718"), quest.getPreloadLuaList()[0]);
    }
}
