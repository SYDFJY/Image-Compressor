package com.nchu.imagecompress.model;

import java.awt.Color;
import java.awt.Font;

/**
 * 主题配色板 — 封装一套完整主题的所有设计令牌（颜色/字体/间距/阴影/圆角）。
 *
 * <p>每套主题是一个不可变的值对象，由 {@link com.nchu.imagecompress.util.ThemeUtil} 在切换主题时加载。
 * 6 套预设主题通过静态工厂方法创建。</p>
 *
 * <h3>设计令牌层级</h3>
 * <ul>
 *   <li><b>主色阶</b>：5 级梯度（深→浅），用于按钮/选中/悬停/发光</li>
 *   <li><b>背景层</b>：窗口根背景 / 卡片 / 输入框 / 选中行 / 悬停行</li>
 *   <li><b>文字层</b>：一级 / 二级 / 三级</li>
 *   <li><b>边框分割线</b>：默认 / 悬停</li>
 *   <li><b>功能状态色</b>：成功 / 失败 / 警告</li>
 * </ul>
 *
 * @author NCHU-Student
 * @version 1.0.0
 * @since 2026-07-09
 */
public class ThemePalette {

    // ==================== 主色阶（5 级） ====================
    public final Color primaryDeepest;   // 按下态、最深强调
    public final Color primaryDeep;      // 选中填充、悬停
    public final Color primary;          // 核心主色
    public final Color primaryLight;     // 悬停高光、进度亮端
    public final Color primaryLightest;  // 极淡、图标悬停

    // ==================== 背景层 ====================
    public final Color bgWindow;         // 窗口根背景
    public final Color bgCard;           // 卡片底色
    public final Color bgInput;          // 输入框默认底色
    public final Color bgSelected;       // 选中行背景
    public final Color bgHover;          // 悬停背景

    // ==================== 文字层 ====================
    public final Color textPrimary;      // 一级文字
    public final Color textSecondary;    // 二级文字
    public final Color textTertiary;     // 三级文字

    // ==================== 边框分割线 ====================
    public final Color border;           // 普通边框
    public final Color borderHover;      // 悬停边框

    // ==================== 功能状态色 ====================
    public final Color success;
    public final Color successLight;
    public final Color error;
    public final Color errorLight;
    public final Color warning;
    public final Color warningLight;

    // ==================== 阴影参数 ====================
    /** 一级轻阴影 RGBA: r,g,b,alpha (0-255) */
    public final int shadow1R, shadow1G, shadow1B, shadow1Alpha;
    /** 二级中阴影 */
    public final int shadow2R, shadow2G, shadow2B, shadow2Alpha;

    // ==================== 专属质感 ====================
    /** 卡片是否有 1px 描边（暗色主题需要） */
    public final boolean cardHasBorder;
    /** 卡片描边色 */
    public final Color cardBorderColor;
    /** 主按钮是否有外发光 */
    public final boolean buttonHasGlow;
    /** 主按钮发光色 */
    public final Color buttonGlowColor;
    /** 分段控件样式: "A"=深底白字, "B"=浅底深字, "C"=下划线 */
    public final String segmentedStyle;

    // ==================== 字体（主题间一致，保留定制能力） ====================
    public final Font fontTitle;
    public final Font fontBody;
    public final Font fontSmall;
    public final Font fontTiny;
    public final Font fontMono;

    // ==================== 间距/圆角（主题间一致） ====================
    public static final int ARC_CARD = 12;
    public static final int ARC_BUTTON = 8;
    public static final int ARC_TAG = 6;
    public static final int ARC_THUMB = 4;
    public static final int ARC_PILL = 999;

    public static final int SPACE_XS = 4;
    public static final int SPACE_SM = 8;
    public static final int SPACE_MD = 12;
    public static final int SPACE_LG = 16;
    public static final int SPACE_XL = 20;
    public static final int SPACE_XXL = 24;

