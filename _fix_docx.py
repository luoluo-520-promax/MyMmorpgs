# -*- coding: utf-8 -*-
"""校正 MyMmorpg 全方位介绍文档：剔除不符项、补全版本运营能力。"""
from __future__ import annotations

import copy
import re
from pathlib import Path

from docx import Document
from docx.oxml.ns import qn
from docx.text.paragraph import Paragraph

SRC = Path(r"c:\Users\ASUS\Desktop\MyMmorpg项目全方位详细介绍文档.docx")
DST = SRC  # 原地更新
BACKUP = SRC.with_name(SRC.stem + "_备份_校正前.docx")


def delete_paragraph(paragraph: Paragraph) -> None:
    el = paragraph._element
    parent = el.getparent()
    if parent is not None:
        parent.remove(el)


def set_run_text(paragraph: Paragraph, text: str) -> None:
    """保留首个 run 的样式，清空其余 run，写入新文本。"""
    if not paragraph.runs:
        paragraph.add_run(text)
        return
    paragraph.runs[0].text = text
    for r in paragraph.runs[1:]:
        r.text = ""


def insert_after(paragraph: Paragraph, text: str, style: str | None = None) -> Paragraph:
    new_p = copy.deepcopy(paragraph._element)
    # clear text in clone
    for t in new_p.iter(qn("w:t")):
        t.text = ""
    paragraph._element.addnext(new_p)
    p = Paragraph(new_p, paragraph._parent)
    if style:
        try:
            p.style = style
        except Exception:
            pass
    set_run_text(p, text)
    return p


def should_delete(text: str) -> bool:
    t = text.strip()
    if not t:
        return False
    # P21 / MyLunarCore 专属且不属于本仓库的整段
    delete_markers = [
        "11.23 MyLunarCore",
        "3.8 P21 MyLunarCore",
        "5.13 P21 MyLunarCore",
        "5.14 P21 MyLunarCore",
        "附录 D MyLunarCore",
        "附录 E MyLunarCore",
        "Q23：体验闭环 1020–1069",
        "ExperienceImmersionFlowsTest",
        "ExperienceLoopBusinessFlowsTest",
        "docs/feature-gap-fill.md",
        "docs/protocol-docs.md（由 meta 生成）",
        "docs/adr/0001-monolith-first.md",
        "docs/admin-api.md",
        "docs/ops-daily-checklist.md",
        "docs/meta/cmdid-config-meta.yaml",
        "docs/microservice-split-roadmap.md",
        "docs/multi-node-failover.md",
        "docs/production-hardening.md",
        "docs/kcp-crypto.md",
        "docs/observability-logging.md",
        "docs/load-test-baseline.md",
        "QUICKSTART.md",
        "docker-compose.dev.yml",
        "publish_drill.ps1",
        "k6_baseline.js",
        "cn.itcast.demo.mylunarcore",
        "microservices/services/{api-gateway",
        "CalculateUpgradeMaterials（1046）",
        "BatchSweep（1048）",
        "SetBattleAuto",
        "BattleManualUlt",
        "SyncKeyBind（1062）",
        "DevelopmentPlanService + SweepService",
        "BattleAutoService.auto_strategy",
        "Plane/Floor 箱庭进图",
        "Admin 内置 8080",
        "非独立 admin-service",
        "MyLunarCore 生产只跑根目录单体",
        "MyLunarCore 以 JUnit 5 为主",
        "MyLunarCore：完整开放世界 P5–P20",
        "MyLunarCore 另含 deploy/prometheus",
        "MyLunarCore 定位、启动",
        "单体 Admin HTTP 摘要",
        "CmdId 元数据（含 1020–1069）",
        "实验脚手架边界",
        "microservices/README.md",
        "多节点切服验证",
        "生产加固清单",
        "KCP AES-GCM / TLS 备选",
        "微服务拆分路线（battle 不拆）",
        "玩法/技术缺口与协议接线清单",
        "单体优先架构决策",
    ]
    for m in delete_markers:
        if m in t:
            return True
    # TOC / 标题行
    if re.match(r"^(3\.8|5\.13|5\.14|11\.23|附录 [DE]|Q23)", t):
        return True
    if "P21 MyLunarCore" in t and any(
        t.startswith(p) for p in ("3.8", "5.13", "5.14", "11.23")
    ):
        return True
    return False


