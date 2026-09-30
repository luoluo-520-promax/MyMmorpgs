# -*- coding: utf-8 -*-
"""Generate MyMmorpg full project summary Word document on Desktop."""
from docx import Document
from docx.shared import Pt, Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml.ns import qn
from docx.oxml import OxmlElement
import os
from datetime import datetime


def set_run_font(run, name_cn="宋体", name_en="Times New Roman", size=12, bold=False, color=None):
    run.bold = bold
    run.font.size = Pt(size)
    if color:
        run.font.color.rgb = color
    run.font.name = name_en
    rPr = run._element.get_or_add_rPr()
    rFonts = rPr.get_or_add_rFonts()
    rFonts.set(qn("w:ascii"), name_en)
    rFonts.set(qn("w:hAnsi"), name_en)
    rFonts.set(qn("w:eastAsia"), name_cn)


def set_paragraph_format(p, space_after=8, first_line=True, align=None):
    pf = p.paragraph_format
    pf.space_after = Pt(space_after)
    pf.space_before = Pt(0)
    pf.line_spacing = 1.35
    pf.first_line_indent = Cm(0.74) if first_line else Cm(0)
    if align is not None:
        p.alignment = align


def add_heading_cn(doc, text, level=1):
    h = doc.add_heading(text, level=level)
    for run in h.runs:
        if level == 1:
            set_run_font(run, "黑体", "Arial", 16, True, RGBColor(0x1F, 0x4E, 0x79))
        elif level == 2:
            set_run_font(run, "黑体", "Arial", 14, True, RGBColor(0x2E, 0x75, 0xB6))
        else:
            set_run_font(run, "黑体", "Arial", 12, True, RGBColor(0x5B, 0x9B, 0xD5))
    h.paragraph_format.space_before = Pt(14 if level == 1 else 10)
    h.paragraph_format.space_after = Pt(8)
    h.paragraph_format.first_line_indent = Cm(0)
    return h


def add_para(doc, text, first_line=True, size=12, bold=False, align=None, space_after=8):
    p = doc.add_paragraph()
    run = p.add_run(text)
    set_run_font(run, size=size, bold=bold)
    set_paragraph_format(p, space_after=space_after, first_line=first_line, align=align)
    return p


def add_bullet(doc, text, level=0):
    p = doc.add_paragraph(style="List Bullet")
    p.clear()
    run = p.add_run(text)
    set_run_font(run, size=11)
    p.paragraph_format.left_indent = Cm(0.75 + level * 0.5)
    p.paragraph_format.first_line_indent = Cm(0)
    p.paragraph_format.space_after = Pt(4)
    p.paragraph_format.line_spacing = 1.25
    return p


def add_table(doc, headers, rows):
    table = doc.add_table(rows=1 + len(rows), cols=len(headers))
    table.style = "Table Grid"
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    for i, h in enumerate(headers):
        cell = table.rows[0].cells[i]
        cell.text = ""
        p = cell.paragraphs[0]
        run = p.add_run(h)
        set_run_font(run, "黑体", "Arial", 10, True, RGBColor(0xFF, 0xFF, 0xFF))
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        shading = OxmlElement("w:shd")
        shading.set(qn("w:fill"), "1F4E79")
        shading.set(qn("w:val"), "clear")
        cell._tc.get_or_add_tcPr().append(shading)
    for r_idx, row in enumerate(rows):
        for c_idx, val in enumerate(row):
            cell = table.rows[r_idx + 1].cells[c_idx]
            cell.text = ""
            p = cell.paragraphs[0]
            run = p.add_run(str(val))
            set_run_font(run, size=9)
            p.paragraph_format.space_after = Pt(2)
            p.paragraph_format.space_before = Pt(2)
            if r_idx % 2 == 1:
                shading = OxmlElement("w:shd")
                shading.set(qn("w:fill"), "D6E3F0")
                shading.set(qn("w:val"), "clear")
                cell._tc.get_or_add_tcPr().append(shading)
    doc.add_paragraph()
    return table


def add_page_number(section):
    footer = section.footer
    footer.is_linked_to_previous = False
    p = footer.paragraphs[0]
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER

    def add_field(paragraph, instr):
        run = paragraph.add_run()
        begin = OxmlElement("w:fldChar")
        begin.set(qn("w:fldCharType"), "begin")
        run._r.append(begin)
        run2 = paragraph.add_run()
        instr_el = OxmlElement("w:instrText")
        instr_el.set(qn("xml:space"), "preserve")
        instr_el.text = instr
        run2._r.append(instr_el)
        run3 = paragraph.add_run()
        separate = OxmlElement("w:fldChar")
        separate.set(qn("w:fldCharType"), "separate")
        run3._r.append(separate)
        run4 = paragraph.add_run()
        end = OxmlElement("w:fldChar")
        end.set(qn("w:fldCharType"), "end")
        run4._r.append(end)
        set_run_font(run, size=9)
        set_run_font(run2, size=9)
        set_run_font(run4, size=9)

    r = p.add_run("第 ")
    set_run_font(r, size=9)
    add_field(p, " PAGE ")
    r2 = p.add_run(" 页 / 共 ")
    set_run_font(r2, size=9)
    add_field(p, " NUMPAGES ")
    r3 = p.add_run(" 页")
    set_run_font(r3, size=9)


def insert_toc(doc):
    p = doc.add_paragraph()
    p.paragraph_format.first_line_indent = Cm(0)
    p.paragraph_format.space_after = Pt(6)

    run = p.add_run()
    begin = OxmlElement("w:fldChar")
    begin.set(qn("w:fldCharType"), "begin")
    run._r.append(begin)

    run2 = p.add_run()
    instr = OxmlElement("w:instrText")
    instr.set(qn("xml:space"), "preserve")
    instr.text = ' TOC \\o "1-3" \\h \\z \\u '
    run2._r.append(instr)

    run3 = p.add_run()
    separate = OxmlElement("w:fldChar")
    separate.set(qn("w:fldCharType"), "separate")
    run3._r.append(separate)

    run4 = p.add_run(
        "【目录占位】请用 Microsoft Word 打开后：若提示更新域请选「是」；"
        "或右键本段 → 更新域 → 更新整个目录；也可 Ctrl+A 后按 F9。"
    )
    set_run_font(run4, size=10, color=RGBColor(0x66, 0x66, 0x66))

    run5 = p.add_run()
    end = OxmlElement("w:fldChar")
    end.set(qn("w:fldCharType"), "end")
    run5._r.append(end)


