#!/usr/bin/env python3
from pathlib import Path

script = Path(__file__).with_name("patch_bili_iqsoo_perf_v101.py")
code = compile(script.read_text(encoding="utf-8"), str(script), "exec")
exec(code, {"__name__": "__main__", "__file__": str(script)})
