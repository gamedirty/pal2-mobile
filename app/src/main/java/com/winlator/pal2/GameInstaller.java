package com.winlator.pal2;

import android.content.Context;

import com.winlator.core.Callback;
import com.winlator.core.FileUtils;
import com.winlator.core.TarCompressorUtils;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public abstract class GameInstaller {
    public static final String GAME_EXE = "Pal2.exe";
    public static final String ASSET_FILE = "pal2-game.tzst";

    public interface ProgressCallback {
        void onProgress(int percent, long extractedBytes, long totalBytes);
    }

    public static File getGameDir(Context context) {
        // Internal storage: avoids the sdcard FUSE daemon (a ColorOS MediaProvider bug crashes
        // on opendir bursts during wine drive enumeration) and gives the game faster IO.
        return new File(context.getFilesDir(), "pal2-game");
    }

    public static boolean isInstalled(Context context) {
        File gameDir = getGameDir(context);
        return new File(gameDir, GAME_EXE).isFile() && new File(gameDir, "PicLib").isDirectory();
    }

    public static void installAsync(final Context context, final ProgressCallback progressCallback, final Callback<Boolean> callback) {
        Executors.newSingleThreadExecutor().execute(() -> {
            final File target = getGameDir(context);
            FileUtils.delete(target);
            if (!target.mkdirs()) {
                callback.call(false);
                return;
            }

            final long contentLength = TarCompressorUtils.getContentLength(TarCompressorUtils.Type.ZSTD, context, ASSET_FILE, target);
            if (contentLength <= 0) {
                callback.call(false);
                return;
            }

            AtomicLong totalSizeRef = new AtomicLong();
            boolean success = TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, context, ASSET_FILE, target, (file, size) -> {
                if (size > 0) {
                    long totalSize = totalSizeRef.addAndGet(size);
                    if (progressCallback != null) {
                        final long done = totalSize;
                        progressCallback.onProgress((int)((done * 100) / contentLength), done, contentLength);
                    }
                }
                return file;
            });

            callback.call(success && isInstalled(context));
        });
    }
}
