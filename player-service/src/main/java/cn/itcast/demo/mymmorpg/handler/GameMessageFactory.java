/**
 * 文件维护说明
 * 1) 文件路径：player-service/src/main/java/cn/itcast/demo/mymmorpg/handler/GameMessageFactory.java
 * 2) 所属模块：player-service / main/java/cn/itcast/demo/mymmorpg/handler
 * 3) 主要职责：类 GameMessageFactory，承载模块内相关实现。
 * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。
 * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。
 */
package cn.itcast.demo.mymmorpg.handler; // player-service 协议 Facade 与消息 dispatch 管道
import cn.itcast.demo.mymmorpg.protocol.MessageMeta; // PayloadPacket 子类上的 cmd 元数据，与 @RequestHandler(cmd) 交叉校验
import cn.itcast.demo.mymmorpg.handler.MessageRoute; // Facade 类上的 module 编号
import cn.itcast.demo.mymmorpg.handler.RequestHandler; // Facade 方法上的 cmd 编号
import cn.itcast.demo.mymmorpg.handler.DispatchSession; // Invoker 第一个参数类型约束
import cn.itcast.demo.mymmorpg.protocol.PayloadPacket; // 请求包装基类，(byte[]) 构造反序列化 protobuf
import cn.itcast.demo.mymmorpg.protocol.Modules; // AUTH/SCENE 等模块常量，启动时 touch 防误删
import jakarta.annotation.PostConstruct; // Spring 容器就绪后扫描所有 @MessageRoute Facade 注册路由
import org.slf4j.Logger; // 每条路由注册 info 日志
import org.slf4j.LoggerFactory; // 按类名创建 SLF4J Logger
import org.springframework.context.ApplicationContext; // getBeansWithAnnotation 发现所有 Facade Bean
import org.springframework.stereotype.Component; // 单例路由表，MessageDispatchPipeline/ClientRequestTask 共用
import java.lang.reflect.Constructor; // PayloadPacket (byte[]) 构造器，绑定 MethodHandle
import java.lang.invoke.MethodHandle; // 比反射更快的 Facade 方法调用
import java.lang.invoke.MethodHandles; // unreflect 方法/构造器
import java.lang.reflect.Method; // 扫描 @RequestHandler 标注的 public 方法
import java.lang.reflect.Modifier; // 校验 handler 必须 public
import java.util.Map; // ApplicationContext 返回的 Facade Bean 映射
import java.util.concurrent.ConcurrentHashMap; // msgId -> Invoker 线程安全路由表
/**
 * 消息注册与路由中心：@MessageRoute(module) + @RequestHandler(cmd) -> signedMsgId -> MethodHandle Invoker。
 * <p>全局 msgId 计算：{@code abs(module) * 100 + abs(cmd)}，module 符号决定 msgId 正负。</p>
 */
@Component // Spring 单例，@PostConstruct 扫描 Facade 填充 routes
public class GameMessageFactory { // Netty/WebSocket 入站 msgId 路由到 Facade MethodHandle
    private static final Logger log = LoggerFactory.getLogger(GameMessageFactory.class); // 记录每条 msgId 路由注册
    /** Facade 方法调用函数式接口，MethodHandle.invoke 包装 */
    @FunctionalInterface // 函数式接口，供 MethodHandle lambda 适配
    private interface RequestExecutor { // MethodHandle 适配为 (session, packet) -> Object
        Object execute(DispatchSession session, PayloadPacket packet) throws Throwable; // GameMessageFactory 逻辑
    } // 编译单元结束

    /** PayloadPacket (byte[]) 构造函数式接口，protobuf 字节 -> 具体 CsReq 包装 */
    @FunctionalInterface // 函数式接口，供 MethodHandle lambda 适配
    private interface PacketCreator { // 构造器 MethodHandle 适配为 byte[] -> PayloadPacket
        PayloadPacket create(byte[] payload) throws Throwable; // GameMessageFactory 逻辑
    } // 编译单元结束

