# -*- coding: utf-8 -*-
"""就地更新桌面 Word 项目介绍文档，补充 P13 体验优化章节。"""
from docx import Document
from docx.oxml import OxmlElement
from docx.text.paragraph import Paragraph

DOC_PATH = r"c:\Users\ASUS\Desktop\MyMmorpg项目全方位详细介绍文档.docx"


def insert_paragraph_after(paragraph, text="", style=None):
    new_p = OxmlElement("w:p")
    paragraph._p.addnext(new_p)
    new_para = Paragraph(new_p, paragraph._parent)
    if style:
        new_para.style = style
    if text:
        new_para.add_run(text)
    return new_para


def replace_if_contains(doc, needle, new_text):
    for p in doc.paragraphs:
        if needle in p.text:
            p.text = new_text
            return True
    return False


def main():
    doc = Document(DOC_PATH)

    replace_if_contains(
        doc,
        "修订说明",
        "修订说明：在原有 P6–P12（含性能加固 Actor 信箱/对象池/AOI 增量等）基础上，"
        "新增 P13 体验优化（探索罗盘/地图标记/世界影响、区域特色移动/攀爬歇脚/通用探索套件、"
        "生态叙事/环境叙事/探索正向反馈、战斗辅助模式、资源自动化/灵活日常/智能养成）；"
        "技术栈截至 2026-08-22。请在 Word 中全选后按 F9 更新目录。",
    )

    # 3.2.8 P13 产品说明（插在 P11 段落后）
    anchor_32 = None
    for p in doc.paragraphs:
        if "3.2.7" in p.text and "P11" in p.text and p.style.name.startswith("Heading"):
            anchor_32 = p
            break
    if anchor_32 is not None:
        # 找到 P11 小节末尾（下一个 Heading 3 之前）— 在 anchor 后若干段插入
        cur = anchor_32
        for _ in range(12):
            nxt = cur._p.getnext()
            if nxt is None:
                break
            cur = Paragraph(nxt, cur._parent)
            if cur.style.name.startswith("Heading") and "3.2.7" not in cur.text:
                break
        insert_paragraph_after(
            cur,
            "3.2.8 探索便利 / 移动流畅 / 生态沉浸 / 战斗辅助 / 长线减负（P13）",
            "Heading 3",
        )
        cur = doc.paragraphs[[i for i, p in enumerate(doc.paragraphs) if "3.2.8" in p.text][-1]]
        for line in [
            ("Normal", "探索罗盘：区域探索度≥70% 解锁 ExplorationCompassService，探测附近未收集宝箱/神瞳/限时挑战，避免「跟着视频跑」。"),
            ("Normal", "大地图标记：MapMarkerService 统一 COLLECTED/UNCOLLECTED/IN_PROGRESS 状态与追踪图标。"),
            ("Normal", "探索改变世界：ExplorationWorldImpactService 按阈值解锁路径、变更 NPC 对话、触发区域事件。"),
            ("Normal", "区域特色移动：RegionalTraverseService 滑索/气流/高速载具；ClimbRestPointService 攀爬歇脚恢复体力；UniversalTraversalService 账号级解锁核心探索套件（不绑定角色）。"),
            ("Normal", "生态沉浸：EcoNarrativeBridgeService 生态行为×任务联动；EnvironmentalStoryService 场景道具无声叙事；WorldExplorationFeedbackService 探索行为正向改变世界。"),
            ("Normal", "战斗辅助：CombatAssistService 经典/简化模式（自动连招、伤害预警、低血量时间缓速、增强 HitStop）。"),
            ("Normal", "长线减负：ResourceAutomationService 自动矿机/农场；FlexibleDailyQuestService 多路径日常+一键领取；BuildRecommendationService 养成方案推荐与一键配置。"),
            ("List Bullet", "API：GET .../compass/status、POST .../compass/probe、GET .../map/markers、GET .../explore/world-impact、POST .../traverse/regional/use、POST .../traverse/universal/grant-kit、POST .../combat/assist/mode、POST .../automation/*、POST .../daily/*、GET .../build/recommend。"),
            ("List Bullet", "测试：OpenWorldP13FlowTest、OpenWorldP13EdgeCaseTest、OpenWorldP13BusinessFlowTest；权威 docs/open-world-top-tier.md P13。"),
        ]:
            style, text = line
            cur = insert_paragraph_after(cur, text, style)

    # 5.2.11 P13 技术章节（插在 P12 列表后）
    anchor_52 = None
    for p in doc.paragraphs:
        if "5.2.10 P12" in p.text and p.style.name.startswith("Heading"):
            anchor_52 = p
            break
    if anchor_52 is not None:
        cur = anchor_52
        for _ in range(12):
            nxt = cur._p.getnext()
            if nxt is None:
                break
            cur = Paragraph(nxt, cur._parent)
        cur = insert_paragraph_after(
            cur,
            "5.2.11 P13 体验优化（探索便利 / 移动流畅 / 生态沉浸 / 战斗辅助 / 长线减负）",
            "Heading 3",
        )
        for line in [
            ("Normal", "代码详见 docs/open-world-top-tier.md P13 节；门面 OpenWorldGameplayFacade；"
             "GET /gameplay/status?playerId=&regionId= 返回 compass/map_markers/exploration_impacts 等 P13 快照。"),
            ("List Bullet", "探索：ExplorationCompassService（70% 解锁雷达）、MapMarkerService、ExplorationWorldImpactService；"
             "collectWithExplorationFeedback 联动区域进度与世界影响。"),
            ("List Bullet", "移动：RegionalTraverseService、ClimbRestPointService（MovementAdmissionService 集成歇脚点）、"
             "UniversalTraversalService（HOOK/GLIDE/CLIMB/GRAPPLE/SWIM 账号级解锁）。"),
            ("List Bullet", "生态：EcoNarrativeBridgeService、EnvironmentalStoryService、WorldExplorationFeedbackService。"),
            ("List Bullet", "战斗：CombatAssistService（CLASSIC/SIMPLIFIED + HitFeedbackService 增强反馈）。"),
            ("List Bullet", "减负：ResourceAutomationService、FlexibleDailyQuestService、BuildRecommendationService。"),
            ("List Bullet", "测试：OpenWorldP13FlowTest / OpenWorldP13EdgeCaseTest（common）；"
             "OpenWorldP13BusinessFlowTest（scene Internal API 全链路）。"),
        ]:
            style, text = line
            cur = insert_paragraph_after(cur, text, style)

    # 10.1 已实现列表
    for p in doc.paragraphs:
        if "P12 性能加固" in p.text and "Actor 信箱" in p.text and p.style.name.startswith("List"):
            insert_paragraph_after(
                p,
                "P13 体验优化：探索罗盘/地图标记/探索度世界影响、区域滑索与攀爬歇脚、通用探索套件去角色绑定、"
                "生态叙事与环境叙事、探索正向反馈、战斗简化模式、资源自动化/灵活日常/智能养成；"
                "已接入 OpenWorldGameplayFacade、InternalOpenWorldController、MovementAdmissionService。",
                "List Bullet",
            )
            break

    # 第十一章业务故事 11.14
    for p in doc.paragraphs:
        if "11.13" in p.text and "P11" in p.text:
            cur = p
            for line in [
                ("Heading 2", "11.14 探索罗盘到智能养成（P13）"),
                ("Normal", "探索度达 70% → GET /compass/status 解锁 → POST /compass/probe 标记剩余宝箱/挑战；"
                 "GET /map/markers 查看已收集/未收集；GET /explore/world-impact 触发路径/NPC/事件。"),
                ("Normal", "POST /traverse/universal/grant-kit 账号解锁钩锁/滑翔/攀爬；"
                 "POST /traverse/regional/use 使用滑索；攀爬命中歇脚点 POST /move/admit 恢复体力。"),
                ("Normal", "POST /eco/narrative/evaluate 生态×任务；POST /env/story/inspect 环境叙事；"
                 "POST /explore/feedback 清理营地解锁捷径。"),
                ("Normal", "POST /combat/assist/mode SIMPLIFIED 开启辅助；"
                 "POST /automation/build + /automation/claim-all 自动产出；"
                 "POST /daily/report 多路径日常 + /daily/claim-all；GET /build/recommend + POST /build/apply 一键养成。"),
            ]:
                style, text = line
                cur = insert_paragraph_after(cur, text, style)
            break

    # FAQ Q13
    for p in doc.paragraphs:
        if p.text.startswith("Q12") and "P12" in p.text:
            cur = insert_paragraph_after(p, "Q13：P13 探索罗盘/辅助战斗/日常减负如何联调？", "Heading 2")
            insert_paragraph_after(
                cur,
                "答：场景侧 InternalOpenWorldController：/compass/*、/map/markers、/explore/world-impact、"
                "/traverse/regional/*、/traverse/universal/*、/combat/assist/*、/automation/*、/daily/*、/build/*。"
                "GET /gameplay/status?playerId=&regionId= 一次拉取 P13 状态。"
                "回归：mvn -pl mmorpg-common,scene-service test -Dtest=OpenWorldP13*。"
                "权威说明 docs/open-world-top-tier.md（P13）。",
                "Normal",
            )
            break

    # 第十四章 API 速览补充
    for p in doc.paragraphs:
        if "P10 大世界" in p.text and "/env-ai/pickup" in p.text:
            if "P13" not in p.text:
                p.add_run(
                    "；P13：/compass/*、/map/markers、/explore/world-impact、/traverse/regional/*、"
                    "/traverse/universal/*、/eco/narrative/evaluate、/env/story/inspect、/explore/feedback、"
                    "/combat/assist/*、/automation/*、/daily/*、/build/*。"
                )
            break

    # 第十八章总结
    replace_if_contains(
        doc,
        "它覆盖玩家入口、大世界（含 P5～P11",
        "它覆盖玩家入口、大世界（含 P5～P13",
    )
    for p in doc.paragraphs:
        if "MyMmorpg 用现代 Java" in p.text and "集群 AI" in p.text and "P13" not in p.text:
            p.add_run(
                "；P13 探索罗盘与地图标记、区域特色移动与通用探索套件、生态/环境叙事、战斗简化模式、"
                "资源自动化与灵活日常、智能养成推荐。"
            )
            break

    # 附录日期
    for p in doc.paragraphs:
        if "生成日期" in p.text and "P12" in p.text:
            p.text = (
                "生成日期：2026年08月22日（P13 体验优化；P12 性能加固同日；"
                "P10/P11 原地增补同日）"
            )
            break
    for p in doc.paragraphs:
        if "文档生成日期" in p.text:
            p.text = "文档生成日期：2026年08月22日（P13 体验优化）"
            break

    # 文档地图
    for p in doc.paragraphs:
        if "open-world-top-tier.md" in p.text and "P0～P11" in p.text:
            p.text = p.text.replace("P0～P11", "P0～P13")
            break

    doc.save(DOC_PATH)
    print("Updated:", DOC_PATH)


if __name__ == "__main__":
    main()