    /** 区块外边距 (16px) — 卡片/区域之间 */
    public static final int SPACE_BLOCK = 16;
    /** 行内间距 (8px) — 表单行垂直间距 */
    public static final int SPACE_ROW = 8;
    /** 标签-控件间距 (12px) — 标签与输入控件的横向距离 */
    public static final int SPACE_LABEL_GAP = 12;

    // ==================== 私有构造 ====================

    private ThemePalette(Builder b) {
        this.primaryDeepest = b.primaryDeepest;
        this.primaryDeep = b.primaryDeep;
        this.primary = b.primary;
        this.primaryLight = b.primaryLight;
        this.primaryLightest = b.primaryLightest;

        this.bgWindow = b.bgWindow;
        this.bgCard = b.bgCard;
        this.bgInput = b.bgInput;
        this.bgSelected = b.bgSelected;
        this.bgHover = b.bgHover;

        this.textPrimary = b.textPrimary;
        this.textSecondary = b.textSecondary;
        this.textTertiary = b.textTertiary;

        this.border = b.border;
        this.borderHover = b.borderHover;

        this.success = b.success;
        this.successLight = b.successLight;
        this.error = b.error;
        this.errorLight = b.errorLight;
        this.warning = b.warning;
        this.warningLight = b.warningLight;

        this.shadow1R = b.shadow1R; this.shadow1G = b.shadow1G;
        this.shadow1B = b.shadow1B; this.shadow1Alpha = b.shadow1Alpha;
        this.shadow2R = b.shadow2R; this.shadow2G = b.shadow2G;
        this.shadow2B = b.shadow2B; this.shadow2Alpha = b.shadow2Alpha;

        this.cardHasBorder = b.cardHasBorder;
        this.cardBorderColor = b.cardBorderColor;
        this.buttonHasGlow = b.buttonHasGlow;
        this.buttonGlowColor = b.buttonGlowColor;
        this.segmentedStyle = b.segmentedStyle;

        this.fontTitle = b.fontTitle;
        this.fontBody = b.fontBody;
        this.fontSmall = b.fontSmall;
        this.fontTiny = b.fontTiny;
        this.fontMono = b.fontMono;
    }

    // ==================== 阴影颜色快捷方法 ====================

    public Color getShadow1() {
        return new Color(shadow1R, shadow1G, shadow1B, shadow1Alpha);
    }

    public Color getShadow2() {
        return new Color(shadow2R, shadow2G, shadow2B, shadow2Alpha);
    }

    // ==================== Builder ====================

    public static class Builder {
        private Color primaryDeepest, primaryDeep, primary, primaryLight, primaryLightest;
        private Color bgWindow, bgCard, bgInput, bgSelected, bgHover;
        private Color textPrimary, textSecondary, textTertiary;
        private Color border, borderHover;
        private Color success, successLight, error, errorLight, warning, warningLight;
        private int shadow1R, shadow1G, shadow1B, shadow1Alpha;
        private int shadow2R, shadow2G, shadow2B, shadow2Alpha;
        private boolean cardHasBorder;
        private Color cardBorderColor;
        private boolean buttonHasGlow;
        private Color buttonGlowColor;
        private String segmentedStyle = "A";
        private Font fontTitle, fontBody, fontSmall, fontTiny, fontMono;

        // 默认字体
        {
            fontTitle  = new Font("Microsoft YaHei", Font.BOLD, 15);
            fontBody   = new Font("Microsoft YaHei", Font.PLAIN, 14);
            fontSmall  = new Font("Microsoft YaHei", Font.PLAIN, 12);
            fontTiny   = new Font("Microsoft YaHei", Font.PLAIN, 11);
            fontMono   = new Font("Consolas", Font.PLAIN, 13);
        }

