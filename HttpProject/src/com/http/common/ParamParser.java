package common;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

public class ParamParser {

    /** 解析查询串（a=1&b=2）或表单体 */
    public static Map<String, String> parseParamString(String paramStr) {
        Map<String, String> map = new HashMap<>();
        if (paramStr == null || paramStr.isEmpty()) return map;

        String[] pairs = paramStr.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            String key = urlDecode(kv[0]);
            String val = kv.length > 1 ? urlDecode(kv[1]) : "";
            map.put(key, val);
        }
        return map;
    }

    /** 把请求体整个当表单读 */
    public static Map<String, String> parseBody(Request req) {
        String body = new String(req.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        return parseParamString(body);
    }

    private static String urlDecode(String s) {
        try { return URLDecoder.decode(s, "UTF-8"); }
        catch (UnsupportedEncodingException e) { return s; }
    }
}