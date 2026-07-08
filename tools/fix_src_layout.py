#!/usr/bin/env python3
"""Move src/java -> src/main/java or src/test/java based on *Test*.java or IT suffix."""
from pathlib import Path
import shutil

ROOT = Path(__file__).resolve().parents[1]
MODULES = ["mmorpg-common", "player-service", "battle-service", "activity-service", "mmorpg-gateway"]

TEST_MARKERS = ("Test.java", "IT.java", "Tests.java")


def is_test_file(name: str) -> bool:
    return any(name.endswith(m) for m in TEST_MARKERS) or name.endswith("Test.java")


def move_tree(src_java: Path, module: Path) -> None:
    if not src_java.exists():
        return
    for java in list(src_java.rglob("*.java")):
        rel = java.relative_to(src_java)
        dest_root = module / "src" / ("test" if is_test_file(java.name) else "main") / "java"
        dest = dest_root / rel
        dest.parent.mkdir(parents=True, exist_ok=True)
        if dest.exists():
            dest.unlink()
        shutil.move(str(java), str(dest))
    # cleanup empty dirs
    if src_java.exists():
        shutil.rmtree(src_java, ignore_errors=True)


def merge_battle_into_common() -> None:
    battle_src = ROOT / "battle-service" / "src"
    common_main = ROOT / "mmorpg-common" / "src" / "main" / "java"
    common_test = ROOT / "mmorpg-common" / "src" / "test" / "java"
    for kind in ["main", "test"]:
        src = battle_src / kind / "java"
        if not src.exists():
            # try wrong layout
            wrong = battle_src / "java"
            if wrong.exists() and kind == "main":
                src = wrong
            else:
                continue
        dest = common_main if kind == "main" else common_test
        for java in src.rglob("*.java"):
            rel = java.relative_to(src)
            target = dest / rel
            if target.exists() and "battle-service" not in str(java):
                continue
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(java, target)
            print(f"copy battle -> {target.relative_to(ROOT)}")


def main() -> None:
    for mod in MODULES:
        wrong = ROOT / mod / "src" / "java"
        move_tree(wrong, ROOT / mod)
    merge_battle_into_common()
    print("Layout fixed.")


if __name__ == "__main__":
    main()