    /** 路由条目：msgId、Facade MethodHandle、Packet 构造 Handle、是否 BattleMessage 跨服标记 */
    public record Invoker(int msgId, RequestExecutor executor, PacketCreator packetCreator, // 单条 msgId 路由元数据
                          boolean battleMessage) { // true 时 MessageDispatchPipeline 优先 RPC 转发 FIGHT
        public Object invoke(DispatchSession session, PayloadPacket pkt) throws Exception { // ClientRequestTask 调用 Facade
            try { // 代码块开始
                return executor.execute(session, pkt); // MethodHandle 调用 AuthFacade.accountLogin 等
            } catch (Throwable t) { // Facade 抛出的受检/非受检异常
                if (t instanceof Exception e) { // 已是 Exception 直接上抛
                    throw e; // ClientRequestTask catch 记录 warn 不断开连接
                } // 块 代码块结束

                throw new IllegalStateException("处理消息失败, msgId=" + msgId, t); // Error 等包装为 IllegalStateException
            } // 编译单元结束
        } // 编译单元结束

        public PayloadPacket newPacket(byte[] payload) throws Exception { // ClientRequestTask 构造 CsReq 包装
            try { // 代码块开始
                return packetCreator.create(payload); // byte[] -> GamePackets.AccountLoginCsReq 等
            } catch (Throwable t) { // protobuf parseFrom 或构造器失败
                if (t instanceof Exception e) { // 已是 Exception 直接上抛
                    throw e; // 交由 ClientRequestTask 吞掉并 log
                } // 块 代码块结束

                throw new IllegalStateException("构造消息失败 msgId=" + msgId, t); // 包装非 Exception  Throwable
            } // 编译单元结束
        } // 编译单元结束
    } // 编译单元结束

    /** Spring 容器，启动时扫描 @MessageRoute Bean */
    private final ApplicationContext ctx; // getBeansWithAnnotation(MessageRoute.class) 发现 Facade
    /** 全局 msgId -> Invoker，ConcurrentHashMap 支持运行时只读并发查表 */
    private final ConcurrentHashMap<Integer, Invoker> routes = new ConcurrentHashMap<>(); // msgId 路由表，preHandle/get/ClientRequestTask 共用
    public GameMessageFactory(ApplicationContext ctx) { // Spring 构造注入 ApplicationContext
        this.ctx = ctx; // 持有容器供 @PostConstruct init 扫描 Facade
    } // 编译单元结束

    /** MessageDispatchPipeline.preHandle 与 ClientRequestTask 按 msgId 查路由 */
    public Invoker get(int msgId) { // IO 线程 preHandle 与业务线程 ClientRequestTask 查表
        return routes.get(msgId); // null 表示未知 msgId，preHandle 丢弃
    } // 编译单元结束

