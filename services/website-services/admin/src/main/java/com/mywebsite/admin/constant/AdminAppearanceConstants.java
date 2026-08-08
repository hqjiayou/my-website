package com.mywebsite.admin.constant;

import java.util.Set;

/** Appearance schema v1 闭集常量。 */
public final class AdminAppearanceConstants {

    public static final int SCHEMA_VERSION_V1 = 1;

    public static final String PRIMARY_COLOR_PATTERN = "^#[0-9A-Fa-f]{6}$";

    public static final Set<String> CONFIG_KEYS_V1 =
            Set.of("primaryColor", "fontStyle", "buttonStyle", "heroLayout");

    public static final Set<String> FONT_STYLES = Set.of("modern", "classic", "soft", "japanese");

    public static final Set<String> BUTTON_STYLES = Set.of("rounded", "pill", "square");

    public static final Set<String> HERO_LAYOUTS =
            Set.of("left-text-right-visual", "centered", "full-bleed");

    private AdminAppearanceConstants() {}
}
