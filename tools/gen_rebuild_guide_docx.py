# -*- coding: utf-8 -*-
"""Generate MyMmorpg rebuild tutorial Word document on Desktop."""
from pathlib import Path

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn
from docx.shared import Pt, RGBColor, Cm


def set_run_font(run, name="微软雅黑", size=11, bold=False, color=None):
    run.font.name = name
    run._element.rPr.rFonts.set(qn("w:eastAsia"), name)
    run.font.size = Pt(size)
    run.bold = bold
    if color:
        run.font.color.rgb = color


def add_heading_cn(doc, text, level=1):
    h = doc.add_heading(text, level=level)
    for run in h.runs:
        set_run_font(run, size={1: 18, 2: 14, 3: 12}.get(level, 11), bold=True)
    return h


def add_para(doc, text, size=11, bold=False, space_after=6):
    p = doc.add_paragraph()
    run = p.add_run(text)
    set_run_font(run, size=size, bold=bold)
    p.paragraph_format.space_after = Pt(space_after)
    p.paragraph_format.line_spacing = 1.35
    return p


def add_bullet(doc, text, level=0):
    p = doc.add_paragraph(style="List Bullet")
    p.clear()
    run = p.add_run(text)
    set_run_font(run, size=11)
    p.paragraph_format.left_indent = Cm(0.75 * (level + 1))
    p.paragraph_format.space_after = Pt(3)
    return p


def add_code(doc, text):
    p = doc.add_paragraph()
    run = p.add_run(text)
    set_run_font(run, name="Consolas", size=9)
    run._element.rPr.rFonts.set(qn("w:eastAsia"), "微软雅黑")
    p.paragraph_format.left_indent = Cm(0.5)
    p.paragraph_format.space_before = Pt(4)
    p.paragraph_format.space_after = Pt(8)
    p.paragraph_format.line_spacing = 1.15
    # light gray feel via shading on paragraph
    shd = p._element.get_or_add_pPr()
    from docx.oxml import OxmlElement

    shd_elem = OxmlElement("w:shd")
    shd_elem.set(qn("w:fill"), "F5F5F5")
    shd_elem.set(qn("w:val"), "clear")
    shd.append(shd_elem)
    return p


def add_table(doc, headers, rows):
    table = doc.add_table(rows=1 + len(rows), cols=len(headers))
    table.style = "Table Grid"
    hdr = table.rows[0].cells
    for i, h in enumerate(headers):
        hdr[i].text = ""
        run = hdr[i].paragraphs[0].add_run(h)
        set_run_font(run, size=10, bold=True)
    for r_idx, row in enumerate(rows):
        cells = table.rows[r_idx + 1].cells
        for c_idx, val in enumerate(row):
            cells[c_idx].text = ""
            run = cells[c_idx].paragraphs[0].add_run(str(val))
            set_run_font(run, size=9)
    doc.add_paragraph()
    return table


