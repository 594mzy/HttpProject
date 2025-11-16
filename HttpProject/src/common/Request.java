package com.http.common;

import java.util.HashMap;
import java.util.Map;

/**
 * 封装HTTP请求信息（请求行、请求头、请求体、参数）
 * TODO：无需修改，已完整实现
 */
public class Request {
    private String method; // 请求方法：GET/POST
    private String path; // 资源路径：如 /index.html
    private String httpVersion; // HTTP版本：如 HTTP/1.1
    private Map<String, String> headers = new HashMap<>(); // 请求头：key-value格式
    private String body; // 请求体（仅POST有）
    private Map<String, String> params = new HashMap<>(); // 请求参数（POST解析后）

    // Getter & Setter（直接使用，无需修改）
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getHttpVersion() { return httpVersion; }
    public void setHttpVersion(String httpVersion) { this.httpVersion = httpVersion; }
    public Map<String, String> getHeaders() { return headers; }
    public void addHeader(String key, String value) { this.headers.put(key, value); }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public Map<String, String> getParams() { return params; }
    public void addParam(String key, String value) { this.params.put(key, value); }
}