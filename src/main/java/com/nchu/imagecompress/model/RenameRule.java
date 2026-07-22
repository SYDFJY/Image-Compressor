package com.nchu.imagecompress.model;

import com.nchu.imagecompress.util.FileUtil;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 批量重命名规则 — 定义文件名模板与 Token 替换逻辑。
 *
 * <h3>支持的 Token</h3>
 * <pre>
 *   {date}         → 从文件名提取日期，失败时使用文件修改时间
 *   {date:格式}     → 指定日期格式（如 {date:yyyyMMdd}）
 *   {counter}      → 递增序号（自动位数）
 *   {counter:N}    → 固定 N 位补零序号
 *   {name}         → 原始文件名（不含扩展名）
 *   {ext}          → 扩展名（不含点）
 *   {text}         → 自定义文本
 * </pre>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-22
 */
public class RenameRule {

    /** 文件名模板，如 "{date}_{text}_{counter:3}" */
    private String pattern = "{name}";

    /** 起始序号 */
    private int counterStart = 1;

    /** 固定位数（0 = 自动，1-6 = 固定补零） */
    private int counterDigits = 0;

    /** 日期提取格式 */
    private String datePattern = "yyyyMMdd";

    /** 自定义文本 */
    private String customText = "";

    // 预编译的日期提取正则
    private static final Pattern DATE_YYYYMMDD_HHMMSS = Pattern.compile("\\d{8}_\\d{6}");
    private static final Pattern DATE_YYYYMMDD = Pattern.compile("\\d{8}");
    private static final Pattern DATE_YYYY_MM_DD = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    public RenameRule() {}

    public RenameRule(String pattern) {
        this.pattern = pattern;
    }

    // ==================== Token 替换 ====================

    /**
     * 对单个文件应用重命名规则，返回新文件名（含扩展名）。
     *
     * @param fileInfo 文件信息
     * @param counter  当前序号（从 1 开始）
     * @return 新文件名（含扩展名）
     */
    public String apply(FileInfo fileInfo, int counter) {
        String result = pattern;
        String originalName = FileUtil.getNameWithoutExtension(fileInfo.getFileName());
        String extension = FileUtil.getExtension(fileInfo.getFileName());

        // {date} 或 {date:格式}
        result = replaceDateTokens(result, fileInfo);

        // {counter} 或 {counter:N}
        result = replaceCounterTokens(result, counter);

        // {name}
        result = result.replace("{name}", originalName);

        // {ext}
        result = result.replace("{ext}", extension);

        // {text}
        result = result.replace("{text}", customText);

        // 清理多余空格
        result = result.replaceAll("\\s+", "_").replaceAll("_+", "_");

        // 拼回扩展名
        if (extension != null && !extension.isEmpty()) {
            result = result + "." + extension;
        }

        return result;
    }

    // ==================== 日期提取 ====================

    /**
     * 从文件名中提取日期字符串。
     * 按优先级尝试多种格式，都不匹配时使用文件修改时间。
     */
    private String extractDateFromFilename(String filename) {
        // 1. yyyyMMdd_HHmmss
        Matcher m1 = DATE_YYYYMMDD_HHMMSS.matcher(filename);
        if (m1.find()) return m1.group();

        // 2. yyyyMMdd
        Matcher m2 = DATE_YYYYMMDD.matcher(filename);
        if (m2.find()) return m2.group();

        // 3. yyyy-MM-dd
        Matcher m3 = DATE_YYYY_MM_DD.matcher(filename);
        if (m3.find()) return m3.group();

        return null;
    }

    private String replaceDateTokens(String template, FileInfo fileInfo) {
        // {date:格式}
        Pattern p = Pattern.compile("\\{date:([^}]+)\\}");
        Matcher m = p.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String fmt = m.group(1);
            String dateStr = extractDateFromFilename(fileInfo.getFileName());
            if (dateStr == null) {
                // 回退：使用文件修改时间
                File src = fileInfo.getSourceFile();
                long modTime = (src != null && src.exists()) ? src.lastModified() : System.currentTimeMillis();
                dateStr = new SimpleDateFormat(fmt).format(new Date(modTime));
            } else {
                // 解析已提取的日期，按目标格式重新格式化
                try {
                    Date parsed = parseDate(dateStr);
                    if (parsed != null) {
                        dateStr = new SimpleDateFormat(fmt).format(parsed);
                    }
                } catch (Exception ignored) {
                    // 格式化失败，使用原始提取字符串
                }
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(dateStr));
        }
        m.appendTail(sb);

        // {date}（无格式指定）
        String dateReplacement = extractDateFromFilename(fileInfo.getFileName());
        if (dateReplacement == null) {
            File src = fileInfo.getSourceFile();
            long modTime = (src != null && src.exists()) ? src.lastModified() : System.currentTimeMillis();
            dateReplacement = new SimpleDateFormat(datePattern).format(new Date(modTime));
        }
        return sb.toString().replace("{date}", dateReplacement);
    }

    /**
     * 解析常见日期格式字符串。
     */
    private static Date parseDate(String dateStr) {
        String[] fmts = {"yyyyMMdd_HHmmss", "yyyyMMdd", "yyyy-MM-dd"};
        for (String fmt : fmts) {
            try {
                return new SimpleDateFormat(fmt).parse(dateStr);
            } catch (Exception ignored) {}
        }
        return null;
    }

    // ==================== 计数器替换 ====================

    private String replaceCounterTokens(String template, int counter) {
        // {counter:N}
        Pattern p = Pattern.compile("\\{counter:(\\d+)\\}");
        Matcher m = p.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            int digits = Integer.parseInt(m.group(1));
            String val = String.format("%0" + digits + "d",
                    counterStart + counter - 1);
            m.appendReplacement(sb, Matcher.quoteReplacement(val));
        }
        m.appendTail(sb);

        // {counter}（自动位数）
        int autoDigits = counterDigits > 0 ? counterDigits
                : Math.max(1, (int) Math.log10(counterStart + counter - 1) + 1);
        String autoVal = String.format("%0" + autoDigits + "d",
                counterStart + counter - 1);
        return sb.toString().replace("{counter}", autoVal);
    }

    // ==================== 文件名校验 ====================

    /**
     * 检查文件名是否有效（无非法字符，长度合法）。
     */
    public static boolean isValidFilename(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        // Windows 非法字符
        if (name.matches(".*[<>:\"/\\\\|?*].*")) return false;
        // 长度限制（Windows 255 字符）
        if (name.length() > 255) return false;
        // 不能以空格或点结尾
        if (name.trim().isEmpty()) return false;
        return true;
    }

    // ==================== Getter / Setter ====================

    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }

    public int getCounterStart() { return counterStart; }
    public void setCounterStart(int counterStart) { this.counterStart = counterStart; }

    public int getCounterDigits() { return counterDigits; }
    public void setCounterDigits(int counterDigits) { this.counterDigits = counterDigits; }

    public String getDatePattern() { return datePattern; }
    public void setDatePattern(String datePattern) { this.datePattern = datePattern; }

    public String getCustomText() { return customText; }
    public void setCustomText(String customText) { this.customText = customText; }
}
