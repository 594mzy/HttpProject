package server;

import common.Request;
import common.Response;
import common.MimeUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class Router {

    // 简易路由表： "GET /user/login" -> handler
    private final Map<String, Function<Request, Response>> routeTable = new HashMap<>();
    private final ResourceHandler resourceHandler;

    public Router() {
        // 尝试根据工作目录推断静态资源的文件系统路径（开发环境常见位置）
        String fsStatic = null;
        try {
            String wd = new java.io.File(".").getCanonicalPath();
            // 只查找 src/com/http/static（以及当前目录下的一级子目录用于嵌套项目结构）
            java.nio.file.Path primary = java.nio.file.Paths.get(wd, "src", "com", "http", "static");
            if (java.nio.file.Files.exists(primary)) {
                fsStatic = primary.toString();
                System.out.println("[Router] Using filesystem static root (com/http): " + fsStatic);
            } else {
                // 在当前目录的一级子目录中查找（例如外层 HttpProject 包含内层 HttpProject）
                java.io.File wdFile = new java.io.File(wd);
                java.io.File[] children = wdFile.listFiles(java.io.File::isDirectory);
                if (children != null) {
                    for (java.io.File child : children) {
                        java.nio.file.Path cand = java.nio.file.Paths.get(child.getAbsolutePath(), "src", "com", "http",
                                "static");
                        if (java.nio.file.Files.exists(cand)) {
                            fsStatic = cand.toString();
                            System.out.println(
                                    "[Router] Found filesystem static root in child folder (com/http): " + fsStatic);
                            break;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        if (fsStatic != null) {
            resourceHandler = new ResourceHandler(fsStatic);
        } else {
            System.out.println("[Router] No filesystem static root found; using classpath resources");
            resourceHandler = new ResourceHandler();
        }
        // 业务路由
        UserHandler userHandler = new UserHandler();
        routeTable.put("POST /user/login", userHandler::login);
        routeTable.put("POST /user/register", userHandler::register);
        // Demo: 根路径重定向到静态首页（用于演示 301/302）
        routeTable.put("GET /301", req -> {
            Response r = new Response();
            r.setStatusCode(301);
            r.setReasonPhrase("Found");
            r.setHeader("Location", "/static/index.html");
            return r;
        });
        routeTable.put("GET /302", req -> {
            Response r = new Response();
            r.setStatusCode(302);
            r.setReasonPhrase("Found");
            r.setHeader("Location", "/static/index.html");
            return r;
        });
        routeTable.put("GET /", req -> {
            Response r = new Response();
            r.setStatusCode(302);
            r.setReasonPhrase("Found");
            r.setHeader("Location", "/static/index.html");
            return r;
        });
        // Demo: 触发服务器内部错误用于演示 500
        routeTable.put("GET /err", req -> {
            throw new RuntimeException("intentional test error");
        });
    }

    /** 唯一暴露的方法：把请求扔进来，把响应拿出去 */
    public Response dispatch(Request req) {
        try {
            String key = req.getMethod() + " " + req.getPath();
            Function<Request, Response> handler = routeTable.get(key);

            if (handler != null)
                return handler.apply(req);

            // 处理静态资源：统一先判断路径是否以 /static/ 开头
            if (req.getPath().startsWith("/static/")) {
                // GET 或 HEAD 正常交给 ResourceHandler 处理
                if ("GET".equals(req.getMethod()) || "HEAD".equals(req.getMethod())) {
                    return resourceHandler.getStaticResource(req.getPath().substring(7), req); // 去掉 /static
                } else {
                    // 对于其他方法（如 POST），先探测资源是否存在：若不存在返回 ResourceHandler 的 404，存在则返回 405
                    Response probe = resourceHandler.getStaticResource(req.getPath().substring(7), req);
                    if (probe.getStatusCode() == 404) {
                        return probe;
                    } else {
                        return new405();
                    }
                }
            }

            // ----- 304 Not Modified -----
            // 由 ResourceHandler 处理静态资源的 If-Modified-Since/Last-Modified 比较。
            // 这里不要做通用的基于请求头的直接返回，以免干扰其他路由的正确判断。
            // 真的找不到
            // 路径命中但方法不对
            boolean hasPath = routeTable.keySet().stream()
                    .anyMatch(k -> k.endsWith(" " + req.getPath()));
            if (hasPath)
                return new405();
            return new404();
        } catch (Exception e) {
            e.printStackTrace(); // 看控制台
            return new500();
        }
    }

    private Response new404() {
        Response resp = new Response();
        resp.setStatusCode(404);
        resp.setReasonPhrase("Not Found");
        resp.setHeader("Content-Type", "text/plain; charset=utf-8");
        resp.setBody("404 Not Found".getBytes());
        return resp;
    }

    private Response new405() {
        Response r = new Response();
        r.setStatusCode(405);
        r.setReasonPhrase("Method Not Allowed");
        r.setHeader("Allow", "GET, POST");
        r.setHeader("Content-Type", "text/plain");
        r.setBody("405 Method Not Allowed".getBytes());
        return r;
    }

    private Response new500() {
        Response r = new Response();
        r.setStatusCode(500);
        r.setReasonPhrase("Internal Server Error");
        r.setHeader("Content-Type", "text/plain");
        r.setBody("500 Internal Server Error".getBytes());
        return r;
    }
}