        public Builder primary(Color d, Color dp, Color p, Color pl, Color plt) {
            this.primaryDeepest = d; this.primaryDeep = dp;
            this.primary = p; this.primaryLight = pl; this.primaryLightest = plt;
            return this;
        }
        public Builder background(Color win, Color card, Color input, Color sel, Color hov) {
            this.bgWindow = win; this.bgCard = card; this.bgInput = input;
            this.bgSelected = sel; this.bgHover = hov;
            return this;
        }
        public Builder text(Color p, Color s, Color t) {
            this.textPrimary = p; this.textSecondary = s; this.textTertiary = t;
            return this;
        }
        public Builder border(Color b, Color bh) {
            this.border = b; this.borderHover = bh;
            return this;
        }
        public Builder status(Color s, Color sl, Color e, Color el, Color w, Color wl) {
            this.success = s; this.successLight = sl;
            this.error = e; this.errorLight = el;
            this.warning = w; this.warningLight = wl;
            return this;
        }
        public Builder shadow1(int r, int g, int b, int a) {
            this.shadow1R = r; this.shadow1G = g; this.shadow1B = b; this.shadow1Alpha = a;
            return this;
        }
        public Builder shadow2(int r, int g, int b, int a) {
            this.shadow2R = r; this.shadow2G = g; this.shadow2B = b; this.shadow2Alpha = a;
            return this;
        }
        public Builder cardBorder(Color c) {
            this.cardHasBorder = true; this.cardBorderColor = c;
            return this;
        }
        public Builder buttonGlow(Color c) {
            this.buttonHasGlow = true; this.buttonGlowColor = c;
            return this;
        }
        public Builder segmented(String style) {
            this.segmentedStyle = style;
            return this;
        }
        public ThemePalette build() {
            return new ThemePalette(this);
        }
    }

    // ==================== 6 套预设主题工厂方法 ====================

    /** 主题一：默认蓝调 · 经典专业款 */
    public static ThemePalette blueClassic() {
        return new Builder()
                .primary(c(0x1D4ED8), c(0x2563EB), c(0x3B82F6), c(0x60A5FA), c(0x93C5FD))
                .background(c(0xF1F5F9), c(0xFFFFFF), c(0xF8FAFC), c(0xEFF6FF), c(0xDBEAFE))
                .text(c(0x0F172A), c(0x334155), c(0x64748B))
                .border(c(0xE2E8F0), c(0xCBD5E1))
                .status(c(0x10B981), c(0xECFDF5), c(0xEF4444), c(0xFEF2F2), c(0xF59E0B), c(0xFFFBEB))
                .shadow1(15, 23, 42, 15)
                .shadow2(15, 23, 42, 20)
                .segmented("A")
                .build();
    }

    /** 主题二：薄荷曼波 · 清新绿调 */
    public static ThemePalette mintGreen() {
        return new Builder()
                .primary(c(0x065F46), c(0x059669), c(0x10B981), c(0x34D399), c(0x6EE7B7))
                .background(c(0xF0FDF4), c(0xFFFFFF), c(0xF8FAFC), c(0xECFDF5), c(0xDCFCE7))
                .text(c(0x064E3B), c(0x047857), c(0x6EE7B7))
                .border(c(0xD1FAE5), c(0xA7F3D0))
                .status(c(0x059669), c(0xECFDF5), c(0xDC2626), c(0xFEF2F2), c(0xD97706), c(0xFFFBEB))
                .shadow1(16, 185, 129, 20)
                .shadow2(16, 185, 129, 20)
                .segmented("B")
                .build();
    }

    /** 主题三：暖阳奶咖 · 暖棕复古风 */
    public static ThemePalette warmCaramel() {
        return new Builder()
                .primary(c(0x78350F), c(0x92400E), c(0xD97706), c(0xF59E0B), c(0xFBBF24))
                .background(c(0xFFFBEB), c(0xFFFEF9), c(0xFFFBF0), c(0xFFFBEB), c(0xFEF3C7))
                .text(c(0x78350F), c(0xB45309), c(0xFBBF24))
                .border(c(0xFDE68A), c(0xFCD34D))
                .status(c(0x059669), c(0xECFDF5), c(0xDC2626), c(0xFEF2F2), c(0xD97706), c(0xFFFBEB))
                .shadow1(217, 119, 6, 20)
                .shadow2(217, 119, 6, 20)
                .segmented("B")
                .build();
    }

