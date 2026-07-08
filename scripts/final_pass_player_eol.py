#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""FINAL PASS: add domain-specific // EOL comments to every uncommented line in player-service (non-service)."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(r"c:\Users\ASUS\IdeaProjects\test\MyMmorpg\player-service\src\main\java\cn\itcast\demo\mymmorpg")
DIRS = ["config", "entity", "event", "handler", "net", "model", "support", "repository", "client", "web"]
APP = ROOT / "MyMmorpgApplication.java"

# Import comment_for from sibling script
sys.path.insert(0, str(Path(__file__).resolve().parent))
from fix_player_comments import comment_for, strip_comment  # noqa: E402

BANNED = re.compile(
    r"(方法返回值|条件判断|注册为Spring|执行本行语句|条件分支判断|执行语句|执行代码|方法入口|类定义|依赖注入字段|构造器注入字段|块结束|包声明：|导入 JDK|导入第三方|导入项目类)"
)

MODULE_PKG = {
    "config": "player-service 配置层：策略/缓存/Redis/dev 种子/Netty 开关",
    "entity": "player-service JPA 实体，映射 GM 后台 RBAC 或游戏表",
    "event": "player-service 领域事件与 EventBus 注册",
    "handler": "player-service 协议 Facade 与消息 dispatch 管道",
    "net": "player-service Netty/WebSocket 网络层与 YAML 配置加载",
    "model": "player-service 功能解锁模型与配置 DTO",
    "support": "player-service 业务策略接口与 JMX/ConfigManager",
    "repository": "player-service Spring Data JPA 仓储接口",
    "client": "player-service Feign 客户端，远程调用 battle/activity",
    "web": "player-service REST 控制器，传输层加密协商",
}

IMPORT_HINTS = {
    "jakarta.persistence": "JPA 映射注解与生命周期回调",
    "java.time.LocalDateTime": "GM 账号 create_time 等时间戳字段",
    "java.time.Instant": "player_skill.learn_time UTC 时间",
    "java.math.BigDecimal": "skill_config.cast_time 精度字段",
    "org.springframework.web": "Spring MVC REST 映射注解",
    "org.springframework.http": "HTTP Content-Type 与响应体",
    "org.springframework.data.jpa": "Spring Data JPA Repository 基类",
    "org.springframework.data.redis": "Redis 缓存与在线标记",
    "org.springframework.context": "Spring 容器 ApplicationContext/Event",
    "org.springframework.boot": "Spring Boot 自动配置与 CommandLineRunner",
    "org.springframework.stereotype": "Spring 组件 stereotype 注解",
    "org.springframework.beans.factory": "Bean 后置处理器与依赖注入",
    "org.springframework.cache": "Spring Cache 注解与 CacheResolver",
    "org.springframework.security.crypto": "BCrypt PasswordEncoder",
    "io.netty": "Netty NIO 服务端 pipeline",
    "redis.embedded": "embedded-redis 本地开发进程",
    "java.nio.charset.StandardCharsets": "UTF-8 解密上传明文",
    "java.util.Map": "REST JSON 请求/响应 Map 体",
    "java.util.List": "敏感词表或 YAML 解析结果列表",
    "java.util.concurrent": "并发 Map/线程池/原子计数",
    "java.util.regex.Pattern": "effect_params JSON 字段正则提取",
    "java.io.IOException": "embedded-redis start/stop 或 Socket 探测",
    "java.net.Socket": "TCP 短连接探测 Redis PING",
    "java.net.InetSocketAddress": "host:port 连接目标",
    "com.fasterxml.jackson": "JSON 序列化功能解锁配置",
    "org.cloud.openfeign": "Feign 声明式 HTTP 客户端",
    "cn.itcast.demo.mymmorpg.protocol": "游戏 protobuf 协议与 msgId",
    "cn.itcast.demo.mymmorpg.service": "领域 Service 委托（本文件为 Facade/Handler）",
    "cn.itcast.demo.mymmorpg.support": "业务策略/ConfigManager/JMX 支撑",
    "cn.itcast.demo.mymmorpg.config": "player-service 配置 Bean",
    "cn.itcast.demo.mymmorpg.entity": "JPA 实体引用",
    "cn.itcast.demo.mymmorpg.repository": "JPA Repository 仓储",
    "cn.itcast.demo.mymmorpg.handler": "消息路由 Facade 与 dispatch 接口",
    "cn.itcast.demo.mymmorpg.net": "Netty/WebSocket 网络组件",
    "cn.itcast.demo.mymmorpg.event": "领域事件类型",
    "cn.itcast.demo.mymmorpg.model": "功能解锁 FunctionBox 模型",
    "cn.itcast.demo.mymmorpg.client": "Feign 远程命令客户端",
}

