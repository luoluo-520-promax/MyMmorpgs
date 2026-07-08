#!/usr/bin/env python3
"""Replace generic placeholder comments with domain-specific comments in player-service handler/net/config."""
import re
from pathlib import Path
from typing import Optional, Tuple

ROOT = Path(r"c:\Users\ASUS\IdeaProjects\test\MyMmorpg\player-service\src\main\java\cn\itcast\demo\mymmorpg")

DIRS = ["handler", "net", "config"]

BAD = re.compile(
    r"//\s*(创建新对象/集合承载本次请求中间结果|注入 yml 或环境变量配置|列表集合|返回值|条件判断|"
    r"执行本行语句推进业务流程|更新局部变量，驱动后续业务步骤|条件不满足时进入失败处理分支|"
    r"结束当前方法并返回结果给调用方|构造器/方法调用参数列表结束|方法体开始|"
    r"类型定义：本编译单元的主入口|遍历集合驱动批量处理|跳过当前元素，处理下一项|"
    r"校验通过，允许后续流程|校验失败，调用方应短路或拒绝|无可用结果，上层按 null 处理|"
    r"提前结束当前分支，不继续执行后续逻辑|返回数值型默认值给协议或算法|"
    r"从 payload 解码 Protobuf 请求体|从内存态 Map 读取玩家/分线/实体引用|"
    r"将 Facade/Service 组装的 ProtocolMessage 交回 dispatch 层编码|"
    r"构造器注入字段 \w+ 赋值|框架/持久化注解，改变 Bean 或字段行为|"
    r"Spring 组件，参与组件扫描|注册 Spring Bean，供容器注入或启动扫描|"
    r"标记 Facade 所属协议 module|Bean 初始化完成后注册路由/预热|"
    r"加入响应技能列表|抑制编译器/IDE 已知误报警告)$"
)

VALUE_COMMENTS = {
    "game.dispatch.rpc-timeout-ms": "game.dispatch.rpc-timeout-ms，FIGHT RPC 同步等待上限毫秒",
    "game.idempotency.enabled": "game.idempotency.enabled，false 时跳过幂等 replay/record",
    "game.idempotency.window-ms": "game.idempotency.window-ms，重复包去重窗口毫秒",
    "game.idempotency.max-entries": "game.idempotency.max-entries，幂等缓存条目上限",
    "game.battle.route-to-fight": "game.battle.route-to-fight，true 时 BattleMessage 经 RPC 转发 FIGHT",
    "game.dispatch.stripes": "game.dispatch.stripes，业务分片数，0 表示按 CPU 核数",
    "game.ws.idle-timeout-ms": "game.ws.idle-timeout-ms，WebSocket 无业务帧超时毫秒",
    "game.netty.host": "game.netty.host，Netty bind 地址",
    "game.netty.port": "game.netty.port，ServerConfig 无效时的备用 TCP 端口",
    "game.netty.read-idle-seconds": "game.netty.read-idle-seconds，读空闲秒数触发 IdleClose",
}

NEW_COMMENTS = {
    "new Account()": "dev 种子：联调账号 testuser/123456 实体",
    "new Player()": None,  # context-dependent, handled below
    "new MapConfig()": "dev 种子：星穹月台 map 4096x4096 默认 2 分线",
    "new MonsterConfig()": None,
    "new SkillConfig()": None,
    "new BuffConfig()": None,
    "new PlayerSkill()": "dev 种子：首个角色预置已学技能行",
    "new ItemConfig()": None,
    "new PlayerBagItem()": None,
    "new Activity()": None,
    "new CRC32()": "CRC32 计算 payload 指纹，组成幂等键第三段",
    "new ProtocolMessage(": "封装 FIGHT RPC 回包为出站 ProtocolMessage",
    "new RpcForwardClientMessage(": "GAME->FIGHT 转发包：rid+playerId+msgId+payload",
    "new Invoker(": "注册 msgId 路由条目：MethodHandle+PacketCreator+battle 标记",
    "new AtomicInteger()": "dispatch 线程命名自增序号",
    "new Thread(r)": "创建 dispatch stripe 工作线程",
    "new Thread(EmbeddedRedisDevSupport::stopQuietly": "JVM 退出 hook，stop embedded Redis 释放 6379",
    "new ServerBootstrap()": "Netty ServerBootstrap，配置 boss/worker 与 childHandler pipeline",
    "new YamlPropertySourceLoader()": "Spring Boot YAML 解析器，加载 config/*.yml",
    "new HashMap<>()": None,
    "new HashMap<String, Object>()": "临时 Map，写入 server.port 覆盖 Spring Boot 端口",
    "new Socket()": None,
    "new byte[64]": "RESP PING 响应读缓冲，最多 64 字节",
    "new byte[msg.readableBytes()]": "拷贝 Netty 帧剩余字节为 protobuf payload",
    "new byte[length - 4]": "WebSocket 帧 protobuf 区，length 含 msgId 4 字节",
    "new byte[0]": "空 payload 合法，如心跳类无 body 请求",
    "new WsDispatchSession.WsState()": "懒创建 WebSocket 会话状态 accountId/playerId/lastSeenMs",
    "new WsDispatchSession(": "包装 WebSocketSession 为统一 DispatchSession",
    "new NettyDispatchSession(": "包装 ChannelHandlerContext 为统一 DispatchSession",
    "new GameMessage(": "Netty 出站帧 msgId+payload，经 GameMessageEncoder 加长度前缀",
    "new ClientRequestTask(": "FIGHT RPC 失败或普通消息：业务线程执行 Facade",
    "new MapPropertySource(": "最高优先级 PropertySource，http.port 映射 server.port",
    "new Entry(System.currentTimeMillis()": "幂等缓存条目：写入时间与上次成功回包",
    "new IdleStateHandler(": "Netty pipeline 读空闲检测，触发 NettyIdleCloseHandler",
    "new NettyIdleCloseHandler()": "READER_IDLE 时 close Channel，回收半开连接",
    "new LengthFieldBasedFrameDecoder(": "按前 4 字节 length 拆粘包，max 1MB",
    "new GameMessageDecoder()": "pipeline 解码：ByteBuf -> GameMessage(msgId,payload)",
    "new GameMessageEncoder()": "pipeline 出站编码：GameMessage -> [length][msgId][payload]",
    "new ChannelInitializer<SocketChannel>()": "每连接初始化 Netty pipeline Handler 链",
    "new NioEventLoopGroup(1)": "boss 线程组，单线程 accept",
    "new NioEventLoopGroup()": "worker 线程组，处理已连接 Channel IO",
    "new FileSystemResourceLoaderFactory()": "优先从磁盘 config/ 加载 YAML",
    "new ClassPathResourceLoaderFactory()": "jar classpath config/ YAML 回退",
    "new PlayerSkillId(": "player_skill 复合主键 playerId+skillId",
}

