package common;

// 导入HashMap：用于存储文件后缀与MIME类型的映射关系（键值对）
import java.util.HashMap;
// 导入Locale：提供统一的区域设置，确保字符串大小写转换时不受系统区域影响
import java.util.Locale;
// 导入Map：定义键值对集合的接口，用于声明映射对象类型
import java.util.Map;

/**
 * MIME类型工具类：提供文件后缀到Content-Type的映射、Content-Type归一化、
 * 文本/图片类型判断等功能，支持核心MIME类型（text/html、application/json、image/*等）
 */
public class MimeUtil {
    // 静态常量：文件后缀到MIME类型的映射表（键：小写文件后缀，值：对应的Content-Type）
    // 静态变量属于类级别的资源，仅初始化一次，避免重复创建
    private static final Map<String, String> EXT_TO_MIME = new HashMap<>();

    // 静态代码块：初始化文件后缀与MIME类型的映射关系（类加载时执行，仅执行一次）
    static {
        // HTML相关后缀：.html、.htm → text/html（UTF-8编码）
        EXT_TO_MIME.put("html", "text/html; charset=utf-8");
        EXT_TO_MIME.put("htm", "text/html; charset=utf-8");
        // JSON后缀：.json → application/json（UTF-8编码）
        EXT_TO_MIME.put("json", "application/json; charset=utf-8");
        // JPEG图片后缀：.jpg、.jpeg → image/jpeg（图片类型无编码参数）
        EXT_TO_MIME.put("jpg", "image/jpeg");
        EXT_TO_MIME.put("jpeg", "image/jpeg");
        // PNG图片后缀：.png → image/png
        EXT_TO_MIME.put("png", "image/png");
        // 文本文件后缀：.txt → text/plain（UTF-8编码）
        EXT_TO_MIME.put("txt", "text/plain; charset=utf-8");
    }

    /**
     * 根据文件名后缀获取对应的Content-Type（MIME类型）
     * 未知后缀或无后缀时，返回默认的二进制类型application/octet-stream
     * @param filename 文件名（如index.html、data.json、photo.jpg）
     * @return 对应的Content-Type字符串，默认返回application/octet-stream
     */
    public static String fromFilename(String filename) {
        // 文件名若为null，直接返回默认MIME类型（避免空指针异常）
        if (filename == null) return "application/octet-stream";
        // 查找文件名中最后一个"."的索引（用于提取后缀）
        int idx = filename.lastIndexOf('.');
        // 无后缀情况：1. 未找到"."（idx=-1）；2. "."在文件名末尾（如"file."）
        if (idx == -1 || idx == filename.length() - 1) {
            return "application/octet-stream";
        }
        // 提取后缀：从"."的下一个字符开始截取，转为小写（统一格式，避免大小写敏感）
        // Locale.ROOT：使用根区域设置，确保不同系统下大小写转换规则一致
        String ext = filename.substring(idx + 1).toLowerCase(Locale.ROOT);
        // 从映射表中获取对应的MIME类型，无匹配时返回默认值
        return EXT_TO_MIME.getOrDefault(ext, "application/octet-stream");
    }

    /**
     * 归一化Content-Type头部字符串：提取主类型部分，去除参数和空格，转为小写
     * 例："Text/HTML; charset=UTF-8" → "text/html"；"Application/JSON" → "application/json"
     * @param contentTypeHeader 原始的Content-Type头部值（可能含参数、大小写混合、空格）
     * @return 归一化后的主类型字符串，输入为null时返回null
     */
    public static String normalizeContentType(String contentTypeHeader) {
        // 输入为null，直接返回null（不处理）
        if (contentTypeHeader == null) return null;
        // 按分号";"分割：取第一部分（主类型，忽略后续参数如charset）
        // trim()：去除前后空格（处理" text/html "这类情况）
        // toLowerCase(Locale.ROOT)：转为小写，统一格式
        String ct = contentTypeHeader.split(";")[0].trim().toLowerCase(Locale.ROOT);
        // 返回归一化后的主类型
        return ct;
    }

    /**
     * 判断Content-Type是否为文本类型（含text/*和application/json）
     * @param contentTypeHeader 原始的Content-Type头部值（如"text/html; charset=utf-8"）
     * @return 是文本类型返回true，否则返回false
     */
    public static boolean isTextType(String contentTypeHeader) {
        // 先归一化Content-Type，得到主类型（如"text/html"、"application/json"）
        String ct = normalizeContentType(contentTypeHeader);
        // 文本类型判断规则：
        // 1. 归一化后的类型不为null
        // 2. 是text/html（HTML文件）、application/json（JSON数据）
        // 3. 以text/开头（如text/plain、text/css等）
        return ct != null && (ct.equals("text/html") || ct.equals("application/json") || ct.startsWith("text/"));
    }

    /**
     * 判断Content-Type是否为图片类型（image/*）
     * @param contentTypeHeader 原始的Content-Type头部值（如"image/jpeg"、"image/png; name=photo"）
     * @return 是图片类型返回true，否则返回false
     */
    public static boolean isImageType(String contentTypeHeader) {
        // 先归一化Content-Type，得到主类型（如"image/jpeg"、"image/png"）
        String ct = normalizeContentType(contentTypeHeader);
        // 图片类型判断规则：归一化后的类型不为null，且以image/开头（符合HTTP MIME类型规范）
        return ct != null && ct.startsWith("image/");
    }
}