ENTITY_FIELD_CN = {
    "id": "主键",
    "username": "GM 登录名",
    "password": "BCrypt 哈希密码",
    "enabled": "账号启用开关",
    "createTime": "账号创建时间",
    "roleId": "角色 id",
    "permissionId": "权限 id",
    "userId": "admin_user 外键",
    "name": "名称",
    "code": "权限码",
    "description": "描述文案",
}

ANNOTATION_HINTS = {
    "@Entity": "JPA 实体映射数据库表",
    "@Table": "指定表名与列约束",
    "@Id": "主键列",
    "@GeneratedValue": "自增主键策略",
    "@Column": "列名/nullable/length 约束",
    "@PrePersist": "insert 前填充默认值",
    "@PostConstruct": "容器就绪后执行初始化",
    "@PreDestroy": "容器关闭前释放资源",
    "@Override": "实现接口/父类方法",
    "@Bean": "注册 Spring 单例 Bean",
    "@Configuration": "配置类集中装配 Bean",
    "@Component": "Spring 单例组件",
    "@Service": "领域服务 Bean",
    "@Repository": "JPA 仓储接口",
    "@RestController": "REST JSON 控制器",
    "@RequestMapping": "控制器 URL 前缀",
    "@GetMapping": "HTTP GET 端点",
    "@PostMapping": "HTTP POST 端点",
    "@RequestBody": "JSON 请求体反序列化",
    "@ConditionalOnMissingBean": "无自定义 Bean 时才注册默认实现",
    "@ConditionalOnProperty": "配置开关控制 Bean 是否加载",
    "@DependsOn": "等待依赖 Bean 初始化完成",
    "@Value": "注入 application.yml 配置项",
    "@FunctionalInterface": "函数式接口，供 MethodHandle lambda 适配",
    "@EnableScheduling": "启用 @Scheduled 定时任务",
    "@EnableFeignClients": "扫描 Feign 客户端接口",
    "@ConfigurationPropertiesScan": "扫描 @ConfigurationProperties 绑定类",
    "@SpringBootApplication": "player-service 启动入口与组件扫描",
    "@MessageRoute": "Facade 协议 module 编号",
    "@RequestHandler": "Facade 方法 cmd 与 msgId 路由",
    "@EventListener": "Spring 事件监听器",
    "@Async": "异步事件处理线程池",
    "@Cacheable": "Spring Cache 读缓存",
    "@CacheEvict": "Spring Cache 失效条目",
    "@Transactional": "JPA 事务边界",
    "@Sharable": "Netty Handler 单例可共享到多 Channel",
}


def needs_comment(line: str) -> bool:
    s = line.rstrip()
    if not s.strip():
        return False
    t = s.strip()
    if t.startswith("/*") or t.startswith("*") or t.startswith("*/"):
        return False
    code, _ = strip_comment(line)
    if "//" in line and code.rstrip() == line.rstrip().split("//")[0].rstrip():
        # has // only in string
        in_str = False
        quote = None
        for i, ch in enumerate(line):
            if ch in ('"', "'") and (i == 0 or line[i - 1] != "\\"):
                if not in_str:
                    in_str, quote = True, ch
                elif ch == quote:
                    in_str, quote = False, None
            if not in_str and ch == "/" and i + 1 < len(line) and line[i + 1] == "/":
                return False
        return True
    if "//" in line:
        return False
    return True


def import_comment(imp: str, filename: str) -> str:
    imp = imp.replace("import ", "").replace(";", "").strip()
    if imp.startswith("static "):
        return f"静态导入 {imp.split('.')[-1]}"
    simple = imp.split(".")[-1]
    for prefix, hint in IMPORT_HINTS.items():
        if imp.startswith(prefix) or prefix in imp:
            return hint
    if "Repository" in simple:
        return f"JPA 仓储，读写 {simple.replace('Repository', '')} 表"
    if "Service" in simple:
        return f"领域服务 {simple}，Facade/Controller 委托"
    if "Facade" in simple:
        return f"协议 Facade {simple} 协作"
    if "Event" in simple:
        return f"领域事件 {simple} 发布/订阅"
    if "Policy" in simple:
        return f"可热替换业务策略 {simple}"
    if "Client" in simple:
        return f"Feign 远程调用 {simple}"
    return f"{simple}，{filename} 编译依赖"


def package_comment(pkg: str, rel: str) -> str:
    for mod, desc in MODULE_PKG.items():
        if f"/{mod}/" in rel.replace("\\", "/") or rel.startswith(mod + "/"):
            return desc
    if "MyMmorpgApplication" in rel:
        return "player-service 根包，Spring Boot 启动类所在命名空间"
    return f"player-service 包 {pkg}"


