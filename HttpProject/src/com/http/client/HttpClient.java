package client;

import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class HttpClient {

    /**
     * 发送 GET 请求
     * @param url 目标 URL（如 http://localhost:8080/login?name=test）
     * @return 服务器返回的完整响应（响应头 + 响应体）
     */
    public String sendGet(String url) {
        try {
            // 1. 解析 URL，获取 host、port、uri
            Map<String, String> urlInfo = parseUrl(url);
            String host = urlInfo.get("host");//主机：localhost
            int port = Integer.parseInt(urlInfo.get("port"));//端口：8080
            String uri = urlInfo.get("uri");//路径+参数

            // 2. 创建 Socket 连接服务器
            Socket socket = new Socket(host, port);

            // 3. 构建 GET 请求头（按 HTTP 规范，每行以 \r\n 结尾，最后加空行）
            StringBuilder request = new StringBuilder();
            request.append("GET ").append(uri).append(" HTTP/1.1\r\n"); // 请求行
            request.append("Host: ").append(host).append(":").append(port).append("\r\n"); // Host 头
            request.append("Connection: close\r\n"); // 告诉服务器处理完关闭连接
            request.append("\r\n"); // 空行表示请求头结束

            // 4. 发送请求
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write(request.toString().getBytes());
            outputStream.flush();

            // 5. 接收响应
            String response = readResponse(socket.getInputStream());

            // 6. 关闭连接
            socket.close();
            return response;

        } catch (Exception e) {
            e.printStackTrace();
            return "请求失败：" + e.getMessage();
        }
    }

    /**
     * 发送 POST 请求
     * @param url 目标 URL（如 http://localhost:8080/register）
     * @param params POST 参数（如 {name: "test", pwd: "123"}）
     * @return 服务器返回的完整响应
     */
    public String sendPost(String url, Map<String, String> params) {
        try {
            // 1. 解析 URL
            Map<String, String> urlInfo = parseUrl(url);
            String host = urlInfo.get("host");
            int port = Integer.parseInt(urlInfo.get("port"));
            String uri = urlInfo.get("uri");

            // 2. 拼接 POST 参数（表单格式：name=test&pwd=123）
            String postBody = buildPostParams(params);

            // 3. 创建 Socket 连接
            Socket socket = new Socket(host, port);

            // 4. 构建 POST 请求头
            StringBuilder request = new StringBuilder();
            request.append("POST ").append(uri).append(" HTTP/1.1\r\n"); // 请求行
            request.append("Host: ").append(host).append(":").append(port).append("\r\n");
            request.append("Content-Type: application/x-www-form-urlencoded\r\n"); // 表单类型
            request.append("Content-Length: ").append(postBody.getBytes().length).append("\r\n"); // 参数长度
            request.append("Connection: close\r\n");
            request.append("\r\n"); // 空行分隔请求头和请求体
            request.append(postBody); // 请求体（参数），即客户需要修改或提交的内容（比如登录场景下的，账号密码）

            // 5. 发送请求
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write(request.toString().getBytes());
            outputStream.flush();

            // 6. 接收响应
            String response = readResponse(socket.getInputStream());

            // 7. 关闭连接
            socket.close();
            return response;

        } catch (Exception e) {
            e.printStackTrace();
            return "请求失败：" + e.getMessage();
        }
    }

    /**
     * 解析 URL，提取 host、port、uri
     * 示例：http://localhost:8080/login?name=test → host=localhost, port=8080, uri=/login?name=test
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

    /**
     * 从输入流读取服务器响应，转成字符串
     */
    private String readResponse(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder response = new StringBuilder();
        String line;
        // 逐行读取响应（包括响应头和响应体）
        while ((line = reader.readLine()) != null) {
            response.append(line).append("\r\n"); // 保留换行符
        }
        return response.toString();
    }

    // 测试方法：运行后可验证功能
    public static void main(String[] args) {
        HttpClient client = new HttpClient();

        // 测试 GET 请求（假设服务器在 localhost:8080 运行）
        String getResponse = client.sendGet("http://localhost:8080/static/index.html");
        System.out.println("GET 响应:\n" + getResponse);

        // 测试 POST 请求（模拟登录）
        Map<String, String> params = new HashMap<>();
        params.put("username", "test");
        params.put("password", "123456");
        String postResponse = client.sendPost("http://localhost:8080/user/login", params);
        System.out.println("POST 响应:\n" + postResponse);
    }
}