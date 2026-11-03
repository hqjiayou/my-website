package com.mywebsite.admin.dto;

public final class AdminAiExperienceExtractionDtos {

    private AdminAiExperienceExtractionDtos() {}

    /**
     * 手动批量提炼入参（Issue #184 UoW-2，P1 定案；触发端点在 UoW-3 接）。
     * 两项都可为 null：窗口默认 14 天、候选轮上限默认 40，超界在服务层钳制。
     */
    public record AdminAiExperienceExtractionRequestDto(Integer windowDays, Integer maxTurns) {}

    /**
     * 一批提炼的汇总。计数口径：scannedTurns 是信号预筛命中的候选轮数；
     * extractionCalls 是按来源 agent 分组（每组再按执行层单请求轮数上限分片）
     * 实际发出的提炼调用数；producedCandidates 是落 PENDING 的候选数；
     * failedTurns 是因组链为空或提炼调用失败而整片未产出的轮数——不静默丢弃。
     */
    public record AdminAiExperienceExtractionResultDto(
            int windowDays, int scannedTurns, int extractionCalls, int producedCandidates, int failedTurns) {}
}
