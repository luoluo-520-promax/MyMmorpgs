/**
 * HTTP 全局异常处理器：将 Spring MVC 层抛出的各类异常统一转换为 {@link ApiErrorResponse} JSON，
 * 避免 Controller 重复写 try-catch，并保证客户端始终收到固定结构的错误码与 traceId。
 */
package cn.itcast.demo.mymmorpg.web;

import jakarta.servlet.http.HttpServletRequest; // 读取当前请求 URI，写入错误响应便于定位接口

import org.slf4j.Logger; // 记录参数校验 warn 与未预期异常 error

import org.slf4j.LoggerFactory; // 创建本类专用 Logger

import org.springframework.http.HttpStatus; // 400/500 等 HTTP 状态码常量

import org.springframework.http.ResponseEntity; // 封装 HTTP 状态 + JSON 响应体

import org.springframework.validation.BindException; // 表单/Query 参数绑定失败（如类型转换错误）

import org.springframework.web.bind.MethodArgumentNotValidException; // @Valid/@Validated 注解校验失败

import org.springframework.web.bind.MissingServletRequestParameterException; // 缺少必填 Query/Form 参数

import org.springframework.web.bind.annotation.ExceptionHandler; // 声明某方法处理特定异常类型

import org.springframework.web.bind.annotation.RestControllerAdvice; // 全局拦截所有 @RestController 抛出的异常

import org.springframework.http.converter.HttpMessageNotReadableException; // JSON 体格式错误或字段类型不匹配

import java.util.stream.Collectors; // 将多条字段校验错误拼接成一条可读 message

/**
 * 挂载在所有 REST Controller 之上：参数类异常返回 400，其余未捕获异常返回 500。
 */
@RestControllerAdvice // Spring 启动时注册为全局异常切面
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class); // 本类日志门面

    /**
     * 捕获客户端参数/请求体错误，统一返回 HTTP 400 与可读错误信息。
     */
    @ExceptionHandler({
            IllegalArgumentException.class, // 业务层主动抛出的非法参数（如 ID<=0）
            MethodArgumentNotValidException.class, // DTO 字段 @NotNull/@Size 等校验不通过
            BindException.class, // 非 @RequestBody 的参数绑定失败
            MissingServletRequestParameterException.class, // 缺少 ?foo= 这类必填参数
            HttpMessageNotReadableException.class // 请求体不是合法 JSON 或枚举值无法解析
    })
    public ResponseEntity<ApiErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
        String message = resolveValidationMessage(ex); // 从异常中提取人类可读的错误描述
        log.warn("请求参数异常 path={} message={}", request.getRequestURI(), message); // warn 级别，便于排查错误调用
        return ResponseEntity.status(HttpStatus.BAD_REQUEST) // HTTP 400 Bad Request
                .body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST.value(), message, request)); // 构造统一 JSON 错误体
    }

    /**
     * 兜底处理器：上述未覆盖的异常一律视为服务端内部错误，对外隐藏细节。
     */
    @ExceptionHandler(Exception.class) // 匹配所有 Exception 子类（不含 Error）
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("未处理异常 path={}", request.getRequestURI(), ex); // error 级别并打印堆栈，供运维告警
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR) // HTTP 500
                .body(ApiErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR.value(), "服务器内部错误", request)); // 对外固定文案，不泄露内部异常信息
    }

    /**
     * 按异常类型提取最合适的错误 message：优先返回字段级校验详情，否则用异常 message 或默认文案。
     */
    private String resolveValidationMessage(Exception ex) {
        if (ex instanceof MethodArgumentNotValidException manv && manv.getBindingResult().hasErrors()) { // @RequestBody + @Valid 校验失败
            return manv.getBindingResult().getFieldErrors().stream() // 遍历每个字段的错误
                    .map(err -> err.getField() + ": " + err.getDefaultMessage()) // 拼成 "username: 不能为空" 格式
                    .collect(Collectors.joining("; ")); // 多个字段错误用分号连接
        }
        if (ex instanceof BindException be && be.getBindingResult().hasErrors()) { // 表单绑定校验失败，处理逻辑同上
            return be.getBindingResult().getFieldErrors().stream()
                    .map(err -> err.getField() + ": " + err.getDefaultMessage())
                    .collect(Collectors.joining("; "));
        }
        return ex.getMessage() == null || ex.getMessage().isBlank() ? "请求参数错误" : ex.getMessage(); // 其他 400 类异常直接透传 message，空则给默认提示
    }
}
