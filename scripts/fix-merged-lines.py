#!/usr/bin/env python3
"""Fix merged Java lines caused by UTF-8 corruption (comments + code on same line)."""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MODULES = ["mmorpg-common", "player-service", "battle-service", "activity-service"]

# ?import on same line
RE_IMPORT = re.compile(r"(?<=\?)(import )")
# ? + spaces + code keyword
RE_CODE_AFTER_Q = re.compile(
    r"\?(?=\s{2,}(?:"
    r"if |return |private |public |protected |static |final |var |case |default |"
    r"throw |try |catch |for |@PostMapping|@Transactional|@Managed|"
    r"stringRedisTemplate|currencyMap|deleteState|syncBuilder|grantList|"
    r"progressStore|out\.|toGrant|items |ProtocolMessage |BattleRuntimeState |"
    r"PlayerActivityProgress|RewardTierPayload|String |Optional|ActivityConfigPayload|"
    r"long |int |void |boolean |List<|@RequestHandler|\.set|\.add|import "
    r"))"
)
# comment then closing brace on same line as statement
RE_STMT_BRACE = re.compile(r"(;)( //[^}]*?)( {4,}\})")
# comment then method/class closing brace after return/statement
RE_STMT_METHOD_END = re.compile(r"(;)( //[^}]*?)( {4,}\})( //[^\n]*)$")
# broken annotation missing closing quote
# broken @ManagedOperation description line (missing closing quote)
RE_ANNOTATION = re.compile(
    r"@ManagedOperation\(description = \"按模\?ID 移除实体上的 Buff（主动取消原因码\?\)\s*\n"
)
RE_ANNOTATION_FIX = '@ManagedOperation(description = "按模板 ID 移除实体上的 Buff（主动取消原因码）")\n'
# @PostMapping merged with public method
RE_POSTMAPPING = re.compile(r"(// 开始战\?)\s+(public ResponseEntity)")
RE_POSTMAPPING_FIX = r"\1\n    \2"
# comment-only import lines: // ...import
RE_COMMENT_IMPORT = re.compile(r"^//[^\n]*?(import cn\.itcast)")
# forEach merged
RE_FOREACH = re.compile(r"(//[^\n]*?)( {4,})(activities\.forEach|items\.forEach|rewards\.forEach)")


def split_line(line: str) -> list[str]:
    if "//" not in line and "?" not in line:
        return [line]
    s = line
    s = RE_IMPORT.sub(r"\n\1", s)
    s = RE_CODE_AFTER_Q.sub("\n", s)
    s = RE_STMT_BRACE.sub(r"\1\2\n\3", s)
    s = RE_FOREACH.sub(r"\1\n\3", s)
    m = RE_COMMENT_IMPORT.search(s)
    if m:
        s = s[m.start(1):]
    return s.split("\n")


def fix_text(text: str) -> str:
    text = RE_ANNOTATION.sub(RE_ANNOTATION_FIX, text)
    text = RE_POSTMAPPING.sub(RE_POSTMAPPING_FIX, text)
    lines = text.split("\n")
    out: list[str] = []
    for line in lines:
        if line.strip().startswith("//") and "import cn.itcast" in line:
            idx = line.find("import cn.itcast")
            out.append(line[idx:])
            continue
        if re.match(r"^// \?+$", line.strip()):
            continue
        out.extend(split_line(line))
    return "\n".join(out)


def fix_file(path: Path) -> bool:
    text = path.read_text(encoding="utf-8", errors="replace")
    new_text = fix_text(text)
    if new_text != text:
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(new_text)
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
    print(f"Fixed merged lines in {len(fixed)} files")
    return 0


if __name__ == "__main__":
    sys.exit(main())
