# -*- coding: utf-8 -*-
"""原地更新桌面 MyMmorpg 全方位介绍文档：增补 P9 长线养成/立体战斗/生态/家园/韧性/探索终局。"""
from docx import Document
from docx.oxml.ns import qn
from docx.oxml import OxmlElement
from docx.text.paragraph import Paragraph

PATH = r"c:\Users\ASUS\Desktop\MyMmorpg项目全方位详细介绍文档.docx"


def set_para_text(paragraph, text):
    if paragraph.runs:
        paragraph.runs[0].text = text
        for r in paragraph.runs[1:]:
            r.text = ""
    else:
        paragraph.add_run(text)


def insert_paragraph_after(paragraph, text, style=None):
    new_p = OxmlElement("w:p")
    paragraph._p.addnext(new_p)
    new_para = Paragraph(new_p, paragraph._parent)
    if style is not None:
        new_para.style = style
    elif paragraph.style is not None:
        new_para.style = paragraph.style
    new_para.add_run(text)
    return new_para


def find_para(doc, predicate):
    for p in doc.paragraphs:
        if predicate(p.text.strip()):
            return p
    return None


def find_para_exact(doc, text):
    return find_para(doc, lambda t: t == text)


def find_para_startswith(doc, prefix):
    return find_para(doc, lambda t: t.startswith(prefix))


def find_para_contains(doc, needle):
    return find_para(doc, lambda t: needle in t)


def add_table_row(table, cells):
    row = table.add_row()
    for i, val in enumerate(cells):
        if i < len(row.cells):
            row.cells[i].text = val
    return row