    /** 启动阶段：扫描 Facade -> 校验签名 -> 绑定 MethodHandle -> 写入 routes */
    @PostConstruct // 容器就绪后执行初始化
    public void init() { // Spring 容器就绪后执行一次路由注册
        Map<String, Object> beans = ctx.getBeansWithAnnotation(MessageRoute.class); // 所有 @MessageRoute Facade Bean
        for (Object facade : beans.values()) { // 遍历 AuthFacade/SceneFacade/BagFacade 等
            Class<?> type = facade.getClass(); // 可能是 CGLIB 代理类，getMethods 仍可见 @RequestHandler
            MessageRoute mr = type.getAnnotation(MessageRoute.class); // 读取 module 编号
            if (mr == null) { // getBeansWithAnnotation 已过滤，防御性检查
                continue; // 跳过无 @MessageRoute 的 Bean
            } // 编译单元结束

            int module = mr.module(); // 如 Modules.AUTH = 1
            if (Math.abs(module) >= 326) { // signedMsgId 乘法上限，防止 int 溢出
                throw new IllegalStateException("@MessageRoute(module) 越界: " + type.getName() + " module=" + module); // 启动失败，路由表不写入
            } // 编译单元结束

            for (Method m : type.getMethods()) { // 遍历 public 方法找 @RequestHandler
                if (!m.isAnnotationPresent(RequestHandler.class)) { // 非 handler 方法跳过
                    continue; // 如 Object.hashCode 等
                } // 编译单元结束

                RequestHandler requestHandler = m.getAnnotation(RequestHandler.class); // 读取 cmd 编号
                if (!Modifier.isPublic(m.getModifiers())) { // MethodHandles.lookup 要求 public
                    throw new IllegalStateException("@RequestHandler 方法必须 public: " + type.getName() + "#" + m.getName()); // 启动校验失败
                } // 编译单元结束

                Class<?>[] params = m.getParameterTypes(); // 须 (DispatchSession, PayloadPacket子类)
                if (params.length != 2 || params[0] != DispatchSession.class || !PayloadPacket.class.isAssignableFrom(params[1])) { // 签名约束
                    throw new IllegalStateException("@RequestHandler 签名必须为 (DispatchSession, PayloadPacket子类): " // 启动校验失败
                            + type.getName() + "#" + m.getName()); // GameMessageFactory 逻辑
                } // 编译单元结束

                @SuppressWarnings("unchecked") // @SuppressWarnings 注解
                Class<? extends PayloadPacket> pktType = (Class<? extends PayloadPacket>) params[1]; // 如 GamePackets.AccountLoginCsReq
                MessageMeta meta = pktType.getAnnotation(MessageMeta.class); // 协议 code 生成器写入的 cmd 元数据
                if (meta == null) { // PayloadPacket 子类必须带 @MessageMeta
                    throw new IllegalStateException("消息类缺少 @MessageMeta: " + pktType.getName()); // 启动校验失败
                } // 编译单元结束

                int cmd = requestHandler.cmd(); // Facade 方法上的 cmd，须与 MessageMeta 一致
                if (Math.abs(cmd) >= 100) { // 每模块最多 99 个 cmd，signedMsgId=module*100+cmd
                    throw new IllegalStateException("@RequestHandler(cmd) 越界: " + type.getName() // 启动校验失败
                            + "#" + m.getName() + " cmd=" + cmd); // GameMessageFactory 逻辑
                } // 编译单元结束

                if (meta.cmd() != cmd) { // 防止 Facade 与协议类 cmd 不一致导致路由错乱
                    throw new IllegalStateException("处理器 cmd 与消息体 @MessageMeta 不一致: " // 启动校验失败
                            + type.getName() + "#" + m.getName() // GameMessageFactory 逻辑
                            + " handlerCmd=" + cmd + " packetCmd=" + meta.cmd()); // GameMessageFactory 逻辑
                } // 编译单元结束

                int msgId = signedMsgId(module, cmd); // 如 AUTH cmd1 -> msgId 101
                Constructor<? extends PayloadPacket> ctor; // GameMessageFactory 逻辑
                try { // 代码块开始
                    ctor = pktType.getConstructor(byte[].class); // 统一 (byte[]) 构造，内部 parseFrom protobuf
                } catch (NoSuchMethodException e) { // 缺少 (byte[]) 构造器
                    throw new IllegalStateException("消息类必须提供 public (byte[]) 构造: " + pktType.getName(), e); // 启动校验失败
                } // 块 代码块结束

                boolean battle = implementsBattleMessage(pktType); // 实现 BattleMessage 接口则走 RPC 转发
                MethodHandle methodHandle; // GameMessageFactory 逻辑
                MethodHandle ctorHandle; // GameMessageFactory 逻辑
                try { // 代码块开始
                    MethodHandles.Lookup lookup = MethodHandles.lookup(); // 当前模块 lookup 权限
                    methodHandle = lookup.unreflect(m).bindTo(facade); // 预绑定 Facade 实例，invoke 更快
                    ctorHandle = lookup.unreflectConstructor(ctor); // PayloadPacket (byte[]) 构造 MethodHandle
                } catch (IllegalAccessException e) { // 非 public 或模块不可见
                    throw new IllegalStateException("创建消息调用句柄失败: " + type.getName() + "#" + m.getName(), e); // 启动校验失败
                } // 块 代码块结束

                RequestExecutor executor = (session, packet) -> methodHandle.invoke(session, packet); // lambda 包装 Facade MethodHandle
                PacketCreator packetCreator = bytes -> (PayloadPacket) ctorHandle.invoke(bytes); // lambda 包装构造器 Handle
                Invoker inv = new Invoker(msgId, executor, packetCreator, battle); // 组装路由条目
                Invoker old = routes.putIfAbsent(msgId, inv); // 同一 msgId 不允许重复注册
                if (old != null) { // 两个 Facade 注册了相同 msgId
                    throw new IllegalStateException("重复 msgId 路由: " + msgId // 启动校验失败
                            + " old=" + old // GameMessageFactory 逻辑
                            + " new=" + type.getName() + "#" + m.getName()); // GameMessageFactory 逻辑
                } // 编译单元结束

                log.info("Route registered msgId={} -> {}#{}({})", msgId, type.getSimpleName(), m.getName(), pktType.getSimpleName()); // 启动日志便于核对路由
            } // 编译单元结束
        } // 编译单元结束

        _touch(Modules.AUTH); // 编译期引用 Modules 常量，防止误删导致 module 编号漂移
        _touch(Modules.SCENE); // 编译期引用 Modules.SCENE 防误删
    } // 编译单元结束

