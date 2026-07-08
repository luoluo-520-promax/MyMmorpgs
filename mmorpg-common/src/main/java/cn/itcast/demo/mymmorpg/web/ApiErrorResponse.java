/**
 * 统一 HTTP 错误响应体：所有 REST 异常经 {@link GlobalExceptionHandler} 转换后返回此结构，
 * 前端/调用方可稳定解析 code、message、traceId、path、timestamp 五个字段。
 */
package cn.itcast.demo.mymmorpg.web;

import jakarta.servlet.http.HttpServletRequest; // 读取请求 URI 写入 path 字段

import org.slf4j.MDC; // 从日志上下文取 TraceIdFilter 注入的 traceId

/**
 * 不可变错误响应 DTO，仅通过静态工厂 {@link #of} 创建。
 */
public final class ApiErrorResponse {

    /** 业务/HTTP 错误码，与 ResponseEntity 状态码一致（如 400、500） */
    private final int code;
    /** 面向调用方的人类可读错误描述 */
    private final String message;
    /** 全链路追踪 ID，与日志 MDC 及响应头 X-Trace-Id 一致 */
    private final String traceId;
    /** 触发异常的请求路径，如 /api/player/login */
    private final String path;
    /** 服务端生成响应时的毫秒时间戳，便于客户端排序与对账 */
    private final long timestamp;

    /** 私有构造：强制外部通过 of() 工厂创建，保证 traceId/path 等字段完整填充 */
    private ApiErrorResponse(int code, String message, String traceId, String path, long timestamp) {
        this.code = code; // 写入 HTTP/业务错误码
        this.message = message; // 写入对外展示的错误文案
        this.traceId = traceId; // 写入链路追踪 ID，便于 grep 日志
        this.path = path; // 写入出错的 API 路径
        this.timestamp = timestamp; // 写入响应生成时刻
    }

    /**
     * 根据 HTTP 状态码、错误信息与当前请求构造标准错误体。
     * traceId 优先从 MDC 读取（TraceIdFilter 已注入），其次读请求头 X-Trace-Id。
     */
    public static ApiErrorResponse of(int code, String message, HttpServletRequest request) {
        String traceId = MDC.get(TraceIdFilter.TRACE_ID); // 从 SLF4J 上下文取本请求 traceId
        if (traceId == null || traceId.isBlank()) { // Filter 未生效时（如单元测试）回退读请求头
            traceId = request.getHeader("X-Trace-Id"); // 支持网关/上游透传的 traceId
        }
        return new ApiErrorResponse(code, message, traceId, request.getRequestURI(), System.currentTimeMillis()); // 组装完整错误响应
    }

    /** 返回错误码，供 JSON 序列化字段 "code" */
    public int getCode() {
        return code;
    }

    /** 返回错误描述，供 JSON 序列化字段 "message" */
    public String getMessage() {
        return message;
    }

    /** 返回链路追踪 ID，供 JSON 序列化字段 "traceId" */
    public String getTraceId() {
        return traceId;
    }

    /** 返回出错请求路径，供 JSON 序列化字段 "path" */
    public String getPath() {
        return path;
    }

    /** 返回响应生成时间戳，供 JSON 序列化字段 "timestamp" */
    public long getTimestamp() {
        return timestamp;
    }
}
