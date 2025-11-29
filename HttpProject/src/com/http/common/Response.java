package common;

// 导入标准字符集：用于响应体字符串转换时的默认编码（UTF-8）
import java.nio.charset.StandardCharsets;
// 导入Collections：用于返回不可修改的Map集合，保证数据安全性
import java.util.Collections;
// 导入HashMap：用于存储响应头的键值对
import java.util.HashMap;
// 导入Map：定义响应头的键值对集合类型
import java.util.Map;

/**
 * HTTP响应封装类：存储HTTP响应的核心信息（状态行、状态码、原因短语、响应头、响应体）
 * 提供安全的访问/修改方法，以及常用辅助判断（重定向、304未修改）和响应体字符串转换功能
 * 遵循HTTP协议规范，处理响应头大小写不敏感、响应体空值安全等细节
 */
public class Response {
    /**
     * 完整的HTTP响应状态行（格式：HTTP/1.1 200 OK）
     * 包含协议版本、状态码、原因短语，可直接用于响应输出
     */
    private String statusLine;

    /**
     * HTTP响应状态码（如200、301、302、304、404、500等）
     * 标识响应的处理结果，遵循HTTP协议标准状态码定义
     */
    private int statusCode;

    /**
     * 响应状态码对应的原因短语（如OK、Not Found、Moved Permanently等）
     * 是状态码的文字描述，增强响应的可读性
     */
    private String reasonPhrase;

    /**
     * HTTP响应头集合（键值对形式）
     * 存储响应相关的元信息（如Content-Type、Content-Length、Location等）
     * 内部用HashMap存储，通过set方法自动转为小写键，保证HTTP头大小写不敏感的特性
     */
    private final Map<String, String> headers = new HashMap<>();

    /**
     * HTTP响应体字节数组
     * 存储响应的实际数据（如HTML页面、JSON字符串、图片二进制数据等）
     * 默认初始化为空字节数组，避免空指针异常，setBody时自动处理null输入
     */
    private byte[] body = new byte[0];

    /**
     * 获取完整的响应状态行（如"HTTP/1.1 200 OK"）
     * @return 状态行字符串，未设置则返回null
     */
    public String getStatusLine() { return statusLine; }

    /**
     * 获取响应状态码（如200、404）
     * @return 状态码整数（默认0，需通过set方法设置）
     */
    public int getStatusCode() { return statusCode; }

    /**
     * 获取响应原因短语（如"OK"、"Not Found"）
     * @return 原因短语字符串，未设置则返回null
     */
    public String getReasonPhrase() { return reasonPhrase; }

    /**
     * 获取响应头集合（不可修改）
     * 防止外部直接修改内部头集合，保证响应数据的一致性和安全性
     * @return 不可修改的响应头Map（键为小写，值为原始值）
     */
    public Map<String, String> getHeaders() { return Collections.unmodifiableMap(headers); }

    /**
     * 获取响应体字节数组
     * @return 响应体字节数组（默认空数组，非null）
     */
    public byte[] getBody() { return body; }

    /**
     * 设置完整的响应状态行
     * @param statusLine 状态行字符串（如"HTTP/1.1 302 Found"）
     */
    public void setStatusLine(String statusLine) { this.statusLine = statusLine; }

    /**
     * 设置响应状态码
     * @param statusCode 状态码整数（需符合HTTP协议标准，如200、304、404等）
     */
    public void setStatusCode(int statusCode) { this.statusCode = statusCode; }

    /**
     * 设置响应原因短语
     * @param reasonPhrase 原因短语字符串（如"Found"、"Not Modified"）
     */
    public void setReasonPhrase(String reasonPhrase) { this.reasonPhrase = reasonPhrase; }

    /**
     * 设置单个响应头（自动将头名转为小写，遵循HTTP头大小写不敏感规范）
     * 若存在同名头（忽略大小写），新值会覆盖旧值
     * @param name 响应头名（如Content-Type、Location）
     * @param value 响应头值
     */
    public void setHeader(String name, String value) { this.headers.put(name.toLowerCase(), value); }

    /**
     * 批量设置响应头（清空原有头，批量添加新头，自动将头名转为小写）
     * @param map 待设置的响应头Map（键为任意大小写，值为头值）
     */
    public void setHeaders(Map<String, String> map) {
        this.headers.clear(); // 清空原有所有响应头
        // 遍历输入Map，将所有头名转为小写后存入内部集合
        map.forEach((k, v) -> this.headers.put(k.toLowerCase(), v));
    }

    /**
     * 设置响应体字节数组（处理null输入，自动转为空字节数组）
     * @param body 响应体字节数组（可为null，null时设为空数组）
     */
    public void setBody(byte[] body) { this.body = body == null ? new byte[0] : body; }

    /**
     * 根据头名获取响应头值（自动将头名转为小写，保证查询一致性）
     * @param name 响应头名（大小写不敏感，如"content-type"、"Content-Type"均可）
     * @return 对应的响应头值，未找到则返回null
     */
    public String getHeader(String name) { return headers.get(name.toLowerCase()); }

    /**
     * 判断当前响应是否为重定向响应（状态码为301或302）
     * 301：永久重定向；302：临时重定向
     * @return 是重定向响应返回true，否则返回false
     */
    public boolean isRedirect() {
        return this.statusCode == 301 || this.statusCode == 302;
    }

    /**
     * 判断当前响应是否为"资源未修改"响应（状态码为304）
     * 对应HTTP的条件请求（如带If-None-Match、If-Modified-Since头的请求）
     * @return 是304响应返回true，否则返回false
     */
    public boolean isNotModified() {
        return this.statusCode == 304;
    }

    /**
     * 将响应体字节数组转为字符串（自动处理字符集，兼容多种编码）
     * 优先从Content-Type头提取charset，无则默认UTF-8，编码异常时也用UTF-8兜底
     * @return 响应体对应的字符串（空响应体返回空字符串）
     */
    public String bodyAsString() {
        // 若响应体为null（理论上不会发生，因setBody已处理null），返回空字符串
        if (body == null) return "";

        // 步骤1：从Content-Type头提取字符集（如"text/html; charset=utf-8"中的utf-8）
        String charset = null;
        String ct = getHeader("content-type"); // 获取Content-Type头（自动转为小写键查询）
        if (ct != null && ct.contains("charset=")) {
            // 找到charset=的起始索引，截取其后的字符作为字符集（去除前后空格）
            int idx = ct.indexOf("charset=");
            charset = ct.substring(idx + 8).trim();
        }

        // 步骤2：根据字符集转换字节数组为字符串
        try {
            // 无指定字符集则用UTF-8，有则用指定字符集
            if (charset == null) return new String(body, StandardCharsets.UTF_8);
            return new String(body, charset);
        } catch (Exception e) {
            // 字符集不支持或转换异常时，用UTF-8兜底（保证返回有效字符串）
            return new String(body, StandardCharsets.UTF_8);
        }
    }
}