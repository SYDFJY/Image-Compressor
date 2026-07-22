package com.nchu.imagecompress.model;

/**
 * 界面主题枚举（v2 — 6 套自定义主题）。
 *
 * <p>每套主题对应一个 {@link ThemePalette} 配色板。
 * 切���主题时通过 {@code ThemePalette} 工厂方法加载完整设计令牌。</p>
 *
 * @author NCHU-Student
 * @version 2.0.0
 * @since 2026-07-08
 */
public enum Theme {

    // ========== v4 蓝韵系列 6 套新主题 ==========

    /** 蓝韵 · 清爽现代风 */
    BLUE_RHYME("蓝韵", "清爽现代风", "#1A6FFF", ThemePalette.blueRhyme()),

    /** 海洋青 · 深邃海洋 */
    OCEAN("海洋青", "深邃海洋", "#0891B2", ThemePalette.ocean()),

    /** 深邃黑蓝 · 暗夜经典 */
    DARK("深邃黑蓝", "暗夜经典", "#4D94FF", ThemePalette.dark()),

    /** 落日橙 · 温暖暮色 */
    SUNSET("落日橙", "温暖暮色", "#F59E0B", ThemePalette.sunset()),

    /** 森林绿 · 自然暗绿 */
    FOREST("森林绿", "自然暗绿", "#22C55E", ThemePalette.forest()),

    /** 薰衣草紫 · 梦幻紫调 */
    LAVENDER("薰衣草紫", "梦幻紫调", "#8B5CF6", ThemePalette.lavender()),

    // ========== 原有 7 套经典主题 ==========

    /** 多巴胺暖 · 温暖活力风 */
    DOPAMINE("多巴胺暖", "温暖活力风", "#FF8A7A", ThemePalette.dopamineWarm()),

    /** 默认蓝调 · 经典专业款 */
    BLUE_CLASSIC("默认蓝调", "经典专业款", "#3B82F6", ThemePalette.blueClassic()),

    /** 薄荷曼波 · 清新绿调 */
    MINT_GREEN("薄荷曼波", "清新绿调", "#10B981", ThemePalette.mintGreen()),

    /** 暖阳奶咖 · 暖棕复古风 */
    WARM_CARAMEL("暖阳奶咖", "暖棕复古风", "#D97706", ThemePalette.warmCaramel()),

    /** 极简墨灰 · 无彩商务风 */
    MONO_GRAY("极简墨灰", "无彩商务风", "#334155", ThemePalette.monoGray()),

    /** 暗夜紫晶 · 深紫科技风 */
    DARK_PURPLE("暗夜紫晶", "深紫科技风", "#A78BFA", ThemePalette.darkPurple()),

    /** 深海蓝调 · 藏青沉稳风 */
    DEEP_NAVY("深海蓝调", "藏青沉稳风", "#60A5FA", ThemePalette.deepNavy()),

    // ==================== 撞色系列 5 套新主题（浅色） ====================

    /** 紫黑电竞 · 专业神秘感（浅色） */
    PURPLE_GAMING("紫黑电竞", "专业神秘", "#A855F7", ThemePalette.purpleGaming()),

    /** 绿米自然 · 温润自然感（浅色） */
    GREEN_NATURE("绿米自然", "温润自然", "#73AE52", ThemePalette.greenNature()),

    /** 雾蓝冷感 · 工程师调性（浅色） */
    MIST_BLUE("雾蓝冷感", "冷静专业", "#0EA5E9", ThemePalette.mistBlue()),

    /** 嫩绿蓝清新 · 年轻活力感（浅色） */
    FRESH_GREEN_BLUE("嫩绿蓝清新", "年轻活力", "#05A5FA", ThemePalette.freshGreenBlue()),

    /** 浅粉红暖意 · 温柔有力（浅色） */
    SOFT_PINK("浅粉红暖意", "温柔有力", "#E72D48", ThemePalette.softPink()),

    // ==================== 撞色系列 5 套新主题（深色） ====================

    /** 紫黑电竞 · 深紫未来感（深色） */
    PURPLE_GAMING_DARK("紫黑电竞·暗", "深紫未来感", "#A855F7", ThemePalette.purpleGamingDark()),

    /** 绿米自然 · 暗夜自然风（深色） */
    GREEN_NATURE_DARK("绿米自然·暗", "暗夜自然风", "#73AE52", ThemePalette.greenNatureDark()),

    /** 雾蓝冷感 · 夜蓝专注（深色） */
    MIST_BLUE_DARK("雾蓝冷感·暗", "夜蓝专注", "#0EA5E9", ThemePalette.mistBlueDark()),

    /** 嫩绿蓝清新 · 深海活力（深色） */
    FRESH_GREEN_BLUE_DARK("嫩绿蓝清新·暗", "深海活力", "#05A5FA", ThemePalette.freshGreenBlueDark()),

    /** 浅粉红暖意 · 夜红酒红（深色） */
    SOFT_PINK_DARK("浅粉红暖意·暗", "夜红酒红", "#E72D48", ThemePalette.softPinkDark());

    /** 主题显示名称 */
    private final String displayName;

    /** 副标题（风格描述） */
    private final String subtitle;

    /** 主色色值（用于色块显示） */
    private final String primaryHex;

    /** 配色板（设计令牌全集） */
    private final ThemePalette palette;

    Theme(String displayName, String subtitle, String primaryHex, ThemePalette palette) {
        this.displayName = displayName;
        this.subtitle = subtitle;
        this.primaryHex = primaryHex;
        this.palette = palette;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getPrimaryHex() {
        return primaryHex;
    }

    public ThemePalette getPalette() {
        return palette;
    }

    /**
     * 根据主题名称查找对应枚举（不区分大小写）。
     *
     * @param name 主题名（支持旧名称兼容：LIGHT/DARK/INTELLIJ/DARCULA → 默认蓝调/暗夜紫晶）
     * @return 匹配的 Theme，默认返回 BLUE_CLASSIC
     */
    public static Theme fromName(String name) {
        if (name == null || name.isEmpty()) {
            return BLUE_CLASSIC;
        }
        // 新名称匹配
        for (Theme theme : values()) {
            if (theme.name().equalsIgnoreCase(name)) {
                return theme;
            }
        }
        // 兼容旧配置
        switch (name.toUpperCase()) {
            case "LIGHT":
            case "INTELLIJ":
                return BLUE_CLASSIC;
            case "DARK":
            case "DARCULA":
                return DARK_PURPLE;
            default:
                return BLUE_CLASSIC;
        }
    }

    @Override
    public String toString() {
        return displayName;
    }
}
