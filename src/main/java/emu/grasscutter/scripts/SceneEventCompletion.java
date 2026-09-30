package emu.grasscutter.scripts;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/** Completion policy for a queued Lua callback that may need the scene monitor. */
public final class SceneEventCompletion {
    private SceneEventCompletion() {}

    public static void await(Future<?> event, Object scene) throws InterruptedException, ExecutionException {
        // An activity Lua action may kill monsters while it holds this monitor. Its queued death
        // callback needs the same monitor, so let it run after the outer action releases the scene.
        if (!Thread.holdsLock(scene)) event.get();
    }
}
