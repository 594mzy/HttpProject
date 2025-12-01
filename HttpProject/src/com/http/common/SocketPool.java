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
        // 尝试从队列中获取一个Socket，最多等待1秒。
        // poll(time, unit)是一个阻塞方法。
        // 如果在1秒内成功获取到Socket，就返回它。
        // 如果1秒后队列仍然为空，poll方法会返回null。
        Socket socket = connectionPool.poll(1, TimeUnit.SECONDS);
        // 检查获取到的Socket是否有效，或者是否需要创建新的Socket。
        // 如果socket为null（说明池中无连接）。
        // 或者socket.isClosed()为true（连接已被关闭）。
        // 或者!socket.isConnected()为true（连接已断开）。
        if (socket == null || socket.isClosed() || !socket.isConnected()) {
            // 新建连接
            socket = new Socket(host, port);
            // 设置Socket的Keep-Alive选项为true。
            // 这是一个TCP层的选项，它会定期发送心跳包来检测连接是否有效。
            // 配合HTTP的Keep-Alive头，可以实现应用层的长连接。
            socket.setKeepAlive(true);
        }
        socket.setSoTimeout(15_000); // 增加读超时为 15 秒，避免过早超时
        return socket;
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
        // 检查Socket是否有效，并且连接池还没有满。
        // socket != null
        // !socket.isClosed()
        // connectionPool.size() < maxPoolSize
        // 只有当这三个条件都满足时，才将连接放回池中。
        if (socket != null && !socket.isClosed() && connectionPool.size() < maxPoolSize) {
            try {
                connectionPool.put(socket);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // 如果Socket无效或池已满，则不做任何操作，该Socket对象最终会被GC回收。
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
