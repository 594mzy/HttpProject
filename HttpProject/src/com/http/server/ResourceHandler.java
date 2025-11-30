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

    private static final String STATIC_ROOT = "/static"; // 对应 resources/static

    public Response getStaticResource(String path, Request req) {
        Response resp = new Response();
        // 安全：禁止向上跳目录
        if (path.contains(".."))
            return new404();

        // 去 classpath 下读文件
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