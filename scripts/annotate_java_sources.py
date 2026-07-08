# -*- coding: utf-8 -*-
"""为 Java 源文件添加详细中文注释（仅处理尚未正确标注的文件）。"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

IMPORT_HINTS = {
    "SpringApplication": "Spring Boot 应用启动器",
    "SpringBootApplication": "Spring Boot 自动配置与组件扫描",
    "ConfigurationProperties": "绑定配置属性",
    "ConfigurationPropertiesScan": "扫描 @ConfigurationProperties 类",
    "EnableFeignClients": "启用 Feign 远程调用客户端",
    "EnableScheduling": "启用定时任务",
    "Component": "注册为 Spring 组件",
    "Service": "注册为 Spring 服务 Bean",
    "Repository": "数据访问层",
    "RestController": "REST 控制器",
    "Configuration": "Spring 配置类",
    "Bean": "声明 Spring Bean",
    "Autowired": "自动注入依赖",
    "Value": "注入配置属性值",
    "Transactional": "声明事务边界",
    "Logger": "日志记录器类型",
    "LoggerFactory": "日志工厂",
    "ObjectMapper": "Jackson JSON 序列化/反序列化",
    "StringRedisTemplate": "Redis 字符串操作模板",
    "Optional": "可选值容器",
    "List": "列表集合",
    "Map": "键值映射",
    "HashMap": "哈希映射实现",
    "ArrayList": "动态数组列表",
    "Set": "集合接口",
    "HashSet": "哈希集合实现",
    "Comparator": "比较器",
    "Objects": "对象工具类",
    "Mono": "Reactor 单值异步流",
    "Flux": "Reactor 多值异步流",
    "GlobalFilter": "Gateway 全局过滤器接口",
    "GatewayFilterChain": "Gateway 过滤器链",
    "Ordered": "过滤器排序接口",
    "ServerWebExchange": "Web 请求/响应上下文",
    "ServerHttpRequest": "响应式 HTTP 请求",
    "ServerHttpResponse": "响应式 HTTP 响应",
    "HttpHeaders": "HTTP 请求头",
    "HttpStatus": "HTTP 状态码枚举",
    "MediaType": "媒体类型常量",
    "WebSocketService": "WebSocket 服务接口",
    "EnableConfigurationProperties": "启用配置属性绑定",
    "Primary": "优先使用的 Bean",
    "SuppressWarnings": "抑制编译器警告",
    "PostMapping": "HTTP POST 映射",
    "RequestBody": "请求体参数绑定",
    "RequestHeader": "请求头参数绑定",
    "RequestMapping": "请求路径映射",
    "ResponseEntity": "HTTP 响应封装",
    "ConditionalOnProperty": "按配置属性条件注册 Bean",
    "ConditionalOnMissingBean": "缺少 Bean 时注册",
    "Entity": "JPA 实体",
    "Table": "JPA 表映射",
    "Id": "JPA 主键",
    "Column": "JPA 列映射",
    "GeneratedValue": "JPA 主键生成策略",
    "JpaRepository": "Spring Data JPA 仓库基接口",
    "Test": "单元测试方法",
    "BeforeMethod": "TestNG 测试前置",
    "AfterMethod": "TestNG 测试后置",
}


def has_valid_header(text: str) -> bool:
    """判断是否已有正确 UTF-8 中文文件头。"""
    if "文件维护说明" in text and "文件路径" in text:
        return True
    if "文件说明" in text and "职责" in text:
        return True
    return False


def needs_reannotate(text: str) -> bool:
    """Gateway 等文件注释已损坏（中文变成问号）。"""
    if "????" in text[:2000]:
        return True
    return not has_valid_header(text)


def infer_class_desc(path: str, class_name: str) -> str:
    rel = path.replace("\\", "/")
    if "Test" in class_name or "/test/" in rel:
        return f"测试类 {class_name}，验证相关业务逻辑。"
    if "Application" in class_name:
        return f"微服务启动入口 {class_name}。"
    if "Controller" in class_name:
        return f"HTTP 控制器 {class_name}，对外暴露 REST 接口。"
    if "Repository" in class_name:
        return f"数据仓库接口 {class_name}，封装数据库访问。"
    if "Facade" in class_name:
        return f"门面类 {class_name}，协调协议层与业务服务。"
    if "Service" in class_name:
        return f"业务服务 {class_name}，承载核心领域逻辑。"
    if "Filter" in class_name:
        return f"过滤器 {class_name}，在请求链中执行横切逻辑。"
    if "Properties" in class_name:
        return f"配置属性类 {class_name}，绑定 application.yml 配置项。"
    if "Configuration" in class_name:
        return f"Spring 配置类 {class_name}，注册 Bean 与组件扫描。"
    if "Event" in class_name:
        return f"领域事件 {class_name}，在模块内传递状态变更。"
    return f"类 {class_name}，承载模块内相关实现。"


def build_file_header(rel_path: str, class_name: str) -> str:
    module = rel_path.split("/")[0] if "/" in rel_path else rel_path.split("\\")[0]
    sub = "/".join(rel_path.replace("\\", "/").split("/")[2:-1]) if "src/" in rel_path.replace("\\", "/") else ""
    desc = infer_class_desc(rel_path, class_name)
    return (
        "/**\n"
        " * 文件维护说明\n"
        f" * 1) 文件路径：{rel_path.replace(chr(92), '/')}\n"
        f" * 2) 所属模块：{module} / {sub}\n"
        f" * 3) 主要职责：{desc}\n"
        " * 4) 变更建议：修改前先确认上下游依赖、协议字段与缓存键是否受影响。\n"
        " * 5) 风险提示：若涉及并发、事务、MQ、Redis，请同步补充回归测试与监控指标。\n"
        " */\n"
    )


def extract_class_name(text: str) -> str:
    m = re.search(r"\b(?:public\s+)?(?:final\s+)?(?:class|interface|enum|record)\s+(\w+)", text)
    return m.group(1) if m else "Unknown"


def comment_import(line: str) -> str:
    stripped = line.strip()
    if not stripped.startswith("import ") or stripped.endswith(";") is False:
        return line
    if "//" in line:
        return line
    imp = stripped[7:-1]
    if imp.startswith("static "):
        return line
    simple = imp.rsplit(".", 1)[-1]
    hint = IMPORT_HINTS.get(simple, f"导入 {simple}")
    return f"import {imp}; // {hint}\n"


def comment_package(line: str, pkg: str) -> str:
    if "//" in line:
        return line
    return f"package {pkg}; // 声明当前类所在包：{pkg}\n"


def strip_broken_header(text: str) -> str:
    """移除损坏的文件头块注释（含大量问号）。"""
    if not text.startswith("/**"):
        return text
    end = text.find("*/")
    if end == -1:
        return text
    header = text[:end + 2]
    if "????" not in header:
        return text
    rest = text[end + 2:]
    # 移除紧随其后的单行 // 包注释
    rest = re.sub(r"^\s*//[^\n]*\n", "", rest, count=1)
    return rest.lstrip("\n")


def annotate_line(line: str, in_method: bool) -> str:
    """为方法体内有效代码行添加行尾注释。"""
    raw = line.rstrip("\n\r")
    if not in_method:
        return line
    stripped = raw.strip()
    if not stripped or stripped.startswith("//") or stripped.startswith("*") or stripped.startswith("/*"):
        return line
    if stripped in ("{", "}", "};"):
        return line
    if "//" in raw:
        return line
  # 简单启发式中文说明
    comment = None
    if stripped.startswith("return "):
        comment = "返回结果"
    elif stripped.startswith("if (") or stripped.startswith("if("):
        comment = "条件判断"
    elif stripped.startswith("else if"):
        comment = "否则若条件满足"
    elif stripped == "else":
        comment = "否则分支"
    elif stripped.startswith("for (") or stripped.startswith("for("):
        comment = "循环遍历"
    elif stripped.startswith("while "):
        comment = "循环条件"
    elif stripped.startswith("throw "):
        comment = "抛出异常"
    elif stripped.startswith("this."):
        comment = "访问当前实例字段或方法"
    elif "=" in stripped and not stripped.startswith("="):
        comment = "赋值或初始化"
    elif stripped.endswith(");") or stripped.endswith(");"):
        comment = "调用方法"
    elif stripped.startswith("@Override"):
        comment = "重写父类/接口方法"
    else:
        comment = "执行业务步骤"
    indent = raw[: len(raw) - len(raw.lstrip())]
    return f"{indent}{stripped} // {comment}\n"


def reannotate_inplace(path: str) -> bool:
    """在已有文件头的前提下，为 import 与方法体补充注释。"""
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        text = f.read()
    lines = text.splitlines(keepends=True)
    out = []
    in_method = False
    brace_depth = 0

    for line in lines:
        stripped = line.strip()
        if stripped.startswith("import ") and stripped.endswith(";") and "//" not in line:
            out.append(comment_import(line))
            continue
        if stripped.startswith("package ") and stripped.endswith(";") and "//" not in line:
            pkg = stripped[8:-1]
            out.append(comment_package(line, pkg))
            continue
        if re.search(r"\b(public|private|protected)\s+[\w<>,\s\[\]]+\s+\w+\s*\([^)]*\)\s*\{?\s*$", stripped):
            in_method = True
            brace_depth = stripped.count("{") - stripped.count("}")
            if "//" not in line and not stripped.startswith("/**"):
                out.append(line.rstrip("\n\r") + " // 方法定义\n")
            else:
                out.append(line)
            continue
        if in_method:
            brace_depth += line.count("{") - line.count("}")
            out.append(annotate_line(line, True))
            if brace_depth <= 0 and stripped.endswith("}"):
                in_method = False
            continue
        out.append(line)

    new_text = "".join(out)
    if new_text != text:
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write(new_text)
        return True
    return False


    rel = os.path.relpath(path, ROOT).replace("\\", "/")
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        text = f.read()

    if not needs_reannotate(text):
        return False

    text = strip_broken_header(text)
    class_name = extract_class_name(text)
    header = build_file_header(rel, class_name)

    lines = text.splitlines(keepends=True)
    out = [header]
    in_method = False
    brace_depth = 0
    pkg_done = False

    for line in lines:
        stripped = line.strip()
        if stripped.startswith("package ") and stripped.endswith(";"):
            pkg = stripped[8:-1]
            out.append(comment_package(line, pkg))
            pkg_done = True
            continue
        if stripped.startswith("import "):
            out.append(comment_import(line))
            continue
        if re.search(r"\b(public|private|protected)\s+[\w<>,\s\[\]]+\s+\w+\s*\([^)]*\)\s*\{?\s*$", stripped):
            in_method = True
            brace_depth = stripped.count("{") - stripped.count("}")
            if "//" not in line and not stripped.startswith("/**"):
                out.append(line.rstrip("\n\r") + " // 方法定义\n")
            else:
                out.append(line)
            continue
        if in_method:
            brace_depth += line.count("{") - line.count("}")
            out.append(annotate_line(line, True))
            if brace_depth <= 0 and stripped.endswith("}"):
                in_method = False
            continue
        # 类/字段/注解
        if stripped.startswith("@") and "//" not in line:
            out.append(line.rstrip("\n\r") + " // Spring/框架注解\n")
        elif re.match(r"(public|private|protected)\s+(static\s+)?(final\s+)?[\w<>,\s\[\]]+\s+\w+\s*[=;]", stripped) and "//" not in line:
            out.append(line.rstrip("\n\r") + " // 字段定义\n")
        elif re.match(r"(public|private|protected)\s+(static\s+)?(final\s+)?(?:class|interface|enum)\s+", stripped) and "//" not in line:
            out.append(line.rstrip("\n\r") + " // 类型定义\n")
        else:
            out.append(line)

    new_text = "".join(out)
    if not new_text.endswith("\n"):
        new_text += "\n"
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(new_text)
    return True


def clean_corrupted_line(line: str) -> str:
    """移除或修复含乱码问号的行内/独立注释。"""
    stripped = line.rstrip("\n\r")
    # 独立注释行：全是问号说明
    if re.match(r"^\s*//\s*\?+", stripped):
        return ""
    # 行尾注释含乱码：去掉旧注释，保留代码
    if "//" in stripped:
        code, _, comment = stripped.partition("//")
        if re.search(r"\?{2,}", comment):
            return code.rstrip() + "\n"
    return line


def clean_javadoc_blocks(text: str) -> str:
    """删除含乱码的 Javadoc 块。"""
    def repl(m):
        block = m.group(0)
        if re.search(r"\?{2,}", block):
            return ""
        return block
    return re.sub(r"/\*\*.*?\*/", repl, text, flags=re.DOTALL)


def cleanup_corrupted(path: str) -> bool:
    with open(path, "r", encoding="utf-8") as f:
        text = f.read()
    if not re.search(r"\?{3,}", text):
        return False
    text = clean_javadoc_blocks(text)
    lines = [clean_corrupted_line(l) for l in text.splitlines(keepends=True)]
    text = "".join(lines)
  # 去掉 import 前残留的无意义单行注释
    text = re.sub(r"^\s*//\s*[^\n]*\n(?=import )", "", text, flags=re.MULTILINE)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    return True


def main():
    targets = []
    for mod in ("player-service", "mmorpg-gateway", "activity-service", "battle-service", "mmorpg-common"):
        mod_path = os.path.join(ROOT, mod)
        if not os.path.isdir(mod_path):
            continue
        for dirpath, dirnames, filenames in os.walk(mod_path):
            if "target" in dirpath.replace("\\", "/").split("/"):
                dirnames[:] = []
                continue
            for fn in filenames:
                if fn.endswith(".java"):
                    targets.append(os.path.join(dirpath, fn))

    cleaned = 0
    for p in sorted(targets):
        if cleanup_corrupted(p):
            cleaned += 1
            print("cleaned:", os.path.relpath(p, ROOT))

    changed = 0
    for p in sorted(targets):
        with open(p, "r", encoding="utf-8") as f:
            content = f.read()
        # 乱码清理后重新标注 import 与无注释代码行
        if re.search(r"\?{3,}", content):
            cleanup_corrupted(p)
            if reannotate_inplace(p):
                changed += 1
                print("re-annotated:", os.path.relpath(p, ROOT))
        elif needs_reannotate(content):
            if annotate_file(p):
                changed += 1
                print("annotated:", os.path.relpath(p, ROOT))
    print(f"Done. cleaned={cleaned}, annotated={changed}, total_targets={len(targets)}.")


if __name__ == "__main__":
    main()
