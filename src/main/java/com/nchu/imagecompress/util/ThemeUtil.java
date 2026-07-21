package com.nchu.imagecompress.util;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.nchu.imagecompress.model.Theme;
import com.nchu.imagecompress.model.ThemePalette;

import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/**
 * FlatLaf 主题管理工具类（v3 — 动态多主题系统）。
 *
 * <p>核心变化：将静态 final 颜色令牌改为可变静态字段，
 * 通过 {@link ThemePalette} 加载不同主题的完整设计令牌。</p>
 *
 * <h3>6 套预设主题</h3>
 * <ol>
 *   <li>默认蓝调 — 经典专业款</li>
 *   <li>薄荷曼波 — 清新绿调</li>
 *   <li>暖阳奶咖 — 暖棕复古风</li>
 *   <li>极简墨灰 — 无彩商务风</li>
 *   <li>暗夜紫晶 — 深紫科技风</li>
 *   <li>深海蓝调 — 藏青沉稳风</li>
 * </ol>
 *
 * @author NCHU-Student
 * @version 3.0.0
 * @since 2026-07-08
 */
public final class ThemeUtil {

    // ==================== 当前配色（可变，随主题切换更新） ====================

    /** 主色阶 */
    public static Color PRIMARY_DEEPEST;
    public static Color PRIMARY_DEEP;
    public static Color PRIMARY;
    public static Color PRIMARY_LIGHT;
    public static Color PRIMARY_LIGHTEST;

    /** 背景层 */
    public static Color BG_WINDOW;
    public static Color BG_CARD;
    public static Color BG_INPUT;
    public static Color BG_SELECTED;
    public static Color BG_HOVER;

    /** 文字层 */
    public static Color TEXT_PRIMARY;
    public static Color TEXT_SECONDARY;
    public static Color TEXT_TERTIARY;

    /** 边框分割线 */
    public static Color BORDER;
    public static Color BORDER_HOVER;

    /** 功能状态色 */
    public static Color SUCCESS;
    public static Color SUCCESS_LIGHT;
    public static Color ERROR;
    public static Color ERROR_LIGHT;
    public static Color WARNING;
    public static Color WARNING_LIGHT;

    // ==================== 当前配色板（只读） ====================

    private static ThemePalette currentPalette;

    /** 当前激活的主题缓存（v3 — 直接赋值，O(1) 获取，避免 Color.equals 推断） */
    private static Theme currentThemeCache = Theme.BLUE_CLASSIC;

    // ==================== 设计令牌：圆角（固定值） ====================

    public static final int ARC_CARD = ThemePalette.ARC_CARD;
    public static final int ARC_BUTTON = ThemePalette.ARC_BUTTON;
    public static final int ARC_TAG = ThemePalette.ARC_TAG;
    public static final int ARC_THUMB = ThemePalette.ARC_THUMB;
    public static final int ARC_PILL = ThemePalette.ARC_PILL;

    // ==================== 设计令牌：间距（固定值） ====================

    public static final int SPACE_XS = ThemePalette.SPACE_XS;
    public static final int SPACE_SM = ThemePalette.SPACE_SM;
    public static final int SPACE_MD = ThemePalette.SPACE_MD;
    public static final int SPACE_LG = ThemePalette.SPACE_LG;
    public static final int SPACE_XL = ThemePalette.SPACE_XL;
    public static final int SPACE_XXL = ThemePalette.SPACE_XXL;

    /** 区块外边距 (16px) — 卡片/区域之间 */
    public static final int SPACE_BLOCK = ThemePalette.SPACE_BLOCK;
    /** 行内间距 (8px) — 表单行垂直间距 */
    public static final int SPACE_ROW = ThemePalette.SPACE_ROW;
    /** 标签-控件间距 (12px) — 标签与输入控件的横向距离 */
    public static final int SPACE_LABEL_GAP = ThemePalette.SPACE_LABEL_GAP;

    // ==================== 设计令牌：字体（随主题切换更新） ====================

