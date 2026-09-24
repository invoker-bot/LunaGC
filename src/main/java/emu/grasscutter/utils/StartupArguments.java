package emu.grasscutter.utils;

import static emu.grasscutter.config.Configuration.*;

import ch.qos.logback.classic.*;
import emu.grasscutter.*;
import emu.grasscutter.net.packet.PacketOpcodesUtils;
import emu.grasscutter.tools.Dumpers;
import java.util.*;
import java.util.function.Function;
import org.slf4j.LoggerFactory;

/** A parser for start-up arguments. */
public interface StartupArguments {
    /* A map of parameter -> argument handler. */
    Map<String, Function<String, Boolean>> argumentHandlers =
            new HashMap<>() {
                {
                    putAll(
                            // Map.of stops at ten key/value pairs and this table has more, so the
                            // entries are spelled out through ofEntries instead. The handlers are
                            // unchanged.
                            Map.ofEntries(
                                    Map.entry(
                                            "-dumppacketids",
                                            parameter -> {
                                                PacketOpcodesUtils.dumpPacketIds();
                                                return true;
                                            }),
                                    Map.entry("-version", StartupArguments::printVersion),
                                    Map.entry("-debug", StartupArguments::enableDebug),
                                    Map.entry("-dev", StartupArguments::enableDeveloperMode),
                                    Map.entry(
                                            "-lang",
                                            parameter -> {
                                                Grasscutter.setPreferredLanguage(parameter);
                                                return false;
                                            }),
                                    Map.entry(
                                            "-game",
                                            parameter -> {
                                                Grasscutter.setRunModeOverride(
                                                        Grasscutter.ServerRunMode.GAME_ONLY);
                                                return false;
                                            }),
                                    Map.entry(
                                            "-dispatch",
                                            parameter -> {
                                                Grasscutter.setRunModeOverride(
                                                        Grasscutter.ServerRunMode.DISPATCH_ONLY);
                                                return false;
                                            }),
                                    Map.entry(
                                            "-noconsole",
                                            parameter -> {
                                                Grasscutter.setNoConsole(true);
                                                return false;
                                            }),
                                    Map.entry(
                                            "-test",
                                            parameter -> {
                                                // Disable the console.
                                                SERVER.game.enableConsole = false;
                                                // Disable HTTP encryption.
                                                SERVER.http.encryption.useEncryption = false;
                                                return false;
                                            }),
                                    Map.entry("-dump", StartupArguments::dump),

                                    // Aliases.
                                    Map.entry("-v", StartupArguments::printVersion)));
                    putAll(
                            Map.of(
                                    "-debugall",
                                    parameter -> {
                                        StartupArguments.enableDebug("all");
                                        return false;
                                    }));
                }
            };

    /**
     * Parses the provided start-up arguments.
     *
     * @param args The application start-up arguments.
     * @return If the application should exit.
     */
    static boolean parse(String[] args) {
        boolean exitEarly = false;

        // Parse the arguments.
        for (var input : args) {
            var containsParameter = input.contains("=");

            var argument = containsParameter ? input.split("=")[0] : input;
            var handler = argumentHandlers.get(argument.toLowerCase());

            if (handler != null) {
                exitEarly |= handler.apply(containsParameter ? input.split("=")[1] : null);
            }
        }

        return exitEarly;
    }

    /**
     * Prints the server version.
     *
     * @param parameter Additional parameters.
     * @return True to exit early.
     */
    private static boolean printVersion(String parameter) {
        System.out.println("Grasscutter version: " + BuildConfig.VERSION + "-" + BuildConfig.GIT_HASH);
        return true;
    }

    /**
     * Enables debug logging.
     *
     * @param parameter Additional parameters.
     * @return False to continue execution.
     */
    private static boolean enableDebug(String parameter) {
        if (parameter != null && parameter.equals("all")) {
            // Override default debug configs
            applyDebugLoggingOverrides();

            // Log level to other third-party services
            Level loggerLevel = DEBUG_MODE_INFO.servicesLoggersLevel;
            // Change loggers to debug.
            ((Logger) LoggerFactory.getLogger("io.javalin")).setLevel(loggerLevel);
            ((Logger) LoggerFactory.getLogger("org.quartz")).setLevel(loggerLevel);
            ((Logger) LoggerFactory.getLogger("org.reflections")).setLevel(loggerLevel);
            ((Logger) LoggerFactory.getLogger("org.eclipse.jetty")).setLevel(loggerLevel);
            ((Logger) LoggerFactory.getLogger("org.mongodb.driver")).setLevel(loggerLevel);
        }

        // Set the main logger to debug.
        Grasscutter.getLogger().setLevel(DEBUG_MODE_INFO.serverLoggerLevel);
        Grasscutter.getLogger().debug("The logger is now running in debug mode.");
        // Set the server to debug.
        GameConstants.DEBUG = true;
        return false;
    }

