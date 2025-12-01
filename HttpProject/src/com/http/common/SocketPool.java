package common;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class SocketPool {
    /**
     * 长连接池（keep-alive）：管理Socket连接的复用
     */
    // 连接池队列（存储可用的长连接）
    // 使用一个阻塞队列来存储可用的Socket连接。
    // BlockingQueue的特性是：当队列空时，取元素的操作会阻塞；当队列满时，放元素的操作会阻塞。
    // 这使得连接的获取和归还可以安全地在多线程环境下进行，无需额外的同步代码。
    private final BlockingQueue<Socket> connectionPool;
    // 目标服务器地址
    private final String host;
    // 目标服务器端口
    private final int port;
    // 连接池最大容量
    private final int maxPoolSize;

    // SocketPool的构造函数
    public SocketPool(String host, int port, int maxPoolSize) {
        this.host = host;
        this.port = port;
        this.maxPoolSize = maxPoolSize;
        // 初始化阻塞队列，使用LinkedBlockingQueue，并指定其容量为maxPoolSize。
        // 如果不指定容量，它将是无界的，可能导致内存溢出。
        this.connectionPool = new LinkedBlockingQueue<>(maxPoolSize);
    }

    /**
     * 获取长连接（优先复用池内连接，无可用则新建）
     */
    public Socket getConnection() throws IOException, InterruptedException {
        // 循环尝试获取池内可用连接；若池为空或无法得到可用连接则创建新的Socket并返回。
        while (true) {
            Socket socket = connectionPool.poll(1, TimeUnit.SECONDS);
            if (socket == null) {
                // 池中暂时无连接，创建新的连接并返回
                Socket s = new Socket(host, port);
                s.setKeepAlive(true);
                s.setSoTimeout(15_000);
                return s;
            }

            // 如果取到连接，先检查基本状态
            boolean valid = true;
            try {
                if (socket.isClosed() || !socket.isConnected()) {
                    valid = false;
                } else if (!isAlive(socket)) {
                    // 探活失败
                    valid = false;
                }
            } catch (Exception e) {
                valid = false;
            }

            if (!valid) {
                // 关闭并继续循环尝试获取下一个连接
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
                continue;
            }

            // 连接可用，配置超时并返回
            socket.setSoTimeout(15_000);
            return socket;
        }
    }

    // 新增：简单探活，失败就丢弃
    public boolean isAlive(Socket s) {
        if (s == null || s.isClosed())
            return false;
        try {
            s.sendUrgentData(0xFF);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 归还连接到池（复用）
     */
    public void releaseConnection(Socket socket) {
        if (socket == null)
            return;

        // 如果已经关闭或未连接，直接丢弃并确保关闭
        if (socket.isClosed() || !socket.isConnected()) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            return;
        }

        // 使用探活方法判断连接是否仍然可用
        if (!isAlive(socket)) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            return;
        }

        // 尝试将连接放回池（非阻塞）。如果池满，则关闭该连接以避免泄漏。
        boolean offered = connectionPool.offer(socket);
        if (!offered) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * 关闭连接池
     */
    public void closePool() {
        // 遍历队列中的所有Socket连接。
        connectionPool.forEach(socket -> {
            try {
                socket.close();
            } catch (IOException e) {// 若关闭时Socket已断开，网络中断，端口被占用时会产生IO异常
                e.printStackTrace();
            }
        });
        connectionPool.clear();// 清空队列，释放对已关闭Socket对象的引用
    }

}
