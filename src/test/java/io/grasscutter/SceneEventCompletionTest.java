package io.grasscutter;

import static org.junit.jupiter.api.Assertions.*;

import emu.grasscutter.scripts.SceneEventCompletion;
import java.time.Duration;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class SceneEventCompletionTest {
    @Test void deathDuringLuaCleanupQueuesItsCallbackUntilTheSceneMonitorIsReleased() throws Exception {
        var scene = new Object();
        var executor = Executors.newSingleThreadExecutor();
        var callbackStarted = new CountDownLatch(1);
        var callbackFinished = new CountDownLatch(1);
        try {
            assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
                synchronized (scene) {
                    var deathEvent = executor.submit(() -> {
                        callbackStarted.countDown();
                        synchronized (scene) { callbackFinished.countDown(); }
                    });
                    assertTrue(callbackStarted.await(1, TimeUnit.SECONDS));
                    SceneEventCompletion.await(deathEvent, scene);
                    assertEquals(1, callbackFinished.getCount());
                }
                assertTrue(callbackFinished.await(1, TimeUnit.SECONDS));
            });
        } finally { executor.shutdownNow(); }
    }

    @Test void ordinaryDeathWaitsForItsCallbackAndPropagatesFailure() throws Exception {
        var event = new CompletableFuture<Void>();
        var executor = Executors.newSingleThreadExecutor();
        var waiting = new CountDownLatch(1);
        try {
            var caller = executor.submit(() -> {
                waiting.countDown();
                SceneEventCompletion.await(event, new Object());
                return null;
            });
            assertTrue(waiting.await(1, TimeUnit.SECONDS));
            assertFalse(caller.isDone());
            event.complete(null);
            caller.get(1, TimeUnit.SECONDS);
            var failed = CompletableFuture.failedFuture(new IllegalStateException("Lua failure"));
            assertThrows(ExecutionException.class, () -> SceneEventCompletion.await(failed, new Object()));
        } finally { executor.shutdownNow(); }
    }
}
