#!/usr/bin/env python3
"""大世界混合压测客户端模型：跑图 + 采集 + 遇怪锁（调用 scene-service internal API）。

用法:
  uv pip install requests
  python scripts/open_world_mixed_load.py --base http://127.0.0.1:8082 --players 500
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.parse
import urllib.request


def post(url: str, timeout: float = 60.0) -> dict:
    req = urllib.request.Request(url, method="POST", data=b"")
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def main() -> int:
    parser = argparse.ArgumentParser(description="Open-world mixed load benchmark")
    parser.add_argument("--base", default="http://127.0.0.1:8082", help="scene-service base URL")
    parser.add_argument("--players", type=int, default=500)
    parser.add_argument("--gather", type=int, default=1000)
    parser.add_argument("--combat", type=int, default=500)
    parser.add_argument("--moves", type=int, default=5000)
    args = parser.parse_args()

    qs = urllib.parse.urlencode(
        {
            "players": args.players,
            "gatherOps": args.gather,
            "combatLocks": args.combat,
            "moveQueries": args.moves,
        }
    )
    url = f"{args.base.rstrip('/')}/internal/scene/open-world/load/mixed?{qs}"
    try:
        body = post(url)
    except urllib.error.URLError as e:
        print(f"request failed: {e}", file=sys.stderr)
        print("Ensure scene-service is up, or run unit OpenWorldRuntimeServiceTest offline.", file=sys.stderr)
        return 2
    print(json.dumps(body, ensure_ascii=False, indent=2))
    return 0 if body.get("ok") else 1


if __name__ == "__main__":
    raise SystemExit(main())
