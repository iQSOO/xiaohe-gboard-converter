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
    private final Handler handler = new Handler(Looper.getMainLooper());

    private volatile File master;
    private volatile boolean watching;
    private volatile long startedAt;
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
        startedAt = System.currentTimeMillis();

        master = new File(masterPath);
        if (!isPng(master)) {
            lastMessage = "透明原图无效：" + masterPath;
            return false;
        }

        int uid = Os.getuid();
        int count = 0;

        // WeChat's default crop output path in this build is derived from
        // Environment.DIRECTORY_PICTURES + WeiXin/ or WeChat/.
        count += watchDir(new File("/storage/emulated/0/Pictures"));
        count += watchDir(new File("/storage/emulated/0/Pictures/WeiXin"));
        count += watchDir(new File("/storage/emulated/0/Pictures/WeChat"));

        // Legacy compatibility path from qs0/b.b().
        count += watchDir(new File("/storage/emulated/0/tencent/MicroMsg"));
        count += watchDir(new File("/storage/emulated/0/tencent/MicroMsg/WeiXin"));
        count += watchDir(new File("/storage/emulated/0/tencent/MicroMsg/WeChat"));

        // Gallery-selection flow may write CropImage_OutputPath directly
        // into WeChat's private avatar storage. Only root Shizuku can observe it.
        if (uid == 0) {
            count += watchPrivateAvatarTree(new File("/data/user/0/com.tencent.mm/MicroMsg"), 0);
        }

        if (count == 0) {
            lastMessage = "没有可监听目录。uid=" + uid;
            return false;
        }

        watching = true;
        lastMessage = "守护中 · uid=" + uid
                + " · 监听 " + count + " 个目录"
                + (uid == 0 ? " · 相册/拍摄均可尝试" : " · 普通 Shizuku 建议走微信“拍摄”头像");

        handler.postDelayed(() -> {
            synchronized (WatcherService.this) {
                if (watching) {
                    stopWatchingInternal();
                    lastMessage = "90 秒守护窗口已结束";
                }
            }
        }, 90_000L);

        return true;
    }

    private int watchDir(File dir) {
        if (dir == null || !dir.isDirectory() || !dir.canRead()) return 0;

        final int mask = FileObserver.CREATE
                | FileObserver.CLOSE_WRITE
                | FileObserver.MOVED_TO
                | FileObserver.MODIFY;

        FileObserver observer = new FileObserver(dir.getAbsolutePath(), mask) {
            @Override public void onEvent(int event, String path) {
                if (!watching || path == null) return;
                File f = new File(dir, path);

                int n = eventCount.incrementAndGet();
                lastEvent = "#" + n + " " + eventName(event) + " " + f.getAbsolutePath();

                // If WeiXin/WeChat directory appears after monitoring started, attach watcher.
                if ((event & (FileObserver.CREATE | FileObserver.MOVED_TO)) != 0 && f.isDirectory()) {
                    String name = f.getName();
                    if ("WeiXin".equalsIgnoreCase(name) || "WeChat".equalsIgnoreCase(name)) {
                        watchDir(f);
                    }
                    return;
                }

                if ((event & (FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO)) != 0) {
                    handlePublicCandidate(f);
                }
            }
        };

        observers.add(observer);
        observer.startWatching();
        return 1;
    }

    private int watchPrivateAvatarTree(File dir, int depth) {
        if (dir == null || !dir.isDirectory() || !dir.canRead() || depth > 8) return 0;

        int count = 0;
        String path = dir.getAbsolutePath().toLowerCase(Locale.ROOT);
        String name = dir.getName().toLowerCase(Locale.ROOT);

        boolean interesting = path.contains("/avatar")
                || name.equals("avatar")
                || depth <= 2;

        if (interesting) {
            final int mask = FileObserver.CREATE | FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO;
            FileObserver observer = new FileObserver(dir.getAbsolutePath(), mask) {
                @Override public void onEvent(int event, String child) {
                    if (!watching || child == null) return;
                    File f = new File(dir, child);
                    int n = eventCount.incrementAndGet();
                    lastEvent = "#" + n + " " + eventName(event) + " " + f.getAbsolutePath();

                    if ((event & (FileObserver.CREATE | FileObserver.MOVED_TO)) != 0 && f.isDirectory()) {
                        watchPrivateAvatarTree(f, 0);
                        return;
                    }

                    if ((event & (FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO)) != 0) {
                        handlePrivateCandidate(f);
                    }
                }
            };
            observers.add(observer);
            observer.startWatching();
            count++;
        }

        File[] kids;
        try { kids = dir.listFiles(); } catch (Throwable t) { kids = null; }
        if (kids == null) return count;

        for (File f : kids) {
            if (!f.isDirectory()) continue;
            String childName = f.getName().toLowerCase(Locale.ROOT);
            if (depth < 2 || path.contains("/avatar") || childName.equals("avatar")) {
                count += watchPrivateAvatarTree(f, depth + 1);
            }
        }

        return count;
    }

    private void handlePublicCandidate(File file) {
        if (!file.isFile()) return;
        if (System.currentTimeMillis() - startedAt > 90_000L) return;
        if (isPng(file)) return;

        String path = file.getAbsolutePath().toLowerCase(Locale.ROOT);
        String name = file.getName().toLowerCase(Locale.ROOT);

        boolean inWechatPicturePath =
                path.contains("/pictures/weixin/")
                || path.contains("/pictures/wechat/")
                || path.contains("/tencent/micromsg/weixin/")
                || path.contains("/tencent/micromsg/wechat/");

        if (!inWechatPicturePath) return;

        // Default ImageCropUI names files *_crop.jpg. Camera route can also reuse
        // the captured output path, so square JPEGs created in these directories
        // during the short guard window are candidates as well.
        if (!(name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".tmp"))) return;

        replaceIfAvatarLike(file, "公共裁剪");
    }

    private void handlePrivateCandidate(File file) {
        if (!file.isFile() || isPng(file)) return;

        String path = file.getAbsolutePath().toLowerCase(Locale.ROOT);
        String name = file.getName().toLowerCase(Locale.ROOT);

        boolean candidate =
                path.contains("/avatar/")
                || name.endsWith(".crop")
                || name.endsWith(".tmp");

        if (!candidate) return;
        replaceIfAvatarLike(file, "私有头像");
    }

    private void replaceIfAvatarLike(File file, String source) {
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
        boolean sane = max >= 64 && max < 1440; // w0 copies source byte-for-byte below 1440.

        if (!square || !sane) {
            lastMessage = "检测到但不是目标头像：" + file.getName()
                    + " (" + o.outWidth + "×" + o.outHeight + ")";
            return;
        }

        try {
            overwrite(master, file);
            int n = replaceCount.incrementAndGet();
            lastMessage = "已替换 #" + n + " [" + source + "] "
                    + file.getAbsolutePath()
                    + " (" + o.outWidth + "×" + o.outHeight + ")";
        } catch (Throwable t) {
            lastMessage = "替换失败：" + file.getAbsolutePath()
                    + " · " + t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    private static void overwrite(File src, File dst) throws IOException {
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst, false)) {
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
            for (int i = 0; i < 8; i++) if (h[i] != PNG_MAGIC[i]) return false;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String eventName(int event) {
        if ((event & FileObserver.CLOSE_WRITE) != 0) return "CLOSE_WRITE";
        if ((event & FileObserver.MOVED_TO) != 0) return "MOVED_TO";
        if ((event & FileObserver.CREATE) != 0) return "CREATE";
        if ((event & FileObserver.MODIFY) != 0) return "MODIFY";
        return String.valueOf(event);
    }

    @Override public synchronized void stopWatching() {
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
        File p1 = new File("/storage/emulated/0/Pictures/WeiXin");
        File p2 = new File("/storage/emulated/0/Pictures/WeChat");
        File p3 = new File("/storage/emulated/0/tencent/MicroMsg/WeiXin");
        File p4 = new File("/storage/emulated/0/tencent/MicroMsg/WeChat");
        File priv = new File("/data/user/0/com.tencent.mm/MicroMsg");

        return "uid=" + Os.getuid()
                + "\nmaster.png=" + isPng(new File(masterPath))
                + "\nPictures/WeiXin: exists=" + p1.exists() + ", read=" + p1.canRead()
                + "\nPictures/WeChat: exists=" + p2.exists() + ", read=" + p2.canRead()
                + "\nlegacy/WeiXin: exists=" + p3.exists() + ", read=" + p3.canRead()
                + "\nlegacy/WeChat: exists=" + p4.exists() + ", read=" + p4.canRead()
                + "\nprivate MicroMsg: exists=" + priv.exists() + ", read=" + priv.canRead()
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
