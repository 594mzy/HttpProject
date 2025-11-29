package client;

// 导入公共模块的Response实体类：用于封装解析后的HTTP响应数据（状态行、头信息、响应体等）
import common.Response;

// 导入标准字符集：HTTP协议默认使用UTF-8编码，用于字节数组与字符串的转换
import java.nio.charset.StandardCharsets;

/**
 * HTTP响应解析器：将服务器返回的原始字节数组解析为结构化的Response对象
 * 输入格式要求：<响应头（含状态行）+ CRLFCRLF（头体分隔符）+ 响应体字节>
 * 核心功能：解析状态行（协议版本、状态码、原因短语）、响应头（键值对）、响应体（原始字节）
 */
public class ResponseParser {
    /**
     * 核心解析方法：将原始响应字节数组转为Response对象
     * @param raw 服务器返回的原始响应字节数组（包含完整的响应头和响应体）
     * @return 解析后的Response对象（空输入返回空响应）
     */
    public static Response parse(byte[] raw) {
        // 初始化响应对象：用于存储解析后的所有响应数据
        Response resp = new Response();
        // 空值校验：若原始字节数组为null或长度为0，直接返回空响应（避免空指针异常）
        if (raw == null || raw.length == 0) return resp;

        // 1. 分割响应头和响应体：找到头体分隔符CRLFCRLF（\r\n\r\n）
        // 先将原始字节转为UTF-8字符串（响应头是文本格式，可直接解析）
        String rawStr = new String(raw, StandardCharsets.UTF_8);
        // 查找CRLFCRLF的起始索引（分隔响应头和响应体的标志）
        int idx = rawStr.indexOf("\r\n\r\n");

        // 存储解析后的响应头字符串和响应体字节数组
        String headerStr;
        byte[] body = new byte[0];

        // 若找到分隔符（正常HTTP响应格式）
        if (idx != -1) {
            // 截取响应头字符串：从开头到CRLFCRLF结束（含分隔符本身，共4个字节）
            headerStr = rawStr.substring(0, idx + 4);
            // 计算响应体在原始字节数组中的起始位置：响应头字符串的UTF-8字节长度
            // 注意：必须用headerStr的字节长度计算，而非字符串长度（避免UTF-8多字节字符导致偏移错误）
            int bodyStart = headerStr.getBytes(StandardCharsets.UTF_8).length;
            // 若响应体起始位置小于原始字节数组长度（说明存在响应体）
            if (bodyStart < raw.length) {
                // 计算响应体长度：原始字节长度 - 响应体起始位置
                int len = raw.length - bodyStart;
                // 初始化响应体字节数组
                body = new byte[len];
                // 从原始字节数组中拷贝响应体数据（从bodyStart开始，拷贝len个字节）
                System.arraycopy(raw, bodyStart, body, 0, len);
            }
        } else {
            // 未找到分隔符（异常格式，如仅返回响应头无响应体）：将所有数据视为响应头
            headerStr = rawStr;
        }

        // 2. 解析响应头（含状态行）：按\r\n分割响应头字符串，得到每行数据
        String[] lines = headerStr.split("\r\n");
        // 若分割后有数据（至少包含状态行）
        if (lines.length > 0) {
            // 第一行是状态行（格式：HTTP/1.1 200 OK）
            String statusLine = lines[0];
            // 将状态行设置到响应对象
            resp.setStatusLine(statusLine);
            // 分割状态行：最多分割3部分（避免原因短语含空格的情况，如"500 Internal Server Error"）
            String[] parts = statusLine.split(" ", 3);

            // 解析状态码（parts[1]是状态码字符串，如"200"）
            if (parts.length >= 2) {
                try {
                    // 将字符串状态码转为整数，设置到响应对象
                    resp.setStatusCode(Integer.parseInt(parts[1]));
                } catch (NumberFormatException ignored) {
                    // 忽略数字格式异常（如状态码非法），状态码保持默认值
                }
            }

            // 解析原因短语（parts[2]是原因短语，如"OK"、"Not Found"）
            if (parts.length >= 3) {
                resp.setReasonPhrase(parts[2]);
            }
        }

        // 3. 解析响应头字段（从第2行开始，每行是一个头字段，格式：Key: Value）
        for (int i = 1; i < lines.length; i++) {
            // 获取当前头字段行
            String line = lines[i];
            // 跳过空行或纯空白行（响应头中可能存在空行，尤其是分隔符前的冗余空行）
            if (line == null || line.trim().isEmpty()) continue;

            // 查找冒号（分隔头名和头值的标志）
            int colon = line.indexOf(':');
            // 若没有冒号，视为非法头字段，跳过
            if (colon == -1) continue;

            // 截取头名：冒号前的部分，去除前后空格（如"Content-Type"）
            String name = line.substring(0, colon).trim();
            // 截取头值：冒号后的部分，去除前后空格（如"text/html;charset=UTF-8"）
            String value = line.substring(colon + 1).trim();
            // 将头字段设置到响应对象（键值对形式存储）
            resp.setHeader(name, value);
        }

        // 4. 设置响应体到响应对象
        resp.setBody(body);
        // 返回解析完成的Response对象
        return resp;
    }
}