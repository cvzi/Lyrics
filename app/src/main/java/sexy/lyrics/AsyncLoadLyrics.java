package sexy.lyrics;

import android.os.Handler;
import android.os.Looper;

import androidx.core.util.Consumer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AsyncLoadLyrics {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static ExecutorService IO = Executors.newSingleThreadExecutor();

    private static synchronized ExecutorService getExecutor() {
        if (IO == null || IO.isShutdown() || IO.isTerminated()) {
            IO = Executors.newSingleThreadExecutor();
        }
        return IO;
    }

    public static void run(
            LyricsViewActivity activity,
            String[] params,
            Consumer<Lyrics> onSuccess,
            Consumer<Throwable> onError
    ) {
        getExecutor().execute(() -> {
            try {
                Lyrics result = activity.loadLyricsBlocking(params);
                MAIN.post(() -> onSuccess.accept(result));
            } catch (Throwable t) {
                if (onError != null) MAIN.post(() -> onError.accept(t));
            }
        });
    }

    public static synchronized void shutdown() {
        if (IO != null && !IO.isShutdown()) {
            IO.shutdownNow();
        }
        IO = null;
    }
}