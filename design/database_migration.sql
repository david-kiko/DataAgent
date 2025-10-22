-- 为Agent表增加CSV上传支持字段
-- 执行时间：2025-01-22

-- 为agent表增加CSV相关字段
ALTER TABLE agent ADD COLUMN csv_upload_enabled TINYINT DEFAULT 0 COMMENT '是否允许上传CSV文件：0-否，1-是';
ALTER TABLE agent ADD COLUMN csv_max_file_size BIGINT DEFAULT 10485760 COMMENT 'CSV文件最大大小（字节），默认10MB';
ALTER TABLE agent ADD COLUMN csv_allowed_types VARCHAR(255) DEFAULT 'csv,xlsx' COMMENT '允许的文件类型，逗号分隔';

-- 注意：CSV文件表和分析结果表将在后续步骤中创建
