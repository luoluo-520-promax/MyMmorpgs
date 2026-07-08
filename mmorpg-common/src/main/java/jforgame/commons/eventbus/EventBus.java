/**
 * 轻量级进程内事件总线：Spring Bean 调用 register() 后扫描 @Subscribe 方法，
 * publish() 时按事件类型 + owner 作用域匹配订阅者，并在独立线程池中异步反射回调。
 */
package jforgame.commons.eventbus;

import org.slf4j.Logger; // 记录订阅方法反射调用失败

import org.slf4j.LoggerFactory; // 创建 EventBus 专用 Logger

import java.lang.reflect.Method; // 反射调用订阅者的 @Subscribe 方法

import java.lang.reflect.Modifier; // 校验方法必须是 public

import java.util.ArrayList; // 收集一次 publish 匹配到的全部订阅者

import java.util.List; // matched 列表类型

import java.util.Map; // 事件类型 -> 订阅者列表 的注册表

import java.util.concurrent.ConcurrentHashMap; // 多线程 register/publish 并发安全

import java.util.concurrent.CopyOnWriteArrayList; // 订阅者列表读多写少，迭代时不加锁

import java.util.concurrent.ExecutorService; // 异步派发线程池

import java.util.concurrent.Executors; // 创建 CachedThreadPool

/**
 * 典型流程：RpcBeansConfiguration 创建 Bean → 各模块 register(handler) → RPC 连上后 publish(RpcConnectedEvent)。
 */
public class EventBus {

    private static final Logger log = LoggerFactory.getLogger(EventBus.class); // 派发失败时 warn，不中断其他订阅者

    /** 键=事件 Class（如 RpcConnectedEvent.class），值=该类型全部订阅者；支持父类/接口匹配 */
    private final Map<Class<?>, CopyOnWriteArrayList<Subscriber>> subs = new ConcurrentHashMap<>();
    /** 无界缓存线程池：每个 publish 可能向多个订阅者各 submit 一个任务 */
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "eventbus-worker"); // 线程名便于 jstack 区分 EventBus 工作线程
        t.setDaemon(true); // 守护线程，JVM 退出时不阻塞（游戏服/战斗服进程关闭场景）
        return t;
    });

    /**
     * 扫描 bean 上所有 public void xxx(BaseEvent子类) 且带 @Subscribe 的方法，写入 subs 注册表。
     * 同一 bean 可注册多个方法监听不同事件类型。
     */
    public void register(Object bean) {
        if (bean == null) { // 防御性判空，避免 NPE
            return; // 无订阅者可注册，直接结束
        }
        Class<?> type = bean.getClass(); // 运行时类型（可能是 CGLIB 代理子类，getMethods 仍能找到 @Subscribe）
        for (Method m : type.getMethods()) { // 遍历所有 public 方法（含继承）
            if (!m.isAnnotationPresent(Subscribe.class)) { // 无 @Subscribe 注解则不是事件回调
                continue; // 跳过普通业务方法
            }
            if (!Modifier.isPublic(m.getModifiers()) || m.getReturnType() != void.class) { // 必须 public void，否则反射调用不规范
                continue; // 签名不符合 EventBus 约定，静默忽略
            }
            Class<?>[] ps = m.getParameterTypes(); // 读取方法参数列表
            if (ps.length != 1 || !BaseEvent.class.isAssignableFrom(ps[0])) { // 必须恰好一个参数且为 BaseEvent 子类
                continue; // 例如 (String) 或 (Event, Context) 都不合法
            }
            // 以「方法参数类型」为 key 注册：监听 RpcConnectedEvent 的方法只收 RpcConnectedEvent 及其子类
            subs.computeIfAbsent(ps[0], k -> new CopyOnWriteArrayList<>()).add(new Subscriber(bean, m));
        }
    }

    /**
     * 发布事件：收集所有「注册类型可赋值自 event 实际类型」的订阅者，
     * 再按 owner 与 OwnerScopedSubscriber 过滤，最后异步 invoke 各订阅方法。
     */
    public void publish(BaseEvent event) {
        if (event == null) { // 空事件无意义
            return; // 与 register(null) 一致，静默忽略
        }
        List<Subscriber> matched = new ArrayList<>(); // 本次 publish 待派发的订阅者快照
        Class<?> ec = event.getClass(); // 事件实际类型，如 RpcConnectedEvent
        for (Map.Entry<Class<?>, CopyOnWriteArrayList<Subscriber>> e : subs.entrySet()) { // 遍历全部注册项
            if (e.getKey().isAssignableFrom(ec)) { // 注册类型是 ec 的父类/接口，或相同类型
                matched.addAll(e.getValue()); // 该 bucket 下所有订阅者都匹配此事件
            }
        }
        Object owner = event.getOwner(); // 事件携带的归属标识（如 serverId），可能为 null
        for (Subscriber s : matched) { // 逐个提交异步任务
            if (owner != null && s.bean instanceof OwnerScopedSubscriber scoped) { // 事件有 owner 且订阅者声明了作用域
                Object scopeOwner = scoped.getOwnerScope(); // 订阅者只关心哪个 owner
                if (scopeOwner != null && !scopeOwner.equals(owner)) { // owner 不一致则跳过，实现「本 server 只收本 server 事件」
                    continue; // 不投递给该订阅者
                }
            }
            executor.submit(() -> { // 异步执行，publish 调用方（如 RPC Handler）立即返回
                try { // 隔离单个订阅者异常，不影响同事件其他订阅者
                    s.method.invoke(s.bean, event); // 反射调用 @Subscribe 方法，传入 event 实例
                } catch (Exception ex) { // 包括 InvocationTargetException（订阅方法内部抛错）
                    log.warn("Event dispatch failed event={} sub={}#{} err={}",
                            ec.getSimpleName(), // 哪类事件派发失败
                            s.bean.getClass().getSimpleName(), // 哪个 Bean
                            s.method.getName(), // 哪个方法
                            ex.toString()); // 异常摘要（不打印完整堆栈避免刷屏，订阅者应自行 log）
                }
            });
        }
    }

    /** 内部记录：订阅者 Bean 实例 + 待反射调用的 Method */
    private record Subscriber(Object bean, Method method) {
    }
}
