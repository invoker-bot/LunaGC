package emu.grasscutter.net.packet;

import java.lang.annotation.*;

/**
 * Marks a handler that deliberately sends nothing back, so developer mode stops filing it as an
 * unimplemented request.
 *
 * <p>{@link emu.grasscutter.server.dev.UnimplementedRequestReporter} files a request as NO_RESPONSE
 * when its handler ran and not one packet left the session - the signature of a half-implemented
 * feature whose early returns never answer the client. But some requests are the client reporting
 * a state the server has no stake in: the anim hash it switched to, the position it reconciled to,
 * the stamina step it took. Those handlers exist only to close the harvest loop, and their silence
 * is correct - so without this annotation they would reappear at the top of every session's report
 * and bury the gaps that are still real.
 *
 * <p>The annotation is what makes the intent legible: the opcode has a handler, the handler has no
 * proto, and the missing reply is a decision rather than an oversight.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface NoResponseExpected {
}
