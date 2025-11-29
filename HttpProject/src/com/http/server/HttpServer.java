package server;

import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class HttpServer {
    private static final int DEFAULT_PORT = 8080; // 默认服务器端口

    private final int port;
    private ServerSocket serverSocket;
    private final ExecutorService pool;
    private final Router router;

    public HttpServer() {
        this(DEFAULT_PORT);
    }

    public HttpServer(int port) {
        this.port = port;
        // 线程池：固定线程数为 CPU 核心数的 2 倍，简单且稳定
        this.pool = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() * 2));
        this.router = new Router();
    }

    /** 启动服务器，阻塞运行直到发生错误或被 stop() 调用 */
    public void start() throws IOException {
        try {
            serverSocket = new ServerSocket(port);
        } catch (BindException be) {
            // 如果默认端口被占用，尝试绑定到随机可用端口（0）作为回退
            if (port == DEFAULT_PORT) {
                System.err.println("Port " + DEFAULT_PORT + " is in use, attempting to bind to a random available port...");
                serverSocket = new ServerSocket(0);
            } else {
                throw be;
            }
        }

        System.out.println("HTTP server started on port " + serverSocket.getLocalPort());

        try {
            while (!serverSocket.isClosed()) {
                Socket client = serverSocket.accept();
                // 提交到线程池处理，ClientHandler 会负责关闭 socket
                pool.submit(new ClientHandler(client, router));
            }
        } finally {
            stop();
        }
    }

    /** 优雅停止服务器 */
    public void stop() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }

        pool.shutdown();
        try {
            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }

        System.out.println("HTTP server stopped");
    }

    public static void main(String[] args) {
        int p = DEFAULT_PORT;
        // 优先使用命令行端口参数，其次读取环境变量 HTTP_PORT
        if (args != null && args.length > 0) {
            try { p = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {}
        } else {
            String env = System.getenv("HTTP_PORT");
            if (env != null) {
                try { p = Integer.parseInt(env); } catch (NumberFormatException ignored) {}
            }
        }

        HttpServer server = new HttpServer(p);
        // 注册 JVM 关闭钩子，确保优雅停止
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        try {
            server.start();
        } catch (IOException e) {
            System.err.println("Failed to start HTTP server: " + e.getMessage());
        }
    }
}
