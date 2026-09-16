#!/usr/bin/env bash
set -euo pipefail

WORK_ROOT="${RUNNER_TEMP:-/tmp}/rime-helper-build"
TRIME_DIR="$WORK_ROOT/trime"
CONFIG_DIR="$WORK_ROOT/config"
OVERLAY_DIR="${GITHUB_WORKSPACE}/rime-apk-overlay"
OUTPUT_DIR="${GITHUB_WORKSPACE}/out"

rm -rf "$WORK_ROOT" "$OUTPUT_DIR"
mkdir -p "$WORK_ROOT" "$CONFIG_DIR" "$OUTPUT_DIR"

printf '\n==> Clone Trime 3.3.11 and native submodules\n'
git clone --depth 1 --branch v3.3.11 --recurse-submodules --shallow-submodules \
  https://github.com/osfans/trime.git "$TRIME_DIR"
cd "$TRIME_DIR"
git submodule update --init --recursive --depth 1

printf '\n==> Clone Rime configurations\n'
git clone --depth 1 https://github.com/iDvel/rime-ice.git "$CONFIG_DIR/rime-ice"
git clone --depth 1 https://github.com/Mintimate/oh-my-rime.git "$CONFIG_DIR/oh-my-rime"
git clone --depth 1 https://github.com/rime/rime-wubi.git "$CONFIG_DIR/rime-wubi"
git clone --depth 1 https://github.com/rime/rime-pinyin-simp.git "$CONFIG_DIR/rime-pinyin-simp"

SHARED_LINK="$TRIME_DIR/app/src/main/assets/shared"
SHARED_DIR="$(readlink -f "$SHARED_LINK" 2>/dev/null || true)"
if [[ -z "$SHARED_DIR" ]]; then
  rm -rf "$SHARED_LINK"
  mkdir -p "$SHARED_LINK"
  SHARED_DIR="$SHARED_LINK"
fi
mkdir -p "$SHARED_DIR"
printf 'Shared Rime data directory: %s\n' "$SHARED_DIR"

printf '\n==> Install offline dictionaries and schemas\n'
rsync -a --delete-excluded \
  --exclude='.git/' --exclude='.github/' --exclude='others/' \
  --exclude='README*' --exclude='*.md' --exclude='*.png' --exclude='*.jpg' \
  "$CONFIG_DIR/rime-ice/" "$SHARED_DIR/"

rsync -a \
  --exclude='.git/' --exclude='.github/' --exclude='README*' --exclude='*.md' \
  "$CONFIG_DIR/rime-wubi/" "$SHARED_DIR/"
rsync -a \
  --exclude='.git/' --exclude='.github/' --exclude='README*' --exclude='*.md' \
  "$CONFIG_DIR/rime-pinyin-simp/" "$SHARED_DIR/"

mkdir -p "$SHARED_DIR/lua/aux_code"
cp "$CONFIG_DIR/oh-my-rime/lua/auxCode_filter.lua" "$SHARED_DIR/lua/auxCode_filter.lua"
if [[ -d "$CONFIG_DIR/oh-my-rime/lua/aux_code" ]]; then
  rsync -a "$CONFIG_DIR/oh-my-rime/lua/aux_code/" "$SHARED_DIR/lua/aux_code/"
else
  find "$CONFIG_DIR/oh-my-rime" -type f \( -iname '*aux*code*.txt' -o -iname '*zrm*.txt' -o -iname '*flypy*.txt' \) \
    -exec cp -f {} "$SHARED_DIR/lua/aux_code/" \;
fi

printf '\n==> Generate Wubi86 first-two-key auxiliary-code table\n'
export WUBI_SOURCE_DIR="$CONFIG_DIR/rime-wubi"
export RIME_SHARED_DIR="$SHARED_DIR"
python3 <<'PY'
from pathlib import Path
import os
import re

source = Path(os.environ["WUBI_SOURCE_DIR"])
out = Path(os.environ["RIME_SHARED_DIR"]) / "lua" / "aux_code" / "wubi86_aux.txt"

mapping: dict[str, list[str]] = {}
for path in source.rglob("*"):
    if not path.is_file() or path.name.startswith("."):
        continue
    if path.suffix.lower() not in {".yaml", ".txt", ".tsv", ".dict"} and ".dict." not in path.name:
        continue
    try:
        text = path.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        continue

    in_body = False
    for raw in text.splitlines():
        line = raw.strip("\ufeff\r\n")
        if line.strip() == "...":
            in_body = True
            continue
        if not in_body or not line or line.startswith("#"):
            continue
        fields = line.split("\t")
        if len(fields) < 2:
            continue
        word, code = fields[0].strip(), fields[1].strip().lower()
        if len(word) != 1 or not re.fullmatch(r"[a-z]{1,4}", code):
            continue
        prefix = code[:2]
        bucket = mapping.setdefault(word, [])
        if prefix not in bucket:
            bucket.append(prefix)

