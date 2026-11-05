package com.mywebsite.agent.service;

import com.mywebsite.agent.dto.AgentModelSelection;
import java.util.List;

/**
 * 嵌入类的唯一读取入口（Issue #126 登记制，UoW-2；scope 于 Issue #209 UoW-3
 * 由站点改知识空间）：admin 与 agent-app 共用这一份实现，替代原先两处经
 * {@code ModelChainResolver} 扫模型入口 {@code embeddingSpec} 的推算。
 *
 * <p>来源＝登记表 {@code agent_embedding_class}：空间登记行在前（按
 * {@code sort_order}）、全站登记行在后，逐行把被引用入口解析成凭证；入口
 * 不存在/已软删/被禁用、模式非 KEY、{@code embeddingSpec} 空白或密钥解密
 * 失败的行跳过并记日志，不整批失败。合并后仍经
 * {@code AgentEmbeddingClassUtils.dedupe} 兜底（规范化类键、上限 4），
 * 返回元素的字段口径与推算时代逐字一致。
 *
 * <p>首次读物化：某 scope（空间/全站）从未物化过时，先按旧推算口径把该
 * scope 的类物化成登记行并落 marker（表
 * {@code agent_embedding_class_materialization}），再按登记读。marker 只增
 * 不删，人工删光登记行不会被推算复活。空间 scope 的旧推算口径＝该空间
 * 创建者的站点入口扫描（模型入口仍按站点归属，空间没有自己的入口集；
 * UoW-3 前该空间文档正是经创建者 scope 的类集索引，物化与 V61 的存量行
 * 扇出同源等价）。
 */
public interface AgentEmbeddingClassService {

    /** 该知识空间当前生效的嵌入类（空间登记优先、全站补充）；没有时返回空列表。 */
    List<AgentModelSelection> embeddingClassesForSpace(long baseId);
}
