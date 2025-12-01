package server;

// 导入公共模块的Request实体类，用于封装解析后的HTTP请求数据
import common.Request;

// 导入字节数组输入流，用于处理内存中的字节数据作为输入源
import java.io.ByteArrayInputStream;
// 导入字节数组输出流，用于在内存中累积输出数据（如请求体、请求头）
import java.io.ByteArrayOutputStream;
// 导入IO异常类，处理流操作中的异常
import java.io.IOException;
// 导入输入流接口，作为请求数据的输入源
import java.io.InputStream;
// 导入序列输入流，用于拼接多个输入流（处理头部读取时多读的body部分）
import java.io.SequenceInputStream;
// 导入标准字符集，用于HTTP协议默认的UTF-8编码转换
import java.nio.charset.StandardCharsets;
// 导入哈希表，用于存储临时结果（头部数据+剩余字节）和解析后的请求头
import java.util.HashMap;
// 导入Map接口，用于定义键值对集合类型
import java.util.Map;

/**
 * HTTP请求解析器：负责从输入流中完整解析HTTP请求
 * 支持解析请求行、请求头、两种请求体编码（固定长度Content-Length / 分块传输Chunked）
 * 处理边界情况：头部读取时可能多读的body字节、流意外中断、非法请求格式
 */
public class RequestParser {

    /**
     * 核心解析方法：从输入流解析HTTP请求（阻塞直到头部完整读取，再按需读取请求体）
     * 
     * @param in 输入流（通常是Socket的输入流，承载客户端发送的HTTP请求数据）
     * @return 解析后的Request对象，包含请求方法、路径、版本、头信息、请求体
     * @throws IOException 当流读取失败、请求格式非法时抛出
     */
    public static Request parse(InputStream in) throws IOException {
        // 1. 读取请求头部（直到\r\n\r\n结束），返回结果包含原始头部字符串和可能多读的body字节
        // 原因：HTTP头和body之间用\r\n\r\n分隔，读取头部时可能会多读部分body，需要暂存后续使用
        Map<String, Object> headerResult = readHeaders(in);
        // 从结果中获取原始头部字符串（含请求行+所有头字段+末尾\r\n\r\n），默认空字符串
        String rawHeaders = (String) headerResult.getOrDefault("rawHeaders", "");
        // 从结果中获取读取头部时多读的body字节，默认空字节数组
        byte[] remaining = (byte[]) headerResult.getOrDefault("remaining", new byte[0]);

        // 2. 解析请求行和请求头：先将原始头部按\r\n分割成多行（每行是请求行或头字段）
        String[] lines = rawHeaders.split("\r\n");
        // 校验：如果分割后没有任何行，说明请求为空，抛出非法请求异常
        if (lines.length == 0)
            throw new IOException("Invalid HTTP request: empty headers");

        // 提取第一行作为请求行（格式：Method Path Version，如GET /index.html HTTP/1.1）
        String requestLine = lines[0];
        // 按空格分割请求行，最多分割3部分（避免路径或版本中含空格的异常情况）
        String[] reqParts = requestLine.split(" ", 3);
        // 校验：请求行必须包含方法、路径、版本三部分，否则非法
        if (reqParts.length < 3)
            throw new IOException("Invalid request line: " + requestLine);

        // 3. 初始化Request对象，封装请求行数据
        Request req = new Request();
        req.setMethod(reqParts[0]); // 设置请求方法（如GET、POST）
        req.setPath(reqParts[1]); // 设置请求路径（如/、/api/json）
        req.setVersion(reqParts[2]);// 设置HTTP协议版本（如HTTP/1.1）

        // 4. 解析请求头字段：将原始头部字符串转为键值对Map
        Map<String, String> headers = parseHeaders(rawHeaders);
        // 遍历解析后的头字段，设置到Request对象中
        for (Map.Entry<String, String> e : headers.entrySet()) {
            req.setHeader(e.getKey(), e.getValue());
        }

        // 5. 处理请求体输入流：如果读取头部时多读了部分body字节，需要拼接输入流
        // 原因：保证后续读取body时，先读取已缓存的remaining字节，再读原始输入流
        InputStream bodyIn = in;
        if (remaining != null && remaining.length > 0) {
            // 拼接：已缓存的字节流（remaining） + 原始输入流（in）
            bodyIn = new SequenceInputStream(new ByteArrayInputStream(remaining), in);
        }

        // 6. 读取请求体（根据请求头的传输编码类型处理）
        // 获取Transfer-Encoding头（转为小写，避免大小写敏感问题，HTTP头字段不区分大小写）
        String transferEncoding = headers.getOrDefault("transfer-encoding", "").toLowerCase();
        // 没有正文的头字段，直接返回
        if (!headers.containsKey("content-length") &&
                !"chunked".equalsIgnoreCase(headers.getOrDefault("transfer-encoding", ""))) {
            return req; // ← 读完头就结束，不再尝试读 body
        }
        // 情况1：分块传输编码（chunked），按chunked规则读取
        if ("chunked".equals(transferEncoding)) {
            // 用字节数组输出流累积分块数据
            ByteArrayOutputStream bodyBaos = new ByteArrayOutputStream();
            // 调用分块读取方法处理body
            readChunkedBody(bodyIn, bodyBaos);
            // 将累积的字节数组设为请求体
            req.setBody(bodyBaos.toByteArray());
        }
        // 情况2：固定长度编码（含Content-Length头），按指定长度读取
        else if (headers.containsKey("content-length")) {
            // 解析Content-Length值（可能解析失败，默认-1）
            int contentLength = -1;
            try {
                contentLength = Integer.parseInt(headers.get("content-length"));
            } catch (NumberFormatException ignored) {
                // 忽略数字格式异常，此时contentLength保持-1，不读取body
            }
            // 只有当长度大于0时，才读取固定长度的body
            if (contentLength > 0) {
                ByteArrayOutputStream bodyBaos = new ByteArrayOutputStream();
                // 调用固定长度读取方法处理body
                readFixedLengthBody(bodyIn, bodyBaos, contentLength);
                // 设置请求体
                req.setBody(bodyBaos.toByteArray());
            }
        }

        // 返回解析完整的Request对象
        return req;
    }

