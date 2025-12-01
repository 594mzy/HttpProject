package client;

import java.util.HashMap;
import java.util.Map;
import common.UrlParser; // 用于解析当前 URL，以便把相对 Location 转成绝对 URL
import java.net.URISyntaxException;

/**
 * 重定向处理器：自动处理 301/302 重定向响应
 */
public class RedirectHandler {
    // 最大重定向次数（避免死循环，比如 A→B→A）
    private static final int MAX_REDIRECT_COUNT = 5;
    // 依赖你写的 HttpClient
    private final HttpClient httpClient;

    // 构造方法：初始化 HttpClient
    public RedirectHandler() {
        this.httpClient = new HttpClient();
    }

    /**
     * 发送 GET 请求，自动处理重定向
     * @param url 初始请求 URL
     * @return 最终的非重定向响应
     */
    public String sendGetWithRedirect(String url) {
        return sendWithRedirect(url, null, "GET");
    }

    /**
     * 发送 POST 请求，自动处理重定向
     * @param url 初始请求 URL
     * @param params POST 参数
     * @return 最终的非重定向响应
     */
    public String sendPostWithRedirect(String url, Map<String, String> params) {
        return sendWithRedirect(url, params, "POST");
    }

    /**
     * 核心重定向处理逻辑（封装 GET/POST 通用逻辑）
     * @param url 请求 URL
     * @param params POST 参数（GET 传 null）
     * @param method 请求方法（GET/POST）
     * @return 最终响应
     */
    private String sendWithRedirect(String url, Map<String, String> params, String method) {
        // 记录重定向次数，超过最大值则停止
        int redirectCount = 0;
        String currentUrl = url;
        String response;

        while (redirectCount < MAX_REDIRECT_COUNT) {
            // 1. 发送当前 URL 的请求
            if ("GET".equalsIgnoreCase(method)) {
                response = httpClient.sendGet(currentUrl);
            } else if ("POST".equalsIgnoreCase(method)) {
                response = httpClient.sendPost(currentUrl, params);
            } else {
                return "不支持的请求方法：" + method;
            }

            // 2. 解析响应状态码
            int statusCode = parseStatusCode(response);
            // 3. 如果不是重定向，直接返回响应
            if (statusCode != 301 && statusCode != 302) {
                return response;
            }

            // 4. 如果是重定向，解析 Location 响应头（新 URL）
            String newUrl = parseLocationHeader(response);
            if (newUrl == null || newUrl.isEmpty()) {
                return "重定向响应缺少 Location 头：\n" + response;
            }

            // 处理相对 Location（如 "/static/index.html"） → 转为绝对 URL
            if (newUrl.startsWith("/")) {
                try {
                    UrlParser.UrlInfo info = UrlParser.parse(currentUrl);
                    String base = info.scheme + "://" + info.host + ":" + info.port;
                    newUrl = base + newUrl;
                } catch (URISyntaxException e) {
                    return "无法解析当前 URL 以生成绝对重定向地址：" + currentUrl;
                }
            } else if (newUrl.startsWith("//")) {
                // 协议相对 URL（//example.com/path）
                try {
                    UrlParser.UrlInfo info = UrlParser.parse(currentUrl);
                    newUrl = info.scheme + ":" + newUrl;
                } catch (URISyntaxException e) {
                    return "无法解析当前 URL（协议相对重定向）：" + currentUrl;
                }
            }

            // 5. 更新当前 URL，准备下一次请求
            currentUrl = newUrl;
            redirectCount++;
            System.out.println("自动重定向：" + url + " → " + newUrl + "（第 " + redirectCount + " 次）");
        }

        // 超过最大重定向次数，返回错误
        return "超过最大重定向次数（" + MAX_REDIRECT_COUNT + "次），可能存在循环重定向！";
    }

    /**
     * 从响应中解析状态码（比如从 "HTTP/1.1 302 Found" 提取 302）
     * @param response 服务器返回的完整响应
     * @return 状态码（解析失败返回 -1）
     */
    private int parseStatusCode(String response) {
        if (response == null || response.isEmpty()) {
            return -1;
        }
        // 响应行是第一行，按空格拆分
        String[] responseLines = response.split("\r\n");
        if (responseLines.length == 0) {
            return -1;
        }
        String statusLine = responseLines[0]; // 比如 "HTTP/1.1 302 Found"
        String[] parts = statusLine.split(" ");
        if (parts.length < 2) {
            return -1;
        }
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * 从响应头中解析 Location 字段（重定向的新 URL）
     * @param response 服务器返回的完整响应
     * @return Location 值（null 表示未找到）
     */
    private String parseLocationHeader(String response) {
        if (response == null || response.isEmpty()) {
            return null;
        }
        String[] responseLines = response.split("\r\n");
        // 遍历响应头（空行前的部分）
        for (String line : responseLines) {
            line = line.trim();
            // 匹配 Location 头（忽略大小写）
            if (line.toLowerCase().startsWith("location:")) {
                // 提取 Location 后的内容（去掉 "Location:" 前缀，trim 去空格）
                return line.substring("location:".length()).trim();
            }
        }
        return null;
    }

    // 测试方法：验证重定向功能
    public static void main(String[] args) {
        RedirectHandler redirectHandler = new RedirectHandler();

        // 测试 GET 重定向（假设服务器配置 / → /static/index.html 302 重定向）
        String getRedirectResponse = redirectHandler.sendGetWithRedirect("http://localhost:8080/");
        System.out.println("GET 重定向最终响应:\n" + getRedirectResponse);

        // 测试 POST 重定向（可选，POST 重定向较少见）
        // 这个我感觉没必要测试了
//        Map<String, String> params = new HashMap<>();
//        params.put("username", "test");
//        params.put("password", "123456");
//        String postRedirectResponse = redirectHandler.sendPostWithRedirect("http://localhost:8080/old-login", params);
//        System.out.println("POST 重定向最终响应:\n" + postRedirectResponse);
    }
}
