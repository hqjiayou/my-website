package com.mywebsite.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public final class AdminEmbeddingClassDtos {

    private AdminEmbeddingClassDtos() {}

    /**
     * 一行登记的视图（Issue #126 UoW-1）：登记行本身只存 entry 引用，
     * {@code embeddingSpec} 与 {@code className}（规范化类键）是读时从被引用入口
     * 解析出来的展示字段，不落登记表。被引用入口已被删除时
     * {@code entryDisplayName}/{@code embeddingSpec}/{@code className} 为 null，
     * 登记行仍如实返回，供页面提示清理。
     */
    public record AdminEmbeddingClassDto(
            Long id,
            Long agentId,
            Long entryId,
            String entryDisplayName,
            String embeddingSpec,
            String className,
            boolean enabled,
            int sortOrder) {}

    /**
     * 整表保存：一份有序的登记清单整体替换某 scope 的现有登记。
     * 数组序即类优先级（服务端按数组序写 sort_order）；{@code enabled} 缺省视为 true。
     * 空列表＝清空该 scope 的登记。
     */
    public record AdminEmbeddingClassSaveRequest(@Valid List<Item> items) {

        public record Item(@NotNull Long entryId, Boolean enabled) {}
    }
}
