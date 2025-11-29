package common;

// 导入Java自带的URI类：用于解析URL的结构化信息（协议、主机、端口等），底层实现URL语法解析
import java.net.URI;
// 导入URI语法异常类：当URL格式不符合规范时抛出（如缺少主机、非法字符等）
import java.net.URISyntaxException;

/**
 * URL解析工具类：将URL字符串解析为结构化的UrlInfo对象，处理边界情况（如默认端口、空路径、无协议URL）
 * 核心功能：提取URL中的协议（scheme）、主机（host）、端口（port）、路径（path）、查询参数（query）
 */
public class UrlParser {

    /**
     * URL解析结果封装类：存储解析后的URL各结构化字段（不可修改，通过构造方法初始化）
     * 提供toString方法便于调试查看解析结果
     */
    public static class UrlInfo {
        /** 协议类型（如http、https，默认http） */
        public final String scheme;
        /** 主机名/IP地址（如localhost、www.example.com、192.168.1.1） */
        public final String host;
        /** 端口号（http默认80，https默认443，URL中显式指定则用指定值） */
        public final int port;
        /** 资源路径（含查询参数，如/index.html?name=test，空路径时默认为/） */
        public final String path;
        /** 查询参数（不含?，如name=test，无查询参数则为null） */
        public final String query;
        /** 是否为HTTPS协议（通过scheme判断，equalsIgnoreCase忽略大小写） */
        public final boolean isHttps;

        /**
         * UrlInfo构造方法：初始化所有解析后的URL字段
         * @param scheme 协议（http/https）
         * @param host 主机名/IP
         * @param port 端口号
         * @param path 路径（含查询参数）
         * @param query 查询参数（不含?）
         */
        public UrlInfo(String scheme, String host, int port, String path, String query) {
            this.scheme = scheme;
            this.host = host;
            this.port = port;
            this.path = path;
            this.query = query;
            // 初始化是否为HTTPS：判断协议是否为https（忽略大小写，兼容HTTPS/HttpS等写法）
            this.isHttps = "https".equalsIgnoreCase(scheme);
        }

        /**
         * 重写toString方法：返回结构化的UrlInfo字符串（便于调试时查看各字段值）
         * @return 包含所有字段的字符串表示
         */
        @Override
        public String toString() {
            return "UrlInfo{" +
                    "scheme='" + scheme + '\'' +
                    ", host='" + host + '\'' +
                    ", port=" + port +
                    ", path='" + path + '\'' +
                    ", query='" + query + '\'' +
                    '}';
        }
    }

    /**
     * 核心解析方法：将URL字符串解析为UrlInfo对象，处理默认值和边界情况
     * 支持的URL格式：
     * 1. 完整URL（如http://localhost:8080/index.html?name=test、https://www.example.com）
     * 2. 无协议URL（如localhost:8080/path、example.com）
     * 3. 无端口URL（如http://localhost/index.html，默认http 80、https 443）
     * @param url 待解析的URL字符串
     * @return 解析后的UrlInfo对象（含所有结构化字段）
     * @throws URISyntaxException 当URL格式非法（如缺少主机、含非法字符）时抛出
     */
    public static UrlInfo parse(String url) throws URISyntaxException {
        // 1. 用Java原生URI类解析URL（底层处理URL语法规则，提取基础字段）
        URI uri = new URI(url);

        // 2. 处理协议（scheme）：若URL未指定协议（如"localhost:8080"），默认设为http
        String scheme = uri.getScheme() == null ? "http" : uri.getScheme();

        // 3. 处理主机（host）：若URI解析出的host为null（无协议URL可能出现），补协议后重新解析
        String host = uri.getHost();
        if (host == null) {
            // 补救措施：给URL补全协议前缀（如"example.com:8080"→"http://example.com:8080"）
            uri = new URI(scheme + "://" + url);
            // 重新获取补协议后的host（此时应能正确解析）
            host = uri.getHost();
        }

        // 4. 处理端口（port）：URI.getPort()返回-1表示未显式指定端口，按协议设默认值
        int port = uri.getPort();
        if (port == -1) {
            // https协议默认端口443，http协议默认端口80（忽略scheme大小写）
            port = "https".equalsIgnoreCase(scheme) ? 443 : 80;
        }

        // 5. 处理路径（path）：获取原始路径（含特殊字符，不编码），空路径时默认设为/
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/"; // URL无路径时（如http://localhost），默认路径为根路径/
        }

        // 6. 处理查询参数（query）：获取原始查询参数（不含?，无查询参数则为null）
        String query = uri.getRawQuery();

        // 7. 构建并返回UrlInfo对象：路径拼接查询参数（path+?+query，query为null则仅返回path）
        return new UrlInfo(
                scheme,
                host,
                port,
                path + (query != null ? "?" + query : ""), // 路径含查询参数（如/index.html?name=test）
                query // 单独存储查询参数（不含?，便于后续提取）
        );
    }
}