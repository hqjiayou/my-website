-- =============================================================================
-- website-services | Flyway V69 | 对话链内置化（Issue #214 UoW-3）
-- 链是「组合」不是资源：自本版起 agent 的对话链存回 agent 自有字段，按模式
-- 各一份有序 entry_ids（chain_key_entry_ids / chain_cli_entry_ids，JSON 数组
-- 文本，形态与 agent_ai_chain_preset.entry_ids 一致）；agent_ai_chain_preset
-- 降级为「链模板库」，套用即拷贝、与 agent 链不再联动。
--
-- 回填口径（等价性）：每个未删 agent 取其现行引用预设（chain_preset_id →
-- 未删预设）的 entry_ids，逐字按成员入口的 mode 拆分（mode 空值按 KEY，与
-- ModelChainResolver.entryMode 及 V35 列默认同口径），段内保预设原序。
-- 迁移前后同一 agent 在同一模式下的成员集与顺序不变 ⇒ 解析结果等价
-- （组链时本来就先按 mode 过滤再按序组链）。已删成员在旧解析下本就被批量
-- 查询过滤（@TableLogic），回填时同样剔除（JOIN 带 deleted = FALSE）。
-- 某模式无成员时该列留 NULL（＝该模式未配链，解析按空链处理）。
-- 未引用预设／预设已删／预设 entry_ids 非法 JSON 的 agent 两列留 NULL，
-- 与旧口径「无链」一致。
--
-- chain_preset_id 列保留停用：本迁移不写它、解析与管理面自本版起不再读写，
-- 列的删除留 UoW-7 评估。FK fk_agent_chain_preset 保留不动。
-- =============================================================================

ALTER TABLE agent
    ADD COLUMN chain_key_entry_ids TEXT,
    ADD COLUMN chain_cli_entry_ids TEXT;

COMMENT ON COLUMN agent.chain_key_entry_ids IS '自有对话链·KEY 模式有序 entry_ids（JSON 数组文本，#214 UoW-3）；NULL＝未配链';
COMMENT ON COLUMN agent.chain_cli_entry_ids IS '自有对话链·CLI 模式有序 entry_ids（JSON 数组文本，#214 UoW-3）；NULL＝未配链';

UPDATE agent a
SET chain_key_entry_ids = split.key_ids,
    chain_cli_entry_ids = split.cli_ids
FROM (
    SELECT a2.id AS agent_id,
           (SELECT jsonb_agg(m.entry_id ORDER BY m.ord)::text
              FROM (
                  SELECT e.id AS entry_id, t.ord AS ord
                    FROM jsonb_array_elements_text(
                             CASE
                                 WHEN p.entry_ids ~ '^\s*\[.*\]\s*$' THEN p.entry_ids::jsonb
                                 ELSE '[]'::jsonb
                             END) WITH ORDINALITY AS t(eid, ord)
                    JOIN agent_model_entry e
                      ON t.eid ~ '^\d+$'
                     AND e.id = t.eid::bigint
                     AND e.deleted = FALSE
                   WHERE COALESCE(NULLIF(upper(btrim(e.mode)), ''), 'KEY') = 'KEY'
              ) m) AS key_ids,
           (SELECT jsonb_agg(m.entry_id ORDER BY m.ord)::text
              FROM (
                  SELECT e.id AS entry_id, t.ord AS ord
                    FROM jsonb_array_elements_text(
                             CASE
                                 WHEN p.entry_ids ~ '^\s*\[.*\]\s*$' THEN p.entry_ids::jsonb
                                 ELSE '[]'::jsonb
                             END) WITH ORDINALITY AS t(eid, ord)
                    JOIN agent_model_entry e
                      ON t.eid ~ '^\d+$'
                     AND e.id = t.eid::bigint
                     AND e.deleted = FALSE
                   WHERE COALESCE(NULLIF(upper(btrim(e.mode)), ''), 'KEY') = 'CLI'
              ) m) AS cli_ids
      FROM agent a2
      JOIN agent_ai_chain_preset p
        ON p.id = a2.chain_preset_id
       AND p.deleted = FALSE
     WHERE a2.deleted = FALSE
       AND a2.chain_preset_id IS NOT NULL
) split
WHERE a.id = split.agent_id;