SETTER_COMMENTS = {
    'setAccountName("testuser")': "联调账号名，与 Postman/接口文档一致",
    'setPassword(passwordEncoder.encode("123456"))': "BCrypt 哈希密码 123456，非明文入库",
    'setName("星穹列车员")': "dev 主角色名，Lv35 VIP",
    'setLevel(35)': "高等级便于测功能解锁与战斗",
    'setVipRight(1)': "VIP 标记，FunctionService 可扩展校验",
    'setName("无名侠")': "dev 副角色名，同账号多角色",
    'setLevel(10)': "低等级副角色，测等级门槛",
    'setVipRight(0)': "非 VIP 副角色",
    'setName("星穹月台")': "dev 示例地图名",
    'setWidth(4096)': "地图宽 4096，SceneActor AOI 边界",
    'setHeight(4096)': "地图高 4096",
    'setDefaultLines(2)': "默认 2 条分线，SwitchLine 可选",
    'setName("流浪者")': "dev 怪物模板 id=1，Lv10",
    'setModelId(1001)': "客户端模型 id 1001",
    'setDescription("示例怪物")': "怪物描述文案",
    'setName("盗贼")': "dev 怪物模板 id=2，Lv12",
    'setModelId(1002)': "客户端模型 id 1002",
    'setLevel(12)': "盗贼等级 12",
    'setName("飞龙探云手")': "dev 技能1：偷取类主动技能",
    'setEffect("偷取敌人东西或金币")': "技能效果描述",
    'setNeedLevel(1)': "1 级可学",
    'setCooldown(30)': "冷却 30 秒",
    'setManaCost(20)': "消耗 20 法力",
    'setCastTime(new BigDecimal("1.50"))': "施法前摇 1.5 秒",
    'setSkillType(1)': "主动技能",
    'setTargetType(1)': "单体目标",
    'setRange(300)': "施法距离 300",
    'setShape(1)': "圆形范围",
    'setShapeParams("{\\"radius\\":200}")': "圆形半径 200 JSON",
    'setName("逍遥神剑")': "dev 技能2：全体攻击 Lv20",
    'setEffect("李逍遥自创的绝技，攻击敌方全体")': "全体攻击效果描述",
    'setNeedLevel(20)': "20 级可学",
    'setCooldown(60)': "冷却 60 秒",
    'setManaCost(50)': "消耗 50 法力",
    'setCastTime(new BigDecimal("2.00"))': "施法前摇 2 秒",
    'setShape(2)': "扇形范围",
    'setShapeParams("{\\"radius\\":300,\\"angle\\":90}")': "扇形半径300角度90 JSON",
    'setName("泰山压顶")': "dev 技能3：土系 Lv30 法术",
    'setEffect("土系高级法术")': "土系法术效果描述",
    'setNeedLevel(30)': "30 级可学",
    'setCooldown(45)': "冷却 45 秒",
    'setManaCost(80)': "消耗 80 法力",
    'setCastTime(new BigDecimal("3.00"))': "施法前摇 3 秒",
    'setShapeParams("{\\"radius\\":250}")': "圆形半径 250 JSON",
    'setName("力量祝福")': "dev Buff1：+100 攻击 300s",
    'setDuration(300_000)': "持续 300 秒（毫秒）",
    'setPeriodicInterval(null)': "非周期 buff，无 tick",
    'setStackLimit(5)': "最多叠 5 层",
    'setEffectType(1)': "属性加成类 buff",
    'setEffectParams("{\\"attack\\":100}")': "+100 攻击力 JSON",
    'setDescription("提升 100 点攻击力")': "Buff 描述文案",
    'setName("中毒")': "dev Buff2：周期伤害 10s",
    'setDuration(10_000)': "持续 10 秒",
    'setPeriodicInterval(2000)': "每 2s tick 一次伤害",
    'setEffectType(2)': "周期伤害类 buff",
    'setEffectParams("{\\"damage_per_tick\\":50}")': "每 tick 50 伤害 JSON",
    'setDescription("每2秒受到50点伤害")': "中毒描述文案",
    'setName("急如风")': "dev Buff3：攻速 +30% 15s",
    'setDuration(15_000)': "持续 15 秒",
    'setStackLimit(1)': "不可叠加",
    'setEffectParams("{\\"attack_speed\\":30}")': "攻速 +30% JSON",
    'setDescription("攻击速度提升 30%")': "急如风描述文案",
    'setName("经验药水")': "dev 道具1：消耗品 +1000 exp",
    'setKind(1)': "消耗品 kind=1",
    'setStackLimit(99)': "堆叠上限 99",
    'setLevelRequired(1)': "1 级可用",
    'setDescription("使用后获得1000点经验")': "经验药水描述",
    'setPrice(10)': "商店价 10",
    'setSellPrice(1)': "出售价 1",
    'setEffectParams("{\\"exp\\":1000}")': "ItemPolicy 解析 +1000 经验",
    'setName("铁剑")': "dev 道具2：装备 Lv10",
    'setKind(2)': "装备 kind=2",
    'setStackLimit(1)': "装备不可堆叠",
    'setLevelRequired(10)': "10 级可装备",
    'setDescription("一把普通的铁剑")': "铁剑描述",
    'setPrice(100)': "商店价 100",
    'setSellPrice(50)': "出售价 50",
    'setEffectParams(null)': "装备无 effect_params",
    'setType(1)': "首充活动 type=1",
    'setOpened(true)': "活动开关开启",
    'setType(2)': "签到活动 type=2",
    'setCount(5)': "槽位数量 5",
    'setBind(0)': "可交易",
    'setSlotIndex(0)': "背包槽位 0",
    'setCount(1)': "数量 1",
    'setBind(1)': "绑定不可交易",
    'setSlotIndex(1)': "背包槽位 1",
    'setLearnTime(now)': "学习时间 UTC 当前时刻",
}


