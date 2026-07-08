#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""为 mmorpg-common 模块 Java 源文件批量添加/修复中文注释。"""
from __future__ import annotations

import re
import sys
from pathlib import Path
from typing import Optional

ROOT = Path(__file__).resolve().parent.parent
COMMON_SRC = ROOT / "mmorpg-common" / "src"

# ---------- 乱码修复映射 ----------
GARBLED_REPLACEMENTS: list[tuple[str, str]] = [
    (r"MQ 未启\?", "MQ 未启用"),
    (r"用于\?Spring 管理的核心组件\?", "用于非 Spring 管理的核心组件。"),
    (r"出售等\?", "出售等）"),
    (r"玩\?ID", "玩家 ID"),
    (r"根\?owner", "根据 owner"),
    (r"\?EventBus", "，EventBus"),
    (r"\?owner \?", "据 owner 与"),
    (r"上下文\?", "上下文。"),
    (r"过滤\?", "过滤。"),
    (r"调用与扩展\?", "调用与扩展。"),
    (r"受影响\?", "受影响。"),
    (r"指标\?", "指标。"),
    (r"\?\*/", "。"),
    (r"\? \*", "。\n *"),
    (r"\?{2,}", ""),  # 连续问号（纯乱码占位）
]

# ---------- 中文描述辅助 ----------
CN_WORDS = {
    "account": "账号", "player": "玩家", "skill": "技能", "item": "物品",
    "scene": "场景", "chat": "聊天", "battle": "战斗", "activity": "活动",
    "monster": "怪物", "map": "地图", "buff": "Buff", "gold": "金币",
    "exp": "经验", "level": "等级", "name": "名称", "id": "标识",
    "config": "配置", "message": "消息", "packet": "数据包", "rpc": "RPC",
    "session": "会话", "server": "服务器", "client": "客户端", "port": "端口",
    "repository": "仓储", "entity": "实体", "service": "服务", "handler": "处理器",
    "publisher": "发布器", "subscriber": "订阅者", "event": "事件", "bus": "总线",
    "login": "登录", "logout": "登出", "enter": "进入", "publish": "发布",
    "register": "注册", "dispatch": "派发", "encode": "编码", "decode": "解码",
    "request": "请求", "response": "响应", "future": "异步结果", "balance": "负载均衡",
    "strategy": "策略", "fight": "战斗", "center": "中心", "cross": "跨服",
    "remote": "远程", "crypto": "加密", "scheduler": "调度", "singleton": "单例",
    "factory": "工厂", "policy": "策略", "envelope": "信封", "adapter": "适配器",
    "dispatcher": "分发器", "registry": "注册表", "starter": "启动器", "filter": "过滤器",
    "exception": "异常", "error": "错误", "trace": "追踪", "redis": "Redis",
    "rocketmq": "RocketMQ", "mq": "消息队列", "log": "日志", "task": "任务",
    "wave": "波次", "ret": "返回码", "code": "码", "module": "模块", "protocol": "协议",
    "binary": "二进制", "frame": "帧", "sender": "发送器", "context": "上下文",
    "type": "类型", "node": "节点", "cluster": "集群", "connector": "连接器",
    "wire": "线格式", "sign": "签名", "util": "工具", "facade": "门面",
    "callback": "回调", "progress": "进度", "notification": "通知", "grant": "发放",
    "load": "加载", "query": "查询", "noop": "空实现", "test": "测试",
}

MODULE_CN = {
    "config": "配置", "entity": "实体", "model": "模型", "net": "网络",
    "port": "端口接口", "protocol": "协议", "repository": "数据仓储",
    "rpc": "RPC 通信", "service": "业务服务", "support": "支撑工具",
    "web": "Web 层", "eventbus": "事件总线",
}


def fix_garbled(text: str) -> str:
    for pat, repl in GARBLED_REPLACEMENTS:
        text = re.sub(pat, repl, text)
    # 修复行内 ? 紧跟中文句号场景
    text = re.sub(r"([\u4e00-\u9fff])\?(?=[\s\*\"'])", r"\1", text)
    return text


def camel_to_cn(name: str) -> str:
    parts = re.sub(r"([a-z])([A-Z])", r"\1 \2", name).lower().split()
    return "".join(CN_WORDS.get(p, p) for p in parts) or name


