package com.iqsoo.wechatguard;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;
import android.system.Os;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public final class WatcherService extends IWatcherService.Stub {
    private static final byte[] PNG_MAGIC = new byte[]{
            (byte)0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A
    };

    private final List<FileObserver> observers = new ArrayList<>();
    private final AtomicInteger replaceCount = new AtomicInteger();
    private final AtomicInteger eventCount = new AtomicInteger();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private volatile File master;
    private volatile boolean watching;
    private volatile String lastMessage = "未启动";
    private volatile String lastEvent = "无";

    public WatcherService() {}
    public WatcherService(Context ignored) {}

    @Override
    public synchronized boolean startWatching(String masterPath) {
        stopWatchingInternal();
        replaceCount.set(0);
        eventCount.set(0);
        lastEvent = "无";

        master = new File(masterPath);
        if (!isPng(master)) {
            lastMessage = "透明原图无效：" + masterPath;
            return false;
        }

        int uid = Os.getuid();
        int watchCount = 0;

        File external = new File("/storage/emulated/0/Android/data/com.tencent.mm");
        if (external.isDirectory() && external.canRead()) {
            watchCount += addWatchRecursive(external, 0, false);
        }

        if (uid == 0) {
            File privateRoot = new File("/data/user/0/com.tencent.mm/MicroMsg");
            if (privateRoot.isDirectory() && privateRoot.canRead()) {
                watchCount += addWatchRecursive(privateRoot, 0, true);
            }
        }

        if (watchCount == 0) {
            lastMessage = "没有可监听的微信目录。uid=" + uid
                    + "；外部 Android/data 可能被系统限制。";
            return false;
        }

        watching = true;
        lastMessage = String.format(Locale.US,
                "守护中 · uid=%d · 监听 %d 个目录 · 90 秒窗口",
                uid, watchCount);

        mainHandler.postDelayed(() -> {
            synchronized (WatcherService.this) {
                if (watching) {
                    stopWatchingInternal();
                    lastMessage = "90 秒守护窗口已结束";
                }
            }
        }, 90_000L);

        return true;
    }

    private int addWatchRecursive(File dir, int depth, boolean privateTree) {
        if (dir == null || !dir.isDirectory()) return 0;
        if (depth > (privateTree ? 7 : 5)) return 0;

        int count = 0;
        String p = dir.getAbsolutePath();

        boolean shouldWatch;
        if (privateTree) {
            shouldWatch = p.contains("/avatar") || depth <= 2;
        } else {
            shouldWatch = true;
        }

        if (shouldWatch) {
            addObserver(dir, privateTree);
            count++;
        }

        File[] kids;
        try {
            kids = dir.listFiles();
        } catch (Throwable t) {
            return count;
        }
        if (kids == null) return count;

        for (File f : kids) {
            if (!f.isDirectory()) continue;
            if (privateTree) {
                String name = f.getName();
                boolean pathInteresting = p.contains("/avatar")
                        || name.equalsIgnoreCase("avatar")
                        || depth < 2;
                if (!pathInteresting) continue;
            }
            count += addWatchRecursive(f, depth + 1, privateTree);
        }
        return count;
    }

    private void addObserver(File dir, boolean privateTree) {
        final int mask = FileObserver.CREATE
                | FileObserver.CLOSE_WRITE
                | FileObserver.MOVED_TO
                | FileObserver.MODIFY;

        FileObserver observer = new FileObserver(dir.getAbsolutePath(), mask) {
            @Override public void onEvent(int event, String path) {
                if (!watching || path == null) return;
                File changed = new File(dir, path);

                int n = eventCount.incrementAndGet();
                lastEvent = "#" + n + " " + eventName(event) + " " + changed.getAbsolutePath();

                if ((event & (FileObserver.CREATE | FileObserver.MOVED_TO)) != 0 && changed.isDirectory()) {
                    addWatchRecursive(changed, 0, privateTree);
                    return;
                }

                if ((event & (FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO)) != 0) {
                    handleCandidate(changed, privateTree);
                }
            }
        };
        observers.add(observer);
        observer.startWatching();
    }

    private void handleCandidate(File file, boolean privateTree) {
        if (!file.isFile()) return;
        if (isPng(file)) return;

        String name = file.getName().toLowerCase(Locale.ROOT);
        String path = file.getAbsolutePath().toLowerCase(Locale.ROOT);

        if (privateTree) {
            boolean finalAvatar = name.startsWith("user_hd_")
                    || name.endsWith(".png.tmp")
                    || name.endsWith(".jpg.tmp")
                    || path.contains("/avatar/");
            if (!finalAvatar) return;
        } else {
            if (!(path.contains("/cache/") || path.contains("/files/"))) return;
        }

        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) {
            lastMessage = "检测到文件但不是图片：" + file.getName();
            return;
        }

        int max = Math.max(o.outWidth, o.outHeight);
        int diff = Math.abs(o.outWidth - o.outHeight);
        boolean square = diff <= Math.max(8, Math.round(max * 0.05f));
        boolean reasonable = max >= 64 && max <= 4096;

        if (!square || !reasonable) {
            lastMessage = "忽略非头像图片：" + file.getName()
                    + " (" + o.outWidth + "×" + o.outHeight + ")";
            return;
        }

        try {
            overwrite(master, file);
            int n = replaceCount.incrementAndGet();
            lastMessage = "已替换 #" + n + "：" + file.getAbsolutePath()
                    + " (" + o.outWidth + "×" + o.outHeight + ")";
        } catch (Throwable t) {
            lastMessage = "替换失败：" + file.getAbsolutePath()
                    + " · " + t.getClass().getSimpleName()
                    + ": " + t.getMessage();
        }
    }

    private static String eventName(int event) {
        if ((event & FileObserver.CLOSE_WRITE) != 0) return "CLOSE_WRITE";
        if ((event & FileObserver.MOVED_TO) != 0) return "MOVED_TO";
        if ((event & FileObserver.CREATE) != 0) return "CREATE";
        if ((event & FileObserver.MODIFY) != 0) return "MODIFY";
        return String.valueOf(event);
    }

    private static void overwrite(File source, File dest) throws IOException {
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(dest, false)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            out.flush();
            out.getFD().sync();
        }
    }

    private static boolean isPng(File f) {
        if (f == null || !f.isFile() || f.length() < 8) return false;
        byte[] h = new byte[8];
        try (FileInputStream in = new FileInputStream(f)) {
            if (in.read(h) != 8) return false;
            for (int i = 0; i < 8; i++) {
                if (h[i] != PNG_MAGIC[i]) return false;
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public synchronized void stopWatching() {
        stopWatchingInternal();
        lastMessage = "已停止";
    }

    private synchronized void stopWatchingInternal() {
        watching = false;
        for (FileObserver o : observers) {
            try { o.stopWatching(); } catch (Throwable ignored) {}
        }
        observers.clear();
    }

    @Override public boolean isWatching() { return watching; }
    @Override public String getLastMessage() { return lastMessage; }
    @Override public int getReplaceCount() { return replaceCount.get(); }

    @Override
    public String getDiagnostics(String masterPath) {
        File ext = new File("/storage/emulated/0/Android/data/com.tencent.mm");
        File priv = new File("/data/user/0/com.tencent.mm/MicroMsg");
        return "uid=" + Os.getuid()
                + "\nmaster=" + masterPath
                + "\nmaster.png=" + isPng(new File(masterPath))
                + "\nexternal=" + ext.getAbsolutePath()
                + "\nexternal.exists=" + ext.exists()
                + "\nexternal.read=" + ext.canRead()
                + "\nprivate=" + priv.getAbsolutePath()
                + "\nprivate.exists=" + priv.exists()
                + "\nprivate.read=" + priv.canRead()
                + "\nwatching=" + watching
                + "\nobserver.count=" + observers.size()
                + "\nevent.count=" + eventCount.get()
                + "\nreplace.count=" + replaceCount.get()
                + "\nlast.event=" + lastEvent
                + "\nlast=" + lastMessage;
    }

    @Override public void destroy() {
        stopWatchingInternal();
        System.exit(0);
    }
}