if len(mapping) < 3000:
    raise SystemExit(f"Wubi86 mapping is unexpectedly small: {len(mapping)} entries")

out.parent.mkdir(parents=True, exist_ok=True)
with out.open("w", encoding="utf-8", newline="\n") as fh:
    for char in sorted(mapping):
        fh.write(f"{char}={' '.join(mapping[char])}\n")
print(f"Generated {out} with {len(mapping)} Chinese-character mappings")
PY

printf '\n==> Create Xiaohe auxiliary-code schemas\n'
export BASE_FLYPY_SCHEMA="$SHARED_DIR/double_pinyin_flypy.schema.yaml"
python3 <<'PY'
from pathlib import Path
import os

base_path = Path(os.environ["BASE_FLYPY_SCHEMA"])
shared = Path(os.environ["RIME_SHARED_DIR"])
base = base_path.read_text(encoding="utf-8")


def build_schema(schema_id: str, display_name: str, namespace: str, note: str) -> str:
    text = base
    text = text.replace("schema_id: double_pinyin_flypy", f"schema_id: {schema_id}", 1)
    text = text.replace("name: 小鹤双拼", f"name: {display_name}", 1)
    text = text.replace("prism: double_pinyin_flypy", f"prism: {schema_id}", 1)
    filter_line = f"    - lua_filter@*auxCode_filter@{namespace}  # {note}\n"
    if "    - uniquifier" not in text:
        raise SystemExit("Unable to locate filter insertion point in Flypy schema")
    text = text.replace("    - uniquifier", filter_line + "    - uniquifier", 1)
    text += (
        "\n\n# iQSOO 辅助码设置\n"
        "aux_code:\n"
        "  trigger_word: \";\"\n"
        "  show_aux_notice: \"trigger\"\n"
    )
    return text

(shared / "iqsoo_flypy_aux.schema.yaml").write_text(
    build_schema("iqsoo_flypy_aux", "小鹤双拼·鹤形辅码", "flypy_full", "小鹤鹤形辅码"),
    encoding="utf-8",
)
(shared / "iqsoo_flypy_wubi86.schema.yaml").write_text(
    build_schema("iqsoo_flypy_wubi86", "小鹤双拼·五笔86辅码", "wubi86_aux", "五笔86前两码辅码"),
    encoding="utf-8",
)

(shared / "default.custom.yaml").write_text(
    "# iQSOO custom schema list\n"
    "patch:\n"
    "  schema_list:\n"
    "    - schema: iqsoo_flypy_aux\n"
    "    - schema: iqsoo_flypy_wubi86\n"
    "    - schema: wubi86\n"
    "    - schema: double_pinyin_flypy\n",
    encoding="utf-8",
)
PY

printf '\n==> Add Android mnemonic page and rebrand install package\n'
cp "$OVERLAY_DIR/MnemonicActivity.kt" \
  "$TRIME_DIR/app/src/main/java/com/osfans/trime/ui/main/MnemonicActivity.kt"

export TRIME_DIR
python3 <<'PY'
from pathlib import Path
import os
import re

root = Path(os.environ["TRIME_DIR"])

# Independent install package; keep Java/Kotlin namespace unchanged to minimize invasive changes.
gradle = root / "app/build.gradle.kts"
text = gradle.read_text(encoding="utf-8")
text = text.replace('applicationId = "com.osfans.trime"', 'applicationId = "com.iqsoo.rimehelper"', 1)
text = text.replace('versionCode = 20260701', 'versionCode = 2026070301', 1)
text = text.replace('versionName = "3.3.11"', 'versionName = "1.0.0-rime3.3.11"', 1)
text = re.sub(r'^\s*applicationIdSuffix = "\.debug"\s*\n', '', text, flags=re.MULTILINE)
gradle.write_text(text, encoding="utf-8")

# Add mnemonic preference to the main settings screen.
main = root / "app/src/main/java/com/osfans/trime/ui/main/MainFragment.kt"
text = main.read_text(encoding="utf-8")
if "import android.content.Intent" not in text:
    text = text.replace("package com.osfans.trime.ui.main\n\n", "package com.osfans.trime.ui.main\n\nimport android.content.Intent\n", 1)