def infer_module_desc(rel_path: str) -> str:
    for key, cn in MODULE_CN.items():
        if f"/{key}/" in rel_path.replace("\\", "/") or f"\\{key}\\" in rel_path:
            return cn
    if "test" in rel_path:
        return "单元测试"
    return "通用"


def file_header(rel: str, class_name: str, kind: str) -> str:
    module = infer_module_desc(rel)
    return f"""/**
 * 文件说明
 * 模块：mmorpg-common / {module}
 * 路径：{rel.replace(chr(92), '/')}
 * 类型：{kind}
 * 职责：定义 {class_name}，供各业务模块复用与扩展。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */"""


def import_comment(imp: str) -> str:
    pkg = imp.replace("import ", "").replace(";", "").strip()
    if pkg.startswith("java.") or pkg.startswith("jakarta.") or pkg.startswith("javax."):
        return f"// 导入 JDK/Jakarta 类：{pkg.split('.')[-1]}"
    if pkg.startswith("org."):
        return f"// 导入第三方库：{pkg.split('.')[-1]}"
    return f"// 导入项目类：{pkg.split('.')[-1]}"


def field_comment(field_line: str) -> str:
    m = re.search(
        r"(?:private|protected|public|static|final|\s)*\s+([\w<>,\[\].\s]+?)\s+(\w+)\s*(?:=|;)",
        field_line,
    )
    if not m:
        return "字段"
    ftype, fname = m.group(1).strip(), m.group(2)
    return f"{camel_to_cn(fname)}（类型：{ftype.split('.')[-1]}）"


def method_comment(sig: str) -> str:
    m = re.search(
        r"(?:public|private|protected|static|\s)*\s*(?:[\w<>,\[\].\s]+?)\s+(\w+)\s*\(([^)]*)\)",
        sig,
    )
    if not m:
        return "执行方法逻辑"
    name, params = m.group(1), m.group(2).strip()
    if name == "get" or (name.startswith("get") and name[3:4].isupper()):
        return f"获取{camel_to_cn(name[3:])}属性值"
    if name.startswith("set") and name[3:4].isupper():
        return f"设置{camel_to_cn(name[3:])}属性值"
    if name.startswith("is") and name[2:3].isupper():
        return f"判断{camel_to_cn(name[2:])}是否为真"
    if name == "<init>" or re.match(r"^[A-Z]", name):
        return f"构造 {name} 实例"
    param_desc = "无参数" if not params else f"参数：{params[:60]}"
    return f"{camel_to_cn(name)}；{param_desc}"


def describe_code_line(line: str) -> Optional[str]:
    s = line.strip()
    if not s or s.startswith("//") or s.startswith("*") or s.startswith("/*") or s.startswith("@"):
        return None
    if s in ("{", "}", "};"):
        return None
    if s.startswith("return "):
        return "返回结果"
    if s.startswith("if "):
        return "条件分支判断"
    if s.startswith("else if"):
        return "否则若条件成立"
    if s.startswith("else"):
        return "否则分支"
    if s.startswith("for "):
        return "循环遍历"
    if s.startswith("while "):
        return "循环条件判断"
    if s.startswith("switch "):
        return "多分支选择"
    if s.startswith("case "):
        return "分支 case"
    if s.startswith("default:"):
        return "默认分支"
    if s.startswith("try"):
        return "异常捕获开始"
    if s.startswith("catch"):
        return "捕获异常并处理"
    if s.startswith("finally"):
        return "最终清理逻辑"
    if s.startswith("throw "):
        return "抛出异常"
    if s.startswith("break"):
        return "跳出循环或分支"
    if s.startswith("continue"):
        return "跳过本次循环"
    if s.startswith("synchronized"):
        return "同步块加锁"
    if ".add(" in s or ".addAll(" in s:
        return "向集合添加元素"
    if ".put(" in s:
        return "向映射写入键值"
    if ".get(" in s and not s.startswith("return"):
        return "读取映射或对象属性"
    if ".remove(" in s:
        return "移除元素"
    if ".computeIfAbsent(" in s:
        return "键不存在时计算并放入"
    if ".invoke(" in s:
        return "反射调用方法"
    if ".submit(" in s:
        return "提交异步任务"
    if ".warn(" in s or ".error(" in s or ".debug(" in s or ".info(" in s:
        return "记录日志"
    if s.startswith("this."):
        return "访问或赋值当前实例字段"
    if re.match(r"^\w+\s*=\s*", s):
        return "局部变量赋值"
    if s.endswith(";") and "(" in s:
        return "调用方法或执行语句"
    if s.endswith(";"):
        return "执行语句"
    return "执行代码"