    public static Font FONT_TITLE;
    public static Font FONT_BODY;
    public static Font FONT_SMALL;
    public static Font FONT_TINY;
    public static Font FONT_MONO;

    // ==================== 主题变更监听 ====================

    private static final List<Runnable> themeChangeListeners = new ArrayList<>();

    // ==================== 动态前景/背景色注册表（Phase 9） ====================

    /** 已注册动态前景色的组件 → 颜色供应商 */
    private static final Map<JComponent, Supplier<Color>> fgRegistry = new WeakHashMap<>();

    /** 已注册动态背景色的组件 → 颜色供应商 */
    private static final Map<JComponent, Supplier<Color>> bgRegistry = new WeakHashMap<>();

    private ThemeUtil() {}

    // ==================== 主题加载 ====================

    /**
     * 应用指定主题。
     * 默认使用 FlatLightLaf 作为基底，然后注入自定义配色板。
     *
     * @param theme 目标主题
     * @return true 表示成功
     */
    public static boolean applyTheme(Theme theme) {
        try {
            // ① 确保 FlatLaf 已初始化（首次调用时 setup）
            if (!(UIManager.getLookAndFeel() instanceof FlatLaf)) {
                FlatLightLaf.setup();
            }

            // ② 加载配色板
            ThemePalette palette = theme.getPalette();
            applyPalette(palette);
            currentThemeCache = theme;

            // ③ 写入 UIManager 全局属性
            applyGlobalDefaults();

            LogUtil.info("[ThemeUtil] 主题已加载: " + theme.getDisplayName());
            return true;
        } catch (Exception e) {
            LogUtil.error("[ThemeUtil] 主题加载失败: " + e.getMessage());
            // 回退：加载默认蓝调
            applyPalette(ThemePalette.blueClassic());
            applyGlobalDefaults();
            return false;
        }
    }

    /**
     * 切换主题并刷新全窗口 UI。
     *
     * @param theme 目标主题
     */
    public static void switchTheme(Theme theme) {
        applyTheme(theme);
        // 通知所有监听器（在各视图 updateUI 之前让它们刷新缓存的颜色引用）
        fireThemeChange();
        // FlatLaf.updateUI() 会递归更新所有窗口的 ComponentUI
        FlatLaf.updateUI();
    }

    // ==================== 配色板加载 ====================

    /** 当前配色板引用（主题已通过 currentThemeCache 缓存） */
    private static void applyPalette(ThemePalette p) {
        currentPalette = p;

        // 主色阶
        PRIMARY_DEEPEST  = p.primaryDeepest;
        PRIMARY_DEEP     = p.primaryDeep;
        PRIMARY          = p.primary;
        PRIMARY_LIGHT    = p.primaryLight;
        PRIMARY_LIGHTEST = p.primaryLightest;

        // 背景层
        BG_WINDOW   = p.bgWindow;
        BG_CARD     = p.bgCard;
        BG_INPUT    = p.bgInput;
        BG_SELECTED = p.bgSelected;
        BG_HOVER    = p.bgHover;

        // 文字层
        TEXT_PRIMARY   = p.textPrimary;
        TEXT_SECONDARY = p.textSecondary;
        TEXT_TERTIARY  = p.textTertiary;

        // 边框
        BORDER       = p.border;
        BORDER_HOVER = p.borderHover;

        // 状态色
        SUCCESS       = p.success;
        SUCCESS_LIGHT = p.successLight;
        ERROR         = p.error;
        ERROR_LIGHT   = p.errorLight;
        WARNING       = p.warning;
        WARNING_LIGHT = p.warningLight;

        // 字体
        FONT_TITLE = p.fontTitle;
        FONT_BODY  = p.fontBody;
        FONT_SMALL = p.fontSmall;
        FONT_TINY  = p.fontTiny;
        FONT_MONO  = p.fontMono;
    }

    // ==================== UIManager 全局属性注入 ====================

