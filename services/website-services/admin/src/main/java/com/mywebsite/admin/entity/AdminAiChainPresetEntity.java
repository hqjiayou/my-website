package com.mywebsite.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;

/**
 * Admin 侧对平台 {@code agent_ai_chain_preset} 表的双映射实体（与 agent-core
 * {@code AgentAiChainPresetEntity} 同表不同模块）。对话链预设（Issue #210
 * UoW-7）：命名的有序模型入口集合，用途 v1 只有 {@code CHAT}；agent 以
 * {@code chain_preset_id} 引用、多 agent 可共享。
 */
@TableName("agent_ai_chain_preset")
public class AdminAiChainPresetEntity extends AbstractAuditableEntity {

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
