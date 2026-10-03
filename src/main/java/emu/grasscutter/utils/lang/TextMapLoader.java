package emu.grasscutter.utils.lang;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import it.unimi.dsi.fastutil.ints.*;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;

/** Reads only requested strings, including optional newer and split resource dumps. */
final class TextMapLoader {
    private static final int HASH_DRIFT = 512;
    private static final JsonArray NAME_FALLBACKS = readNameFallbacks();

    private static JsonArray readNameFallbacks() {
        try (var stream =
                TextMapLoader.class.getResourceAsStream("/languages/game-name-fallbacks.json")) {
            if (stream == null) throw new IOException("Missing game name fallbacks");
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject()
                    .getAsJsonArray("entries");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    static Int2ObjectMap<String> load(Path directory, String language, IntSet hashes)
            throws IOException {
        var wanted = new IntOpenHashSet(hashes);
        for (int hash : hashes.toIntArray()) {
            wanted.add(hash + HASH_DRIFT);
            wanted.add(hash - HASH_DRIFT);
        }
        // New playable avatars sometimes reuse a story name under a different hash. Request that
        // name in every language rather than falling back to the English internal icon name.
        for (var entry : NAME_FALLBACKS) {
            var row = entry.getAsJsonObject();
            if (!hashes.contains((int) row.get("hash").getAsLong()) || !row.has("sourceHash")) continue;
            int source = (int) row.get("sourceHash").getAsLong();
            wanted.add(source);
            wanted.add(source + HASH_DRIFT);
            wanted.add(source - HASH_DRIFT);
        }
        var files = new ArrayList<Path>();
        var base = directory.resolve("TextMap" + language + ".json");
        if (Files.isRegularFile(base)) {
            files.add(base);
        } else {
            try (var chunks = Files.list(directory)) {
                chunks
                        .filter(
                                p -> p.getFileName().toString().matches("TextMap" + language + "_[0-9]+\\.json"))
                        .sorted(Comparator.comparing(Path::toString))
                        .forEach(files::add);
            }
        }
        var medium = directory.resolve("TextMap_Medium" + language + ".json");
        if (Files.isRegularFile(medium)) files.add(medium);
        if (files.isEmpty()) throw new IOException("No text maps for " + language);
        var strings = new Int2ObjectOpenHashMap<String>();
        for (var file : files) {
            try (var reader = new JsonReader(Files.newBufferedReader(file, StandardCharsets.UTF_8))) {
                reader.beginObject();
                while (reader.hasNext()) {
                    int hash = (int) Long.parseUnsignedLong(reader.nextName());
                    if (!wanted.contains(hash)) {
                        reader.skipValue();
                        continue;
                    }
                    var text = reader.nextString();
                    if (!text.isBlank() && !text.startsWith("[N/A]")) strings.put(hash, text);
                }
                reader.endObject();
            }
        }
        for (var entry : NAME_FALLBACKS) {
            var row = entry.getAsJsonObject();
            int hash = (int) row.get("hash").getAsLong();
            // Exact and drifted names in newer resource exports always take precedence.
            if (!hashes.contains(hash) || resolve(strings, hash) != null) continue;
            String name =
                    row.has("sourceHash") ? resolve(strings, (int) row.get("sourceHash").getAsLong()) : null;
            if (name == null && row.has("names")) {
                var names = row.getAsJsonObject("names");
                if (names.has(language)) name = names.get(language).getAsString();
            }
            if (name != null && !name.isBlank()) strings.put(hash, name);
        }
        return strings;
    }

    /** Keep exact matches first; older exports used +512, newer exports also use -512. */
    static String resolve(Int2ObjectMap<String> strings, int hash) {
        var text = strings.get(hash);
        if (text == null) text = strings.get(hash + HASH_DRIFT);
        if (text == null) text = strings.get(hash - HASH_DRIFT);
        return text;
    }
}
