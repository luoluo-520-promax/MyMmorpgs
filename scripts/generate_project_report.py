# -*- coding: utf-8 -*-
"""Generate MyMmorpg project summary Word report to Desktop."""
from datetime import datetime
from pathlib import Path

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH, WD_LINE_SPACING
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from docx.shared import Cm, Pt, RGBColor


def set_run_font(run, name="微软雅黑", size=11, bold=False, color=None):
    run.font.name = name
    run._element.rPr.rFonts.set(qn("w:eastAsia"), name)
    run.font.size = Pt(size)
    run.bold = bold
    if color is not None:
        run.font.color.rgb = color


def add_page_number(paragraph):
    """Insert PAGE field into paragraph."""
    run = paragraph.add_run()
    fld_char_begin = OxmlElement("w:fldChar")
    fld_char_begin.set(qn("w:fldCharType"), "begin")

    instr_text = OxmlElement("w:instrText")
    instr_text.set(qn("xml:space"), "preserve")
    instr_text.text = " PAGE "

    fld_char_separate = OxmlElement("w:fldChar")
    fld_char_separate.set(qn("w:fldCharType"), "separate")

    fld_char_end = OxmlElement("w:fldChar")
    fld_char_end.set(qn("w:fldCharType"), "end")

    run._r.append(fld_char_begin)
    run._r.append(instr_text)
    run._r.append(fld_char_separate)
    run._r.append(fld_char_end)
    set_run_font(run, size=9)


def add_toc_field(paragraph):
    """Insert TOC field (Word will populate on open / update fields)."""
    run = paragraph.add_run()
    fld_char_begin = OxmlElement("w:fldChar")
    fld_char_begin.set(qn("w:fldCharType"), "begin")

    instr_text = OxmlElement("w:instrText")
    instr_text.set(qn("xml:space"), "preserve")
    instr_text.text = r' TOC \o "1-3" \h \z \u '

    fld_char_separate = OxmlElement("w:fldChar")
    fld_char_separate.set(qn("w:fldCharType"), "separate")

    # Placeholder until Word refreshes TOC
    placeholder = OxmlElement("w:t")
    placeholder.text = "（请在 Word 中右键目录 →「更新域」以生成完整目录与页码）"

    fld_char_end = OxmlElement("w:fldChar")
    fld_char_end.set(qn("w:fldCharType"), "end")

    run._r.append(fld_char_begin)
    run._r.append(instr_text)
    run._r.append(fld_char_separate)
    run._r.append(placeholder)
    run._r.append(fld_char_end)
    set_run_font(run, size=11, color=RGBColor(0x66, 0x66, 0x66))


def add_update_fields_on_open(doc):
    """Ask Word to update fields when document opens."""
    settings = doc.settings.element
    update_fields = OxmlElement("w:updateFields")
    update_fields.set(qn("w:val"), "true")
    settings.append(update_fields)


def setup_styles(doc):
    style = doc.styles["Normal"]
    style.font.name = "微软雅黑"
    style.font.size = Pt(11)
    style._element.rPr.rFonts.set(qn("w:eastAsia"), "微软雅黑")
    pf = style.paragraph_format
    pf.line_spacing_rule = WD_LINE_SPACING.ONE_POINT_FIVE
    pf.space_after = Pt(6)

    for i, size in [(1, 18), (2, 14), (3, 12)]:
        hs = doc.styles[f"Heading {i}"]
        hs.font.name = "微软雅黑"
        hs.font.size = Pt(size)
        hs.font.bold = True
        hs.font.color.rgb = RGBColor(0x1A, 0x3A, 0x5C)
        hs._element.rPr.rFonts.set(qn("w:eastAsia"), "微软雅黑")


def add_heading(doc, text, level=1):
    return doc.add_heading(text, level=level)


def add_para(doc, text, bold=False, indent=False):
    p = doc.add_paragraph()
    run = p.add_run(text)
    set_run_font(run, bold=bold)
    if indent:
        p.paragraph_format.first_line_indent = Cm(0.74)
    return p


def add_bullets(doc, items, level=0):
    for item in items:
        p = doc.add_paragraph(style="List Bullet")
        run = p.add_run(item)
        set_run_font(run)
        if level:
            p.paragraph_format.left_indent = Cm(0.75 * level)


def add_numbered(doc, items):
    for item in items:
        p = doc.add_paragraph(style="List Number")
        run = p.add_run(item)
        set_run_font(run)


def add_table(doc, headers, rows):
    table = doc.add_table(rows=1 + len(rows), cols=len(headers))
    table.style = "Table Grid"
    hdr = table.rows[0].cells
    for i, h in enumerate(headers):
        hdr[i].text = ""
        p = hdr[i].paragraphs[0]
        run = p.add_run(h)
        set_run_font(run, size=10, bold=True)
    for r_idx, row in enumerate(rows):
        cells = table.rows[r_idx + 1].cells
        for c_idx, val in enumerate(row):
            cells[c_idx].text = ""
            p = cells[c_idx].paragraphs[0]
            run = p.add_run(str(val))
            set_run_font(run, size=10)
    doc.add_paragraph()
    return table


