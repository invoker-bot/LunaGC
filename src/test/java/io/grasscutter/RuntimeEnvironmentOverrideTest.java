package io.grasscutter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import emu.grasscutter.config.ConfigContainer;
import org.junit.jupiter.api.Test;

class RuntimeEnvironmentOverrideTest {
    @Test
    void dockerOverridesBothDatabaseConnectionsAndAdvertisedAddresses() {
        var config = new ConfigContainer();
        config.databaseInfo.server.connectionUri = "mongodb://dispatch-old:27017";
        config.databaseInfo.game.connectionUri = "mongodb://game-old:27017";
        config.server.http.accessAddress = "dispatch-old.example";
        config.server.game.accessAddress = "game-old.example";

        config.applyDatabaseUriOverride(" mongodb://mongo:27017 ");
        config.applyPublicAddressOverride(" game.example.com ");

        assertEquals("mongodb://mongo:27017", config.databaseInfo.server.connectionUri);
        assertEquals("mongodb://mongo:27017", config.databaseInfo.game.connectionUri);
        assertEquals("game.example.com", config.server.http.accessAddress);
        assertEquals("game.example.com", config.server.game.accessAddress);
    }

    @Test
    void missingOverridesKeepSavedConfiguration() {
        var config = new ConfigContainer();
        config.databaseInfo.game.connectionUri = "mongodb://saved:27017";
        config.server.game.accessAddress = "saved.example.com";

        config.applyDatabaseUriOverride(null);
        config.applyPublicAddressOverride(" ");

        assertEquals("mongodb://saved:27017", config.databaseInfo.game.connectionUri);
        assertEquals("saved.example.com", config.server.game.accessAddress);
    }
}
