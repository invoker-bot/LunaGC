package emu.grasscutter.server.http.handlers;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import emu.grasscutter.game.shop.FreeStore;
import emu.grasscutter.game.shop.ShopCatalog;
import emu.grasscutter.server.http.HttpServer;
import io.javalin.Javalin;
import io.javalin.json.JavalinGson;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorePriceRoutesTest {
    @TempDir Path dir;

    @Test
    void bothSdkPriceApiVersionsResolveTheMonthlyCardTierForGetAndPost() throws Exception {
        Class.forName("emu.grasscutter.Grasscutter");
        var store = new FreeStore(new ShopCatalog(dir.resolve("shop.json")));
        store.load();
        var app = Javalin.create(config -> config.jsonMapper(new JavalinGson()));
        new AnnouncementsHandler(() -> store).applyRoutes(app);
        // The production fallback returns an empty success body for unhandled SDK routes.
        new HttpServer.UnhandledRequestRouter().applyRoutes(app);
        app.start(0);
        try {
            var client =
                    HttpClient.newBuilder()
                            .version(HttpClient.Version.HTTP_1_1)
                            .connectTimeout(Duration.ofSeconds(5))
                            .build();
            for (var region : List.of("hk4e_cn", "hk4e_global")) {
                String currency = region.equals("hk4e_cn") ? "CNY" : "USD";
                for (var api : List.of("listPriceTier", "listPriceTierV2")) {
                    for (var method : List.of("GET", "POST")) {
                        var request =
                                HttpRequest.newBuilder(
                                                URI.create(
                                                        "http://127.0.0.1:"
                                                                + app.port()
                                                                + "/"
                                                                + region
                                                                + "/mdk/shopwindow/shopwindow/"
                                                                + api
                                                                + "?game_biz="
                                                                + region
                                                                + "&country=CN&currency="
                                                                + currency))
                                        .timeout(Duration.ofSeconds(5))
                                        .method(method, HttpRequest.BodyPublishers.noBody())
                                        .build();
                        var reply = client.send(request, HttpResponse.BodyHandlers.ofString());
                        assertEquals(200, reply.statusCode());
                        var json = JsonParser.parseString(reply.body()).getAsJsonObject();
                        assertEquals(0, json.get("retcode").getAsInt());
                        var data = json.getAsJsonObject("data");
                        assertTrue(data.has("tiers"), api + " must not return the empty fallback body");
                        assertFalse(data.get("price_tier_version").getAsString().isEmpty());
                        var moon =
                                StreamSupport.stream(data.getAsJsonArray("tiers").spliterator(), false)
                                        .map(row -> row.getAsJsonObject())
                                        .filter(row -> row.get("tier_id").getAsString().equals("Tier_5"))
                                        .findFirst()
                                        .orElseThrow();
                        var price = moon.getAsJsonArray("t_price").get(0).getAsJsonObject();
                        assertEquals(1, price.get("enable").getAsInt());
                        assertEquals("0", price.get("price").getAsString());
                        assertEquals(currency, price.get("currency").getAsString());
                    }
                }
            }
        } finally {
            app.stop();
        }
    }
}
