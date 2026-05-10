#!/usr/bin/env python3
"""Convert Chinese words to XiaoHe double-pinyin shortcuts for Gboard.

Default input:  dictionary.txt
Default output: gboard_xiaohe.txt

Output format is compatible with Gboard personal dictionary import:
    word<TAB>shortcut<TAB>locale
"""

from __future__ import annotations

import argparse
import re
from pathlib import Path

try:
    from pypinyin import Style, pinyin
except ImportError as exc:  # pragma: no cover
    raise SystemExit("缺少依赖：pypinyin。请先运行：pip install -r requirements.txt") from exc

XIAOHE_FINALS = {
    "a": "a", "ai": "d", "an": "j", "ang": "h", "ao": "c",
    "e": "e", "ei": "w", "en": "f", "eng": "g", "er": "r",
    "o": "o", "ou": "z", "ong": "s",
    "i": "i", "ia": "x", "ian": "m", "iang": "l", "iao": "n", "ie": "p",
    "in": "b", "ing": "k", "iong": "s", "iu": "q",
    "u": "u", "ua": "x", "uai": "k", "uan": "r", "uang": "l", "ue": "t",
    "ui": "v", "un": "y", "uo": "o",
    "v": "v", "ve": "t", "van": "t", "vn": "y",
    "ü": "v", "üe": "t", "üan": "t", "ün": "y",
}

XIAOHE_INITIALS = {
    "b": "b", "p": "p", "m": "m", "f": "f", "d": "d", "t": "t", "n": "n", "l": "l",
    "g": "g", "k": "k", "h": "h", "j": "j", "q": "q", "x": "x",
    "zh": "v", "ch": "i", "sh": "u", "r": "r", "z": "z", "c": "c", "s": "s",
    "y": "y", "w": "w", "": "",
}

CJK_RE = re.compile(r"[\u4e00-\u9fff]")


def normalize_final(final: str) -> str:
    """Normalize pypinyin finals to XiaoHe table keys."""
    final = re.sub(r"[1-5]$", "", final.strip().lower())
    return final.replace("u:", "ü")


def convert_word_to_xiaohe(word: str) -> str:
    """Return XiaoHe double-pinyin shortcut for a Chinese word."""
    clean_word = word.strip()
    if not clean_word:
        return ""

    initials = pinyin(clean_word, style=Style.INITIALS, strict=False, errors="ignore")
    finals = pinyin(clean_word, style=Style.FINALS_TONE3, strict=False, errors="ignore")

    codes: list[str] = []
    for sm_item, ym_item in zip(initials, finals):
        initial = sm_item[0] if sm_item else ""
        final = normalize_final(ym_item[0] if ym_item else "")
        if not final and not initial:
            continue
        codes.append(XIAOHE_INITIALS.get(initial, "") + XIAOHE_FINALS.get(final, final[:1]))

    return "".join(codes)


def extract_word(line: str) -> str:
    """Extract a Chinese word from common dictionary line formats."""
    text = line.strip().lstrip("\ufeff")
    if not text or text.startswith("#"):
        return ""

    parts = [part.strip() for part in re.split(r"[\t, ]+", text) if part.strip()]
    cjk_parts = [part for part in parts if CJK_RE.search(part)]
    if cjk_parts:
        return cjk_parts[0]
    return text if CJK_RE.search(text) else ""


def convert_file(input_path: Path, output_path: Path, locale: str) -> int:
    seen: set[str] = set()
    converted = 0

    with input_path.open("r", encoding="utf-8") as infile, output_path.open("w", encoding="utf-8", newline="\n") as outfile:
        outfile.write("# Gboard Dictionary version:1\n")
        for raw_line in infile:
            word = extract_word(raw_line)
            if not word or word in seen:
                continue
            shortcut = convert_word_to_xiaohe(word)
            if not shortcut:
                continue
            outfile.write(f"{word}\t{shortcut}\t{locale}\n")
            seen.add(word)
            converted += 1

    return converted


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Convert Chinese dictionary words to XiaoHe shortcuts for Gboard.")
    parser.add_argument("-i", "--input", default="dictionary.txt", help="input dictionary file, default: dictionary.txt")
    parser.add_argument("-o", "--output", default="gboard_xiaohe.txt", help="output file, default: gboard_xiaohe.txt")
    parser.add_argument("--locale", default="zh-CN", help="Gboard locale, default: zh-CN")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    input_path = Path(args.input)
    output_path = Path(args.output)

    if not input_path.is_file():
        raise SystemExit(f"输入文件不存在：{input_path}")

    count = convert_file(input_path, output_path, args.locale)
    print(f"完成转换：{count} 条词库记录 -> {output_path}")


if __name__ == "__main__":
    main()
