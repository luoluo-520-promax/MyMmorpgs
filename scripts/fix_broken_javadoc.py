# -*- coding: utf-8 -*-
"""修复注解脚本导致的未闭合 Javadoc（常量行末尾 /** 或 /** 未闭合）。"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# 手工修复映射：文件相对路径 -> [(错误片段起始, 正确替换)]
MANUAL = {
    "mmorpg-common/src/main/java/cn/itcast/demo/mymmorpg/protocol/RetCode.java": [
        (
            "public static final int NOT_LOGGED_IN = 5;/** 服务器当前在线人数过多，暂时拒绝新登录。\n    public static final int SERVER_OVERLOADED = 6;",
            "public static final int NOT_LOGGED_IN = 5;\n    /** 服务器当前在线人数过多，暂时拒绝新登录 */\n    public static final int SERVER_OVERLOADED = 6;",
        ),
        (
            "/** 技。\n    public static final int SKILL_NOT_FOUND = 50;",
            "/** 技能相关错误码 */\n    public static final int SKILL_NOT_FOUND = 50;",
        ),
    ],
    "mmorpg-common/src/main/java/cn/itcast/demo/mymmorpg/protocol/ChatRetCode.java": [
        (
            "public static final int OK = 0;/** 消息内容包含敏感。\n    public static final int SENSITIVE = 1;",
            "public static final int OK = 0;\n    /** 消息内容包含敏感词 */\n    public static final int SENSITIVE = 1;",
        ),
        (
            "/** 频道未解锁或不可。\n    public static final int CHANNEL_UNAVAILABLE = 4;",
            "/** 频道未解锁或不可用 */\n    public static final int CHANNEL_UNAVAILABLE = 4;",
        ),
    ],
}


def fix_unclosed_block_comments(text: str) -> str:
    """将 `;/** 未闭合` 拆成独立 Javadoc 块。"""
    def repl(m):
        const_line = m.group(1)
        comment = m.group(2).strip()
        if not comment.endswith("*/"):
            comment = comment.rstrip("。").rstrip(".") + " */"
        else:
            comment = "*/"
        return f"{const_line};\n    /** {comment}\n"
    # 常量行后紧跟未闭合注释
    text = re.sub(
        r"(public static final int \w+ = \d+);/\*\* ([^\n]+)\n",
        repl,
        text,
    )
    # /** 单行未闭合（下一行是 public static）
    text = re.sub(
        r"/\*\* ([^\n*]+)\n(\s+public static)",
        r"/\*\* \1 */\n\2",
        text,
    )
    return text


def fix_file(rel: str):
    path = ROOT / rel
    text = path.read_text(encoding="utf-8")
    original = text
    for old, new in MANUAL.get(rel, []):
        text = text.replace(old, new)
    text = fix_unclosed_block_comments(text)
    if text != original:
        path.write_text(text, encoding="utf-8")
        print("fixed:", rel)


if __name__ == "__main__":
    for rel in MANUAL:
        fix_file(rel)
    # 扫描全项目
    for path in ROOT.rglob("*.java"):
        if "target" in path.parts:
            continue
        rel = path.relative_to(ROOT).as_posix()
        if rel in MANUAL:
            continue
        text = path.read_text(encoding="utf-8")
        if re.search(r"= \d+;/\*\*", text) or re.search(r"/\*\*[^\n]+\n\s+public static", text):
            new = fix_unclosed_block_comments(text)
            if new != text:
                path.write_text(new, encoding="utf-8")
                print("auto-fixed:", rel)