    /**
     * Enables developer mode.
     *
     * <p>Distinct from {@link #enableDebug(String)}, which only sets log levels: developer mode
     * additionally arms the server-side instrumentation that watches for requests the server
     * probably does not implement. See
     * {@link emu.grasscutter.server.dev.UnimplementedRequestReporter}. Debug logging comes along
     * for free, because that reporter's output is useless without it.
     *
     * <p>Developer mode also applies the packet/request-logging overrides from {@code
     * server.debugMode}. This box is meant to be fully observable, but until it did, {@code
     * enableDebug} only wired them up for the '-debugall'/'-dev all' spellings -- a bare '-dev'
     * bound a {@code null} parameter, skipped that block, and left {@code server.game.logPackets}
     * at whatever {@code config.json} said (NONE), so not one RECV/SEND line was ever written while
     * everything else looked like debug was on. The third-party logger levels are intentionally
     * left alone here: that part of '-debugall' is noise on a server that answers HTTP all day.
     *
     * @param parameter Additional parameters (unused; '-dev all' behaves like '-dev').
     * @return False to continue execution.
     */
    private static boolean enableDeveloperMode(String parameter) {
        StartupArguments.enableDebug(parameter);

        // Wire up packet/dispatch logging from the debug configuration. enableDebug() only does
        // this for '-debugall', so a bare '-dev' left logPackets at NONE and every recv/send line
        // stayed invisible while the rest of the box read as fully in debug.
        applyDebugLoggingOverrides();

        GameConstants.DEVELOPER_MODE = true;
        Grasscutter.getLogger().info("Developer mode is enabled -- unimplemented requests will be reported.");
        return false;
    }

    /**
     * Copies the packet and request logging knobs from {@code server.debugMode} into the live
     * configuration.
     */
    private static void applyDebugLoggingOverrides() {
        GAME_INFO.isShowLoopPackets = DEBUG_MODE_INFO.isShowLoopPackets;
        GAME_INFO.isShowPacketPayload = DEBUG_MODE_INFO.isShowPacketPayload;
        GAME_INFO.logPackets = DEBUG_MODE_INFO.logPackets;
        DISPATCH_INFO.logRequests = DEBUG_MODE_INFO.logRequests;

        // The effective modes are only visible by reading them back here: server.game.logPackets
        // in config.json stays whatever it was, and these fields are what send()/handleReceive()
        // actually switch on. Without this line there is no way to tell from the log whether a
        // session is being recorded until the first client connects -- or, as it turned out,
        // discovers it is not.
        Grasscutter.getLogger()
                .info(
                        "Packet logging: {} (loop: {}, payload: {}), dispatch request logging: {}.",
                        GAME_INFO.logPackets,
                        GAME_INFO.isShowLoopPackets,
                        GAME_INFO.isShowPacketPayload,
                        DISPATCH_INFO.logRequests);
    }

    /**
     * Dumps the specified information.
     *
     * @param parameter The parameter to dump.
     * @return True to exit early.
     */
    private static boolean dump(String parameter) {
        // Parse the parameter.
        if (!parameter.contains(",")) {
            Grasscutter.getLogger().error("Dumper usage: -dump=<content>,<language>");
            return true;
        }

        var split = parameter.split(",");
        var content = split[0];
        var language = split[1];

        try {
            switch (content.toLowerCase()) {
                case "commands" -> Dumpers.dumpCommands(language);
                case "avatars" -> Dumpers.dumpAvatars(language);
                case "items" -> Dumpers.dumpItems(language);
                case "scenes" -> Dumpers.dumpScenes();
                case "entities" -> Dumpers.dumpEntities(language);
                case "quests" -> Dumpers.dumpQuests(language);
                case "areas" -> Dumpers.dumpAreas(language);
            }

            Grasscutter.getLogger().info("Finished dumping.");
        } catch (Exception exception) {
            Grasscutter.getLogger().error("Unable to complete dump.", exception);
        }

        return true;
    }
}
