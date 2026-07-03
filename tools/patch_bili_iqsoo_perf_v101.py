#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path.cwd()


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding='utf-8')


def write(rel: str, text: str) -> None:
    path = ROOT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding='utf-8')


def replace_exact(rel: str, old: str, new: str, required: bool = True) -> int:
    text = read(rel)
    n = text.count(old)
    if required and n == 0:
        raise RuntimeError(f'Expected text not found in {rel}: {old[:100]!r}')
    if n:
        write(rel, text.replace(old, new))
    print(f'{rel}: exact replacement x{n}')
    return n


def replace_regex(rel: str, pattern: str, replacement: str, count: int = 0, required: bool = True) -> int:
    text = read(rel)
    new_text, n = re.subn(pattern, replacement, text, count=count, flags=re.S)
    if required and n == 0:
        raise RuntimeError(f'Pattern not found in {rel}: {pattern[:100]!r}')
    if n:
        write(rel, new_text)
    print(f'{rel}: regex replacement x{n}')
    return n


def replace_kotlin_function_body(rel: str, marker: str, new_body: str, required: bool = True) -> bool:
    text = read(rel)
    start = text.find(marker)
    if start < 0:
        if required:
            raise RuntimeError(f'Function marker not found in {rel}: {marker}')
        print(f'{rel}: marker skipped: {marker}')
        return False
    brace = text.find('{', start)
    if brace < 0:
        raise RuntimeError(f'Opening brace not found after {marker}')
    depth = 0
    in_string = False
    quote = ''
    escape = False
    i = brace
    while i < len(text):
        if in_string:
            if escape:
                escape = False
            elif text[i] == '\\':
                escape = True
            elif text.startswith(quote, i):
                in_string = False
                i += len(quote) - 1
        else:
            if text.startswith('"""', i):
                in_string = True
                quote = '"""'
                i += 2
            elif text[i] == '"':
                in_string = True
                quote = '"'
            elif text[i] == '{':
                depth += 1
            elif text[i] == '}':
                depth -= 1
                if depth == 0:
                    patched = text[:brace + 1] + '\n' + new_body.rstrip() + '\n' + text[i:]
                    write(rel, patched)
                    print(f'{rel}: replaced body for {marker}')
                    return True
        i += 1
    raise RuntimeError(f'Closing brace not found for {marker}')


gradle = 'app/build.gradle.kts'
replace_exact(gradle, 'applicationId = "com.android.purebilibili"', 'applicationId = "com.iqsoo.bili"')
replace_exact(gradle, 'versionCode = 245', 'versionCode = 10001')
replace_exact(gradle, 'versionName = "9.8.6"', 'versionName = "1.0.1"')
replace_regex(
    gradle,
    r'''        debug \{.*?\n        \}\n        create\("dev"\) \{''',
    '''        debug {
            isCrunchPngs = false
            resValue("string", "app_name", "Bili-iQSOO")
            buildConfigField("boolean", "ALLOW_HARDCODED_DNS_FALLBACK", "false")
            buildConfigField("boolean", "ENABLE_VERBOSE_DEBUG_LOGS", "false")
            buildConfigField("boolean", "ENABLE_VERBOSE_RUNTIME_LOG_PERSISTENCE", "false")
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("dev") {''',
    count=1,
)
replace_exact(
    gradle,
    'output.outputFileName = "BiliPai-${variant.name}-${variant.versionName}.apk"',
    'output.outputFileName = "Bili-iQSOO-${variant.name}-${variant.versionName}.apk"'
)

for rel in [
    'app/src/main/res/values/strings.xml',
    'app/src/main/res/values-en/strings.xml',
    'app/src/main/res/values-zh-rTW/strings.xml',
]:
    if (ROOT / rel).exists():
        text = read(rel)
        text = re.sub(r'(<string\s+name="app_name">).*?(</string>)', r'\1Bili-iQSOO\2', text)
        write(rel, text)

write('app/src/main/java/com/android/purebilibili/core/performance/HighRefreshRateController.kt', r'''package com.android.purebilibili.core.performance

import android.app.Activity
import android.view.Display
import android.view.WindowManager
import kotlin.math.abs

object HighRefreshRateController {
    @Suppress("DEPRECATION")
    fun apply(activity: Activity) {
        runCatching {
            val display = activity.windowManager.defaultDisplay
            val currentMode = display.mode
            val allModes = display.supportedModes.toList()
            val sameResolutionModes = allModes.filter { mode ->
                mode.physicalWidth == currentMode.physicalWidth &&
                    mode.physicalHeight == currentMode.physicalHeight
            }
            val candidates = sameResolutionModes.ifEmpty { allModes }
            val bestMode = candidates.maxWithOrNull(
                compareBy<Display.Mode> { it.refreshRate }
                    .thenBy { it.physicalWidth * it.physicalHeight }
            ) ?: return

            val attributes = activity.window.attributes
            val modeChanged = attributes.preferredDisplayModeId != bestMode.modeId
            val rateChanged = abs(attributes.preferredRefreshRate - bestMode.refreshRate) > 0.1f
            if (modeChanged || rateChanged) {
                attributes.preferredDisplayModeId = bestMode.modeId
                attributes.preferredRefreshRate = bestMode.refreshRate
                activity.window.attributes = attributes
            }
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        }
    }
}
''')

