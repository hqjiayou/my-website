package com.mywebsite.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;

/**
 * 嵌入类登记的首次读物化标记（表 {@code agent_embedding_class_materialization}，
 * Flyway V49，Issue #126 UoW-2）。
 *
 * <p>一行＝一个 scope 已完成「推算结果 → 登记行」的一次性物化：{@code scopeKey}
 * 为 {@code GLOBAL}（全站）或 {@code SPACE:<baseId>}（知识空间；#209 UoW-3 前
 * 为 {@code AGENT:<agentId>}，存量键已由 Flyway V61 一次性转换）。标记只增不删：
 * 物化后该 scope 的登记行就是唯一真相源，人工删光登记行也不会被推算结果
 * 「复活」——标记在，读取就只认登记表。本表无审计列，物化时间即
 * {@code materializedAt}。
 */
@TableName("agent_embedding_class_materialization")
public class AgentEmbeddingClassMaterializationEntity {

    /** 物化作用域键：{@code GLOBAL} 或 {@code SPACE:<baseId>}。 */
    @TableId(value = "scope_key", type = IdType.INPUT)
    private String scopeKey;

    private OffsetDateTime materializedAt;

    public String getScopeKey() {
        return scopeKey;
    }

    public void setScopeKey(String scopeKey) {
        this.scopeKey = scopeKey;
    }

    public OffsetDateTime getMaterializedAt() {
        return materializedAt;
    }

    public void setMaterializedAt(OffsetDateTime materializedAt) {
        this.materializedAt = materializedAt;
    }
}
