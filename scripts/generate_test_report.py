# -*- coding: utf-8 -*-
"""Generate MyMmorpg full-test Word report."""
from __future__ import annotations

from datetime import datetime
from pathlib import Path

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn
from docx.shared import Pt, RGBColor

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs" / ("MyMmorpg" + "\u529f\u80fd\u4e0e\u4e1a\u52a1\u6d41\u7a0b\u6d4b\u8bd5\u62a5\u544a.docx")


def set_run_font(run, name="微软雅黑", size=11, bold=False, color=None):
    run.font.name = name
    run._element.rPr.rFonts.set(qn("w:eastAsia"), name)
    run.font.size = Pt(size)
    run.bold = bold
    if color is not None:
        run.font.color.rgb = color


def add_heading(doc, text, level=1):
    h = doc.add_heading(text, level=level)
    for run in h.runs:
        set_run_font(run, size=16 if level == 1 else 13 if level == 2 else 12, bold=True)
    return h


def add_para(doc, text, bold=False, size=11):
    p = doc.add_paragraph()
    run = p.add_run(text)
    set_run_font(run, size=size, bold=bold)
    return p


def add_table(doc, headers, rows):
    table = doc.add_table(rows=1 + len(rows), cols=len(headers))
    table.style = "Table Grid"
    for i, h in enumerate(headers):
        cell = table.rows[0].cells[i]
        cell.text = ""
        run = cell.paragraphs[0].add_run(h)
        set_run_font(run, size=10, bold=True)
    for r_idx, row in enumerate(rows):
        for c_idx, val in enumerate(row):
            cell = table.rows[r_idx + 1].cells[c_idx]
            cell.text = ""
            run = cell.paragraphs[0].add_run(str(val))
            set_run_font(run, size=9)
    doc.add_paragraph()
    return table


