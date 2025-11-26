package server;

// 导入公共模块的Response实体类，用于封装要发送的HTTP响应数据
import common.Response;

// 导入IO异常类，处理流写入过程中的异常
import java.io.IOException;
// 导入输出流接口，作为响应数据的输出目标（通常是Socket的输出流）
import java.io.OutputStream;
// 导入标准字符集，用于HTTP协议默认的UTF-8编码转换（头部字符串转字节）
import java.nio.charset.StandardCharsets;
// 导入Map接口，用于遍历响应头的键值对
import java.util.Map;

/**
 * HTTP响应写入器：负责将封装好的Response对象完整写入输出流
 * 支持自动处理响应状态行、响应头（含Connection、Content-Length/Transfer-Encoding）
 * 支持两种响应体传输模式：固定长度（Content-Length）、分块传输（Chunked）
 * 自动格式化响应头名称为HTTP标准格式（首字母大写，连字符分隔）
 */
public class ResponseWriter {

    /**
     * 核心写入方法：将Response对象的状态行、头部、响应体依次写入输出流
     * 自动处理头部补充（Connection、Content-Length）和响应体传输编码
     * @param out 输出流（通常是Socket的输出流，用于向客户端发送响应）
     * @param resp 要发送的HTTP响应对象（封装状态码、头信息、响应体）
     * @param keepAlive 是否启用Keep-Alive长连接（决定Connection头的值）
     * @throws IOException 当流写入失败、连接中断时抛出
     */
    public static void write(OutputStream out, Response resp, boolean keepAlive) throws IOException {
        // 空值校验：若输出流或响应对象为null，直接返回（避免空指针异常）
        if (out == null || resp == null) return;

        // 1. 处理响应状态行（格式：HTTP/1.1 200 OK）
        // 从响应对象中获取预定义的状态行，若未设置则使用默认格式
        String statusLine = resp.getStatusLine();
        if (statusLine == null || statusLine.isEmpty()) {
            // 构建默认状态行：HTTP/1.1 + 状态码 + 原因短语（若为null则设为空字符串）
            statusLine = "HTTP/1.1 " + resp.getStatusCode() + " " + (resp.getReasonPhrase() == null ? "" : resp.getReasonPhrase());
        }

        // 2. 响应头准备：处理头部补充和传输编码判断
        // 获取响应对象中已设置的原始头字段（键值对）
        Map<String, String> headers = resp.getHeaders();
        // 判断是否已存在Content-Length头（避免重复设置）
        boolean hasContentLength = headers.containsKey("content-length");
        // 获取Transfer-Encoding头（判断是否使用分块传输）
        String transferEncoding = headers.get("transfer-encoding");
        // 获取响应体：若为null则转为空字节数组（避免后续处理空指针）
        byte[] body = resp.getBody() == null ? new byte[0] : resp.getBody();

        // 3. 构建完整的响应头字符串（含状态行+所有头字段+末尾分隔符）
        StringBuilder headerBuilder = new StringBuilder();
        // 先写入状态行（状态行是响应的第一行，必须放在最前面）
        headerBuilder.append(statusLine).append("\r\n");

        // 遍历已设置的原始头字段，写入响应头
        for (Map.Entry<String, String> e : headers.entrySet()) {
            String name = e.getKey();       // 头字段名（原始可能为小写，如content-type）
            String value = e.getValue();    // 头字段值
            // 跳过空键或空值的头字段（避免非法格式）
            if (name == null || value == null) continue;
            // 将头字段名格式化为HTTP标准格式（如content-type→Content-Type）
            String headerName = formatHeaderName(name);
            // 按"头名: 值\r\n"格式拼接
            headerBuilder.append(headerName).append(": ").append(value).append("\r\n");
        }

        // 4. 处理Connection头（若未手动设置则自动补充）
        if (!headers.containsKey("connection")) {
            // 根据keepAlive参数决定：长连接设为keep-alive，短连接设为close
            headerBuilder.append("Connection: ").append(keepAlive ? "keep-alive" : "close").append("\r\n");
        }

        // 5. 确定响应体传输模式：分块传输（Chunked）或固定长度
        boolean useChunked = false;
        // 若Transfer-Encoding头存在且值为chunked（忽略大小写和前后空格），则使用分块传输
        if (transferEncoding != null && "chunked".equalsIgnoreCase(transferEncoding.trim())) {
            useChunked = true;
        }

        // 若不使用分块传输，确保存在Content-Length头（避免客户端无法判断响应体结束）
        if (!useChunked) {
            if (!hasContentLength) {
                // 自动补充Content-Length：值为响应体的字节长度（仅当响应体已完整存在于内存时安全）
                headerBuilder.append("Content-Length: ").append(body.length).append("\r\n");
            }
        }

        // 6. 响应头结束标志：空行（\r\n），分隔头部和响应体
        headerBuilder.append("\r\n");

        // 7. 写入响应头到输出流（一次性写入所有头部，提升效率）
        out.write(headerBuilder.toString().getBytes(StandardCharsets.UTF_8));
        // 强制刷新输出流：确保头部数据立即发送到客户端（避免缓存导致的延迟）
        out.flush();

        // 8. 写入响应体到输出流（根据传输模式处理）
        if (useChunked) {
            // 分块传输模式：调用分块写入方法
            writeChunkedBody(out, body);
        } else {
            // 固定长度模式：若响应体非空，直接写入所有字节
            if (body.length > 0) out.write(body);
            // 刷新输出流：确保响应体数据立即发送
            out.flush();
        }
    }