def setup_header_footer(doc, title):
    section = doc.sections[0]
    section.top_margin = Cm(2.5)
    section.bottom_margin = Cm(2.5)
    section.left_margin = Cm(2.5)
    section.right_margin = Cm(2.5)

    header = section.header
    header.is_linked_to_previous = False
    hp = header.paragraphs[0]
    hp.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    run = hp.add_run(title)
    set_run_font(run, size=9, color=RGBColor(0x88, 0x88, 0x88))

    footer = section.footer
    footer.is_linked_to_previous = False
    fp = footer.paragraphs[0]
    fp.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run1 = fp.add_run("第 ")
    set_run_font(run1, size=9)
    add_page_number(fp)
    run2 = fp.add_run(" 页")
    set_run_font(run2, size=9)


def build_cover(doc):
    for _ in range(4):
        doc.add_paragraph()
    title = doc.add_paragraph()
    title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = title.add_run("MyMmorpg 项目全面总结报告")
    set_run_font(run, size=28, bold=True, color=RGBColor(0x1A, 0x3A, 0x5C))

    sub = doc.add_paragraph()
    sub.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = sub.add_run("Java 17 / Spring Boot 3 大型多人在线角色扮演游戏（MMORPG）服务端")
    set_run_font(run, size=14, color=RGBColor(0x44, 0x44, 0x44))

    for _ in range(2):
        doc.add_paragraph()

    meta_lines = [
        f"报告生成日期：{datetime.now().strftime('%Y年%m月%d日')}",
        "文档性质：项目总览 / 技术说明 / 能力清单 / 部署与运维指南",
        "适用读者：产品、策划、开发、测试、运维、管理人员及对项目感兴趣的所有人",
        "仓库定位：支持单体与微服务双模式的 MMORPG 服务端示例工程",
    ]
    for line in meta_lines:
        p = doc.add_paragraph()
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        run = p.add_run(line)
        set_run_font(run, size=11)

    note = doc.add_paragraph()
    note.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = note.add_run(
        "\n说明：打开本文档后，若目录页码未自动刷新，请按 Ctrl+A 全选，"
        "再按 F9，或右键目录选择「更新域」→「更新整个目录」。"
    )
    set_run_font(run, size=10, color=RGBColor(0x66, 0x66, 0x66))

    doc.add_page_break()


def build_toc_page(doc):
    add_heading(doc, "目录", level=1)
    add_para(
        doc,
        "本目录由 Word 域自动生成，层级对应正文一级至三级标题。更新域后即可看到带页码的完整目录。",
    )
    p = doc.add_paragraph()
    add_toc_field(p)
    doc.add_page_break()


