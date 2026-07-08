/**
 * 文件说明
 * 模块：mmorpg-common / RPC 通信
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/rpc/RpcResponse.java
 * 类型：类
 * 职责：Protostuff RPC 远程调用响应对象（扩展占位），携带执行结果或错误信息。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.rpc;

/**
 * Protostuff RPC 响应对象：与  配对，返回成功载荷或错误描述（扩展占位）。
 */
public class RpcResponse {

    /** 与 RpcRequest.requestId 对应的请求序号，客户端据此匹配回调 */
    private long requestId;
    /** 远程方法是否执行成功 */
    private boolean success;
    /** Protostuff 序列化后的方法返回值二进制（success 为 true 时有值） */
    private byte[] returnPayload;
    /** 远程调用失败时的错误描述（success 为 false 时有值） */
    private String errorMessage;

    /** 获取对应的 RPC 请求序号 */
    public long getRequestId() {
        return requestId;
    }

    /** 设置对应的 RPC 请求序号 */
    public void setRequestId(long requestId) {
        this.requestId = requestId;
    }

    /** 判断远程方法是否执行成功 */
    public boolean isSuccess() {
        return success;
    }

    /** 设置远程方法执行是否成功 */
    public void setSuccess(boolean success) {
        this.success = success;
    }

    /** 获取 Protostuff 编码的方法返回值二进制 */
    public byte[] getReturnPayload() {
        return returnPayload;
    }

    /** 设置 Protostuff 编码的方法返回值二进制 */
    public void setReturnPayload(byte[] returnPayload) {
        this.returnPayload = returnPayload;
    }

    /** 获取远程调用失败时的错误描述 */
    public String getErrorMessage() {
        return errorMessage;
    }

    /** 设置远程调用失败时的错误描述 */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
