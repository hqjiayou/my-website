package com.mywebsite.agent.dto;

import java.util.List;

/**
 * 派生索引中某空间已索引文档的清单项（Issue #209 UoW-2）：供 Java 侧按空间核对
 * 索引现状（存量对账）。现行执行层清单端点只回 sourceId 清单，
 * {@code embeddingClasses} 恒为空集，保留字段供端点扩展时承载类名。
 */
public record AgentIndexedDocument(String sourceId, List<String> embeddingClasses) {

    public AgentIndexedDocument {
        embeddingClasses = embeddingClasses == null ? List.of() : List.copyOf(embeddingClasses);
    }
}
