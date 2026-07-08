#!/usr/bin/env python3
"""Fix UTF-8 corruption from PowerShell Set-Content -NoNewline batch replace."""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODULES = ["mmorpg-common", "player-service", "battle-service", "activity-service"]


def sanitize(text: str) -> str:
    # Remove replacement chars from truncated multi-byte sequences
    text = text.replace("\ufffd", "")

    # Remove standard file maintenance header (Chinese or mojibake)
    text = re.sub(
        r"/\*\*\s*\n(?:\s*\*[^\n]*\n)*?\s*\*[^\n]*(?:文件维护|æ–‡ä»¶|æ\x96\x87ä»¶)[^\n]*\n(?:\s*\*[^\n]*\n)*?\s*\*/\s*\n",
        "",
        text,
        count=1,
    )

    # Fix merged header lines: "扩展。 * 4)" -> separate lines
    text = re.sub(
        r"(扩展[。]?)\s*\*\s*(\d\))",
        r"\1\n * \2)",
        text,
    )

    # Fix broken HTML comment closings
    text = re.sub(r"ã?/p>", "。</p>", text)
    text = re.sub(r"([^<])?/p>", r"。</p>", text)

    # Fix broken string literals (truncated Chinese before comma + request)
    text = re.sub(
        r'"服务器内部错[^"]*?, request\)',
        '"服务器内部错误", request)',
        text,
    )
    text = re.sub(
        r'log\.error\("未处理异[^"]*?path=',
        'log.error("未处理异常 path=',
        text,
    )

    # Fix comments ending with truncated punctuation before closing */
    text = re.sub(r"扩展\s*\*/", "扩展。\n */", text)

    return text


def fix_file(path: Path) -> bool:
    raw = path.read_bytes()
    try:
        text = raw.decode("utf-8")
        had_invalid = False
    except UnicodeDecodeError:
        text = raw.decode("utf-8", errors="replace")
        had_invalid = True

    if "\ufffd" in text or had_invalid:
        new_text = sanitize(text)
        if new_text != text:
            with open(path, "w", encoding="utf-8", newline="\n") as f:
                f.write(new_text)
            return True
    elif sanitize(text) != text:
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(sanitize(text))
        return True
    return False


def main() -> int:
    fixed = []
    for mod in MODULES:
        base = ROOT / mod / "src"
        if not base.exists():
            continue
        for path in base.rglob("*.java"):
            if fix_file(path):
                fixed.append(path)

    print(f"Fixed {len(fixed)} files")
    for p in fixed:
        print(p.relative_to(ROOT))
    return 0


if __name__ == "__main__":
    sys.exit(main())