def strip_comment(line: str) -> Tuple[str, Optional[str]]:
    if "//" not in line:
        return line.rstrip(), None
    # preserve URLs in strings - simple: find last //
    idx = line.rfind("//")
    # skip if inside string (rough heuristic)
    before = line[:idx]
    if before.count('"') % 2 == 1:
        return line.rstrip(), None
    return before.rstrip(), line[idx + 2 :].strip()


def comment_for(code: str, filename: str) -> Optional[str]:
    c = code.strip()
    if not c or c.startswith("/*") or c.startswith("*") or c.startswith("//") or c.startswith("/**"):
        return None
    if c.startswith("package ") or c.startswith("import "):
        if "java.util.List" in c and "列表集合" in (c):
            return "Netty MessageToMessageDecoder 解码结果列表"
        return None
    if c.startswith("@") and "{" not in c:
        return None

    for key, val in VALUE_COMMENTS.items():
        if key in c and "@Value" in c:
            return val

    for key, val in NEW_COMMENTS.items():
        if key in c and val:
            return val

    for key, val in SETTER_COMMENTS.items():
        if key.replace('\\"', '"') in c or key in c:
            return val

    # Variable-specific new X()
    if "Player p1 = new Player()" in c:
        return "dev 种子：主角色星穹列车员 Lv35 VIP"
    if "Player p2 = new Player()" in c:
        return "dev 种子：副角色无名侠 Lv10 同账号"
    if "MonsterConfig m1 = new MonsterConfig()" in c:
        return "dev 种子：怪物流浪者 Lv10 model 1001"
    if "MonsterConfig m2 = new MonsterConfig()" in c:
        return "dev 种子：怪物盗贼 Lv12 model 1002"
    if "SkillConfig s1 = new SkillConfig()" in c:
        return "dev 种子：技能1 飞龙探云手 Lv1 单体"
    if "SkillConfig s2 = new SkillConfig()" in c:
        return "dev 种子：技能2 逍遥神剑 Lv20 扇形"
    if "SkillConfig s3 = new SkillConfig()" in c:
        return "dev 种子：技能3 泰山压顶 Lv30 圆形"
    if "BuffConfig b1 = new BuffConfig()" in c:
        return "dev 种子：Buff 力量祝福 +100 攻击"
    if "BuffConfig b2 = new BuffConfig()" in c:
        return "dev 种子：Buff 中毒周期伤害"
    if "BuffConfig b3 = new BuffConfig()" in c:
        return "dev 种子：Buff 急如风攻速加成"
    if "ItemConfig expPot = new ItemConfig()" in c:
        return "dev 种子：道具经验药水 +1000 exp"
    if "ItemConfig sword = new ItemConfig()" in c:
        return "dev 种子：道具铁剑 Lv10 装备"
    if "PlayerBagItem b1 = new PlayerBagItem()" in c:
        return "dev 种子：槽0 放 5 瓶经验药水"
    if "PlayerBagItem b2 = new PlayerBagItem()" in c:
        return "dev 种子：槽1 放 1 把绑定铁剑"
    if "Activity firstRecharge = new Activity()" in c:
        return "dev 种子：首充大礼包活动 JSON"
    if "Activity signIn = new Activity()" in c:
        return "dev 种子：夏日签到活动 JSON"
    if "try (Socket socket = new Socket())" in c and "isPortOpen" in filename:
        return "短连接探测 host:port 是否可达"
    if "try (Socket socket = new Socket())" in c:
        return "TCP 连接后发 RESP PING 验证 Redis 协议"

    if c.startswith("this."):
        field = c.split("=")[0].replace("this.", "").strip()
        return f"构造器注入 {field}"

    if "LoggerFactory.getLogger" in c:
        return "SLF4J Logger，记录本类 Netty/WebSocket/dispatch 日志"

    if c.startswith("private final ") or c.startswith("private static final "):
        if "Logger" in c:
            return "SLF4J Logger 实例"
        if "Repository" in c:
            return "JPA 仓储，dev 种子写入与 count 判空"
        if "PasswordEncoder" in c:
            return "BCrypt 编码器，哈希 testuser 密码"
        if "ExecutorService[]" in c:
            return "分片单线程池数组，同 playerId 串行"
        if "ConcurrentHashMap" in c:
            if "routes" in c:
                return "msgId -> Invoker 路由表，启动扫描 Facade 注册"
            if "cache" in c:
                return "幂等键 -> Entry，窗口内 replay 缓存"
            if "sessions" in c:
                return "WebSocket sessionId -> WsState 并发映射"
            if "sessionRefs" in c:
                return "sessionId -> live WebSocketSession，IdleReaper 关闭用"
        if "YamlPropertySourceLoader" in c:
            return "Spring Boot YAML 解析器"
        if "ResourceLoaderFactory" in c:
            return "YAML 资源加载工厂"
        if "List<String> BLOCKED" in c:
            return "聊天敏感词静态表"
        return "依赖注入字段"

    if c.startswith("public ") and "(" in c and ") {" in c:
        if "Facade" in c:
            return "msgId 路由 Facade，GameMessageFactory 扫描注册"
        if "Dispatcher" in c or "Pipeline" in c:
            return "WebSocket/Netty 共用消息分发管道"
        if "Forwarder" in c:
            return "BattleMessage 跨服 RPC 转发 FIGHT"
        if "Idempotency" in c:
            return "msgId+payload CRC 幂等去重服务"
        if "ThreadModel" in c:
            return "按 playerId 分片业务线程模型"
        if "Handler" in c:
            return "WebSocket 二进制帧入口 Handler"
        if "Configuration" in c:
            return "Spring @Configuration 策略/缓存装配"
        if "Loader" in c:
            return "dev 环境示例数据 CommandLineRunner"
        if "Support" in c:
            return "embedded Redis 本地开发支持"
        if "Server" in c:
            return "Netty TCP 游戏端口 BaseServer"
        if "Reaper" in c:
            return "WebSocket 空闲连接回收"
        if "Decoder" in c:
            return "Netty pipeline 入站 msgId+payload 解码"
        if "Encoder" in c:
            return "Netty pipeline 出站帧编码"
        if "PostProcessor" in c:
            return "Spring 启动前加载游戏 YAML 配置"
        return "类定义"

    if ") {" in c or c.endswith(") {") or c.startswith("public void run") or c.startswith("protected void"):
        if "run(" in c:
            return "容器就绪后按表 count=0 写入 dev 种子"
        if "preHandle" in c:
            return "IO 线程校验 msgId 是否已注册"
        if "dispatch" in c:
            return "提交业务线程或 RPC 转发 FIGHT"
        if "channelRead0" in c:
            return "Netty 入站 GameMessage 转 DispatchSession 分发"
        if "decode" in c:
            return "ByteBuf 读 msgId+payload 写入 out 列表"
        if "encode" in c:
            return "GameMessage 编码为带长度前缀 ByteBuf"
        if "initChannel" in c:
            return "装配 Netty pipeline：Idle->拆包->解码->分发->编码"
        if "start()" in c:
            return "PostConstruct 启动 Netty bind 监听"
        if "stop()" in c:
            return "PreDestroy 关闭 ServerChannel 与 EventLoopGroup"
        if "shutdown" in c:
            return "优雅关闭 dispatch stripe 线程池"
        if "init()" in c:
            return "PostConstruct 扫描 @MessageRoute Facade 注册 msgId 路由"
        if "postProcessEnvironment" in c:
            return "加载 common.yml + 按 server.type 加载专属 YAML"
        return "方法入口"

    if c.startswith("if "):
        if "count() == 0" in c:
            return "表为空才写入 dev 种子，避免重复插入"
        if "count() == 0 &&" in c:
            return "表空且已有角色时才写关联 dev 数据"
        if "get(msgId) == null" in c or "inv == null" in c:
            return "未注册 msgId，丢弃未知包"
        if "enabled" in c and "windowMs" in c:
            return "幂等功能关闭或窗口为 0"
        if "e == null" in c or "e.getValue()" in c:
            return "Entry 已过期超出幂等窗口"
        if "response == null" in c:
            return "无回包则不写入幂等缓存"
        if "size <= maxEntries" in c:
            return "未超容量上限，无需 sweep"
        if "payload == null" in c or "payload.length == 0" in c:
            return "空 payload 幂等指纹 hash=0"
        if "fight == null" in c:
            return "无可用 FIGHT 节点，pipeline 本地回退"
        if "task == null" in c:
            return "防御空 Runnable"
        if "msg == null" in c:
            return "防御空 ProtocolMessage 出站"
        if "session != null" in c or "playerPushRegistry == null" in c:
            return "未装配推送表或空响应时跳过 bind"
        if "pid == null" in c or "playerId <= 0" in c:
            return "未选角不绑定推送通道"
        if "requestMsgId ==" in c and "ENTER_SCENE" in c:
            return "进场景回包成功时 bind 推送"
        if "requestMsgId ==" in c and "SWITCH_LINE" in c:
            return "切线回包成功时刷新推送绑定"
        if "rsp.getRetcode() ==" in c or "parsed.getRetcode() ==" in c:
            return "业务 retcode OK 才更新会话/绑定"
        if "rsp.getLoading()" in c:
            return "数据仍在加载，触发异步预加载"
        if "inv.battleMessage()" in c:
            return "BattleMessage + GAME 服 + 已选角：优先 RPC 转发"
        if "resp != null" in c:
            return "FIGHT RPC 成功回包"
        if "replay != null" in c:
            return "窗口内重传包，直接 replay 跳过 Facade"
        if "pkt == null" in c:
            return "PayloadPacket 构造失败"
        if "out instanceof ProtocolMessage" in c:
            return "Facade 返回 ProtocolMessage 才编码回客户端"
        if "idempotencyService != null" in c:
            return "幂等服务可用时缓存成功回包"
        if "mr == null" in c:
            return "防御 getBeansWithAnnotation 过滤后仍为空"
        if "Math.abs(module) >= 326" in c:
            return "module 越界会导致 signedMsgId 溢出"
        if "!m.isAnnotationPresent" in c:
            return "跳过无 @RequestHandler 的方法"
        if "!Modifier.isPublic" in c:
            return "handler 必须 public 才能 unreflect"
        if "params.length != 2" in c:
            return "签名必须为 (DispatchSession, PayloadPacket子类)"
        if "meta == null" in c:
            return "协议类缺少 @MessageMeta"
        if "Math.abs(cmd) >= 100" in c:
            return "每模块 cmd 上限 99"
        if "meta.cmd() != cmd" in c:
            return "Facade cmd 须与 @MessageMeta 一致"
        if "old != null" in c:
            return "同一 msgId 不允许重复注册"
        if "inv == null" in c:
            return "msgId 未注册路由"
        if "readableBytes() < 4" in c:
            return "帧不足 4 字节无法读 msgId"
        if "readableBytes() < 8" in c:
            return "WebSocket 帧不足 length+msgId 8 字节"
        if "length < 4" in c:
            return "length 非法或帧不完整"
        if "!enabled" in c:
            return "embedded Redis 开关关闭"
        if "isRedisAvailable" in c:
            return "6379 已有 Redis 且 PING 成功则跳过 embedded"
        if "redisServer != null" in c:
            return "embedded 实例已启动，双检锁短路"
        if "!isPortOpen" in c:
            return "TCP 端口不可达"
        if "read <= 0" in c:
            return "PING 无响应，非 Redis 服务"
        if "httpPort != null" in c:
            return "YAML http.port 映射 Spring server.port"
        if "fs.exists()" in c or "cp.exists()" in c:
            return "优先外置 config，否则 classpath"
        if "common != null" in c or "specific != null" in c:
            return "YAML 文件存在则 addFirst 注入"
        if "ps != null" in c:
            return "有效 PropertySource 加入 Environment"
        if "bindFuture != null" in c:
            return "关闭已 bind 的 ServerChannel"
        if "bossGroup != null" in c or "workerGroup != null" in c:
            return "释放 Netty EventLoopGroup"
        if "accountRepository.count()" in c:
            return "已有账号则跳过 dev 种子"
        if "skillConfigRepository.existsById" in c:
            return "skill_config 已有对应 id 才写 player_skill"
        if "expCfg != null" in c or "swordCfg != null" in c:
            return "item_config 已写入才预置背包"
        if "rawContent == null" in c:
            return "空聊天消息拒绝发送"
        if "text.length() > 512" in c:
            return "超长消息截断至 512 字"
        if "text.contains(word)" in c:
            return "命中敏感词则整条丢弃"
        if "json == null" in c:
            return "无 effect_params 效果为 0"
        if "v == null" in c and "accountId" in c:
            return "登出清除 Channel 账号绑定"
        if "v == null" in c and "playerId" in c:
            return "登出或切账号清除 playerId"
        if "playerId != null && playerId > 0" in c:
            return "已选角才 unbind 推送"
        if "st != null" in c:
            return "已选角 WebSocket 断线解绑推送"
        if "now - e.getValue().tsMs > windowMs" in c:
            return "过期 Entry 删除，视为新请求"
        if "now - e.tsMs > windowMs" in c:
            return "sweep 仅删窗口外过期键"
        if "t instanceof Exception" in c:
            return "包装非 Exception 为 IllegalStateException"
        if "implementsBattleMessage" in c or "BattleMessage" in c:
            return "实现 BattleMessage 标记接口"
        return "业务条件分支"

    if c.startswith("return "):
        if "null;" in c:
            if "tryReplay" in c or "forward" in c or "newPacket" in c or "get(" in c:
                return "无命中/无节点，上层按 null 处理"
            if "filterContent" in c or "chat" in filename.lower():
                return "拒绝发送返回 null"
            return "空结果短路"
        if "false;" in c:
            return "校验失败返回 false"
        if "true;" in c:
            return "校验通过"
        if "enabled;" in c:
            return "返回跨服转发开关状态"
        if "routes.get" in c or ".get(msgId)" in c:
            return "按 msgId 查 Invoker 路由条目"
        if "out;" in c or "pm;" in c or "parsed" not in c and "ProtocolMessage" in c:
            return "ProtocolMessage 交 ClientRequestTask 编码出站"
        if "e.response;" in c:
            return "重传包 replay 上次缓存回包"
        if "marker +" in c or "marker + \":\"" in c:
            return "幂等键：marker:msgId:crc32Hash"
        if "crc.getValue()" in c:
            return "CRC32 payload 指纹"
        if "executor.execute" in c or "inv.newPacket" in c:
            return "MethodHandle 调用 Facade 或构造 PayloadPacket"
        if "module < 0" in c or "abs :" in c:
            return "负 module 时 msgId 为负，协议方向约定"
        if "Math.abs(module)" in c:
            return "signedMsgId = abs(module)*100 + abs(cmd)"
        if "sessions;" in c or "sessionStates" in c:
            return "暴露 WsState 映射供 IdleReaper 扫描"
        if "sessionRefs.get" in c:
            return "取 live WebSocketSession 执行 close"
        if "fs;" in c or "cp;" in c:
            return "返回 YAML Resource 供 exists 判断"
        if "text;" in c:
            return "过滤后正文供 ChatService 广播"
        if "matcher.find()" in c:
            return "正则提取 effect_params 整数字段"
        if "damage;" in c or "heal;" in c:
            return "默认技能伤害/治疗公式结果"
        if "ctx.channel().attr" in c:
            return "从 Channel Attribute 读 accountId/playerId"
        return "方法返回值"

    if c.startswith("continue;"):
        return "跳过当前 Facade 方法/路由项"
    if c.startswith("throw "):
        return "配置/路由错误快速失败"
    if c.startswith("break;") or c.startswith("}"):
        return None
    if c.startswith("log."):
        return "记录 dev 种子/路由注册/Netty 启停日志"
    if ".save(" in c:
        if "accountRepository" in c:
            return "持久化联调账号 testuser"
        if "playerRepository" in c:
            return "持久化 dev 角色行"
        if "mapConfigRepository" in c:
            return "持久化星穹月台 map_config"
        if "monsterConfigRepository" in c:
            return "持久化 dev 怪物模板"
        if "skillConfigRepository" in c:
            return "持久化 dev 技能静态配置"
        if "buffConfigRepository" in c:
            return "持久化 dev Buff 静态配置"
        if "playerSkillRepository" in c:
            return "持久化首个角色预置技能"
        if "itemConfigRepository" in c:
            return "持久化 dev 道具模板"
        if "playerBagItemRepository" in c:
            return "持久化首个角色示例背包"
        if "activityRepository" in c:
            return "持久化首充/签到活动"
        return "JPA save 写入 dev 种子行"
    if ".set(" in c and "ChannelAttrs" in c:
        if "ACCOUNT_ID" in c:
            return "AuthFacade 登录成功写入 Channel 账号"
        if "PLAYER_ID" in c:
            return "选角成功写入 Channel playerId 作 dispatchKey"
        return "更新 Channel Attribute 会话状态"
    if ".set(null)" in c:
        return "清除 Channel 会话绑定"
    if "session.accountId(" in c or "session.playerId(" in c:
        return "AuthFacade 更新 DispatchSession 账号/玩家绑定"
    if "state.accountId" in c or "state.playerId" in c:
        return "WebSocket WsState 读写 accountId/playerId"
    if "state.lastSeenMs" in c:
        return "刷新 WebSocket 活跃时间戳，IdleReaper 判定用"
    if "pipeline.handle(" in c:
        return "WebSocket/Netty 统一入口：preHandle+dispatch"
    if "dispatchThreadModel.submit(" in c:
        return "按 dispatchKey 提交到 stripe 单线程池"
    if "idempotencyService.tryReplay(" in c:
        return "dispatch 前查幂等缓存，重传则 replay"
    if "idempotencyService.record(" in c:
        return "Facade 成功后写入幂等缓存"
    if "battleRpcForwarder.forward(" in c:
        return "同步 RPC 转发 FIGHT 等待回包"
    if "session.afterResponse(" in c:
        return "进场景/切线成功后 bind 推送通道"
    if "session.send(" in c:
        return "Netty writeAndFlush 或 WebSocket BinaryFrame 出站"
    if "ctx.writeAndFlush(" in c:
        return "经 GameMessageEncoder pipeline 编码回客户端"
    if "BinaryFrameSender.sendWebSocket(" in c:
        return "WebSocket 出站帧格式与 Netty 对齐"
    if "playerPushRegistry.bind" in c:
        return "注册 playerId 推送通道 Netty/WebSocket"
    if "playerPushRegistry.unbind(" in c:
        return "断线/空闲解除 playerId 推送映射"
    if "factory.get(" in c:
        return "msgId 路由表查 Invoker"
    if "inv.invoke(" in c:
        return "MethodHandle 反射调用 Facade handler"
    if "factory.newPacket(" in c:
        return "byte[] 构造 PayloadPacket 子类"
    if "routes.putIfAbsent(" in c:
        return "msgId 路由注册，禁止重复"
    if "crc.update(" in c:
        return "CRC32 累加 payload 字节"
    if "cache.put(" in c:
        return "写入幂等键与成功回包 Entry"
    if "cache.remove(" in c:
        return "过期幂等 Entry 删除"
    if "it.remove()" in c:
        return "sweep 删除过期幂等缓存项"
    if "removed++" in c:
        return "sweep 计数，单次最多删 1000"
    if "Thread.currentThread().interrupt()" in c:
        return "恢复中断标志"
    if "es.shutdown()" in c:
        return "停止接收新 dispatch 任务"
    if "es.awaitTermination" in c:
        return "等待队列中消息处理完"
    if "es.shutdownNow()" in c:
        return "超时强制中断 dispatch 线程"
    if "t.setName(" in c:
        return "命名 dispatch-N-M 线程便于 jstack"
    if "t.setDaemon(true)" in c:
        return "daemon 线程不阻塞 JVM 退出"
    if "redisServer.start()" in c:
        return "启动 embedded Redis 子进程"
    if "redisServer.stop()" in c:
        return "stop embedded Redis 释放端口"
    if "socket.connect(" in c:
        return "500ms 超时 TCP 连接 host:port"
    if "socket.getOutputStream().write" in c and "PING" in c:
        return "发送 RESP PING\\r\\n 探测 Redis"
    if "socket.getOutputStream().flush()" in c:
        return "刷新 TCP 输出确保 PING 发出"
    if "socket.getInputStream().read" in c:
        return "读取 RESP 响应判断 PONG"
    if "new String(buffer" in c:
        return "解码 RESP 响应为字符串查 PONG"
    if "response.startsWith" in c:
        return "确认 Redis PING 返回 PONG"
    if "sources.addFirst(" in c:
        return "游戏 YAML 配置最高优先级注入 Environment"
    if "map.put(" in c:
        return "http.port 写入 server.port 键"
    if "YAML_LOADER.load" in c:
        return "解析 YAML 为 PropertySource 列表"
    if "shutdownQuietly()" in c:
        return "释放 boss/worker EventLoopGroup"
    if "bindFuture" in c and "bind(" in c:
        return "阻塞至 Netty bind 完成"
    if "b.group(" in c:
        return "绑定 boss/worker EventLoopGroup"
    if "b.channel(" in c:
        return "NIO ServerSocketChannel 实现"
    if "ch.pipeline().addLast(" in c:
        return "pipeline 追加 Handler"
    if "ctx.close()" in c:
        return "异常/空闲强制关闭 Channel"
    if "super.channelInactive" in c:
        return "继续 Netty 默认断连处理"
    if "buf.order(" in c:
        return "WebSocket 帧大端序与 Netty 一致"
    if "buf.getInt()" in c:
        if "length" in c:
            return "读 WebSocket 帧 length 字段"
        return "读 WebSocket 帧 msgId 路由键"
    if "buf.get(payload)" in c:
        return "读 protobuf payload 字节"
    if "sessions.computeIfAbsent" in c:
        return "懒创建 sessionId 对应 WsState"
    if "sessionRefs.put" in c or "sessionRefs.remove" in c:
        return "维护 sessionId -> WebSocketSession 映射"
    if "sessions.remove" in c:
        return "断线清理 WsState"
    if "parseFrom(" in c:
        return "protobuf 解码请求/响应体"
    if "preloadService.trigger(" in c:
        return "触发 BAG/SKILL 异步预加载"
    if "accountPlayerService." in c or "bagService." in c or "sceneActorService." in c or "skillService." in c or "chatService." in c:
        return "委托领域 Service 处理业务逻辑"
    if "passwordEncoder.encode" in c:
        return "BCrypt 哈希 dev 账号密码"
    if "account.setAccountId" in c or "setAccountId" in c:
        return "角色外键绑定 dev 账号 id"
    if "ps.setId(" in c:
        return "player_skill 复合主键 playerId+skillId"
    if "b1.setPlayerId" in c or "b2.setPlayerId" in c:
        return "背包行绑定首个 dev 角色 id"
    if "b1.setItemConfigId" in c or "b2.setItemConfigId" in c:
        return "背包槽引用 item_config id"
    if "firstRecharge.setData" in c or "signIn.setData" in c:
        return "活动档位 JSON 写入 data 字段"
    if "String.format(" in c:
        return "组装活动 rewardTiers JSON 字符串"
    if "Instant.now()" in c:
        return "player_skill.learn_time UTC 时间戳"
    if "System.currentTimeMillis()" in c:
        return "活动起止时间毫秒戳"
    if "playerRepository.findAll()" in c:
        return "取首个 dev 角色（星穹列车员）"
    if "itemConfigRepository.findAll()" in c:
        return "查 item_config 取奖励道具 id"
    if "for (int sid" in c:
        return "为首个角色预置 skill 1、2"
    if "for (String word" in c:
        return "线性扫描敏感词表"
    if "for (Object facade" in c:
        return "遍历 Spring 容器内所有 @MessageRoute Facade"
    if "for (Method m" in c:
        return "扫描 Facade public 方法找 @RequestHandler"
    if "for (ExecutorService es" in c:
        return "遍历 dispatch stripe 线程池"
    if "for (Class<?> itf" in c:
        return "检查 PayloadPacket 是否实现 BattleMessage"
    if "while (it.hasNext()" in c:
        return "sweep 迭代幂等缓存删除过期项"
    if "while (System.currentTimeMillis()" in c:
        return "轮询等待 embedded Redis PING 就绪"
    if "lookup.unreflect" in c:
        return "MethodHandle 绑定 Facade 实例方法"
    if "lookup.unreflectConstructor" in c:
        return "MethodHandle 绑定 PayloadPacket(byte[]) 构造器"
    if "RequestExecutor executor" in c:
        return "lambda 包装 MethodHandle 为 RequestExecutor"
    if "PacketCreator packetCreator" in c:
        return "lambda 包装构造器 Handle 为 PacketCreator"
    if "_touch(Modules" in c:
        return "编译期引用 Modules 常量防误删"
    if "Pattern.compile" in c:
        return "effect_params JSON 字段提取正则"
    if "pattern.matcher" in c:
        return "匹配 effect_params 中 exp/hp/mp 字段"
    if "Math.max(" in c:
        return "配置下限钳制或等级下限为 1"
    if "Math.floorMod(" in c:
        return "dispatchKey 取模选 stripe，负数安全"
    if "Executors.newSingleThreadExecutor" in c:
        return "每 stripe 单线程保证同玩家 FIFO"
    if "this.stripes = new ExecutorService[n]" in c:
        return "按分片数创建 stripe 线程池数组"
    if "int n = stripes" in c:
        return "stripes=0 时默认 CPU 核数至少 2"
    if "int port = serverConfig" in c:
        return "YAML http.port 优先，否则 game.netty.port"
    if "String host = StringUtils" in c:
        return "ServerConfig IP 优先，否则 game.netty.host"
    if "int msgId = msg.readInt()" in c:
        return "读 4 字节 msgId 作为路由键"
    if "out.add(new GameMessage" in c:
        return "解码结果放入 out 列表传给 MessageIoDispatcher"
    if "import java.util.List" in c:
        return "MessageToMessageDecoder 解码结果 out 列表类型"

    # constructor params
    if c.endswith(") {") and ("Repository" in c or "Service" in c or "pipeline" in c or "factory" in c):
        return "构造器注入依赖"

    if c.endswith(");") or c.endswith("});") or (not c.endswith("{") and not c.endswith("}")):
        # try to keep existing good comments - if we got here and line is executable, add minimal domain hint
        if any(x in c for x in ["=", "(", "++", "--", "+=", "-="]) and not c.strip().startswith("//"):
            if "dispatchKey" in c:
                return "已选角用 playerId，登录前用 marker.hashCode 分片"
            if "long pid" in c or "long aid" in c:
                return "从 DispatchSession 取 playerId/accountId 作业务主键"
            if "long rid" in c:
                return "RPC 请求序号匹配响应 Future"
            if "int msgId" in c:
                return "客户端协议 msgId 路由键"
            if "int module" in c:
                return "@MessageRoute module 编号"
            if "int cmd" in c:
                return "@RequestHandler cmd 编号"
            if "String key" in c:
                return "幂等键 marker:msgId:crc32"
            if "String marker" in c:
                return "netty:channelId 或 ws:sessionId"
            if "String bindHost" in c:
                return "embedded-redis bind 127.0.0.1 替代 0.0.0.0"
            if "String json" in c:
                return "活动档位 JSON 字符串"
            if "String text" in c:
                return "trim 后聊天正文"
            if "String host" in c and "port" in c:
                return "embedded Redis 探测 host:port"
    return None