needle = '''            addDestinationPreference(
                R.string.user_dictionary,
                R.drawable.ic_baseline_book_24,
                NavigationRoute.UserDict,
            )
'''
insert = needle + '''            addPreference(
                "双拼与五笔助记",
                "查看小鹤双拼键位、鹤形辅码和五笔86字根",
                icon = R.drawable.ic_baseline_book_24,
            ) {
                startActivity(Intent(requireContext(), MnemonicActivity::class.java))
            }
'''
if needle not in text:
    raise SystemExit("Unable to patch MainFragment")
text = text.replace(needle, insert, 1)
main.write_text(text, encoding="utf-8")

# Declare activity and ensure launcher alias is unique to the new application ID.
manifest = root / "app/src/main/AndroidManifest.xml"
text = manifest.read_text(encoding="utf-8")
activity = '''        <activity
            android:name=".ui.main.MnemonicActivity"
            android:exported="false"
            android:parentActivityName=".ui.main.MainActivity" />

'''
needle = "        <!-- Using an activity alias to disable/enable the app icon in the launcher -->\n"
if needle not in text:
    raise SystemExit("Unable to patch AndroidManifest.xml")
text = text.replace(needle, activity + needle, 1)
text = text.replace('android:name="com.osfans.trime.MainLauncherAlias"', 'android:name="com.iqsoo.rimehelper.MainLauncherAlias"', 1)
manifest.write_text(text, encoding="utf-8")

# Rebrand visible strings while keeping upstream attribution in the About screen.
for path in (root / "app/src/main/res").glob("values*/strings.xml"):
    text = path.read_text(encoding="utf-8")
    text = re.sub(
        r'<string name="app_name_release">.*?</string>',
        '<string name="app_name_release">Rime 助记输入法</string>',
        text,
    )
    text = re.sub(
        r'<string name="app_name_debug">.*?</string>',
        '<string name="app_name_debug">Rime 助记输入法</string>',
        text,
    )
    text = re.sub(
        r'<string name="trime_app_slogan">.*?</string>',
        '<string name="trime_app_slogan">双拼辅码与五笔86，完全离线</string>',
        text,
    )
    text = text.replace("<b>同文输入法</b>", "<b>Rime 助记输入法</b>")
    text = text.replace("<b>Trime</b>", "<b>Rime Helper</b>")
    path.write_text(text, encoding="utf-8")
PY

printf '\n==> Build signed debug APK for arm64-v8a\n'
chmod +x gradlew
export BUILD_ABI=arm64-v8a
export CI_NAME="iQSOO GitHub Actions"
export BUILD_GIT_REPO="https://github.com/iQSOO/xiaohe-gboard-converter/tree/rime-helper-apk"
./gradlew --no-daemon --stacktrace :app:assembleDebug

APK_PATH="$(find "$TRIME_DIR/app/build/outputs/apk" -type f -name '*.apk' | sort | head -n 1)"
if [[ -z "$APK_PATH" || ! -f "$APK_PATH" ]]; then
  echo "No APK was produced" >&2
  find "$TRIME_DIR/app/build/outputs" -maxdepth 5 -type f -print || true
  exit 1
fi

cp "$APK_PATH" "$OUTPUT_DIR/Rime-Helper-v1.0.0-arm64-v8a.apk"
cp "$TRIME_DIR/LICENSE" "$OUTPUT_DIR/GPL-3.0-LICENSE.txt"
cat > "$OUTPUT_DIR/OPEN_SOURCE_NOTICES.txt" <<'EOF'
Rime 助记输入法 v1.0.0

Android frontend: Trime 3.3.11 — GPL-3.0-or-later
Input engine: librime 1.17.0 — BSD-3-Clause
Pinyin dictionaries/configuration: rime-ice — GPL-3.0
Xiaohe auxiliary-code filter/data: Mintimate/oh-my-rime — see upstream license
Wubi86 schema/dictionary: rime/rime-wubi — see upstream license
Pinyin reverse lookup: rime/rime-pinyin-simp — see upstream license

Modifications:
- Independent package com.iqsoo.rimehelper
- Xiaohe Double Pinyin with Xiaohe shape auxiliary codes
- Xiaohe Double Pinyin with first-two-key Wubi86 auxiliary codes
- Standalone Wubi86 schema
- In-app mnemonic and learning guide
EOF

(
  cd "$OUTPUT_DIR"
  sha256sum Rime-Helper-v1.0.0-arm64-v8a.apk > SHA256SUMS.txt
)

printf '\nProduced files:\n'
ls -lh "$OUTPUT_DIR"
