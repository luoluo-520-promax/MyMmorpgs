#!/usr/bin/env python3
"""Flatten MMORPG Java packages: fewer top-level folders, easier for beginners."""
from __future__ import annotations

import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

REPLACEMENTS: list[tuple[str, str]] = [
    ("cn.itcast.demo.mymmorpg.infra.rpc.protostuff", "cn.itcast.demo.mymmorpg.rpc"),
    ("cn.itcast.demo.mymmorpg.infra.rpc.protocol", "cn.itcast.demo.mymmorpg.rpc"),
    ("cn.itcast.demo.mymmorpg.infra.rpc.internal", "cn.itcast.demo.mymmorpg.rpc"),
    ("cn.itcast.demo.mymmorpg.infra.rpc.config", "cn.itcast.demo.mymmorpg.rpc"),
    ("cn.itcast.demo.mymmorpg.infra.rpc.router", "cn.itcast.demo.mymmorpg.rpc"),
    ("cn.itcast.demo.mymmorpg.infra.rpc.future", "cn.itcast.demo.mymmorpg.rpc"),
    ("cn.itcast.demo.mymmorpg.infra.rpc.event", "cn.itcast.demo.mymmorpg.rpc"),
    ("cn.itcast.demo.mymmorpg.infra.rpc.codec", "cn.itcast.demo.mymmorpg.rpc"),
    ("cn.itcast.demo.mymmorpg.infra.rpc.data", "cn.itcast.demo.mymmorpg.rpc"),
    ("cn.itcast.demo.mymmorpg.infra.server.resource", "cn.itcast.demo.mymmorpg.net"),
    ("cn.itcast.demo.mymmorpg.infra.server.layer", "cn.itcast.demo.mymmorpg.net"),
    ("cn.itcast.demo.mymmorpg.buff.domain.periodic", "cn.itcast.demo.mymmorpg.model"),
    ("cn.itcast.demo.mymmorpg.function.infrastructure", "cn.itcast.demo.mymmorpg.config"),
    ("cn.itcast.demo.mymmorpg.activity.infrastructure", "cn.itcast.demo.mymmorpg.service"),
    ("cn.itcast.demo.mymmorpg.battle.infrastructure", "cn.itcast.demo.mymmorpg.service"),
    ("cn.itcast.demo.mymmorpg.buff.infrastructure", "cn.itcast.demo.mymmorpg.service"),
    ("cn.itcast.demo.mymmorpg.activity.application", "cn.itcast.demo.mymmorpg.service"),
    ("cn.itcast.demo.mymmorpg.battle.application", "cn.itcast.demo.mymmorpg.service"),
    ("cn.itcast.demo.mymmorpg.function.application", "cn.itcast.demo.mymmorpg.service"),
    ("cn.itcast.demo.mymmorpg.buff.application", "cn.itcast.demo.mymmorpg.service"),
    ("cn.itcast.demo.mymmorpg.activity.domain", "cn.itcast.demo.mymmorpg.model"),
    ("cn.itcast.demo.mymmorpg.function.domain", "cn.itcast.demo.mymmorpg.model"),
    ("cn.itcast.demo.mymmorpg.buff.domain", "cn.itcast.demo.mymmorpg.model"),
    ("cn.itcast.demo.mymmorpg.battle.domain", "cn.itcast.demo.mymmorpg.model"),
    ("cn.itcast.demo.mymmorpg.message.annotation", "cn.itcast.demo.mymmorpg.handler"),
    ("cn.itcast.demo.mymmorpg.message.dispatch", "cn.itcast.demo.mymmorpg.handler"),
    ("cn.itcast.demo.mymmorpg.message.facade", "cn.itcast.demo.mymmorpg.handler"),
    ("cn.itcast.demo.mymmorpg.message.packet", "cn.itcast.demo.mymmorpg.protocol"),
    ("cn.itcast.demo.mymmorpg.service.account", "cn.itcast.demo.mymmorpg.service"),
    ("cn.itcast.demo.mymmorpg.infra.transport", "cn.itcast.demo.mymmorpg.net"),
    ("cn.itcast.demo.mymmorpg.infra.server", "cn.itcast.demo.mymmorpg.net"),
    ("cn.itcast.demo.mymmorpg.infra.scheduler", "cn.itcast.demo.mymmorpg.support"),
    ("cn.itcast.demo.mymmorpg.infra.script", "cn.itcast.demo.mymmorpg.support"),
    ("cn.itcast.demo.mymmorpg.infra.util", "cn.itcast.demo.mymmorpg.support"),
    ("cn.itcast.demo.mymmorpg.infra.jmx", "cn.itcast.demo.mymmorpg.support"),
    ("cn.itcast.demo.mymmorpg.infra.mq", "cn.itcast.demo.mymmorpg.support"),
    ("cn.itcast.demo.mymmorpg.infra.web", "cn.itcast.demo.mymmorpg.web"),
    ("cn.itcast.demo.mymmorpg.infra.rpc", "cn.itcast.demo.mymmorpg.rpc"),
    ("cn.itcast.demo.mymmorpg.battle.config", "cn.itcast.demo.mymmorpg.config"),
    ("cn.itcast.demo.mymmorpg.battle.api", "cn.itcast.demo.mymmorpg.web"),
    ("cn.itcast.demo.mymmorpg.gateway.observe", "cn.itcast.demo.mymmorpg.gateway"),
    ("cn.itcast.demo.mymmorpg.module", "cn.itcast.demo.mymmorpg.protocol"),
    ("cn.itcast.demo.mymmorpg.security", "cn.itcast.demo.mymmorpg.support"),
    ("cn.itcast.demo.mymmorpg.activity", "cn.itcast.demo.mymmorpg.service"),
    ("cn.itcast.demo.mymmorpg.message", "cn.itcast.demo.mymmorpg.handler"),
    ("cn.itcast.demo.mymmorpg.battle", "cn.itcast.demo.mymmorpg.service"),
    ("cn.itcast.demo.mymmorpg.function", "cn.itcast.demo.mymmorpg.service"),
    ("cn.itcast.demo.mymmorpg.buff", "cn.itcast.demo.mymmorpg.service"),
    ("cn.itcast.demo.mymmorpg.infra", "cn.itcast.demo.mymmorpg.support"),
]