def process_line(line: str, filename: str) -> str:
    code, existing = strip_comment(line)
    if not code.strip():
        return line.rstrip()
    # block comments and javadoc - keep as is
    if code.strip().startswith("*") or code.strip().startswith("/*"):
        return line.rstrip()
    # import lines - fix only if bad
    if code.strip().startswith("import "):
        if existing and BAD.search("// " + existing):
            new_c = comment_for(code, filename)
            if new_c:
                return f"{code} // {new_c}"
        return line.rstrip()
    # annotation-only lines without bad comment
    if existing and not BAD.search("// " + existing):
        return line.rstrip()
    if existing is None and not any(code.strip().endswith(x) for x in ["{", "}", ";"]) and "{" not in code:
        # non-executable
        if code.strip().startswith("private ") and ";" not in code:
            return line.rstrip()
    new_comment = comment_for(code, filename)
    if new_comment:
        return f"{code} // {new_comment}"
    if existing and BAD.search("// " + existing):
        # bad comment but no replacement - remove bad comment for braces
        if code.strip() in ("}", "};", "});", "})"):
            return code
        return code
    return line.rstrip()


def process_file(path: Path) -> bool:
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines()
    filename = path.name
    new_lines = [process_line(l, filename) for l in lines]
    # Fix corrupted GameMessageDecoder block comments
    if filename == "GameMessageDecoder.java":
        new_lines = fix_decoder_file(new_lines)
    new_text = "\n".join(new_lines) + ("\n" if text.endswith("\n") else "")
    if new_text != text:
        path.write_text(new_text, encoding="utf-8")
        return True
    return False