    /**
     * 读取HTTP请求头：从输入流读取数据，直到遇到\r\n\r\n（请求头结束标志）
     * 处理边界情况：可能会多读部分请求体字节，需一并返回供后续处理
     * 
     * @param in 输入流（Socket输入流）
     * @return Map集合，包含两个键：
     *         - rawHeaders: 完整的原始请求头字符串（含请求行+所有头字段+末尾\r\n\r\n）
     *         - remaining: 读取头部时多读的请求体字节（无则返回空数组）
     * @throws IOException 流读取失败时抛出
     */
    private static Map<String, Object> readHeaders(InputStream in) throws IOException {
        // 初始化返回结果Map
        Map<String, Object> result = new HashMap<>();
        // 字节数组输出流：累积读取的头部数据
        ByteArrayOutputStream headerBaos = new ByteArrayOutputStream();
        // 读取缓冲区：每次从流中读取1024字节（平衡效率和内存占用）
        byte[] buf = new byte[1024];
        // 记录每次读取的字节数
        int read;
        // 标记是否找到请求头结束标志（\r\n\r\n）
        boolean found = false;

        // 循环读取输入流数据（阻塞直到有数据或流关闭）
        while ((read = in.read(buf)) != -1) {
            // 将读取到的字节写入累积输出流
            headerBaos.write(buf, 0, read);
            // 获取当前已累积的所有字节数据
            byte[] soFar = headerBaos.toByteArray();
            // 查找\r\n\r\n在字节数组中的起始位置
            int pos = indexOfCRLFCRLF(soFar);

            // 如果找到结束标志
            if (pos != -1) {
                // 计算请求头结束位置（pos是\r\n\r\n的起始索引，+4是因为\r\n\r\n占4个字节）
                int headerEnd = pos + 4;
                // 将累积的字节数据中，从0到headerEnd的部分转为原始头部字符串（UTF-8编码）
                String headerStr = new String(soFar, 0, headerEnd, StandardCharsets.UTF_8);
                // 存入结果Map
                result.put("rawHeaders", headerStr);

                // 如果累积的字节数超过headerEnd，说明多读了部分请求体
                if (soFar.length > headerEnd) {
                    // 计算多读的字节长度
                    byte[] remaining = new byte[soFar.length - headerEnd];
                    // 拷贝多读的字节到remaining数组（从headerEnd开始，到数组末尾）
                    System.arraycopy(soFar, headerEnd, remaining, 0, remaining.length);
                    // 存入结果Map
                    result.put("remaining", remaining);
                } else {
                    // 没有多读，存入空数组
                    result.put("remaining", new byte[0]);
                }

                // 标记已找到结束标志，跳出循环
                found = true;
                break;
            }
        }

        // 如果循环结束仍未找到\r\n\r\n（流提前关闭）
        if (!found) {
            // 将已读取的所有数据作为原始头部（尽管不完整，尽量返回）
            String headerStr = headerBaos.toString(StandardCharsets.UTF_8.name());
            result.put("rawHeaders", headerStr);
            // 无多读的body，存入空数组
            result.put("remaining", new byte[0]);
        }

        // 返回结果（原始头部+可能的剩余字节）
        return result;
    }

