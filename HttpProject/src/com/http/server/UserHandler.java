package server;

import common.Request;
import common.Response;
import common.ParamParser;

import java.util.Map;

public class UserHandler {

    // 临时用内存 map 代替数据库
    private static final Map<String, String> userDB = new java.util.concurrent.ConcurrentHashMap<>();

    /** 注册：POST /user/register */
    public Response register(Request req) {
        Map<String, String> p = ParamParser.parseBody(req);
        String username = p.get("username");
        String password = p.get("password");

        Response resp = new Response();
        if (username == null || password == null) {
            return jsonResp(resp, 400, "{\"msg\":\"缺少字段\"}");
        }
        if (userDB.putIfAbsent(username, password) != null) {
            return jsonResp(resp, 409, "{\"msg\":\"用户名已存在\"}");
        }
        return jsonResp(resp, 200, "{\"msg\":\"注册成功\"}");
    }

    /** 登录：POST /user/login */
    public Response login(Request req) {
        Map<String, String> p = ParamParser.parseBody(req);
        String username = p.get("username");
        String password = p.get("password");

        Response resp = new Response();
        if (username == null || password == null) {
            return jsonResp(resp, 400, "{\"msg\":\"缺少字段\"}");
        }
        if (!password.equals(userDB.get(username))) {
            return jsonResp(resp, 401, "{\"msg\":\"账号或密码错误\"}");
        }
        // 简单会话：把用户名写 Cookie 里（实际项目要生成 token）
        resp.setHeader("Set-Cookie", "user=" + username + "; Path=/");
        return jsonResp(resp, 200, "{\"msg\":\"登录成功\"}");
    }

    private Response jsonResp(Response resp, int code, String json) {
        resp.setStatusCode(code);
        resp.setReasonPhrase(code == 200 ? "OK" : "Error");
        resp.setHeader("Content-Type", "application/json; charset=utf-8");
        resp.setBody(json.getBytes());
        return resp;
    }
}