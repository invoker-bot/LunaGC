package emu.grasscutter.server.http.documentation;

import static emu.grasscutter.config.Configuration.DOCUMENT_LANGUAGE;

import emu.grasscutter.data.ResourceLoader;
import emu.grasscutter.tools.Tools;
import emu.grasscutter.utils.lang.Language;
import io.javalin.http.*;
import java.util.List;

final class GachaMappingRequestHandler implements DocumentationHandler {
    private volatile List<String> gachaJsons;

    @Override
    public void handle(Context ctx) {
        if (!ResourceLoader.isLoadedAll()) {
            ctx.status(503).result("Resources are still loading");
            return;
        }

        var jsons = gachaJsons;
        if (jsons == null) {
            synchronized (this) {
                if (gachaJsons == null) gachaJsons = Tools.createGachaMappingJsons();
                jsons = gachaJsons;
            }
        }

        final int langIdx =
                Language.TextStrings.MAP_LANGUAGES.getOrDefault(
                        DOCUMENT_LANGUAGE,
                        0); // TODO: This should really be based off the client language somehow
        ctx.contentType(ContentType.APPLICATION_JSON).result(jsons.get(langIdx));
    }
}