def fix_decoder_file(lines):
    result = []
    for line in lines:
        if "一问多答解码" in line or "List：解码" in line or "一帧内容" in line and "?" in line:
            continue
        if line.strip() == "// 入站解码：ByteBuf（已拆好单帧?> GameMessage":
            result.append("/**")
            result.append(" * Netty pipeline 入站解码：LengthFieldBasedFrameDecoder 已剥长度前缀后的单帧 ByteBuf。")
            result.append(" * 读出 msgId(4) + protobuf payload，组装 GameMessage 传给 MessageIoDispatcher。")
            result.append(" */")
            continue
        if "?ByteBuf 中读?msgId" in line:
            result.append("    protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) { // ByteBuf 读 msgId+payload 写入 out 列表")
            continue
        if line.strip().startswith("// ?") and "msgId" in line:
            continue
        if "} // if 结束" in line:
            result.append("        }")
            continue
        if "} // decode 结束" in line:
            result.append("    }")
            continue
        if "} // class GameMessageDecoder 结束" in line:
            result.append("}")
            continue
        if "读取 4 字节整数作为消息?" in line:
            continue
        if "剩余可读字节数作?payload" in line:
            continue
        if "把剩余字节全部读?payload" in line:
            continue
        if "把解析结果丢?out" in line:
            continue
        result.append(line)
    return result


def main():
    changed = []
    for d in DIRS:
        for path in sorted((ROOT / d).glob("*.java")):
            if process_file(path):
                changed.append(str(path.relative_to(ROOT.parent.parent.parent.parent.parent)))
    print(f"Modified {len(changed)} files:")
    for f in changed:
        print(f)


if __name__ == "__main__":
    main()
