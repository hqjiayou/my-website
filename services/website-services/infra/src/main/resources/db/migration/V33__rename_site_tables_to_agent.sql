-- V33: site 域整体更名为 agent（site 与 agent client 合并为单一 agent 模块，深度改名的一部分）。
-- 只改 agent 域的表：site / site_user* / site_ai_* / ai_model_entry。
-- CMS 与历史表不在此列（site_content / site_appearance / site_template / site_reservation / media_file 保持原名，随 CMS 处置另案）。
-- Postgres 的 ALTER TABLE RENAME 会自动传播到外键引用方；索引/约束名不会自动改，下方 DO 块统一处理改名后表上的含 site 名称对象。

ALTER TABLE site RENAME TO agent;
ALTER TABLE site_user RENAME TO agent_user;
ALTER TABLE site_user_profile RENAME TO agent_user_profile;
ALTER TABLE site_user_memory RENAME TO agent_user_memory;
ALTER TABLE site_ai_conversation RENAME TO agent_ai_conversation;
ALTER TABLE site_ai_conversation_share RENAME TO agent_ai_conversation_share;
ALTER TABLE site_ai_message RENAME TO agent_ai_message;
ALTER TABLE site_ai_prompt_profile RENAME TO agent_ai_prompt_profile;
ALTER TABLE site_ai_knowledge_base RENAME TO agent_ai_knowledge_base;
ALTER TABLE site_ai_knowledge_base_link RENAME TO agent_ai_knowledge_base_link;
ALTER TABLE site_ai_knowledge_document RENAME TO agent_ai_knowledge_document;
ALTER TABLE ai_model_entry RENAME TO agent_model_entry;

-- 列改名（只作用于已改名的表）：site_id → agent_id、site_user_id → agent_user_id、agent.site_code → agent_code、agent.site_name → agent_name、agent.site_type → agent_type。
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT table_name, column_name
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name IN ('agent', 'agent_user', 'agent_user_profile', 'agent_user_memory',
                             'agent_ai_conversation', 'agent_ai_conversation_share', 'agent_ai_message',
                             'agent_ai_prompt_profile', 'agent_ai_knowledge_base',
                             'agent_ai_knowledge_base_link', 'agent_ai_knowledge_document',
                             'agent_model_entry')
          AND column_name IN ('site_id', 'site_user_id', 'site_code', 'site_name', 'site_type')
    LOOP
        EXECUTE format('ALTER TABLE %I RENAME COLUMN %I TO %I',
                       r.table_name,
                       r.column_name,
                       CASE r.column_name
                           WHEN 'site_id' THEN 'agent_id'
                           WHEN 'site_user_id' THEN 'agent_user_id'
                           WHEN 'site_code' THEN 'agent_code'
                           WHEN 'site_name' THEN 'agent_name'
                           WHEN 'site_type' THEN 'agent_type'
                       END);
    END LOOP;
END $$;

-- 索引改名：改名后表上名字含 site 的索引（含唯一索引）同步换前缀。
DO $$
DECLARE
    r RECORD;
    new_name TEXT;
BEGIN
    FOR r IN
        SELECT i.relname AS index_name
        FROM pg_index x
        JOIN pg_class i ON i.oid = x.indexrelid
        JOIN pg_class t ON t.oid = x.indrelid
        WHERE t.relnamespace = 'public'::regnamespace
          AND t.relname IN ('agent', 'agent_user', 'agent_user_profile', 'agent_user_memory',
                            'agent_ai_conversation', 'agent_ai_conversation_share', 'agent_ai_message',
                            'agent_ai_prompt_profile', 'agent_ai_knowledge_base',
                            'agent_ai_knowledge_base_link', 'agent_ai_knowledge_document',
                            'agent_model_entry')
          AND i.relname LIKE '%site%'
    LOOP
        new_name := replace(replace(replace(r.index_name,
                        'site_ai', 'agent_ai'), 'site_user', 'agent_user'), 'site', 'agent');
        IF new_name <> r.index_name THEN
            EXECUTE format('ALTER INDEX %I RENAME TO %I', r.index_name, new_name);
        END IF;
    END LOOP;
END $$;

-- 约束改名：改名后表上名字含 site 的约束（PK/FK/UK/CHECK）同步换前缀。
DO $$
DECLARE
    r RECORD;
    new_name TEXT;
BEGIN
    FOR r IN
        SELECT con.conname AS constraint_name, t.relname AS table_name
        FROM pg_constraint con
        JOIN pg_class t ON t.oid = con.conrelid
        WHERE t.relnamespace = 'public'::regnamespace
          AND t.relname IN ('agent', 'agent_user', 'agent_user_profile', 'agent_user_memory',
                            'agent_ai_conversation', 'agent_ai_conversation_share', 'agent_ai_message',
                            'agent_ai_prompt_profile', 'agent_ai_knowledge_base',
                            'agent_ai_knowledge_base_link', 'agent_ai_knowledge_document',
                            'agent_model_entry')
          AND con.conname LIKE '%site%'
    LOOP
        new_name := replace(replace(replace(r.constraint_name,
                        'site_ai', 'agent_ai'), 'site_user', 'agent_user'), 'site', 'agent');
        IF new_name <> r.constraint_name THEN
            EXECUTE format('ALTER TABLE %I RENAME CONSTRAINT %I TO %I',
                           r.table_name, r.constraint_name, new_name);
        END IF;
    END LOOP;
END $$;