    /** 编译期保留 Modules 引用，无运行时逻辑 */
    private static void _touch(int v) { // 仅引用 Modules 常量，无运行时副作用
        // no-op：防止 Modules 常量被 IDE 优化删除
    } // 编译单元结束

    /** ClientRequestTask 按 msgId 构造 PayloadPacket */
    public PayloadPacket newPacket(int msgId, byte[] payload) { // byte[] protobuf -> GamePackets.*CsReq
        Invoker inv = routes.get(msgId); // 查路由条目
        if (inv == null) { // 未知 msgId
            return null; // ClientRequestTask 静默 return
        } // 编译单元结束

        try { // 代码块开始
            return inv.newPacket(payload); // MethodHandle 调用 (byte[]) 构造器 parseFrom
        } catch (Exception e) { // protobuf 损坏或构造失败
            throw new IllegalStateException("构造消息失败 msgId=" + msgId, e); // 包装后上抛给 ClientRequestTask
        } // 块 代码块结束
    } // 编译单元结束

    /** module 符号决定 msgId 正负：负 module 为客户端->服务端方向等协议约定 */
    public static int signedMsgId(int module, int cmd) { // @MessageRoute + @RequestHandler 计算全局 msgId
        int abs = Math.abs(module) * 100 + Math.abs(cmd); // 如 module=1 cmd=1 -> 101
        return module < 0 ? -abs : abs; // 负 module 时 msgId 为负，协议方向约定
    } // 编译单元结束

    /** 检查 PayloadPacket 是否实现 BattleMessage 标记接口，决定是否走 BattleRpcForwarder */
    private static boolean implementsBattleMessage(Class<?> pktType) { // 扫描接口名避免硬依赖 GamePackets 内部类
        for (Class<?> itf : pktType.getInterfaces()) { // PayloadPacket 子类可能 implements BattleMessage
            if (itf.getName().equals("cn.itcast.demo.mymmorpg.protocol.GamePackets$BattleMessage")) { // 战斗类 CsReq 标记
                return true; // Invoker.battleMessage=true，MessageDispatchPipeline 优先 RPC
            } // 编译单元结束
        } // 编译单元结束

        return false; // 普通消息走本地 ClientRequestTask
    } // 编译单元结束
} // 编译单元结束