main_activity = 'app/src/main/java/com/android/purebilibili/MainActivity.kt'
replace_exact(
    main_activity,
    'import com.android.purebilibili.core.coroutines.AppScope\n',
    'import com.android.purebilibili.core.coroutines.AppScope\nimport com.android.purebilibili.core.performance.HighRefreshRateController\n'
)
replace_exact(
    main_activity,
    '        super.onCreate(savedInstanceState)\n        //  初始调用，后续会根据主题动态更新\n',
    '        super.onCreate(savedInstanceState)\n        HighRefreshRateController.apply(this)\n        //  初始调用，后续会根据主题动态更新\n'
)
replace_exact(
    main_activity,
    '    override fun onResume() {\n        super.onResume()\n        refreshSystemThemeSnapshot(reason = "resume")\n',
    '    override fun onResume() {\n        super.onResume()\n        HighRefreshRateController.apply(this)\n        refreshSystemThemeSnapshot(reason = "resume")\n'
)
replace_exact(main_activity, 'internal fun shouldUseRealtimeSplashBlur(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE',
              'internal fun shouldUseRealtimeSplashBlur(sdkInt: Int): Boolean = false')
replace_exact(main_activity, 'internal fun splashExitDurationMs(): Long = 920L', 'internal fun splashExitDurationMs(): Long = 180L')
replace_exact(main_activity, 'internal fun splashMaxKeepOnScreenMs(): Long = 1000L', 'internal fun splashMaxKeepOnScreenMs(): Long = 450L')
replace_exact(main_activity, 'internal fun customSplashHoldDurationMs(): Long = 1900L', 'internal fun customSplashHoldDurationMs(): Long = 120L')
replace_exact(main_activity, 'internal fun customSplashFadeDurationMs(): Int = 1450', 'internal fun customSplashFadeDurationMs(): Int = 160')
replace_exact(main_activity, 'val mainHazeState = rememberRecoverableHazeState(initialBlurEnabled = true)',
              'val mainHazeState = rememberRecoverableHazeState(initialBlurEnabled = false)')
replace_regex(
    main_activity,
    r'''\n\s*LaunchedEffect\(Unit\) \{\n\s*val autoCheckUpdateEnabled = SettingsManager\.getAutoCheckAppUpdate\(context\)\.first\(\).*?\n\s*\}\n\s*\}\n\s*\n\s*//  首次启动检测''',
    '\n\n            // Independent build: online update check disabled on app entry.\n\n            //  首次启动检测',
    count=1,
    required=False,
)

runtime = 'app/src/main/java/com/android/purebilibili/app/PureApplicationRuntimeConfig.kt'
replace_exact(runtime, 'fun deferredNonCriticalStartupDelayMs(): Long = 900L',
              'fun deferredNonCriticalStartupDelayMs(): Long = 3_000L')
replace_exact(runtime, 'fun dex2OatProfileInstallDelayMs(): Long = 2_500L',
              'fun dex2OatProfileInstallDelayMs(): Long = 6_000L')
replace_exact(runtime, 'fun resolveImageMemoryCachePercent(): Double = 0.10',
              'fun resolveImageMemoryCachePercent(): Double = 0.14')

app = 'app/src/main/java/com/android/purebilibili/app/PureApplication.kt'
replace_exact(app, '.crossfade(true)', '.crossfade(false)')
replace_exact(app, '        CrashReporter.installGlobalExceptionHandler()\n', '', required=False)
replace_regex(
    app,
    r'''\n\s*// 启动即确保首页视觉默认值生效.*?\n\s*startupOrchestrator\.runImmediate''',
    '\n\n        // Performance build: do not force heavy glass/blur defaults at cold start.\n        startupOrchestrator.runImmediate',
    count=1,
)
replace_kotlin_function_body(app, 'private fun initTelemetryNow()', '        // Disabled in Bili-iQSOO performance build.')

startup_tasks = 'app/src/main/java/com/android/purebilibili/app/startup/AppStartupTask.kt'
replace_exact(
    startup_tasks,
    '''        AppStartupTask(
            id = "plugin_init",
            phase = StartupPhase.AFTER_FIRST_INTERACTIVE,
            criticality = StartupCriticality.DEFERRED,
            thread = StartupThread.MAIN_IDLE
        )''',
    '''        AppStartupTask(
            id = "plugin_init",
            phase = StartupPhase.AFTER_FIRST_INTERACTIVE,
            criticality = StartupCriticality.DEFERRED,
            thread = StartupThread.MAIN_DELAYED,
            delayMs = deferredDelayMs + 2_000L
        )'''
)