    /**
     * 在字节数组中查找HTTP请求头结束标志：\r\n\r\n（4个连续字节）
     * 
     * @param data 要查找的字节数组（已累积的头部数据）
     * @return 找到则返回\r\n\r\n的起始索引，未找到返回-1
     */
    private static int indexOfCRLFCRLF(byte[] data) {
        // 循环遍历字节数组，最多遍历到倒数第4个字节（避免数组越界）
        for (int i = 0; i < data.length - 3; i++) {
            // 判断当前字节和后续3个字节是否依次为\r、\n、\r、\n
            if (data[i] == '\r' && data[i + 1] == '\n' && data[i + 2] == '\r' && data[i + 3] == '\n') {
                // 找到，返回起始索引i
                return i;
            }
        }
        // 遍历完未找到，返回-1
        return -1;
    }

    /**
     * 将原始请求头字符串解析为键值对Map（HTTP头字段）
     * 规则：HTTP头字段键不区分大小写，统一转为小写；值去除前后空格
     * 
     * @param headerStr 原始请求头字符串（含请求行+所有头字段+末尾\r\n\r\n）
     * @return 解析后的头字段Map（key：小写头名，value：头值）
     */
    private static Map<String, String> parseHeaders(String headerStr) {
        // 初始化头字段Map
        Map<String, String> headers = new HashMap<>();
        // 若输入字符串为空，直接返回空Map
        if (headerStr == null || headerStr.isEmpty())
            return headers;

        // 按\r\n分割字符串，得到每行数据（第一行是请求行，后续是头字段）
        String[] lines = headerStr.split("\r\n");
        // 遍历所有行：从索引1开始（跳过第一行请求行）
        for (int i = 1; i < lines.length; i++) {
            // 去除当前行的前后空格（处理可能的空白行或行首尾空格）
            String line = lines[i].trim();
            // 若行为空，跳过（可能是头部末尾的空行）
            if (line.isEmpty())
                continue;

            // 查找行中的冒号（HTTP头格式：Key: Value）
            int colonIndex = line.indexOf(':');
            // 若存在冒号，说明是合法的头字段行
            if (colonIndex != -1) {
                // 截取冒号前的部分作为头名，去除前后空格并转为小写（统一格式）
                String key = line.substring(0, colonIndex).trim().toLowerCase();
                // 截取冒号后的部分作为头值，去除前后空格
                String value = line.substring(colonIndex + 1).trim();
                // 存入Map（若有重复头名，后面的会覆盖前面的，简化处理）
                headers.put(key, value);
            }
        }

        // 返回解析后的头字段Map
        return headers;
    }

    /**
     * 读取固定长度的HTTP请求体（对应Content-Length头指定的长度）
     * 确保读取的字节数严格等于指定长度，避免少读或多读
     * 
     * @param in            输入流（可能是拼接后的SequenceInputStream）
     * @param bodyBaos      字节数组输出流：累积读取的请求体数据
     * @param contentLength 预期读取的字节长度（从Content-Length头解析）
     * @throws IOException 流读取失败、提前结束时抛出
     */
    private static void readFixedLengthBody(InputStream in, ByteArrayOutputStream bodyBaos, int contentLength)
            throws IOException {
        // 读取缓冲区：8192字节（较大缓冲区提升读取效率，适合文件上传等大请求体）
        byte[] buffer = new byte[8192];
        // 记录已读取的字节数
        int bytesRead = 0;

        // 循环读取，直到已读取字节数达到预期长度
        while (bytesRead < contentLength) {
            // 计算本次需要读取的字节数：取缓冲区大小和剩余未读长度的较小值
            // 避免最后一次读取时超出剩余长度
            int toRead = Math.min(buffer.length, contentLength - bytesRead);
            // 从输入流读取数据到缓冲区，返回实际读取的字节数
            int r = in.read(buffer, 0, toRead);

            // 若返回-1，说明流提前结束，抛出异常（未读满预期长度）
            if (r == -1)
                throw new IOException("Unexpected end of stream while reading fixed-length request body");

            // 将本次读取的字节写入累积输出流
            bodyBaos.write(buffer, 0, r);
            // 更新已读取字节数
            bytesRead += r;
        }
    }

