#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""修复 mmorpg-common Java 源文件中的乱码与重复注释。"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "mmorpg-common" / "src"

REPLACEMENTS = [
    ("MQ 未启?", "MQ 未启用"),
    ("本?receiverId", "本实例 receiverId"),
    ("消息类?接收者", "消息类型 + 接收者"),
    ("忽略?receiver 的消", "忽略无 receiver 的消息"),
    ("按模?ID", "按模板 ID"),
    ("原因码?", "原因码）"),
    ("再?schedule", "再次 schedule"),
    ("任务?{", "任务由 {"),
    ("用于?Spring", "用于非 Spring"),
    ("出售等?", "出售等）"),
    ("玩?ID", "玩家 ID"),
    ("根?owner", "根据 owner"),
    ("?EventBus", "，EventBus"),
    ("?owner ?", "据 owner 与"),
    ("上下文?", "上下文。"),
    ("过滤?", "过滤。"),
    ("调用与扩展?", "调用与扩展。"),
    ("受影响?", "受影响。"),
    ("指标?", "指标。"),
    ("文档?server.type", "文档 server.type"),
    ("CENTRE (10)?", "CENTRE (10) 等"),
    ("协调?", "协调。"),
    ("轮?", "轮询。"),
    ("RANDOM ?ROUND", "RANDOM 或 ROUND"),
    ("开始战?", "开始战斗"),
    ("未处理异?", "未处理异常"),
    ("服务器内部错?", "服务器内部错误"),
    ("按模?", "按模板"),
    ("主动取消原因码?", "主动取消原因码）"),
    ("?/p>", "。</p>"),
    ("<ã?", "<"),
    ("清?MDC", "清理 MDC"),
    ("其他测", "其他测试"),
    ("回退?X-Trace-Id", "回退到 X-Trace-Id"),
    ("注册?publish", "注册后 publish"),
    ("第一?3 只", "第一波 3 只"),
    ("第二?1 ?", "第二波 1 只"),
    ("最?MonsterConfig", "最小 MonsterConfig"),
    ("注?traceId", "注入 traceId"),
    ("可使?Redis", "可使用 Redis"),
    ("集中存储）?", "集中存储）。"),
    ("服务?RSA", "服务端 RSA"),
    ("上传的?RSA", "上传的 RSA 公钥"),
    ("sessionId?", "sessionId。"),
    ("加密后?AES", "加密后的 AES"),
    ("Base64?", "Base64）。"),
    ("随?AES", "随机 AES"),
    ("生成时使用）?", "生成时使用）。"),
    ("进?AES-GCM", "进行 AES-GCM"),
    ("返?Base64?", "返回 Base64。"),
    ("密钥?AES-GCM", "密钥对 AES-GCM"),
    ("维?messageType", "维护 messageType"),
    ("作?key", "作为 key"),
    ("未找?MQ", "未找到 MQ"),
    ("发送失?sender", "发送失败 sender"),
    ("?Java RPC", "在 Java RPC"),
    ("对象?{", "对象与 {"),
    ("请求后?RPC", "请求后的 RPC"),
    ("战斗服?RPC", "战斗服的 RPC"),
    ("响应?Future?", "响应的 Future。"),
    ("战斗服?RPC 连接", "战斗服的 RPC 连接"),
    ("委?{", "委托 {"),
    ("getRpc()}?", "getRpc()}。"),
    ("监?{", "监听 {"),
    ("中心?RPC", "中心服 RPC"),
    ("游戏?战斗服", "游戏服/战斗服"),
    ("连接中心?RPC", "连接中心服 RPC"),
    ("场景?Actor", "场景 Actor"),
    ("-128 ?-1", "-128 至 -1"),
    ("业务?+）?", "业务（0+）。"),
    ("客户?服务器", "客户端/服务器"),
    ("module * 100 + cmd?", "module * 100 + cmd。"),
    ("登?选角", "登录/选角"),
    ("背?道具", "背包/道具"),
    ("客户?服务器消息", "客户端/服务器消息"),
    ("原?protobuf", "原始 protobuf"),
    ("命?ID?", "命令 ID。"),
    ("<。<。", "<"),
    ("专?YAML", "专属 YAML"),
    ("默认值）?", "默认值）。"),
    ("客户?TCP", "客户端 TCP"),
    ("不允?new", "不允许 new"),
    ("指?WebSocket", "指定 WebSocket"),
    ("再?4 字节", "再加 4 字节"),
    ("长?= 4", "长度 = 4"),
    ("最前面?4 字节", "最前面 4 字节"),
    ("最?4 字节?frameContentLength", "前 4 字节 frameContentLength"),
    ("再?payload", "再加 payload"),
    ("最?3 个）?", "最多 3 个）。"),
    ("对应?skill_config?", "对应表 skill_config。"),
    ("对应?player_skill?", "对应表 player_skill。"),
    ("0=未绑?1=绑定", "0=未绑定，1=绑定"),
    ("对应?map_config?", "对应表 map_config。"),
    ("对应?monster_config?", "对应表 monster_config。"),
    ("表?chat_message_log?", "表 chat_message_log。"),
    ("对应?buff_config?", "对应表 buff_config。"),
    ("返?null", "返回 null"),
    ("不?owner", "不受 owner"),
    ("返?null 表示", "返回 null 表示"),
    ("全局订阅）?", "全局订阅）。"),
    ("学?释放", "学习/释放"),
    ("RocketMQ）?", "RocketMQ）。"),
    ("单体模式?{", "单体模式下 {"),
    ("ActivityService}?", "ActivityService}。"),
    ("便?MQ", "便于 MQ"),
    ("命?ID +", "命令 ID +"),
    ("只?移除", "只读/移除"),
    ("消息类?命令号", "消息类型/命令号"),
    ("该消息?Protobuf", "该消息的 Protobuf"),
    ("响应?Contributor", "响应式 Contributor"),
    ("响应?Redis", "响应式 Redis"),
    ("自定?Redis", "自定义 Redis"),
    ("响应?PING", "响应式 PING"),
    ("发?PING", "发送 PING"),
    ("成功?UP", "成功则 UP"),
    ("异常?DOWN", "异常则 DOWN"),
    ("阻塞?PING", "阻塞式 PING"),
    ("借连?PING", "借连接 PING"),
    ("插入顺序?Map", "插入顺序 Map"),
    ("改?RedisPingHealthConfiguration", "改用 RedisPingHealthConfiguration"),
    ("使用 PING?", "使用 PING。"),
    ("0xx：场?", "0xx：场景"),
    ("1xx：场?", "1xx：场景"),
    ("3xx：技?", "3xx：技能"),
    ("6xx：聊?", "6xx：聊天"),
    ("7xx：背?", "7xx：背包"),
    ("8xx：活?", "8xx：活动"),
    ("retcode? 成功）?", "retcode，0 成功）。"),
    ("（?retcode", "（与 retcode"),
    ("?@Subscribe", "带 @Subscribe"),
    ("MDC ?traceId", "MDC 有 traceId"),
    ("sessionId ?AES", "sessionId -> AES"),
    ("payload ?JSON", "payload 为 JSON"),
    ("?Bean 初始化", "在 Bean 初始化"),
    ("?Redis 缓存", "带 Redis 缓存"),
    ("Java ?simpleName", "Java 类 simpleName"),
    ("body ?JSON", "body 为 JSON"),
    ("?{@link", "与 {@link"),
    ("protobuf ?{@code", "protobuf 的 {@code"),
    ("<。<。", "。"),
    ("（AES）?", "（AES）。"),
]