def build_body(doc):
    # ---------- 1 ----------
    add_heading(doc, "一、项目概述：这是什么？", level=1)
    add_para(
        doc,
        "MyMmorpg 是一个用 Java 17 和 Spring Boot 3 构建的大型多人在线角色扮演游戏（MMORPG）服务端示例工程。"
        "简单说：它负责在服务器上处理玩家登录、走路、战斗、聊天、背包、任务、商城充值、抽卡、活动、"
        "世界 Boss、后台运营等一整套“游戏后端”能力，而不是客户端画面本身。",
        indent=True,
    )
    add_para(
        doc,
        "项目有一个非常重要的设计：同一套代码可以按两种方式运行——",
        indent=True,
    )
    add_bullets(
        doc,
        [
            "单体模式（默认）：主要只启动 player-service，其它玩法模块“嵌”在同一个进程里，适合本地开发、学习、演示。",
            "微服务模式：把场景、战斗、活动、商城等拆成独立服务，通过 Feign/HTTP 互相调用，适合更接近线上的拆分部署。",
        ],
    )
    add_para(
        doc,
        "能力设计上对标“二次元开放世界联机游戏”常见形态（类似原神类产品的服务端思路）："
        "优先 4 人联机房间共斗，而不是一开始就做千人同屏；同时保留大世界 AOI、跨服好友、战令月卡、"
        "元素反应、体力、抽卡保底等玩法骨架，方便继续产品化。",
        indent=True,
    )

    add_heading(doc, "1.1 一句话价值", level=2)
    add_bullets(
        doc,
        [
            "对新人：可当作完整可运行的游戏后端学习样板（协议、缓存、支付、热更、CI 都有）。",
            "对研发：单体起步、按域拆分，降低从 Demo 到微服务的迁移成本。",
            "对运维/管理：有明确的环境变量矩阵、生产密钥校验、观测与发布审计路径。",
        ],
    )

    add_heading(doc, "1.2 技术栈一览（通俗解释）", level=2)
    add_table(
        doc,
        ["技术", "通俗理解", "在本项目中的作用"],
        [
            ["Java 17", "现代 Java 语言版本", "全部业务代码的基础语言"],
            ["Spring Boot 3.2.5", "快速搭后端的框架", "各微服务的启动与配置"],
            ["Spring Cloud / Alibaba", "微服务全家桶", "服务发现、Feign 调用、可扩展网关"],
            ["Netty", "高性能网络通信库", "游戏实时协议（TCP，可选 UDP/KCP）"],
            ["Protobuf", "紧凑的二进制消息格式", "客户端与服务器之间的游戏包"],
            ["MySQL", "关系型数据库", "账号、订单、邮件、任务等持久数据"],
            ["Redis", "高速内存缓存/队列", "在线态、匹配队列、排行榜、热数据"],
            ["RocketMQ（可选）", "消息队列", "支付事件、战斗结束等异步链路"],
            ["Docker Compose", "一键起依赖", "本地 MySQL / Redis / 可选 Nacos"],
            ["TestNG + CI", "自动化测试与流水线", "单元/集成测试、质量门禁"],
        ],
    )

    # ---------- 2 ----------
    add_heading(doc, "二、读者导读：不同角色怎么看这份报告", level=1)
    add_table(
        doc,
        ["角色", "建议重点阅读章节", "你能获得什么"],
        [
            ["产品 / 策划", "一、三、五、十一", "已有玩法边界、演示级与计划中能力"],
            ["后端开发", "二、四、六、七、八、九", "模块边界、接口、数据与一致性"],
            ["客户端开发", "四、六、协议相关小节", "入口端口、命令号段、会话与重连"],
            ["测试", "九、十、十二", "测试命令、集成依赖、验收清单"],
            ["运维 / SRE", "八、十、十二", "部署模式、密钥、观测、HA"],
            ["管理者", "一、三、十一、十三", "整体进度、风险与后续路线"],
        ],
    )

    # ---------- 3 ----------
    add_heading(doc, "三、能力分层：已实现 / 演示级 / 计划中", level=1)
    add_para(
        doc,
        "为避免“文档写得很全、实际却半成品”的误解，项目把能力分成三层。"
        "阅读时请以本节为准理解成熟度。",
        indent=True,
    )

    add_heading(doc, "3.1 已实现（可运行、有落地代码）", level=2)
    add_bullets(
        doc,
        [
            "单体与微服务双模式切换（远程 Port / Feign 命令转发）。",
            "内部 API HMAC 鉴权；生产环境密钥启动 fail-fast 校验。",
            "Admin 后台：IP 白名单、HMAC/API Key、配置导入、投诉、操作日志。",
            "分阶段热更与发布审计（activity → update → player 配置缓存）。",
            "AI 助手：默认 rule-coach；LLM 关闭时规则回退。",
            "智能 NPC 对话；战斗 AI 队友指挥；Boss 行为树（实时战斗不依赖 LLM）。",
            "元素反应引擎（附着→触发→反应）；体力 Resin；装备随机词条。",
            "联机房间 CoopRoom（最多 4 人）；战令 / 月卡 / 首充双倍；七日签到。",
            "玩家与背包冷热分离（Redis 热数据 + 定时落 MySQL）。",
            "移动延迟补偿 SnapshotBuffer；反作弊（超速/瞬移/伤害溢出等）。",
            "TLog 行为日志（默认可落结构化日志，可选 Kafka）。",
            "GlobalUID 雪花算法；跨服好友 Redis；Center 多节点路由与迁移票据。",
            "大世界底座：AOI 网格、按密度拆合 Zone、无缝交接。",
            "世界 Boss、事件小队、好友助战、家园拜访、跨服 4 人副本匹配。",
            "抽卡微服务（保底/十连/天井/概率公示/审计）；肉鸽玩法骨架。",
            "支付沙箱 HMAC + 对账 SPI；订单 outbox / 发奖幂等 / 账本流水（经济硬化）。",
            "CI：单元测试、集成测试、JaCoCo + SpotBugs 质量门禁。",
        ],
    )

    add_heading(doc, "3.2 演示级（能跑，但产品化未完成）", level=2)
    add_bullets(
        doc,
        [
            "匹配：双人副本 / PVP / 跨服 4 人可用，但跨服场景实例生命周期仍简化。",
            "部分活动/战斗奖励数值为演示常量；世界 Boss 产出 grantPlans 后需走 bag/mail 履约。",
            "好友助战 / 家园拜访为 Redis 快照级社交，非完整养成装修产品。",
            "智能 NPC 以规则为主，LLM 为可选增强。",
            "AOI 压测为单进程基准，不是真实集群千人同屏。",
            "RocketMQ / TLog Kafka 开发默认关闭，生产需显式开启。",
            "会话 RSA 未配置时每次启动临时生成密钥（生产必须固定密钥）。",
        ],
    )

    add_heading(doc, "3.3 计划中（尚未实施）", level=2)
    add_bullets(
        doc,
        [
            "继续拆分 battle/skill/activity 的 CommandPort；再建 mmorpg-infra。",
            "player-service 去除剩余域 Maven 依赖，真正 gateway-only。",
            "完整 RedLock/Redisson；官方支付 SDK 直连；KCP 与参考实现对齐。",
            "真实 GeoIP/GSLB、跨区域多活、战斗反作弊规则引擎生产化。",
            "战令任务配置表可视化、元素反应策划表编辑器、TLog 落 Kafka/ES。",
        ],
    )

    # ---------- 4 ----------
    add_heading(doc, "四、整体架构：系统如何协作", level=1)
    add_para(
        doc,
        "可以把整套系统想象成一家“游戏公司大楼”：玩家从大门进来，前台（player-service / 可选网关）"
        "接待并转发；各楼层（场景、战斗、商城……）各司其职；地下室是 MySQL/Redis 存数据；"
        "监控室负责日志与告警。",
        indent=True,
    )

    add_heading(doc, "4.1 逻辑分层", level=2)
    add_numbered(
        doc,
        [
            "接入层：客户端通过 HTTP / WebSocket / Netty TCP（及可选 UDP/KCP）连入；可选 mmorpg-gateway 做区域路由与粘滞。",
            "网关/会话层：player-service 负责登录鉴权、会话、在线管理，并把游戏命令路由到各领域。",
            "领域服务层：scene / battle / bag / hall / quest / match / shop / activity / gacha / admin 等。",
            "基础设施层：MySQL 四库、Redis（可 Sentinel）、可选 RocketMQ / Kafka / Nacos。",
            "运维面：Admin 导入与热更、CLI 工具、Prometheus 指标与告警骨架。",
        ],
    )

    add_heading(doc, "4.2 单体模式（默认推荐起步）", level=2)
    add_para(
        doc,
        "只启动 player-service（HTTP 默认 8989），Maven 把各域模块嵌入同一进程，"
        "game.*.remote.enabled=false。依赖只需 MySQL + Redis。",
        indent=True,
    )
    add_para(doc, "典型启动步骤：", bold=True)
    add_numbered(
        doc,
        [
            "docker compose up -d mysql redis",
            "复制 .env.example 为 .env（开发默认即可）",
            "mvn -pl player-service -am spring-boot:run -Dspring-boot.run.profiles=dev",
        ],
    )

    add_heading(doc, "4.3 微服务模式", level=2)
    add_para(
        doc,
        "各域独立 JAR 运行；player-service 通过 Feign 转发；域服务通过 RestTemplate 远程 Port 回调。"
        "需要统一配置 INTERNAL_API_SECRET，并按需打开 GAME_*_REMOTE_ENABLED。",
        indent=True,
    )

    add_heading(doc, "4.4 服务端口一览", level=2)
    add_table(
        doc,
        ["服务", "HTTP 端口", "一句话职责"],
        [
            ["player-service", "8989", "客户端总入口：登录、会话、命令路由"],
            ["scene-service", "8981", "场景、AOI、怪物、移动与反作弊"],
            ["chat-service", "8982", "聊天"],
            ["bag-service", "8983", "背包、体力、发奖"],
            ["skill-service", "8984", "技能"],
            ["admin-service", "8985", "运营后台、导入、热更、AI 草案"],
            ["hall-service", "8986", "好友、邮件、排行、联机房间"],
            ["quest-service", "8987", "任务"],
            ["matchmaking-service", "8988", "匹配与跨服组队副本"],
            ["shop-service", "8990", "商城货架、订单、支付"],
            ["battle-service", "8991", "战斗计算、AI 队友、挑战"],
            ["activity-service", "8992", "活动、签到、世界事件"],
            ["update-service", "8993", "客户端版本与资源更新清单"],
            ["gacha-service", "8994", "抽卡保底、概率、历史审计"],
            ["mmorpg-gateway", "8443", "可选 HTTP API 网关"],
        ],
    )

    # ---------- 5 ----------
    add_heading(doc, "五、模块说明：仓库里每一块是干什么的", level=1)

    add_heading(doc, "5.1 基础共享模块", level=2)
    add_table(
        doc,
        ["模块", "作用"],
        [
            ["mmorpg-protocol", "全部 Protobuf 协议与 MessageId / RetCode 等协议基础设施"],
            ["mmorpg-domain-api", "跨服务 Port 接口（如背包命令/发货），降低硬依赖"],
            ["mmorpg-common", "公共适配、远程 Port、过渡聚合层"],
            ["mmorpg-gateway", "Spring Cloud Gateway：路由、TraceId、区域头、账号粘滞"],
            ["mmorpg-cli", "命令行工具：导入活动/更新清单等运维操作"],
        ],
    )

    add_heading(doc, "5.2 玩家与接入（player-service）", level=2)
    add_para(
        doc,
        "玩家服务是“总前台”。它持有账号与角色数据，管理 token/在线状态，并通过 Netty 接收游戏二进制协议。"
        "在单体模式下，许多领域逻辑就近嵌入；在微服务模式下，它更多做转发与聚合。",
        indent=True,
    )
    add_bullets(
        doc,
        [
            "登录、选角、踢旧连接（重复登录）。",
            "AI 顾问入口（规则教练 / 可选 LLM）。",
            "Center 路由：查询场景节点计划、签发/消费迁移票据。",
            "冷热分离：热数据进 Redis，定时刷盘 MySQL。",
        ],
    )

    add_heading(doc, "5.3 场景与大世界（scene-service）", level=2)
    add_bullets(
        doc,
        [
            "AoiGrid：空间哈希 AOI，先粗筛格子再按距离精筛。",
            "WorldZoneManager：按玩家密度拆合 Zone。",
            "分线：单线人数上限、空线回收、断线重连快照。",
            "SnapshotBuffer：约 500ms 历史位姿，用于延迟补偿与超速回滚。",
            "AntiCheatService：移动超速/瞬移等校验。",
            "无缝交接：跨节点 MigrationTicket（速度/朝向/zone）。",
        ],
    )

    add_heading(doc, "5.4 战斗（battle-service）", level=2)
    add_bullets(
        doc,
        [
            "战斗开始 / 行动 / 结算；挑战关卡（协议段 13xx）。",
            "元素反应引擎：Aura → Trigger → Reaction → 倍率。",
            "AI 队友听指挥走位；Boss 行为树（狂暴/转阶段/锁仇恨）。",
            "战斗日/周统计写入 Redis Hash。",
            "战斗结束可通过 MQ 通知活动服推进进度（最终一致）。",
        ],
    )

    add_heading(doc, "5.5 背包与经济相关（bag-service / shop-service）", level=2)
    add_bullets(
        doc,
        [
            "背包热数据 Redis + 定时落库；装备随机词条写入 affix_blob。",
            "体力 Resin：自然恢复与每日购买次数。",
            "商城：货架、订单双写、支付验签（生产禁用 MOCK）。",
            "经济硬化：wallet_ledger / item_ledger / grant_idempotency / mq_outbox / mq_inbox。",
            "对账：ChannelBillProvider + reconcile 差异与重试履约。",
            "战令 / 月卡 / 首充双倍（PassService）。",
        ],
    )

    add_heading(doc, "5.6 社交大厅（hall-service）", level=2)
    add_bullets(
        doc,
        [
            "好友在线态（Redis player:online:{id}）；跨服好友 SET。",
            "邮件及附件发奖（幂等落到背包）。",
            "排行榜：Redis ZSET（等级/战力），miss 时回源玩家库 Top50。",
            "CoopRoom 联机房间（≤4）；事件临时小队；好友助战；家园拜访。",
        ],
    )

    add_heading(doc, "5.7 任务 / 匹配 / 抽卡 / 活动 / 更新", level=2)
    add_bullets(
        doc,
        [
            "quest-service：JSON 配置驱动（Quests.json），支持 Admin/Internal 导入与 reload。",
            "matchmaking-service：互补评分匹配；跨实例 SET NX + Lua；跨服 4 人副本（match type=3）。",
            "gacha-service：保底/十连/天井/历史；概率公示与审计摘要（协议段 14xx）。",
            "activity-service：活动配置与进度、七日签到、世界 Boss/事件；战斗结束消费推进。",
            "update-service：客户端版本与资源更新清单，参与分阶段热更。",
        ],
    )

    add_heading(doc, "5.8 后台与 AI 内容面（admin-service）", level=2)
    add_bullets(
        doc,
        [
            "配置导入：活动、更新清单、任务、商城等（经 Feign 调各域）。",
            "分阶段热更 POST /admin/ops/reload；发布历史 GET /admin/ops/publish-history。",
            "投诉处理与操作审计日志。",
            "AI 草案：活动/商城礼包/任务/战斗周报/投诉分类（可规则+模型）。",
            "鉴权：Admin HMAC 或 API Key + 用户 Id；IP 白名单；RBAC 权限点。",
        ],
    )

    add_heading(doc, "5.9 其它玩法与工具", level=2)
    add_bullets(
        doc,
        [
            "皮肤衣柜/穿戴（协议 12xx，配置 SkinConfigs.json）。",
            "肉鸽（15xx）：战斗结算回写，局内天赋指令。",
            "chat-service：聊天通道。",
            "skill-service：技能域。",
            "mmorpg-cli：运维导入命令行。",
        ],
    )

    # ---------- 6 ----------
    add_heading(doc, "六、核心玩法与产品能力详解", level=1)
    add_para(
        doc,
        "本章用“玩家能感受到什么”的语言，解释服务端已经铺好的玩法骨架。",
        indent=True,
    )

    add_heading(doc, "6.1 登录与会话", level=2)
    add_para(
        doc,
        "玩家完成鉴权后进入长连接会话。重复登录会踢掉旧连接。"
        "跨场景节点传送时，可能收到重定向（带 session_ticket），目标节点一次性消费票据进入场景；"
        "断线后可在宽限时间内 ResumeScene 恢复。",
        indent=True,
    )

    add_heading(doc, "6.2 大世界移动与可见性（AOI）", level=2)
    add_para(
        doc,
        "服务器不会把整张地图所有人的状态无脑广播给每个人，而是用 AOI（Area of Interest，兴趣区域）"
        "只同步附近实体。Zone 可按密度动态拆合，便于后续按负载扩容场景节点。"
        "二游产品形态上，更推荐 CoopRoom 小房间共斗；千人同屏压测接口主要用于工程验证。",
        indent=True,
    )

    add_heading(doc, "6.3 战斗与元素反应", level=2)
    add_para(
        doc,
        "战斗由 battle-service 权威计算。元素反应走附着→触发→反应链路，并支持客户端预测相关标记与 rollback。"
        "Boss 用行为树做阶段切换与仇恨，不把实时战斗决策绑在大模型上，保证延迟与确定性。",
        indent=True,
    )

    add_heading(doc, "6.4 联机与匹配", level=2)
    add_bullets(
        doc,
        [
            "CoopRoom：最多 4 人房间共斗，世界 Boss 可在房间内协作。",
            "匹配评分考虑等级、战力、等待时间等互补因素。",
            "跨服副本匹配类型 3：4 人配对，场景号段 9100+modeId。",
        ],
    )

    add_heading(doc, "6.5 成长与养成相关", level=2)
    add_bullets(
        doc,
        [
            "任务：配置表驱动，进度落库。",
            "背包与装备词条：随机副词条可落库。",
            "体力：控制探索/副本消耗节奏。",
            "抽卡：保底与天井、概率公示与审计，满足合规展示需求的骨架。",
            "战令/月卡/首充双倍/七日签到：常见商业化与留存组件。",
        ],
    )

    add_heading(doc, "6.6 社交", level=2)
    add_bullets(
        doc,
        [
            "好友与跨服好友、在线提示。",
            "邮件附件发奖。",
            "排行榜。",
            "助战借用好友快照、家园拜访（演示级）。",
        ],
    )

    add_heading(doc, "6.7 AI 能力边界（重要）", level=2)
    add_para(
        doc,
        "本项目的 AI 分两类，请勿混淆：",
        indent=True,
    )
    add_bullets(
        doc,
        [
            "运营/顾问向：rule-coach、NPC 线索对话、Admin 内容草案——可用 LLM，但失败会回退规则，不阻断服。",
            "实时战斗向：队友指令与 Boss 行为树——明确不依赖 LLM，保证帧级可预期。",
        ],
    )

    # ---------- 7 ----------
    add_heading(doc, "七、协议、命令路由与内部契约", level=1)

    add_heading(doc, "7.1 对外游戏协议", level=2)
    add_para(
        doc,
        "客户端与服务器之间主要使用 Protobuf 二进制包，协议定义集中在 mmorpg-protocol。"
        "不同玩法常用命令号段示例：皮肤 12xx、挑战 13xx、抽卡 14xx、肉鸽 15xx。"
        "HTTP 端口用于登录、管理、内部探针；实时玩法走 Netty。",
        indent=True,
    )

    add_heading(doc, "7.2 内部 API（服务之间）", level=2)
    add_bullets(
        doc,
        [
            "路径前缀：/internal/**",
            "鉴权：HMAC-SHA256（时间戳 + 签名 + 玩家 Id 等头）。",
            "命令型：Protobuf application/octet-stream；查询/Port 型：JSON。",
            "生产必须开启校验并使用强密钥，弱默认值会启动失败。",
        ],
    )

    add_heading(doc, "7.3 同步调用与异步事件", level=2)
    add_para(
        doc,
        "第一批拆分约定：player 接入后同步调用 battle 的 start/action/end；"
        "battle 发布 BattleEnded 等事件，activity 订阅以最终一致方式推进进度。"
        "跨服务一致性优先“事件 + 幂等 + 最终一致”，避免一开始上重型分布式事务。",
        indent=True,
    )

    add_heading(doc, "7.4 Gateway 路由与粘滞", level=2)
    add_bullets(
        doc,
        [
            "示例路由：/player/**、/battle/**、/activity/**。",
            "TraceId 全链路透传，便于排查。",
            "RegionAwareRoutingFilter 注入 X-Region / X-Edge-Pop（应用层就近；真实 GSLB 仍靠基础设施）。",
            "默认按账号 Id / Token 哈希粘滞到同一 player 实例。",
        ],
    )

    # ---------- 8 ----------
    add_heading(doc, "八、数据、缓存与消息", level=1)

    add_heading(doc, "8.1 数据库拆分", level=2)
    add_table(
        doc,
        ["库名", "主要归属", "权威脚本位置（示意）"],
        [
            ["player_db", "账号/玩家/邮件/好友/任务进度/经济表等", "player-service/.../schema/player_db.sql"],
            ["battle_db", "战斗域", "battle-service/.../schema/battle_db.sql"],
            ["activity_db", "活动配置等", "activity-service/.../schema/activity_db.sql"],
            ["update_db", "更新清单", "update-service/.../schema/update_db.sql"],
        ],
    )
    add_para(
        doc,
        "单体模式通常只需 player_db 即可起步；微服务模式应保证四库齐全。"
        "部分服务已启用 Flyway（baseline-on-migrate），新增表以迁移脚本为准并与 schema 同步维护。"
        "活动进度以 Redis JSON 为主，避免继续依赖过时的进度表设计。",
        indent=True,
    )

    add_heading(doc, "8.2 Redis 用途速览", level=2)
    add_bullets(
        doc,
        [
            "在线态、跨服好友、匹配队列与状态。",
            "战斗状态与活跃战斗索引（避免 KEYS 扫描）。",
            "排行榜 ZSET；战斗日/周统计 Hash。",
            "背包/玩家热数据；迁移票据（无 Redis 时可回退进程内）。",
            "生产可切换 Redis Sentinel 高可用 compose 文件。",
        ],
    )

    add_heading(doc, "8.3 消息与幂等", level=2)
    add_bullets(
        doc,
        [
            "支付与发奖链路强调 outbox / inbox 与 grant 幂等，防止重复发货。",
            "RocketMQ 开发默认 NoOp，生产按需开启。",
            "TLog 默认可走日志；开启 Kafka 后异步管道上报。",
        ],
    )

    # ---------- 9 ----------
    add_heading(doc, "九、安全、反作弊与生产校验", level=1)
    add_heading(doc, "9.1 密钥与鉴权矩阵（摘要）", level=2)
    add_table(
        doc,
        ["配置项", "开发", "生产要求"],
        [
            ["INTERNAL_API_SECRET", "可弱/可关校验", "强密钥，必须开启校验"],
            ["ADMIN_HMAC_SECRET / ADMIN_API_KEY", "可弱，鉴权可关", "强密钥，鉴权必须开"],
            ["ADMIN_IP_WHITELIST", "默认可开", "建议开启"],
            ["SESSION_RSA_*", "可空临时生成", "必须固定密钥"],
            ["SHOP_PAYMENT_MOCK_ENABLED", "true", "必须 false，并配置渠道密钥"],
            ["MYSQL_PASSWORD 等", "本地默认可", "强密码必填"],
        ],
    )

    add_heading(doc, "9.2 反作弊", level=2)
    add_para(
        doc,
        "AntiCheatService 已接入场景移动等路径，可检测超速、瞬移、伤害溢出等，并支持违规计数与临时封禁骨架。"
        "完整规则引擎与生产策略仍属后续强化项。",
        indent=True,
    )

    add_heading(doc, "9.3 支付安全", level=2)
    add_para(
        doc,
        "支持渠道沙箱 HMAC 与 HTTP 验签适配；生产禁用 MOCK；提供对账入口对比渠道账单差异，"
        "结合账本与幂等表降低资损与重复履约风险。",
        indent=True,
    )

    # ---------- 10 ----------
    add_heading(doc, "十、部署、运维与可观测性", level=1)

    add_heading(doc, "10.1 本地依赖", level=2)
    add_para(doc, "docker compose up -d 可拉起 MySQL（四库初始化）、Redis、可选 Nacos。", indent=True)

    add_heading(doc, "10.2 热更发布", level=2)
    add_numbered(
        doc,
        [
            "通过 Admin/CLI 导入活动或更新清单。",
            "调用 POST /admin/ops/reload（需相应权限）。",
            "按 activity → update → player 顺序刷新。",
            "用 GET /admin/ops/publish-history 查看审计。",
        ],
    )

    add_heading(doc, "10.3 可观测性", level=2)
    add_bullets(
        doc,
        [
            "Actuator：health / info / metrics / prometheus。",
            "X-Trace-Id 网关到服务透传。",
            "Feign FallbackFactory（scene/hall/battle）与 circuit-breaker 开关。",
            "deploy/observability 下提供 Prometheus 与告警骨架（验签失败、履约停滞、宕机、堆内存、outbox 积压等）。",
        ],
    )

    add_heading(doc, "10.4 弹性建议", level=2)
    add_bullets(
        doc,
        [
            "场景节点按 Zone 负载 HPA。",
            "战斗服务独立扩缩池。",
            "Redis Sentinel / 多 AZ。",
            "跨区域偏目录同步（读多写少），实时权威粘滞到玩家当前 Zone 节点。",
        ],
    )

    # ---------- 11 ----------
    add_heading(doc, "十一、测试与质量保障", level=1)
    add_para(
        doc,
        "测试框架以 TestNG 为主。每个模块通常有 testng-unit.xml（默认快速）与 testng-integration.xml（可能依赖 Docker）。",
        indent=True,
    )
    add_table(
        doc,
        ["命令", "用途"],
        [
            ["mvn test / mvn -B test", "全仓库单元测试"],
            ["mvn test -Pintegration-tests", "集成测试（常需 MySQL/Redis 容器）"],
            ["mvn verify -Pquality-gate", "JaCoCo 覆盖率 + SpotBugs"],
            ["test-suites/ 下聚合 xml", "IDE 一键跑全量单元/集成套件"],
        ],
    )
    add_para(
        doc,
        "GitHub Actions CI 覆盖单元、集成与质量门禁思路，保证主干可持续集成。",
        indent=True,
    )

    # ---------- 12 ----------
    add_heading(doc, "十二、快速上手与常用操作清单", level=1)
    add_heading(doc, "12.1 五分钟跑起单体", level=2)
    add_numbered(
        doc,
        [
            "安装 JDK 17、Maven、Docker。",
            "在仓库根目录：docker compose up -d mysql redis。",
            "复制环境变量样例：cp .env.example .env（Windows 可用 copy）。",
            "启动：mvn -pl player-service -am spring-boot:run -Dspring-boot.run.profiles=dev。",
            "客户端连 HTTP 8989，游戏 Netty 端口见 config/。",
        ],
    )

    add_heading(doc, "12.2 微服务切换要点", level=2)
    add_numbered(
        doc,
        [
            "确保四库与 Redis 就绪，配置同一 INTERNAL_API_SECRET。",
            "独立 activity 时打开远程 Port 并配置 BAG_SERVICE_URL（否则发奖 NoOp，生产会失败）。",
            "生产关闭支付 MOCK，配置渠道密钥。",
            "启动各域服务后，再启动 player 并打开对应 GAME_*_REMOTE_ENABLED。",
        ],
    )

    add_heading(doc, "12.3 推荐阅读的仓库文档", level=2)
    add_table(
        doc,
        ["文档", "内容"],
        [
            ["README.md", "快速开始与模块一览"],
            ["DEPLOYMENT.md", "部署模式、能力分层、环境变量矩阵"],
            [".env.example", "全量环境变量样例"],
            ["docs/service-boundaries.md", "服务边界与 Feign/MQ 契约"],
            ["docs/architecture-gateway.md", "Gateway-only 与 common 拆分路线"],
            ["docs/db-migration.md", "数据库拆分与 Flyway"],
            ["docs/resilience-observability.md", "容错与观测基线"],
            ["TESTING.md", "TestNG 套件与命令"],
        ],
    )

    # ---------- 13 ----------
    add_heading(doc, "十三、演进路线与风险提示", level=1)
    add_heading(doc, "13.1 架构演进方向", level=2)
    add_bullets(
        doc,
        [
            "把 player-service 收敛为真正网关进程：只依赖 protocol/domain-api/common + Feign。",
            "为 battle/skill/activity 等补齐 CommandPort，消除编译期硬依赖。",
            "拆出 mmorpg-infra（net/rpc/support/config/web）。",
            "观测从骨架到完整：统一日志、Alertmanager 通知、限流与多活演练。",
        ],
    )

    add_heading(doc, "13.2 使用与推广时的注意点", level=2)
    add_bullets(
        doc,
        [
            "演示级能力请勿直接当成生产完整产品功能对外承诺。",
            "涉及支付、抽卡概率、封禁处罚，必须叠加真实合规与审计流程。",
            "密钥、证书、渠道密钥严禁提交到 Git；以环境变量/密钥管理系统注入。",
            "压测数据（如 AOI 千人）代表单进程基准，不代表集群容量承诺。",
        ],
    )

    # ---------- 14 ----------
    add_heading(doc, "十四、术语表（人人都能懂）", level=1)
    add_table(
        doc,
        ["术语", "通俗解释"],
        [
            ["MMORPG", "很多人同时在线玩的角色扮演游戏"],
            ["单体", "很多功能打包在一个进程里跑"],
            ["微服务", "按业务拆成多个可独立部署的小服务"],
            ["Feign", "一种声明式 HTTP 客户端，用来调用别的服务"],
            ["Protobuf", "紧凑的二进制通信格式，适合游戏包"],
            ["AOI", "只同步你附近的人/怪，节省带宽与算力"],
            ["HMAC", "用共享密钥做请求签名，防止伪造内部调用"],
            ["幂等", "同一请求做多次，结果像只成功一次，防重复发奖"],
            ["Outbox/Inbox", "可靠发消息/收消息的记账模式，避免丢与重"],
            ["热更", "尽量不停服刷新配置或资源相关缓存"],
            ["行为树 BT", "用树状条件/动作组织 AI 决策，适合 Boss"],
            ["GSLB", "全球负载均衡，让玩家连更近的接入点"],
            ["Sentinel（Redis）", "Redis 高可用主从哨兵模式"],
            ["TraceId", "一次请求的追踪号，串起整条调用链日志"],
        ],
    )

    # ---------- 15 ----------
    add_heading(doc, "十五、总结", level=1)
    add_para(
        doc,
        "MyMmorpg 不是单一 Demo 脚本，而是一套面向“可学习、可演示、可逐步生产化”的 MMORPG 服务端工程。"
        "它用单体降低入门成本，用清晰的服务边界与开关走向微服务；在玩法上覆盖二次元联机常见组件；"
        "在工程上具备协议、缓存、支付硬化、热更审计、测试与观测的基础闭环。",
        indent=True,
    )
    add_para(
        doc,
        "若你是第一次接触本仓库：请先按第十二章把单体跑起来，再结合第三章能力分层理解“哪些能当真、哪些是骨架”。"
        "若你要推进上线：请以第八、九、十章的数据、安全与运维要求为硬门槛，并按第十三章路线继续收敛网关依赖与生产化能力。",
        indent=True,
    )

    add_heading(doc, "附录 A：微服务远程开关速查", level=1)
    add_para(doc, "将下列变量设为 true 可开启对应域的远程转发（需同时配置服务 URL）：", indent=True)
    add_bullets(
        doc,
        [
            "GAME_SCENE_REMOTE_ENABLED",
            "GAME_CHAT_REMOTE_ENABLED",
            "GAME_BAG_REMOTE_ENABLED",
            "GAME_SKILL_REMOTE_ENABLED",
            "GAME_HALL_REMOTE_ENABLED",
            "GAME_QUEST_REMOTE_ENABLED",
            "GAME_MATCH_REMOTE_ENABLED",
            "GAME_BATTLE_REMOTE_ENABLED",
            "GAME_ACTIVITY_REMOTE_ENABLED / GAME_PORT_REMOTE_ENABLED（按实际配置名）",
            "GAME_GACHA_REMOTE_ENABLED",
            "GAME_SHOP_REMOTE_ENABLED 等（详见 .env.example / DEPLOYMENT.md）",
        ],
    )

    add_heading(doc, "附录 B：报告编制说明", level=1)
    add_bullets(
        doc,
        [
            "本报告依据仓库 README、DEPLOYMENT、docs/*、父 POM 模块列表与公开能力说明整理。",
            "生成日期见封面；若仓库后续演进，请以最新代码与 DEPLOYMENT.md 为准。",
            "文档含自动目录与页脚页码；首次打开请更新域以同步页码。",
        ],
    )


def main():
    desktop = Path.home() / "Desktop"
    if not desktop.exists():
        desktop = Path.home() / "桌面"
    # Prefer Unicode path; also write ASCII fallback if OS encoding issues arise
    out = desktop / "MyMmorpg项目全面总结报告.docx"

    doc = Document()
    setup_styles(doc)
    setup_header_footer(doc, "MyMmorpg 项目全面总结报告")
    add_update_fields_on_open(doc)

    build_cover(doc)
    build_toc_page(doc)
    build_body(doc)

    doc.save(str(out))
    print(f"OK: {out}")


if __name__ == "__main__":
    main()