def insert_table_after_paragraph(paragraph, rows_data):
    cols = len(rows_data[0])
    tbl = OxmlElement("w:tbl")
    tblPr = OxmlElement("w:tblPr")
    tblW = OxmlElement("w:tblW")
    tblW.set(qn("w:w"), "5000")
    tblW.set(qn("w:type"), "pct")
    tblPr.append(tblW)
    tbl.append(tblPr)
    tblGrid = OxmlElement("w:tblGrid")
    for _ in range(cols):
        gridCol = OxmlElement("w:gridCol")
        tblGrid.append(gridCol)
    tbl.append(tblGrid)

    for row_data in rows_data:
        tr = OxmlElement("w:tr")
        for cell_text in row_data:
            tc = OxmlElement("w:tc")
            tcPr = OxmlElement("w:tcPr")
            tcW = OxmlElement("w:tcW")
            tcW.set(qn("w:w"), str(5000 // cols))
            tcW.set(qn("w:type"), "pct")
            tcPr.append(tcW)
            tc.append(tcPr)
            p = OxmlElement("w:p")
            r = OxmlElement("w:r")
            t = OxmlElement("w:t")
            t.set(qn("xml:space"), "preserve")
            t.text = cell_text
            r.append(t)
            p.append(r)
            tc.append(p)
            tr.append(tc)
        tbl.append(tr)

    paragraph._p.addnext(tbl)
    return tbl


def main():
    doc = Document(PATH)

    # ---------- 封面修订说明 ----------
    p = find_para_startswith(doc, "修订说明：")
    if p:
        set_para_text(
            p,
            "修订说明：已原地增补 P6～P8，以及 P9 长线商业化养成闭环（命座机制挂载/圣遗物定向锁定/专武特效）、"
            "立体战斗（下落重击/水下物理）、动态稀有精英与驯服跟随采集、家园防盗博弈与幻影引导、"
            "韧性霸体与预输入、区域觉醒与世界之核（2026-08-20）。请用 Word 打开后全选按 F9 更新目录域。",
        )

    # ---------- 1.4 定位 ----------
    p = find_para_startswith(doc, "对齐二游常见体验：")
    if p:
        set_para_text(
            p,
            "对齐二游常见体验：元素反应、体力、装备随机词条与定向锁定、命座改机制、战令/月卡、签到、联机房间、"
            "智能 NPC 与 Boss 行为树、公会（组织/科技/远征）、深渊（半月重置高难连打）、"
            "下落重击/水下领域、稀有精英偶遇、家园偷菜博弈、韧性处决、区域全收集觉醒等。",
        )

    # ---------- 第三章：P7 条后补 P9 总览 bullet ----------
    p = find_para_startswith(doc, "【P7 动作感与世界纵深】")
    if p:
        insert_paragraph_after(
            p,
            "【P9 长线养成与探索终局】ConstellationService + SkillCastValidator（命座 ≥1 读 constellation_buff_override，"
            "改冷却/投射物/追加伤害判定；MSG 1210–1212 + AttributeRecalc）；"
            "RelicScoringService/RelicRandomizer + EquipEnhanceService.lockSubStat（POST /internal/bag/relic/lock，"
            "强化跳过锁定位）；BattleTriggerService（专武 special_effect_trigger 与命座解耦）；"
            "FallAttackValidator（ACTION_FALL_HEAVY：≥5m 且 vy<-3，落地硬直与冲击波破坏）；"
            "UnderwaterPhysicsService（BIOME_UNDERWATER，突进缩 60%，OVERLOAD→导电 DoT）；"
            "RareEliteSpawnService（15 分钟≥30 杀→GRID_INFESTATION + RARE_ELITE_APPEAR 500m）；"
            "CreatureUtilityService 跟随采集；HomelandGuardService 偷取≤20%/守卫减速；"
            "PoiseService/InputBufferService（HIT_CONFIRM/处决/预输入+100ms）；"
            "RegionAwakeningService（100%→REGION_MASTERY +72h +5% Buff，最多 3 层）；"
            "WorldCoreDungeonService（奇观全解锁→4 人世界之核 CoopRoom）。"
            "测试：OpenWorldP9FlowTest / OpenWorldP9EdgeCaseTest / OpenWorldP9BusinessFlowTest / RelicLockEnhanceTest。",
            style="List Bullet",
        )

    # ---------- 3.2.4：改「预留未实现」并在其后追加 3.2.5（勿插到 3.3 之后）----------
    p = find_para_startswith(doc, "预留未实现玩法字段：player_characters.constellation_lv")
    if p is None:
        p = find_para_startswith(doc, "仍预留/未完全产品化：")
    if p and "命座机制挂载与圣遗物定向锁定已在 P9" not in p.text:
        set_para_text(
            p,
            "仍预留/未完全产品化：装备套装共鸣完整匹配（suit_id 字段仍在）、"
            "fishing_spot/cooking_recipe（钓鱼 QTE）、world_incident_template（奇遇池）、"
            "个人名片 player:profile——schema 或字段已定稿，部分玩法链未接完。"
            "命座机制挂载与圣遗物定向锁定已在 P9 落地（见 3.2.5 / 5.2.7）。",
        )

    if find_para_exact(doc, "3.2.5 长线养成 / 立体战斗 / 探索终局（P9）") is None:
        anchor = find_para_startswith(doc, "仍预留/未完全产品化：")
        if anchor is None:
            anchor = find_para_startswith(doc, "预留未实现玩法字段：")
        if anchor is None:
            anchor = find_para_exact(doc, "3.2.4 公会与深渊（P8，二游长线社交/练度校验）")
        if anchor:
            bullets = [
                "探索终局：区域探索度 100% 触发 REGION_MASTERY 全服 EPIC_MVP 广播，并给予 72 小时全属性 +5% WorldBuff（最多同时 3 个区域 Buff）；"
                "奇观全解锁后开放「世界之核」4 人联机副本（区别于 P8 深渊）。",
                "手感深化：PoiseService 韧性条破韧处决（EXECUTION_TRIGGER）与开大超级装甲（免疫打断非无敌）；"
                "InputBufferService 在 dodgeWindow 结束后额外 100ms 保留预输入，超时 INPUT_CLEAR。",
                "社交痕迹博弈：家园作物 READY 后好友最多偷 20%；房主可装守卫机关，触发则偷取失败并 DEBUFF_SPEED_DOWN 30s；"
                "高难解谜失败 3 次推荐幻影引导 PHANTOM_GUIDE_MARK（规则模拟路径，不跳过谜题）。",
                "生态偶遇：格子 15 分钟击杀≥30 触发金光稀有精英并 AOI 红点预警；驯服生物可开启跟随采集，"
                "harvest_affinity 加成直入背包。",
                "立体战斗：下落重击服务端校验高度/速度并下发落地硬直；水下独立生物群系缩减突进、改写火雷爆炸反应。",
                "长线养成负反馈规避：命座解锁改技能机制（非纯数值）；圣遗物词条可定向锁定后再强化；"
                "专武精炼绑定 special_effect_trigger，由 BattleTriggerService 独立监听。",
            ]
            # 倒序插在 anchor 后：先插最后 bullet，再 heading，最终 heading 紧挨 anchor
            cursor = anchor
            # 先插 heading，再按正序插 bullets：用多次 after last
            h = insert_paragraph_after(
                cursor,
                "3.2.5 长线养成 / 立体战斗 / 探索终局（P9）",
                style="Heading 3",
            )
            cursor = h
            for b in bullets:
                cursor = insert_paragraph_after(cursor, b, style="List Bullet")

    # ---------- 仓库比喻补一句 ----------
    p = find_para_startswith(doc, "仓库（背包服务）：")
    if p and "定向锁定" not in p.text:
        set_para_text(
            p,
            p.text.strip()
            + " P9 起支持圣遗物/装备副词条定向锁定后再强化。",
        )
    # ---------- 5.2.6 后插入 5.2.7 + 能力表 ----------
    # 避免重复插入：若已存在则跳过
    if find_para_exact(doc, "5.2.7 P9 长线养成 / 立体战斗 / 生态社交 / 韧性手感 / 探索终局") is None:
        p = find_para_startswith(doc, "对照与测试见 docs/guild-abyss.md、docs/open-world-top-tier.md（P8）")
        if p is None:
            p = find_para_exact(doc, "5.2.6 P8 公会与深渊（社交留存 / 长线练度）")
        if p:
            # 找到 P8 说明段落后插
            note_anchor = find_para_startswith(
                doc, "对照与测试见 docs/guild-abyss.md、docs/open-world-top-tier.md（P8）"
            )
            anchor = note_anchor or p
            h = insert_paragraph_after(
                anchor,
                "5.2.7 P9 长线养成 / 立体战斗 / 生态社交 / 韧性手感 / 探索终局",
                style="Heading 3",
            )
            note = insert_paragraph_after(
                h,
                "完整对照与测试见 docs/open-world-top-tier.md（P9 章节）、"
                "OpenWorldP9FlowTest、OpenWorldP9EdgeCaseTest、OpenWorldP9BusinessFlowTest、"
                "RelicLockEnhanceTest。门面仍为 OpenWorldGameplayFacade；场景入口 InternalOpenWorldController；"
                "背包入口 InternalBagController（/internal/bag/relic/*）。",
                style="Normal",
            )
            insert_table_after_paragraph(
                note,
                [
                    ["能力", "实现类", "主要 API / 协议"],
                    [
                        "命座机制挂载",
                        "ConstellationService + SkillCastValidator",
                        "POST .../constellation/unlock、.../skill/cast-resolve；MsgId 1210–1212",
                    ],
                    [
                        "圣遗物定向锁定",
                        "RelicScoringService / EquipEnhanceService",
                        "POST /internal/bag/relic/lock|/relic/enhance",
                    ],
                    [
                        "专武特效解耦",
                        "BattleTriggerService",
                        "onHit / refineWeapon（special_effect_trigger）",
                    ],
                    [
                        "下落重击",
                        "FallAttackValidator",
                        "POST .../fall-attack/validate；MsgId 212–213",
                    ],
                    [
                        "水下物理层",
                        "UnderwaterPhysicsService + MovementAdmission",
                        "POST .../underwater/enter|leave；突进缩 60%",
                    ],
                    [
                        "动态稀有精英",
                        "RareEliteSpawnService",
                        "POST .../rare-elite/kill；MsgId 217 RARE_ELITE_APPEAR",
                    ],
                    [
                        "驯服跟随采集",
                        "CreatureUtilityService",
                        "POST .../creature/follow-harvest",
                    ],
                    [
                        "家园防盗博弈",
                        "HomelandGuardService",
                        "POST .../homeland/guard|steal",
                    ],
                    [
                        "幻影求助引导",
                        "WorldEncounterService",
                        "POST .../phantom/puzzle-fail；MsgId 219",
                    ],
                    [
                        "韧性/霸体",
                        "PoiseService",
                        "POST .../poise/hit；MsgId 214/216 HIT_CONFIRM/EXECUTION",
                    ],
                    [
                        "预输入保留",
                        "InputBufferService",
                        "POST .../input-buffer/arm|enqueue；MsgId 215 INPUT_CLEAR",
                    ],
                    [
                        "区域觉醒",
                        "RegionAwakeningService",
                        "POST .../region/awaken；MsgId 218 REGION_MASTERY",
                    ],
                    [
                        "世界之核",
                        "WorldCoreDungeonService",
                        "POST .../world-core/create；GET .../world-core/status",
                    ],
                ],
            )

    # ---------- 5.4 背包：圣遗物锁定 ----------
    p = find_para_startswith(doc, "发奖强调幂等")
    if p and "relic/lock" not in p.text:
        set_para_text(
            p,
            p.text.strip()
            + " P9：POST /internal/bag/relic/lock 消耗锁定道具固定副词条索引；"
            "强化时 EquipEnhanceService/RelicScoringService 伪随机跳过锁定位，缓解「强化歪了」；"
            "POST /internal/bag/relic/enhance 走圣遗物塑形路径。suit_id 套装共鸣完整匹配仍属后续。",
        )

    # ---------- 10.1 已实现 ----------
    p = find_para_startswith(doc, "抽卡、挑战、肉鸽")
    if p:
        set_para_text(
            p,
            "抽卡、挑战、肉鸽、匹配分片、排行榜、邮件附件幂等发奖；"
            "P8：公会（创建/科技/远征分档/聊天 roster/会长转让）与深渊（12 间连打/星级/里程碑邮件/半月重置）；"
            "P9：命座机制覆盖、圣遗物定向锁定、专武特效触发、下落重击、水下物理、稀有精英、跟随采集、"
            "家园守卫偷菜、幻影引导、韧性/预输入、区域觉醒、世界之核。",
        )

    p = find_para_startswith(doc, "元素反应、体力、词条、战令")
    if p and "P9" not in p.text:
        set_para_text(
            p,
            p.text.strip()
            + "；P9 已接入门面与 Internal API（见 5.2.7）。",
        )

    # ---------- 10.3 计划中 ----------
    p = find_para_startswith(doc, "命座 Buff 挂载、装备套装共鸣匹配")
    if p:
        set_para_text(
            p,
            "装备套装共鸣完整匹配、钓鱼 QTE、对话树/连载任务链、世界奇遇池、"
            "个人名片 player:profile（字段已定稿，部分玩法链未接完）。"
            "命座机制挂载与圣遗物定向锁定已移入「已实现（P9）」。",
        )

    # ---------- 11.x 业务故事 ----------
    if find_para_exact(doc, "11.11 命座解锁到区域觉醒（P9）") is None:
        p = find_para_exact(doc, "第十二章 团队角色如何协作")
        if p:
            steps = [
                "探索度达 100% → REGION_MASTERY 广播 EPIC_MVP 并获得 72h 全属性 +5% Buff；"
                "奇观全解锁后创建世界之核 4 人房。",
                "破韧打空韧性触发 EXECUTION_TRIGGER；开大设置超级装甲免疫打断；"
                "闪避窗口后 100ms 内可预输入重击，超时服务端 INPUT_CLEAR。",
                "好友偷菜≤20%；若房主安装守卫则失败并减速 30s；高难解谜失败 3 次推荐幻影路径引导。",
                "格子杀怪达标刷出金光精英并 AOI 预警；开启跟随采集后额外产出直入背包。",
                "高空 ACTION_FALL_HEAVY 满足高度/速度后结算倍率伤害，未命中则服务端强制落地硬直；"
                "进入 BIOME_UNDERWATER 后突进缩距、火雷爆炸改为导电 DoT。",
                "消耗命座材料解锁 → AttributeRecalc；施法走 constellation_buff_override；"
                "消耗锁定道具固定词条后再强化，避免歪强化。",
            ]
            for s in steps:
                insert_paragraph_after(p, s, style="List Number")
            insert_paragraph_after(p, "11.11 命座解锁到区域觉醒（P9）", style="Heading 2")

    # ---------- 内部 HTTP ----------
    p = find_para_startswith(doc, "内部 HTTP 前缀 /internal/**")
    if p and "constellation" not in p.text:
        set_para_text(
            p,
            p.text.strip()
            + "；P9 大世界：/constellation/unlock、/skill/cast-resolve、/fall-attack/validate、"
            "/underwater/*、/rare-elite/kill、/creature/follow-harvest、/homeland/guard|steal、"
            "/phantom/puzzle-fail、/poise/hit、/input-buffer/*、/region/awaken、/world-core/*；"
            "背包 /internal/bag/relic/lock|/relic/enhance。",
        )

    # ---------- FAQ ----------
    if find_para_exact(doc, "Q9：P9 命座/圣遗物/下落攻击如何联调？") is None:
        p = find_para_exact(doc, "第十六章 名词小词典")
        if p:
            insert_paragraph_after(
                p,
                "场景侧用 InternalOpenWorldController 对应 POST；背包侧 POST /internal/bag/relic/lock 后 enhance；"
                "回归套件：OpenWorldP9BusinessFlowTest（API 全链路）、OpenWorldP9FlowTest / EdgeCaseTest、"
                "RelicLockEnhanceTest。协议常量见 MessageId 1210–1212 与 212–219。",
                style="Normal",
            )
            insert_paragraph_after(p, "Q9：P9 命座/圣遗物/下落攻击如何联调？", style="Heading 2")

    # ---------- 总结 ----------
    p = find_para_startswith(doc, "MyMmorpg 用现代 Java 技术栈")
    if p:
        set_para_text(
            p,
            "MyMmorpg 用现代 Java 技术栈，搭出一套既适合学习演示、又具备微服务扩展潜力的 MMORPG 服务端。"
            "它覆盖玩家入口、大世界（含 P5～P9 探索/手感/动作/养成与终局）、战斗（含 P8 深渊高难回廊）、"
            "社交（含 P8 公会与 P9 家园博弈/幻影引导）、任务、活动、抽卡、商城支付、后台运营、安全与工程化测试等板块，"
            "并吸收二游常见体验（元素反应、体力、战令、签到、联机房间、行为树 Boss、神瞳式收集、区域声望、"
            "命座改机制、圣遗物塑形、下落重击、水下领域、稀有精英、韧性处决、区域觉醒、公会远征、深渊练度校验等）。",
        )

    # ---------- 附录 C ----------
    p = find_para_startswith(doc, "生成日期：")
    if p:
        set_para_text(p, "生成日期：2026年08月20日（P9 长线养成/立体战斗/探索终局增补同日）")

    # ---------- 名词小词典 table 13 ----------
    if len(doc.tables) > 13:
        t13 = doc.tables[13]
        existing = {row.cells[0].text.strip() for row in t13.rows}
        glossary = [
            ("命座 Constellation", "解锁后改技能机制（冷却/投射物/追加判定），非纯数值；伴随 AttributeRecalc"),
            ("圣遗物锁定", "消耗品锁定副词条，强化随机时跳过该区间，缓解歪强化挫败"),
            ("下落重击 Fall Heavy", "服务端校验下落高度与速度的垂直切入攻击；可产生冲击波破坏与落地硬直"),
            ("水下领域", "BIOME_UNDERWATER：突进缩距、火雷爆炸改导电 DoT、独立 Boss BT 分支"),
            ("稀有精英 Infestation", "格子短时击杀达标刷出金光精英，并 AOI 红点预警"),
            ("韧性 Poise", "破韧可触发处决窗口；开大超级装甲免疫打断但伤害照吃；硬直以 HIT_CONFIRM 为准"),
            ("区域觉醒", "探索度 100% 全服广播 + 72h 全属性 Buff；奇观全收集解锁世界之核"),
            ("世界之核", "区别于深渊的 4 人联机终局本，校验练度+探索双毕业"),
        ]
        for name, desc in glossary:
            if name not in existing:
                add_table_row(t13, [name, desc])

    # ---------- 文档地图 table 14 ----------
    if len(doc.tables) > 14:
        t14 = doc.tables[14]
        for row in t14.rows:
            if "open-world-top-tier" in row.cells[0].text:
                row.cells[1].text = "大世界顶尖标准（含 P0～P9）"
                break

    # ---------- P6 装备强化行补充锁定提示（table 6）----------
    if len(doc.tables) > 6:
        t6 = doc.tables[6]
        for row in t6.rows:
            if "装备强化" in row.cells[0].text:
                if "relic/lock" not in row.cells[2].text:
                    row.cells[2].text = (
                        row.cells[2].text.strip()
                        + "；P9 relic/lock 定向锁定"
                    )
                break

    doc.save(PATH)
    print("updated:", PATH)


if __name__ == "__main__":
    main()
