package io.grasscutter;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.Descriptors.FileDescriptor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Ensures every recovered schema can initialize its generated Java descriptor. */
public final class RecoveredProtoDescriptorTest {
    private static final Pattern OUTER_CLASS = Pattern.compile("option java_outer_classname = \"([^\"]+)\";");

    @Test
    public void recoveredDescriptorsLoad() throws Exception {
        int checked = 0;
        try (var files = Files.list(Path.of("src/main/proto"))) {
            for (var path : files.filter(p -> p.toString().endsWith(".proto")).toList()) {
                var source = Files.readString(path);
                if (!source.startsWith("// Recovered from ")
                        && !source.startsWith("// 7.1 ")
                        && !source.startsWith("// Verified 7.1 ")) continue;
                var match = OUTER_CLASS.matcher(source);
                if (!match.find()) throw new IllegalStateException("Missing Java class in " + path);
                var name = match.group(1);
                var type = Class.forName("emu.grasscutter.net.proto." + name);
                var descriptor = (FileDescriptor) type.getMethod("getDescriptor").invoke(null);
                assertNotNull(descriptor, name);
                checked++;
            }
        }
        // Recovery sources may be replaced by verified native schemas; keep checking both origins.
        assertTrue(checked >= 818, "Unexpectedly few recovered/native descriptors: " + checked);
    }
}
