package common;

// buffered reader removed; use InputStream-based parsing
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.SequenceInputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

public class BIOTransport {
    // 连接池实例
    private final SocketPool socketPool;

    /**
     * 构造函数，初始化连接池
     * 
     * @param host        目标服务器地址
     * @param port        目标服务器端口
     * @param maxPoolSize 连接池最大容量
     */
    public BIOTransport(String host, int port, int maxPoolSize) {
        this.socketPool = new SocketPool(host, port, maxPoolSize);
    }

    /**
     * 发送字节数据到服务器（低级接口，注意：不要关闭流以便支持 Keep-Alive）
     */
    public void sendData(byte[] data) throws IOException, InterruptedException {
        Socket socket = null;
        try {
            // 从连接池获取连接
            socket = socketPool.getConnection();
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write(data);
            outputStream.flush(); // 确保数据立即发送
            // 不要关闭 outputStream，否则会关闭底层 Socket，影响连接池复用
        } finally {
            // 归还连接到池（不要关闭 socket）
            if (socket != null) {
                socketPool.releaseConnection(socket);
            }
        }
    }

    /**
     * 从服务器读取字节数据（低级接口，注意：不要关闭流以便支持 Keep-Alive）
     */
    public int receiveData(byte[] buffer) throws IOException, InterruptedException {
        Socket socket = null;
        try {
            socket = socketPool.getConnection();
            InputStream inputStream = socket.getInputStream();
            int r = inputStream.read(buffer);
            // 不要关闭 inputStream，这会关闭 Socket
            return r;
        } finally {
            if (socket != null) {
                socketPool.releaseConnection(socket);
            }
        }
    }

    /**
     * 发送一个完整请求并在同一 socket 上读取完整的响应（请求行+头+body），
     * 返回响应的原始字符串（header + body）。
     * 该方法会在读取响应头时检查是否包含 `Connection: close`，如果包含则关闭 socket 而不归还到池。
     */
    public String sendRequest(byte[] request) throws IOException, InterruptedException {
        Socket socket = null;
        try {
            socket = socketPool.getConnection();
            OutputStream out = socket.getOutputStream();
            out.write(request);
            out.flush();

            // 读取响应头（字节级别，避免字符解码导致Content-Length/字节数不匹配）
            InputStream in = socket.getInputStream();
            // readHeaders 会读取到头部结束并返回可能多读的 body 字节
            java.util.Map<String, Object> headerRes = readHeaders(in);
            String headers = (String) headerRes.getOrDefault("rawHeaders", "");
            byte[] remaining = (byte[]) headerRes.getOrDefault("remaining", new byte[0]);

            // 解析头部简单字段
            int contentLength = -1;
            boolean connectionClose = false;
            boolean isChunked = false;
            for (String hl : headers.split("\r\n")) {
                String l = hl.trim().toLowerCase();
                if (l.startsWith("content-length:")) {
                    try {
                        contentLength = Integer.parseInt(hl.substring(15).trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (l.startsWith("connection:")) {
                    if (l.contains("close"))
                        connectionClose = true;
                }
                if (l.startsWith("transfer-encoding:")) {
                    if (l.contains("chunked"))
                        isChunked = true;
                }
            }

            byte[] bodyBytes = new byte[0];
            try {
                InputStream bodyIn = in;
                if (remaining != null && remaining.length > 0) {
                    bodyIn = new SequenceInputStream(new ByteArrayInputStream(remaining), in);
                }
                if (contentLength > 0) {
                    bodyBytes = readFixedLength(bodyIn, contentLength);
                } else if (isChunked) {
                    bodyBytes = readChunkedBody(bodyIn);
                } else if (connectionClose) {
                    bodyBytes = readUntilEOF(bodyIn);
                } else {
                    bodyBytes = new byte[0];
                }
            } catch (SocketTimeoutException ste) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
                throw ste;
            }

            String response = headers + new String(bodyBytes, StandardCharsets.UTF_8);
            // 根据 Connection 头决定是归还连接还是关闭连接
            if (connectionClose) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
            } else {
                socketPool.releaseConnection(socket);
            }

            return response;
        } finally {
            // 如果我们已经关闭或归还了 socket，上面的逻辑已处理；但若发生异常且 socket 未处理，则确保归还或关闭
            // 注意：不要重复归还已归还的 socket（SocketPool 实现应能容忍重复归还也最好避免）。
            // 为安全起见这里不做额外操作（Socket 在上面已经处理），避免 double-release。
        }
    }

    private byte[] readFixedLength(InputStream in, int len) throws IOException {
        byte[] data = new byte[len];
        int total = 0;
        while (total < len) {
            int r = in.read(data, total, len - total);
            if (r == -1)
                throw new IOException("Unexpected EOF while reading fixed length body");
            total += r;
        }
        return data;
    }

    private byte[] readChunkedBody(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while (true) {
            String line = readLine(in);
            if (line == null)
                throw new IOException("Unexpected end of stream while reading chunk size");
            String chunkSizeHex = line.split(";", 2)[0].trim();
            int chunkSize;
            try {
                chunkSize = Integer.parseInt(chunkSizeHex, 16);
            } catch (NumberFormatException e) {
                throw new IOException("Invalid chunk size: " + chunkSizeHex);
            }
            if (chunkSize == 0) {
                // read trailers until blank line
                while (true) {
                    String trailer = readLine(in);
                    if (trailer == null)
                        throw new IOException("Unexpected end of stream while reading chunked trailers");
                    if (trailer.isEmpty())
                        break;
                }
                break;
            }
            int remaining = chunkSize;
            byte[] buffer = new byte[8192];
            while (remaining > 0) {
                int toRead = Math.min(buffer.length, remaining);
                int r = in.read(buffer, 0, toRead);
                if (r == -1)
                    throw new IOException("Unexpected EOF while reading chunk data");
                baos.write(buffer, 0, r);
                remaining -= r;
            }
            // consume CRLF after chunk
            String crlf = readLine(in);
            if (crlf == null)
                throw new IOException("Unexpected end of stream after chunk data");
        }
        return baos.toByteArray();
    }

    private byte[] readUntilEOF(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) {
            baos.write(buf, 0, n);
        }
        return baos.toByteArray();
    }

