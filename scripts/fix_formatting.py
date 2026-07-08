# -*- coding: utf-8 -*-
"""修复字段/注解粘连（`;@Column` 等）并恢复 gateway 过滤器文件头。"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

GATEWAY_HEADERS = {
    "mmorpg-gateway/src/main/java/cn/itcast/demo/mymmorpg/gateway/AuthGlobalFilter.java": "认证全局过滤器：校验 Token 并注入 X-Account-Id。",
    "mmorpg-gateway/src/main/java/cn/itcast/demo/mymmorpg/gateway/RateLimitGlobalFilter.java": "限流全局过滤器：基于 Redis 的全站与用户维度 QPS 控制。",
}


def fix_semicolon_annotation(path: Path):
    text = path.read_text(encoding="utf-8")
    new = re.sub(r";(@\w+)", r";\n    \1", text)
    new = re.sub(r";(@Override)", r";\n\n    \1", new)
    if new != text:
        path.write_text(new, encoding="utf-8")
        return True
    return False


def rewrite_gateway_header(rel: str, duty: str):
    path = ROOT / rel
    text = path.read_text(encoding="utf-8")
    # 去掉旧文件头（到 package 之前）
    m = re.search(r"\bpackage\s+", text)
    if not m:
        return
    body = text[m.start():]
    header = (
        "/**\n"
        " * 文件维护说明\n"
        f" * 1) 文件路径：{rel}\n"
        " * 2) 所属模块：mmorpg-gateway\n"
        f" * 3) 主要职责：{duty}\n"
        " * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。\n"
        " * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。\n"
        " */\n"
    )
    path.write_text(header + body, encoding="utf-8")
    print("header:", rel)


if __name__ == "__main__":
    for rel, duty in GATEWAY_HEADERS.items():
        rewrite_gateway_header(rel, duty)
    count = 0
    for path in ROOT.rglob("*.java"):
        if "target" in path.parts:
            continue
        if fix_semicolon_annotation(path):
            count += 1
    print(f"semicolon-annotation fixes: {count}")
