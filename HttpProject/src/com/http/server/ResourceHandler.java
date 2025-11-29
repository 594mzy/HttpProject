package server;

import common.Response;
import common.MimeUtil;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ResourceHandler {

    private static final String STATIC_ROOT = "/static";  // 对应 resources/static

    public Response getStaticResource(String path) {
        Response resp = new Response();
        // 安全：禁止向上跳目录
        if (path.contains("..")) return new404();

        // 去 classpath 下读文件
        URL url = getClass().getResource(STATIC_ROOT + path);
        if (url == null) return new404();

        try {
            Path file = Paths.get(url.toURI());
            if (!Files.exists(file) || Files.isDirectory(file)) return new404();

            byte[] body = Files.readAllBytes(file);
            String mime = MimeUtil.fromFilename(file.getFileName().toString());

            resp.setStatusCode(200);
            resp.setReasonPhrase("OK");
            resp.setHeader("Content-Type", mime);
            resp.setHeader("Content-Length", String.valueOf(body.length));
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