def set_update_fields_on_open(document):
    settings = document.settings.element
    update = OxmlElement("w:updateFields")
    update.set(qn("w:val"), "true")
    settings.append(update)


def build_manual_toc_entries():
    """Fallback printable TOC outline (page numbers filled by Word field TOC)."""
    return [
        "一、写给所有读者的导读",
        "    1.1 适合谁阅读",
        "    1.2 阅读建议",
        "二、项目是什么、解决什么问题",
        "    2.1 项目定位",
        "    2.2 核心设计目标",
        "    2.3 技术栈一览",
        "三、功能与玩法全景（用业务语言说）",
        "    3.1～3.10 账号/场景/战斗/背包/社交/匹配/活动/商城/抽卡/后台",
        "四、能力成熟度：已实现、演示、计划中",
        "五、总体架构与部署模式",
        "六、各业务服务职责详解",
        "七、数据、缓存与消息",
        "八、协议、网关与安全",
        "九、测试、CI 与质量",
        "十、本地启动、部署与运维要点",
        "十一、核心子系统深潜",
        "十二、仓库目录与文档地图",
        "十三、常见问题（FAQ）",
        "十四、总结与后续建议",
        "附录 A：常用命令速查",
        "附录 B：术语表",
        "附录 C：文档维护说明",
    ]


