package com.mywebsite.admin.service;

import com.mywebsite.admin.dto.AdminAiUsageDtos.AdminAiUsageReportDto;

public interface AdminAiUsageService {

    /**
     * 最近 {@code days} 天（UTC）的用量聚合＋近期明细（Issue #140 UoW-2，只读）。
     * {@code source} 为空表示全部来源；非法值由实现抛 BAD_REQUEST。
     */
    AdminAiUsageReportDto usageReport(String source, int days, int limit);
}
