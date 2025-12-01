package client;

import common.BIOTransport;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class HttpClient {
    // 维护一个 BIOTransport 映射（按 host:port 区分不同服务的 transport 池）
    private final Map<String, BIOTransport> transports = new HashMap<>();
    // 连接池最大容量
    private static final int MAX_POOL_SIZE = 10;

    /**
     * 发送 GET 请求
     * 
     * @param url 目标 URL（如 http://localhost:8080/login?name=test）
     * @return 服务器返回的完整响应（响应头 + 响应体）
     */
    public String sendGet(String url) {
        BIOTransport transport = null;
        String key = null;
        try {
            // 1. 解析 URL，获取 host、port、uri
            Map<String, String> urlInfo = parseUrl(url);
            String host = urlInfo.get("host");// 主机：localhost
            int port = Integer.parseInt(urlInfo.get("port"));// 端口：8080
            String uri = urlInfo.get("uri");// 路径+参数
            key = host + ":" + port;
            // 2. 创建或获取 BIOTransport（内部维护 SocketPool）
            transports.putIfAbsent(key, new BIOTransport(host, port, MAX_POOL_SIZE));
            transport = transports.get(key);
            // 3. 构建 GET 请求头（按 HTTP 规范，每行以 \r\n 结尾，最后加空行）
            StringBuilder request = new StringBuilder();
            request.append("GET ").append(uri).append(" HTTP/1.1\r\n"); // 请求行
            request.append("Host: ").append(host).append(":").append(port).append("\r\n"); // Host 头
            request.append("Connection: keep-alive\r\n"); // 告诉服务器处理完关闭连接
            request.append("\r\n"); // 空行表示请求头结束
            // 4-6. 使用 BIOTransport 发送并读取完整响应（内部在同一 socket 上完成）
            String response = transport.sendRequest(request.toString().getBytes());
            return response;

        } catch (Exception e) {
            // 如果 transport 可用，可考虑关闭 transport 对应的连接池以清理异常状态
            if (transport != null) {
                try {
                    transport.close();
                } catch (Exception ignored) {
                }
                if (key != null)
                    transports.remove(key);
            }
            e.printStackTrace();
            return "请求失败：" + e.getMessage();
        }
    }

    /**
     * 发送 POST 请求
     * 
     * @param url    目标 URL（如 http://localhost:8080/register）
     * @param params POST 参数（如 {name: "test", pwd: "123"}）
     * @return 服务器返回的完整响应
     */
    public String sendPost(String url, Map<String, String> params) {
        BIOTransport transport = null;
        String key = null;
        try {
            // 1. 解析 URL
            Map<String, String> urlInfo = parseUrl(url);
            String host = urlInfo.get("host");
            int port = Integer.parseInt(urlInfo.get("port"));
            String uri = urlInfo.get("uri");
            key = host + ":" + port;
            // 2. 拼接 POST 参数（表单格式：name=test&pwd=123）
            String postBody = buildPostParams(params);

            // 3. 创建 Socket 连接
            transports.putIfAbsent(key, new BIOTransport(host, port, MAX_POOL_SIZE));
            transport = transports.get(key);

            // 4. 构建 POST 请求头
            StringBuilder request = new StringBuilder();
            request.append("POST ").append(uri).append(" HTTP/1.1\r\n"); // 请求行
            request.append("Host: ").append(host).append(":").append(port).append("\r\n");
            request.append("Content-Type: application/x-www-form-urlencoded\r\n"); // 表单类型
            request.append("Content-Length: ").append(postBody.getBytes().length).append("\r\n"); // 参数长度
            request.append("Connection: keep-alive\r\n");
            request.append("\r\n"); // 空行分隔请求头和请求体
            request.append(postBody); // 请求体（参数），即客户需要修改或提交的内容（比如登录场景下的，账号密码）

            // 使用 BIOTransport 在同一 socket 上发送并读取完整响应
            String response = transport.sendRequest(request.toString().getBytes());
            return response;

        } catch (Exception e) {
            if (transport != null) {
                try {
                    transport.close();
                } catch (Exception ignored) {
                }
                if (key != null)
                    transports.remove(key);
            }
            e.printStackTrace();
            return "请求失败：" + e.getMessage();
        }
    }

    /**
     * 关闭所有连接池
     */
    public void closeAllPools() {
        for (BIOTransport t : transports.values()) {
            t.close();
        }
        transports.clear();
    }

    /**
     * 解析 URL，提取 host、port、uri
     * 示例：http://localhost:8080/login?name=test → host=localhost, port=8080,
     * uri=/login?name=test
     */
    private Map<String, String> parseUrl(String url) {
        Map<String, String> result = new HashMap<>();
        // 去掉协议头（http://）
        String urlWithoutProtocol = url.replace("http://", "");
        // 拆分主机部分和路径（按第一个 "/" 分割）
        int firstSlashIndex = urlWithoutProtocol.indexOf("/");
        String hostPortPart;
        String uri;
        if (firstSlashIndex == -1) {
            // 没有路径，默认 uri 为 /
            hostPortPart = urlWithoutProtocol;
            uri = "/";
        } else {
            hostPortPart = urlWithoutProtocol.substring(0, firstSlashIndex);
            uri = urlWithoutProtocol.substring(firstSlashIndex);
        }
        // 拆分主机和端口（默认端口 80）
        int colonIndex = hostPortPart.indexOf(":");
        String host;
        int port;
        if (colonIndex == -1) {
            host = hostPortPart;
            port = 80; // HTTP 默认端口
        } else {
            host = hostPortPart.substring(0, colonIndex);
            port = Integer.parseInt(hostPortPart.substring(colonIndex + 1));
        }
        // 存入结果
        result.put("host", host);
        result.put("port", String.valueOf(port));
        result.put("uri", uri);
        return result;
    }

    /**
     * 拼接 POST 参数为表单格式（name=test&pwd=123）
     */
    private String buildPostParams(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        Set<Map.Entry<String, String>> entries = params.entrySet();
        for (Map.Entry<String, String> entry : entries) {
            sb.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
        }
        // 去掉最后一个 "&"
        if (sb.length() > 0) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }

    // 测试方法：运行后可验证功能
    public static void main(String[] args) {
        HttpClient client = new HttpClient();

        /* ---------- 1. 测试 Keep-Alive：连续发 3 次 GET ---------- */
        for (int i = 0; i < 3; i++) {
            long t0 = System.currentTimeMillis();
            String resp = client.sendGet("http://localhost:8080/static/index.html");
            long t1 = System.currentTimeMillis();
            System.out.println("GET req #" + i + " 耗时 " + (t1 - t0) + " ms");
            System.out.println("GET resp len=" + (resp == null ? 0 : resp.length()));
        }

        /* ---------- 2. 顺手测一次 POST（不改也行，可选） ---------- */
        Map<String, String> params = new HashMap<>();
        params.put("username", "test");
        params.put("password", "123456");
        long t2 = System.currentTimeMillis();
        String postResp = client.sendPost("http://localhost:8080/user/register", params);
        long t3 = System.currentTimeMillis();
        System.out.println("POST req  耗时 " + (t3 - t2) + " ms");
        System.out.println("POST resp len=" + (postResp == null ? 0 : postResp.length()));

        /* ---------- 3. 程序结束前统一释放连接池 ---------- */
        client.closeAllPools();
    }
}