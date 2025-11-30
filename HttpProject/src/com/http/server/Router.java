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
    private final ResourceHandler resourceHandler = new ResourceHandler();

    public Router() {
        // 业务路由
        UserHandler userHandler = new UserHandler();
        routeTable.put("POST /user/login", userHandler::login);
        routeTable.put("POST /user/register", userHandler::register);
    }

    /** 唯一暴露的方法：把请求扔进来，把响应拿出去 */
    public Response dispatch(Request req) {
        try {
            String key = req.getMethod() + " " + req.getPath();
            Function<Request, Response> handler = routeTable.get(key);

            if (handler != null)
                return handler.apply(req);

            // 没有业务路由就交给静态资源处理器
            // Router.dispatch() 里替换原来的静态分支
            if ("GET".equals(req.getMethod()) && req.getPath().startsWith("/static/")) {
                //System.out.println("Serving static resource: " + req.getPath());
                return resourceHandler.getStaticResource(req.getPath().substring(7), req);// 去掉 /static
            }else if("HEAD".equals(req.getMethod()) && req.getPath().startsWith("/static/")){
                return resourceHandler.getStaticResource(req.getPath().substring(7), req);// 去掉 /static
            }
            // ----- 304 Not Modified -----
            String ifModified = req.getHeader("if-modified-since");
            if (ifModified != null && !ifModified.isEmpty()) {
                // 演示：直接返回 304，正式项目要对比时间戳
                Response r = new Response();
                r.setStatusCode(304);
                r.setReasonPhrase("Not Modified");
                return r;
            }
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