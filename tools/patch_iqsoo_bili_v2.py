#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path.cwd()

def read(rel):
    return (ROOT / rel).read_text(encoding="utf-8")

def write(rel, text):
    path = ROOT / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")

def replace_exact(rel, old, new, required=True):
    text = read(rel)
    count = text.count(old)
    if required and count == 0:
        raise RuntimeError(f"Expected text not found in {rel}: {old[:120]!r}")
    text = text.replace(old, new)
    write(rel, text)
    print(f"{rel}: exact replacement x{count}")

def replace_regex(rel, pattern, replacement, count=0, min_count=1):
    text = read(rel)
    new_text, n = re.subn(pattern, replacement, text, count=count, flags=re.S)
    if n < min_count:
        raise RuntimeError(f"Pattern not found in {rel}: {pattern[:120]!r}")
    write(rel, new_text)
    print(f"{rel}: regex replacement x{n}")

def replace_kotlin_function_body(rel, marker, new_body):
    text = read(rel)
    start = text.find(marker)
    if start < 0:
        raise RuntimeError(f"Function marker not found in {rel}: {marker}")
    brace = text.find("{", start)
    if brace < 0:
        raise RuntimeError(f"Opening brace not found after {marker}")
    depth = 0
    in_string = False
    string_quote = ""
    escape = False
    i = brace
    triple = '"' * 3
    while i < len(text):
        ch = text[i]
        if in_string:
            if escape:
                escape = False
            elif ch == "\\":
                escape = True
            elif text.startswith(string_quote, i):
                in_string = False
                i += len(string_quote) - 1
        else:
            if text.startswith(triple, i):
                in_string = True
                string_quote = triple
                i += 2
            elif ch == '"':
                in_string = True
                string_quote = '"'
            elif ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    new_text = text[:brace+1] + "\n" + new_body.rstrip() + "\n" + text[i:]
                    write(rel, new_text)
                    print(f"{rel}: replaced body for {marker}")
                    return
        i += 1
    raise RuntimeError(f"Closing brace not found for {marker}")

gradle = "app/build.gradle.kts"
replace_exact(gradle, 'applicationId = "com.android.purebilibili"', 'applicationId = "com.iqsoo.bili"')
replace_exact(gradle, 'versionCode = 245', 'versionCode = 246')
replace_exact(gradle, 'versionName = "9.8.6"', 'versionName = "9.8.6-iqsoo1"')
replace_exact(gradle, 'resValue("string", "app_name", "BiliPai Debug")',
              'resValue("string", "app_name", "iQSOO Bili")')
replace_exact(gradle, 'output.outputFileName = "BiliPai-${variant.name}-${variant.versionName}.apk"',
              'output.outputFileName = "iQSOO-Bili-${variant.name}-${variant.versionName}.apk"')

for rel in [
    "app/src/main/res/values/strings.xml",
    "app/src/main/res/values-en/strings.xml",
    "app/src/main/res/values-zh-rTW/strings.xml",
]:
    if (ROOT / rel).exists():
        text = read(rel)
        text = re.sub(r'(<string\s+name="app_name">).*?(</string>)', r'\1iQSOO Bili\2', text)
        write(rel, text)

settings_manager = "app/src/main/java/com/android/purebilibili/core/store/SettingsManager.kt"
replace_exact(settings_manager, "internal const val DEFAULT_CRASH_TRACKING_ENABLED = true",
              "internal const val DEFAULT_CRASH_TRACKING_ENABLED = false")
replace_exact(settings_manager, "internal const val DEFAULT_ANALYTICS_ENABLED = true",
              "internal const val DEFAULT_ANALYTICS_ENABLED = false")

onboarding = "app/src/main/java/com/android/purebilibili/feature/onboarding/OnboardingBottomSheet.kt"
replace_exact(onboarding, "import androidx.compose.ui.platform.LocalUriHandler\n", "", required=False)
replace_exact(onboarding, "    val uriHandler = LocalUriHandler.current\n", "", required=False)
replace_regex(
    onboarding,
    r'\n\s*//\s*GitHub 链接\s*\n\s*Spacer\(modifier = Modifier\.height\(12\.dp\)\)\s*\n\s*Text\(\s*'
    r'"github\.com/jay3-yy/BiliPai".*?\n\s*\)\s*\n',
    "\n",
    count=1,
)
replace_exact(onboarding, '"开始探索 BiliPai"', '"开始使用 iQSOO Bili"', required=False)
replace_exact(onboarding, "BiliPai", "iQSOO Bili", required=False)