def build():
    doc = Document()
    section = doc.sections[0]
    section.top_margin = Cm(2.2)
    section.bottom_margin = Cm(2.2)
    section.left_margin = Cm(2.5)
    section.right_margin = Cm(2.5)

    # Cover
    for _ in range(3):
        doc.add_paragraph()
    title = doc.add_paragraph()
    title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = title.add_run("MyMmorpg 从零重写实战教程")
    set_run_font(run, size=26, bold=True, color=RGBColor(0x1A, 0x3A, 0x5C))

    sub = doc.add_paragraph()
    sub.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = sub.add_run("Java 17 · Spring Boot 3.2 · 单体可拆微服务 MMORPG 服务端")
    set_run_font(run, size=12, color=RGBColor(0x55, 0x55, 0x55))

    meta = doc.add_paragraph()
    meta.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = meta.add_run("面向：从零搭建 / 对照现有仓库重写\n基于仓库：MyMmorpg（mmorpg-parent）")
    set_run_font(run, size=11)

    tip = doc.add_paragraph()
    tip.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = tip.add_run("建议打印或分屏对照本机仓库阅读；按阶段验收，不要一次写完所有服务。")
    set_run_font(run, size=10, color=RGBColor(0x88, 0x44, 0x00))

    doc.add_page_break()

    # TOC-like outline
    add_heading_cn(doc, "目录概览", 1)
    toc = [
        "一、项目是什么、你要建成什么样",
        "二、技术栈与环境准备",
        "三、整体架构（必读）",
        "四、推荐重写路线图（分 12 阶段）",
        "五、阶段 0～3：父工程、协议、公共层、玩家接入壳",
        "六、阶段 4～6：场景、战斗、社交与经济基础",
        "七、阶段 7～9：活动 MQ、商城硬化、玩法扩展",
        "八、阶段 10～11：Admin、网关、微服务拆分",
        "九、协议与消息处理主链路（手把手）",
        "十、数据库、Redis、配置与密钥",
        "十一、测试、CI 与本地启动检查清单",
        "十二、常见坑与对照现有代码的方法",
        "附录：端口表、模块表、命令号段、关键类路径",
    ]
    for i, t in enumerate(toc, 1):
        add_para(doc, f"{i}. {t}", size=11)

    doc.add_page_break()

    # 1
    add_heading_cn(doc, "一、项目是什么、你要建成什么样", 1)
    add_para(
        doc,
        "MyMmorpg 是一个用 Java 17 + Spring Boot 3 编写的 MMORPG 服务端示例。"
        "它不是单纯的 CRUD 后台，而是同时具备：长连接游戏协议（Netty TCP / 可选 KCP / WebSocket）、"
        "领域微服务（战斗、场景、背包、活动、商城等）、运营后台（Admin）、以及可切换的「单体嵌入」与「微服务远程」两种部署模式。",
    )
    add_para(doc, "重写时你的最低可交付目标（MVP）建议定为：", bold=True)
    add_bullet(doc, "能启动 player-service（单体模式），HTTP 8989 + Netty 游戏端口可用。")
    add_bullet(doc, "客户端（或自写调试工具）能完成：登录/创角 → 进场景 → 开战/结束战斗。")
    add_bullet(doc, "消息按 msgId 路由到 Facade，回包走统一 ProtocolMessage。")
    add_bullet(doc, "MySQL（player_db）+ Redis 可用；至少有账号/玩家表与会话。")
    add_para(
        doc,
        "之后再按路线图叠加背包、大厅、匹配、活动、商城、抽卡等。"
        "切记：先把「接入 + 路由 + 一个完整玩法闭环」跑通，再拆服务。",
    )

    add_heading_cn(doc, "1.1 两种部署模式（重写全程要记住）", 2)
    add_table(
        doc,
        ["模式", "怎么跑", "适合"],
        [
            [
                "单体（默认）",
                "只启动 player-service；Maven 依赖嵌入其它域 JAR；GAME_*_REMOTE_ENABLED=false",
                "本地开发、联调、大部分单测/IT",
            ],
            [
                "微服务",
                "各 *ServiceApplication 独立进程；打开对应 REMOTE_ENABLED；Feign 调 /internal/**",
                "拆分验证、生产形态、压力与故障隔离",
            ],
        ],
    )
    add_para(
        doc,
        "设计意图：同一个领域代码（例如 BattleService）既可以嵌入 player 进程被本地调用，"
        "也可以独立成 battle-service，由 player 通过 Feign 转发。重写时先实现「本地可调用的领域服务」，"
        "再补 Gateway/Feign 开关，最后才真正拆进程。",
    )

    # 2
    add_heading_cn(doc, "二、技术栈与环境准备", 1)
    add_heading_cn(doc, "2.1 技术栈", 2)
    add_table(
        doc,
        ["类别", "选型"],
        [
            ["语言 / 构建", "Java 17、Maven 多模块（mmorpg-parent）"],
            ["框架", "Spring Boot 3.2.5、Spring Cloud 2023.0.x、Spring Cloud Alibaba（Nacos 可选）"],
            ["通信", "Netty TCP、可选 KCP、WebSocket、HTTP/Feign、可选 RocketMQ"],
            ["协议", "Protobuf（mmorpg-protocol）+ 自研 MessageId / Modules"],
            ["数据", "MySQL 8、Redis 7、JPA（ddl-auto=validate）、Flyway（经济/P0 表）"],
            ["测试", "TestNG、Testcontainers / embedded-redis、JaCoCo、SpotBugs"],
            ["运维", "docker compose、Prometheus 计数器、结构化 TLog（Kafka 可选）"],
        ],
    )

    add_heading_cn(doc, "2.2 本机环境检查清单", 2)
    add_bullet(doc, "JDK 17（JAVA_HOME 指向 17）")
    add_bullet(doc, "Maven 3.8+（或使用仓库自带 mvnw / mvnw.cmd）")
    add_bullet(doc, "Docker Desktop（用于 MySQL + Redis；可选 Nacos）")
    add_bullet(doc, "IDE：IntelliJ IDEA 推荐，导入根 pom 为 Maven 工程")
    add_bullet(doc, "Git；复制 .env.example 为 .env（勿提交真实密钥）")

    add_heading_cn(doc, "2.3 第一次拉起依赖", 2)
    add_code(
        doc,
        "cd MyMmorpg\n"
        "copy .env.example .env\n"
        "docker compose up -d mysql redis\n"
        "# 可选：docker compose up -d nacos",
    )
    add_para(
        doc,
        "Compose 会创建 player_db / battle_db / activity_db / update_db，并挂载各库 schema。"
        "单体开发初期主要用到 player_db + Redis。",
    )

    # 3
    add_heading_cn(doc, "三、整体架构（必读）", 1)
    add_heading_cn(doc, "3.1 模块地图", 2)
    add_table(
        doc,
        ["模块", "职责"],
        [
            ["mmorpg-protocol", "Protobuf、Modules、MessageId、封包结构"],
            ["mmorpg-domain-api", "跨模块 Port 接口（薄边界）"],
            ["mmorpg-common", "安全/雪花/MQ/Port 实现底座/AOI 等公共能力"],
            ["player-service", "客户端入口：Netty/WS/HTTP、会话、命令 Gateway"],
            ["scene/chat/bag/skill/hall/quest/matchmaking", "场景与社交玩法域"],
            ["battle/activity/shop/gacha/update", "战斗、活动、经济、抽卡、热更"],
            ["admin-service", "运营导入、热更、RBAC、AI 辅助草案"],
            ["mmorpg-gateway", "可选 HTTP 网关（功能号段/区域路由）"],
            ["mmorpg-cli", "命令行工具"],
        ],
    )

    add_heading_cn(doc, "3.2 请求主链路（写任何玩法前先理解）", 2)
    add_para(doc, "一条完整客户端请求大致经过：")
    add_code(
        doc,
        "客户端二进制帧\n"
        "  → LengthFieldBasedFrameDecoder + GameMessageDecoder（读 4 字节 msgId）\n"
        "  → MessageDispatchPipeline（Netty / WebSocket 共用）\n"
        "  → ClientRequestTask（按玩家分片串行，避免并发乱序）\n"
        "  → @MessageRoute Facade + @RequestHandler（msgId = module*100 + cmd）\n"
        "  → *CommandGateway（本地 Service 或 Feign Remote）\n"
        "  → 领域 Service / Internal API\n"
        "  → ProtocolMessage 编码回写客户端",
    )
    add_para(
        doc,
        "仓库里没有叫 GameCommandDispatcher 的类；等价核心是 GameMessageFactory（扫描路由）"
        " + MessageDispatchPipeline（分发）。GM 另有 GmCommandDispatcher。",
    )

    add_heading_cn(doc, "3.3 Port / Gateway / Feign 三层边界", 2)
    add_bullet(doc, "mmorpg-domain-api：定义跨模块 Port（如 BattleCommandPort、BagItemGrantPort）。")
    add_bullet(doc, "mmorpg-common：Port 的 NoOp / Rest 远程实现、HMAC、Outbox 等基础设施。")
    add_bullet(
        doc,
        "player-service：*CommandGateway —— remoteEnabled 且有 Feign Client 时走远程，否则调本地 Bean。",
    )
    add_para(
        doc,
        "各域独立启动时，Application 类通常带 @ConditionalOnProperty(name=\"spring.application.name\", havingValue=\"xxx-service\")，"
        "这样在单体嵌入扫描时不会再起第二套 Application 入口。",
    )

    add_heading_cn(doc, "3.4 包命名约定", 2)
    add_para(doc, "统一根包：cn.itcast.demo.mymmorpg")
    add_table(
        doc,
        ["层", "典型子包"],
        [
            ["协议", "protocol / protocol.protobuf"],
            ["公共", "port / support / security / mq / rpc / aoi / world"],
            ["player", "handler / net / client / service / web / config"],
            ["域服务", "service / web / config / model / repository / client"],
        ],
    )

    # 4
    add_heading_cn(doc, "四、推荐重写路线图（分 12 阶段）", 1)
    add_para(
        doc,
        "按下列顺序推进。每一阶段结束都要能编译通过，并完成该阶段的「验收标准」。"
        "不要跳过协议与公共层直接写业务。",
    )
    add_table(
        doc,
        ["阶段", "目标", "验收标准"],
        [
            ["0", "父 POM / BOM / 多模块空壳", "mvn -N validate 成功"],
            ["1", "协议 Modules/MessageId/Proto", "protocol 模块编译出类"],
            ["2", "domain-api + common 骨架", "HMAC、雪花、基础 Port 可单测"],
            ["3", "player 接入壳 + Auth 0xx", "TCP 连上能登录/创角"],
            ["4", "场景 1xx + player_db/Redis", "进场景、移动、会话在线"],
            ["5", "战斗/技能/Buff 2xx–4xx", "开战→行动→结束闭环"],
            ["6", "背包/大厅/聊天/任务/匹配", "至少一个社交或背包闭环"],
            ["7", "活动 + MQ inbox/outbox", "BattleEnded 能推进活动进度"],
            ["8", "商城经济硬化 16xx", "订单/幂等/ledger/对账骨架"],
            ["9", "皮肤/挑战/抽卡/肉鸽/更新", "对应号段命令可测"],
            ["10", "admin + gateway + ops", "导入/热更/鉴权可用"],
            ["11", "逐域微服务化", "REMOTE_ENABLED 切换无业务回退"],
        ],
    )

    doc.add_page_break()

    # 5
    add_heading_cn(doc, "五、阶段 0～3：地基怎么打", 1)

    add_heading_cn(doc, "阶段 0：创建父工程", 2)
    add_para(doc, "做法：")
    add_bullet(doc, "新建 Maven 父工程 packaging=pom，artifactId=mmorpg-parent。")
    add_bullet(doc, "parent 使用 spring-boot-starter-parent 3.2.5；properties 固定 java.version=17。")
    add_bullet(doc, "dependencyManagement 引入 spring-cloud-dependencies、spring-cloud-alibaba-dependencies。")
    add_bullet(doc, "先声明空 modules：mmorpg-protocol、mmorpg-domain-api、mmorpg-common、player-service。")
    add_bullet(doc, "预留 profiles：integration-tests、quality-gate（JaCoCo/SpotBugs 可后加）。")
    add_para(doc, "验收：根目录 mvn -N validate；IDE 能识别多模块。")

    add_heading_cn(doc, "阶段 1：协议模块 mmorpg-protocol（重中之重）", 2)
    add_para(doc, "协议是整个游戏服的「合同」。建议按现仓库方式拆：")
    add_bullet(doc, "Modules.java：定义模块号常量（AUTH=0, SCENE=1, BATTLE=2 …）。")
    add_bullet(doc, "MessageId / RetCode：具体命令与错误码。")
    add_bullet(doc, "规则：msgId = module * 100 + cmd。例如 SCENE=1 且 cmd=1 → msgId=101。")
    add_bullet(doc, "命名习惯：*_CS_REQ（客户端请求）、*_SC_RSP（服务端响应）、*_SC_NOTIFY（推送）。")
    add_bullet(doc, "src/main/proto/*.proto：按域拆分（player/scene/battle…），用 protobuf-maven-plugin 生成。")
    add_bullet(doc, "封包辅助：ProtocolMessage、GamePackets、PayloadPacket。")
    add_para(doc, "号段速查（与现仓库 Modules 对齐）：", bold=True)
    add_table(
        doc,
        ["module", "范围", "领域"],
        [
            ["AUTH=0", "0xx", "登录/选角/创角/天赋/踢线"],
            ["SCENE=1", "1xx", "进场景/移动/切线/遭遇"],
            ["BATTLE=2", "2xx", "开战/行动/同步/结束"],
            ["SKILL=3", "3xx", "技能"],
            ["BUFF=4", "4xx", "Buff"],
            ["HALL=5", "5xx", "好友/邮件/排行"],
            ["CHAT=6", "6xx", "聊天"],
            ["BAG=7", "7xx", "背包"],
            ["ACTIVITY=8", "8xx", "活动"],
            ["UPDATE=9", "9xx", "版本清单"],
            ["QUEST=10", "10xx", "任务"],
            ["MATCH=11", "11xx", "匹配"],
            ["SKIN=12", "12xx", "衣柜/穿戴"],
            ["CHALLENGE=13", "13xx", "挑战关卡"],
            ["GACHA=14", "14xx", "抽卡"],
            ["ROGUE=15", "15xx", "肉鸽"],
            ["SHOP=16", "16xx", "商城"],
        ],
    )
    add_para(doc, "验收：protocol 单独 mvn -pl mmorpg-protocol install；能在代码里引用 Modules.BATTLE。")

    add_heading_cn(doc, "阶段 2：domain-api + common", 2)
    add_para(doc, "domain-api（保持薄）：", bold=True)
    add_bullet(doc, "只放跨模块 Port 接口，例如 BattleCommandPort、BagCommandPort、BagItemGrantPort、SkillCommandPort、ActivityCommandPort。")
    add_bullet(doc, "不要在这里放 Spring 实现或 JPA 实体。")
    add_para(doc, "common（厚但要分层清晰）：", bold=True)
    add_bullet(doc, "security：InternalApiSignUtil（HmacSHA256）、InternalApiAuthFilter、会话加密相关。")
    add_bullet(doc, "support：SnowflakeIdGenerator / GlobalUID。")
    add_bullet(doc, "handler 注解：@MessageRoute、@RequestHandler（供 GameMessageFactory 扫描）。")
    add_bullet(doc, "mq：MqOutboxService、MqInbox、死信；初期可先 NoOp。")
    add_bullet(doc, "port 包：接口 + NoOp* + Rest* 远程实现；RemotePortAutoConfiguration。")
    add_para(
        doc,
        "建议：common 一开始只放「接入与跨服务真正需要」的东西，AOI/大世界/支付等可后续迁入，避免一上来 common 变成巨型泥球。",
    )
    add_para(doc, "验收：写一个 InternalApiSignUtil 单元测试；雪花 ID 不重复。")

    add_heading_cn(doc, "阶段 3：player-service 接入壳", 2)
    add_para(doc, "必须实现的类（对照现仓库路径）：", bold=True)
    add_table(
        doc,
        ["职责", "建议类名"],
        [
            ["Spring Boot 入口", "MyMmorpgApplication（scanBasePackages=cn.itcast.demo.mymmorpg）"],
            ["Netty TCP", "BaseServer（game.netty.port，默认约 8089）"],
            ["编解码", "GameMessageDecoder / Encoder + LengthField 帧"],
            ["KCP（可后做）", "GameKcpServer"],
            ["WebSocket（可后做）", "WebSocketConfig + PlayerBinaryWebSocketHandler"],
            ["路由注册", "GameMessageFactory"],
            ["分发", "MessageDispatchPipeline + ClientRequestTask"],
            ["鉴权玩法", "AuthFacade（0xx）"],
        ],
    )
    add_para(doc, "实现步骤建议：")
    add_bullet(doc, "先让 Netty 能 accept，打印收到的 msgId 与长度。")
    add_bullet(doc, "再实现 GameMessageFactory：启动时扫描 @MessageRoute，建立 msgId → MethodHandle。")
    add_bullet(doc, "实现 Auth：登录、创角、选角；玩家实体落 MySQL；会话 token 放 Redis。")
    add_bullet(doc, "application.yml：server.port=8989；spring.profiles.active=dev；数据源与 Redis。")
    add_bullet(doc, "提供 application-dev.yml（宽松密钥）与 application-prod.yml（生产 fail-fast 校验可后加）。")
    add_para(doc, "启动命令：")
    add_code(
        doc,
        "docker compose up -d mysql redis\n"
        "mvn -pl player-service -am spring-boot:run -Dspring-boot.run.profiles=dev",
    )
    add_para(doc, "验收：用简易客户端或测试发送 AUTH 请求，能收到成功回包；Redis 出现在线键。")

    doc.add_page_break()

    # 6
    add_heading_cn(doc, "六、阶段 4～6：核心玩法闭环", 1)

    add_heading_cn(doc, "阶段 4：场景域（可先写在 common/player，再抽 scene-service）", 2)
    add_bullet(doc, "命令：进场景、移动、实体同步、切线（Modules.SCENE / 1xx）。")
    add_bullet(doc, "空间：可先做简单房间坐标校验；后续再上 AoiGrid 空间哈希 AOI。")
    add_bullet(doc, "会话：player:online:{id} 等 Redis 键；断线清理。")
    add_bullet(doc, "预测与反作弊可后置：MovementPredictionValidator、AntiCheatService。")
    add_para(doc, "验收：登录后进场景，移动包有回执或广播给同场景玩家。")

    add_heading_cn(doc, "阶段 5：战斗 / 技能 / Buff", 2)
    add_para(doc, "推荐实现顺序：")
    add_bullet(doc, "BattleService：start / action / end，状态机清晰（创建→进行中→结算）。")
    add_bullet(doc, "战斗状态优先放 Redis；活跃战斗索引用 SET（避免 KEYS）。")
    add_bullet(doc, "player 侧 BattleFacade → BattleCommandGateway → 本地 BattleService。")
    add_bullet(doc, "预留 Feign：RemoteBattleGateway + BattleCommandClient → POST /internal/battle/*。")
    add_bullet(doc, "技能/Buff 可先做配置驱动的数值加减，再做元素反应引擎。")
    add_bullet(doc, "独立 battle-service：BattleServiceApplication + InternalBattleController + schema/battle_db.sql。")
    add_para(doc, "验收：完整打一场战斗；结束事件可先打日志，为阶段 7 的 MQ 留钩子。")

    add_heading_cn(doc, "阶段 6：背包、大厅、聊天、任务、匹配", 2)
    add_para(doc, "每个域建议统一模板：")
    add_code(
        doc,
        "xxx-service/\n"
        "  web/InternalXxxController     # /internal/** 供 Feign\n"
        "  service/XxxService            # 领域逻辑\n"
        "  config/XxxPortConfiguration   # Port 绑定 / NoOp\n"
        "  repository/ 或使用 common 共享实体\n"
        "  resources/application.yml + schema/*.sql（如需独立库）",
    )
    add_bullet(doc, "背包（7xx）：道具增删改查；发奖走 BagItemGrantPort，强调幂等键。")
    add_bullet(doc, "大厅（5xx）：好友、邮件、排行榜（Redis ZSET，miss 回源 MySQL TopN）。")
    add_bullet(doc, "聊天（6xx）：频道消息 + 敏感词过滤（SensitiveWord.txt）。")
    add_bullet(doc, "任务（10xx）：JSON 配置驱动（config/quest/Quests.json）+ 进度落库。")
    add_bullet(doc, "匹配（11xx）：队列放 Redis；可用 SET NX + Lua；分片与跨 shard 配对可后做。")
    add_para(doc, "验收：任选「邮件附件发奖」或「匹配成功开战斗」打通跨域调用。")

    # 7
    add_heading_cn(doc, "七、阶段 7～9：活动、经济、扩展玩法", 1)

    add_heading_cn(doc, "阶段 7：活动 + 消息队列", 2)
    add_bullet(doc, "activity-service：活动配置、玩家进度（可用 Redis JSON）、领奖。")
    add_bullet(doc, "异步优先：battle 发布 BattleEnded；activity 消费更新进度。")
    add_bullet(doc, "可靠性：生产者 Outbox（mq_outbox）+ 消费者 Inbox（mq_inbox）幂等。")
    add_bullet(doc, "开发期 RocketMQ 可关闭（NoOp）；生产显式 ROCKETMQ_ENABLED=true。")
    add_bullet(doc, "对照类：BattleEndedMqConsumer、ShopOrderPaidMqConsumer、Flyway V1__p0_mq_inbox.sql。")
    add_para(doc, "验收：结束战斗后活动进度变化；重复投递不会重复发奖。")

    add_heading_cn(doc, "阶段 8：商城与经济硬化（非常重要）", 2)
    add_para(doc, "游戏经济最容易被刷，重写时尽早按「账本 + 幂等」设计：")
    add_bullet(doc, "订单表 shop_order；支付事件走 outbox。")
    add_bullet(doc, "grant_idempotency / wallet_ledger / item_ledger。")
    add_bullet(doc, "支付：dev 可用 MOCK；prod 必须 SHOP_PAYMENT_MOCK_ENABLED=false + 渠道密钥。")
    add_bullet(doc, "对账入口：对比 ChannelBillProvider 账单差异。")
    add_bullet(doc, "战令/月卡/首充可做在 shop 或 activity，接口放 /internal/shop/pass/* 等。")
    add_para(doc, "验收：模拟支付成功 → 发货一次；重复回调不重复加货币。")

    add_heading_cn(doc, "阶段 9：皮肤 / 挑战 / 抽卡 / 肉鸽 / 更新", 2)
    add_bullet(doc, "皮肤 12xx：配置 config/skin/SkinConfigs.json；衣柜与穿戴。")
    add_bullet(doc, "挑战 13xx：联动 BattleService.startChallengeBattle。")
    add_bullet(doc, "抽卡 14xx：独立 gacha-service（端口 8994）；保底/十连/概率公示/审计。")
    add_bullet(doc, "肉鸽 15xx：局内天赋；结算回写。")
    add_bullet(doc, "更新 9xx：update-service + 资源清单；差分/CDN 预热可后做。")
    add_para(doc, "验收：每个号段至少 1 条请求/响应自动化测试。")

    # 8
    add_heading_cn(doc, "八、阶段 10～11：运营面与微服务化", 1)

    add_heading_cn(doc, "阶段 10：Admin + Gateway", 2)
    add_bullet(doc, "admin-service：配置导入（活动/任务/商城等）、投诉、操作日志。")
    add_bullet(doc, "安全：IP 白名单、HMAC/API Key、RBAC 预设（OPS/PLANNER/CS/SUPERADMIN）。")
    add_bullet(doc, "热更：POST /admin/ops/reload 分阶段刷新缓存；发布审计与回滚/Diff。")
    add_bullet(doc, "mmorpg-gateway：功能号段路由 FunctionNumberRoutingFilter；可选 GeoIP 就近接入。")
    add_para(doc, "验收：通过 Admin 导入一份活动 JSON，reload 后玩家侧可见。")

    add_heading_cn(doc, "阶段 11：真正拆微服务", 2)
    add_para(doc, "推荐拆分顺序（与 docs/service-boundaries.md 一致）：")
    add_bullet(doc, "先保证 player 仍是唯一客户端入口（长连接不拆散）。")
    add_bullet(doc, "先拆 battle（调用链清晰、易事件驱动），再拆 activity（消费事件）。")
    add_bullet(doc, "再拆 bag/shop 等经济相关；最后弱耦合域（chat/update）。")
    add_para(doc, "切换步骤：")
    add_bullet(doc, "各域独立启动，确认 /internal/** + HMAC 互通。")
    add_bullet(doc, "player 设 GAME_BATTLE_REMOTE_ENABLED=true 等，配置 *_SERVICE_URL。")
    add_bullet(doc, "独立 activity 时注意 GAME_PORT_REMOTE_ENABLED 与 BAG_SERVICE_URL，避免发奖 NoOp。")
    add_bullet(doc, "逐步去掉 player 对域模块的 Maven 嵌入依赖，只保留 Feign（目标形态见 docs/architecture-gateway.md）。")
    add_para(doc, "验收：同一套客户端协议在单体与微服务模式下行为一致。")

    doc.add_page_break()

    # 9
    add_heading_cn(doc, "九、协议与消息处理主链路（手把手）", 1)
    add_heading_cn(doc, "9.1 新增一条业务命令的标准步骤", 2)
    add_para(doc, "假设你要新增「背包使用道具」：")
    add_bullet(doc, "1）在 Modules 确认 BAG=7；分配未占用 cmd，例如 7。msgId=707。")
    add_bullet(doc, "2）在 proto 增加 UseItemReq/Rsp；重新生成。")
    add_bullet(doc, "3）在 MessageId / RetCode 登记常量。")
    add_bullet(doc, "4）写 BagFacade：类上 @MessageRoute(module=Modules.BAG)，方法 @RequestHandler(cmd=7)。")
    add_bullet(doc, "5）Facade 调 BagCommandGateway.useItem(...)。")
    add_bullet(doc, "6）Gateway：本地 BagService 或 Feign InternalBagController。")
    add_bullet(doc, "7）Service 改库存，写 ledger（如需要），返回结果。")
    add_bullet(doc, "8）补 TestNG 单测 + 可选 WebMvcIT。")
    add_para(doc, "不要在 Netty Handler 里直接写业务 SQL——一律进 Facade → Gateway → Service。")

    add_heading_cn(doc, "9.2 并发与线程模型注意点", 2)
    add_bullet(doc, "同一玩家请求建议串行（ClientRequestTask 分片），避免背包/战斗状态竞态。")
    add_bullet(doc, "推送多玩家时注意 Channel 是否可写；KCP/TCP 背压策略（不可写时丢弃非关键推送）。")
    add_bullet(doc, "跨线程传递 TraceId（TraceContext），方便排障。")

    add_heading_cn(doc, "9.3 内部 HTTP 契约习惯", 2)
    add_bullet(doc, "路径统一 /internal/...，不对公网。")
    add_bullet(doc, "生产开启 GAME_INTERNAL_API_ENABLED=true，使用 INTERNAL_API_SECRET 做 HMAC。")
    add_bullet(doc, "Feign FallbackFactory（scene/hall/battle）提升容错；熔断配置可参考 deploy/observability。")

    # 10
    add_heading_cn(doc, "十、数据库、Redis、配置与密钥", 1)
    add_heading_cn(doc, "10.1 数据库", 2)
    add_table(
        doc,
        ["库", "权威 DDL 位置", "用途"],
        [
            ["player_db", "player-service/.../schema/player_db.sql", "账号/玩家/好友/邮件/任务进度等"],
            ["battle_db", "battle-service schema / docker/mysql/02-*.sql", "战斗相关"],
            ["activity_db", "activity-service/.../schema/activity_db.sql", "活动"],
            ["update_db", "update-service/.../schema/update_db.sql", "更新清单"],
        ],
    )
    add_bullet(doc, "JPA：spring.jpa.hibernate.ddl-auto=validate（不要用 update 蒙混生产）。")
    add_bullet(doc, "增量用 Flyway：classpath:db/migration，baseline-on-migrate=true。")
    add_bullet(doc, "P0 经济相关：V1__p0_economy_tables.sql（ledger、outbox、idempotency）。")
    add_bullet(doc, "详读仓库 docs/db-migration.md。")

    add_heading_cn(doc, "10.2 Redis 常见键用途", 2)
    add_bullet(doc, "会话 / 在线：player:online:{id}、会话 ticket")
    add_bullet(doc, "匹配：match:queue:* / match:player:* / match:status:*")
    add_bullet(doc, "排行：rank:level / rank:power（ZSET）")
    add_bullet(doc, "战斗：battle 状态 Hash/String + 活跃 SET")
    add_bullet(doc, "跨服好友：cross:friend:{id}")
    add_para(doc, "原则：热数据 Redis + 定时/事件落 MySQL；禁止生产使用 KEYS 扫描。")

    add_heading_cn(doc, "10.3 配置文件分层", 2)
    add_bullet(doc, "Spring：各服务 application.yml + application-dev/prod.yml。")
    add_bullet(doc, "环境变量：.env.example → .env；生产密钥走环境/密钥管理系统，勿入库。")
    add_bullet(doc, "玩法配置：仓库根 config/ 下 JSON/YAML/CSV（gacha、quest、shop、skin…）。")
    add_bullet(doc, "可选 Nacos：optional:nacos:player-service.yaml。")

    add_heading_cn(doc, "10.4 生产必检密钥（摘自部署矩阵）", 2)
    add_bullet(doc, "INTERNAL_API_SECRET 强密钥；GAME_INTERNAL_API_ENABLED=true")
    add_bullet(doc, "ADMIN_AUTH_ENABLED=true；ADMIN_HMAC_SECRET 强密钥")
    add_bullet(doc, "SESSION_RSA_PRIVATE_KEY / PUBLIC_KEY 固定注入（禁止每次临时生成）")
    add_bullet(doc, "SHOP_PAYMENT_MOCK_ENABLED=false；配置渠道 secret")
    add_bullet(doc, "MYSQL_PASSWORD 强密码")

    # 11
    add_heading_cn(doc, "十一、测试、CI 与本地启动检查清单", 1)
    add_heading_cn(doc, "11.1 测试策略", 2)
    add_bullet(doc, "框架：TestNG；各模块 testng-unit.xml / testng-integration.xml。")
    add_bullet(doc, "默认：mvn -B test 跑单元。")
    add_bullet(doc, "集成：mvn -B test -Pintegration-tests（常依赖 MySQL/Redis service 或 Testcontainers）。")
    add_bullet(doc, "质量门禁：mvn -B verify -Pquality-gate（JaCoCo + SpotBugs）。")
    add_bullet(doc, "单测优先 Mock 领域依赖；IT 用 *ApplicationIT / *WebMvcIT。")
    add_para(doc, "重写建议：每完成一个 Facade/Service 就写对应 *Test，避免后期补测债务。")

    add_heading_cn(doc, "11.2 每日开发启动清单", 2)
    add_code(
        doc,
        "1. docker compose ps   # mysql/redis healthy\n"
        "2. 确认 .env 已复制\n"
        "3. mvn -pl player-service -am spring-boot:run -Dspring-boot.run.profiles=dev\n"
        "4. 检查日志：Netty bind、数据源、Redis\n"
        "5. 用 Postman/自研客户端打 AUTH + SCENE\n"
        "6. 改代码后优先跑：mvn -pl <模块> -am test",
    )

    add_heading_cn(doc, "11.3 微服务联调最小集合", 2)
    add_bullet(doc, "先起依赖域（battle/bag/activity…），再起 player。")
    add_bullet(doc, "所有服务 INTERNAL_API_SECRET 必须一致。")
    add_bullet(doc, "对照 DEPLOYMENT.md 端口表检查冲突。")

    # 12
    add_heading_cn(doc, "十二、常见坑与对照现有代码的方法", 1)
    add_heading_cn(doc, "12.1 常见坑", 2)
    add_bullet(doc, "单体扫描到域 Application：忘记 ConditionalOnProperty，导致重复启动或冲突。")
    add_bullet(doc, "远程开关开了但服务没起：Gateway 调 Feign 失败；先确认 Fallback 与错误码。")
    add_bullet(doc, "发奖 NoOp：独立 activity 未配置远程 Bag Port，生产应 fail-fast。")
    add_bullet(doc, "ddl-auto 与 schema 不一致：validate 失败——先改 SQL/Flyway 再改实体。")
    add_bullet(doc, "协议号冲突：新增 cmd 前先搜 Modules/MessageId。")
    add_bullet(doc, "在 Netty IO 线程做阻塞 DB：导致全服卡顿——业务放到业务线程池/玩家串行队列。")
    add_bullet(doc, "支付 MOCK 带到生产：必须靠 ProductionSecretsValidator 类校验拦截。")

    add_heading_cn(doc, "12.2 如何对照现有仓库学习", 2)
    add_para(doc, "不要从「最大的 Service 文件」开始读。推荐阅读顺序：")
    add_bullet(doc, "1）Modules.java、一条 proto、一个 Facade。")
    add_bullet(doc, "2）GameMessageFactory + MessageDispatchPipeline。")
    add_bullet(doc, "3）BattleCommandGateway 的 remote/local 分支。")
    add_bullet(doc, "4）InternalBattleController 与对应 Feign Client。")
    add_bullet(doc, "5）一份 *ServiceTest 看领域不变量。")
    add_bullet(doc, "6）docs/service-boundaries.md、architecture-gateway.md、troubleshooting.md。")

    add_heading_cn(doc, "12.3 重写时的范围控制建议", 2)
    add_para(
        doc,
        "现仓库能力很多（大世界 AOI、Boss 行为树、元素反应、GeoIP、CDN 预热等）。"
        "从零重写请严格按 MVP→闭环→硬化→拆分。把「演示级」和「已实现生产向」区分开（见 DEPLOYMENT.md 能力分层），"
        "先交付可玩可测的骨架，再逐项对齐高级能力。",
    )

    doc.add_page_break()

    # Appendix
    add_heading_cn(doc, "附录 A：服务端口表", 1)
    add_table(
        doc,
        ["服务", "HTTP 端口", "说明"],
        [
            ["player-service", "8989", "客户端连接、会话、命令入口"],
            ["scene-service", "8981", "场景/AOI"],
            ["chat-service", "8982", "聊天"],
            ["bag-service", "8983", "背包"],
            ["skill-service", "8984", "技能"],
            ["admin-service", "8985", "后台"],
            ["hall-service", "8986", "大厅"],
            ["quest-service", "8987", "任务"],
            ["matchmaking-service", "8988", "匹配"],
            ["shop-service", "8990", "商城"],
            ["battle-service", "8991", "战斗"],
            ["activity-service", "8992", "活动"],
            ["update-service", "8993", "更新"],
            ["gacha-service", "8994", "抽卡"],
            ["mmorpg-gateway", "8443（可配）", "可选 API 网关"],
            ["Netty 游戏端口", "约 8089", "TCP/KCP，见 game.netty.*"],
        ],
    )

    add_heading_cn(doc, "附录 B：关键入口类", 1)
    add_table(
        doc,
        ["角色", "类"],
        [
            ["单体/玩家入口", "player-service/.../MyMmorpgApplication"],
            ["Netty TCP", "player-service/.../net/BaseServer"],
            ["消息工厂/分发", "GameMessageFactory、MessageDispatchPipeline"],
            ["战斗微服务", "BattleServiceApplication"],
            ["活动微服务", "ActivityServiceApplication"],
            ["商城微服务", "ShopServiceApplication"],
            ["HTTP Gateway", "mmorpg-gateway/.../GatewayApplication"],
            ["CLI", "mmorpg-cli/.../MmorpgCliApplication"],
        ],
    )

    add_heading_cn(doc, "附录 C：推荐文档阅读清单", 1)
    add_bullet(doc, "README.md — 快速开始")
    add_bullet(doc, "DEPLOYMENT.md — 能力分层、环境变量矩阵、微服务切换")
    add_bullet(doc, "docs/service-boundaries.md — 服务边界与 Feign/事件契约")
    add_bullet(doc, "docs/architecture-gateway.md — Gateway-only 目标形态")
    add_bullet(doc, "docs/db-migration.md — 库表与 Flyway")
    add_bullet(doc, "docs/troubleshooting.md — 拓扑与排障")
    add_bullet(doc, "docs/openapi.md — HTTP/协议/Postman")
    add_bullet(doc, "docs/open-world-top-tier.md — 大世界进阶（后期）")
    add_bullet(doc, ".env.example — 全量环境变量样例")

    add_heading_cn(doc, "附录 D：四周学习/重写排期示例", 1)
    add_table(
        doc,
        ["周次", "目标"],
        [
            ["第 1 周", "阶段 0～3：协议 + 接入壳 + 登录创角"],
            ["第 2 周", "阶段 4～5：场景 + 战斗闭环 + 单测"],
            ["第 3 周", "阶段 6～7：背包/大厅 + 活动 MQ 幂等"],
            ["第 4 周", "阶段 8～10：商城硬化 + Admin；尝试拆 battle 远程"],
        ],
    )
    add_para(
        doc,
        "若你是边学边写：每周只追求一个「可演示 Demo」，周五做回顾（哪些类该下沉 common、哪些该成独立服务）。",
    )

    add_heading_cn(doc, "结语", 1)
    add_para(
        doc,
        "重写 MyMmorpg 的正确姿势是：协议合同先行、接入管道其次、一个玩法闭环验证架构、"
        "再用 Port/Gateway 开关平滑走向微服务，最后才做经济硬化与运营后台。"
        "请始终用测试与验收标准约束范围，避免一次性复制仓库里所有「炫技」能力。",
    )
    add_para(
        doc,
        "文档生成自当前 MyMmorpg 仓库结构；若你后续改了模块边界，请以仓库内 docs/ 与 DEPLOYMENT.md 为准同步修订本文。",
        size=10,
    )

    desktop = Path.home() / "Desktop"
    out_en = desktop / "MyMmorpg-Rebuild-Guide.docx"
    out_cn = desktop / ("MyMmorpg" + "\u4ece\u96f6\u91cd\u5199\u5b9e\u6218\u6559\u7a0b.docx")
    doc.save(str(out_en))
    doc.save(str(out_cn))
    print(out_en)
    print(out_cn)


if __name__ == "__main__":
    build()
