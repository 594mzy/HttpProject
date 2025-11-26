package common;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP请求封装类：存储HTTP请求的核心信息（方法、路径、协议版本、请求头、请求体）
 * 提供安全的访问和修改方式，确保请求数据的一致性
 */
public class Request {
    /**
     * HTTP请求方法（如GET、POST、PUT、DELETE等）
     * 遵循HTTP协议规范，大小写敏感（通常为大写形式）
     */
    private String method;

    /**
     * HTTP请求路径（含查询参数，如/index.html?name=test）
     * 是URL中主机和端口后的部分，标识服务器上的目标资源
     */
    private String path;

    /**
     * HTTP协议版本（如HTTP/1.1、HTTP/1.0）
     * 决定请求的传输规则（如是否支持Keep-Alive、分块传输等）
     */
    private String version;

    /**
     * HTTP请求头集合（键值对形式）
     * 存储请求相关的元信息（如Host、Content-Type、Connection等）
     * 内部使用HashMap存储，通过setHeader自动转为小写键，保证HTTP头大小写不敏感的特性
     */
    private final Map<String, String> headers = new HashMap<>();

    /**
     * HTTP请求体字节数组
     * 存储POST、PUT等请求的请求数据（如表单数据、JSON字符串、文件二进制数据）
     * 默认初始化为空字节数组，避免空指针异常，setBody时会自动处理null输入
     */
    private byte[] body = new byte[0];

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    /**
     * 获取请求头集合（不可修改）
     * 防止外部直接修改内部头集合，保证请求数据的安全性
     */
    public Map<String, String> getHeaders() { return Collections.unmodifiableMap(headers); }

    /**
     * 设置请求头（自动将头名转为小写，遵循HTTP头大小写不敏感规范）
     * @param name 请求头名（如Content-Type、Connection）
     * @param value 请求头值
     */
    public void setHeader(String name, String value) { this.headers.put(name.toLowerCase(), value); }

    /**
     * 获取请求头值（自动将传入的头名转为小写，保证查询一致性）
     * @param name 请求头名（大小写不敏感）
     * @return 对应的请求头值，未找到则返回null
     */
    public String getHeader(String name) { return this.headers.get(name.toLowerCase()); }

    public byte[] getBody() { return body; }

    /**
     * 设置请求体（处理null输入，自动转为空字节数组）
     * @param body 请求体字节数组（可为null）
     */
    public void setBody(byte[] body) { this.body = body == null ? new byte[0] : body; }
}