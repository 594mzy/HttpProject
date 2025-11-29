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
        routeTable.put("POST /user/login",   userHandler::login);
        routeTable.put("POST /user/register",userHandler::register);
    }

    /** 唯一暴露的方法：把请求扔进来，把响应拿出去 */
    public Response dispatch(Request req) {
        String key = req.getMethod() + " " + req.getPath();
        Function<Request, Response> handler = routeTable.get(key);

        if (handler != null) return handler.apply(req);

        // 没有业务路由就交给静态资源处理器
        if (req.getMethod().equals("GET")) return resourceHandler.getStaticResource(req.getPath());

        // 真的找不到
        return new404();
    }

    private Response new404() {
        Response resp = new Response();
        resp.setStatusCode(404);
        resp.setReasonPhrase("Not Found");
        resp.setHeader("Content-Type", "text/plain; charset=utf-8");
        resp.setBody("404 Not Found".getBytes());
        return resp;
    }
}