def main():
    desktop = os.path.join(os.path.expanduser("~"), "Desktop")
    out_path = os.path.join(desktop, "MyMmorpg项目全面总结文档.docx")

    doc = Document()
    section = doc.sections[0]
    section.page_width = Cm(21.0)
    section.page_height = Cm(29.7)
    section.left_margin = Cm(2.5)
    section.right_margin = Cm(2.5)
    section.top_margin = Cm(2.5)
    section.bottom_margin = Cm(2.5)

    add_page_number(section)
    set_update_fields_on_open(doc)

    # Cover
    for _ in range(3):
        doc.add_paragraph()

    title = doc.add_paragraph()
    title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = title.add_run("MyMmorpg")
    set_run_font(r, "黑体", "Arial", 36, True, RGBColor(0x1F, 0x4E, 0x79))

    subtitle = doc.add_paragraph()
    subtitle.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = subtitle.add_run("项目全面总结文档")
    set_run_font(r, "黑体", "Arial", 28, True, RGBColor(0x2E, 0x75, 0xB6))

    doc.add_paragraph()
    line = doc.add_paragraph()
    line.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = line.add_run("Java 17 · Spring Boot 3 · 微服务 MMORPG 服务端")
    set_run_font(r, "楷体", "Arial", 14, False, RGBColor(0x59, 0x59, 0x59))

    doc.add_paragraph()
    doc.add_paragraph()
    for t in [
        f"文档生成日期：{datetime.now().strftime('%Y年%m月%d日')}",
        "文档性质：项目全貌说明（面向产品、研发、测试、运维）",
        "仓库工程名：mmorpg-parent / MyMmorpg",
        "技术栈关键词：Spring Cloud Alibaba、Netty、MySQL、Redis、Protobuf、Feign、RocketMQ",
    ]:
        p = doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        run = p.add_run(t)
        set_run_font(run, size=12)
        p.paragraph_format.space_after = Pt(6)
        p.paragraph_format.first_line_indent = Cm(0)

    doc.add_page_break()

    # TOC
    toc_title = doc.add_paragraph()
    toc_title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r = toc_title.add_run("目  录")
    set_run_font(r, "黑体", "Arial", 18, True, RGBColor(0x1F, 0x4E, 0x79))
    toc_title.paragraph_format.space_after = Pt(12)

    add_para(
        doc,
        "本文档使用 Word 自动目录域。打开后请更新域，目录将自动带上各级标题与对应页码。"
        "页脚已设置「第 X 页 / 共 Y 页」。",
        first_line=True,
        size=10,
    )
    insert_toc(doc)
    add_para(doc, "章节结构预览（更新目录后以自动目录为准）：", first_line=False, size=11, bold=True)
    for item in build_manual_toc_entries():
        add_para(doc, item, first_line=False, size=10, space_after=2)

    doc.add_page_break()

    # Chapter 1
    add_heading_cn(doc, "一、写给所有读者的导读", 1)
    add_para(
        doc,
        "这份文档用通俗语言说明 MyMmorpg 是什么、能做什么、怎么跑起来、各模块如何协作。"
        "你可以把它当作「项目说明书」：产品同学看玩法与能力边界，开发同学看架构与模块职责，"
        "测试同学看验证方式，运维同学看部署与安全配置。",
    )
    add_para(
        doc,
        "一句话概括：MyMmorpg 是一套用 Java 17 与 Spring Boot 3 编写的 MMORPG"
        "（大型多人在线角色扮演游戏）服务端示例。它既支持「单体一把梭」方便本地开发，"
        "也支持把战斗、场景、活动、商城等拆成多个微服务独立部署。"
        "设计上参考了二游常见能力（元素反应、体力、战令、抽卡、联机房间、世界 BOSS 等），"
        "并保留了传统 MMORPG 的场景 AOI、好友邮件、匹配副本等设施。",
    )

    add_heading_cn(doc, "1.1 适合谁阅读", 2)
    add_bullet(doc, "新人入职：先读第一章与第二章，建立整体印象，再按岗位跳到对应章节。")
    add_bullet(doc, "产品 / 策划：重点看第三章（玩法能力）、第四章（能力成熟度：已实现 / 演示 / 计划中）。")
    add_bullet(doc, "后端研发：重点看第五～八章（架构、服务边界、协议与数据、核心子系统）。")
    add_bullet(doc, "测试 / QA：重点看第九章（测试与质量门禁）与第四章成熟度，避免把「演示级」当「生产完备」。")
    add_bullet(doc, "运维 / SRE：重点看第十章（部署、环境变量、安全、可观测）。")

    add_heading_cn(doc, "1.2 阅读建议", 2)
    add_para(
        doc,
        "文档较长，不必一次读完。建议按「先理解部署模式 → 再看服务清单 → 再深入感兴趣的子系统」的顺序。"
        "文中出现的端口号、环境变量名、API 路径均与仓库内 README、DEPLOYMENT.md、docs/ 保持一致，便于对照源码。",
    )

    # Chapter 2
    add_heading_cn(doc, "二、项目是什么、解决什么问题", 1)
    add_heading_cn(doc, "2.1 项目定位", 2)
    add_para(
        doc,
        "MyMmorpg 不是单纯的 CRUD 演示，而是一个「可运行的游戏服务端骨架」。"
        "它覆盖从客户端接入（Netty TCP / 可选 UDP·KCP）、账号登录与会话、场景移动与 AOI、"
        "战斗结算、背包道具、活动运营、商城支付、后台配置导入，到热更发布审计等一整条链路。",
    )
    add_para(
        doc,
        "项目同时承担教学与工程样板的角色：展示如何在同一仓库内用 Maven 多模块组织领域服务，"
        "如何用 Port / Feign / 消息队列做服务间协作，如何用 HMAC 保护内部接口，"
        "如何用 Redis 做热数据与匹配队列，如何用 Flyway 与权威 SQL 管理多库演进。",
    )

    add_heading_cn(doc, "2.2 核心设计目标", 2)
    add_bullet(doc, "双模式：默认单体（只启 player-service）降低上手成本；需要时切微服务。")
    add_bullet(doc, "运维面优先：Admin 导入配置 + 分阶段热更 + 发布审计，对齐「策划改表可热更」的运营习惯。")
    add_bullet(doc, "实时与权威分离意识：场景移动、战斗计算偏实时；经济发奖、支付履约强调幂等与账本。")
    add_bullet(doc, "二游玩法可落地：元素反应、体力 Resin、装备随机词条、战令/月卡、签到、抽卡保底等可运行骨架。")
    add_bullet(doc, "联机优先房间而非千人同屏：CoopRoom（最多 4 人）是推荐形态；大世界 AOI 作为底座与压测能力保留。")

    add_heading_cn(doc, "2.3 技术栈一览", 2)
    add_table(
        doc,
        ["类别", "技术选型", "说明"],
        [
            ["语言 / 运行时", "Java 17", "现代 LTS，与 Spring Boot 3 匹配"],
            ["应用框架", "Spring Boot 3.2.5", "各服务统一父工程"],
            ["微服务", "Spring Cloud 2023.0.5 / Alibaba 2023.0.3.3", "Feign、可选 Nacos 等"],
            ["协议", "Protobuf 3.25.3", "游戏二进制包，集中在 mmorpg-protocol"],
            ["网络", "Netty（TCP + 可选 UDP/KCP）", "玩家长连接与推送"],
            ["数据库", "MySQL（多库）", "player / battle / activity / update"],
            ["缓存 / 队列态", "Redis", "在线、匹配、排行、热数据、票据等"],
            ["消息", "RocketMQ（可关）", "支付 outbox、战斗结束等；开发默认 NoOp"],
            ["测试", "TestNG + Testcontainers", "单元 / 集成套件分离"],
            ["质量", "JaCoCo + SpotBugs", "Maven profile quality-gate"],
            ["容器", "Docker Compose", "MySQL、Redis、可选 Nacos / Sentinel"],
        ],
    )

    # Chapter 3
    add_heading_cn(doc, "三、功能与玩法全景（用业务语言说）", 1)
    add_para(
        doc,
        "本章用「玩家会感知到什么」来描述系统能力，尽量少谈类名。"
        "若某能力标注为演示级，表示链路可跑通，但数值、运营配置或跨服生命周期仍简化。",
    )

    add_heading_cn(doc, "3.1 账号、角色与会话", 2)
    add_bullet(doc, "玩家通过 HTTP / 网关登录拿到会话，再连游戏端口进入世界。")
    add_bullet(doc, "支持会话 RSA（生产必须固定密钥）、重复登录踢旧连接、断线重连快照与重连保护期。")
    add_bullet(doc, "跨服迁移时使用一次性 session_ticket，目标节点消费票据进入场景。")
    add_bullet(doc, "GlobalUID（雪花算法）保证跨服合服场景下的全局唯一 ID。")

    add_heading_cn(doc, "3.2 场景、移动与大世界", 2)
    add_bullet(doc, "场景内用 AOI（兴趣区域）只同步附近实体，降低广播量。")
    add_bullet(doc, "可按密度拆合动态域（Zone）；接近传送门可预加载，实现更平滑的切服。")
    add_bullet(doc, "世界状态包含时间天气、资源、解谜 Bitmap、房主世界等。")
    add_bullet(doc, "世界等级可动态缩放怪物数值；世界 Boss 有全局刷新锁，避免多线重复结算。")
    add_bullet(doc, "移动有 SnapshotBuffer 延迟补偿与超速校验；反作弊服务会统计违规并支持临时封禁。")
    add_bullet(doc, "采集支持 WORLD_SHARED（谁先采谁得）与 PER_PLAYER（每人独立 CD）等模式。")

    add_heading_cn(doc, "3.3 战斗与挑战", 2)
    add_bullet(doc, "战斗开始 / 行动 / 结算走战斗域服务；挑战关卡、肉鸽等有独立协议号段。")
    add_bullet(doc, "元素反应：附着 → 触发 → 反应 → 倍率；客户端可预测，并支持 rollback 标记。")
    add_bullet(doc, "AI 队友听指挥走位；Boss 用行为树（狂暴 / 转阶段 / 锁仇恨），实时战斗不依赖大模型。")
    add_bullet(doc, "战斗日 / 周统计写入 Redis；可供 Admin AI 周报等读取。")
    add_bullet(doc, "联机物权：宝箱/材料归属策略，队伍怪物归属锁，仇恨优先级表。")

    add_heading_cn(doc, "3.4 背包、装备与体力", 2)
    add_bullet(doc, "背包支持道具增减、邮件附件发奖（幂等）。")
    add_bullet(doc, "装备随机词条写入 affix_blob。")
    add_bullet(doc, "体力（Resin）自然恢复与每日购买次数。")
    add_bullet(doc, "冷热分离：热数据可进 Redis，定时刷回 MySQL。")

    add_heading_cn(doc, "3.5 社交与大厅", 2)
    add_bullet(doc, "好友、邮件、排行榜（等级 / 战力 ZSET）。")
    add_bullet(doc, "跨服好友可用 Redis SET 维护；好友在线态可读 Redis。")
    add_bullet(doc, "联机房间 CoopRoom（最多 4 人）共斗世界 BOSS 等。")
    add_bullet(doc, "事件临时小队、好友助战借用、家园拜访（演示级快照社交）。")
    add_bullet(doc, "聊天服务独立端口，处理频道消息。")

    add_heading_cn(doc, "3.6 匹配与副本", 2)
    add_bullet(doc, "双人副本 / PVP，以及跨服 4 人副本匹配（match type=3）。")
    add_bullet(doc, "匹配使用互补评分（等级 / 战力 / 等待时间），队列分片，跨实例用 SET NX + Lua。")
    add_bullet(doc, "跨服场景实例生命周期目前仍简化（演示级）。")

    add_heading_cn(doc, "3.7 任务、活动与签到", 2)
    add_bullet(doc, "任务配置 JSON 驱动（如 config/quest/Quests.json），支持 Admin / Internal 导入与 reload。")
    add_bullet(doc, "活动配置、进度、领奖；战斗结束事件可推进活动进度。")
    add_bullet(doc, "世界事件 / 世界 BOSS：排期、伤害板、结算 grantPlans。")
    add_bullet(doc, "七日签到、月卡日领等运营活动接口。")
    add_bullet(doc, "活动进度以 Redis JSON 为主，避免过时进度表设计。")

    add_heading_cn(doc, "3.8 商城、支付与经济硬化", 2)
    add_bullet(doc, "商城货架与订单；支付可 MOCK（开发）或渠道 HMAC / HTTP 验签（生产禁 MOCK）。")
    add_bullet(doc, "P0 经济硬化：订单双写、mq_outbox / mq_inbox、grant 幂等、wallet / item 账本、对账 SPI。")
    add_bullet(doc, "战令 / 月卡 / 首充双倍等商业化能力骨架。")
    add_bullet(doc, "对账入口可对比 ChannelBillProvider 账单差异并处理重试履约候选。")

    add_heading_cn(doc, "3.9 抽卡、皮肤与更新", 2)
    add_bullet(doc, "独立抽卡服（8994）：保底、十连、天井、历史；概率公示与审计摘要。")
    add_bullet(doc, "皮肤衣柜 / 穿戴（协议号段 12xx，配置见 config/skin/）。")
    add_bullet(doc, "客户端版本与资源更新：差分规划（PatchDiffPlanner）、CDN 预热。")

    add_heading_cn(doc, "3.10 后台 Admin 与 AI 辅助", 2)
    add_bullet(doc, "IP 白名单 + HMAC / API Key；操作日志与投诉处理。")
    add_bullet(doc, "配置导入（活动 / 任务 / 商城 / 更新清单等）与分阶段热更。")
    add_bullet(doc, "AI 草稿：活动、任务、商城包、投诉分类、战斗周报。")
    add_bullet(doc, "玩家侧 rule-coach 顾问与智能 NPC（LLM 可选，默认规则回退）。")
    add_bullet(doc, "发布审计：查看热更历史，阶段顺序 activity → update → player。")

    # Chapter 4
    add_heading_cn(doc, "四、能力成熟度：已实现、演示、计划中", 1)
    add_para(
        doc,
        "阅读任何开源/示例型游戏服务端时，最重要的是分清「能演示」与「能上生产」。"
        "本章直接对齐仓库 DEPLOYMENT.md 的分层，避免误解。",
    )

    add_heading_cn(doc, "4.1 已实现（工程可用）", 2)
    add_bullet(doc, "单体与微服务双模式；内部 API HMAC；生产密钥启动校验。")
    add_bullet(doc, "Admin 导入、热更编排、发布审计。")
    add_bullet(doc, "元素反应、体力、装备词条、CoopRoom、战令/月卡/签到骨架。")
    add_bullet(doc, "玩家/背包冷热分离、移动延迟补偿、TLog（可选 Kafka）、GlobalUID 雪花。")
    add_bullet(doc, "大世界底座：AOI、Zone、WorldState、WorldLevel、Boss 刷新锁、Portal 预加载、重连保护。")
    add_bullet(doc, "网关功能号段路由、区域就近头、反作弊底座、支付对账 SPI、Feign 降级。")
    add_bullet(doc, "好友/邮件/任务进度 MySQL；匹配与战斗状态 Redis；CI 与 Docker Compose 四库初始化。")
    add_bullet(doc, "抽卡微服务、皮肤与挑战号段、肉鸽局内天赋等可运行链路。")

    add_heading_cn(doc, "4.2 演示级（可用但不完整）", 2)
    add_bullet(doc, "跨服场景实例生命周期仍简化；部分奖励数值为演示常量。")
    add_bullet(doc, "好友助战 / 家园拜访为 Redis 快照级社交，非完整养成装修产品。")
    add_bullet(doc, "智能 NPC 以规则为主；AOI 千人压测为单进程基准。")
    add_bullet(doc, "RocketMQ、TLog Kafka 默认关闭；会话 RSA 未配置时开发态临时生成。")
    add_bullet(doc, "元素反应 / 战令 / 签到 / 体力为可运行骨架，数值与运营配置表可继续产品化。")

    add_heading_cn(doc, "4.3 计划中（未实施或未完成）", 2)
    add_bullet(doc, "继续把 battle/skill/activity 抽成 CommandPort；再建 mmorpg-infra。")
    add_bullet(doc, "player-service 去除剩余域 Maven 硬依赖，真正 gateway-only。")
    add_bullet(doc, "完整 RedLock/Redisson、官方支付 SDK 直连、KCP 与 Lunar 全量对齐。")
    add_bullet(doc, "真实 GeoIP/GSLB、跨区域多活、反作弊规则引擎生产化、集群级千人同屏压测。")
    add_bullet(doc, "战令任务配置表、元素反应策划表可视化编辑器、TLog 真 Kafka/ES 落盘等。")

    # Chapter 5
    add_heading_cn(doc, "五、总体架构与部署模式", 1)
    add_heading_cn(doc, "5.1 双模式说明", 2)
    add_para(
        doc,
        "单体模式（默认）：只启动 player-service（HTTP 8989）。其它域模块通过 Maven 依赖「嵌」进同一进程，"
        "远程开关 game.*.remote.enabled=false。适合本地开发、集成测试、小规模演示。",
    )
    add_para(
        doc,
        "微服务模式：各域独立 JAR 与端口；player-service 作为客户端入口与命令转发（Feign）；"
        "域服务通过 RestTemplate 等远程 Port 回调。适合验证拆分、独立扩容场景节点或战斗节点。",
    )

    add_heading_cn(doc, "5.2 服务端口一览", 2)
    add_table(
        doc,
        ["服务", "HTTP 端口", "主要职责"],
        [
            ["player-service", "8989", "客户端连接、会话、聚合入口"],
            ["scene-service", "8981", "场景 / AOI / 大世界"],
            ["chat-service", "8982", "聊天"],
            ["bag-service", "8983", "背包 / 体力 / 发奖"],
            ["skill-service", "8984", "技能"],
            ["admin-service", "8985", "后台管理与 AI 草稿"],
            ["hall-service", "8986", "好友 / 邮件 / 排行 / 联机房间"],
            ["quest-service", "8987", "任务"],
            ["matchmaking-service", "8988", "匹配与跨服副本入口"],
            ["shop-service", "8990", "商城与支付订单"],
            ["battle-service", "8991", "战斗 / AI 队友 / Boss BT"],
            ["activity-service", "8992", "活动 / 世界事件 / 签到"],
            ["update-service", "8993", "版本与资源更新"],
            ["gacha-service", "8994", "抽卡"],
            ["mmorpg-gateway", "8443（可配）", "可选 API 网关"],
        ],
    )

    add_heading_cn(doc, "5.3 请求大致怎么走", 2)
    add_para(
        doc,
        "玩家客户端：优先连 player-service 的游戏端口（Netty），消息按协议号分发到本地实现或远程 Feign。"
        "HTTP API 可经 mmorpg-gateway（账号粘滞、区域头、功能号段路由、TraceId）再转发到各服务。",
    )
    add_para(
        doc,
        "内部调用：路径前缀 /internal/**，生产必须启用 HMAC（时间戳 + 签名）。"
        "命令型接口常传 Protobuf 二进制；查询 / Port 型多为 JSON。",
    )
    add_para(
        doc,
        "异步：支付成功、战斗结束等可走 Outbox → MQ → Inbox 幂等消费，保证经济与活动进度最终一致。",
    )

    add_heading_cn(doc, "5.4 共享基础模块", 2)
    add_table(
        doc,
        ["模块", "作用"],
        [
            ["mmorpg-protocol", "全部 *.proto、MessageId、GamePackets、RetCode 等协议面"],
            ["mmorpg-domain-api", "跨服务 Port 接口（如背包命令 / 发货）"],
            ["mmorpg-common", "公共适配、远程 Port、过渡聚合层"],
            ["mmorpg-gateway", "Spring Cloud Gateway：路由、粘滞、区域、号段、Trace"],
            ["mmorpg-cli", "命令行辅助工具"],
        ],
    )

    add_heading_cn(doc, "5.5 架构示意（文字版）", 2)
    add_para(doc, "客户端 →（可选）Gateway → player-service（长连接/会话）", first_line=False)
    add_para(doc, "        ↘ Feign/Port → scene / battle / bag / hall / quest / match / shop / activity / gacha / update / admin", first_line=False)
    add_para(doc, "基础设施：MySQL 多库 · Redis ·（可选）RocketMQ / Nacos / Kafka(TLog) · Prometheus", first_line=False)
    add_para(
        doc,
        "理解这张图时记住两点：① 玩家实时交互尽量靠近 Scene/Battle；② 涉及钱和道具的变更必须可追踪、可幂等。",
    )

    # Chapter 6
    add_heading_cn(doc, "六、各业务服务职责详解", 1)
    add_para(doc, "下面按服务说明「管什么、不管什么」，便于排障与扩容决策。")

    add_heading_cn(doc, "6.1 player-service（玩家入口）", 2)
    add_para(
        doc,
        "职责：账号与角色、统一鉴权与在线、长连接入口、把客户端命令路由到各域、回写响应。"
        "单体模式下它还承载大量嵌入域逻辑。微服务模式下应逐步变成「网关进程」，只依赖 common + Feign。",
    )
    add_para(
        doc,
        "数据：以 player_db 为主（账号、玩家、好友、邮件、任务进度、经济账本、订单相关表等，视嵌入情况而定）。"
        "可通过 Maven profile 去掉 embed-admin / embed-bag，验证无对应域 JAR 时的编译。",
    )

    add_heading_cn(doc, "6.2 scene-service（场景）", 2)
    add_para(
        doc,
        "职责：地图分线、AOI、怪物与机关、世界状态、世界等级、Boss 刷新、Portal、重连保护、移动校验入口之一。"
        "大世界「主场」在 Scene：匹配只是把人送进来的入口。业务大盘指标也可从场景侧暴露。",
    )

    add_heading_cn(doc, "6.3 battle-service（战斗）", 2)
    add_para(
        doc,
        "职责：战斗生命周期、挑战与肉鸽相关结算回写、AI 队友指令、Boss 行为树与仇恨表、战斗统计。"
        "实时战斗禁止用 LLM 做决策。战斗状态可落 Redis；活跃战斗索引用 SET，避免危险的 KEYS 扫描。",
    )

    add_heading_cn(doc, "6.4 bag-service / skill-service", 2)
    add_para(
        doc,
        "背包负责道具与体力、发奖幂等、装备词条存储；技能负责技能配置与相关逻辑。"
        "二者都可嵌入 player，也可独立部署。活动发奖往往最终落到 Bag 的发货 Port。",
    )

    add_heading_cn(doc, "6.5 hall-service / chat-service / quest-service / matchmaking-service", 2)
    add_para(
        doc,
        "大厅管好友邮件排行与轻社交（小队、助战、家园、CoopRoom）；聊天管频道消息；"
        "任务管配置驱动的任务进度；匹配管队列、评分与跨服副本组队。",
    )

    add_heading_cn(doc, "6.6 shop-service / activity-service / gacha-service / update-service", 2)
    add_para(
        doc,
        "商城管货架与支付履约；活动管运营活动与世界事件（可消费战斗结束 MQ）；"
        "抽卡管概率与保底审计；更新管客户端补丁差分与 CDN 预热。",
    )

    add_heading_cn(doc, "6.7 admin-service", 2)
    add_para(
        doc,
        "运维与策划后台：导入、热更协调、投诉、操作日志、各类 AI 草稿生成。"
        "生产必须开鉴权与强密钥，并建议开 IP 白名单。不要把它暴露在无防护的公网入口。",
    )

    # Chapter 7
    add_heading_cn(doc, "七、数据、缓存与消息", 1)
    add_heading_cn(doc, "7.1 多库拆分", 2)
    add_table(
        doc,
        ["数据库", "权威 DDL 位置", "典型内容"],
        [
            ["player_db", "player-service/.../schema/player_db.sql", "账号玩家、好友邮件任务、经济硬化表"],
            ["battle_db", "battle-service/.../schema/battle_db.sql", "战斗域持久化"],
            ["activity_db", "activity-service/.../schema/activity_db.sql", "活动配置等；进度以 Redis JSON 为主"],
            ["update_db", "update-service/.../schema/update_db.sql", "更新清单"],
        ],
    )
    add_para(
        doc,
        "Docker Compose 首次启动会创建四库并挂载初始化脚本。已有数据卷不会重跑 init，"
        "需要干净库时用 docker compose down -v。部分服务启用 Flyway（baseline-on-migrate），"
        "新增表以 migration 脚本为准，同时维护权威 schema SQL。",
    )
    add_para(
        doc,
        "经济硬化相关表（player_db）：wallet_ledger、item_ledger、grant_idempotency、"
        "shop_order、mq_outbox、mq_inbox。活动库也有 mq_inbox 用于消费幂等。",
    )

    add_heading_cn(doc, "7.2 Redis 用途地图", 2)
    add_bullet(doc, "在线态：player:online:{id}")
    add_bullet(doc, "匹配：match:queue:* / match:player:* / match:status:*")
    add_bullet(doc, "排行榜：rank:level / rank:power（ZSET）；miss 时可回源 player Top50 回填")
    add_bullet(doc, "战斗统计：battle:stats:daily|weekly:*")
    add_bullet(doc, "跨服好友：cross:friend:{id}")
    add_bullet(doc, "迁移票据：center:migration:ticket:*（无 Redis 可回退进程内）")
    add_bullet(doc, "热数据：玩家与背包热缓存 + 定时落库")
    add_bullet(doc, "生产可选 Redis Sentinel 高可用（docker-compose.redis-sentinel.yml）")

    add_heading_cn(doc, "7.3 消息与幂等", 2)
    add_para(
        doc,
        "经济与活动链路强调：Outbox 可靠投递、Inbox 消费幂等、grant_idempotency 防重复发奖。"
        "开发环境 RocketMQ 默认可关闭（NoOp）；生产若依赖事件链路需显式开启。"
        "战斗结束事件可被 activity 消费以推进进度，最终一致而非强事务。",
    )

    # Chapter 8
    add_heading_cn(doc, "八、协议、网关与安全", 1)
    add_heading_cn(doc, "8.1 游戏协议", 2)
    add_para(
        doc,
        "客户端与服务端通过 Protobuf 定义的二进制包通信，协议集中在 mmorpg-protocol。"
        "不同玩法有号段约定，例如皮肤 12xx、挑战 13xx、抽卡 14xx、肉鸽 15xx。"
        "网关可按 X-Msg-Id 映射到目标服务（FunctionNumberRoutingFilter），减轻 player 中转压力。",
    )

    add_heading_cn(doc, "8.2 内部 API 安全", 2)
    add_para(
        doc,
        "所有 /internal/** 在生产必须启用 game.internal-api，使用 HMAC-SHA256"
        "（请求头含时间戳与签名等）。生产启动校验会拒绝空密钥或开发弱默认，"
        "避免「带着测试密钥上线」。dev 默认可不校验以便本地调试。",
    )

    add_heading_cn(doc, "8.3 Admin 安全", 2)
    add_para(
        doc,
        "Admin 使用 API Key / HMAC、可选 IP 白名单、权限点（如 ops:reload、import:*）。"
        "操作应可审计。切勿在公网暴露未鉴权的 Admin。",
    )

    add_heading_cn(doc, "8.4 支付安全", 2)
    add_para(
        doc,
        "生产必须关闭 MOCK 支付，配置渠道密钥；建议开启生产验签。"
        "对账入口可对比渠道账单差异并处理重试履约候选。",
    )

    add_heading_cn(doc, "8.5 反作弊与容错", 2)
    add_bullet(doc, "AntiCheatService：移动超速/瞬移、伤害溢出、违规计数与临时封禁。")
    add_bullet(doc, "Feign FallbackFactory：scene / hall / battle 等关键客户端降级。")
    add_bullet(doc, "OpenFeign 超时与 circuit-breaker 开关已在 player 侧配置。")
    add_bullet(doc, "全链路 X-Trace-Id 透传，便于日志串联。")
    add_bullet(doc, "Gateway 默认按账号粘滞到同一 player-service 实例。")

    # Chapter 9
    add_heading_cn(doc, "九、测试、CI 与质量", 1)
    add_heading_cn(doc, "9.1 测试约定", 2)
    add_para(
        doc,
        "每个模块在 src/test/resources/ 下维护两套 TestNG 套件："
        "testng-unit.xml（默认、快速）与 testng-integration.xml（可能依赖 Docker）。"
        "仓库根目录 test-suites/ 有聚合套件。",
    )
    add_table(
        doc,
        ["场景", "命令"],
        [
            ["全仓库单元测试", "mvn test 或 mvn -B test"],
            ["全仓库集成测试", "mvn test -Pintegration-tests（需 Docker）"],
            ["质量门禁", "mvn -B verify -Pquality-gate（JaCoCo + SpotBugs）"],
            ["单模块集成", "mvn test -pl activity-service -Pintegration-tests"],
        ],
    )

    add_heading_cn(doc, "9.2 CI", 2)
    add_para(
        doc,
        "GitHub Actions（.github/workflows/ci.yml）会跑单元测试，并在集成测试中附带 MySQL + Redis service。"
        "合并前建议至少保证单元绿灯；涉及跨服务与容器依赖的改动再跑集成套件。",
    )

    add_heading_cn(doc, "9.3 测试时要注意什么", 2)
    add_bullet(doc, "不要把演示级能力的断言写成「生产完备」承诺。")
    add_bullet(doc, "支付、发奖、热更相关用例优先覆盖幂等与失败回退。")
    add_bullet(doc, "集成测试需要 Docker 时，先确认本机/CI 能拉起 redis/mysql 镜像。")

    # Chapter 10
    add_heading_cn(doc, "十、本地启动、部署与运维要点", 1)
    add_heading_cn(doc, "10.1 最快上手（单体）", 2)
    add_bullet(doc, "1）docker compose up -d mysql redis")
    add_bullet(doc, "2）复制 .env.example 为 .env（开发默认即可）")
    add_bullet(doc, "3）mvn -pl player-service -am spring-boot:run -Dspring-boot.run.profiles=dev")
    add_para(doc, "客户端默认连 player-service：HTTP 8989，Netty 游戏端口见 config/。")

    add_heading_cn(doc, "10.2 切微服务的关键步骤", 2)
    add_bullet(doc, "启动依赖并确保四库 schema 就绪。")
    add_bullet(doc, "设置全服务相同的 INTERNAL_API_SECRET（强密钥）。")
    add_bullet(doc, "独立 activity 时打开 GAME_PORT_REMOTE_ENABLED 并配置 BAG_SERVICE_URL，否则发奖可能 NoOp，生产会 fail-fast。")
    add_bullet(doc, "支付生产关 MOCK，配置渠道密钥。")
    add_bullet(doc, "先起各域服务，再起 player 并打开对应 GAME_*_REMOTE_ENABLED。")

    add_heading_cn(doc, "10.3 环境变量心智模型", 2)
    add_para(
        doc,
        "dev：鉴权可关、支付可 MOCK、密钥可弱（仅本机）。"
        "test/IT：用测试密钥或关鉴权，避免污染生产秘密。"
        "prod：内部 API、Admin 鉴权、会话 RSA、支付渠道、数据库密码全部强校验，弱默认直接启动失败。"
        "完整矩阵见 DEPLOYMENT.md 与 .env.example。",
    )

    add_heading_cn(doc, "10.4 热更与发布审计", 2)
    add_para(
        doc,
        "导入活动或更新清单后，调用 Admin 的 POST /admin/ops/reload（需相应权限）。"
        "阶段顺序：activity-service → update-service → player-service（清空策划表 Redis 缓存）。"
        "可用 GET /admin/ops/publish-history 查看发布审计。各域还有 POST /internal/ops/reload 探针。",
    )

    add_heading_cn(doc, "10.5 可观测", 2)
    add_para(
        doc,
        "各服务 Actuator 暴露 health / info / metrics / prometheus。"
        "仓库 deploy/observability/ 提供 Prometheus 与告警骨架"
        "（验签失败、履约停滞、实例宕机、堆内存、Outbox 积压等）。"
        "建议后续接统一日志（ELK/Loki）并以 traceId 检索。",
    )

    add_heading_cn(doc, "10.6 扩容建议（大世界）", 2)
    add_para(
        doc,
        "优先水平扩容 Scene，并做好 Portal 无感切换；Boss 房可独立高配实例；"
        "Redis 用 Sentinel / 多 AZ；跨区域只做读多写少的目录同步，实时权威仍粘滞到玩家当前 Zone 节点。"
        "资源倾斜应优先 Scene，而不是过度复杂化匹配算法。",
    )

    # Chapter 11
    add_heading_cn(doc, "十一、核心子系统深潜（给愿意继续读的人）", 1)
    add_heading_cn(doc, "11.1 Center 与场景迁移", 2)
    add_para(
        doc,
        "GAME_CENTER_MODE 可为 local 或 remote。remote 时通过 HTTP 查询中心计划（节点、是否本机）。"
        "跨节点传送返回重定向 retcode，并附带 host/port/session_ticket；目标节点消费票据。"
        "场景节点可注册/心跳/注销；单线人数上限与最大分线、空线回收、断线重连 TTL 均可配置。",
    )

    add_heading_cn(doc, "11.2 大世界顶尖标准（P0/P1）", 2)
    add_para(
        doc,
        "P0：WorldState 权威快照、采集 WORLD_SHARED/PER_PLAYER、WorldLevel 动态数值、"
        "Boss 全局 RespawnTimer、联机物权（LootOwnershipPolicy / PartyEntityOwnership）与仇恨优先级。"
        "P1：Portal 预加载、重连保护、热更差分与 CDN 预热。"
        "P2：网关号段路由、行为树配置化与 GM 调试、业务大盘指标等。"
        "细节见 docs/open-world-top-tier.md。",
    )

    add_heading_cn(doc, "11.3 经济硬化链路", 2)
    add_para(
        doc,
        "下单写入 shop_order；支付事件进 mq_outbox；消费者幂等入 mq_inbox；"
        "发奖走 grant_idempotency；资金与道具变动记 wallet_ledger / item_ledger；"
        "对账接口对比 ChannelBillProvider。"
        "这条链路是「演示可氪」升级到「敢真收款」的最低工程门槛。",
    )

    add_heading_cn(doc, "11.4 AI 边界（非常重要）", 2)
    add_para(
        doc,
        "项目对 AI 的使用刻意分层：运营侧可用 LLM 生成活动/任务/商城草稿与投诉分类；"
        "玩家顾问与 NPC 默认规则引擎，LLM 可选增强且失败回退；"
        "实时战斗 AI 只用规则与行为树，明确禁止 LLM 介入。"
        "这样既展示「AI 能帮运营提效」，又避免把不确定延迟带进帧级战斗。",
    )

    add_heading_cn(doc, "11.5 Gateway-only 演进路线", 2)
    add_para(
        doc,
        "长期目标是 player-service 编译期不再依赖各域实现 JAR，只走 Feign。"
        "已完成：protocol / domain-api 拆分、Admin/Bag 可通过 Maven profile 摘除嵌入。"
        "仍阻塞：battle/skill/activity 等硬类型引用。"
        "下一步是补齐 CommandPort，打开远程开关验证，再删依赖并加 CI 门禁。"
        "见 docs/architecture-gateway.md。",
    )

    # Chapter 12
    add_heading_cn(doc, "十二、仓库目录与文档地图", 1)
    add_heading_cn(doc, "12.1 顶层目录（常见）", 2)
    add_table(
        doc,
        ["路径", "含义"],
        [
            ["*-service/", "各业务微服务源码"],
            ["mmorpg-*/", "协议、领域 API、公共库、网关、CLI"],
            ["config/", "策划与运行配置（任务、皮肤等）"],
            ["docker/ 与 docker-compose*.yml", "本地依赖与 MySQL 初始化"],
            ["docs/", "架构、边界、大世界、DB、可观测专题"],
            ["deploy/", "部署与可观测样例"],
            [".github/workflows/", "CI"],
            ["test-suites/", "聚合 TestNG 套件"],
            ["README.md / DEPLOYMENT.md / TESTING.md", "入口、部署能力、测试说明"],
            [".env.example", "全量环境变量样例"],
        ],
    )

    add_heading_cn(doc, "12.2 建议对照阅读顺序", 2)
    add_bullet(doc, "README.md → 快速启动")
    add_bullet(doc, "DEPLOYMENT.md → 能力分层与环境变量")
    add_bullet(doc, "docs/service-boundaries.md → 拆分契约")
    add_bullet(doc, "docs/open-world-top-tier.md → 大世界能力清单")
    add_bullet(doc, "docs/db-migration.md → 四库与 Flyway")
    add_bullet(doc, "docs/resilience-observability.md → Trace / 降级 / 告警")
    add_bullet(doc, "docs/architecture-gateway.md → 网关化路线")

    # Chapter 13
    add_heading_cn(doc, "十三、常见问题（FAQ）", 1)
    add_heading_cn(doc, "13.1 我只想本地玩通登录进场景，要起哪些东西？", 2)
    add_para(doc, "MySQL + Redis + player-service（dev）通常足够。不必一开始就起全套微服务。")

    add_heading_cn(doc, "13.2 为什么活动发奖没效果？", 2)
    add_para(
        doc,
        "独立 activity-service 时若未配置远程 Bag 与端口，发奖可能是 NoOp。"
        "生产环境会因此 fail-fast，开发环境则可能「静默没货」。",
    )

    add_heading_cn(doc, "13.3 支付相关接口在测试环境乱调安全吗？", 2)
    add_para(doc, "开发可用 MOCK，但切勿把 MOCK 与弱密钥带到生产。真收款链路必须验签、幂等、账本与对账齐备。")

    add_heading_cn(doc, "13.4 千人同屏能直接当卖点吗？", 2)
    add_para(
        doc,
        "当前 AOI 压测偏单进程基准；产品推荐仍是 Coop 联机房间。"
        "千人同屏属于可选压力场景与架构底座，不是已完成的集群产品能力。",
    )

    add_heading_cn(doc, "13.5 Word 目录没有页码怎么办？", 2)
    add_para(
        doc,
        "用 Microsoft Word 打开本文件，允许更新域；或右键目录选择「更新整个目录」。"
        "页脚页码同样依赖域更新。WPS 一般也支持，若显示异常请用 Word 打开一次再另存。",
    )

    add_heading_cn(doc, "13.6 单体和微服务如何选择？", 2)
    add_para(
        doc,
        "本地开发、联调、课程演示：优先单体。"
        "需要验证拆分、独立扩容 Scene/Battle、或多人并行改不同域：再开微服务。"
        "不要为了「看起来微服务」而增加运维负担。",
    )

    # Chapter 14
    add_heading_cn(doc, "十四、总结与后续建议", 1)
    add_para(
        doc,
        "MyMmorpg 已经具备一条可运行的 MMORPG / 二游混合服务端主链路："
        "接入、场景、战斗、经济、运营后台、热更与基础可观测都「有着落」。"
        "它的价值在于：用真实工程结构展示如何把游戏领域拆服务、如何用双模式降低开发成本、"
        "如何把支付与发奖做成可审计的硬化链路、如何把 AI 限制在合适边界。",
    )
    add_para(
        doc,
        "若团队要以本仓库为起点做正式产品，建议优先顺序为："
        "（1）生产密钥与支付验签彻底关掉演示开关；"
        "（2）明确联机产品形态（房间 vs 大世界）并按主形态压测；"
        "（3）推进 gateway-only 与 CommandPort，降低单体耦合；"
        "（4）补齐日志平台与告警值班；"
        "（5）把演示级社交与跨服生命周期产品化。",
    )
    add_para(
        doc,
        "本文档描述的是生成当日仓库所呈现的能力与约定；源码与 DEPLOYMENT.md 若有更新，以仓库为准。",
    )

    # Appendix
    add_heading_cn(doc, "附录 A：常用命令速查", 1)
    add_bullet(doc, "docker compose up -d mysql redis")
    add_bullet(doc, "docker compose up -d   # 含可选 Nacos 等")
    add_bullet(doc, "docker compose -f docker-compose.redis-sentinel.yml up -d")
    add_bullet(doc, "mvn -pl player-service -am spring-boot:run -Dspring-boot.run.profiles=dev")
    add_bullet(doc, "mvn -B test")
    add_bullet(doc, "mvn -B test -Pintegration-tests")
    add_bullet(doc, "mvn -B verify -Pquality-gate")
    add_bullet(doc, 'mvn -pl player-service -am package -DskipTests "-P!embed-admin,!embed-bag"')

    add_heading_cn(doc, "附录 B：术语表", 1)
    add_table(
        doc,
        ["术语", "通俗解释"],
        [
            ["MMORPG", "大型多人在线角色扮演游戏"],
            ["AOI", "兴趣区域：只同步玩家附近的人与物"],
            ["Feign", "声明式 HTTP 客户端，用于服务间同步调用"],
            ["Port（端口适配）", "领域对外依赖的接口抽象，便于本地/远程切换"],
            ["Outbox / Inbox", "发件箱/收件箱模式，保证消息可靠与消费幂等"],
            ["HMAC", "基于哈希的消息认证，用于内部接口防伪造"],
            ["Protobuf", "高效二进制序列化协议"],
            ["行为树 BT", "游戏 AI 常用的可组合决策结构"],
            ["热更", "不停服或短停服刷新配置/资源"],
            ["幂等", "同一请求执行多次结果与一次相同，防重复发奖"],
            ["粘滞（Sticky）", "同一账号尽量落到同一服务实例"],
            ["GSLB", "全局负载均衡，智能DNS/就近接入的基础设施层"],
            ["Resin", "体力资源（二游常见设定）"],
            ["CoopRoom", "联机共斗房间（本项目推荐 ≤4 人）"],
        ],
    )

    add_heading_cn(doc, "附录 C：文档维护说明", 1)
    add_para(
        doc,
        "本 Word 由项目仓库内容汇总生成，面向「所有人都能看懂」的目标，"
        "刻意用业务语言解释工程概念，并保留关键路径与端口便于对照。"
        "若需对外正式发布，建议产品/架构负责人再校对「演示级」边界与对外承诺口径。",
    )
    add_para(
        doc,
        "生成脚本位置（便于日后刷新）：tools/gen_project_summary_docx.py。"
        "重新生成命令：py -3 tools/gen_project_summary_docx.py",
        first_line=True,
        size=11,
    )

    doc.save(out_path)
    print("SAVED:", out_path)
    print("EXISTS:", os.path.exists(out_path))
    print("SIZE:", os.path.getsize(out_path))


if __name__ == "__main__":
    main()
