package com.mywebsite.agent.dto;

import java.util.List;

/**
 * 索引入库的一张结构化表。缺省时不写入检索库的结构化行。
 * 表头行不在 rows 里。rowNumber 是工作表原始行号。
 */
public record AgentRetrievalTable(
        String sheet,
        int headerRow,
        List<String> headers,
        List<AgentRetrievalTableRow> rows,
        boolean truncated) {}
