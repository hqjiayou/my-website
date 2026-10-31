package com.mywebsite.agent.dto;

import java.util.List;

/** 结构化表的一行。单元格是抽取后的原始文本。 */
public record AgentRetrievalTableRow(int rowNumber, List<String> cells) {}
