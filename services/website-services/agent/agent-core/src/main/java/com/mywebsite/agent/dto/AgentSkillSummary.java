package com.mywebsite.agent.dto;

/** agent-services 技能目录条目（只元数据，不含 SKILL.md 正文）。 */
public record AgentSkillSummary(String id, String description, String version) {}
