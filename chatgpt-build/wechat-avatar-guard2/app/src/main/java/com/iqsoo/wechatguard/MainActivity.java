package com.iqsoo.wechatguard;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import rikka.shizuku.Shizuku;

public final class MainActivity extends Activity {
    private static final int PICK_PNG = 1001;
    private static final int SHIZUKU_PERMISSION = 1002;

    private TextView permissionState;
    private TextView status;
    private TextView detail;
    private ImageView preview;
    private IWatcherService watcher;
    private boolean bound;
    private Runnable pendingAction;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Shizuku.OnRequestPermissionResultListener permissionListener =
            (requestCode, grantResult) -> {
                if (requestCode != SHIZUKU_PERMISSION) return;
                updatePermissionState();
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    bindWatcherSafely();
                    handler.postDelayed(this::runPendingAction, 700);
                } else {
                    pendingAction = null;
                    setStatus("Shizuku 权限被拒绝");
                }
            };

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = () -> {
        updatePermissionState();
        if (pendingAction != null) bindWatcherSafely();
    };

    private final Shizuku.OnBinderDeadListener binderDeadListener = () -> {
        watcher = null;
        bound = false;
        updatePermissionState();
        setStatus("Shizuku 服务已断开");
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            watcher = IWatcherService.Stub.asInterface(service);
            bound = true;
            refreshStatus();
            runPendingAction();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            watcher = null;
            bound = false;
            setStatus("守护服务已断开");
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            buildUi();
            Shizuku.addRequestPermissionResultListener(permissionListener);
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
            Shizuku.addBinderDeadListener(binderDeadListener);
            loadPreview();
            updatePermissionState();
            handler.post(statusTicker);
        } catch (Throwable t) {
            TextView fallback = new TextView(this);
            fallback.setPadding(32, 32, 32, 32);
            fallback.setTextSize(15);
            fallback.setText("启动异常：" + t.getClass().getName() + "\n" + String.valueOf(t.getMessage()));
            setContentView(fallback);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionState();
        loadPreview();
    }

    private void buildUi() {
        int p = dp(20);
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(p, p, p, p);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("微信透明头像守卫 v1.3.0");
        title.setTextSize(25);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, full(-2));

        TextView desc = new TextView(this);
        desc.setText("按微信 smali 实际路径修正版：普通 Shizuku 监听 Pictures/WeiXin、Pictures/WeChat 及旧版 tencent/MicroMsg 路径；Root Shizuku 额外监听微信私有头像目录。普通 Shizuku 下请从微信“拍摄”入口更换头像。");
        desc.setTextSize(14);
        LinearLayout.LayoutParams dp = full(-2);
        dp.topMargin = dp(12);
        root.addView(desc, dp);

        permissionState = new TextView(this);
        permissionState.setTextSize(16);
        LinearLayout.LayoutParams ps = full(-2);
        ps.topMargin = dp(16);
        root.addView(permissionState, ps);

        Button allFiles = button("授权 / 检查所有文件访问");
        allFiles.setOnClickListener(v -> openAllFilesSettings());
        root.addView(allFiles, spaced());

        preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setMinimumHeight(dp(180));
        LinearLayout.LayoutParams pp = full(dp(220));
        pp.topMargin = dp(14);
        root.addView(preview, pp);

        Button choose = button("选择透明 PNG");
        choose.setOnClickListener(v -> pickPng());
        root.addView(choose, spaced());

        Button start = button("开始守护 90 秒");
        start.setOnClickListener(v -> startGuard());
        root.addView(start, spaced());

        Button stop = button("停止守护");
        stop.setOnClickListener(v -> stopGuard());
        root.addView(stop, spaced());

        Button diag = button("检查权限 / 路径 / 最近事件");
        diag.setOnClickListener(v -> showDiagnostics());
        root.addView(diag, spaced());

        status = new TextView(this);
        status.setTextSize(17);
        status.setText("未启动守护");
        LinearLayout.LayoutParams sp = full(-2);
        sp.topMargin = dp(18);
        root.addView(status, sp);

        detail = new TextView(this);
        detail.setTextSize(13);
        detail.setTextIsSelectable(true);
        detail.setText("透明原图：\n" + masterFile().getAbsolutePath());
        LinearLayout.LayoutParams ep = full(-2);
        ep.topMargin = dp(10);
        root.addView(detail, ep);

        setContentView(scroll);
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(text);
        b.setTextSize(16);
        return b;
    }

    private LinearLayout.LayoutParams full(int h) {
        return new LinearLayout.LayoutParams(-1, h);
    }

    private LinearLayout.LayoutParams spaced() {
        LinearLayout.LayoutParams p = full(-2);
        p.topMargin = dp(9);
        return p;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private boolean hasAllFiles() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager();
    }

    private void updatePermissionState() {
        if (permissionState == null) return;
        boolean all = hasAllFiles();
        boolean alive = false;
        boolean granted = false;
        try {
            alive = Shizuku.pingBinder();
            granted = alive && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable ignored) {}
        final boolean fAlive = alive;
        final boolean fGranted = granted;
        runOnUiThread(() -> permissionState.setText(
                "所有文件访问：" + (all ? "已授权" : "未授权") +
                "\nShizuku：" + (fAlive ? "运行中" : "未运行") +
                "\nShizuku 授权：" + (fGranted ? "已授权" : "未授权")
        ));
    }

    private void openAllFilesSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            setStatus("当前系统不需要“所有文件访问”特殊授权");
            return;
        }
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Throwable e) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            } catch (Throwable ex) {
                setStatus("无法打开权限设置：" + ex.getMessage());
            }
        }
    }

    private void pickPng() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/png");
        startActivityForResult(i, PICK_PNG);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_PNG || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try {
            saveTransparentPng(data.getData());
            loadPreview();
            Toast.makeText(this, "透明 PNG 已保存为 400×400", Toast.LENGTH_SHORT).show();
        } catch (Throwable e) {
            setStatus("图片处理失败：" + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void saveTransparentPng(Uri uri) throws Exception {
        Bitmap src;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            src = BitmapFactory.decodeStream(in);
        }
        if (src == null) throw new IllegalArgumentException("无法解码图片");
        if (!containsTransparency(src)) {
            src.recycle();
            throw new IllegalArgumentException("PNG 没有实际透明像素");
        }

        Bitmap out = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawARGB(0, 0, 0, 0);
        float scale = Math.min(400f / src.getWidth(), 400f / src.getHeight());
        float dx = (400f - src.getWidth() * scale) * 0.5f;
        float dy = (400f - src.getHeight() * scale) * 0.5f;
        Matrix m = new Matrix();
        m.postScale(scale, scale);
        m.postTranslate(dx, dy);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        c.drawBitmap(src, m, paint);

        File dst = masterFile();
        File dir = dst.getParentFile();
        if (dir == null || (!dir.exists() && !dir.mkdirs())) {
            out.recycle();
            src.recycle();
            throw new IllegalStateException("无法创建透明头像目录");
        }
        File tmp = new File(dir, "master.tmp");
        try (FileOutputStream fos = new FileOutputStream(tmp, false)) {
            if (!out.compress(Bitmap.CompressFormat.PNG, 100, fos)) {
                throw new IllegalStateException("PNG 编码失败");
            }
            fos.flush();
            fos.getFD().sync();
        }
        if (dst.exists() && !dst.delete()) throw new IllegalStateException("无法更新旧头像");
        if (!tmp.renameTo(dst)) throw new IllegalStateException("保存透明头像失败");
        out.recycle();
        src.recycle();
    }

    private boolean containsTransparency(Bitmap b) {
        if (!b.hasAlpha()) return false;
        int sx = Math.max(1, b.getWidth() / 200);
        int sy = Math.max(1, b.getHeight() / 200);
        for (int y = 0; y < b.getHeight(); y += sy) {
            for (int x = 0; x < b.getWidth(); x += sx) {
                if ((b.getPixel(x, y) >>> 24) != 0xFF) return true;
            }
        }
        return false;
    }

    private File masterFile() {
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return new File(new File(downloads, "WeChatGuard"), "master.png");
    }

    private void loadPreview() {
        if (preview == null) return;
        File f = masterFile();
        if (!f.isFile()) {
            preview.setImageDrawable(null);
            return;
        }
        preview.setImageBitmap(BitmapFactory.decodeFile(f.getAbsolutePath()));
    }

    private void startGuard() {
        if (!masterFile().isFile()) {
            setStatus("请先选择透明 PNG");
            return;
        }
        ensureShizukuThen(this::startGuardInternal);
    }

    private void ensureShizukuThen(Runnable action) {
        pendingAction = action;
        updatePermissionState();
        try {
            if (!Shizuku.pingBinder()) {
                setStatus("Shizuku 未运行");
                return;
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(SHIZUKU_PERMISSION);
                setStatus("等待 Shizuku 授权…");
                return;
            }
            if (!bound || watcher == null) bindWatcherSafely();
            else runPendingAction();
        } catch (Throwable e) {
            pendingAction = null;
            setStatus("Shizuku 调用失败：" + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void bindWatcherSafely() {
        if (bound) return;
        try {
            if (!Shizuku.pingBinder()) return;
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) return;
            Shizuku.bindUserService(userServiceArgs(), connection);
            setStatus("正在连接守护服务…");
        } catch (Throwable e) {
            setStatus("连接守护服务失败：" + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private Shizuku.UserServiceArgs userServiceArgs() {
        ComponentName component = new ComponentName(getPackageName(), WatcherService.class.getName());
        return new Shizuku.UserServiceArgs(component)
                .processNameSuffix("wechat_avatar_guard")
                .daemon(true)
                .debuggable(BuildConfig.DEBUG)
                .version(4)
                .tag("wechat_avatar_guard_v4");
    }

    private void runPendingAction() {
        Runnable r = pendingAction;
        if (r != null && watcher != null) {
            pendingAction = null;
            r.run();
        }
    }

    private void startGuardInternal() {
        try {
            boolean ok = watcher.startWatching(masterFile().getAbsolutePath());
            setStatus(ok ? "守护已开启，90 秒内请立即去微信更换头像" : watcher.getLastMessage());
            refreshStatus();
        } catch (Throwable e) {
            setStatus("启动守护失败：" + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void stopGuard() {
        if (watcher == null) {
            setStatus("守护服务未连接");
            return;
        }
        try {
            watcher.stopWatching();
            refreshStatus();
        } catch (Throwable e) {
            setStatus("停止失败：" + e.getMessage());
        }
    }

    private void showDiagnostics() {
        ensureShizukuThen(() -> {
            try {
                detail.setText("allFiles=" + hasAllFiles() + "\n" +
                        watcher.getDiagnostics(masterFile().getAbsolutePath()));
            } catch (Throwable e) {
                detail.setText("诊断失败：" + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }

    private void refreshStatus() {
        updatePermissionState();
        IWatcherService w = watcher;
        if (w == null) return;
        try {
            setStatus((w.isWatching() ? "守护中" : "未守护") +
                    " · 已替换 " + w.getReplaceCount() + " 次\n" + w.getLastMessage());
        } catch (RemoteException ignored) {}
    }

    private void setStatus(String s) {
        runOnUiThread(() -> {
            if (status != null) status.setText(s);
        });
    }

    private final Runnable statusTicker = new Runnable() {
        @Override public void run() {
            refreshStatus();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(statusTicker);
        try { Shizuku.removeRequestPermissionResultListener(permissionListener); } catch (Throwable ignored) {}
        try { Shizuku.removeBinderReceivedListener(binderReceivedListener); } catch (Throwable ignored) {}
        try { Shizuku.removeBinderDeadListener(binderDeadListener); } catch (Throwable ignored) {}
    }
}
