# -*- coding: utf-8 -*-
"""将敷衍的行内注释（条件判断、返回结果等）替换为有业务含义的中文说明。"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

LAZY_COMMENTS = {
    "条件判断", "返回结果", "方法定义", "字段定义", "调用方法", "执行业务步骤",
    "赋值或初始化", "循环遍历", "类型定义", "Spring/框架注解", "否则若条件满足",
    "否则分支", "循环条件", "抛出异常", "访问当前实例字段或方法", "重写父类/接口方法",
    "调用方法或执行语句",
}

IMPORT_HINTS = {
    "SpringApplication": "Spring Boot 应用启动器",
    "SpringBootApplication": "Spring Boot 自动配置与组件扫描",
    "ConfigurationProperties": "绑定 application.yml 配置项",
    "ConfigurationPropertiesScan": "扫描 @ConfigurationProperties 类",
    "EnableFeignClients": "启用 Feign 远程调用客户端",
    "EnableScheduling": "启用定时任务",
    "Component": "注册为 Spring 组件",
    "Service": "注册为 Spring 服务 Bean",
    "Repository": "Spring Data JPA 仓储接口",
    "RestController": "REST 控制器",
    "Configuration": "Spring 配置类",
    "Bean": "声明 Spring Bean",
    "Autowired": "自动注入依赖",
    "Value": "注入配置属性值",
    "Transactional": "声明数据库事务边界",
    "Override": "实现接口或重写父类方法",
    "Entity": "JPA 实体映射",
    "Table": "指定数据库表名",
    "Id": "主键字段",
    "Column": "列映射",
    "GeneratedValue": "主键生成策略",
    "Embeddable": "可嵌入复合主键",
    "EmbeddableId": "复合主键引用",
    "Logger": "SLF4J 日志记录器",
    "LoggerFactory": "日志工厂",
    "StringRedisTemplate": "Redis 字符串操作模板",
    "ObjectMapper": "JSON 序列化/反序列化",
    "Duration": "时间间隔（Redis TTL 等）",
    "Instant": "UTC 时间点",
    "ArrayList": "动态数组列表",
    "HashMap": "哈希映射",
    "HashSet": "哈希集合",
    "List": "列表集合",
    "Set": "集合接口",
    "Collection": "集合接口",
    "Objects": "对象工具类（非空校验等）",
    "ConcurrentHashMap": "线程安全并发映射",
    "AtomicLong": "线程安全自增序列",
    "ChannelHandlerContext": "Netty 通道上下文",
    "WebSocketSession": "Spring WebSocket 会话",
    "GlobalFilter": "Spring Cloud Gateway 全局过滤器",
    "GatewayFilterChain": "Gateway 过滤器链",
    "ServerWebExchange": "响应式 HTTP 交换对象",
    "Mono": "Reactor 单值异步类型",
    "Flux": "Reactor 流式异步类型",
    "Player": "玩家实体",
    "Account": "账号实体",
    "SkillConfig": "技能静态配置",
    "PlayerSkill": "玩家已学技能记录",
    "PlayerSkillId": "玩家技能复合主键",
    "MonsterConfig": "怪物配置",
    "ItemConfig": "道具配置",
    "BuffConfig": "Buff 配置",
    "MapConfig": "地图配置",
    "PlayerBagItem": "背包道具槽位",
    "RetCode": "通用返回码常量",
    "MessageId": "协议消息 ID 常量",
    "ProtocolMessage": "统一协议消息封装（msgId + payload）",
    "ConfigQueryService": "静态配置查询服务",
    "PlayerRepository": "玩家 JPA 仓储",
    "PlayerSkillRepository": "玩家技能 JPA 仓储",
    "SceneActorService": "场景 Actor 与分线管理",
    "PlayerPushRegistry": "在线玩家推送注册表",
    "SkillPolicy": "技能数值策略（伤害/治疗计算）",
    "BattlePolicy": "战斗数值策略",
    "BinaryFrameSender": "二进制帧 WebSocket 发送器",
    "GameMessage": "Netty 游戏消息帧",
    "CastSkillCsReq": "客户端→服务端：施放技能请求",
    "CastSkillScRsp": "服务端→客户端：施放技能响应",
    "GetPlayerSkillsCsReq": "客户端→服务端：查询技能列表请求",
    "GetPlayerSkillsScRsp": "服务端→客户端：技能列表响应",
    "LearnSkillCsReq": "客户端→服务端：学习技能请求",
    "LearnSkillScRsp": "服务端→客户端：学习技能响应",
    "PlayerSkillInfo": "协议层玩家技能详情",
    "SkillCooldownScNotify": "服务端→客户端：技能冷却推送",
    "SkillLearnScNotify": "服务端→客户端：技能习得推送",
    "BuffService": "Buff 施加远程服务",
    "PlayerDataLoadPort": "玩家数据异步预加载端口",
    "PlayerNotificationPort": "玩家下行推送端口",
    "Cipher": "JCE 对称/非对称密码器",
    "KeyGenerator": "对称密钥生成器",
    "SecretKey": "对称密钥",
    "GCMParameterSpec": "AES-GCM 认证加密参数",
    "SecretKeySpec": "从字节构造对称密钥",
    "ByteBuffer": "字节缓冲区（拼接 IV+密文）",
    "KeyPair": "RSA 公钥/私钥对",
    "KeyPairGenerator": "RSA 密钥对生成器",
    "Base64": "Base64 编解码",
    "RocketMQTemplate": "RocketMQ 消息发送模板",
    "DefaultMQProducer": "RocketMQ 生产者",
    "ChannelHandlerContext": "Netty 通道处理器上下文",
    "Bootstrap": "Netty 客户端/服务端启动器",
    "EventLoopGroup": "Netty 事件循环线程组",
}

RETCODE_DESC = {
    "OK": "成功",
    "PLAYER_NOT_SELECTED": "未选择角色",
    "PLAYER_NOT_FOUND": "玩家不存在",
    "ACCOUNT_NOT_FOUND": "账号不存在",
    "PASSWORD_WRONG": "密码错误",
    "NOT_LOGGED_IN": "未登录",
    "SERVER_OVERLOADED": "服务器过载",
    "SKILL_NOT_FOUND": "技能不存在",
    "SKILL_ALREADY_LEARNED": "技能已习得",
    "SKILL_LEVEL_NOT_ENOUGH": "等级不足",
    "SKILL_NOT_LEARNED": "技能未习得",
    "SKILL_PASSIVE": "被动技能不可施放",
    "SKILL_COOLDOWN": "技能冷却中",
    "SKILL_MANA_NOT_ENOUGH": "法力不足",
    "SKILL_CAST_REJECTED": "施法请求被拒绝",
    "SKILL_TARGET_INVALID": "目标无效",
    "SKILL_NOT_IN_SCENE": "不在场景中",
    "SKILL_OUT_OF_RANGE": "超出施法范围",
    "BATTLE_LINEUP_INVALID": "阵容无效",
    "BATTLE_ENEMY_NOT_FOUND": "敌人不存在",
    "BATTLE_ALREADY_ACTIVE": "已有进行中的战斗",
    "BAG_FULL": "背包已满",
    "ITEM_NOT_FOUND": "道具不存在",
    "COUNT_NOT_ENOUGH": "数量不足",
}


def is_lazy(comment: str) -> bool:
    c = comment.strip()
    if c in LAZY_COMMENTS:
        return True
    if re.match(r"^导入 \w+$", c):
        return True
    if c.startswith("声明当前类所在包"):
        return True
    if c.startswith("包声明："):
        return True
    if c.startswith("导入第三方库：") or c.startswith("导入 JDK/Jakarta 类："):
        return True
    if re.match(r"^返回 \S+$", c):
        return True
    if re.match(r"^\w[\w.]* 类型$", c):
        return True
    return False


def retcode_in(text: str) -> str | None:
    m = re.search(r"RetCode\.(\w+)", text)
    if m:
        name = m.group(1)
        return RETCODE_DESC.get(name, name)
    m = re.search(r"BagRetCode\.(\w+)", text)
    if m:
        return m.group(1).lower().replace("_", " ")
    m = re.search(r"ChatRetCode\.(\w+)", text)
    if m:
        return m.group(1).lower().replace("_", " ")
    m = re.search(r"ActivityRetCode\.(\w+)", text)
    if m:
        return m.group(1).lower().replace("_", " ")
    return None


def comment_for_if(cond: str, ctx: dict) -> str | None:
    c = cond.strip()
    if re.search(r"playerId\s*<=\s*0", c):
        return "未选择角色或会话无效"
    if "isEmpty()" in c and ("Opt" in c or "findById" in ctx.get("prev", "")):
        return "数据库中无对应记录"
    if re.search(r"\bsc\s*==\s*null\b", c):
        return "技能配置表中不存在该技能"
    if re.search(r"\bmc\s*==\s*null\b", c):
        return "怪物配置不存在"
    if re.search(r"\bv\s*==\s*null\b", c) and "Redis" in ctx.get("method", ""):
        return "Redis 中尚无缓存"
    if re.search(r"\bv\s*==\s*null\b", c):
        return "缓存或查询结果为空"
    if "existsLearned" in c:
        return "玩家已习得该技能"
    if "SKILL_PASSIVE" in c or "st == SKILL_PASSIVE" in c:
        return "被动技能不可主动施放"
    if "cdRem > 0" in c:
        return "技能仍在冷却中"
    if "curMp < manaCost" in c or "curMp <" in c:
        return "当前法力不足以施放"
    if "TIMESTAMP_WINDOW" in c:
        return "客户端时间戳偏差过大，防重放攻击"
    if "targetCode != RetCode.OK" in c:
        return "目标校验未通过"
    if "ctx != null && ctx.channel().isActive()" in c:
        return "Netty TCP 通道仍存活"
    if re.search(r"\bws\s*!=\s*null\b", c):
        return "回落到 WebSocket 通道推送"
    if "excludePlayerId" in c:
        return "广播时跳过发起者自身"
    if re.search(r"pid\s*!=\s*null\s*&&\s*pid\s*>\s*0", c):
        return "过滤无效的 playerId"
    if "code == RetCode.OK" in c:
        return "仅成功时填充响应数据"
    if "cooldownSec > 0" in c:
        return "该技能有冷却时间"
    if "damage > 0" in c:
        return "产生了伤害效果"
    if "heal > 0" in c:
        return "产生了治疗效果"
    if "hasKey" in c:
        return "Redis 中已有进行中的记录"
    if "getLineupId() == 0" in c:
        return "阵容 ID 无效"
    if "refOpt.isEmpty()" in c:
        return "场景中找不到目标怪物"
    if "getNeedLevel()" in c or "NeedLevel" in c:
        return "角色等级不满足技能学习需求"
    if "== null" in c:
        return "前置查询无结果"
    if "!= 0" in c and "target" in c.lower():
        return "客户端指定了实体目标"
    if "hasPos" in c:
        return "客户端指定了地面坐标目标"
    if "dist.isEmpty()" in c or "dist.get() > range" in c:
        return "目标距离超出技能范围"
    if "tType.isEmpty()" in c:
        return "目标实体不在当前场景分线"
    if "ENTITY_MONSTER" in c or "ENTITY_PLAYER" in c:
        return "校验目标实体类型是否合法"
    if "tid == playerId" in c:
        return "敌人技能不能以自己为目标"
    if "isPlayerInScene" in c:
        return "施法者尚未进入场景"
    if "tt == TARGET_SELF" in c:
        return "自身目标型技能"
    if "tt == TARGET_ENEMY" in c:
        return "敌对目标型技能"
    if "tt == TARGET_ALLY" in c:
        return "友方目标型技能"
    if "tt == TARGET_ALLY || tt == TARGET_SELF" in c:
        return "治疗类技能走治疗公式"
    if "effect.getBuffId() > 0" in c:
        return "效果附带 Buff 需应用到目标"
    if "preloadEnabled" in c:
        return "异步预加载未完成时先返回 loading 态"
    if "info != null" in c:
        return "成功响应携带技能详情"
    if "effects != null" in c:
        return "附带技能效果列表"
    if "remainingCd > 0" in c:
        return "响应中携带剩余冷却秒数"
    return None


def comment_for_return(code: str, ctx: dict) -> str | None:
    rc = retcode_in(code)
    if rc:
        if "RetCode.OK" in code or "RetCode.ok" in code:
            if "listRsp" in code:
                return "返回技能列表成功响应"
            if "learnRsp" in code:
                return "返回习得成功响应"
            if "castRsp" in code:
                return "返回施法成功响应"
            if "startRsp" in code:
                return "返回开战成功响应"
            return "返回成功响应"
        return f"返回「{rc}」错误响应"
    if code.strip() == "return true;":
        return "判定为在线"
    if code.strip() == "return false;":
        return "判定为离线或不可用"
    if code.strip() == "return 0;":
        if "cooldown" in ctx.get("method", "").lower() or "Cooldown" in ctx.get("method", ""):
            return "无剩余冷却"
        return "返回零值"
    if code.strip() == "continue;":
        return "跳过无效条目"
    if re.search(r"return ids;", code):
        return "合并 Netty 与 WebSocket 在线玩家 ID"
    if "Math.max(0, end - now)" in code:
        return "计算剩余冷却秒数（不小于 0）"
    if "Math.max(0, cur - cost)" in code:
        return "扣减后法力不低于 0"
    if "100 + lv * 10" in code:
        return "按等级计算 MP 上限：100 + 等级×10"
    if ".build()" in code and "return" in code:
        return "构建 Protobuf 响应消息"
    if "ProtocolMessage" in code:
        return "封装为统一协议消息"
    if "RetCode.SKILL_TARGET_INVALID" in code:
        return "目标校验失败"
    if "RetCode.OK" in code:
        return "校验通过"
    return None


def comment_for_assign(code: str, ctx: dict) -> str | None:
    if "req.getSkillId()" in code:
        return "从请求中取出技能 ID"
    if "findSkillById" in code:
        return "查 skill_config 静态配置"
    if "playerRepository.findById" in code:
        return "从数据库加载玩家"
    if "findAllByPlayerId" in code:
        return "查玩家全部已学技能"
    if "Instant.now()" in code:
        return "记录当前 UTC 时间"
    if "new PlayerSkill()" in code:
        return "构造新的习得记录"
    if "System.currentTimeMillis()" in code:
        return "取服务端当前毫秒时间戳"
    if "getRemainingCooldown" in code:
        return "查 Redis 中剩余冷却秒数"
    if "ensureMp" in code:
        return "确保 Redis 中有 MP 缓存并返回当前值"
    if "validateCastTarget" in code:
        return "校验施法目标（场景/距离/类型）"
    if "skillPolicy.computeHeal" in code:
        return "按策略公式计算治疗量"
    if "skillPolicy.computeDamage" in code:
        return "按策略公式计算伤害量"
    if "buildSkillInfo" in code:
        return "组装协议层技能详情"
    if "new ArrayList" in code:
        return "初始化可变列表"
    if "new HashSet" in code:
        return "初始化去重集合"
    if "nettyByPlayer.get" in code:
        return "查玩家绑定的 Netty 通道"
    if "wsByPlayer.get" in code:
        return "查玩家绑定的 WebSocket 会话"
    if "stringRedisTemplate.opsForValue().get" in code:
        return "从 Redis 读取字符串值"
    if "REDIS_CD +" in code or "REDIS_MP +" in code:
        return "拼接 Redis 键名"
    if "Long.parseLong" in code or "Integer.parseInt" in code:
        return "将 Redis 字符串解析为数值"
    if "getEpochSecond()" in code:
        return "转为 Unix 秒级时间戳"
    if "getEntityTypeInLine" in code:
        return "查目标在当前分线的实体类型"
    if "distanceBetweenEntities" in code:
        return "计算两实体间距离"
    if "distancePlayerToPoint" in code:
        return "计算玩家到地面点的距离"
    if "getSkillType()" in code or "getTargetType()" in code:
        return "取配置中的类型（默认兜底）"
    if "getManaCost()" in code or "getCooldown()" in code:
        return "取配置中的消耗/冷却（空则 0）"
    if "getLevel()" in code and "null ? 1" in code:
        return "等级为空时默认 1 级"
    if "SkillLearnScNotify.newBuilder" in code:
        return "构建技能习得推送通知"
    if "SkillCooldownScNotify.newBuilder" in code:
        return "构建冷却开始推送通知"
    if "GetPlayerSkillsScRsp.newBuilder" in code:
        return "构建技能列表响应"
    if "LearnSkillScRsp.newBuilder" in code:
        return "构建习得响应"
    if "CastSkillScRsp.newBuilder" in code:
        return "构建施法响应"
    if "PlayerSkillInfo.newBuilder()" in code:
        return "组装客户端可见的技能信息"
    if "EffectInfo.newBuilder()" in code:
        return "构建技能附加效果条目"
    if "battleIdSeq.incrementAndGet()" in code:
        return "分配全局唯一战斗 ID"
    if "playerCombatStats" in code:
        return "按等级计算战斗四维属性"
    if "this." in code and "=" in code and ";" in code:
        m = re.search(r"this\.(\w+)\s*=", code)
        if m:
            return f"注入依赖：{m.group(1)}"
    return None


def comment_for_call(code: str, ctx: dict) -> str | None:
    if "playerSkillRepository.save" in code:
        return "持久化到 player_skill 表"
    if "publishSkillLearned" in code:
        return "发布技能习得 MQ 事件"
    if "publishSkillCast" in code:
        return "发布技能施放 MQ 事件"
    if "playerPushRegistry.send" in code:
        return "推送协议消息到客户端"
    if "stringRedisTemplate.opsForValue().set" in code:
        return "写入 Redis 并设置过期"
    if "consumeMp" in code:
        return "扣减 Redis 中的当前法力"
    if "setCooldownEnd" in code:
        return "记录冷却结束时间到 Redis"
    if "pushCooldownNotify" in code:
        return "推送冷却开始通知（308）"
    if "buffService.applyBuff" in code:
        return "对目标实体施加 Buff"
    if "nettyByPlayer.put" in code:
        return "绑定 Netty 通道到玩家"
    if "wsByPlayer.put" in code:
        return "绑定 WebSocket 到玩家"
    if "nettyByPlayer.remove" in code or "wsByPlayer.remove" in code:
        return "互斥：同一玩家只保留一种连接"
    if "unbind" in code:
        return "解除玩家与连接的双向绑定"
    if "writeAndFlush" in code:
        return "经 Netty 写出游戏消息帧"
    if "BinaryFrameSender.sendWebSocket" in code:
        return "经 WebSocket 发送二进制帧"
    if "addAllSkills" in code:
        return "填充技能列表到响应"
    if "setLoading(true)" in code:
        return "标记为预加载中，客户端可轮询"
    if "setLoading(false)" in code:
        return "标记数据已就绪"
    if "addAll" in code and "keySet" in ctx.get("prev", ""):
        return "合并在线玩家 ID"
    if ".build()" in code:
        return "完成 Protobuf 消息构建"
    if "loadSkillsNow" in code:
        return "同步加载技能列表"
    if "listLoadingRsp" in code:
        return "返回 loading 占位响应"
    if "out.add" in code:
        return "加入响应技能列表"
    if "ids.addAll" in code:
        return "合并连接表中的玩家 ID"
    if "send(pid" in code:
        return "向单个在线玩家推送"
    if "log.debug" in code or "log.warn" in code or "log.error" in code:
        return "记录日志（不阻断主流程）"
    if "keys(" in code:
        return "扫描 Redis 键空间"
    if "hasKey" in code:
        return "检查 Redis 键是否存在"
    if "KeyPairGenerator.getInstance" in code:
        return "创建 RSA 密钥对生成器"
    if "kpg.initialize" in code:
        return "设置 RSA 密钥长度 2048 位"
    if "Cipher.getInstance" in code:
        return "获取 JCE 密码器实例"
    if "cipher.init" in code and "DECRYPT" in code:
        return "初始化解密模式"
    if "cipher.init" in code and "ENCRYPT" in code:
        return "初始化加密模式"
    if "cipher.doFinal" in code:
        return "执行加/解密运算"
    if "Base64.getDecoder().decode" in code:
        return "Base64 解码为字节数组"
    if "Base64.getEncoder().encode" in code:
        return "字节数组编码为 Base64"
    if "KeyGenerator.getInstance" in code:
        return "创建 AES 密钥生成器"
    if "keyGen.generateKey" in code:
        return "生成随机 AES 会话密钥"
    if "SecureRandom" in code and "new" in code:
        return "安全随机数生成 IV"
    if "nextBytes(iv)" in code:
        return "填充 GCM 随机 IV"
    if "ByteBuffer.allocate" in code or "ByteBuffer.wrap" in code:
        return "拼接 IV 与密文或解析缓冲区"
    if "sessionKeys.put" in code or "sessionKeys.get" in code:
        return "读写内存中的会话 AES 密钥"
    if "rocketMQTemplate.syncSend" in code or "rocketMQTemplate.convertAndSend" in code:
        return "同步发送 RocketMQ 消息"
    if "channel.writeAndFlush" in code or "ctx.writeAndFlush" in code:
        return "写出 Netty 响应帧"
    if "future.complete" in code or "future.get" in code:
        return "完成或等待 RPC 异步结果"
    if "ObjectMapper" in code and "readValue" in code:
        return "JSON 反序列化为 Java 对象"
    if "ObjectMapper" in code and "writeValueAsString" in code:
        return "Java 对象序列化为 JSON"
    return None


def comment_for_loop(header: str) -> str | None:
    if "PlayerSkill" in header:
        return "遍历玩家已学技能并组装详情"
    if "playerIds" in header:
        return "逐个向目标玩家推送"
    if "onlinePlayerIds()" in header:
        return "向所有在线玩家广播"
    if "EffectInfo" in header:
        return "逐个处理技能附加效果"
    if "rows" in header:
        return "遍历查询结果"
    return "逐项处理集合元素"


def comment_for_line(stripped: str, ctx: dict) -> str | None:
    if stripped.startswith("if ") or stripped.startswith("if("):
        m = re.match(r"if\s*\((.+)\)\s*\{?", stripped)
        if m:
            return comment_for_if(m.group(1), ctx)
    if stripped.startswith("else if"):
        m = re.match(r"else if\s*\((.+)\)\s*\{?", stripped)
        if m:
            c = comment_for_if(m.group(1), ctx)
            return f"否则，{c}" if c else "否则分支"
    if stripped == "else {" or stripped == "else":
        return "否则走伤害计算分支"
    if stripped.startswith("return "):
        return comment_for_return(stripped, ctx)
    if stripped.startswith("for ") or stripped.startswith("for("):
        return comment_for_loop(stripped)
    if stripped.startswith("continue"):
        return "跳过当前迭代"
    if stripped.startswith("throw "):
        return "抛出业务异常"
    if "=" in stripped and not stripped.startswith("=") and stripped.endswith(";"):
        c = comment_for_assign(stripped, ctx)
        if c:
            return c
    if stripped.endswith(");") or stripped.endswith(");"):
        c = comment_for_call(stripped, ctx)
        if c:
            return c
    if stripped.startswith("this.") and "=" in stripped:
        return comment_for_assign(stripped, ctx)
    if stripped.startswith("@Override"):
        return None
    if stripped.startswith("@"):
        return None
    return None


def fix_import_comment(line: str) -> str:
    stripped = line.strip()
    # 移除独立包声明注释行
    if stripped.startswith("// 包声明："):
        return ""
    m = re.match(r"^//\s*导入(?:第三方库| JDK/Jakarta 类)：(\S+)\s*$", stripped)
    if m:
        return ""
    m = re.match(r"^(\s*import\s+([\w.]+);)\s*//\s*(.+)$", line.rstrip())
    if not m:
        return line
    prefix, imp, comment = m.group(1), m.group(2), m.group(3).strip()
    if not is_lazy(comment):
        return line
    simple = imp.rsplit(".", 1)[-1]
    hint = IMPORT_HINTS.get(simple, f"{simple} 类型")
    return f"{prefix} // {hint}\n"


def fix_package_comment(line: str) -> str:
    m = re.match(r"^(\s*package\s+([\w.]+);)\s*//\s*.+$", line.rstrip())
    if m:
        return f"{m.group(1)}\n"
    return line


def extract_method_name(line: str) -> str | None:
    m = re.search(
        r"\b(?:public|private|protected)\s+[\w<>,\s\[\]?]+\s+(\w+)\s*\(",
        line,
    )
    return m.group(1) if m else None


def process_file(path: Path) -> bool:
    text = path.read_text(encoding="utf-8", errors="replace")
    lines = text.splitlines(keepends=True)
    out: list[str] = []
    ctx: dict = {"method": "", "prev": ""}
    in_method = False
    brace_depth = 0
    changed = False

    for line in lines:
        orig = line
        stripped = line.strip()

        if stripped.startswith("import "):
            line = fix_import_comment(line)
            if line == "":
                changed = True
                continue
        if stripped.startswith("package "):
            line = fix_package_comment(line)
        if stripped.startswith("// 包声明："):
            changed = True
            continue

        mn = extract_method_name(stripped)
        if mn and ("{" in stripped or stripped.endswith(")")):
            ctx["method"] = mn
            if "// 方法定义" in line:
                line = re.sub(r"\s*//\s*方法定义\s*$", "", line.rstrip()) + "\n"

        if "//" in line:
            code, _, comment = line.partition("//")
            comment = comment.strip()
            if is_lazy(comment):
                new_c = comment_for_line(code.strip(), ctx)
                if new_c:
                    line = code.rstrip() + f" // {new_c}\n"
                else:
                    line = code.rstrip() + "\n"
                changed = True
        elif in_method and stripped and not stripped.startswith(("/", "*", "@")):
            new_c = comment_for_line(stripped, ctx)
            if new_c and stripped not in ("{", "}", "};"):
                line = line.rstrip() + f" // {new_c}\n"
                changed = True

        if "// 字段定义" in line:
            line = re.sub(r"\s*//\s*字段定义\s*$", "", line.rstrip()) + "\n"
            changed = True
        if "// Spring/框架注解" in line:
            line = re.sub(r"\s*//\s*Spring/框架注解\s*$", "", line.rstrip()) + "\n"
            changed = True
        if "// 类型定义" in line:
            line = re.sub(r"\s*//\s*类型定义\s*$", "", line.rstrip()) + "\n"
            changed = True

        if mn and "{" in stripped:
            in_method = True
            brace_depth = stripped.count("{") - stripped.count("}")
        elif in_method:
            brace_depth += line.count("{") - line.count("}")
            if brace_depth <= 0 and stripped.endswith("}"):
                in_method = False

        ctx["prev"] = stripped
        out.append(line)
        if line != orig:
            changed = True

    if changed:
        with path.open("w", encoding="utf-8", newline="\n") as f:
            f.write("".join(out))
    return changed


def iter_java_files() -> list[Path]:
    files = []
    for mod in ("mmorpg-common", "player-service", "battle-service", "activity-service", "mmorpg-gateway"):
        src = ROOT / mod / "src"
        if src.is_dir():
            files.extend(src.rglob("*.java"))
    return sorted(files)


def main() -> int:
    files = iter_java_files()
    n = 0
    for p in files:
        if process_file(p):
            n += 1
            print(p.relative_to(ROOT))
    print(f"\nRefined {n}/{len(files)} files.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
