package server;

import common.Response;
import common.MimeUtil;
import common.Request;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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

        // 1) 先尝试从文件系统读取（如果提供了 fsRoot）
        if (fsRoot != null) {
            try {
                Path file = fsRoot.resolve(relPath);
                System.out.println("[ResourceHandler] Serving from fsRoot: " + file.toString());
                if (Files.exists(file) && Files.isRegularFile(file)) {
                    byte[] body = Files.readAllBytes(file);
                    String mime = MimeUtil.fromFilename(file.getFileName().toString());
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
            } catch (IOException e) {
                return new404();
            }
        }

        // 2) 回退：从 classpath 读取资源（原有行为）
        URL url = getClass().getResource(STATIC_ROOT + path);
        if (url == null)
            return new404();

        try (InputStream in = getClass().getResourceAsStream(STATIC_ROOT + path)) {
            if (in == null)
                return new404();
            byte[] body = in.readAllBytes();
            String mime = MimeUtil.fromFilename(path.substring(path.lastIndexOf('/') + 1));
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