def has_file_header(text: str) -> bool:
    return bool(re.search(r"/\*\*\s*\n\s*\*\s*文件说明", text))


def strip_old_broken_header(text: str) -> str:
    return re.sub(
        r"/\*\*\s*\n(?:\s*\*[^\n]*\n)*?\s*\*[^\n]*(?:文件维护|文件说明)[^\n]*\n(?:\s*\*[^\n]*\n)*?\s*\*/\s*\n",
        "",
        text,
        count=1,
    )


def is_javadoc_start(line: str) -> bool:
    return line.strip().startswith("/**")


def is_inside_block_comment(lines: list[str], idx: int) -> bool:
    depth = 0
    for i in range(idx):
        ln = lines[i]
        if "/*" in ln:
            depth += ln.count("/*")
        if "*/" in ln:
            depth -= ln.count("*/")
    return depth > 0


def get_indent(line: str) -> str:
    return re.match(r"^(\s*)", line).group(1)


def already_has_eol_comment(line: str) -> bool:
    # 忽略字符串内的 //
    in_str = False
    quote = None
    for i, ch in enumerate(line):
        if ch in ('"', "'") and (i == 0 or line[i - 1] != "\\"):
            if not in_str:
                in_str, quote = True, ch
            elif ch == quote:
                in_str, quote = False, None
        if not in_str and ch == "/" and i + 1 < len(line) and line[i + 1] == "/":
            return True
    return False


def brace_depth_before(lines: list[str], idx: int) -> int:
    depth = 0
    for i in range(idx):
        depth += lines[i].count("{") - lines[i].count("}")
    return depth


def is_method_signature(line: str) -> bool:
    s = line.strip()
    if not s or s.startswith("@") or s.startswith("//"):
        return False
    if "(" not in s:
        return False
    if re.search(r"\b(class|interface|enum|record|@interface)\b", s):
        return False
    return bool(re.search(r"\b(public|private|protected|static|\w+\s+\w+\s*\()", s))


def is_type_declaration(line: str) -> bool:
    return bool(re.search(r"\b(class|interface|enum|record|@interface)\s+\w+", line.strip()))


def is_field_declaration(line: str) -> bool:
    s = line.strip()
    if not s or s.startswith("@") or "(" in s:
        return False
    return bool(re.search(r"\b(private|protected|public)\s+[\w<>,\[\].\s]+\s+\w+\s*(=|;)", s))


