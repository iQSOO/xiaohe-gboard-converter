package com.iqsoo.uisimulator;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public final class OverlayService extends Service {
    private static final String CHANNEL_ID = "ui_simulator_overlay";
    private WindowManager windowManager;
    private View bubble;
    private View panel;
    private SharedPreferences prefs;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("overlay_state", MODE_PRIVATE);
        createChannel();
        startForeground(1001, buildNotification());
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        showBubble();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "中文悬浮控制", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("保持悬浮控制面板运行");
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending = PendingIntent.getActivity(this, 0, open, flags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder.setContentTitle("中文控制面板测试器")
                .setContentText("悬浮控制正在运行")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pending)
                .setOngoing(true)
                .build();
    }

    private WindowManager.LayoutParams params(int width, int height) {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        return new WindowManager.LayoutParams(width, height, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
    }

    private GradientDrawable background(int color, float radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private void showBubble() {
        if (bubble != null) return;
        TextView icon = text("控", 20, Color.WHITE);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(background(Color.rgb(36, 121, 232), 18, Color.rgb(105, 185, 255)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) icon.setElevation(dp(10));
        bubble = icon;

        final WindowManager.LayoutParams layout = params(dp(58), dp(58));
        layout.gravity = Gravity.TOP | Gravity.START;
        layout.x = prefs.getInt("x", dp(18));
        layout.y = prefs.getInt("y", dp(180));

        icon.setOnTouchListener(new View.OnTouchListener() {
            int startX, startY;
            float downX, downY;
            boolean moved;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = layout.x;
                        startY = layout.y;
                        downX = event.getRawX();
                        downY = event.getRawY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - downX);
                        int dy = (int) (event.getRawY() - downY);
                        moved = moved || Math.abs(dx) > dp(3) || Math.abs(dy) > dp(3);
                        layout.x = startX + dx;
                        layout.y = startY + dy;
                        windowManager.updateViewLayout(bubble, layout);
                        return true;
                    case MotionEvent.ACTION_UP:
                        prefs.edit().putInt("x", layout.x).putInt("y", layout.y).apply();
                        if (!moved) togglePanel(layout.x, layout.y);
                        return true;
                    default:
                        return false;
                }
            }
        });
        windowManager.addView(bubble, layout);
    }

    private void togglePanel(int x, int y) {
        if (panel != null) {
            windowManager.removeView(panel);
            panel = null;
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(14));
        root.setBackground(background(Color.rgb(12, 28, 47), 20, Color.rgb(42, 74, 108)));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) root.setElevation(dp(14));

        TextView title = text("中文悬浮控制面板", 16, Color.WHITE);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(42)));
        TextView status = text("● 独立测试引擎运行中", 12, Color.rgb(72, 222, 137));
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(34)));
        root.addView(makeSwitch("触控反馈", "touch", true));
        root.addView(makeSwitch("状态常亮", "keep", true));
        root.addView(makeSwitch("测试加速", "speed", false));

        Button test = button("执行测试动作", Color.rgb(36, 121, 232), Color.rgb(79, 157, 255));
        test.setOnClickListener(view -> {
            prefs.edit().putLong("last_test", System.currentTimeMillis()).apply();
            status.setText("● 测试成功 · 状态已写入本机");
            Toast.makeText(this, "测试动作已执行", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams testParams = new LinearLayout.LayoutParams(-1, dp(48));
        testParams.topMargin = dp(10);
        root.addView(test, testParams);

        Button close = button("关闭悬浮控制", Color.rgb(70, 26, 39), Color.rgb(144, 55, 73));
        close.setTextColor(Color.rgb(255, 200, 205));
        close.setOnClickListener(view -> stopSelf());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(-1, dp(46));
        closeParams.topMargin = dp(8);
        root.addView(close, closeParams);

        panel = root;
        WindowManager.LayoutParams layout = params(dp(300), WindowManager.LayoutParams.WRAP_CONTENT);
        layout.gravity = Gravity.TOP | Gravity.START;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        layout.x = Math.max(dp(8), Math.min(x, screenWidth - dp(310)));
        layout.y = Math.max(dp(60), y + dp(66));
        windowManager.addView(panel, layout);
    }

    private Button button(String label, int color, int stroke) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setBackground(background(color, 13, stroke));
        return button;
    }

    private View makeSwitch(String label, String key, boolean defaultValue) {
        Switch control = new Switch(this);
        control.setText(label);
        control.setTextSize(14);
        control.setTextColor(Color.rgb(225, 236, 250));
        control.setChecked(prefs.getBoolean(key, defaultValue));
        control.setPadding(0, dp(4), 0, dp(4));
        control.setOnCheckedChangeListener((buttonView, checked) -> {
            prefs.edit().putBoolean(key, checked).apply();
            if (prefs.getBoolean("touch", true)) {
                Toast.makeText(this, label + (checked ? "已开启" : "已关闭"), Toast.LENGTH_SHORT).show();
            }
        });
        return control;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onDestroy() {
        if (windowManager != null) {
            if (panel != null) windowManager.removeView(panel);
            if (bubble != null) windowManager.removeView(bubble);
        }
        panel = null;
        bubble = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