    /**
     * 读取分块编码（Chunked）的HTTP请求体（对应Transfer-Encoding: chunked）
     * 遵循Chunked编码规则：分块长度（十六进制）\r\n 块数据\r\n ... 0\r\n trailers\r\n\r\n
     * 
     * @param in       输入流（可能是拼接后的SequenceInputStream）
     * @param bodyBaos 字节数组输出流：累积所有块数据，组成完整请求体
     * @throws IOException 流读取失败、编码格式非法时抛出
     */
    private static void readChunkedBody(InputStream in, ByteArrayOutputStream bodyBaos) throws IOException {
        // 循环读取每个分块，直到遇到长度为0的结束块
        while (true) {
            // 读取当前分块的长度行（十六进制字符串，可能含分号后的扩展信息）
            String line = readLine(in);
            // 若读取到null，说明流提前结束，抛出异常
            if (line == null)
                throw new IOException("Unexpected end of stream while reading chunk size");

            // 分割分块长度和扩展信息（分号后是扩展信息，可忽略）
            String chunkSizeHex = line.split(";", 2)[0].trim();
            // 声明分块长度变量
            int chunkSize;
            try {
                // 将十六进制字符串转为十进制整数（Chunked编码的长度是十六进制）
                chunkSize = Integer.parseInt(chunkSizeHex, 16);
            } catch (NumberFormatException e) {
                // 若转换失败，说明分块长度格式非法，抛出异常
                throw new IOException("Invalid chunk size: " + chunkSizeHex);
            }

            // 若分块长度为0，说明是结束块，退出循环
            if (chunkSize == 0) {
                // 读取并忽略尾部字段（trailers）：直到遇到空行（\r\n）
                while (true) {
                    String trailer = readLine(in);
                    // 流提前结束，抛出异常
                    if (trailer == null)
                        throw new IOException("Unexpected end of stream while reading chunked trailers");
                    // 遇到空行，说明尾部字段结束，跳出循环
                    if (trailer.isEmpty())
                        break;
                }
                // 结束分块读取，跳出外层循环
                break;
            }

            // 读取当前分块的数据（长度为chunkSize）
            // 记录当前分块剩余未读的字节数
            int remaining = chunkSize;
            // 读取缓冲区：8192字节
            byte[] buffer = new byte[8192];
            // 循环读取当前分块的所有数据
            while (remaining > 0) {
                // 本次读取长度：取缓冲区大小和剩余长度的较小值
                int toRead = Math.min(buffer.length, remaining);
                // 读取数据到缓冲区
                int r = in.read(buffer, 0, toRead);
                // 流提前结束，抛出异常
                if (r == -1)
                    throw new IOException("Unexpected end of stream while reading chunk data");

                // 将读取的数据写入累积输出流
                bodyBaos.write(buffer, 0, r);
                // 更新当前分块剩余未读长度
                remaining -= r;
            }

            // 消费分块数据后的\r\n（分块数据末尾必须跟\r\n，否则格式非法）
            String crlf = readLine(in);
            // 若读取失败，说明格式非法，抛出异常
            if (crlf == null)
                throw new IOException("Unexpected end of stream after chunk data");
        }
    }

    /**
     * 自定义按行读取输入流数据（兼容HTTP协议的\r\n换行符）
     * 功能：从输入流读取字符，直到遇到\r\n，返回去除\r\n后的字符串
     * 
     * @param in 输入流
     * @return 读取到的一行字符串（不含\r\n），若流结束且无数据返回null
     * @throws IOException 流读取失败时抛出
     */
    private static String readLine(InputStream in) throws IOException {
        // 字节数组输出流：累积当前行的字节数据
        ByteArrayOutputStream lineBaos = new ByteArrayOutputStream();
        // 记录上一个读取的字符（用于判断是否是\r\n组合）
        int prev = -1;
        // 记录当前读取的字符
        int cur;

        // 循环读取每个字符（阻塞直到有数据或流关闭）
        while ((cur = in.read()) != -1) {
            // 判断是否遇到\r\n换行符（上一个字符是\r，当前是\n）
            if (prev == '\r' && cur == '\n') {
                // 获取累积的字节数组
                byte[] arr = lineBaos.toByteArray();
                // 若数组长度>0且最后一个字符是\r，移除该\r（避免结果中包含\r）
                if (arr.length > 0 && arr[arr.length - 1] == '\r') {
                    lineBaos.reset(); // 重置输出流
                    // 重新写入除最后一个\r外的所有字节
                    lineBaos.write(arr, 0, arr.length - 1);
                }
                // 将累积的字节转为UTF-8字符串并返回（不含\r\n）
                return lineBaos.toString(StandardCharsets.UTF_8.name());
            }

            // 若未遇到换行符，将当前字符写入累积输出流
            lineBaos.write(cur);
            // 更新上一个字符为当前字符
            prev = cur;
        }

        // 循环结束（流关闭）：若累积的字节数为0，返回null（无数据）
        if (lineBaos.size() == 0)
            return null;
        // 否则，返回累积的字节转为的字符串（可能是不完整的行，流提前结束）
        return lineBaos.toString(StandardCharsets.UTF_8.name());
    }
}