SKIP_FILES = {
    "BattleCommandClient.java",  # Feign removed after monolith merge
}

PREFER_BATTLE_SERVICE = {
    "BattleFacade.java",
    "BattleService.java",
    "BattleSceneFactory.java",
    "BattleEventPublisher.java",
    "InternalBattleController.java",
}

PACKAGE_RE = re.compile(r"^package\s+([\w.]+)\s*;", re.MULTILINE)


def remap_pkg(pkg: str) -> str:
    result = pkg
    for old, new in REPLACEMENTS:
        if result == old or result.startswith(old + "."):
            result = new + result[len(old) :]
    return result


def remap_text(text: str) -> str:
    for old, new in REPLACEMENTS:
        text = text.replace(old, new)
    return text


def pkg_to_dir(module_src: Path, pkg: str) -> Path:
    if pkg == "cn.itcast.demo.mymmorpg":
        return module_src / "java" / "cn" / "itcast" / "demo" / "mymmorpg"
    if pkg.startswith("cn.itcast.demo.mymmorpg."):
        sub = pkg[len("cn.itcast.demo.mymmorpg.") :].replace(".", "/")
        return module_src / "java" / "cn" / "itcast" / "demo" / "mymmorpg" / sub
    if pkg.startswith("jforgame."):
        return module_src / "java" / pkg.replace(".", "/")
    return module_src / "java" / pkg.replace(".", "/")


def module_src_root(java_file: Path) -> Path:
    parts = java_file.parts
    i = parts.index("src")
    return Path(*parts[: i + 1])


def collect_files() -> list[Path]:
    out: list[Path] = []
    for mod in ROOT.iterdir():
        if not mod.is_dir() or mod.name in {"tools", "target", ".git"}:
            continue
        src = mod / "src"
        if not src.exists():
            continue
        for p in src.rglob("*.java"):
            if "target" not in p.parts:
                out.append(p)
    return out


def pick_winner(dest_name: str, candidates: list[tuple[Path, str, str]]) -> tuple[Path, str, str]:
    if dest_name in SKIP_FILES:
        return candidates[0]  # will be dropped later
    if dest_name in PREFER_BATTLE_SERVICE:
        for c in candidates:
            if "battle-service" in str(c[0]):
                return c
    # prefer mmorpg-common over player duplicate facades
    if dest_name == "BattleFacade.java":
        for c in candidates:
            if "battle-service" in str(c[0]):
                return c
    for c in candidates:
        if "mmorpg-common" in str(c[0]):
            return c
    return candidates[0]


def main() -> None:
    files = collect_files()
    planned: dict[tuple[Path, Path], tuple[Path, str, str]] = {}

    for f in files:
        if f.name in SKIP_FILES:
            continue
        text = f.read_text(encoding="utf-8")
        m = PACKAGE_RE.search(text)
        if not m:
            continue
        old_pkg = m.group(1)
        new_pkg = remap_pkg(old_pkg)
        new_text = remap_text(text)
        new_text = PACKAGE_RE.sub(f"package {new_pkg};", new_text, count=1)
        src_root = module_src_root(f)
        dest = pkg_to_dir(src_root, new_pkg) / f.name
        key = (src_root, dest)
        planned.setdefault(key, [])
        planned[key].append((f, new_pkg, new_text))

    final_writes: list[tuple[Path, str]] = []
    for key, candidates in planned.items():
        src_root, dest = key
        if len(candidates) == 1:
            final_writes.append((dest, candidates[0][2]))
        else:
            winner = pick_winner(dest.name, candidates)
            print(f"duplicate {dest.name}: kept {winner[0]}")
            final_writes.append((dest, winner[2]))

    # wipe old package trees
    for mod in ["mmorpg-common", "player-service", "battle-service", "activity-service", "mmorpg-gateway"]:
        for kind in ["main", "test"]:
            base = ROOT / mod / "src" / kind / "java" / "cn" / "itcast" / "demo" / "mymmorpg"
            if base.exists():
                shutil.rmtree(base)
        # jforgame only in common
    jfg = ROOT / "mmorpg-common" / "src" / "main" / "java" / "jforgame"
    jfg_test = ROOT / "mmorpg-common" / "src" / "test" / "java" / "jforgame"
    # preserve jforgame - we didn't delete it

    for dest, content in final_writes:
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_text(content, encoding="utf-8")

    print(f"Wrote {len(final_writes)} Java files.")


if __name__ == "__main__":
    main()
