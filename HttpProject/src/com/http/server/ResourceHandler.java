package server;

import common.Response;
import common.MimeUtil;
import common.Request;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class ResourceHandler {

    private static final String STATIC_ROOT = "/static"; // classpath root
    private final Path fsRoot; // optional filesystem root for static files

    public ResourceHandler() {
        this.fsRoot = null;
    }

    /**
     * 构造器：允许指定文件系统上的静态资源根目录（便于开发时直接从 src 文件夹加载）
     * 
     * @param fsRootPath 静态资源根目录的文件系统路径（绝对或相对），例如 "src/com/resources/static"
     */
    public ResourceHandler(String fsRootPath) {
        if (fsRootPath == null || fsRootPath.isEmpty()) {
            this.fsRoot = null;
        } else {
            this.fsRoot = Paths.get(fsRootPath);
        }
    }

    public Response getStaticResource(String path, Request req) {
        Response resp = new Response();
        // 安全：禁止向上跳目录
        if (path.contains(".."))
            return new404();

        // 兼容：允许传入以 '/' 开头或不以 '/' 开头的 path
        String relPath = path;
        if (relPath.startsWith("/"))
            relPath = relPath.substring(1);

        DateTimeFormatter rfc1123 = DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.ENGLISH)
                .withZone(ZoneId.of("GMT"));

        // 1) 先尝试从文件系统读取（如果提供了 fsRoot）
        if (fsRoot != null) {
            try {
                Path file = fsRoot.resolve(relPath);
                System.out.println("[ResourceHandler] Serving from fsRoot: " + file.toString());
                if (Files.exists(file) && Files.isRegularFile(file)) {
                    long lastModifiedMillis = Files.getLastModifiedTime(file).toMillis();
                    Instant lastModifiedInstant = Instant.ofEpochMilli(lastModifiedMillis);
                    String lastModHeader = rfc1123
                            .format(ZonedDateTime.ofInstant(lastModifiedInstant, ZoneId.of("GMT")));

                    // 如果请求包含 If-Modified-Since，尝试解析并比较
                    String ifMod = req.getHeader("if-modified-since");
                    if (ifMod != null && !ifMod.isEmpty()) {
                        try {
                            ZonedDateTime z = ZonedDateTime.parse(ifMod, rfc1123);
                            Instant i = z.toInstant();
                            // 比较到秒级以避免毫秒差异导致误判
                            if (i.getEpochSecond() >= lastModifiedInstant.getEpochSecond()) {
                                Response notMod = new Response();
                                notMod.setStatusCode(304);
                                notMod.setReasonPhrase("Not Modified");
                                notMod.setHeader("Last-Modified", lastModHeader);
                                return notMod;
                            }
                        } catch (Exception ignored) {
                            // 解析失败则继续正常返回文件
                        }
                    }

                    byte[] body = Files.readAllBytes(file);
                    String mime = MimeUtil.fromFilename(file.getFileName().toString());
                    resp.setStatusCode(200);
                    resp.setReasonPhrase("OK");
                    resp.setHeader("Content-Type", mime);
                    resp.setHeader("Content-Length", String.valueOf(body.length));
                    resp.setHeader("Last-Modified", lastModHeader);
                    if ("HEAD".equals(req.getMethod())) {
                        resp.setBody(new byte[0]);
                        return resp;
                    }
                    resp.setBody(body);
                    return resp;
                }
            } catch (IOException e) {
                return new404();
            }
        }

        // 2) 回退：从 classpath 读取资源（原有行为）。尝试获得 URL 的最后修改时间（若可用）
        URL url = getClass().getResource(STATIC_ROOT + (relPath.startsWith("/") ? relPath : "/" + relPath));
        if (url == null)
            return new404();

        try {
            URLConnection conn = url.openConnection();
            long last = conn.getLastModified();
            if (last > 0) {
                String lastModHeader = rfc1123
                        .format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(last), ZoneId.of("GMT")));
                String ifMod = req.getHeader("if-modified-since");
                if (ifMod != null && !ifMod.isEmpty()) {
                    try {
                        ZonedDateTime z = ZonedDateTime.parse(ifMod, rfc1123);
                        if (z.toInstant().getEpochSecond() >= Instant.ofEpochMilli(last).getEpochSecond()) {
                            Response notMod = new Response();
                            notMod.setStatusCode(304);
                            notMod.setReasonPhrase("Not Modified");
                            notMod.setHeader("Last-Modified", lastModHeader);
                            return notMod;
                        }
                    } catch (Exception ignored) {
                    }
                }
                try (InputStream in = conn.getInputStream()) {
                    if (in == null)
                        return new404();
                    byte[] body = in.readAllBytes();
                    String mime = MimeUtil.fromFilename(relPath.substring(relPath.lastIndexOf('/') + 1));
                    resp.setStatusCode(200);
                    resp.setReasonPhrase("OK");
                    resp.setHeader("Content-Type", mime);
                    resp.setHeader("Content-Length", String.valueOf(body.length));
                    resp.setHeader("Last-Modified", lastModHeader);
                    if ("HEAD".equals(req.getMethod())) {
                        resp.setBody(new byte[0]);
                        return resp;
                    }
                    resp.setBody(body);
                    return resp;
                }
            } else {
                // 无法取得最后修改时间，退回到老逻辑直接读取流
                try (InputStream in = conn.getInputStream()) {
                    if (in == null)
                        return new404();
                    byte[] body = in.readAllBytes();
                    String mime = MimeUtil.fromFilename(relPath.substring(relPath.lastIndexOf('/') + 1));
                    resp.setStatusCode(200);
                    resp.setReasonPhrase("OK");
                    resp.setHeader("Content-Type", mime);
                    resp.setHeader("Content-Length", String.valueOf(body.length));
                    if ("HEAD".equals(req.getMethod())) {
                        resp.setBody(new byte[0]);
                        return resp;
                    }
                    resp.setBody(body);
                    return resp;
                }
            }
        } catch (Exception e) {
            return new404();
        }
    }

    private Response new404() {
        Response r = new Response();
        r.setStatusCode(404);
        r.setReasonPhrase("Not Found");
        r.setHeader("Content-Type", "text/plain");
        r.setBody("404 Not Found".getBytes());
        return r;
    }
}