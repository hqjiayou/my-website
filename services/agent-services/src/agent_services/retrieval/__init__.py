"""agent_retrieval 派生索引（Phase 3）。

KB 真相源在 Java（site_ai_knowledge_document）；本模块只维护派生向量索引。
独立 schema `agent_retrieval` + 专用 DB 用户，禁止读取 Java 业务表。
"""
