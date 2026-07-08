# -*- coding: utf-8 -*-
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

FIXES = {
    "mmorpg-gateway/src/main/java/cn/itcast/demo/mymmorpg/gateway/AuthGlobalFilter.java": [
        (r'writeUnauthorized\(exchange, "[^"]*"\)', None),  # handled below
    ],
}

def fix_auth_filter(path: Path):
    text = path.read_text(encoding="utf-8")
    text = re.sub(
        r'return writeUnauthorized\(exchange, "[^"]*"\);.*',
        'return writeUnauthorized(exchange, "未提供登录令牌"); // Token 为空时返回 401',
        text,
        count=1,
    )
    text = re.sub(
        r'return writeUnauthorized\(exchange, "[^"]*"\);.*',
        'return writeUnauthorized(exchange, "登录令牌无效或已过期"); // Redis 无有效映射时返回 401',
        text,
        count=1,
    )
    path.write_text(text, encoding="utf-8")


def fix_rate_limit_filter(path: Path):
    text = path.read_text(encoding="utf-8")
    text = re.sub(
        r'return writeTooManyRequests\(exchange, "[^"]*"\);.*',
        'return writeTooManyRequests(exchange, "全站请求过于频繁"); // 全局限流触发',
        text,
        count=1,
    )
    text = re.sub(
        r'return writeTooManyRequests\(exchange, "[^"]*"\);.*',
        'return writeTooManyRequests(exchange, "您的请求过于频繁，请稍后再试"); // 用户维度限流触发',
        text,
        count=1,
    )
    path.write_text(text, encoding="utf-8")


def fix_question_comment_lines(path: Path):
    text = path.read_text(encoding="utf-8")
    if not re.search(r"\?{2,}", text):
        return False
    lines = []
    for line in text.splitlines(keepends=True):
        if re.search(r"//\s*\?+", line) and "writeUnauthorized" not in line and "writeTooManyRequests" not in line:
            code = line.split("//")[0].rstrip()
            if code.strip():
                lines.append(code + " // 执行业务逻辑\n")
            else:
                lines.append(line)
        else:
            lines.append(line)
    path.write_text("".join(lines), encoding="utf-8")
    return True


if __name__ == "__main__":
    auth = ROOT / "mmorpg-gateway/src/main/java/cn/itcast/demo/mymmorpg/gateway/AuthGlobalFilter.java"
    rate = ROOT / "mmorpg-gateway/src/main/java/cn/itcast/demo/mymmorpg/gateway/RateLimitGlobalFilter.java"
    fix_auth_filter(auth)
    fix_rate_limit_filter(rate)
    fix_question_comment_lines(auth)
    fix_question_comment_lines(rate)
    print("Gateway filter strings and comments fixed.")