    private static void applyGlobalDefaults() {
        // ==================== 圆角体系 ====================
        UIManager.put("Button.arc", ARC_BUTTON);
        UIManager.put("Component.arc", ARC_BUTTON);
        UIManager.put("ProgressBar.arc", ARC_BUTTON);
        UIManager.put("TextComponent.arc", ARC_BUTTON);
        UIManager.put("TabbedPane.arc", ARC_BUTTON);
        UIManager.put("Panel.arc", ARC_CARD);
        UIManager.put("ScrollBar.thumbArc", ARC_PILL);
        UIManager.put("ComboBox.arc", ARC_BUTTON);
        UIManager.put("Spinner.arc", ARC_BUTTON);
        UIManager.put("CheckBox.arc", ARC_TAG);
        UIManager.put("Slider.thumbArc", ARC_PILL);

        // ==================== 色彩系统 ====================
        UIManager.put("Component.accentColor", hexToInt(PRIMARY));
        UIManager.put("Component.focusedBorderColor", hexToInt(PRIMARY));

        // Panel 背景
        UIManager.put("Panel.background", new ColorUIResource(BG_WINDOW));

        // 按钮
        UIManager.put("Button.padding", new Insets(6, 14, 6, 14));  // 32px 标准高度
        UIManager.put("Button.background", new ColorUIResource(BG_CARD));
        UIManager.put("Button.foreground", new ColorUIResource(TEXT_PRIMARY));
        UIManager.put("Button.hoverBackground", new ColorUIResource(BG_HOVER));
        UIManager.put("Button.hoverForeground", new ColorUIResource(TEXT_PRIMARY));
        UIManager.put("Button.startBackground", new ColorUIResource(BG_HOVER));
        UIManager.put("Button.endBackground", new ColorUIResource(BG_HOVER));
        UIManager.put("Button.disabledText", new ColorUIResource(TEXT_TERTIARY));
        UIManager.put("Button.disabledBackground", new ColorUIResource(BG_HOVER));
        UIManager.put("Button.borderColor", new ColorUIResource(BORDER));

        // 默认按钮（主操作）
        UIManager.put("Button.default.background", new ColorUIResource(PRIMARY));
        UIManager.put("Button.default.foreground", new ColorUIResource(Color.WHITE));
        UIManager.put("Button.default.hoverBackground", new ColorUIResource(PRIMARY_DEEP));

        // 滑块
        UIManager.put("Slider.trackWidth", 4);
        UIManager.put("Slider.thumbWidth", 16);
        UIManager.put("Slider.thumbHeight", 16);
        UIManager.put("Slider.trackColor", new ColorUIResource(BORDER));
        UIManager.put("Slider.thumbColor", new ColorUIResource(PRIMARY));
        UIManager.put("Slider.focusedColor", new ColorUIResource(PRIMARY));

        // 滚动条
        UIManager.put("ScrollBar.track", new ColorUIResource(new Color(0, 0, 0, 0)));
        UIManager.put("ScrollBar.thumb", new ColorUIResource(BORDER_HOVER));
        UIManager.put("ScrollBar.hoverThumbColor", new ColorUIResource(PRIMARY));
        UIManager.put("ScrollBar.width", 8);
        UIManager.put("ScrollBar.thumbInsets", new Insets(1, 1, 1, 1));

        // 进度条
        UIManager.put("ProgressBar.cycleTime", 2000);
        UIManager.put("ProgressBar.foreground", new ColorUIResource(PRIMARY));
        UIManager.put("ProgressBar.background", new ColorUIResource(BORDER));

        // 标签页
        UIManager.put("TabbedPane.tabAreaBackground", new ColorUIResource(BG_CARD));
        UIManager.put("TabbedPane.selectedBackground", new ColorUIResource(BG_CARD));
        UIManager.put("TabbedPane.hoverBackground", new ColorUIResource(BG_HOVER));
        UIManager.put("TabbedPane.hoverForeground", new ColorUIResource(TEXT_PRIMARY));
        UIManager.put("TabbedPane.selectedForeground", new ColorUIResource(PRIMARY));
        UIManager.put("TabbedPane.underlineColor", new ColorUIResource(PRIMARY));
        UIManager.put("TabbedPane.tabInsets", new Insets(6, 14, 6, 14));

        // 输入框
        UIManager.put("TextComponent.borderColor", new ColorUIResource(BORDER));
        UIManager.put("TextField.background", new ColorUIResource(BG_INPUT));

        // 下拉框
        UIManager.put("ComboBox.borderColor", new ColorUIResource(BORDER));
        UIManager.put("ComboBox.buttonBackground", new ColorUIResource(BG_CARD));
        UIManager.put("ComboBox.selectionBackground", new ColorUIResource(BG_SELECTED));
        UIManager.put("ComboBox.selectionForeground", new ColorUIResource(TEXT_PRIMARY));

        // 列表 & 表格
        UIManager.put("List.selectionForeground", new ColorUIResource(TEXT_PRIMARY));
        UIManager.put("Table.selectionForeground", new ColorUIResource(TEXT_PRIMARY));
        UIManager.put("List.selectionBackground", new ColorUIResource(BG_SELECTED));
        UIManager.put("Table.selectionBackground", new ColorUIResource(BG_SELECTED));

        // 菜单
        UIManager.put("Menu.selectionBackground", new ColorUIResource(BG_SELECTED));
        UIManager.put("Menu.selectionForeground", new ColorUIResource(TEXT_PRIMARY));
        UIManager.put("MenuItem.selectionBackground", new ColorUIResource(BG_SELECTED));
        UIManager.put("MenuItem.selectionForeground", new ColorUIResource(TEXT_PRIMARY));
        UIManager.put("MenuBar.background", new ColorUIResource(BG_CARD));
        UIManager.put("MenuBar.borderColor", new ColorUIResource(BORDER));

        // 工具栏
        UIManager.put("ToolBar.background", new ColorUIResource(BG_CARD));
        UIManager.put("ToolBar.borderColor", new ColorUIResource(new Color(0, 0, 0, 0)));

        // 标签
        UIManager.put("Label.foreground", new ColorUIResource(TEXT_PRIMARY));

        // Tooltip
        UIManager.put("ToolTip.background", new ColorUIResource(BG_CARD));
        UIManager.put("ToolTip.foreground", new ColorUIResource(TEXT_PRIMARY));
        UIManager.put("ToolTip.borderColor", new ColorUIResource(BORDER));

        // ==================== 字体层级 ====================
        UIManager.put("defaultFont", FONT_BODY);
        UIManager.put("MenuBar.font", FONT_BODY);
        UIManager.put("Menu.font", FONT_BODY);
        UIManager.put("MenuItem.font", FONT_BODY);
        UIManager.put("ToolBar.font", FONT_BODY);
        UIManager.put("Label.font", FONT_BODY);
        UIManager.put("Button.font", FONT_BODY);
        UIManager.put("TabbedPane.font", FONT_BODY);
        UIManager.put("Slider.font", FONT_SMALL);
        UIManager.put("TextField.font", FONT_BODY);
        UIManager.put("ComboBox.font", FONT_BODY);
        UIManager.put("ProgressBar.font", FONT_TINY);
        UIManager.put("ToolTip.font", FONT_SMALL);

        LogUtil.info("[ThemeUtil] 全局 UI 设计令牌已注入 UIManager");
    }