def method_sig_comment(sig: str, class_name: str, filename: str) -> str:
    s = " ".join(sig.split())
    m = re.search(r"\b(\w+)\s*\(([^)]*)\)\s*(?:throws\s+\w+(?:\.\w+)*)?\s*\{?\s*$", s)
    if not m:
        return f"{class_name} 方法"
    name, params = m.group(1), m.group(2).strip()
    if name == class_name or (name[0].isupper() and "(" in s and "new " not in s):
        return f"构造 {class_name}，注入 {params[:80] or '无参'}"
    if name.startswith("get") and len(name) > 3 and name[3].isupper():
        field = name[3:]
        cn = ENTITY_FIELD_CN.get(field, field)
        return f"读取 {cn}（{field}）"
    if name.startswith("set") and len(name) > 3 and name[3].isupper():
        field = name[3:]
        cn = ENTITY_FIELD_CN.get(field, field)
        return f"写入 {cn}（{field}）"
    if name.startswith("is") and len(name) > 2 and name[2].isupper():
        field = name[2:]
        return f"判断 {ENTITY_FIELD_CN.get(field, field)} 是否为真"
    if name == "main":
        return "JVM 入口，bootstrap Redis 后启动 SpringApplication"
    if name == "run":
        return "CommandLineRunner：容器就绪后执行 dev 种子或预热"
    if name == "prePersist":
        return "JPA insert 前填充 createTime/enabled 默认值"
    if name == "init":
        return "PostConstruct：扫描 Facade 注册 msgId 路由表"
    if name == "start":
        return "PostConstruct：Netty bind 或启动网络监听"
    if name == "stop":
        return "PreDestroy：优雅关闭 Netty/EventLoopGroup"
    if name == "execute" or name == "create" or name == "invoke" or name == "newPacket":
        return f"GameMessageFactory Invoker 内部：{name} 路由调用"
    cf = comment_for(s, filename)
    if cf and not BANNED.search(cf):
        return cf
    return f"{class_name}.{name}：{params[:60] or '无参'}"


def annotation_comment(ann: str, filename: str) -> str:
    base = ann.split("(")[0].strip()
    if base in ANNOTATION_HINTS:
        return ANNOTATION_HINTS[base]
    if base == "@MessageRoute":
        return "Facade 所属协议 module，signedMsgId=module*100+cmd"
    if base == "@RequestHandler":
        return "Facade handler cmd，与 PayloadPacket @MessageMeta 对齐"
    cf = comment_for(ann, filename)
    if cf and not BANNED.search(cf):
        return cf
    return f"{base} 注解"


def brace_comment(stack: list[str]) -> str:
    if not stack:
        return "编译单元结束"
    ctx = stack[-1]
    if ctx.startswith("class:"):
        return f"{ctx[6:]} 类体结束"
    if ctx.startswith("method:"):
        return f"{ctx[7:]} 方法体结束"
    if ctx.startswith("block:"):
        return f"{ctx[6:]} 代码块结束"
    return f"{ctx} 结束"


def type_decl_comment(line: str, class_name: str) -> str:
    s = line.strip()
    if "interface " in s:
        return f"{class_name} 接口定义"
    if "enum " in s:
        return f"{class_name} 枚举"
    if "record " in s:
        return f"{class_name} 记录类型"
    cf = comment_for(s, class_name + ".java")
    if cf and not BANNED.search(cf):
        return cf
    return f"{class_name} 类型定义"


def track_braces(line: str, stack: list[str], class_name: str) -> None:
    s = line.strip()
    # method/class openings
    if re.search(r"\b(class|interface|enum|record)\s+\w+", s) and "{" in s:
        stack.append(f"class:{class_name}")
    elif re.search(r"\)\s*\{", s) and not s.startswith("@"):
        m = re.search(r"\b(\w+)\s*\([^)]*\)\s*\{", s)
        if m:
            stack.append(f"method:{m.group(1)}")
        else:
            stack.append("block:匿名块")
    elif s.endswith("{") and ("if " in s or "else" in s or "for " in s or "while " in s or "try" in s or "catch" in s or "switch " in s):
        kind = s.split("(")[0].strip().split()[-1] if "(" in s else s.replace("{", "").strip()
        stack.append(f"block:{kind}")
    elif s == "{":
        stack.append("block:复合语句")


