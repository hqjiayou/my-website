package com.mywebsite.admin.constant;

import java.util.Set;

/** Agent Core 生产常量（状态 / 站点用途标签；与 API 契约对齐）。旧行业闭集已随行业模板下架移除。 */
public final class AdminAgentConstants {

    public static final String STATUS_ACTIVE = "ACTIVE";

    public static final String STATUS_DISABLED = "DISABLED";

    public static final Set<String> ALLOWED_STATUSES = Set.of(STATUS_ACTIVE, STATUS_DISABLED);

    /** agent 类型（#213）：站点型（建站向导／站点工作台形态）。 */
    public static final String AGENT_KIND_SITE = "SITE";

    /** agent 类型（#213）：纯 AI 型（无站点外壳、只跑 AI 对话）。 */
    public static final String AGENT_KIND_AI_ONLY = "AI_ONLY";

    public static final Set<String> ALLOWED_AGENT_KINDS = Set.of(AGENT_KIND_SITE, AGENT_KIND_AI_ONLY);

    /** 站点用途标签预设（前端下拉与此对齐；预设之外允许自订标签，见 AGENT_TAG_PATTERN）。 */
    public static final Set<String> AGENT_TAG_PRESETS = Set.of(
            "PERSONAL", "PORTFOLIO", "BLOG", "KNOWLEDGE", "TOOL", "OTHER");

    /** 用途标签：1–32 字符，字母/数字开头，可含字母、数字、底线、连字符与空格。 */
    public static final String AGENT_TAG_PATTERN = "^[A-Za-z0-9][A-Za-z0-9_\\- ]{0,31}$";

    /** agentCode：小写字母开头，后续可为小写字母/数字/_/-，总长 2–64。 */
    public static final String AGENT_CODE_PATTERN = "^[a-z][a-z0-9_-]{1,63}$";

    public static final int DEFAULT_PAGE_NUM = 1;

    public static final int DEFAULT_PAGE_SIZE = 20;

    public static final int MAX_PAGE_SIZE = 100;

    private AdminAgentConstants() {}
}