    // ==================== 主题变更监听 ====================

    /**
     * 注册主题变更监听器。切换主题后依次调用。
     * 用于视图层刷新自定义绘制的颜色缓存。
     */
    public static void addThemeChangeListener(Runnable listener) {
        if (listener != null && !themeChangeListeners.contains(listener)) {
            themeChangeListeners.add(listener);
        }
    }

    private static void fireThemeChange() {
        // 1. 刷新所有已注册的动态前/背景色
        refreshDynamicColors();
        // 2. 通知手动监听器
        for (Runnable r : themeChangeListeners) {
            try {
                r.run();
            } catch (Exception e) {
                LogUtil.error("[ThemeUtil] 主题变更监听器异常: " + e.getMessage());
            }
        }
    }

    // ==================== 动态前景/背景色注册（Phase 9） ====================

    /**
     * 设置组件的动态前景色，主题切换时自动刷新。
     * 替代手动 {@code setForeground(ThemeUtil.TEXT_PRIMARY)}，解决切主题后文字颜色不更新的 bug。
     *
     * @param c            目标组件
     * @param colorSupplier 颜色供应商（通常为 {@code () -> ThemeUtil.TEXT_PRIMARY}）
     */
    public static void setDynamicForeground(JComponent c, java.util.function.Supplier<Color> colorSupplier) {
        if (c == null || colorSupplier == null) return;
        c.setForeground(colorSupplier.get());
        fgRegistry.put(c, colorSupplier);
    }