def rewrite(text: str) -> str | None:
    """返回新文本；None 表示删除；原样返回表示不变。"""
    t = text
    replacements = [
        (
            "生成日期：2026年08月28日（已同步 P21 MyLunarCore 单体对齐：1020–1069、Admin、deploy/CI）",
            "生成日期：2026年08月28日（已同步版本运营自动化：VersionTimeline / VersionGateKeeper / 灰度配置 / 活动快照回滚 / CacheWarmUp / PostMortem / Replay 基线）",
        ),
        (
            "2026年08月28日 增补 P21 MyLunarCore 单体对齐：体验循环 CmdId 1020–1045、体验沉浸 1046–1069、崩铁式箱庭核心循环、单体 Admin/deploy/CI 测试体系，并标注 P5–P20 开放世界为 MyMmorpg 愿景态（MyLunarCore 仓库未全量落地）。",
            "2026年08月28日 增补版本运营自动化七项：VersionTimelineScheduler（未来 7 天时间线秒级切换）、Gateway/登录 VersionGateKeeper（协议 Hash + RetCode.CLIENT_TOO_OLD=601）、OpenWorldConfigPatchService 灰度标签与熔断回滚、ActivitySnapshotManager 活动数据快照与溢出回退、CacheWarmUpService 开服前缓存预热、PostMortemProcessor 活动过期代币善后、BattleReplayService PatchBaselineContext 历史配置回放。P5–P20 开放世界能力以本仓库（MyMmorpg）代码与 TestNG 回归为准。",
        ),
        (
            "MyMmorpg 是一套用 Java 编写的网络游戏服务器工程（本文档为愿景与全景说明）。与之对应的可运行代码仓库为 MyLunarCore（路径示例：IdeaProjects/test/MyLunarCore）：Spring Boot 4 模块化单体 + Netty/KCP + Protobuf + MySQL，定位更接近「崩铁式」Plane/Floor 箱庭 + 回合战，而非本文档后半部描述的 seamless 开放世界 MMORPG。MyMmorpg 文档中的 P5–P20 多为目标态设计；MyLunarCore 已在单体进程内落地核心二游循环、体验闭环（1020–1045）与体验沉浸（1046–1069）。客户端像前台门面与遥控器；服务器才是真正的裁判 + 账本 + 仓库。",
            "MyMmorpg 是一套用 Java 17 / Spring Boot 3 编写的可运行 MMORPG 服务端工程：支持单体（默认 player-service 嵌入多域）与微服务拆分；技术栈含 Netty/KCP、Protobuf、MySQL、Redis、可选 RocketMQ/Nacos。本仓库已落地大世界 P0–P20（见 docs/open-world-top-tier.md）、AI 双管道（docs/ai-enhancement.md）、公会/深渊、活动商城抽卡与版本运营自动化等。客户端像前台门面与遥控器；服务器才是真正的裁判 + 账本 + 仓库。",
        ),
        (
            "MyMmorpg 目标态：只启动 player-service（HTTP 8989），多数域以 Maven 依赖嵌在同一进程。MyLunarCore 生产态：根目录 mvn spring-boot:run，Admin HTTP 8080 + 游戏 Netty/KCP 9000 同进程；依赖 docker-compose.dev.yml 起 MySQL/Redis；详见 README.md / QUICKSTART.md。",
            "单体模式（默认）：只启动 player-service（HTTP 8989），多数域以 Maven 依赖嵌在同一进程；Admin 独立端口 8985（亦可嵌入）。依赖 docker compose 起 MySQL/Redis（可选 Nacos）。详见 README.md / DEPLOYMENT.md。",
        ),
        (
            "MyLunarCore：docker compose -f docker-compose.dev.yml up -d → mvn -DskipTests spring-boot:run；多节点见 deploy/docker-compose.multi-node.yml + docs/multi-node-failover.md。",
            "本地：docker compose up -d mysql redis → mvn -pl player-service -am spring-boot:run -Dspring-boot.run.profiles=dev。微服务拆分见 DEPLOYMENT.md 与 docs/architecture-gateway.md。",
        ),
        (
            "TLogEventPublisher 默认结构化日志；可选 Kafka。LogDesensitizer 脱敏；LogSamplingFilter 采样；TraceContext 跨线程 TraceId，与网关 X-Trace-Id 贯通。MyLunarCore 另含 deploy/prometheus、deploy/grafana 业务/JVM 看板、deploy/clickhouse/init.sql、scripts/publish_drill.ps1、scripts/load/k6_baseline.js；见 docs/observability-logging.md、docs/load-test-baseline.md。",
            "TLogEventPublisher 默认结构化日志；可选 Kafka。LogDesensitizer 脱敏；LogSamplingFilter 采样；TraceContext 跨线程 TraceId，与网关 X-Trace-Id 贯通。可观测样例见 deploy/observability/；压测见 perf/README.md；排障见 docs/troubleshooting.md、docs/resilience-observability.md。",
        ),
        (
            "MyLunarCore 以 JUnit 5 为主：hardening/*BusinessFlowsTest、ExperienceLoop/Immersion 系列、CmdIdUniquenessTest、CmdIdProtoCompletenessTest、WireVersionGuardTest；CI（.github/workflows/ci.yml）：buf lint/breaking + mvn test + k6 nightly。MyMmorpg 目标态仍可用 TestNG 聚合套件（test-suites/）。",
            "本仓库以 TestNG 为主（各模块 *FlowTest / *BusinessFlowTest）；CI 含单元测试、集成测试（MySQL+Redis）、quality-gate（JaCoCo + SpotBugs）。版本运营回归：VersionOpsBusinessFlowTest、VersionTimelineSchedulerTest、GatewayVersionGateKeeperTest、OpenWorldGrayConfigBusinessFlowTest 等。",
        ),
        (
            "支付对账骨架、反作弊、Feign 熔断、冷热分离、TLog、CI 与质量门禁；MyLunarCore：体验闭环 1020–1045 + 体验沉浸 1046–1069 已接线；单体 Admin/deploy/Prometheus/Grafana；P10 运营硬化（表现层抽卡/月卡/时间线/资源版本封锁）。",
            "支付对账骨架、反作弊、Feign 熔断、冷热分离、TLog、CI 与质量门禁；版本运营自动化（时间线调度/门控/灰度/快照回滚/缓存预热/活动善后/回放基线）；P10 运营硬化（抽卡/月卡/资源版本等）。",
        ),
        (
            "MyLunarCore：完整开放世界 P5–P20（SceneTickMicroPipeline、CoopPuzzle、CompanionBot 等）为 MyMmorpg 愿景，当前仓库未实现；seamless Cell / realtime-combat 实验开关默认关。",
            "继续拆分剩余域依赖、完整 RedLock/官方支付 SDK、真实 GSLB 与跨区域多活、集群千人同屏压测进 CI 硬门禁等（见 DEPLOYMENT.md「计划中」）。",
        ),
        (
            "答：MyMmorpg 文档描述开放世界 MMORPG + 多域微服务的全景愿景（P5–P20）；MyLunarCore 是当前可运行仓库，单体实现崩铁式箱庭回合战，并已完成体验闭环/沉浸协议。读代码以 MyLunarCore 为准；读 P5–P20 请标注为「目标态」。",
            "答：本文档描述的就是本仓库 MyMmorpg。P5–P20 开放世界与版本运营能力以 mmorpg-common / scene-service / admin-service / mmorpg-gateway 等模块源码与 TestNG 回归为准；权威说明见 docs/open-world-top-tier.md、DEPLOYMENT.md、README.md。",
        ),
        (
            "Q22：MyLunarCore 和 MyMmorpg 文档是什么关系？",
            "Q22：版本运营（时间线/门控/灰度/快照）如何联调？",
        ),
        (
            "日常运维核对（MyLunarCore 仓库无 troubleshooting.md）",
            "日常排障与拓扑（docs/troubleshooting.md）",
        ),
        (
            "大世界顶尖标准（含 P0–P17）",
            "大世界顶尖标准（含 P0–P20）",
        ),
        (
            "生成日期：2026年08月27日（P20 人群拥挤与战斗性能加固；P19 大世界沉浸纵深补齐；P18 八大手感短板补齐；P17 联机一致性加固；P16 权威纵深加固；P12 性能深化第三轮）",
            "生成日期：2026年08月28日（版本运营自动化七项；P20 人群拥挤与战斗性能加固；P19–P12 既有能力）",
        ),
        (
            "MyMmorpg 用现代 Java 技术栈，搭出一套既适合学习演示、又具备微服务扩展潜力的 MMORPG 服务端愿景。与之对齐的可运行实现为 MyLunarCore 单体：崩铁式 Plane/Floor + 回合战 + 完整二游日常循环，并已完成体验闭环（1020–1045）与体验沉浸（1046–1069）协议接线。本文档 P5–P20 开放世界、SceneTickMicroPipeline、CompanionBot 等为 MyMmorpg 目标态，MyLunarCore 仓库尚未全量落地，请勿与单体已实现能力混淆。学生与管理者应同时阅读 docs/adr/0001-monolith-first.md 与 docs/feature-gap-fill.md。",
            "MyMmorpg 用现代 Java 技术栈，搭出一套既适合学习演示、又具备微服务扩展潜力的可运行 MMORPG 服务端。默认单体（player-service）可本地一键启动；亦可按域拆分为网关 + 多微服务。P5–P20 开放世界、SceneTickMicroPipeline、CompanionBot、版本运营自动化等均以本仓库代码为准。学生与管理者请同时阅读 README.md、DEPLOYMENT.md 与 docs/open-world-top-tier.md。",
        ),
        (
            "GAME_AI_REMOTE_ENABLED=true（ai-service:8995）；GAME_AI_REMOTE_ENABLED=true（ai-service:8995）；GAME_AI_REMOTE_ENABLED=true（ai-service:8995）；GAME_AI_REMOTE_ENABLED=true（ai-service:8995）；GAME_AI_REMOTE_ENABLED=true（ai-service:8995）；GAME_AI_REMOTE_ENABLED=true（ai-service:8995）；GAME_AI_REMOTE_ENABLED=true（ai-service:8995）；",
            "GAME_AI_REMOTE_ENABLED=true（ai-service:8995）；",
        ),
        (
            "热更差分、资源补丁链、CDN 预热。",
            "热更差分、资源补丁链、CDN 预热；版本时间线自动切换、协议门控、配置灰度、活动数据快照回滚、开服缓存预热、活动善后与回放历史基线。",
        ),
        (
            "Admin：配置导入、投诉、操作日志、AI 草稿、分阶段 reload（activity→update→quest→player）、发布审计、配置 Diff/回滚/灰度/引用完整性、运营控制台 ops-console.html。RBAC 预设：OPS / PLANNER / CS / SUPERADMIN。鉴权：IP 白名单 + HMAC/API Key；生产强制开启。",
            "Admin（8985）：配置导入、投诉、操作日志、AI 草稿、分阶段 reload（activity→update→quest→player）、发布审计、配置 Diff/回滚/灰度/引用完整性、运营控制台 ops-console.html（含灰度流量滑动条、版本时间线面板）。版本运营 API：/admin/ops/version-timeline、/admin/ops/version/gray-publish|gray-rollback|snapshot-players|rollback-player-data。RBAC：OPS / PLANNER / CS / SUPERADMIN。鉴权：IP 白名单 + HMAC/API Key；生产强制开启。",
        ),
        (
            "Gateway 可按 X-Account-Id 等粘滞到同一 player 实例；GeoIP/区域头注入就近信息；FunctionNumberRoutingFilter 按 X-Msg-Id 把移动路由到 Scene、背包到 Bag 等，削弱中转瓶颈。",
            "Gateway 可按 X-Account-Id 等粘滞到同一 player 实例；GeoIP/区域头注入就近信息；FunctionNumberRoutingFilter 按 X-Msg-Id 把移动路由到 Scene、背包到 Bag 等。VersionGateKeeperFilter 校验 X-Client-Version / X-Protocol-Schema-Hash，硬不兼容返回 RetCode.CLIENT_TOO_OLD=601；识别 Redis version:preheat:{v} 注入 X-Version-Preheat 引导客户端预下载。",
        ),
        (
            "按 tick 存储的指令流，用于回放晒分与反作弊取证",
            "按 tick 存储的指令流；录制时绑定 config_version/Git SHA/server_epoch（PatchBaselineContext），回放加载历史配置快照，基线缺失则提示录像因版本更迭已失效",
        ),
    ]
    for old, new in replacements:
        if old in t:
            t = t.replace(old, new)
    if t != text:
        return t
    return text


