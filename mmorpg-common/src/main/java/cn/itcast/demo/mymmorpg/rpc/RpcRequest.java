/**
 * 文件说明
 * 模块：mmorpg-common / RPC 通信
 * 路径：src/main/java/cn/itcast/demo/mymmorpg/rpc/RpcRequest.java
 * 类型：类
 * 职责：Protostuff RPC 远程调用请求对象（扩展占位），描述服务类、方法及参数载荷。
 * 注意：本文件仅含注释变更时请勿改动业务逻辑。
 */
package cn.itcast.demo.mymmorpg.rpc;

/**
 * Protostuff RPC 请求对象：携带 requestId、目标服务/方法及 Protostuff 编码的参数（扩展占位）。
 */
public class RpcRequest {

    /** 全局唯一的 RPC 请求序号，用于匹配异步响应 RpcResponse */
    private long requestId;
    /** 目标远程服务类的全限定名，RPC 框架据此定位 Spring Bean */
    private String serviceClass;
    /** 目标远程方法的名称 */
    private String methodName;
    /** 各参数类型的全限定类名数组，用于 Protostuff 反序列化参数 */
    private String[] argTypes;
    /** Protostuff 序列化后的方法实参二进制载荷 */
    private byte[] argsPayload;

    /** 获取 RPC 请求序号，响应端原样回填至 RpcResponse.requestId */
    public long getRequestId() {
        return requestId;
    }

    /** 设置 RPC 请求序号 */
    public void setRequestId(long requestId) {
        this.requestId = requestId;
    }

    /** 获取目标远程服务类全限定名 */
    public String getServiceClass() {
        return serviceClass;
    }

    /** 设置目标远程服务类全限定名 */
    public void setServiceClass(String serviceClass) {
        this.serviceClass = serviceClass;
    }

    /** 获取目标远程方法名 */
    public String getMethodName() {
        return methodName;
    }

    /** 设置目标远程方法名 */
    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    /** 获取参数类型全限定名数组 */
    public String[] getArgTypes() {
        return argTypes;
    }

    /** 设置参数类型全限定名数组 */
    public void setArgTypes(String[] argTypes) {
        this.argTypes = argTypes;
    }

    /** 获取 Protostuff 编码的方法实参二进制 */
    public byte[] getArgsPayload() {
        return argsPayload;
    }

    /** 设置 Protostuff 编码的方法实参二进制 */
    public void setArgsPayload(byte[] argsPayload) {
        this.argsPayload = argsPayload;
    }
}