def main():
    OUT.parent.mkdir(parents=True, exist_ok=True)
    doc = Document()
    section = doc.sections[0]
    section.top_margin = Pt(72)
    section.bottom_margin = Pt(72)

    title = doc.add_paragraph()
    title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = title.add_run("MyMmorpg 功能与业务流程测试报告")
    set_run_font(run, size=22, bold=True)

    meta = doc.add_paragraph()
    meta.alignment = WD_ALIGN_PARAGRAPH.CENTER
    run = meta.add_run(
        f"生成时间：{datetime.now().strftime('%Y-%m-%d %H:%M')}\n"
        "测试范围：全模块单元测试 + 集成测试\n"
        "工程：mmorpg-parent（Java 17 / Spring Boot 3.2.5 / TestNG）"
    )
    set_run_font(run, size=11)

    add_heading(doc, "1. 测试结论摘要", 1)
    add_para(
        doc,
        "本次对仓库全部 Maven 模块执行了单元测试（mvn -B test）与集成测试（mvn -B test -Pintegration-tests）。"
        "发现并修复 1 个阻塞集成测试的缺陷后，单元测试与集成测试均通过（Failures=0, Errors=0）。",
    )
    add_table(
        doc,
        ["项", "结果"],
        [
            ["单元测试总计", "365 例，失败 0，错误 0，跳过 0"],
            ["集成测试总计", "19 例，失败 0，错误 0，跳过 0"],
            ["整体结论", "通过（含缺陷修复后回归）"],
            ["发现缺陷数", "1（已修复）"],
            ["环境备注", "本机 MySQL80 可用；Docker 未启动，Redis 回退 embedded-redis"],
        ],
    )

    add_heading(doc, "2. 测试环境", 1)
    add_table(
        doc,
        ["项目", "说明"],
        [
            ["操作系统", "Windows 11"],
            ["JDK", "Oracle JDK 22（项目目标 Java 17）"],
            ["构建工具", "Apache Maven 3.9.9"],
            ["测试框架", "TestNG + Spring Boot Test / MockMvc"],
            ["MySQL", "本机 MySQL80（127.0.0.1:3306），IT 多数使用 H2 内存库"],
            ["Redis", "无 Docker 时由 RedisTestContainerHolder 回退 embedded-redis"],
            ["命令-单元", "mvn -B test"],
            ["命令-集成", "mvn -B test -Pintegration-tests"],
        ],
    )

    add_heading(doc, "3. 单元测试结果（按模块）", 1)
    add_para(doc, "执行命令：mvn -B test。各业务模块通过 testng-unit.xml 套件筛选。")
    add_table(
        doc,
        ["模块", "用例数", "失败", "错误", "跳过", "结果"],
        [
            ["mmorpg-common", "83", "0", "0", "0", "通过"],
            ["mmorpg-gateway", "34", "0", "0", "0", "通过"],
            ["scene-service", "13", "0", "0", "0", "通过"],
            ["chat-service", "12", "0", "0", "0", "通过"],
            ["hall-service", "15", "0", "0", "0", "通过"],
            ["quest-service", "15", "0", "0", "0", "通过"],
            ["matchmaking-service", "12", "0", "0", "0", "通过"],
            ["bag-service", "6", "0", "0", "0", "通过"],
            ["battle-service", "54", "0", "0", "0", "通过"],
            ["skill-service", "9", "0", "0", "0", "通过"],
            ["admin-service", "18", "0", "0", "0", "通过"],
            ["shop-service", "6", "0", "0", "0", "通过"],
            ["activity-service", "44", "0", "0", "0", "通过"],
            ["update-service", "11", "0", "0", "0", "通过"],
            ["player-service", "33", "0", "0", "0", "通过"],
            ["mmorpg-protocol / domain-api / cli", "无业务用例", "-", "-", "-", "编译通过"],
            ["合计", "365", "0", "0", "0", "BUILD SUCCESS"],
        ],
    )

    add_heading(doc, "4. 集成测试结果（按模块）", 1)
    add_para(doc, "执行命令：mvn -B test -Pintegration-tests。各模块使用 testng-integration.xml。")
    add_table(
        doc,
        ["模块", "用例数", "结果", "覆盖要点"],
        [
            ["mmorpg-gateway", "2", "通过", "Gateway 应用上下文 / Redis stub 启动"],
            ["scene-service", "1", "通过", "场景内部 API WebMvc"],
            ["battle-service", "3", "通过", "战斗服务上下文 + 内部 API"],
            ["shop-service", "6", "通过", "商城订单/支付相关 IT"],
            ["activity-service", "3", "通过", "活动服务上下文 + 内部 API"],
            ["player-service", "4", "通过", "单体上下文启动 + Port 内部 API"],
            ["其余模块", "0", "套件为空/无 IT", "以单元测试覆盖为主"],
            ["合计", "19", "BUILD SUCCESS", "修复后回归通过"],
        ],
    )

    add_heading(doc, "5. 功能与业务流程测试内容", 1)
    add_para(
        doc,
        "下列按业务域归纳本次自动化覆盖的功能与流程。测试以仓库现有 TestNG 用例为准，"
        "覆盖服务逻辑、策略、导入校验、内部 API 与关键路径安全。",
    )

    add_heading(doc, "5.1 玩家与账号（player-service）", 2)
    add_table(
        doc,
        ["业务流程/功能", "测试类/范围", "验证点"],
        [
            ["玩家进度读写", "PlayerProgressServiceTest", "进度更新、查询、边界"],
            ["功能开关/玩法入口", "FunctionServiceTest", "功能可用性判断"],
            ["战斗门面转发", "BattleFacadeTest / RemoteBattleGatewayTest", "本地/远程 battle 调用"],
            ["活动门面", "ActivityFacadeTest", "活动服务代理"],
            ["抽卡（14xx）", "GachaServiceTest", "抽卡发奖、幂等等路径"],
            ["皮肤衣柜/穿戴（12xx）", "SkinServiceTest", "衣柜、穿戴、校验"],
            ["AI 建议", "PlayerAiAdvisorServiceTest", "规则教练建议入口"],
            ["上传存储", "UploadStorageServiceTest", "文件存储路径"],
            ["完整上下文 IT", "PlayerServiceApplicationIT", "H2 + Redis 启动、健康路径"],
            ["内部 Port API IT", "PlayerPortInternalApiWebMvcIT", "内部 HTTP API 契约"],
        ],
    )

    add_heading(doc, "5.2 战斗 / 挑战 / 肉鸽（battle-service）", 2)
    add_table(
        doc,
        ["业务流程/功能", "测试类/范围", "验证点"],
        [
            ["开战/结算主流程", "BattleServiceTest", "创建战斗、结算、状态流转"],
            ["Buff 生效", "BuffServiceTest", "增益叠加、过期"],
            ["挑战关卡（13xx）", "ChallengeServiceTest", "挑战开战联动"],
            ["肉鸽（15xx）", "RogueServiceTest", "局内流程/回写"],
            ["日/周战斗统计", "BattleStatsCollectorTest", "Redis Hash 统计"],
            ["场景工厂", "BattleSceneFactoryTest", "战斗场景构建"],
            ["协议号健全性", "BattleMessageIdSanityTest", "消息 ID 不冲突"],
            ["战斗策略默认实现", "DefaultBattlePolicyTest", "策略规则"],
            ["IT：上下文/内部 API", "BattleServiceApplicationIT / BattleInternalApiWebMvcIT", "启动与内部接口"],
        ],
    )

    add_heading(doc, "5.3 活动（activity-service）", 2)
    add_table(
        doc,
        ["业务流程/功能", "测试类/范围", "验证点"],
        [
            ["活动进度与领奖", "ActivityServiceTest", "进度推进、奖励发放条件"],
            ["条件表达式", "ActivityConditionEvaluatorTest", "条件求值"],
            ["战斗推进活动", "ActivityBattleProgressServiceTest", "战斗结束驱动进度"],
            ["配置导入校验/落库", "ActivityImportValidatorTest / ActivityImportApplyTest", "JSON/CSV 导入"],
            ["玩家进度存储", "ActivityPlayerProgressStoreTest", "进度持久化"],
            ["协议号健全性", "ActivityMessageIdSanityTest", "消息 ID"],
            ["IT：上下文/内部 API", "ActivityServiceApplicationIT / ActivityInternalApiWebMvcIT", "启动与内部接口"],
        ],
    )

    add_heading(doc, "5.4 场景 / AOI / 重连（scene-service）", 2)
    add_table(
        doc,
        ["业务流程/功能", "测试类/范围", "验证点"],
        [
            ["进场/移动/AOI", "SceneActorServiceTest", "演员进出、视野、分线"],
            ["断线重连快照", "SceneReconnectStore（生产代码）+ Actor 测试", "token 保存与消费"],
            ["场景策略", "DefaultScenePolicyTest", "默认策略"],
            ["IT：内部 API", "SceneInternalApiWebMvcIT", "内部场景接口"],
        ],
    )

    add_heading(doc, "5.5 大厅：好友 / 邮件 / 排行（hall-service）", 2)
    add_table(
        doc,
        ["业务流程/功能", "测试类/范围", "验证点"],
        [
            ["好友/邮件/排行榜", "HallServiceTest", "好友关系、邮件附件、排行读写"],
        ],
    )

    add_heading(doc, "5.6 任务（quest-service）", 2)
    add_table(
        doc,
        ["业务流程/功能", "测试类/范围", "验证点"],
        [
            ["接取/完成/奖励", "QuestServiceTest", "任务状态机"],
            ["战斗驱动任务进度", "QuestBattleProgressServiceTest", "战斗结果推进"],
            ["任务配置加载", "QuestConfigServiceTest", "JSON 配置驱动"],
        ],
    )

    add_heading(doc, "5.7 匹配（matchmaking-service）", 2)
    add_table(
        doc,
        ["业务流程/功能", "测试类/范围", "验证点"],
        [
            ["入队/配对/出队", "MatchmakingServiceTest", "等级/战力带宽配对"],
            ["互补评分", "MatchCompatibilityScorerTest（common）", "评分算法"],
        ],
    )

    add_heading(doc, "5.8 背包 / 技能 / 聊天", 2)
    add_table(
        doc,
        ["业务流程/功能", "测试类/范围", "验证点"],
        [
            ["道具策略", "DefaultItemPolicyTest（bag）", "使用/叠加规则"],
            ["技能策略", "DefaultSkillPolicyTest / GroovySkillPolicyTest", "释放校验、脚本策略"],
            ["聊天策略", "DefaultChatPolicyTest / GroovyChatPolicyTest", "频道/屏蔽/脚本策略"],
        ],
    )

    add_heading(doc, "5.9 商城与支付（shop-service + common）", 2)
    add_table(
        doc,
        ["业务流程/功能", "测试类/范围", "验证点"],
        [
            ["下单/发奖流程", "ShopServiceTest", "货架、订单、发奖"],
            ["订单状态机", "ShopOrderStatusTest", "状态迁移"],
            ["CSV/导入校验", "ShopCsvImporterTest / ShopImportValidatorTest", "配置导入"],
            ["HMAC 沙箱验签", "HmacSandboxShopPaymentVerifierTest", "渠道签名"],
            ["HTTP 回调验签", "HttpCallbackShopPaymentVerifierTest", "远端验签适配"],
            ["IT：商城集成", "shop-service integration 套件（6）", "支付相关 IT"],
        ],
    )

    add_heading(doc, "5.10 后台运营 / 热更 / AI（admin-service）", 2)
    add_table(
        doc,
        ["业务流程/功能", "测试类/范围", "验证点"],
        [
            ["配置导入编排", "AdminImportServiceTest", "活动/清单等导入转发"],
            ["分阶段热更", "HotReloadCoordinatorTest", "activity→update→player 顺序与失败处理"],
            ["投诉处理", "AdminComplaintServiceTest", "投诉流转"],
            ["AI 活动/战斗/商城/投诉建议", "AdminAi*ServiceTest", "规则/模板草稿与分类"],
        ],
    )

    add_heading(doc, "5.11 客户端更新（update-service）", 2)
    add_table(
        doc,
        ["业务流程/功能", "测试类/范围", "验证点"],
        [
            ["版本检查/补丁计划", "UpdateServiceTest / PatchPlanBuilderTest", "差分包规划"],
            ["清单导入", "VersionManifestImportServiceTest", "manifest 导入校验"],
        ],
    )

    add_heading(doc, "5.12 网关安全与可观测（mmorpg-gateway）", 2)
    add_table(
        doc,
        ["业务流程/功能", "测试类/范围", "验证点"],
        [
            ["鉴权过滤器", "AuthGlobalFilterTest", "Token / 透传头"],
            ["限流", "RateLimitGlobalFilterTest", "限流阈值与拒绝"],
            ["TraceId", "TraceIdGlobalFilterTest", "链路追踪头"],
            ["HTTP 观测", "HttpResponseObserveGlobalFilterTest", "状态码观测"],
            ["WS 观测", "ObservabilityWebSocketServiceTest", "WebSocket 观测"],
            ["配置与健康", "Gateway*PropertiesTest / RedisPingHealth*", "配置绑定与 Redis 健康"],
            ["IT：网关启动", "GatewayApplicationIT", "上下文启动"],
        ],
    )

    add_heading(doc, "5.13 公共能力（mmorpg-common）", 2)
    add_table(
        doc,
        ["业务流程/功能", "测试类/范围", "验证点"],
        [
            ["内部 API / Admin HMAC", "InternalApiSignUtilTest / AdminApiSignUtilTest", "签名正确性"],
            ["RPC 编解码与签名", "RpcJsonCodec / Protostuff / RpcSign / RoundBalance", "跨服 RPC"],
            ["Center 路由与迁移票", "CenterSceneRouterTest / MigrationTicketServiceTest", "跨节点迁移"],
            ["抽卡引擎/配置", "GachaDrawEngineTest / GachaConfigServiceTest", "保底与权重"],
            ["皮肤配置", "SkinConfigRepositoryTest / SkinImportValidatorTest", "配置加载校验"],
            ["MQ 适配/分发", "MqMessage*Test", "消息类型注册与分发"],
            ["会话加密", "SessionCryptoServiceTest", "会话加解密"],
            ["全局异常与 Trace", "GlobalExceptionHandlerTest / TraceIdFilterTest / ApiErrorResponseTest", "错误契约"],
            ["发布审计", "ConfigPublishAuditServiceTest", "热更审计记录"],
        ],
    )

    add_heading(doc, "6. 发现问题与修复", 1)
    add_para(doc, "缺陷 #1：SceneReconnectStore 多构造器导致 Spring 无法实例化", bold=True)
    add_para(
        doc,
        "现象：player-service 集成测试加载 ApplicationContext 失败，根因链为 "
        "sceneReconnectStore → SceneActorService → AccountPlayerService → AuthFacade → gameMessageFactory。"
        "错误信息：Failed to instantiate SceneReconnectStore: No default constructor found。",
    )
    add_para(
        doc,
        "原因：SceneReconnectStore 同时存在 Spring 注入构造器"
        "（ObjectProvider<StringRedisTemplate>, ObjectMapper, SceneRuntimeProperties）"
        "与单元测试用单参构造器（SceneRuntimeProperties）。多构造器且未标注 @Autowired 时，"
        "Spring 无法选择注入构造器，回退寻找无参构造器失败。",
    )
    add_para(
        doc,
        "修复：在三参数构造器上增加 @Autowired，明确 Spring 注入入口；保留单参构造器供单元测试直接 new。"
        "文件：scene-service/src/main/java/.../SceneReconnectStore.java。",
    )
    add_para(
        doc,
        "回归：修复后单独重跑 player-service IT 通过；全量 mvn -B test -Pintegration-tests 通过（19/19）；"
        "相关单元测试回归通过。",
    )

    add_heading(doc, "7. 测试执行记录", 1)
    add_table(
        doc,
        ["轮次", "命令", "结果", "说明"],
        [
            ["1", "mvn -B test", "SUCCESS（365）", "全量单元测试首次通过"],
            ["2", "mvn -B test -Pintegration-tests", "FAILURE（player 5 fail）", "SceneReconnectStore 构造器缺陷"],
            ["3", "修复 SceneReconnectStore + 重跑 player IT", "SUCCESS", "缺陷修复验证"],
            ["4", "mvn -B test -pl scene/player -am", "SUCCESS", "单元回归"],
            ["5", "mvn -B test -Pintegration-tests", "SUCCESS（19）", "全量集成回归通过"],
        ],
    )

    add_heading(doc, "8. 风险与建议", 1)
    add_para(
        doc,
        "1）本机未启动 Docker Desktop，集成测试 Redis 走 embedded-redis；与 CI（MySQL+Redis service / Testcontainers）环境略有差异，"
        "建议在 Docker 可用时再跑一轮对照。",
    )
    add_para(
        doc,
        "2）部分模块（chat/hall/quest/matchmaking/bag/skill/admin/update）集成套件为空或仅有占位，"
        "业务以单元测试覆盖为主；若需端到端跨服务联调，建议补充 Feign/MQ 驱动的跨模块 IT。",
    )
    add_para(
        doc,
        "3）同类「多构造器 + 测试辅助构造」模式建议统一加 @Autowired（或 @Primary 工厂），避免生产/IT 启动期再次踩坑。",
    )
    add_para(
        doc,
        "4）quality-gate（JaCoCo + SpotBugs）本次未作为强制门禁执行；如需覆盖率与静态分析报告，可再执行 "
        "mvn -B verify -Pquality-gate。",
    )

    add_heading(doc, "9. 附录：测试套件入口", 1)
    add_table(
        doc,
        ["类型", "入口"],
        [
            ["单元聚合", "test-suites/testng-all-unit.xml"],
            ["集成聚合", "test-suites/testng-all-integration.xml"],
            ["模块单元", "各模块 src/test/resources/testng-unit.xml"],
            ["模块集成", "各模块 src/test/resources/testng-integration.xml"],
            ["CI 工作流", ".github/workflows/ci.yml"],
            ["本报告路径", "docs/MyMmorpg功能与业务流程测试报告.docx"],
        ],
    )

    footer = doc.add_paragraph()
    run = footer.add_run(
        "—— 报告结束。本报告由自动化测试执行结果整理生成，缺陷修复已合入工作区源码。"
    )
    set_run_font(run, size=10, color=RGBColor(0x66, 0x66, 0x66))

    doc.save(OUT)
    print(f"Wrote: {OUT}")


if __name__ == "__main__":
    main()
