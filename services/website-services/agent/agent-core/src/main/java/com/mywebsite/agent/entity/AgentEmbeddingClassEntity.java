package com.mywebsite.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.mywebsite.infra.entity.AbstractAuditableEntity;

/**
 * 嵌入类登记（表 {@code agent_embedding_class}，Flyway V48 建表、V61 改 scope，
 * Issue #126 UoW-1 / #209 UoW-3）。
 *
 * <p>一行＝一个已登记的嵌入类。登记行只引用模型入口（{@code entryId})：类的
 * {@code embeddingSpec}、key、{@code baseUrl} 运行时从被引用入口解析，本表不存
 * spec、不存 key——key 轮换只改入口一处。{@code baseId} 为 NULL＝全站 scope
 * （平台默认），非空＝该知识空间 scope；双层合并（空间优先）在消费侧完成，
 * 本表只存各 scope 自己的登记行。scope 于 #209 UoW-3 由站点改知识空间：向量
 * 分区键已改 base，文档的类集只由其所属空间的登记决定。
 *
 * <p>资格（入口须 mode=KEY 且 embeddingSpec 非空）、同 scope 内规范化类键唯一、
 * 每 scope 上限 4 类，均由 admin 登记服务在写入时校验，本表不落对应约束。
 */
@TableName("agent_embedding_class")
public class AgentEmbeddingClassEntity extends AbstractAuditableEntity {

    /** 所属知识空间 id；NULL＝全站 scope（平台默认）。 */
    private Long baseId;

    /** 被引用的模型入口 id（{@code agent_model_entry}）。 */
    private Long entryId;

    private Boolean enabled;

    /** scope 内顺序（整表保存时按提交数组序写入），决定类优先级。 */
    private Integer sortOrder;

    public Long getBaseId() {
        return baseId;
    }

    public void setBaseId(Long baseId) {
        this.baseId = baseId;
    }

    public Long getEntryId() {
        return entryId;
    }

    public void setEntryId(Long entryId) {
        this.entryId = entryId;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