release_disclaimer = "app/src/main/java/com/android/purebilibili/feature/settings/policy/ReleaseChannelDisclaimer.kt"
write(release_disclaimer, r'''package com.android.purebilibili.feature.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

const val OFFICIAL_GITHUB_URL = ""
const val OFFICIAL_TELEGRAM_URL = ""
const val RELEASE_DISCLAIMER_ACK_KEY = "release_disclaimer_ack_iqsoo_v1"

@Composable
fun ReleaseChannelDisclaimerDialog(
    onDismiss: () -> Unit,
    onOpenGithub: () -> Unit,
    onOpenTelegram: () -> Unit,
    title: String = "自用版说明"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Text(
                text = "这是 iQSOO 个人自用修改版，基于 BiliPai 9.8.6 源码构建。\n\n" +
                    "已移除社群、作者联系方式、打赏二维码、外部主页和在线更新跳转；" +
                    "崩溃追踪与使用情况统计默认关闭。\n\n" +
                    "本软件继续遵循 GNU GPLv3，属于非官方第三方客户端，与哔哩哔哩官方无关。"
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("我已了解")
            }
        }
    )
}
''')

update_checker = "app/src/main/java/com/android/purebilibili/feature/settings/update/AppUpdateChecker.kt"
replace_kotlin_function_body(
    update_checker,
    "suspend fun check(currentVersion: String)",
    r'''        Result.success(
            AppUpdateCheckResult(
                isUpdateAvailable = false,
                currentVersion = normalizeVersion(currentVersion),
                latestVersion = normalizeVersion(currentVersion),
                releaseUrl = "",
                releaseNotes = "iQSOO 个人自用修改版已关闭在线更新检查。",
                publishedAt = null,
                assets = emptyList(),
                message = "自用版：在线更新已关闭"
            )
        )'''
)
text = read(update_checker)
text = text.replace("https://api.github.com/repos/jay3-yy/BiliPai/releases", "")
text = text.replace("https://raw.githubusercontent.com/jay3-yy/BiliPai/main/app/build.gradle.kts", "")
text = text.replace("https://github.com/jay3-yy/BiliPai/releases", "")
text = text.replace("https://github.com/jay3-yy/BiliPai", "")
write(update_checker, text)

settings_screen = "app/src/main/java/com/android/purebilibili/feature/settings/screen/SettingsScreen.kt"
replace_regex(
    settings_screen,
    r'    val onTelegramClick: \(\) -> Unit = \{.*?'
    r'    val onDisclaimerClick: \(\) -> Unit = \{ showReleaseDisclaimerDialog = true \}',
    r'''    val removedExternalEntry: () -> Unit = {
        Toast.makeText(context, "个人自用版已移除外部链接", Toast.LENGTH_SHORT).show()
    }
    val onTelegramClick: () -> Unit = removedExternalEntry
    val onTwitterClick: () -> Unit = removedExternalEntry
    val onGithubClick: () -> Unit = removedExternalEntry
    val onVerificationClick: () -> Unit = removedExternalEntry
    val onBuildSourceClick: () -> Unit = removedExternalEntry
    val onBuildFingerprintClick: () -> Unit = removedExternalEntry
    val onDisclaimerClick: () -> Unit = { showReleaseDisclaimerDialog = true }''',
    count=1,
)
replace_exact(
    settings_screen,
    '''        currentReleaseEvidence = AppUpdateChecker
            .check(com.android.purebilibili.BuildConfig.VERSION_NAME)
            .getOrNull()''',
    '''        currentReleaseEvidence = null''',
    required=False,
)
replace_exact(
    settings_screen,
    'text = { Text("感谢你使用 BiliPai！这是一个用爱发电的开源项目。") }',
    'text = { Text("感谢你使用 iQSOO Bili！这是个人自用修改版。") }',
    required=False,
)
replace_kotlin_function_body(
    settings_screen,
    "fun DonateDialog(onDismiss: () -> Unit)",
    r'''    com.android.purebilibili.core.ui.IOSAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("个人自用版") },
        text = {
            Text("打赏二维码和作者联系方式已移除。本版本不接受付款，也不提供任何外部社群入口。")
        },
        confirmButton = {
            com.android.purebilibili.core.ui.IOSDialogAction(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )'''
)

sections = "app/src/main/java/com/android/purebilibili/feature/settings/ui/SettingsSections.kt"

replace_kotlin_function_body(
    sections,
    "fun SupportAuthorCompactSection(",
    r'''    SettingsCardGroup {
        SettingClickableItem(
            icon = rememberAppInfoIcon(),
            title = "iQSOO 个人自用版",
            value = "无打赏、无社群、无联系方式",
            onClick = {},
            iconTint = iOSBlue,
            enableCopy = false
        )
    }'''
)

