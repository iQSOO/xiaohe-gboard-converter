#!/usr/bin/env python3
from pathlib import Path

script = Path(__file__).with_name("patch_bili_iqsoo_perf_v101.py")
code = compile(script.read_text(encoding="utf-8"), str(script), "exec")
exec(code, {"__name__": "__main__", "__file__": str(script)})

policy = Path.cwd() / "app/src/main/java/com/android/purebilibili/feature/settings/policy/ReleaseChannelDisclaimer.kt"
text = policy.read_text(encoding="utf-8")
text = text.replace(
    "import androidx.compose.runtime.Composable\n",
    "import androidx.compose.runtime.Composable\nimport androidx.compose.ui.Modifier\n",
)
policy.write_text(text, encoding="utf-8")