    /** 主题四：极简墨灰 · 无彩商务风 */
    public static ThemePalette monoGray() {
        return new Builder()
                .primary(c(0x0F172A), c(0x1E293B), c(0x334155), c(0x64748B), c(0x94A3B8))
                .background(c(0xF8FAFC), c(0xFFFFFF), c(0xF8FAFC), c(0xF1F5F9), c(0xF1F5F9))
                .text(c(0x0F172A), c(0x334155), c(0x94A3B8))
                .border(c(0xE2E8F0), c(0xCBD5E1))
                .status(c(0x059669), c(0xECFDF5), c(0xDC2626), c(0xFEF2F2), c(0xD97706), c(0xFFFBEB))
                .shadow1(15, 23, 42, 15)
                .shadow2(15, 23, 42, 20)
                .segmented("C")
                .build();
    }

    /** 主题五：暗夜紫晶 · 深紫科技风 */
    public static ThemePalette darkPurple() {
        return new Builder()
                .primary(c(0x7C3AED), c(0x8B5CF6), c(0xA78BFA), c(0xC4B5FD), c(0xDDD6FE))
                .background(c(0x1E1B4B), c(0x312E81), c(0x3730A3), c(0x4338CA), c(0x4338CA))
                .text(c(0xE0E7FF), c(0xA5B4FC), c(0x818CF8))
                .border(c(0x4F46E5), c(0x6366F1))
                .status(c(0x34D399), c(0x1E1B4B), c(0xF87171), c(0x1E1B4B), c(0xFBBF24), c(0x1E1B4B))
                .shadow1(0, 0, 0, 40)
                .shadow2(0, 0, 0, 60)
                .cardBorder(new Color(167, 139, 250, 51))
                .buttonGlow(new Color(167, 139, 250, 76))
                .segmented("A")
                .build();
    }

    /** 主题七：多巴胺暖 · 温暖活力风 */
    public static ThemePalette dopamineWarm() {
        return new Builder()
                .primary(c(0xE04A60), c(0xFF7185), c(0xFF8A7A), c(0xFFA562), c(0xFFB26B))
                .background(c(0xFFFAF7), c(0xFFFFFF), c(0xFFFBF8), c(0xFFF0EB), c(0xFFEDE4))
                .text(c(0x2D1B1E), c(0x6B4C52), c(0xA08088))
                .border(c(0xF5E0D3), c(0xE8CDBD))
                .status(c(0x10B981), c(0xECFDF5), c(0xE04A60), c(0xFFF0EB), c(0xF59E0B), c(0xFFFBEB))
                .shadow1(255, 113, 133, 20)
                .shadow2(255, 113, 133, 25)
                .buttonGlow(new Color(255, 141, 122, 60))
                .segmented("B")
                .build();
    }

    /** 主题六：深海蓝调 · 藏青沉稳风 */
    public static ThemePalette deepNavy() {
        return new Builder()
                .primary(c(0x1D4ED8), c(0x3B82F6), c(0x60A5FA), c(0x93C5FD), c(0xBFDBFE))
                .background(c(0x0F172A), c(0x1E293B), c(0x334155), c(0x1E40AF), c(0x1E40AF))
                .text(c(0xE2E8F0), c(0x94A3B8), c(0x64748B))
                .border(c(0x334155), c(0x475569))
                .status(c(0x10B981), c(0x0F172A), c(0xEF4444), c(0x0F172A), c(0xF59E0B), c(0x0F172A))
                .shadow1(0, 0, 0, 40)
                .shadow2(0, 0, 0, 60)
                .cardBorder(new Color(96, 165, 250, 38))
                .segmented("A")
                .build();
    }

    /** 便捷：hex int → Color */
    private static Color c(int hex) {
        return new Color(hex);
    }
}
