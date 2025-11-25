package common;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class BIOTransport {
    private final SocketPool socketPool;

    public BIOTransport(SocketPool socketPool) {
        this.socketPool = socketPool;
    }

    /**
     * 发送请求字节数组，并接收完整的响应字节数组。
     *正确分离响应头和响应体。
     * 支持 Content-Length 和 chunked 两种传输方式。
     * 正确处理无响应体的状态码。
     * 根据响应头管理 Keep-Alive 连接。
     */
    public byte[] send(byte[] requestBytes) throws IOException, InterruptedException { //  公共方法，对外提供发送请求并接收响应的功能。
        //     - 参数: requestBytes，要发送的请求数据（字节数组）。
        //     - 返回值: 服务器响应的完整数据（字节数组）。
        //     - 异常: IOException (I/O操作失败), InterruptedException (线程等待被中断)。
        Socket socket = null; //
        OutputStream out = null; //
        InputStream in = null; // 。
        String responseHeader = ""; //  声明字符串变量，用于存储原始响应头，以便在finally块中判断连接策略。

        try {
            socket = socketPool.getConnection(); // 从连接池获取一个Socket连接。
            out = socket.getOutputStream(); //  获取Socket的输出流，用于发送数据。
            in = socket.getInputStream(); // 获取Socket的输入流，用于接收数据。

            out.write(requestBytes); // 将请求字节数组写入输出流。
            out.flush(); // 强制刷新输出流，确保数据立即发送出去。

            Map<String, String> headers = readHeaders(in); // 调用辅助方法readHeaders，读取并解析响应头。
            responseHeader = (headers.get("rawHeaders")); // 从解析结果中获取原始响应头字符串。

            int statusCode = Integer.parseInt(headers.get("statusLine").split(" ")[1]); // 解析状态码。
            //     - headers.get("statusLine") 获取响应行，如 "HTTP/1.1 200 OK"。
            //     - split(" ")[1] 分割字符串并取第二个元素 "200"。
            String transferEncoding = headers.getOrDefault("transfer-encoding", "").toLowerCase(); // 获取Transfer-Encoding头，并转为小写。
            String connection = headers.getOrDefault("connection", "keep-alive").toLowerCase(); // 获取Connection头，默认值为"keep-alive"，并转为小写。

            ByteArrayOutputStream bodyBaos = new ByteArrayOutputStream(); // 创建字节数组输出流，用于存储响应体。
            if (shouldHaveBody(statusCode, headers)) { // 调用辅助方法判断响应是否应该包含响应体。
                if ("chunked".equals(transferEncoding)) { // 如果是分块传输编码。
                    readChunkedBody(in, bodyBaos); // 调用辅助方法读取分块响应体。
                } else { // 否则，按固定长度或流结束处理。
                    int contentLength = Integer.parseInt(headers.getOrDefault("content-length", "-1")); // 获取Content-Length头，默认值为"-1"。
                    readFixedLengthBody(in, bodyBaos, contentLength); // 调用辅助方法读取固定长度响应体。
                }
            }

            ByteArrayOutputStream fullResponseBaos = new ByteArrayOutputStream(); // 创建字节数组输出流，用于组合完整响应。
            fullResponseBaos.write(responseHeader.getBytes()); // 写入响应头。
            fullResponseBaos.write(bodyBaos.toByteArray()); // 写入响应体。

            return fullResponseBaos.toByteArray(); // 返回完整的响应字节数组。

        } finally { // finally块，无论try块是否发生异常，此处代码必定执行，用于资源清理。
            if (socket != null) { // 如果Socket不为null。
                Map<String, String> finalHeaders = parseHeaders(responseHeader); // 再次解析响应头（为了在finally中也能获取）。
                boolean keepAlive = "keep-alive".equals(finalHeaders.getOrDefault("connection", "keep-alive").toLowerCase()); //  判断是否需要保持连接。
                if (keepAlive) { //如果是长连接。
                    socketPool.releaseConnection(socket); //将Socket归还给连接池。
                } else {
                    socket.close(); //关闭Socket连接。
                }
            }
        }
    }

    /**
     * 读取并解析响应头，直到遇到 \r\n\r\n。
     * @return 一个Map，包含解析后的头信息和原始头字节。
     */
    private Map<String, String> readHeaders(InputStream in) throws IOException { //读取并解析响应头。
        Map<String, String> headers = new HashMap<>(); //创建Map用于存储解析结果。
        ByteArrayOutputStream headerBaos = new ByteArrayOutputStream(); // 创建字节数组输出流，用于存储原始响应头字节。

        byte[] buffer = new byte[1]; // 创建一个1字节的缓冲区，用于逐字节读取。
        int consecutiveNewlines = 0; // 计数器，用于检测连续的换行符。
        String statusLine = null; // 用于存储响应行。

        while (in.read(buffer) != -1) { // 循环读取，直到流结束（-1）。
            headerBaos.write(buffer); // 将读取到的字节写入headerBaos。
            byte b = buffer[0]; // 获取当前字节。

            if (b == '\r') { //  如果是回车符\r。
                continue; //  忽略，因为我们只关心换行符\n。
            }

            if (b == '\n') { //  如果是换行符\n。
                consecutiveNewlines++; //  连续换行符计数加1。
                if (consecutiveNewlines == 2) { // 如果连续出现两个换行符。
                    break; //  说明响应头结束，跳出循环。
                }
                if (statusLine == null) { // 如果响应行还未设置。
                    statusLine = headerBaos.toString().trim(); //  读取并设置响应行（第一次遇到\n时）。
                }
            } else {
                consecutiveNewlines = 0; // 重置连续换行符计数器。
            }
        }

        headers.put("statusLine", statusLine); // 将响应行存入Map。
        headers.putAll(parseHeaders(headerBaos.toString())); //  调用parseHeaders解析响应头字段，并存入Map。
        headers.put("rawHeaders", String.valueOf(headerBaos)); //  将原始响应头字节流存入Map。

        return headers;
    }
    /**
     * 解析响应头字符串为键值对。
     */
    private Map<String, String> parseHeaders(String headerStr) {//将响应头字符串解析为键值对。
        Map<String, String> headers = new HashMap<>(); // 创建Map用于存储解析结果。
        String[] lines = headerStr.split("\r\n"); // 按\r\n分割字符串，得到每行。
        for (int i = 1; i < lines.length; i++) { // 从第1行开始遍历（跳过第0行的响应行）。
            String line = lines[i].trim(); // 去除行首尾空格。
            if (line.isEmpty()) continue; //  如果是空行，跳过。
            int colonIndex = line.indexOf(':'); // 查找冒号:的位置。
            if (colonIndex != -1) { //  如果找到了冒号。
                String key = line.substring(0, colonIndex).trim().toLowerCase(); // 提取键，并转为小写。
                String value = line.substring(colonIndex + 1).trim(); // 提取值，并去除首尾空格。
                headers.put(key, value); // 将键值对存入Map。
            }
        }
        return headers;
    }

    /**
     * 判断响应是否应该包含响应体。
     * RFC 规范：https://datatracker.ietf.org/doc/html/rfc9110#section-3.3.1
     */
    private boolean shouldHaveBody(int statusCode, Map<String, String> headers) { // 判断是否应该有响应体。
        if (statusCode >= 100 && statusCode < 200 || statusCode == 204 || statusCode == 304) { // 根据RFC规范，这些状态码一定没有响应体。
            return false;
        }
        String transferEncoding = headers.getOrDefault("transfer-encoding", "").toLowerCase(); //  获取Transfer-Encoding头。
        if ("chunked".equals(transferEncoding)) { // 如果是分块编码。
            return true;
        }
        if (headers.containsKey("content-length")) { // 如果包含Content-Length头。
            try {
                return Integer.parseInt(headers.get("content-length")) > 0; //  如果值大于0，返回true。
            } catch (NumberFormatException e) {
                return true; // 如果格式错误，也认为有响应体。
            }
        }
        return true; // 其他情况（如200 OK且无Content-Length），默认认为有响应体。
    }

    /**
     * 读取固定长度的响应体（Content-Length）。
     */
    private void readFixedLengthBody(InputStream in, ByteArrayOutputStream bodyBaos, int contentLength) throws IOException { // 读取固定长度响应体。
        if (contentLength == -1) { //  如果没有Content-Length头。
            byte[] buffer = new byte[8192]; // 创建8KB缓冲区。
            int len;
            while ((len = in.read(buffer)) != -1) { // 循环读取直到流结束。
                bodyBaos.write(buffer, 0, len); // 写入bodyBaos。
            }
        } else { // 如果有Content-Length头。
            byte[] buffer = new byte[8192]; // 创建8KB缓冲区。
            int bytesRead = 0; //已读取字节数计数器。
            while (bytesRead < contentLength) { // 循环直到读取足够的字节。
                int bytesToRead = Math.min(buffer.length, contentLength - bytesRead); // 计算本次最多能读取的字节数。
                int len = in.read(buffer, 0, bytesToRead); //  读取指定长度的字节。
                if (len == -1) { //如果提前读到流结束。
                    throw new IOException("Unexpected end of stream while reading fixed-length body."); // 抛出异常。
                }
                bodyBaos.write(buffer, 0, len); //写入bodyBaos。
                bytesRead += len; //更新已读取字节数。
            }
        }
    }
    /**
     * 读取分块编码的响应体（Transfer-Encoding: chunked）。
     */
    private void readChunkedBody(InputStream in, ByteArrayOutputStream bodyBaos) throws IOException {
        ByteArrayOutputStream chunkLineBaos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1]; // 使用1字节缓冲区进行精细控制
        int state = 0; // 状态机：0-读块长度, 1-读块数据, 2-读块后CRLF, 3-读Trailers。

        // **优化点 1：引入变量来跟踪当前块的大小和已读取字节数**
        int currentChunkSize = 0;
        int bytesReadInChunk = 0;

        while (true) {
            int b = in.read();
            if (b == -1) {
                throw new IOException("Unexpected end of stream while reading chunked body.");
            }

            if (state == 0) { // 状态0: 读取块长度行
                if (b == '\r') continue;
                if (b == '\n') {
                    String chunkSizeHex = chunkLineBaos.toString().trim();
                    chunkLineBaos.reset();
                    currentChunkSize = Integer.parseInt(chunkSizeHex, 16); // 解析出块大小

                    if (currentChunkSize == 0) {
                        state = 3; // 块长度为0，准备读取Trailers
                        continue;
                    }

                    // 切换到状态1之前，重置块字节计数器
                    bytesReadInChunk = 0;
                    state = 1; // 准备读取块数据
                } else {
                    chunkLineBaos.write(b);
                }
            } else if (state == 1) { // 读取块数据

                bodyBaos.write(b);
                bytesReadInChunk++;

                // 检查是否已经读取了当前块的所有字节
                if (bytesReadInChunk == currentChunkSize) {
                    state = 2; // 块数据读取完毕，切换到状态2读取CRLF
                }

            } else if (state == 2) { // 状态2: 读取块数据后的CRLF
                if (b == '\r') continue;
                if (b == '\n') {
                    state = 0; // CRLF读取完毕，切换回状态0准备读取下一个块
                }
            } else if (state == 3) { // 状态3: 忽略Trailers，直到遇到结束的CRLF
                if (b == '\r') continue;
                if (b == '\n') {
                    break;
                }
            }
        }
    }
}
