package com.mywebsite.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;

/**
 * 对话链模板（表 {@code agent_ai_chain_preset}，Issue #210 UoW-7 引入）：命名的
 * 有序模型入口集合，用途 v1 只有 {@code CHAT}（对话）。#210 时代 agent 以
 * {@code chain_preset_id} 引用它组链；#214 UoW-3 链内置化后降级为<b>模板库</b>
 * ——组链只读 agent 自有链字段，本表只被 admin 模板 CRUD 与「载入/另存」读写，
 * 套用是拷贝、与 agent 脱钩。与 admin {@code AdminAiChainPresetEntity} 同表双映射。
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