replace_kotlin_function_body(
    sections,
    "internal fun SettingsAboutHomeSection(",
    r'''    SettingsDetailGroup(title = "关于") {
        SettingsCardGroup {
            SettingClickableItem(
                icon = rememberAppInfoIcon(),
                title = "iQSOO Bili",
                value = "个人自用修改版 · 隐私优先",
                onClick = {},
                iconTint = iOSBlue,
                enableCopy = false
            )
        }
    }'''
)

replace_kotlin_function_body(
    sections,
    "fun ReleaseChannelPinnedCard(",
    r'''    val disclaimerTint = rememberAdaptiveSemanticIconTint(iOSBlue)
    val infoIcon = rememberAppInfoIcon()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = AppShapes.container(ContainerLevel.Dialog),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = infoIcon,
                    contentDescription = null,
                    tint = disclaimerTint,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "个人自用修改版",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "已移除外部推广、社群、联系方式、打赏和在线更新。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onDisclaimerClick) {
                Text("查看自用版与开源许可说明")
            }
        }
    }'''
)

replace_kotlin_function_body(
    sections,
    "fun AboutSection(",
    r'''    val uiPreset = LocalUiPreset.current
    val licensesVisual = rememberSettingsEntryVisual(SettingsSearchTarget.OPEN_SOURCE_LICENSES, uiPreset)
    val replayOnboardingVisual = rememberSettingsEntryVisual(SettingsSearchTarget.REPLAY_ONBOARDING, uiPreset)
    val infoIcon = rememberSettingsSemanticIcon(SettingsIconRole.ABOUT_SUPPORT, uiPreset)
    val sparklesIcon = rememberSettingsSemanticIcon(SettingsIconRole.EASTER_EGG, uiPreset)
    val buildFingerprintIcon = rememberSettingsSemanticIcon(SettingsIconRole.BUILD_FINGERPRINT, uiPreset)
    val easterEggTint = rememberSettingsEntryTint(SettingsEntryTintRole.TERTIARY, iOSYellow, uiPreset)

    AboutProjectOverviewCard(versionName = versionName, contributors = emptyList())
    Spacer(modifier = Modifier.height(12.dp))

    SettingsSectionTitle(title = "许可与本地构建")
    SettingsCardGroup {
        SettingClickableItem(
            icon = licensesVisual.icon,
            iconPainter = licensesVisual.iconResId?.let { painterResource(id = it) },
            title = "开源许可证",
            value = "GNU GPLv3",
            onClick = onLicenseClick,
            iconTint = licensesVisual.iconTint
        )
        SettingsDivider(startIndent = 66.dp)
        SettingClickableItem(
            icon = infoIcon,
            title = "构建类型",
            subtitle = "移除外部引流并采用隐私优先默认值",
            value = "iQSOO 个人自用修改版",
            onClick = {},
            iconTint = iOSBlue,
            enableCopy = true
        )
        SettingsDivider(startIndent = 66.dp)
        SettingClickableItem(
            icon = buildFingerprintIcon,
            title = "安装包 SHA-256",
            subtitle = buildFingerprintSubtitle,
            value = buildFingerprintValue,
            copyValue = buildFingerprintCopyValue,
            onClick = {},
            iconTint = iOSPurple,
            enableCopy = true
        )
    }
    Spacer(modifier = Modifier.height(12.dp))

    SettingsSectionTitle(title = "辅助")
    SettingsCardGroup {
        SettingClickableItem(
            icon = infoIcon,
            title = "版本",
            value = "v$versionName",
            onClick = onVersionClick,
            iconTint = iOSTeal,
            enableCopy = true
        )
        SettingsDivider(startIndent = 66.dp)
        SettingClickableItem(
            icon = replayOnboardingVisual.icon,
            iconPainter = replayOnboardingVisual.iconResId?.let { painterResource(id = it) },
            title = "重播新手引导",
            value = "了解应用功能",
            onClick = onReplayOnboardingClick,
            iconTint = replayOnboardingVisual.iconTint
        )
        SettingsDivider(startIndent = 66.dp)
        SettingSwitchItem(
            icon = sparklesIcon,
            title = "趣味彩蛋",
            subtitle = "刷新、点赞、投币、搜索时显示趣味提示",
            checked = easterEggEnabled,
            onCheckedChange = onEasterEggChange,
            iconTint = easterEggTint
        )
    }'''
)

replace_exact(sections, 'contributors: List<AboutContributor> = AboutContributors',
              'contributors: List<AboutContributor> = emptyList()', required=False)