    // readHeaders & helpers (copy behavior similar to
    // server.RequestParser.readHeaders/readLine)
    private java.util.Map<String, Object> readHeaders(InputStream in) throws IOException {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        ByteArrayOutputStream headerBaos = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int read;
        boolean found = false;
        while ((read = in.read(buf)) != -1) {
            headerBaos.write(buf, 0, read);
            byte[] soFar = headerBaos.toByteArray();
            int pos = indexOfCRLFCRLF(soFar);
            if (pos != -1) {
                int headerEnd = pos + 4;
                String headerStr = new String(soFar, 0, headerEnd, StandardCharsets.UTF_8);
                result.put("rawHeaders", headerStr);
                if (soFar.length > headerEnd) {
                    byte[] remaining = new byte[soFar.length - headerEnd];
                    System.arraycopy(soFar, headerEnd, remaining, 0, remaining.length);
                    result.put("remaining", remaining);
                } else {
                    result.put("remaining", new byte[0]);
                }
                found = true;
                break;
            }
        }
        if (!found) {
            String headerStr = headerBaos.toString(StandardCharsets.UTF_8.name());
            result.put("rawHeaders", headerStr);
            result.put("remaining", new byte[0]);
        }
        return result;
    }

    private static int indexOfCRLFCRLF(byte[] data) {
        for (int i = 0; i < data.length - 3; i++) {
            if (data[i] == '\r' && data[i + 1] == '\n' && data[i + 2] == '\r' && data[i + 3] == '\n') {
                return i;
            }
        }
        return -1;
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream lineBaos = new ByteArrayOutputStream();
        int prev = -1;
        int cur;
        while ((cur = in.read()) != -1) {
            if (prev == '\r' && cur == '\n') {
                byte[] arr = lineBaos.toByteArray();
                if (arr.length > 0 && arr[arr.length - 1] == '\r') {
                    lineBaos.reset();
                    lineBaos.write(arr, 0, arr.length - 1);
                }
                return lineBaos.toString(StandardCharsets.UTF_8.name());
            }
            lineBaos.write(cur);
            prev = cur;
        }
        if (lineBaos.size() == 0)
            return null;
        return lineBaos.toString(StandardCharsets.UTF_8.name());
    }

    /**
     * 关闭连接池
     */
    public void close() {
        socketPool.closePool();
    }
}