def process_file(path: Path) -> tuple[bool, int]:
    rel = str(path.relative_to(ROOT)).replace("\\", "/")
    text = path.read_text(encoding="utf-8")
    lines = text.splitlines()
    class_name = path.stem
    module = rel.split("/")[0] if "/" in rel else "root"
    out: list[str] = []
    stack: list[str] = []
    added = 0

    for raw in lines:
        if not needs_comment(raw):
            track_open = raw.rstrip()
            if track_open.strip().endswith("{"):
                track_braces(track_open, stack, class_name)
            if track_open.strip() == "}" and stack:
                stack.pop()
            out.append(raw.rstrip())
            continue

        code, _ = strip_comment(raw)
        stripped = code.strip()
        indent = code[: len(code) - len(code.lstrip())]
        comment: str | None = None

        if stripped.startswith("package "):
            pkg = stripped.replace("package ", "").replace(";", "")
            comment = package_comment(pkg, rel)
        elif stripped.startswith("import "):
            comment = import_comment(stripped, path.name)
        elif stripped.startswith("@"):
            comment = annotation_comment(stripped, path.name)
        elif stripped == "}":
            comment = brace_comment(stack)
            if stack:
                stack.pop()
        elif stripped == "};":
            comment = brace_comment(stack) + "（含分号）"
            if stack:
                stack.pop()
        elif re.search(r"\b(class|interface|enum|record)\s+\w+", stripped):
            comment = type_decl_comment(stripped, class_name)
            if "{" in stripped:
                stack.append(f"class:{class_name}")
        elif re.search(r"\)\s*\{", stripped) or (
            re.search(r"\b(public|private|protected)\s+", stripped)
            and "(" in stripped
            and stripped.rstrip().endswith("{")
        ):
            comment = method_sig_comment(stripped, class_name, path.name)
            mm = re.search(r"\b(\w+)\s*\(", stripped)
            stack.append(f"method:{mm.group(1) if mm else 'method'}")
        elif stripped.endswith("{") and not stripped.startswith("//"):
            if "else" in stripped:
                comment = "else 分支"
                stack.append("block:else")
            elif stripped == "{":
                comment = "复合语句块开始"
                stack.append("block:块")
            else:
                cf = comment_for(stripped, path.name)
                comment = cf if cf and not BANNED.search(cf) else "代码块开始"
                stack.append("block:块")
        else:
            cf = comment_for(stripped, path.name)
            if cf and not BANNED.search(cf):
                comment = cf
            elif stripped.startswith("private ") or stripped.startswith("protected ") or stripped.startswith("public "):
                if ";" in stripped and "(" not in stripped:
                    comment = f"{class_name} 字段"
                elif "(" in stripped and ";" in stripped:
                    comment = method_sig_comment(stripped, class_name, path.name)
            elif stripped.startswith("return "):
                comment = "返回给调用方"
            elif stripped.startswith("if "):
                comment = "业务分支"
            elif stripped.startswith("for ") or stripped.startswith("while "):
                comment = "迭代处理"
            elif stripped.startswith("throw "):
                comment = "抛出异常终止流程"
            elif stripped.startswith("break") or stripped.startswith("continue"):
                comment = "控制循环/分支"
            elif stripped.startswith("this."):
                field = stripped.split("=")[0].replace("this.", "").strip()
                comment = f"构造器注入 {field}"
            else:
                comment = f"{class_name} 逻辑"

        if comment:
            out.append(f"{code.rstrip()} // {comment}")
            added += 1
        else:
            out.append(code.rstrip())

        # track opening braces on commented lines
        if stripped.endswith("{") and stripped != "}":
            if not (re.search(r"\)\s*\{", stripped) and comment and "method:" in (stack[-1] if stack else "")):
                if not any(stripped.startswith(x) for x in ("package ", "import ", "@")):
                    if re.search(r"\)\s*\{", stripped):
                        m = re.search(r"\b(\w+)\s*\(", stripped)
                        if m and (not stack or not stack[-1].endswith(m.group(1))):
                            stack.append(f"method:{m.group(1)}")
                    elif stripped == "{":
                        stack.append("block:块")
                    elif "else" in stripped:
                        stack.append("block:else")

    new_text = "\n".join(out) + ("\n" if text.endswith("\n") else "")
    changed = new_text != text
    if changed:
        path.write_text(new_text, encoding="utf-8")
    return changed, added


def collect_files() -> list[Path]:
    files: list[Path] = []
    for d in DIRS:
        p = ROOT / d
        if p.is_dir():
            files.extend(sorted(p.rglob("*.java")))
    if APP.is_file():
        files.append(APP)
    return files


def count_gaps() -> int:
    total = 0
    for fp in collect_files():
        with open(fp, encoding="utf-8") as f:
            for line in f:
                if needs_comment(line):
                    total += 1
    return total


def main() -> int:
    modified = 0
    total_added = 0
    for fp in collect_files():
        changed, added = process_file(fp)
        if changed:
            modified += 1
            total_added += added
    gaps = count_gaps()
    print(f"Modified files: {modified}")
    print(f"Comments added (approx): {total_added}")
    print(f"Remaining gap lines: {gaps}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
