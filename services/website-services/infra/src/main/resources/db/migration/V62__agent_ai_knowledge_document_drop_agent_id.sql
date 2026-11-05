-- =============================================================================
-- website-services | Flyway V62 | 知识库文档 agent_id 删列（Issue #209 UoW-5）
-- 文档所有者列 agent_id 的两版退役至此完成：V57（UoW-1）起代码停写、所有者
-- 改经所属知识空间（base）的创建者推导，UoW-2~UoW-4 已把索引分区、查询路径
-- 与全部消费方切到空间口径，代码侧残留读取（历史行兜底分支、ownerAgentId
-- 回退、日志占位）已在本版同批移除。文档自此只属 base 一份，不再带 agent 归属。
-- =============================================================================

ALTER TABLE agent_ai_knowledge_document
    DROP COLUMN agent_id;

COMMENT ON TABLE agent_ai_knowledge_document IS '知识库文档（KB 真相源）：只属一个知识空间（base_id），无 agent 归属列（V62 删 agent_id）';
