package server;

import common.Request;
import common.Response;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final Router router;

    public ClientHandler(Socket socket, Router router) {
        this.socket = socket;
        this.router = router;
    }

    @Override
    public void run() {
        
        try (InputStream in = socket.getInputStream(); OutputStream out = socket.getOutputStream()) {
            // 循环处理同一 socket 上的多次请求（支持 Keep-Alive）
            socket.setSoTimeout(30_000); // 30 秒没读到完整请求就抛 SocketTimeoutException
            while (!socket.isClosed()) {
                // System.out.println("[" + Thread.currentThread() + "] 等待读取请求…");
                Request req;
                try {
                    req = RequestParser.parse(in);
                } catch (IOException e) {
                    // 无法解析请求（客户端断开或发送非法数据），退出循环
                    break;
                }

                if (req == null) break;

                // 保证 path 不为 null
                if (req.getPath() == null || req.getPath().isEmpty()) {
                    req.setPath("/");
                }

                // 分发请求得到响应
                Response resp = router.dispatch(req);

                // 决定是否保持长连接：
                // HTTP/1.1 默认长连接，除非请求头 Connection: close；
                // HTTP/1.0 默认短连接，除非请求头 Connection: keep-alive
                boolean keepAlive = false;
                String connHeader = req.getHeader("connection");
                String version = req.getVersion();
                if (connHeader != null) {
                    if ("keep-alive".equalsIgnoreCase(connHeader)) keepAlive = true;
                    else if ("close".equalsIgnoreCase(connHeader)) keepAlive = false;
                } else {
                    keepAlive = "HTTP/1.1".equalsIgnoreCase(version);
                }

                // 写响应（ResponseWriter 会在头部补全 Connection/Content-Length）
                try {
                    ResponseWriter.write(out, resp, keepAlive);
                } catch (IOException e) {
                    // 写入失败，退出循环并关闭连接
                    break;
                }

                if (!keepAlive) break;
                // 若 keep-alive，继续循环等待下一个请求在同一连接上到来
            }
        }catch(java.net.SocketTimeoutException e) {
            // 读请求超时，直接关闭连接
            System.out.println("[" + Thread.currentThread().getName() + "] 30s 内未收到完整请求，关闭连接");
        }
        catch (IOException ignored) {
            // 忽略流关闭异常
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
