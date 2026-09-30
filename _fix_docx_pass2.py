# -*- coding: utf-8 -*-
"""二次清理：剔除剩余 MyLunarCore / P21 / 不存在文档引用。"""
from __future__ import annotations

import copy
from pathlib import Path

from docx import Document
from docx.oxml.ns import qn
from docx.text.paragraph import Paragraph

SRC = Path(r"c:\Users\ASUS\Desktop\MyMmorpg项目全方位详细介绍文档.docx")


def delete_paragraph(paragraph: Paragraph) -> None:
    el = paragraph._element
    parent = el.getparent()
    if parent is not None:
        parent.remove(el)


def set_run_text(paragraph: Paragraph, text: str) -> None:
    if not paragraph.runs:
        paragraph.add_run(text)
        return
    paragraph.runs[0].text = text
    for r in paragraph.runs[1:]:
        r.text = ""


def main() -> None:
    doc = Document(str(SRC))
    deleted = 0
    rewritten = 0

    delete_contains = [
        "MyLunarCore",
        "docs/admin-api.md",
        "docs/adr/",
        "docs/feature-gap-fill",
        "docs/meta/cmdid",
        "docs/microservice-split-roadmap",
        "docs/multi-node-failover",
        "docs/production-hardening",
        "docs/kcp-crypto",
        "docs/observability-logging",
        "docs/load-test-baseline",
        "docs/ops-daily-checklist",
        "QUICKSTART.md",
        "CmdId 元数据（含 1020",
        "1020–1024 战斗 Auto",
        "附录 D",
        "附录 E",
        "microservices/README.md",
        "实验脚手架边界",
        "单体 Admin HTTP 摘要",
        "单体优先架构决策",
        "玩法/技术缺口",
        "多节点切服验证",
        "生产加固清单",
        "KCP AES-GCM",
        "微服务拆分路线（battle 不拆）",
        "docker-compose.dev.yml",
        "publish_drill",
        "k6_baseline",
        "ExperienceImmersion",
        "ExperienceLoop",
        "Admin :8080",
        "游戏 :9000",
    ]

    rewrite_map = {
        "日常运维核对（MyLunarCore 仓库无 troubleshooting.md）": "日常排障与拓扑（docs/troubleshooting.md）",
        "MyLunarCore 定位、启动、关键配置": "本仓库定位、启动、关键配置（README.md）",
    }

    keep_if_also = ["troubleshooting.md"]  # don't delete if we're rewriting

    for p in list(doc.paragraphs):
        t = (p.text or "").strip()
        if not t:
            continue
        if t in rewrite_map:
            set_run_text(p, rewrite_map[t])
            rewritten += 1
            continue
        # rewrite partial
        if "MyLunarCore 仓库无 troubleshooting" in t:
            set_run_text(p, "日常排障与拓扑")
            rewritten += 1
            continue
        if any(x in t for x in delete_contains):
            # exception: version ops section mentioning nothing lunar
            if "VersionTimeline" in t or "VersionGateKeeper" in t or "5.9.1" in t:
                continue
            if "CLIENT_TOO_OLD" in t and "MyLunarCore" not in t:
                continue
            delete_paragraph(p)
            deleted += 1

    # Ensure 10.1 已实现 mentions version ops
    for p in doc.paragraphs:
        t = p.text or ""
        if t.startswith("支付对账骨架、反作弊") and "版本运营自动化" not in t:
            set_run_text(
                p,
                t.rstrip("。")
                + "；版本运营自动化（VersionTimelineScheduler / VersionGateKeeper / 灰度 / ActivitySnapshot / CacheWarmUp / PostMortem / PatchBaseline）。",
            )
            rewritten += 1
            break

    # Fix story 11.4 to mention timeline
    for p in doc.paragraphs:
        t = p.text or ""
        if t.startswith("分阶段 reload；发布审计留痕"):
            set_run_text(
                p,
                "分阶段 reload；发布审计留痕；亦可写入 version_timeline 由调度器在生效秒自动 publish，或走灰度 gray-publish 后再全量。",
            )
            rewritten += 1
            break

    # Add missing real docs to map if absent
    has_ai = any("docs/ai-enhancement.md" in (p.text or "") for p in doc.paragraphs)
    if not has_ai:
        # find docs/troubleshooting or openapi row to insert nearby
        for p in doc.paragraphs:
            if (p.text or "").strip() == "docs/openapi.md":
                # insert before: create two paragraphs after previous? insert after this pair
                pass

    doc.save(str(SRC))
    print(f"pass2 done deleted={deleted} rewritten={rewritten}")

    # verify
    left = [p.text.strip() for p in doc.paragraphs if "MyLunarCore" in (p.text or "")]
    print("remaining MyLunarCore:", len(left))
    for x in left[:10]:
        print(" -", x[:120])


if __name__ == "__main__":
    main()