replace_regex(
    sections,
    r'\n\s*Text\(\s*text = "贡献者".*?contributors\.forEach \{ contributor ->\s*'
    r'AboutContributorItem\(contributor = contributor\)\s*\}\s*\}\s*',
    r'''
            Text(
                text = "基于 BiliPai 9.8.6 修改，遵循 GNU GPLv3。修改日期：2026-07-03。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
''',
    count=1,
)
replace_exact(sections, 'val profileUrl: String get() = "https://github.com/$githubLogin"',
              'val profileUrl: String get() = ""', required=False)
replace_exact(sections, "    val uriHandler = LocalUriHandler.current\n", "", required=False)
replace_exact(sections, "            .clickable { uriHandler.openUri(contributor.profileUrl) }\n", "", required=False)

text = read(sections)
for old, new in {
    '"BiliPai"': '"iQSOO Bili"',
    '"默认开启，仅用于定位崩溃与严重故障"': '"默认关闭；需要本地诊断时可手动开启"',
    '"默认开启，开启后用于匿名统计每日活跃与基础使用情况"': '"默认关闭；个人自用版不主动收集使用情况"',
    'title = "关于与支持"': 'title = "关于自用版"',
    'value = "版本、开源、帮助与作者"': 'value = "版本、许可、隐私与本地构建"',
}.items():
    text = text.replace(old, new)
write(sections, text)

search_policy = "app/src/main/java/com/android/purebilibili/feature/settings/SettingsSearchPolicy.kt"
replace_exact(
    search_policy,
    "    return SETTINGS_SEARCH_INDEX\n        .mapNotNull { entry ->",
    '''    val hiddenForPersonalBuild = setOf(
        SettingsSearchTarget.OPEN_SOURCE_HOME,
        SettingsSearchTarget.CHECK_UPDATE,
        SettingsSearchTarget.VIEW_RELEASE_NOTES,
        SettingsSearchTarget.DONATE,
        SettingsSearchTarget.TELEGRAM,
        SettingsSearchTarget.TWITTER
    )

    return SETTINGS_SEARCH_INDEX
        .filterNot { it.target in hiddenForPersonalBuild }
        .mapNotNull { entry ->''',
)

root_policy = "app/src/main/java/com/android/purebilibili/feature/settings/SettingsRootCategoryPolicy.kt"
replace_exact(
    root_policy,
    'subtitle = "插件、诊断、版本、更新、社群与支持"',
    'subtitle = "插件、诊断、版本、许可与本地构建"',
)

for path in ROOT.glob("app/src/main/res/**/*author*qr*"):
    if path.is_file():
        path.unlink()
        print(f"removed {path}")
for path in ROOT.glob("app/src/main/res/**/*donat*"):
    if path.is_file():
        path.unlink()
        print(f"removed {path}")

notice = (
    "iQSOO Bili - 个人自用修改版\n\n"
    "上游项目：BiliPai 9.8.6\n"
    "修改日期：2026-07-03\n"
    "许可证：GNU GPL version 3\n\n"
    "主要修改：\n"
    "1. 独立应用名称、包名和版本标识，可与上游版本共存。\n"
    "2. 删除首次引导、设置页和关于页中的社群、作者联系方式、打赏二维码和外部主页跳转。\n"
    "3. 关闭在线更新检查，避免访问上游发布页面。\n"
    "4. 崩溃追踪和使用情况统计默认关闭。\n"
    "5. 保留开源许可证、安装包 SHA-256 和本地构建说明。\n"
    "6. 保留视频、弹幕、缓存、后台播放、画中画、动态、评论和插件等原有功能。\n\n"
    "本版本为非官方第三方客户端，与哔哩哔哩官方无关。\n"
)
write("IQSOO_MODIFICATIONS.md", notice)
write("app/src/main/assets/IQSOO_MODIFICATION_NOTICE.txt", notice)

forbidden = [
    "https://t.me/BiliPai",
    "https://x.com/YangY_0x00",
    "https://github.com/jay3-yy/BiliPai",
    "github.com/jay3-yy/BiliPai",
    "author_qr",
    "Telegram 频道",
    "官方发布渠道仅限 GitHub / Telegram",
]
violations = []
for path in (ROOT / "app/src/main").rglob("*"):
    if not path.is_file() or path.suffix.lower() not in {".kt", ".xml", ".txt", ".json"}:
        continue
    content = path.read_text(encoding="utf-8", errors="ignore")
    for token in forbidden:
        if token in content:
            violations.append(f"{path}: {token}")
if violations:
    raise RuntimeError("Forbidden referral/contact strings remain:\n" + "\n".join(violations))

print("iQSOO personal build patch applied successfully.")