settings = 'app/src/main/java/com/android/purebilibili/core/store/SettingsManager.kt'
replace_exact(settings, 'internal const val DEFAULT_CRASH_TRACKING_ENABLED = true',
              'internal const val DEFAULT_CRASH_TRACKING_ENABLED = false')
replace_exact(settings, 'internal const val DEFAULT_ANALYTICS_ENABLED = true',
              'internal const val DEFAULT_ANALYTICS_ENABLED = false')
replace_exact(settings, 'internal fun resolveDefaultPlayerDiagnosticLoggingEnabled(isDebugBuild: Boolean): Boolean {\n    return !isDebugBuild\n}',
              'internal fun resolveDefaultPlayerDiagnosticLoggingEnabled(isDebugBuild: Boolean): Boolean {\n    return false\n}')
replace_exact(settings, 'HomeHeaderBlurMode.FOLLOW_PRESET -> true',
              'HomeHeaderBlurMode.FOLLOW_PRESET -> false')

ui_preset = 'app/src/main/java/com/android/purebilibili/core/theme/UiPreset.kt'
replace_exact(ui_preset, 'fun fromValue(value: Int): UiPreset = entries.find { it.value == value } ?: IOS',
              'fun fromValue(value: Int): UiPreset = IOS')

quality = 'app/src/main/java/com/android/purebilibili/feature/video/ui/components/QualityMenu.kt'
replace_exact(quality, 'return qualityId in switchableQualityIds',
              'return switchableQualityIds.isEmpty() || qualityId in switchableQualityIds')
replace_exact(quality, '.background(Color.Black.copy(alpha = 0.5f))',
              '.background(Color.Black.copy(alpha = 0.28f))', required=False)
replace_exact(quality, 'contentAlignment = Alignment.Center\n        ) {\n            Surface(',
              'contentAlignment = Alignment.CenterEnd\n        ) {\n            Surface(', required=False)
replace_exact(quality, '.widthIn(min = 200.dp, max = 280.dp)\n                    .heightIn(max = 400.dp)',
              '.padding(end = 12.dp)\n                    .widthIn(min = 220.dp, max = 320.dp)\n                    .heightIn(max = 640.dp)')
replace_exact(quality, '.clip(RoundedCornerShape(12.dp))', '.clip(RoundedCornerShape(22.dp))', required=False)
replace_exact(quality, 'shape = RoundedCornerShape(12.dp),', 'shape = RoundedCornerShape(22.dp),', required=False)

policy = 'app/src/main/java/com/android/purebilibili/feature/settings/policy/ReleaseChannelDisclaimer.kt'
write(policy, r'''package com.android.purebilibili.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

const val OFFICIAL_GITHUB_URL = "https://iqsoo.com"
const val OFFICIAL_TELEGRAM_URL = "https://iqsoo.com"
const val RELEASE_DISCLAIMER_ACK_KEY = "release_disclaimer_ack_bili_iqsoo_v1"

@Composable
fun ReleaseChannelDisclaimerDialog(
    onDismiss: () -> Unit,
    onOpenGithub: () -> Unit,
    onOpenTelegram: () -> Unit,
    title: String = "Bili-iQSOO 说明"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text("Bili-iQSOO 基于开源项目 BiliPai 9.8.6 修改，继续遵守 GNU GPLv3。")
                Spacer(Modifier.height(8.dp))
                Text("本应用是非官方第三方客户端，与哔哩哔哩官方无关。项目网站：iqsoo.com")
            }
        },
        confirmButton = { TextButton(onClick = onOpenGithub) { Text("访问 iqsoo.com") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}
''')

for rel in [
    'app/src/main/java/com/android/purebilibili/feature/onboarding/OnboardingBottomSheet.kt',
    'app/src/main/java/com/android/purebilibili/feature/settings/ui/SettingsSections.kt',
    'app/src/main/java/com/android/purebilibili/feature/settings/screen/SettingsScreen.kt',
]:
    if (ROOT / rel).exists():
        write(rel, read(rel).replace('BiliPai', 'Bili-iQSOO'))

notice = '''Bili-iQSOO 1.0.1 performance build\n\nUpstream: BiliPai 9.8.6\nLicense: GNU GPLv3\nPackage: com.iqsoo.bili\nWebsite: https://iqsoo.com\n\nPerformance changes:\n- Requests the fastest display mode exposed by the device at the current resolution.\n- Release-grade R8/resource shrinking with non-debug runtime semantics.\n- Disables realtime splash blur, always-on haze, image crossfade, telemetry and player diagnostics.\n- Defers noncritical plugin initialization away from the first interactive frames.\n- Fixes quality buttons when switchable track IDs are omitted.\n\nAndroid/OEM thermal, battery and display policy can still override refresh-rate requests.\n'''
write('BILI_IQSOO_MODIFICATIONS.txt', notice)
write('app/src/main/assets/BILI_IQSOO_MODIFICATIONS.txt', notice)

print('Bili-iQSOO 1.0.1 performance patch applied successfully.')
