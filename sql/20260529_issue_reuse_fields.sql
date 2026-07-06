ALTER TABLE ops_issue
  ADD COLUMN reuse_tags VARCHAR(500) NULL COMMENT '复用标签，逗号分隔';

ALTER TABLE ops_issue
  ADD COLUMN knowledge_included TINYINT DEFAULT 1 COMMENT '是否纳入知识沉淀';
