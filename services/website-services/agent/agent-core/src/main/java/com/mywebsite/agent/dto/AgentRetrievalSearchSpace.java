package com.mywebsite.agent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 检索请求中的一个知识空间及其登记嵌入类（Issue #209 UoW-3）：登记已迁空间
 * scope 后，多空间检索按空间分别下发类集——每个空间只用自己登记的类查自己
 * 的分区（UoW-2 的请求级一份类集共用多空间就此退役）。{@code embeddingClasses}
 * 为空/null 时该空间走执行层进程嵌入，与未登记空间的现行口径一致。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentRetrievalSearchSpace(String baseId, List<AgentEmbeddingClass> embeddingClasses) {}
