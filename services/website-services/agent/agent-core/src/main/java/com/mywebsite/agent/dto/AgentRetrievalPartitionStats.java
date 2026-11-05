package com.mywebsite.agent.dto;

import java.util.List;

/**
 * 派生索引中某分区（键空间）的对账统计（Issue #209 UoW-5）：清单端点
 * {@code GET /internal/v1/retrieval/documents?baseId=} 的完整响应——已索引
 * sourceId 清单与该分区全部嵌入类的块行总数。分区键在存量迁移期也用于按旧
 * agent id 查遗留分区（键只是字符串，执行层不校验其是否为现存空间）。
 */
public record AgentRetrievalPartitionStats(String baseId, List<String> sourceIds, long chunkCount) {

    public AgentRetrievalPartitionStats {
        sourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds);
    }
}