DUP_CLASS_JAVADOC = re.compile(
    r"/\*\*\s*\n\s*\*\s*类 \w+：封装相关业务逻辑与数据结构。\s*\n\s*\*/\s*\n",
    re.MULTILINE,
)

GENERIC_JAVADOC = re.compile(
    r"/\*\*\s*\n\s*\*\s*\n\s*\*/\s*\n",
    re.MULTILINE,
)

FIELD_INLINE_NOISE = re.compile(
    r"((?:private|protected|public)\s+(?:static\s+)?(?:final\s+)?[\w<>,\[\].\?\s]+\s+\w+\s*(?:=\s[^;]+)?;)\s*//\s*(?:执行语句|执行代码)\s*",
)


def fix_text(text: str) -> str:
    for old, new in REPLACEMENTS:
        text = text.replace(old, new)
    # 中文后紧跟 ? 且下一字符非字母数字时，删除损坏字符
    text = re.sub(r"([\u4e00-\u9fff])\?(?=[\s\*\"'，。；：）\]\}<>])", r"\1", text)
    text = re.sub(r"([\u4e00-\u9fff])\?(?=$)", r"\1", text, flags=re.MULTILINE)
    # 行首孤立的 ? 替换为“将”
    text = re.sub(r"(?<=[\u4e00-\u9fff])\?([A-Za-z{])", r"\1", text)
    text = DUP_CLASS_JAVADOC.sub("", text)
    text = GENERIC_JAVADOC.sub("", text)
    text = FIELD_INLINE_NOISE.sub(r"\1", text)
    # 修复 ;/** 合并行
    text = re.sub(r";(/\*\*[^*]+\*/)", r";\n    \1", text)
    return text


def main() -> int:
    changed = 0
    for path in sorted(SRC.rglob("*.java")):
        if "target" in path.parts:
            continue
        raw = path.read_text(encoding="utf-8", errors="replace")
        new = fix_text(raw)
        if new != raw:
            with open(path, "w", encoding="utf-8", newline="\n") as f:
                f.write(new)
            changed += 1
    print(f"fixed {changed} files")
    return 0


if __name__ == "__main__":
    sys.exit(main())