    /**
     * 分块传输模式（Chunked）的响应体写入：遵循HTTP分块编码规范
     * 格式：每个分块 = 十六进制分块长度\r\n + 分块数据\r\n，最后以0\r\n\r\n结束
     * @param out 输出流（向客户端发送分块数据）
     * @param body 完整的响应体字节数组（需分割为多个分块）
     * @throws IOException 流写入失败时抛出
     */
    private static void writeChunkedBody(OutputStream out, byte[] body) throws IOException {
        // 定义分块大小：8192字节（平衡传输效率和内存占用，HTTP协议无强制要求，常用8KB/16KB）
        final int CHUNK_SIZE = 8192;
        // 偏移量：记录当前已写入的字节位置（从0开始）
        int offset = 0;

        // 循环分割响应体为多个分块，直到所有字节都写入
        while (offset < body.length) {
            // 计算当前分块的实际长度：取分块大小和剩余未写入字节数的较小值（最后一块可能不足8KB）
            int len = Math.min(CHUNK_SIZE, body.length - offset);
            // 构建分块长度行：十六进制字符串（Chunked编码要求）+ 换行符\r\n
            String sizeLine = Integer.toHexString(len) + "\r\n";
            // 写入分块长度（UTF-8编码，HTTP协议默认）
            out.write(sizeLine.getBytes(StandardCharsets.UTF_8));
            // 写入当前分块的数据：从offset位置开始，读取len字节
            out.write(body, offset, len);
            // 写入分块结束符：\r\n（每个分块数据后必须跟换行符）
            out.write("\r\n".getBytes(StandardCharsets.UTF_8));
            // 更新偏移量：移动到下一个分块的起始位置
            offset += len;
        }

        // 写入分块传输结束标志：0\r\n\r\n（0表示无更多分块，后续空行结束响应体）
        out.write("0\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        // 强制刷新：确保所有分块数据（包括结束标志）都发送到客户端
        out.flush();
    }

    /**
     * 格式化响应头名称为HTTP标准格式：单词首字母大写，连字符分隔（忽略原始大小写）
     * 例：content-type → Content-Type；connection → Connection；x-request-id → X-Request-Id
     * @param lowerName 原始头名称（通常为小写，来自Response的headersMap）
     * @return 格式化后的标准头名称，空输入返回空字符串
     */
    private static String formatHeaderName(String lowerName) {
        // 空值校验：若输入为null或空字符串，直接返回原输入（避免空指针）
        if (lowerName == null || lowerName.isEmpty()) return lowerName;

        // 按连字符"-"分割头名称（处理多单词组合，如x-request-id）
        String[] parts = lowerName.split("-");
        // 字符串构建器：拼接格式化后的每个部分
        StringBuilder sb = new StringBuilder();

        // 遍历每个分割后的部分，进行首字母大写处理
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            // 跳过空部分（处理连续连字符的极端情况，如"content--type"）
            if (p.isEmpty()) continue;
            // 首字母转为大写，后续字符保留原始大小写（HTTP头名称不区分大小写，标准格式为首字母大写）
            sb.append(Character.toUpperCase(p.charAt(0)));
            // 若部分长度>1，拼接剩余字符（如"type"→"Type"）
            if (p.length() > 1) sb.append(p.substring(1));
            // 除最后一个部分外，拼接连字符"-"（还原多单词分隔）
            if (i < parts.length - 1) sb.append('-');
        }

        // 返回格式化后的标准头名称
        return sb.toString();
    }
}