def annotate_file(path: Path) -> tuple[str, int]:
    raw = path.read_text(encoding="utf-8", errors="replace")
    text = fix_garbled(raw)
    text = strip_old_broken_header(text)
    rel = str(path.relative_to(ROOT / "mmorpg-common")).replace("\\", "/")

    lines = text.split("\n")
    out: list[str] = []
    i = 0
    class_name = path.stem
    kind = "类"
    package_done = False
    header_added = False
    pending_sig: list[str] = []

    # 预扫描类名与类型
    for ln in lines:
        m = re.search(r"\b(class|interface|enum|record|@interface)\s+(\w+)", ln)
        if m:
            kind = {"class": "类", "interface": "接口", "enum": "枚举", "record": "记录类", "@interface": "注解"}.get(
                m.group(1), "类型"
            )
            class_name = m.group(2)
            break

    if not has_file_header(text):
        out.append(file_header(rel, class_name, kind))
        header_added = True

    while i < len(lines):
        line = lines[i]
        stripped = line.strip()

        # 跳过已有文件头
        if not header_added and is_javadoc_start(stripped) and i < 8:
            block = [line]
            i += 1
            while i < len(lines):
                block.append(lines[i])
                if "*/" in lines[i]:
                    i += 1
                    break
                i += 1
            fixed_block = fix_garbled("\n".join(block))
            out.extend(fixed_block.split("\n"))
            header_added = True
            continue

        # package
        if stripped.startswith("package ") and not package_done:
            if not (out and out[-1].strip().startswith("// 包声明")):
                out.append(f"// 包声明：{stripped.replace('package ', '').replace(';', '')}")
            out.append(line)
            package_done = True
            i += 1
            continue

        # import
        if stripped.startswith("import ") and not stripped.startswith("import static"):
            if not already_has_eol_comment(line):
                out.append(import_comment(stripped))
            out.append(line)
            i += 1
            continue

        # 块注释修复乱码后原样保留
        if is_inside_block_comment(lines, i) or stripped.startswith("/**") or stripped.startswith("*") or stripped.startswith("/*"):
            out.append(fix_garbled(line))
            i += 1
            continue

        # 类型声明
        if is_type_declaration(line) and not already_has_eol_comment(line):
            prev_nonempty = next((out[j] for j in range(len(out) - 1, -1, -1) if out[j].strip()), "")
            if not prev_nonempty.strip().startswith("/**"):
                indent = get_indent(line)
                out.append(f"{indent}/**")
                out.append(f"{indent} * {kind} {class_name}：封装相关业务逻辑与数据结构。")
                out.append(f"{indent} */")

        # 字段
        if is_field_declaration(line):
            prev_nonempty = next((out[j] for j in range(len(out) - 1, -1, -1) if out[j].strip()), "")
            if not prev_nonempty.strip().startswith("/**") and not prev_nonempty.strip().startswith("//"):
                indent = get_indent(line)
                out.append(f"{indent}/** {field_comment(line)} */")

        # 方法签名（可能多行）
        if is_method_signature(line) or pending_sig:
            if is_method_signature(line):
                pending_sig = [line.rstrip()]
            else:
                pending_sig.append(line.rstrip())
            # 等待签名结束
            sig_text = " ".join(pending_sig)
            if "(" in sig_text and ")" in sig_text and (sig_text.rstrip().endswith("{") or sig_text.rstrip().endswith(";") or sig_text.rstrip().endswith("throws")):
                prev_nonempty = next((out[j] for j in range(len(out) - 1, -1, -1) if out[j].strip()), "")
                if not prev_nonempty.strip().startswith("/**"):
                    indent = get_indent(pending_sig[0])
                    mc = method_comment(sig_text)
                    out.append(f"{indent}/**")
                    out.append(f"{indent} * {mc}")
                    out.append(f"{indent} */")
                for pl in pending_sig:
                    out.append(pl)
                pending_sig = []
                i += 1
                continue
            elif "(" in sig_text and ")" not in sig_text:
                i += 1
                continue
            else:
                pending_sig = []

        # 方法体内行注释
        depth = brace_depth_before(lines, i)
        if depth >= 1 and stripped and not stripped.startswith("@") and not is_type_declaration(line):
            desc = describe_code_line(line)
            if desc and not already_has_eol_comment(line):
                trimmed = line.rstrip()
                if trimmed.endswith("{"):
                    out.append(f"{trimmed} // {desc}")
                else:
                    out.append(f"{trimmed}  // {desc}")
                i += 1
                continue

        out.append(fix_garbled(line))
        i += 1

    result = "\n".join(out)
    if not result.endswith("\n"):
        result += "\n"
    line_count = len(result.splitlines())
    return result, line_count


def main() -> int:
    if not COMMON_SRC.exists():
        print("mmorpg-common/src 不存在", file=sys.stderr)
        return 1

    processed: list[tuple[str, int]] = []
    for sub in ("main/java", "test/java"):
        base = COMMON_SRC / sub.replace("/", "\\").replace("\\", "/")
        if not base.exists():
            continue
        for path in sorted(base.rglob("*.java")):
            if "target" in path.parts:
                continue
            new_text, lines = annotate_file(path)
            with open(path, "w", encoding="utf-8", newline="\n") as f:
                f.write(new_text)
            rel = str(path.relative_to(ROOT / "mmorpg-common")).replace("\\", "/")
            processed.append((rel, lines))

    total_lines = sum(n for _, n in processed)
    print(f"已处理 {len(processed)} 个文件，总行数 {total_lines}")
    print("---")
    for rel, n in processed:
        print(f"{rel}\t{n}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
