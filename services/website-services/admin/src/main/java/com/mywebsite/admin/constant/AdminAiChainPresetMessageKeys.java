package com.mywebsite.admin.constant;

public final class AdminAiChainPresetMessageKeys {

    /** 预设仍被 agent 引用时删除拦截（Issue #210 UoW-7），{0} 为引用它的 agent 数。 */
    public static final String PRESET_REFERENCED = "admin.ai.chain.preset.referenced";

    /** 预设成员包含不存在（或已删除）的模型入口，{0} 为入口 id。 */
    public static final String ENTRY_NOT_FOUND = "admin.ai.chain.preset.entry.not.found";

    private AdminAiChainPresetMessageKeys() {}
}
