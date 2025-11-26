package common;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class BIOTransport {
    // 连接池实例
    private final SocketPool socketPool;

    /**
     * 构造函数，初始化连接池
     * @param host 目标服务器地址
     * @param port 目标服务器端口
     * @param maxPoolSize 连接池最大容量
     */
    public BIOTransport(String host, int port, int maxPoolSize) {
        this.socketPool = new SocketPool(host, port, maxPoolSize);
    }

    /**
     * 发送字节数据到服务器
     * @param data 要发送的字节数组
     * @throws IOException IO异常
     * @throws InterruptedException 线程中断异常
     */
    public void sendData(byte[] data) throws IOException, InterruptedException {
        Socket socket = null;
        OutputStream outputStream = null;
        try {
            // 从连接池获取连接
            socket = socketPool.getConnection();
            // 获取输出流
            outputStream = socket.getOutputStream();
            // 发送数据
            outputStream.write(data);
            outputStream.flush(); // 确保数据立即发送
        } finally {
            // 关闭输出流（不关闭Socket，归还到连接池）
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            // 归还连接到池
            if (socket != null) {
                socketPool.releaseConnection(socket);
            }
        }
    }

    /**
     * 从服务器读取字节数据
     * @param buffer 接收数据的缓冲区
     * @return 读取的字节数
     * @throws IOException IO异常
     * @throws InterruptedException 线程中断异常
     */
    public int receiveData(byte[] buffer) throws IOException, InterruptedException {
        Socket socket = null;
        InputStream inputStream = null;
        try {
            // 从连接池获取连接
            socket = socketPool.getConnection();
            // 获取输入流
            inputStream = socket.getInputStream();
            // 读取数据到缓冲区
            return inputStream.read(buffer);
        } finally {
            // 关闭输入流（不关闭Socket，归还到连接池）
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            // 归还连接到池
            if (socket != null) {
                socketPool.releaseConnection(socket);
            }
        }
    }
    /**
     * 关闭连接池
     */
    public void close() {
        socketPool.closePool();
    }
}