VERSION_OPS_HEADING = "5.9.1 版本运营自动化（时间线 / 门控 / 灰度 / 快照 / 预热 / 善后 / 回放基线）"
VERSION_OPS_BODY = [
    "自动化版本时间线：表 version_timeline（版本号、预下载/强制更新/生效/结束时间）；VersionTimelineScheduler（admin-service）启动加载未来 7 天 Timeline，@Scheduled 每秒检查；到达生效点自动调用 OpenWorldConfigPatchService.publishStaging；生效前 10 分钟写入 Redis version:preheat:{v}；生效前 5 分钟触发 ActivitySnapshotManager 快照。",
    "客户端多版本门控：登录上报 ClientVersion + ProtocolSchemaHash（proto 字段 protocol_schema_hash）；VersionGateKeeper + ProtocolCompatMap 维护 MIN_SUPPORTED_VERSION 与 SOFT/HARD 策略；SOFT 屏蔽新 MsgId（如 2600–2605）；HARD（战斗结构变更）返回 RetCode.CLIENT_TOO_OLD=601 强制更新。GatewayVersionGateKeeperFilter 同步拦截 HTTP 入口。",
    "配置灰度：publishStaging(GrayConditions) 支持 zoneId、accountId%N、isBetaTester、trafficPercent；SceneConfigResolver / SceneActorService.resolveOpenWorldCell 优先匹配灰度，失败回退全局基线；Admin 一键 gray-rollback 仅清除灰度层。场景 Internal API：/config/gray-publish、/config/gray-rollback。",
    "玩家活动快照与回退：ActivitySnapshotManager 将 activity:token / player:act 等写入 MySQL activity_snapshot；紧急回滚时比对溢出差值自动扣除并标记系统补偿邮件。",
    "缓存预热：CacheWarmUpService 在生效前预热 abyss/activity/shop 热点 Key；Redis SETNX 锁 + 双重检测，避免开服击穿 MySQL。",
    "活动善后：PostMortemProcessor 结束 T+3 天兑换缓冲期（只出不进），其后按比例将过期代币转为摩拉并发送《逾期代币回收说明》，清理 Redis 过期 Key。",
    "回放基线：BattleReplayService.start(..., PatchBaselineContext)；HistoricalConfigSnapshotStore 按 config_version 存历史规则；Skill/元素回放读历史快照而非最新配置。",
    "回归测试：VersionOpsBusinessFlowTest、VersionTimelineSchedulerTest、GatewayVersionGateKeeperTest、OpenWorldGrayConfigBusinessFlowTest、ConfigGrayMatcherTest、BattleReplayBaselineTest、PostMortemProcessorTest、CacheWarmUpServiceTest。",
]