    /**
     * 设置组件的动态背景色，主题切换时自动刷新。
     * 替代手动 {@code setBackground(ThemeUtil.BG_CARD)} + ThemeChangeListener。
     *
     * @param c            目标组件
     * @param colorSupplier 颜色供应商（通常为 {@code () -> ThemeUtil.BG_CARD}）
     */
    public static void setDynamicBackground(JComponent c, java.util.function.Supplier<Color> colorSupplier) {
        if (c == null || colorSupplier == null) return;
        c.setBackground(colorSupplier.get());
        bgRegistry.put(c, colorSupplier);
    }

    /** 遍历注册表，刷新所有已注册组件的前/背景色 */
    private static void refreshDynamicColors() {
        for (Map.Entry<JComponent, Supplier<Color>> e : fgRegistry.entrySet()) {
            JComponent c = e.getKey();
            Supplier<Color> supplier = e.getValue();
            if (c != null && supplier != null) {
                c.setForeground(supplier.get());
            }
        }
        for (Map.Entry<JComponent, Supplier<Color>> e : bgRegistry.entrySet()) {
            JComponent c = e.getKey();
            Supplier<Color> supplier = e.getValue();
            if (c != null && supplier != null) {
                c.setBackground(supplier.get());
            }
        }
    }

    // ==================== 便捷方法 ====================

    /** 获取当前配色板（用于访问阴影、描边等非全局属性） */
    public static ThemePalette getCurrentPalette() {
        return currentPalette;
    }

    /**
     * 获取当前激活的主题枚举（v3 — 缓存直接返回，O(1) 无比较）。
     */
    public static Theme getCurrentTheme() {
        return currentThemeCache != null ? currentThemeCache : Theme.BLUE_CLASSIC;
    }

    /**
     * 显示带显式按钮样式的消息对话框。
     *
     * <p>全局 Button.background 设为 BG_CARD（接近 Panel.background = BG_WINDOW），
     * 导致 JOptionPane 中的按钮融入背景看不见。此方法弹窗前临时覆盖为
     * 主色背景 + 白色文字，弹窗后恢复。</p>
     *
     * @param parent  父组件
     * @param message 消息文本
     * @param title   对话框标题
     */
    public static void showStyledMessageDialog(Component parent, String message, String title) {
        Object oldBg = UIManager.get("Button.background");
        Object oldFg = UIManager.get("Button.foreground");
        Object oldHoverBg = UIManager.get("Button.hoverBackground");
        try {
            UIManager.put("Button.background", new ColorUIResource(PRIMARY));
            UIManager.put("Button.foreground", new ColorUIResource(Color.WHITE));
            UIManager.put("Button.hoverBackground", new ColorUIResource(PRIMARY_DEEP));
            JOptionPane.showMessageDialog(parent, message, title, JOptionPane.INFORMATION_MESSAGE);
        } finally {
            UIManager.put("Button.background", oldBg);
            UIManager.put("Button.foreground", oldFg);
            if (oldHoverBg != null) {
                UIManager.put("Button.hoverBackground", oldHoverBg);
            }
        }
    }

    /** Color → hex int（用于 UIManager） */
    private static int hexToInt(Color c) {
        return (c.getRed() << 16) | (c.getGreen() << 8) | c.getBlue();
    }
}
