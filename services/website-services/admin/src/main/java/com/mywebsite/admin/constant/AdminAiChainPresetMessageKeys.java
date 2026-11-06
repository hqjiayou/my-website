package com.mywebsite.admin.constant;

public final class AdminAiChainPresetMessageKeys {

    /** 预设成员包含不存在（或已删除）的模型入口，{0} 为入口 id。 */
    public static final String ENTRY_NOT_FOUND = "admin.ai.chain.preset.entry.not.found";

    /** 自有链成员的模式与目标链模式不一致，{0} 为入口 id（Issue #214 UoW-3）。 */
    public static final String ENTRY_MODE_MISMATCH = "admin.ai.chain.entry.mode.mismatch";

    private AdminAiChainPresetMessageKeys() {}
}
