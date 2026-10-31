package com.mywebsite.agent.dto;

/**
 * 索引入库的一段正文及其位置。缺省时 Python 仍按 content 整篇切块。
 * 页码与表格行区间不同时填写。行区间不写入知识库正文。
 */
public record AgentRetrievalSegment(
        String text, Integer page, String sheet, Integer rowStart, Integer rowEnd) {}
