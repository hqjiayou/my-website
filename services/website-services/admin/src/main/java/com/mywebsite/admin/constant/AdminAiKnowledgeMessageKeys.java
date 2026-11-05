package com.mywebsite.admin.constant;

public final class AdminAiKnowledgeMessageKeys {

    public static final String FILE_REQUIRED = "admin.ai.kb.file.required";
    public static final String FILE_TYPE = "admin.ai.kb.file.type";
    public static final String FILE_SIZE = "admin.ai.kb.file.size";
    public static final String FILE_ENCODING = "admin.ai.kb.file.encoding";
    public static final String FILE_EMPTY = "admin.ai.kb.file.empty";
    /** 上传 mode 必填且只能是 CLI / KEY（Issue #208 UoW-M1）。 */
    public static final String MODE_INVALID = "admin.ai.kb.mode.invalid";
    /** 模型转写调用失败（供应商/调用层），{0} 为失败性质；与「无入口」「返回空」分列。 */
    public static final String FILE_TRANSCRIBE_FAILED = "admin.ai.kb.file.transcribe.failed";
    public static final String FILE_DUPLICATE = "admin.ai.kb.file.duplicate";
    public static final String FILE_STORAGE_FAILED = "admin.ai.kb.file.storage.failed";
    public static final String FILE_ORIGINAL_MISSING = "admin.ai.kb.file.original.missing";
    public static final String TITLE_INVALID = "admin.ai.kb.title.invalid";
    public static final String BASE_NOT_FOUND = "admin.ai.kb.base.not.found";
    public static final String BASE_NOT_EMPTY = "admin.ai.kb.base.not.empty";
    public static final String BASE_IS_DEFAULT = "admin.ai.kb.base.is.default";
    public static final String BASE_ALREADY_LINKED = "admin.ai.kb.base.already.linked";
    public static final String BASE_LINK_SELF = "admin.ai.kb.base.link.self";
    /** 解除创建者自身订阅被拒：创建者行是空间归属锚点，且 link 侧拒自链、无从补回。 */
    public static final String BASE_UNLINK_CREATOR = "admin.ai.kb.base.unlink.creator";

    private AdminAiKnowledgeMessageKeys() {}
}
