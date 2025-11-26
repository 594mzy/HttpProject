package client;

// 导入BIO传输工具类：基于阻塞IO（BIO）实现Socket数据发送/接收
import common.BIOTransport;
// 导入响应实体类：封装HTTP响应数据（状态码、头信息、响应体等）
import common.Response;
// 导入Socket池工具类：管理Socket连接复用（这里池大小固定为1）
import common.SocketPool;
// 导入URL解析工具类：解析URL中的协议、主机、端口、路径等信息
import common.UrlParser;
// 导入URL解析结果封装类：存储解析后的URL各部分（scheme、host、port、path等）
import common.UrlParser.UrlInfo;

// 导入IO异常类：处理流操作、Socket连接等IO相关异常
import java.io.IOException;
// 导入URI语法异常类：处理URL格式非法的异常
import java.net.URISyntaxException;
// 导入标准字符集：用于HTTP请求字符串转字节（UTF-8编码，HTTP协议默认）
import java.nio.charset.StandardCharsets;
// 导入Map接口：用于遍历响应头的键值对
import java.util.Map;
// 导入并发哈希表：线程安全的Map实现，用于缓存GET请求响应（支持多线程并发访问）
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简单HTTP客户端：支持GET请求、自动重定向、本地缓存、条件请求（If-None-Match/If-Modified-Since）
 * 核心特性：
 * 1. 基于BIO+Socket池实现TCP连接通信
 * 2. 最大5次重定向限制（防止循环重定向）
 * 3. 内存缓存GET请求响应（键为URL）
 * 4. 支持304 Not Modified缓存复用（减少带宽消耗）
 * 5. 兼容绝对路径、根相对路径、相对路径的重定向Location
 */
public class HttpClient {
    // 最大重定向次数：限制为5次，避免因循环重定向导致无限循环
    private final int maxRedirects = 5;
    // GET请求响应缓存：线程安全的ConcurrentHashMap，键为请求URL，值为对应的响应对象
    // 仅缓存GET请求（符合HTTP缓存语义，GET请求默认幂等可缓存）
    private final Map<String, Response> cache = new ConcurrentHashMap<>();

    /**
     * 无参构造方法：初始化HTTP客户端（默认初始化缓存和重定向配置）
     */
    public HttpClient() {
    }

