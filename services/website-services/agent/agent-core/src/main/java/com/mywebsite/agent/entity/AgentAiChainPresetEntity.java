package com.mywebsite.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;

/**
 * 对话链预设（表 {@code agent_ai_chain_preset}，Issue #210 UoW-7）：命名的有序
 * 模型入口集合，用途 v1 只有 {@code CHAT}（对话）。agent 以
 * {@code chain_preset_id} 引用、多 agent 可共享同一条预设；组链按
 * {@code entryIds} 的顺序取入口、过滤停用后交 {@code ModelChainResolver}。
 * 与 admin {@code AdminAiChainPresetEntity} 同表双映射。
 */
@TableName("agent_ai_chain_preset")
public class AgentAiChainPresetEntity extends AbstractAuditableEntity {

    private String name;

    /** 用途：v1 只有 CHAT（对话链）。 */
    private String purpose;

    /** 成员入口 id 的 JSON 数组文本（有序）；NULL/空＝无成员。成员资格与启用状态无关。 */
    private String entryIds;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public String getEntryIds() {
        return entryIds;
    }

    public void setEntryIds(String entryIds) {
        this.entryIds = entryIds;
    }
}