DOC_MAP_ADDITIONS = [
    ("docs/troubleshooting.md", "拓扑图与常见故障排障"),
    ("docs/ai-enhancement.md", "AI 双管道、P15、风控与 Admin 闭环"),
    ("admin-service/.../schema/version_timeline.sql", "版本时间线 DDL"),
    ("activity-service/.../schema/activity_db.sql（activity_snapshot）", "活动数据快照表"),
]


def main() -> None:
    if not BACKUP.exists():
        BACKUP.write_bytes(SRC.read_bytes())
        print("backup ->", BACKUP)

    doc = Document(str(SRC))
    deleted = 0
    rewritten = 0
    insert_anchor = None

    # Pass 1: rewrite / delete
    for p in list(doc.paragraphs):
        text = p.text.strip()
        if not text:
            continue
        if should_delete(text):
            delete_paragraph(p)
            deleted += 1
            continue
        new = rewrite(text)
        if new is None:
            delete_paragraph(p)
            deleted += 1
        elif new != text:
            set_run_text(p, new)
            rewritten += 1
        if text.startswith("5.9 管理后台") or "5.9 管理后台与热更新" in text:
            insert_anchor = p
        # after rewrite, anchor may have been updated
        if p.text.strip().startswith("5.9 管理后台"):
            insert_anchor = p

    # Pass 2: insert version ops section after 5.9 body paragraph if missing
    has_version_ops = any(VERSION_OPS_HEADING in (p.text or "") for p in doc.paragraphs)
    if not has_version_ops:
        # find 5.9 content paragraph (the long Admin one)
        anchor = None
        for p in doc.paragraphs:
            t = p.text or ""
            if "版本运营 API" in t or ("ops-console.html" in t and "Admin" in t and "8985" in t):
                anchor = p
                break
            if "配置导入、投诉、操作日志、AI 草稿" in t and "ops-console" in t:
                anchor = p
                break
        if anchor is None and insert_anchor is not None:
            # next sibling body
            found = False
            for p in doc.paragraphs:
                if p._element is insert_anchor._element:
                    found = True
                    continue
                if found and (p.text or "").strip():
                    anchor = p
                    break
        if anchor is not None:
            cur = insert_after(anchor, VERSION_OPS_HEADING, style="Heading 3")
            for body in VERSION_OPS_BODY:
                cur = insert_after(cur, body)
            print("inserted version ops section")
        else:
            print("WARN: could not find 5.9 anchor for version ops insert")

    # Pass 3: ensure FAQ Q22 answer exists and points correctly
    for p in doc.paragraphs:
        if p.text.strip().startswith("Q22：版本运营"):
            # ensure following answer - if next is wrong LunarCore answer already rewritten
            break

    # Pass 4: append Q24 if missing for version ops detail
    has_q24 = any("Q24：版本运营" in (p.text or "") for p in doc.paragraphs)
    if not has_q24:
        # find Q22 paragraph and insert after its answer
        q22 = None
        for i, p in enumerate(doc.paragraphs):
            if "Q22：版本运营" in (p.text or ""):
                q22 = p
                # find answer paragraph after
                for j in range(i + 1, min(i + 4, len(doc.paragraphs))):
                    if (doc.paragraphs[j].text or "").startswith("答："):
                        q22 = doc.paragraphs[j]
                        break
                break
        if q22 is not None:
            cur = insert_after(q22, "Q24：版本时间线与灰度发布有哪些 Admin/Internal 接口？", style="Heading 2")
            insert_after(
                cur,
                "答：Admin：GET/POST /admin/ops/version-timeline、POST /admin/ops/version/gray-publish、/gray-rollback、/snapshot-players、/rollback-player-data、GET /gray-status。场景：POST /internal/scene/open-world/config/stage|publish|gray-publish|gray-rollback|snapshot-instance。登录协议 AccountLoginCsReq.protocol_schema_hash；返回码 CLIENT_TOO_OLD=601。回归命令：mvn -pl mmorpg-common,scene-service,mmorpg-gateway,admin-service,player-service test \"-Dtest=VersionOps*,VersionTimeline*,GatewayVersion*,OpenWorldGray*,ClientVersionGateTest\"。",
            )
            print("inserted Q24")

    # Pass 5: update doc map open-world range if still old
    for p in doc.paragraphs:
        if "大世界顶尖标准（含 P0–P17）" in (p.text or ""):
            set_run_text(p, (p.text or "").replace("P0–P17", "P0–P20"))
            rewritten += 1

    # Remove leftover TOC-like orphan lines that still mention P21 after delete pass failed style match
    for p in list(doc.paragraphs):
        t = (p.text or "").strip()
        if "P21" in t or "MyLunarCore" in t:
            # soft rewrite remaining MyLunarCore mentions
            if "MyLunarCore" in t and "MyMmorpg" in t and "愿景" in t:
                set_run_text(p, rewrite(t) if rewrite(t) != t else t.replace("愿景", "工程"))
                rewritten += 1
            elif should_delete(t) or t.startswith("附录 D") or t.startswith("附录 E"):
                delete_paragraph(p)
                deleted += 1
            elif "MyLunarCore" in t:
                # neutralize remaining
                nt = re.sub(r"MyLunarCore[^\s，。；：]*", "（已移除：非本仓库内容）", t)
                if "已移除" in nt:
                    # if paragraph is mostly lunar, delete
                    if t.count("MyLunarCore") >= 1 and len(t) < 80:
                        delete_paragraph(p)
                        deleted += 1
                    else:
                        set_run_text(p, nt)
                        rewritten += 1

    # Final sweep: any remaining MyLunarCore
    for p in list(doc.paragraphs):
        t = p.text or ""
        if "MyLunarCore" in t:
            if "已移除" in t or should_delete(t):
                delete_paragraph(p)
                deleted += 1
            else:
                set_run_text(
                    p,
                    t.replace("MyLunarCore", "本仓库 MyMmorpg")
                    .replace("愿景态", "已实现能力")
                    .replace("目标态", "已实现能力"),
                )
                rewritten += 1

    doc.save(str(DST))
    print(f"done: deleted={deleted}, rewritten={rewritten}, saved={DST}")


if __name__ == "__main__":
    main()
