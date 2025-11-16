package com.http.common;

import java.util.HashMap;
import java.util.Map;

/**
 * 封装HTTP响应信息（状态行、响应头、响应体）
 * TODO：无需修改，已完整实现
 */
public class Response {
    private int statusCode = 200; // 状态码：默认200 OK
    private String statusMsg = "OK"; // 状态描述：默认OK
    private String contentType = "text/html; charset=utf-8"; // MIME类型：默认HTML
    private String body = ""; // 响应体内容
    private Map<String, String> headers = new HashMap<>(); // 响应头：key-value格式

    // Getter & Setter（直接使用，无需修改）
    public int getStatusCode() { return statusCode; }
    public void setStatusCode(int statusCode) { this.statusCode = statusCode; }
    public String getStatusMsg() { return statusMsg; }
    public void setStatusMsg(String statusMsg) { this.statusMsg = statusMsg; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public Map<String, String> getHeaders() { return headers; }
    public void addHeader(String key, String value) { this.headers.put(key, value); }
}