    /**
     * 发送HTTP GET请求，自动处理重定向、缓存、条件请求
     * @param url 目标请求URL（支持http协议，如http://localhost:8080/index.html）
     * @return 最终的HTTP响应对象（可能是缓存响应或新请求的响应）
     * @throws IOException 当Socket连接失败、流操作异常、URL格式非法时抛出
     * @throws InterruptedException 当线程在等待Socket连接时被中断抛出
     */
    public Response get(String url) throws IOException, InterruptedException {
        // 当前请求的URL（初始为传入的url，重定向时会更新为新地址）
        String current = url;
        // 已重定向次数计数器（初始为0，超过maxRedirects则终止重定向）
        int redirects = 0;

        // 循环处理请求+重定向：直到获取非重定向响应或达到最大重定向次数
        while (true) {
            // URL解析结果对象：存储解析后的协议、主机、端口、路径等信息
            UrlInfo info;
            try {
                // 解析当前URL：将字符串URL转为结构化的UrlInfo对象
                info = UrlParser.parse(current);
            } catch (URISyntaxException e) {
                // 捕获URL语法异常，包装为IO异常抛出（让调用者统一处理）
                throw new IOException("Invalid URL: " + current, e);
            }

            // 构造请求路径：若解析后的path为空（如URL为http://localhost），则默认路径为"/"
            String path = info.path == null || info.path.isEmpty() ? "/" : info.path;

            // 从缓存中获取当前URL对应的已缓存响应（用于构建条件请求头）
            Response cached = cache.get(current);
            // 字符串构建器：拼接GET请求的完整报文（请求行+请求头+空行）
            StringBuilder req = new StringBuilder();

            // 1. 拼接请求行：GET方法 + 路径（含查询参数） + HTTP/1.1协议版本
            req.append("GET ").append(path).append(" HTTP/1.1\r\n");
            // 2. 拼接Host头：主机名 + 非默认端口（80/443）时补充端口号
            req.append("Host: ").append(info.host);
            // 若端口不是HTTP默认80（http）或443（https），则在Host头后追加端口
            if (!(info.port == 80 || info.port == 443)) req.append(":" + info.port);
            req.append("\r\n");
            // 3. 拼接Connection头：设置为close（短连接，简化处理，不启用Keep-Alive）
            req.append("Connection: close\r\n");
            // 4. 拼接Accept头：表示客户端接受所有类型的响应数据（/*/*）
            req.append("Accept: */*\r\n");
            // 5. 拼接User-Agent头：标识客户端身份（自定义简易客户端版本）
            req.append("User-Agent: SimpleHttpClient/1.0\r\n");

            // 若存在缓存响应，添加条件请求头（减少重复数据传输）
            if (cached != null) {
                // 获取缓存响应的ETag头（资源唯一标识）
                String etag = cached.getHeader("etag");
                // 获取缓存响应的Last-Modified头（资源最后修改时间）
                String lm = cached.getHeader("last-modified");
                // 若有ETag，添加If-None-Match头：仅当服务器资源ETag与客户端不一致时返回完整响应
                if (etag != null) {
                    req.append("If-None-Match: ").append(etag).append("\r\n");
                }
                // 若有Last-Modified，添加If-Modified-Since头：仅当服务器资源更新时间晚于该时间时返回完整响应
                if (lm != null) {
                    req.append("If-Modified-Since: ").append(lm).append("\r\n");
                }
            }

            // 6. 请求头结束标志：空行（\r\n），分隔请求头和请求体（GET请求无请求体）
            req.append("\r\n");

            // 将拼接好的请求字符串转为UTF-8字节数组（网络传输需字节流）
            byte[] reqBytes = req.toString().getBytes(StandardCharsets.UTF_8);

            // 创建Socket池：指定目标主机、端口，池大小为1（单连接复用，简化池管理）
            SocketPool pool = new SocketPool(info.host, info.port, 1);
            // 创建BIO传输对象：通过Socket池获取Socket，实现请求发送和响应接收
            BIOTransport transport = new BIOTransport(pool);
            // 发送请求字节数组，接收服务器返回的原始响应字节（阻塞直到响应接收完成）
            byte[] raw = transport.send(reqBytes);

            // 解析原始响应字节：将字节流转为结构化的Response对象
            Response resp = ResponseParser.parse(raw);

            // 获取响应状态码（如200、301、302、304等）
            int code = resp.getStatusCode();

            // 处理重定向响应（301永久重定向、302临时重定向）
            if ((code == 301 || code == 302) && redirects < maxRedirects) {
                // 从响应头中获取重定向目标地址（Location头）
                String loc = resp.getHeader("location");
                // 若Location头为空，无法重定向，直接返回当前响应
                if (loc == null || loc.isEmpty()) {
                    return resp;
                }

                // 解析Location地址：处理绝对路径、根相对路径、相对路径三种情况
                String next;
                // 情况1：Location是绝对路径（以http://或https://开头）
                if (loc.startsWith("http://") || loc.startsWith("https://")) {
                    next = loc;
                }
                // 情况2：Location是根相对路径（以/开头，基于主机根目录）
                else if (loc.startsWith("/")) {
                    next = info.scheme + "://" + info.host
                            + (info.port == 80 || info.port == 443 ? "" : ":" + info.port)
                            + loc;
                }
                // 情况3：Location是相对路径（基于当前请求路径的目录）
                else {
                    // 获取当前URL的基础路径（去掉文件名，保留目录部分）
                    String basePath = info.path;
                    // 找到最后一个/的索引（分割目录和文件名）
                    int idx = basePath.lastIndexOf('/');
                    // 若存在/，截取到最后一个/（含/），作为基础目录；否则基础路径为/
                    if (idx >= 0) basePath = basePath.substring(0, idx + 1);
                    // 拼接完整的重定向地址
                    next = info.scheme + "://" + info.host
                            + (info.port == 80 || info.port == 443 ? "" : ":" + info.port)
                            + basePath + loc;
                }

                // 重定向次数+1，更新当前请求URL为新地址，继续循环发送请求
                redirects++;
                current = next;
                continue; // 跳过后续逻辑，直接进入下一次循环（请求新地址）
            }

            // 处理304 Not Modified（资源未修改）响应
            if (code == 304) {
                // 从缓存中获取当前URL的缓存响应
                Response cachedResp = cache.get(current);
                // 若存在缓存响应：合并304响应中可能更新的头部（如新ETag、Cache-Control等）到缓存
                if (cachedResp != null) {
                    for (Map.Entry<String, String> e : resp.getHeaders().entrySet()) {
                        // 仅合并非空的键值对（避免覆盖为null）
                        if (e.getKey() != null && e.getValue() != null) {
                            cachedResp.setHeader(e.getKey(), e.getValue());
                        }
                    }
                    // 返回更新后的缓存响应（避免重复下载相同资源）
                    return cachedResp;
                }
                // 若不存在缓存（异常情况），直接返回304响应
                return resp;
            }

            // 处理200 OK响应：将响应存入缓存（覆盖旧缓存，确保缓存最新）
            // 即使是条件请求（之前发过If-None-Match/If-Modified-Since），也更新缓存
            if (code == 200) {
                cache.put(current, resp);
            }

            // 返回最终响应（非重定向、非304的响应，如200、404、500等）
            return resp;
        }
    }
}